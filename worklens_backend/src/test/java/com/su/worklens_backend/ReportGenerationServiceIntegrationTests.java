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
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

@SpringBootTest
class ReportGenerationServiceIntegrationTests extends PostgresIntegrationTestSupport {

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
    void generateDailyReportsPersistsStructuredEmployeeReportsThenDeletesOnlyThatDaysRawRecords() throws Exception {
        long aliceEmployeeId = insertUser("employee.alice", "EMPLOYEE", "E001", "Alice");
        long bobEmployeeId = insertUser("employee.bob", "EMPLOYEE", "E002", "Bob");
        LocalDate reportDate = LocalDate.of(2026, 7, 8);

        insertUsageRecord(aliceEmployeeId, "Chrome", "2026-07-08T09:00:00", "2026-07-08T10:00:00");
        insertUsageRecord(aliceEmployeeId, "Slack", "2026-07-08T10:00:00", "2026-07-08T10:30:00");
        insertUsageRecord(aliceEmployeeId, "Chrome", "2026-07-08T11:00:00", "2026-07-08T11:15:00");
        insertUsageRecord(aliceEmployeeId, "Chrome", "2026-07-09T09:00:00", "2026-07-09T09:20:00");
        insertUsageRecord(bobEmployeeId, "Teams", "2026-07-08T09:00:00", "2026-07-08T09:45:00");

        given(llmProvider.generateText(anyString()))
                .willReturn("Alice daily summary", "Bob daily summary");

        reportGenerationService.generateDailyReports(reportDate);

        List<Map<String, Object>> reports = jdbcTemplate.queryForList(
                """
                        SELECT report_scope, period_type, target_employee_id, period_start_date, period_end_date,
                               detail_json::text AS detail_json, summary, source_layer, source_count
                        FROM llm_reports
                        WHERE report_scope = 'EMPLOYEE' AND period_type = 'DAILY'
                        ORDER BY target_employee_id
                        """
        );
        assertThat(reports).hasSize(2);

        Map<String, Object> aliceReport = reports.get(0);
        assertThat(aliceReport.get("target_employee_id")).isEqualTo(aliceEmployeeId);
        assertThat(aliceReport.get("period_start_date").toString()).isEqualTo("2026-07-08");
        assertThat(aliceReport.get("period_end_date").toString()).isEqualTo("2026-07-08");
        assertThat(aliceReport.get("summary")).isEqualTo("Alice daily summary");
        assertThat(aliceReport.get("source_layer")).isEqualTo("RAW_USAGE");
        assertThat(aliceReport.get("source_count")).isEqualTo(3);

        List<Map<String, Object>> detailItems = objectMapper.readValue(
                aliceReport.get("detail_json").toString(),
                new TypeReference<>() {
                }
        );
        assertThat(detailItems).extracting(item -> item.get("appName")).containsExactly("Chrome", "Slack");
        assertThat(detailItems).extracting(item -> item.get("durationSeconds")).containsExactly(4500, 1800);
        assertThat(detailItems).extracting(item -> item.get("durationMinutes")).containsExactly(75, 30);
        assertThat(detailItems).extracting(item -> item.get("ratio")).containsExactly(0.7143, 0.2857);

        Integer remainingRawRecordCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM usage_records", Integer.class);
        assertThat(remainingRawRecordCount).isEqualTo(1);
        String remainingStart = jdbcTemplate.queryForObject("SELECT started_at::text FROM usage_records", String.class);
        assertThat(remainingStart).startsWith("2026-07-09 09:00:00");
    }

    @Test
    void generateDailyReportsPersistsTeamDailyReportFromAggregatedRawUsageWithoutIndividualDetails() throws Exception {
        long aliceEmployeeId = insertUser("employee.alice", "EMPLOYEE", "E001", "Alice");
        long bobEmployeeId = insertUser("employee.bob", "EMPLOYEE", "E002", "Bob");
        LocalDate reportDate = LocalDate.of(2026, 7, 8);

        insertUsageRecord(aliceEmployeeId, "Chrome", "2026-07-08T09:00:00", "2026-07-08T10:00:00");
        insertUsageRecord(aliceEmployeeId, "Slack", "2026-07-08T10:00:00", "2026-07-08T10:30:00");
        insertUsageRecord(bobEmployeeId, "Chrome", "2026-07-08T11:00:00", "2026-07-08T11:45:00");

        given(llmProvider.generateText(anyString()))
                .willReturn("Alice daily summary", "Bob daily summary", "Team daily summary");

        reportGenerationService.generateDailyReports(reportDate);

        List<Map<String, Object>> reports = jdbcTemplate.queryForList(
                """
                        SELECT report_scope, period_type, requester_employee_id, target_employee_id,
                               period_start_date, period_end_date, detail_json::text AS detail_json,
                               summary, source_layer, source_count
                        FROM llm_reports
                        WHERE report_scope = 'TEAM' AND period_type = 'DAILY'
                        """
        );
        assertThat(reports).hasSize(1);

        Map<String, Object> teamReport = reports.get(0);
        assertThat(teamReport.get("requester_employee_id")).isNull();
        assertThat(teamReport.get("target_employee_id")).isNull();
        assertThat(teamReport.get("period_start_date").toString()).isEqualTo("2026-07-08");
        assertThat(teamReport.get("period_end_date").toString()).isEqualTo("2026-07-08");
        assertThat(teamReport.get("summary")).isEqualTo("Team daily summary");
        assertThat(teamReport.get("source_layer")).isEqualTo("RAW_USAGE");
        assertThat(teamReport.get("source_count")).isEqualTo(3);

        List<Map<String, Object>> detailItems = objectMapper.readValue(
                teamReport.get("detail_json").toString(),
                new TypeReference<>() {
                }
        );
        assertThat(detailItems).extracting(item -> item.get("appName")).containsExactly("Chrome", "Slack");
        assertThat(detailItems).extracting(item -> item.get("durationSeconds")).containsExactly(6300, 1800);
        assertThat(detailItems).extracting(item -> item.get("durationMinutes")).containsExactly(105, 30);
        assertThat(detailItems).extracting(item -> item.get("ratio")).containsExactly(0.7778, 0.2222);
        assertThat(teamReport.get("detail_json").toString())
                .doesNotContain("Alice")
                .doesNotContain("Bob")
                .doesNotContain("E001")
                .doesNotContain("E002")
                .doesNotContain("employeeId")
                .doesNotContain("startedAt")
                .doesNotContain("endedAt");
    }

    @Test
    void generateDailyReportsDoesNotDeleteRawRecordsWhenLlmFailsBeforeArchiveTransaction() {
        long aliceEmployeeId = insertUser("employee.alice", "EMPLOYEE", "E001", "Alice");
        LocalDate reportDate = LocalDate.of(2026, 7, 8);
        insertUsageRecord(aliceEmployeeId, "Chrome", "2026-07-08T09:00:00", "2026-07-08T10:00:00");

        given(llmProvider.generateText(anyString()))
                .willThrow(new IllegalStateException("LLM unavailable"));

        assertThatThrownBy(() -> reportGenerationService.generateDailyReports(reportDate))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("LLM unavailable");

        Integer reportCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM llm_reports", Integer.class);
        Integer rawRecordCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM usage_records", Integer.class);
        assertThat(reportCount).isZero();
        assertThat(rawRecordCount).isEqualTo(1);
    }

    @Test
    void generateDailyReportsRetriesOnlyMissingReportsAndDeletesRawRecordsAfterTeamReportSucceeds() {
        long aliceEmployeeId = insertUser("employee.alice", "EMPLOYEE", "E001", "Alice");
        long bobEmployeeId = insertUser("employee.bob", "EMPLOYEE", "E002", "Bob");
        LocalDate reportDate = LocalDate.of(2026, 7, 8);
        insertUsageRecord(aliceEmployeeId, "Chrome", "2026-07-08T09:00:00", "2026-07-08T10:00:00");
        insertUsageRecord(bobEmployeeId, "Teams", "2026-07-08T10:00:00", "2026-07-08T10:30:00");

        given(llmProvider.generateText(anyString()))
                .willReturn("Alice daily summary")
                .willThrow(new IllegalStateException("Second LLM call failed"));

        assertThatCode(() -> reportGenerationService.generateDailyReports(reportDate))
                .doesNotThrowAnyException();

        Integer reportCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM llm_reports", Integer.class);
        Integer rawRecordCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM usage_records", Integer.class);
        assertThat(reportCount).isEqualTo(1);
        assertThat(rawRecordCount).isEqualTo(2);

        reset(llmProvider);
        given(llmProvider.generateText(anyString()))
                .willReturn("Bob daily summary", "Team daily summary");

        reportGenerationService.generateDailyReports(reportDate);

        Integer recoveredReportCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM llm_reports", Integer.class);
        Integer remainingRawRecordCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM usage_records", Integer.class);
        assertThat(recoveredReportCount).isEqualTo(3);
        assertThat(remainingRawRecordCount).isZero();
    }

    @Test
    void teamDailyFailureKeepsRawRecordsAndRetryConverges() {
        long aliceEmployeeId = insertUser("employee.alice", "EMPLOYEE", "E001", "Alice");
        long bobEmployeeId = insertUser("employee.bob", "EMPLOYEE", "E002", "Bob");
        LocalDate reportDate = LocalDate.of(2026, 7, 8);
        insertUsageRecord(aliceEmployeeId, "Chrome", "2026-07-08T09:00:00", "2026-07-08T10:00:00");
        insertUsageRecord(bobEmployeeId, "Teams", "2026-07-08T10:00:00", "2026-07-08T10:30:00");

        given(llmProvider.generateText(anyString()))
                .willReturn("Alice daily summary", "Bob daily summary")
                .willThrow(new IllegalStateException("Team LLM failed"));

        assertThatThrownBy(() -> reportGenerationService.generateDailyReports(reportDate))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Team LLM failed");

        Integer reportCountAfterFailure = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM llm_reports", Integer.class);
        Integer rawCountAfterFailure = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM usage_records", Integer.class);
        assertThat(reportCountAfterFailure).isEqualTo(2);
        assertThat(rawCountAfterFailure).isEqualTo(2);

        reset(llmProvider);
        given(llmProvider.generateText(anyString()))
                .willReturn("Team daily summary");

        reportGenerationService.generateDailyReports(reportDate);

        Integer recoveredReportCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM llm_reports", Integer.class);
        Integer remainingRawRecordCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM usage_records", Integer.class);
        assertThat(recoveredReportCount).isEqualTo(3);
        assertThat(remainingRawRecordCount).isZero();
    }

    @Test
    void lateRawRecordsArrivingAfterArchiveSurviveRerun() {
        long aliceEmployeeId = insertUser("employee.alice", "EMPLOYEE", "E001", "Alice");
        LocalDate reportDate = LocalDate.of(2026, 7, 8);
        insertUsageRecord(aliceEmployeeId, "Chrome", "2026-07-08T09:00:00", "2026-07-08T10:00:00");

        given(llmProvider.generateText(anyString()))
                .willReturn("Alice daily summary", "Team daily summary");

        reportGenerationService.generateDailyReports(reportDate);

        Integer rawCountAfterArchive = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM usage_records", Integer.class);
        assertThat(rawCountAfterArchive).isZero();

        insertUsageRecord(aliceEmployeeId, "Slack", "2026-07-08T09:15:00", "2026-07-08T09:20:00");

        reset(llmProvider);
        reportGenerationService.generateDailyReports(reportDate);

        Integer remainingRawRecordCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM usage_records", Integer.class);
        Integer reportCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM llm_reports", Integer.class);
        assertThat(remainingRawRecordCount).isEqualTo(1);
        assertThat(reportCount).isEqualTo(2);
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

    private void insertUsageRecord(long employeeId, String appName, String startedAt, String endedAt) {
        jdbcTemplate.update(
                """
                        INSERT INTO usage_records (employee_id, app_name, started_at, ended_at, created_at)
                        VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)
                        """,
                employeeId,
                appName,
                LocalDateTime.parse(startedAt),
                LocalDateTime.parse(endedAt)
        );
    }

    private void truncateIfExists(String sql) {
        try {
            jdbcTemplate.execute(sql);
        } catch (Exception ignored) {
        }
    }
}
