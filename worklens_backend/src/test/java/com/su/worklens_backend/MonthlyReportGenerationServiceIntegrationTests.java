package com.su.worklens_backend;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.su.worklens_backend.service.LlmProvider;
import com.su.worklens_backend.service.ReportGenerationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

@SpringBootTest
class MonthlyReportGenerationServiceIntegrationTests extends PostgresIntegrationTestSupport {

    private static final String PASSWORD_HASH = "pbkdf2_sha256$120000$d29ya2xlbnMtc2FsdC0wMQ==$y7dDc5YjVRKR+v1GlPwEumSMa6Wa4bMH0h23Tk8Tx64=";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ReportGenerationService reportGenerationService;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private LlmProvider llmProvider;

    @BeforeEach
    void cleanDatabase() {
        truncateIfExists("TRUNCATE TABLE llm_reports RESTART IDENTITY CASCADE");
        truncateIfExists("TRUNCATE TABLE detail_access_audit_logs RESTART IDENTITY CASCADE");
        truncateIfExists("TRUNCATE TABLE detail_access_requests RESTART IDENTITY CASCADE");
        truncateIfExists("TRUNCATE TABLE usage_records RESTART IDENTITY CASCADE");
        truncateIfExists("TRUNCATE TABLE auth_tokens RESTART IDENTITY CASCADE");
        truncateIfExists("TRUNCATE TABLE auth_users RESTART IDENTITY CASCADE");
        truncateIfExists("TRUNCATE TABLE employees RESTART IDENTITY CASCADE");
    }

    @Test
    void generateMonthlyReportsAggregatesEmployeeWeeklyReportsThenDeletesWeeklySources() throws Exception {
        long aliceEmployeeId = insertUser("employee.alice", "EMPLOYEE", "E001", "Alice");
        long bobEmployeeId = insertUser("employee.bob", "EMPLOYEE", "E002", "Bob");
        LocalDate monthEndDate = LocalDate.of(2026, 7, 31);
        insertEmployeeWeeklyReport(aliceEmployeeId, "2026-07-06", "2026-07-12", """
                [
                  {"appName":"Chrome","durationSeconds":5400,"durationMinutes":90,"ratio":0.6429},
                  {"appName":"IDE","durationSeconds":1800,"durationMinutes":30,"ratio":0.2143},
                  {"appName":"Slack","durationSeconds":1200,"durationMinutes":20,"ratio":0.1429}
                ]
                """);
        insertEmployeeWeeklyReport(aliceEmployeeId, "2026-07-13", "2026-07-19", """
                [
                  {"appName":"Chrome","durationSeconds":3600,"durationMinutes":60,"ratio":0.5000},
                  {"appName":"Teams","durationSeconds":3600,"durationMinutes":60,"ratio":0.5000}
                ]
                """);
        insertEmployeeWeeklyReport(bobEmployeeId, "2026-07-06", "2026-07-12", """
                [
                  {"appName":"Teams","durationSeconds":2700,"durationMinutes":45,"ratio":1.0000}
                ]
                """);

        given(llmProvider.generateText(anyString()))
                .willReturn("Alice monthly summary", "Bob monthly summary", "Team monthly summary");

        reportGenerationService.generateMonthlyReports(monthEndDate);

        List<Map<String, Object>> monthlyReports = jdbcTemplate.queryForList(
                """
                        SELECT target_employee_id, period_start_date, period_end_date, detail_json::text AS detail_json,
                               summary, source_layer, source_count
                        FROM llm_reports
                        WHERE report_scope = 'EMPLOYEE' AND period_type = 'MONTHLY'
                        ORDER BY target_employee_id
                        """
        );
        assertThat(monthlyReports).hasSize(2);

        Map<String, Object> aliceMonthlyReport = monthlyReports.get(0);
        assertThat(aliceMonthlyReport.get("target_employee_id")).isEqualTo(aliceEmployeeId);
        assertThat(aliceMonthlyReport.get("period_start_date").toString()).isEqualTo("2026-07-01");
        assertThat(aliceMonthlyReport.get("period_end_date").toString()).isEqualTo("2026-07-31");
        assertThat(aliceMonthlyReport.get("summary")).isEqualTo("Alice monthly summary");
        assertThat(aliceMonthlyReport.get("source_layer")).isEqualTo("WEEKLY_REPORTS");
        assertThat(aliceMonthlyReport.get("source_count")).isEqualTo(2);

        List<Map<String, Object>> aliceDetails = objectMapper.readValue(
                aliceMonthlyReport.get("detail_json").toString(),
                new TypeReference<>() {
                }
        );
        assertThat(aliceDetails).extracting(item -> item.get("appName")).containsExactly("Chrome", "Teams", "IDE", "Slack");
        assertThat(aliceDetails).extracting(item -> item.get("durationSeconds")).containsExactly(9000, 3600, 1800, 1200);
        assertThat(aliceDetails).extracting(item -> item.get("durationMinutes")).containsExactly(150, 60, 30, 20);
        assertThat(aliceDetails).extracting(item -> item.get("ratio")).containsExactly(0.5769, 0.2308, 0.1154, 0.0769);

        Integer remainingWeeklyReportCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM llm_reports WHERE period_type = 'WEEKLY'",
                Integer.class
        );
        Integer rawRecordCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM usage_records", Integer.class);
        assertThat(remainingWeeklyReportCount).isZero();
        assertThat(rawRecordCount).isZero();
    }

    @Test
    void generateMonthlyReportsAggregatesTeamWeeklyReportsWithoutIndividualDetails() throws Exception {
        LocalDate monthEndDate = LocalDate.of(2026, 7, 31);
        insertTeamWeeklyReport("2026-07-06", "2026-07-12", """
                [
                  {"appName":"Chrome","durationSeconds":7200,"durationMinutes":120,"ratio":0.6667},
                  {"appName":"IDE","durationSeconds":1800,"durationMinutes":30,"ratio":0.1667},
                  {"appName":"Slack","durationSeconds":1800,"durationMinutes":30,"ratio":0.1667}
                ]
                """);
        insertTeamWeeklyReport("2026-07-13", "2026-07-19", """
                [
                  {"appName":"Chrome","durationSeconds":3600,"durationMinutes":60,"ratio":0.4000},
                  {"appName":"Teams","durationSeconds":5400,"durationMinutes":90,"ratio":0.6000}
                ]
                """);

        given(llmProvider.generateText(anyString()))
                .willReturn("Team monthly summary");

        reportGenerationService.generateMonthlyReports(monthEndDate);

        List<Map<String, Object>> reports = jdbcTemplate.queryForList(
                """
                        SELECT requester_employee_id, target_employee_id, period_start_date, period_end_date,
                               detail_json::text AS detail_json, summary, source_layer, source_count
                        FROM llm_reports
                        WHERE report_scope = 'TEAM' AND period_type = 'MONTHLY'
                        """
        );
        assertThat(reports).hasSize(1);
        Map<String, Object> teamMonthlyReport = reports.get(0);
        assertThat(teamMonthlyReport.get("requester_employee_id")).isNull();
        assertThat(teamMonthlyReport.get("target_employee_id")).isNull();
        assertThat(teamMonthlyReport.get("period_start_date").toString()).isEqualTo("2026-07-01");
        assertThat(teamMonthlyReport.get("period_end_date").toString()).isEqualTo("2026-07-31");
        assertThat(teamMonthlyReport.get("summary")).isEqualTo("Team monthly summary");
        assertThat(teamMonthlyReport.get("source_layer")).isEqualTo("WEEKLY_REPORTS");
        assertThat(teamMonthlyReport.get("source_count")).isEqualTo(2);

        List<Map<String, Object>> detailItems = objectMapper.readValue(
                teamMonthlyReport.get("detail_json").toString(),
                new TypeReference<>() {
                }
        );
        assertThat(detailItems).extracting(item -> item.get("appName")).containsExactly("Chrome", "Teams", "IDE", "Slack");
        assertThat(detailItems).extracting(item -> item.get("durationSeconds")).containsExactly(10800, 5400, 1800, 1800);
        assertThat(detailItems).extracting(item -> item.get("durationMinutes")).containsExactly(180, 90, 30, 30);
        assertThat(detailItems).extracting(item -> item.get("ratio")).containsExactly(0.5455, 0.2727, 0.0909, 0.0909);
        assertThat(teamMonthlyReport.get("detail_json").toString())
                .doesNotContain("Alice")
                .doesNotContain("Bob")
                .doesNotContain("E001")
                .doesNotContain("E002")
                .doesNotContain("employeeId")
                .doesNotContain("startedAt")
                .doesNotContain("endedAt");
    }

    @Test
    void generateMonthlyReportsIsIdempotentOnRerun() throws Exception {
        long aliceEmployeeId = insertUser("employee.alice", "EMPLOYEE", "E001", "Alice");
        LocalDate monthEndDate = LocalDate.of(2026, 7, 31);
        insertEmployeeWeeklyReport(aliceEmployeeId, "2026-07-06", "2026-07-12", """
                [
                  {"appName":"Chrome","durationSeconds":5400,"durationMinutes":90,"ratio":1.0000}
                ]
                """);
        insertTeamWeeklyReport("2026-07-06", "2026-07-12", """
                [
                  {"appName":"Chrome","durationSeconds":5400,"durationMinutes":90,"ratio":1.0000}
                ]
                """);

        given(llmProvider.generateText(anyString()))
                .willReturn("Alice monthly summary", "Team monthly summary");

        reportGenerationService.generateMonthlyReports(monthEndDate);

        Integer monthlyReportCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM llm_reports WHERE period_type = 'MONTHLY'",
                Integer.class
        );
        Integer weeklyReportCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM llm_reports WHERE period_type = 'WEEKLY'",
                Integer.class
        );
        assertThat(monthlyReportCount).isEqualTo(2);
        assertThat(weeklyReportCount).isZero();

        reset(llmProvider);
        reportGenerationService.generateMonthlyReports(monthEndDate);

        Integer monthlyReportCountAfterRerun = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM llm_reports WHERE period_type = 'MONTHLY'",
                Integer.class
        );
        assertThat(monthlyReportCountAfterRerun).isEqualTo(2);
        verify(llmProvider, never()).generateText(anyString());
    }

    private long insertUser(String username, String role, String employeeNo, String name) {
        jdbcTemplate.update(
                "INSERT INTO employees (name, employee_no, created_at) VALUES (?, ?, CURRENT_TIMESTAMP)",
                name,
                employeeNo
        );
        Long employeeId = jdbcTemplate.queryForObject(
                "SELECT id FROM employees WHERE employee_no = ?",
                Long.class,
                employeeNo
        );
        jdbcTemplate.update(
                "INSERT INTO auth_users (username, password_hash, role, employee_id, created_at) VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)",
                username,
                PASSWORD_HASH,
                role,
                employeeId
        );
        assertThat(employeeId).isNotNull();
        return employeeId;
    }

    private void insertEmployeeWeeklyReport(long employeeId, String startDate, String endDate, String detailJson) {
        jdbcTemplate.update(
                """
                        INSERT INTO llm_reports (
                            report_type, requester_employee_id, target_employee_id, summary,
                            period_started_at, period_ended_at, created_at, report_scope, period_type,
                            period_start_date, period_end_date, detail_json, source_layer, source_count, generated_at
                        )
                        VALUES ('EMPLOYEE_WEEKLY', ?, ?, 'weekly summary', ?::date, ?::date, CURRENT_TIMESTAMP,
                                'EMPLOYEE', 'WEEKLY', ?::date, ?::date, ?::jsonb, 'DAILY_REPORTS', 1, CURRENT_TIMESTAMP)
                        """,
                employeeId,
                employeeId,
                startDate,
                endDate,
                startDate,
                endDate,
                detailJson
        );
    }

    private void insertTeamWeeklyReport(String startDate, String endDate, String detailJson) {
        jdbcTemplate.update(
                """
                        INSERT INTO llm_reports (
                            report_type, requester_employee_id, target_employee_id, summary,
                            period_started_at, period_ended_at, created_at, report_scope, period_type,
                            period_start_date, period_end_date, detail_json, source_layer, source_count, generated_at
                        )
                        VALUES ('TEAM_WEEKLY', NULL, NULL, 'team weekly summary', ?::date, ?::date, CURRENT_TIMESTAMP,
                                'TEAM', 'WEEKLY', ?::date, ?::date, ?::jsonb, 'DAILY_REPORTS', 1, CURRENT_TIMESTAMP)
                        """,
                startDate,
                endDate,
                startDate,
                endDate,
                detailJson
        );
    }

    private void truncateIfExists(String sql) {
        try {
            jdbcTemplate.execute(sql);
        } catch (Exception ignored) {
        }
    }
}
