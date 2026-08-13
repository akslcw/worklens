package com.su.worklens_backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.su.worklens_backend.auth.AuthenticatedUser;
import com.su.worklens_backend.dto.ChangePasswordRequest;
import com.su.worklens_backend.dto.CurrentUserResponse;
import com.su.worklens_backend.dto.LoginRequest;
import com.su.worklens_backend.dto.LoginResponse;
import com.su.worklens_backend.dto.PasswordChangeResponse;
import com.su.worklens_backend.dto.TeamUsageSummaryResponse;
import com.su.worklens_backend.mapper.UsageRecordMapper;
import com.su.worklens_backend.service.AuthService;
import com.su.worklens_backend.service.impl.UsageRecordServiceImpl;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UsageRecordServiceImplTests {

    @Test
    void teamSummaryUsesDatabaseAggregatesWithoutLoadingUsageRecords() {
        UsageRecordMapper usageRecordMapper = mock(UsageRecordMapper.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForMap(anyString())).thenReturn(Map.of(
                "total_usage_minutes", 135L,
                "active_employee_count", 2L
        ));
        when(jdbcTemplate.queryForList(anyString())).thenReturn(List.of(
                Map.of("app_name", "Slack", "usage_minutes", 75L),
                Map.of("app_name", "Chrome", "usage_minutes", 60L)
        ));
        UsageRecordServiceImpl service = new UsageRecordServiceImpl(
                usageRecordMapper,
                jdbcTemplate,
                new ObjectMapper(),
                enforcingRoleAuthService(),
                Clock.fixed(Instant.parse("2026-07-08T16:00:00Z"), ZoneId.of("Asia/Hong_Kong"))
        );

        TeamUsageSummaryResponse response = service.getTeamUsageSummary(
                new AuthenticatedUser(1L, 1L, "manager", "MANAGER")
        );

        assertThat(response.getTotalUsageMinutes()).isEqualTo(135L);
        assertThat(response.getActiveEmployeeCount()).isEqualTo(2);
        assertThat(response.getTeamAverageUsageMinutes()).isEqualByComparingTo("67.5");
        assertThat(response.getAppUsageRatios()).extracting("appName")
                .containsExactly("Slack", "Chrome");
        verify(usageRecordMapper, never()).selectList(null);
    }

    @Test
    void teamSummaryRejectsNonManagerRoles() {
        UsageRecordServiceImpl service = new UsageRecordServiceImpl(
                mock(UsageRecordMapper.class),
                mock(JdbcTemplate.class),
                new ObjectMapper(),
                enforcingRoleAuthService(),
                Clock.fixed(Instant.parse("2026-07-08T16:00:00Z"), ZoneId.of("Asia/Hong_Kong"))
        );

        assertThatThrownBy(() -> service.getTeamUsageSummary(
                new AuthenticatedUser(2L, 2L, "employee.alice", "EMPLOYEE")
        ))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Insufficient role");
    }

    private AuthService enforcingRoleAuthService() {
        return new AuthService() {
            @Override
            public void requireRole(AuthenticatedUser authenticatedUser, String requiredRole) {
                if (authenticatedUser == null || !requiredRole.equals(authenticatedUser.getRole())) {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Insufficient role for this operation");
                }
            }

            @Override
            public LoginResponse login(LoginRequest request) {
                throw new UnsupportedOperationException("not used in this test");
            }

            @Override
            public AuthenticatedUser resolveAuthenticatedUser(String bearerToken) {
                throw new UnsupportedOperationException("not used in this test");
            }

            @Override
            public AuthenticatedUser getAuthenticatedUser(HttpServletRequest request) {
                throw new UnsupportedOperationException("not used in this test");
            }

            @Override
            public CurrentUserResponse getCurrentUser(HttpServletRequest request) {
                throw new UnsupportedOperationException("not used in this test");
            }

            @Override
            public PasswordChangeResponse changePassword(HttpServletRequest request, ChangePasswordRequest changePasswordRequest) {
                throw new UnsupportedOperationException("not used in this test");
            }
        };
    }
}
