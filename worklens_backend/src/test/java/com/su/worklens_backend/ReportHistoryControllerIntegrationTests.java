package com.su.worklens_backend;

import com.su.worklens_backend.service.LlmProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@SpringBootTest
class ReportHistoryControllerIntegrationTests extends PostgresIntegrationTestSupport {

    private static final String PASSWORD = "Password123!";
    private static final String PASSWORD_HASH = "pbkdf2_sha256$120000$d29ya2xlbnMtc2FsdC0wMQ==$y7dDc5YjVRKR+v1GlPwEumSMa6Wa4bMH0h23Tk8Tx64=";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

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
    void employeeReportHistoryRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/llm/employee-report-history"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void employeeCanListOwnArchivedReportHistoryAcrossAllPeriodTypes() throws Exception {
        long aliceEmployeeId = insertUser("employee.alice", PASSWORD_HASH, "EMPLOYEE", "E001", "Alice");
        long bobEmployeeId = insertUser("employee.bob", PASSWORD_HASH, "EMPLOYEE", "E002", "Bob");
        String aliceToken = loginAndReadToken("employee.alice", PASSWORD);
        insertArchivedReport("EMPLOYEE", "DAILY", aliceEmployeeId, "Alice daily summary",
                LocalDate.of(2026, 7, 8), LocalDate.of(2026, 7, 8), LocalDateTime.parse("2026-07-08T23:56:00"));
        insertArchivedReport("EMPLOYEE", "WEEKLY", aliceEmployeeId, "Alice weekly summary",
                LocalDate.of(2026, 7, 6), LocalDate.of(2026, 7, 12), LocalDateTime.parse("2026-07-13T00:30:00"));
        insertArchivedReport("EMPLOYEE", "MONTHLY", aliceEmployeeId, "Alice monthly summary",
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31), LocalDateTime.parse("2026-08-01T01:00:00"));
        insertArchivedReport("EMPLOYEE", "WEEKLY", bobEmployeeId, "Bob weekly summary",
                LocalDate.of(2026, 7, 6), LocalDate.of(2026, 7, 12), LocalDateTime.parse("2026-07-13T00:35:00"));

        mockMvc.perform(get("/llm/employee-report-history")
                        .header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].summary").value("Alice daily summary"))
                .andExpect(jsonPath("$[0].periodType").value("DAILY"))
                .andExpect(jsonPath("$[0].periodStartDate").value("2026-07-08"))
                .andExpect(jsonPath("$[1].summary").value("Alice weekly summary"))
                .andExpect(jsonPath("$[1].periodType").value("WEEKLY"))
                .andExpect(jsonPath("$[2].summary").value("Alice monthly summary"))
                .andExpect(jsonPath("$[2].periodType").value("MONTHLY"))
                .andExpect(jsonPath("$[2].periodStartDate").value("2026-07-01"))
                .andExpect(jsonPath("$[2].periodEndDate").value("2026-07-31"))
                .andExpect(jsonPath("$[0].requesterEmployeeId").doesNotExist())
                .andExpect(jsonPath("$[0].targetEmployeeId").doesNotExist());
    }

    @Test
    void managerCannotReadEmployeeReportHistory() throws Exception {
        insertUser("manager", PASSWORD_HASH, "MANAGER", "M001", "Manager User");
        String managerToken = loginAndReadToken("manager", PASSWORD);

        mockMvc.perform(get("/llm/employee-report-history")
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void managerCanListArchivedTeamReportHistory() throws Exception {
        insertUser("manager.one", PASSWORD_HASH, "MANAGER", "M001", "Manager One");
        insertUser("manager.two", PASSWORD_HASH, "MANAGER", "M002", "Manager Two");
        String managerOneToken = loginAndReadToken("manager.one", PASSWORD);
        insertArchivedReport("TEAM", "DAILY", null, "First team daily",
                LocalDate.of(2026, 7, 8), LocalDate.of(2026, 7, 8), LocalDateTime.parse("2026-07-08T23:57:00"));
        insertArchivedReport("TEAM", "WEEKLY", null, "Team weekly",
                LocalDate.of(2026, 7, 6), LocalDate.of(2026, 7, 12), LocalDateTime.parse("2026-07-13T00:31:00"));

        mockMvc.perform(get("/llm/team-report-history")
                        .header("Authorization", "Bearer " + managerOneToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].summary").value("First team daily"))
                .andExpect(jsonPath("$[0].periodType").value("DAILY"))
                .andExpect(jsonPath("$[1].summary").value("Team weekly"))
                .andExpect(jsonPath("$[1].periodType").value("WEEKLY"))
                .andExpect(jsonPath("$[1].periodStartDate").value("2026-07-06"))
                .andExpect(jsonPath("$[1].periodEndDate").value("2026-07-12"))
                .andExpect(jsonPath("$[0].requesterEmployeeId").doesNotExist())
                .andExpect(jsonPath("$[0].targetEmployeeId").doesNotExist());
    }

    @Test
    void employeeCannotReadTeamReportHistory() throws Exception {
        insertUser("employee.alice", PASSWORD_HASH, "EMPLOYEE", "E001", "Alice");
        String employeeToken = loginAndReadToken("employee.alice", PASSWORD);

        mockMvc.perform(get("/llm/team-report-history")
                        .header("Authorization", "Bearer " + employeeToken))
                .andExpect(status().isForbidden());
    }

    private long insertUser(String username, String passwordHash, String role, String employeeNo, String name) {
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
                passwordHash,
                role,
                employeeId
        );
        assertThat(employeeId).isNotNull();
        return employeeId;
    }

    private void insertArchivedReport(
            String reportScope,
            String periodType,
            Long targetEmployeeId,
            String summary,
            LocalDate periodStartDate,
            LocalDate periodEndDate,
            LocalDateTime createdAt
    ) {
        jdbcTemplate.update(
                """
                        INSERT INTO llm_reports (
                            report_type,
                            requester_employee_id,
                            target_employee_id,
                            summary,
                            period_started_at,
                            period_ended_at,
                            created_at,
                            report_scope,
                            period_type,
                            period_start_date,
                            period_end_date,
                            detail_json,
                            source_layer,
                            source_count,
                            generated_at
                        )
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, '[]'::jsonb, 'RAW_USAGE', 1, ?)
                        """,
                reportScope + "_" + periodType,
                targetEmployeeId,
                targetEmployeeId,
                summary,
                periodStartDate.atStartOfDay(),
                periodEndDate.atTime(23, 59, 59),
                createdAt,
                reportScope,
                periodType,
                periodStartDate,
                periodEndDate,
                createdAt
        );
    }

    private String loginAndReadToken(String username, String password) throws Exception {
        String responseBody = mockMvc.perform(post("/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "%s",
                                  "password": "%s"
                                }
                                """.formatted(username, password)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        int tokenStart = responseBody.indexOf("\"token\":\"");
        int valueStart = tokenStart + 9;
        int valueEnd = responseBody.indexOf("\"", valueStart);
        return responseBody.substring(valueStart, valueEnd);
    }

    private void truncateIfExists(String sql) {
        try {
            jdbcTemplate.execute(sql);
        } catch (Exception ignored) {
        }
    }
}
