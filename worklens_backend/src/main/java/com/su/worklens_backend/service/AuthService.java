package com.su.worklens_backend.service;

import com.su.worklens_backend.auth.AuthenticatedUser;
import com.su.worklens_backend.dto.ChangePasswordRequest;
import com.su.worklens_backend.dto.CurrentUserResponse;
import com.su.worklens_backend.dto.LoginRequest;
import com.su.worklens_backend.dto.LoginResponse;
import com.su.worklens_backend.dto.PasswordChangeResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public interface AuthService {

    LoginResponse login(LoginRequest request);

    AuthenticatedUser resolveAuthenticatedUser(String bearerToken);

    AuthenticatedUser getAuthenticatedUser(HttpServletRequest request);

    CurrentUserResponse getCurrentUser(HttpServletRequest request);

    PasswordChangeResponse changePassword(HttpServletRequest request, ChangePasswordRequest changePasswordRequest);

    /**
     * Revokes the token presented in the Authorization header, if any.
     */
    void logout(String bearerToken);

    /**
     * Role assertion used as defense in depth inside service implementations.
     * The {@code AuthTokenFilter} performs the primary path-based role checks,
     * but services must never rely on the filter alone: this guard makes any
     * direct or filtered-bypassed call fail closed.
     */
    default void requireRole(AuthenticatedUser authenticatedUser, String requiredRole) {
        if (authenticatedUser == null || !requiredRole.equals(authenticatedUser.getRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Insufficient role for this operation");
        }
    }
}
