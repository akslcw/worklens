package com.su.worklens_backend;

import com.su.worklens_backend.service.EmployeeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Decision 4A: soft-deleted employees are physically purged (with their
 * usage history, access requests, audit logs and orphaned login rows) once
 * the retention window has elapsed.
 */
@SpringBootTest
class RetentionPurgeIntegrationTests extends PostgresIntegrationTestSupport {

    @Autowired
    private EmployeeService employeeService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void purgesOnlyEmployeesDeletedBeforeTheRetentionCutoff() {
        truncate("TRUNCATE TABLE detail_access_audit_logs RESTART IDENTITY CASCADE");
        truncate("TRUNCATE TABLE detail_access_requests RESTART IDENTITY CASCADE");
        truncate("TRUNCATE TABLE usage_records RESTART IDENTITY CASCADE");
        truncate("TRUNCATE TABLE llm_reports RESTART IDENTITY CASCADE");
        truncate("TRUNCATE TABLE auth_tokens RESTART IDENTITY CASCADE");
        truncate("TRUNCATE TABLE auth_users RESTART IDENTITY CASCADE");
        truncate("TRUNCATE TABLE auth_login_attempts RESTART IDENTITY CASCADE");
        truncate("TRUNCATE TABLE employees RESTART IDENTITY CASCADE");

        long oldEmployeeId = insertSoftDeletedEmployee("E001", LocalDateTime.now().minusDays(31));
        insertUsageRecord(oldEmployeeId);
        jdbcTemplate.update(
                "INSERT INTO detail_access_requests (requester_employee_id, target_employee_id, reason, status, created_at) "
                        + "VALUES (?, ?, 'audit retention', 'PENDING', CURRENT_TIMESTAMP)",
                oldEmployeeId,
                oldEmployeeId
        );
        jdbcTemplate.update(
                "INSERT INTO detail_access_audit_logs (detail_access_request_id, viewer_employee_id, target_employee_id, viewed_at) "
                        + "VALUES ((SELECT id FROM detail_access_requests LIMIT 1), ?, ?, CURRENT_TIMESTAMP)",
                oldEmployeeId,
                oldEmployeeId
        );

        long recentEmployeeId = insertSoftDeletedEmployee("E002", LocalDateTime.now().minusDays(1));
        insertUsageRecord(recentEmployeeId);

        LocalDateTime cutoff = LocalDateTime.now().minusDays(30);
        int purged = employeeService.purgeExpiredEmployees(cutoff);

        assertThat(purged).isEqualTo(1);

        Integer oldEmployeeCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM employees WHERE id = ?",
                Integer.class,
                oldEmployeeId
        );
        Integer oldUsageCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM usage_records WHERE employee_id = ?",
                Integer.class,
                oldEmployeeId
        );
        Integer oldRequests = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM detail_access_requests WHERE requester_employee_id = ? OR target_employee_id = ?",
                Integer.class,
                oldEmployeeId,
                oldEmployeeId
        );
        Integer oldAudits = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM detail_access_audit_logs WHERE viewer_employee_id = ? OR target_employee_id = ?",
                Integer.class,
                oldEmployeeId,
                oldEmployeeId
        );
        Integer orphanAccounts = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM auth_users WHERE employee_id IS NULL AND username = 'E001'",
                Integer.class
        );

        assertThat(oldEmployeeCount).isZero();
        assertThat(oldUsageCount).isZero();
        assertThat(oldRequests).isZero();
        assertThat(oldAudits).isZero();
        assertThat(orphanAccounts).isZero();

        Integer recentEmployeeCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM employees WHERE id = ?",
                Integer.class,
                recentEmployeeId
        );
        Integer recentUsageCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM usage_records WHERE employee_id = ?",
                Integer.class,
                recentEmployeeId
        );
        assertThat(recentEmployeeCount).isEqualTo(1);
        assertThat(recentUsageCount).isEqualTo(1);
    }

    private long insertSoftDeletedEmployee(String employeeNo, LocalDateTime deletedAt) {
        jdbcTemplate.update(
                "INSERT INTO employees (name, employee_no, created_at, deleted_at) VALUES (?, ?, CURRENT_TIMESTAMP, ?)",
                "Soft Deleted",
                employeeNo,
                Timestamp.valueOf(deletedAt)
        );
        Long employeeId = jdbcTemplate.queryForObject(
                "SELECT id FROM employees WHERE employee_no = ?",
                Long.class,
                employeeNo
        );
        jdbcTemplate.update(
                "INSERT INTO auth_users (username, password_hash, role, employee_id, created_at) VALUES (?, 'unused', 'EMPLOYEE', NULL, CURRENT_TIMESTAMP)",
                employeeNo
        );
        assertThat(employeeId).isNotNull();
        return employeeId;
    }

    private void insertUsageRecord(long employeeId) {
        jdbcTemplate.update(
                "INSERT INTO usage_records (employee_id, app_name, started_at, ended_at, created_at) "
                        + "VALUES (?, 'Chrome', CURRENT_TIMESTAMP - INTERVAL '1 hour', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                employeeId
        );
    }

    private void truncate(String sql) {
        try {
            jdbcTemplate.execute(sql);
        } catch (Exception ignored) {
        }
    }
}
