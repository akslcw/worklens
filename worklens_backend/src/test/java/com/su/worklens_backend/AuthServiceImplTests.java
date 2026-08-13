package com.su.worklens_backend;

import com.su.worklens_backend.auth.AuthenticatedUser;
import com.su.worklens_backend.entity.AuthToken;
import com.su.worklens_backend.entity.AuthUser;
import com.su.worklens_backend.mapper.AuthLoginAttemptMapper;
import com.su.worklens_backend.mapper.AuthTokenMapper;
import com.su.worklens_backend.mapper.AuthUserMapper;
import com.su.worklens_backend.mapper.EmployeeMapper;
import com.su.worklens_backend.service.PasswordHasher;
import com.su.worklens_backend.service.impl.AuthServiceImpl;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * H3: token expiry evaluation must use the injected Hong Kong clock, not the
 * JVM default zone, so a UTC container does not silently extend the 24h TTL.
 */
class AuthServiceImplTests {

    private static final ZoneId HONG_KONG = ZoneId.of("Asia/Hong_Kong");

    @Test
    void resolveAuthenticatedUserRejectsTokenExpiredAccordingToInjectedClock() {
        Clock clock = Clock.fixed(Instant.parse("2026-07-08T16:00:00Z"), HONG_KONG);
        AuthTokenMapper authTokenMapper = mock(AuthTokenMapper.class);
        AuthUserMapper authUserMapper = mock(AuthUserMapper.class);
        AuthServiceImpl authService = new AuthServiceImpl(
                authUserMapper,
                mock(AuthLoginAttemptMapper.class),
                authTokenMapper,
                mock(EmployeeMapper.class),
                mock(PasswordHasher.class),
                clock
        );

        AuthToken token = new AuthToken();
        token.setUserId(1L);
        token.setToken("token-1");
        token.setCreatedAt(LocalDateTime.of(2026, 7, 8, 0, 0));
        token.setExpiresAt(LocalDateTime.of(2026, 7, 8, 23, 59));
        when(authTokenMapper.selectOne(any())).thenReturn(token);

        assertThat(authService.resolveAuthenticatedUser("Bearer token-1")).isNull();
    }

    @Test
    void resolveAuthenticatedUserAcceptsTokenStillValidAccordingToInjectedClock() {
        Clock clock = Clock.fixed(Instant.parse("2026-07-08T16:00:00Z"), HONG_KONG);
        AuthTokenMapper authTokenMapper = mock(AuthTokenMapper.class);
        AuthUserMapper authUserMapper = mock(AuthUserMapper.class);
        AuthServiceImpl authService = new AuthServiceImpl(
                authUserMapper,
                mock(AuthLoginAttemptMapper.class),
                authTokenMapper,
                mock(EmployeeMapper.class),
                mock(PasswordHasher.class),
                clock
        );

        AuthToken token = new AuthToken();
        token.setUserId(1L);
        token.setToken("token-1");
        token.setCreatedAt(LocalDateTime.of(2026, 7, 8, 0, 0));
        token.setExpiresAt(LocalDateTime.of(2026, 7, 9, 0, 0));
        when(authTokenMapper.selectOne(any())).thenReturn(token);

        AuthUser authUser = new AuthUser();
        authUser.setId(1L);
        authUser.setEmployeeId(10L);
        authUser.setUsername("employee.alice");
        authUser.setRole("EMPLOYEE");
        when(authUserMapper.selectById(1L)).thenReturn(authUser);

        AuthenticatedUser authenticatedUser = authService.resolveAuthenticatedUser("Bearer token-1");

        assertThat(authenticatedUser).isNotNull();
        assertThat(authenticatedUser.getUsername()).isEqualTo("employee.alice");
    }
}
