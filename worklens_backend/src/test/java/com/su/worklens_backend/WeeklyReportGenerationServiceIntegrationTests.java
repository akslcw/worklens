package com.su.worklens_backend;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.su.worklens_backend.service.LlmProvider;
import com.su.worklens_backend.service.ReportGenerationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
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
class WeeklyReportGenerationServiceIntegrationTests extends PostgresIntegrationTestSupport {

    private static final String PASSWORD_HASH = "pbkdf2_sha256$120000$d29ya2xlbnMtc2FsdC0wMQ==$y7dDc5YjVRKR+v1GlPwEumSMa6Wa4bMH0h23Tk8Tx64=";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ReportGenerationService reportGenerationService;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private LlmProvider llmProvider;

    @BeforeEach
    void cleanDatabase() {
        truncateIfExists("TRUNCATE TABLE llm_reports RESTART IDENTITY CASCADE");
        truncateIfExists("TRUNCATE TABLE detail_access_audit_logs RESTART IDENTITY CASCADE");
        truncateIfExists("TRUNCATE TABLE detail_access_requests RESTART IDENTITY CASCADE");
        truncateIfExists("TRUNCATE TABLE usage_records RESTART IDENTITY CASCADE");
        truncateIfExists("TRUNCATE TABLE auth_tokens RESTART IDENTITY CASCADE");
        truncateIfExists("TRUNCATE TABLE auth_login_attempts RESTART IDENTITY CASCADE");
        truncateIfExists("TRUNCATE TABLE auth_users RESTART IDENTITY CASCADE");
        truncateIfExists("TRUNCATE TABLE employees RESTART IDENTITY CASCADE");
    }

    @Test
    void generateWeeklyReportsAggregatesEmployeeDailyReportsThenDeletesDailySources() throws Exception {
        long aliceEmployeeId = insertUser("employee.alice", "EMPLOYEE", "E001", "Alice");
        long bobEmployeeId = insertUser("employee.bob", "EMPLOYEE", "E002", "Bob");
        LocalDate weekEndDate = LocalDate.of(2026, 7, 12);
        insertEmployeeDailyReport(aliceEmployeeId, "2026-07-06", """
                [
                  {"appName":"Chrome","durationSeconds":3600,"durationMinutes":60,"ratio":0.7500},
                  {"appName":"Slack","durationSeconds":1200,"durationMinutes":20,"ratio":0.2500}
                ]
                """);
        insertEmployeeDailyReport(aliceEmployeeId, "2026-07-07", """
                [
                  {"appName":"Chrome","durationSeconds":1800,"durationMinutes":30,"ratio":0.5000},
                  {"appName":"IDE","durationSeconds":1800,"durationMinutes":30,"ratio":0.5000}
                ]
                """);
        insertEmployeeDailyReport(bobEmployeeId, "2026-07-06", """
                [
                  {"appName":"Teams","durationSeconds":2700,"durationMinutes":45,"ratio":1.0000}
                ]
                """);

        given(llmProvider.generateText(anyString()))
                .willReturn("Alice weekly summary", "Bob weekly summary", "Team weekly summary");

        reportGenerationService.generateWeeklyReports(weekEndDate);

        List<Map<String, Object>> weeklyReports = jdbcTemplate.queryForList(
                """
                        SELECT target_employee_id, period_start_date, period_end_date, detail_json::text AS detail_json,
                               summary, source_layer, source_count
                        FROM llm_reports
                        WHERE report_scope = 'EMPLOYEE' AND period_type = 'WEEKLY'
                        ORDER BY target_employee_id
                        """
        );
        assertThat(weeklyReports).hasSize(2);

        Map<String, Object> aliceWeeklyReport = weeklyReports.get(0);
        assertThat(aliceWeeklyReport.get("target_employee_id")).isEqualTo(aliceEmployeeId);
        assertThat(aliceWeeklyReport.get("period_start_date").toString()).isEqualTo("2026-07-06");
        assertThat(aliceWeeklyReport.get("period_end_date").toString()).isEqualTo("2026-07-12");
        assertThat(aliceWeeklyReport.get("summary")).isEqualTo("Alice weekly summary");
        assertThat(aliceWeeklyReport.get("source_layer")).isEqualTo("DAILY_REPORTS");
        assertThat(aliceWeeklyReport.get("source_count")).isEqualTo(2);

        List<Map<String, Object>> aliceDetails = objectMapper.readValue(
                aliceWeeklyReport.get("detail_json").toString(),
                new TypeReference<>() {
                }
        );
        assertThat(aliceDetails).extracting(item -> item.get("appName")).containsExactly("Chrome", "IDE", "Slack");
        assertThat(aliceDetails).extracting(item -> item.get("durationSeconds")).containsExactly(5400, 1800, 1200);
        assertThat(aliceDetails).extracting(item -> item.get("durationMinutes")).containsExactly(90, 30, 20);
        assertThat(aliceDetails).extracting(item -> item.get("ratio")).containsExactly(0.6429, 0.2143, 0.1429);

        Integer remainingDailyReportCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM llm_reports WHERE period_type = 'DAILY'",
                Integer.class
        );
        Integer rawRecordCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM usage_records", Integer.class);
        assertThat(remainingDailyReportCount).isZero();
        assertThat(rawRecordCount).isZero();
    }

    @Test
    void generateWeeklyReportsAggregatesTeamDailyReportsWithoutIndividualDetails() throws Exception {
        LocalDate weekEndDate = LocalDate.of(2026, 7, 12);
        insertTeamDailyReport("2026-07-06", """
                [
                  {"appName":"Chrome","durationSeconds":6300,"durationMinutes":105,"ratio":0.7778},
                  {"appName":"Slack","durationSeconds":1800,"durationMinutes":30,"ratio":0.2222}
                ]
                """);
        insertTeamDailyReport("2026-07-07", """
                [
                  {"appName":"Chrome","durationSeconds":900,"durationMinutes":15,"ratio":0.3333},
                  {"appName":"IDE","durationSeconds":1800,"durationMinutes":30,"ratio":0.6667}
                ]
                """);

        given(llmProvider.generateText(anyString()))
                .willReturn("Team weekly summary");

        reportGenerationService.generateWeeklyReports(weekEndDate);

        List<Map<String, Object>> reports = jdbcTemplate.queryForList(
                """
                        SELECT requester_employee_id, target_employee_id, period_start_date, period_end_date,
                               detail_json::text AS detail_json, summary, source_layer, source_count
                        FROM llm_reports
                        WHERE report_scope = 'TEAM' AND period_type = 'WEEKLY'
                        """
        );
        assertThat(reports).hasSize(1);
        Map<String, Object> teamWeeklyReport = reports.get(0);
        assertThat(teamWeeklyReport.get("requester_employee_id")).isNull();
        assertThat(teamWeeklyReport.get("target_employee_id")).isNull();
        assertThat(teamWeeklyReport.get("period_start_date").toString()).isEqualTo("2026-07-06");
        assertThat(teamWeeklyReport.get("period_end_date").toString()).isEqualTo("2026-07-12");
        assertThat(teamWeeklyReport.get("summary")).isEqualTo("Team weekly summary");
        assertThat(teamWeeklyReport.get("source_layer")).isEqualTo("DAILY_REPORTS");
        assertThat(teamWeeklyReport.get("source_count")).isEqualTo(2);

        List<Map<String, Object>> detailItems = objectMapper.readValue(
                teamWeeklyReport.get("detail_json").toString(),
                new TypeReference<>() {
                }
        );
        assertThat(detailItems).extracting(item -> item.get("appName")).containsExactly("Chrome", "IDE", "Slack");
        assertThat(detailItems).extracting(item -> item.get("durationSeconds")).containsExactly(7200, 1800, 1800);
        assertThat(detailItems).extracting(item -> item.get("durationMinutes")).containsExactly(120, 30, 30);
        assertThat(detailItems).extracting(item -> item.get("ratio")).containsExactly(0.6667, 0.1667, 0.1667);
        assertThat(teamWeeklyReport.get("detail_json").toString())
                .doesNotContain("Alice")
                .doesNotContain("Bob")
                .doesNotContain("E001")
                .doesNotContain("E002")
                .doesNotContain("employeeId")
                .doesNotContain("startedAt")
                .doesNotContain("endedAt");
    }

    @Test
    void generateWeeklyReportsIsIdempotentOnRerun() throws Exception {
        long aliceEmployeeId = insertUser("employee.alice", "EMPLOYEE", "E001", "Alice");
        LocalDate weekEndDate = LocalDate.of(2026, 7, 12);
        insertEmployeeDailyReport(aliceEmployeeId, "2026-07-06", """
                [
                  {"appName":"Chrome","durationSeconds":3600,"durationMinutes":60,"ratio":1.0000}
                ]
                """);
        insertTeamDailyReport("2026-07-06", """
                [
                  {"appName":"Chrome","durationSeconds":3600,"durationMinutes":60,"ratio":1.0000}
                ]
                """);

        given(llmProvider.generateText(anyString()))
                .willReturn("Alice weekly summary", "Team weekly summary");

        reportGenerationService.generateWeeklyReports(weekEndDate);

        Integer weeklyReportCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM llm_reports WHERE period_type = 'WEEKLY'",
                Integer.class
        );
        Integer dailyReportCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM llm_reports WHERE period_type = 'DAILY'",
                Integer.class
        );
        assertThat(weeklyReportCount).isEqualTo(2);
        assertThat(dailyReportCount).isZero();

        reset(llmProvider);
        reportGenerationService.generateWeeklyReports(weekEndDate);

        Integer weeklyReportCountAfterRerun = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM llm_reports WHERE period_type = 'WEEKLY'",
                Integer.class
        );
        assertThat(weeklyReportCountAfterRerun).isEqualTo(2);
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

    private void insertEmployeeDailyReport(long employeeId, String reportDate, String detailJson) {
        jdbcTemplate.update(
                """
                        INSERT INTO llm_reports (
                            report_type, requester_employee_id, target_employee_id, summary,
                            period_started_at, period_ended_at, created_at, report_scope, period_type,
                            period_start_date, period_end_date, detail_json, source_layer, source_count, generated_at
                        )
                        VALUES ('EMPLOYEE_DAILY', ?, ?, 'daily summary', ?::date, ?::date, CURRENT_TIMESTAMP,
                                'EMPLOYEE', 'DAILY', ?::date, ?::date, ?::jsonb, 'RAW_USAGE', 1, CURRENT_TIMESTAMP)
                        """,
                employeeId,
                employeeId,
                reportDate,
                reportDate,
                reportDate,
                reportDate,
                detailJson
        );
    }

    private void insertTeamDailyReport(String reportDate, String detailJson) {
        jdbcTemplate.update(
                """
                        INSERT INTO llm_reports (
                            report_type, requester_employee_id, target_employee_id, summary,
                            period_started_at, period_ended_at, created_at, report_scope, period_type,
                            period_start_date, period_end_date, detail_json, source_layer, source_count, generated_at
                        )
                        VALUES ('TEAM_DAILY', NULL, NULL, 'team daily summary', ?::date, ?::date, CURRENT_TIMESTAMP,
                                'TEAM', 'DAILY', ?::date, ?::date, ?::jsonb, 'RAW_USAGE', 1, CURRENT_TIMESTAMP)
                        """,
                reportDate,
                reportDate,
                reportDate,
                reportDate,
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
