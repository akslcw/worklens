package com.su.worklens_backend;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@SpringBootTest
class TeamUsageSummaryControllerIntegrationTests extends PostgresIntegrationTestSupport {

    private static final String PASSWORD = "Password123!";
    private static final String PASSWORD_HASH = "pbkdf2_sha256$120000$d29ya2xlbnMtc2FsdC0wMQ==$y7dDc5YjVRKR+v1GlPwEumSMa6Wa4bMH0h23Tk8Tx64=";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabase() {
        truncateIfExists("TRUNCATE TABLE usage_records RESTART IDENTITY CASCADE");
        truncateIfExists("TRUNCATE TABLE auth_tokens RESTART IDENTITY CASCADE");
        truncateIfExists("TRUNCATE TABLE auth_login_attempts RESTART IDENTITY CASCADE");
        truncateIfExists("TRUNCATE TABLE auth_users RESTART IDENTITY CASCADE");
        truncateIfExists("TRUNCATE TABLE employees RESTART IDENTITY CASCADE");
    }

    @Test
    void getTeamUsageSummaryRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/team-usage-summary"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void employeeCannotGetTeamUsageSummary() throws Exception {
        insertUser("employee.alice", PASSWORD_HASH, "EMPLOYEE", "E001", "Alice");
        String employeeToken = loginAndReadToken("employee.alice", PASSWORD);

        mockMvc.perform(get("/team-usage-summary")
                        .header("Authorization", "Bearer " + employeeToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void managerCanGetAggregatedTeamUsageSummaryForTodayOnly() throws Exception {
        insertUser("manager", PASSWORD_HASH, "MANAGER", "M001", "Manager User");
        long aliceEmployeeId = insertUser("employee.alice", PASSWORD_HASH, "EMPLOYEE", "E001", "Alice");
        long bobEmployeeId = insertUser("employee.bob", PASSWORD_HASH, "EMPLOYEE", "E002", "Bob");
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Hong_Kong"));
        LocalDate yesterday = today.minusDays(1);
        insertUsageRecord(aliceEmployeeId, "Slack", today.atTime(9, 0), today.atTime(9, 30));
        insertUsageRecord(aliceEmployeeId, "Chrome", today.atTime(10, 0), today.atTime(11, 0));
        insertUsageRecord(bobEmployeeId, "Slack", today.atTime(11, 0), today.atTime(11, 45));
        insertUsageRecord(aliceEmployeeId, "Teams", yesterday.atTime(9, 0), yesterday.atTime(12, 0));

        String managerToken = loginAndReadToken("manager", PASSWORD);

        MvcResult result = mockMvc.perform(get("/team-usage-summary")
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.teamAverageUsageMinutes").value(67.5))
                .andExpect(jsonPath("$.totalUsageMinutes").value(135))
                .andExpect(jsonPath("$.activeEmployeeCount").value(2))
                .andExpect(jsonPath("$.appUsageRatios[0].appName").value("Slack"))
                .andExpect(jsonPath("$.appUsageRatios[0].usageMinutes").value(75))
                .andExpect(jsonPath("$.appUsageRatios[0].usageRatio").value(0.5556))
                .andExpect(jsonPath("$.appUsageRatios[1].appName").value("Chrome"))
                .andExpect(jsonPath("$.appUsageRatios[1].usageMinutes").value(60))
                .andExpect(jsonPath("$.appUsageRatios[1].usageRatio").value(0.4444))
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        assertThat(responseBody).doesNotContain("authUserId");
        assertThat(responseBody).doesNotContain("employeeId");
        assertThat(responseBody).doesNotContain("username");
        assertThat(responseBody).doesNotContain("Teams");
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

    private void insertUsageRecord(long employeeId, String appName, LocalDateTime startedAt, LocalDateTime endedAt) {
        jdbcTemplate.update(
                """
                        INSERT INTO usage_records (employee_id, app_name, started_at, ended_at, created_at)
                        VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)
                        """,
                employeeId,
                appName,
                startedAt,
                endedAt
        );
    }

    private String loginAndReadToken(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "%s",
                                  "password": "%s"
                                }
                                """.formatted(username, password)))
                .andExpect(status().isOk())
                .andReturn();
        String responseBody = result.getResponse().getContentAsString();
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
