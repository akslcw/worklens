package com.su.worklens_backend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.su.worklens_backend.auth.AuthenticatedUser;
import com.su.worklens_backend.dto.ChangePasswordRequest;
import com.su.worklens_backend.dto.CurrentUserResponse;
import com.su.worklens_backend.dto.LoginRequest;
import com.su.worklens_backend.dto.LoginResponse;
import com.su.worklens_backend.dto.PasswordChangeResponse;
import com.su.worklens_backend.entity.AuthLoginAttempt;
import com.su.worklens_backend.entity.AuthToken;
import com.su.worklens_backend.entity.AuthUser;
import com.su.worklens_backend.entity.Employee;
import com.su.worklens_backend.mapper.AuthLoginAttemptMapper;
import com.su.worklens_backend.mapper.AuthTokenMapper;
import com.su.worklens_backend.mapper.AuthUserMapper;
import com.su.worklens_backend.mapper.EmployeeMapper;
import com.su.worklens_backend.service.AuthService;
import com.su.worklens_backend.service.PasswordHasher;
import com.su.worklens_backend.service.PasswordPolicy;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class AuthServiceImpl implements AuthService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuthServiceImpl.class);

    public static final String CURRENT_USER_ATTRIBUTE = "currentUser";
    private static final int TOKEN_TTL_HOURS = 24;
    private static final int MAX_FAILED_LOGIN_ATTEMPTS = 5;
    private static final int LOGIN_LOCK_MINUTES = 15;
    private static final String DUMMY_PASSWORD_HASH =
            "pbkdf2_sha256$120000$d29ya2xlbnMtc2FsdC0wMQ==$y7dDc5YjVRKR+v1GlPwEumSMa6Wa4bMH0h23Tk8Tx64=";

    private final AuthUserMapper authUserMapper;
    private final AuthLoginAttemptMapper authLoginAttemptMapper;
    private final AuthTokenMapper authTokenMapper;
    private final EmployeeMapper employeeMapper;
    private final PasswordHasher passwordHasher;
    private final Clock clock;

    public AuthServiceImpl(
            AuthUserMapper authUserMapper,
            AuthLoginAttemptMapper authLoginAttemptMapper,
            AuthTokenMapper authTokenMapper,
            EmployeeMapper employeeMapper,
            PasswordHasher passwordHasher,
            Clock clock
    ) {
        this.authUserMapper = authUserMapper;
        this.authLoginAttemptMapper = authLoginAttemptMapper;
        this.authTokenMapper = authTokenMapper;
        this.employeeMapper = employeeMapper;
        this.passwordHasher = passwordHasher;
        this.clock = clock;
    }

    @Override
    @Transactional(noRollbackFor = ResponseStatusException.class)
    public LoginResponse login(LoginRequest request) {
        String username = request.getUsername().trim();
        LocalDateTime now = LocalDateTime.now(clock);
        authLoginAttemptMapper.insertIfMissing(username, now);
        AuthLoginAttempt loginAttempt = authLoginAttemptMapper.selectForUpdate(username);

        boolean lockExpired = loginAttempt.getLockedUntil() != null
                && !loginAttempt.getLockedUntil().isAfter(now);
        if (loginAttempt.getLockedUntil() != null && loginAttempt.getLockedUntil().isAfter(now)) {
            throw new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "Too many failed login attempts. Try again in 15 minutes."
            );
        }
        if (lockExpired) {
            loginAttempt.setFailedAttempts(0);
            loginAttempt.setLockedUntil(null);
        }

        AuthUser authUser = authUserMapper.selectOne(
                new LambdaQueryWrapper<AuthUser>().eq(AuthUser::getUsername, username)
        );

        boolean passwordMatches;
        if (authUser == null) {
            // Verify against a dummy hash so unknown usernames cost the same
            // PBKDF2 work as a wrong password, removing the timing side
            // channel that would otherwise enumerate valid usernames.
            passwordHasher.matches(request.getPassword(), DUMMY_PASSWORD_HASH);
            passwordMatches = false;
        } else {
            passwordMatches = passwordHasher.matches(request.getPassword(), authUser.getPasswordHash());
        }

        if (!passwordMatches) {
            int failedAttempts = loginAttempt.getFailedAttempts() + 1;
            loginAttempt.setFailedAttempts(failedAttempts);
            loginAttempt.setUpdatedAt(now);
            if (failedAttempts >= MAX_FAILED_LOGIN_ATTEMPTS) {
                loginAttempt.setLockedUntil(now.plusMinutes(LOGIN_LOCK_MINUTES));
                authLoginAttemptMapper.updateById(loginAttempt);
                LOGGER.warn("Login locked for username={} after {} consecutive failures.", username, failedAttempts);
                throw new ResponseStatusException(
                        HttpStatus.TOO_MANY_REQUESTS,
                        "Too many failed login attempts. Try again in 15 minutes."
                );
            }
            authLoginAttemptMapper.updateById(loginAttempt);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password");
        }
        authLoginAttemptMapper.deleteById(username);
        if (authUser.getEmployeeId() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Account is not bound to an employee");
        }

        authTokenMapper.delete(
                new LambdaQueryWrapper<AuthToken>().eq(AuthToken::getUserId, authUser.getId())
        );
        AuthToken authToken = new AuthToken();
        authToken.setUserId(authUser.getId());
        String tokenValue = UUID.randomUUID().toString().replace("-", "");
        authToken.setToken(sha256Hex(tokenValue));
        authToken.setCreatedAt(now);
        authToken.setExpiresAt(now.plusHours(TOKEN_TTL_HOURS));
        authTokenMapper.insert(authToken);

        return new LoginResponse(
                tokenValue,
                authUser.getUsername(),
                resolveDisplayName(authUser),
                authUser.getRole(),
                Boolean.TRUE.equals(authUser.getMustChangePassword())
        );
    }

    @Override
    public AuthenticatedUser resolveAuthenticatedUser(String bearerToken) {
        if (bearerToken == null || bearerToken.isBlank()) {
            return null;
        }

        String tokenValue = bearerToken.startsWith("Bearer ") ? bearerToken.substring(7).trim() : bearerToken.trim();
        if (tokenValue.isEmpty()) {
            return null;
        }

        AuthToken authToken = authTokenMapper.selectOne(
                new LambdaQueryWrapper<AuthToken>().eq(AuthToken::getToken, sha256Hex(tokenValue))
        );

        if (authToken == null || authToken.getExpiresAt().isBefore(LocalDateTime.now(clock))) {
            return null;
        }

        AuthUser authUser = authUserMapper.selectById(authToken.getUserId());
        if (authUser == null) {
            return null;
        }
        if (authUser.getEmployeeId() == null) {
            return null;
        }

        return new AuthenticatedUser(
                authUser.getId(),
                authUser.getEmployeeId(),
                authUser.getUsername(),
                authUser.getRole(),
                Boolean.TRUE.equals(authUser.getMustChangePassword())
        );
    }

    @Override
    public AuthenticatedUser getAuthenticatedUser(HttpServletRequest request) {
        Object attribute = request.getAttribute(CURRENT_USER_ATTRIBUTE);
        if (!(attribute instanceof AuthenticatedUser authenticatedUser)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        return authenticatedUser;
    }

    @Override
    public CurrentUserResponse getCurrentUser(HttpServletRequest request) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(request);

        return new CurrentUserResponse(
                authenticatedUser.getEmployeeId(),
                authenticatedUser.getUsername(),
                resolveDisplayName(authenticatedUser.getEmployeeId(), authenticatedUser.getUsername()),
                authenticatedUser.getRole(),
                authenticatedUser.isMustChangePassword()
        );
    }

    @Override
    public PasswordChangeResponse changePassword(HttpServletRequest request, ChangePasswordRequest changePasswordRequest) {
        AuthenticatedUser authenticatedUser = getAuthenticatedUser(request);
        AuthUser authUser = authUserMapper.selectById(authenticatedUser.getAuthUserId());
        if (authUser == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        if (!passwordHasher.matches(changePasswordRequest.getCurrentPassword(), authUser.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid current password");
        }
        validateNewPassword(changePasswordRequest.getNewPassword(), changePasswordRequest.getCurrentPassword());

        authUser.setPasswordHash(passwordHasher.hash(changePasswordRequest.getNewPassword()));
        authUser.setMustChangePassword(false);
        authUserMapper.updateById(authUser);
        authTokenMapper.delete(
                new LambdaQueryWrapper<AuthToken>().eq(AuthToken::getUserId, authUser.getId())
        );
        return new PasswordChangeResponse(authUser.getUsername(), false);
    }

    /**
     * Server-side password policy: at least 8 characters with upper case,
     * lower case, digit and symbol, and different from the current password.
     */
    private void validateNewPassword(String newPassword, String currentPassword) {
        PasswordPolicy.requireStrength(newPassword);
        PasswordPolicy.requireDifferent(newPassword, currentPassword);
    }

    @Override
    public void logout(String bearerToken) {
        String tokenValue = bearerToken.startsWith("Bearer ") ? bearerToken.substring(7).trim() : bearerToken.trim();
        if (!tokenValue.isEmpty()) {
            authTokenMapper.delete(
                    new LambdaQueryWrapper<AuthToken>().eq(AuthToken::getToken, sha256Hex(tokenValue))
            );
        }
    }

    private String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private String resolveDisplayName(AuthUser authUser) {
        return resolveDisplayName(authUser.getEmployeeId(), authUser.getUsername());
    }

    private String resolveDisplayName(Long employeeId, String fallbackUsername) {
        if (employeeId == null) {
            return fallbackUsername;
        }
        Employee employee = employeeMapper.selectById(employeeId);
        if (employee == null || employee.getName() == null || employee.getName().isBlank()) {
            return fallbackUsername;
        }
        return employee.getName();
    }
}
