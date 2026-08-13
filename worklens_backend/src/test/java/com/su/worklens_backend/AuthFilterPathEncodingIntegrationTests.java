package com.su.worklens_backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.net.URI;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Regression tests for the path-encoding authorization bypass (C1).
 *
 * The servlet container keeps the raw percent-encoded URI in
 * HttpServletRequest#getRequestURI(), while servlet mapping and Spring MVC's
 * PathPatternParser match the decoded path. A filter that compares the raw URI
 * against path prefixes therefore lets GET /%65mployees skip the role check
 * and still reach the /employees handler. These tests assert that encoded
 * variants are subject to the same role checks as the plain paths.
 */
@AutoConfigureMockMvc
@SpringBootTest
class AuthFilterPathEncodingIntegrationTests extends PostgresIntegrationTestSupport {

    private static final String PASSWORD = "Password123!";
    private static final String PASSWORD_HASH = "pbkdf2_sha256$120000$d29ya2xlbnMtc2FsdC0wMQ==$y7dDc5YjVRKR+v1GlPwEumSMa6Wa4bMH0h23Tk8Tx64=";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabase() {
        try {
            jdbcTemplate.execute("TRUNCATE TABLE usage_records RESTART IDENTITY CASCADE");
            jdbcTemplate.execute("TRUNCATE TABLE auth_tokens RESTART IDENTITY CASCADE");
            jdbcTemplate.execute("TRUNCATE TABLE auth_users RESTART IDENTITY CASCADE");
            jdbcTemplate.execute("TRUNCATE TABLE employees RESTART IDENTITY CASCADE");
        } catch (Exception ignored) {
        }
    }

    @Test
    void employeeRoleCannotListEmployeesWithEncodedPath() throws Exception {
        String employeeToken = loginAs("employee.alice", PASSWORD_HASH, "EMPLOYEE", "E900", "Alice");

        mockMvc.perform(get(URI.create("/%65mployees"))
                        .header("Authorization", "Bearer " + employeeToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void employeeRoleCannotListEmployeesWithPartiallyEncodedPath() throws Exception {
        String employeeToken = loginAs("employee.alice", PASSWORD_HASH, "EMPLOYEE", "E900", "Alice");

        mockMvc.perform(get(URI.create("/emplo%79ees"))
                        .header("Authorization", "Bearer " + employeeToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void employeeRoleCannotCreateEmployeeWithEncodedPath() throws Exception {
        String employeeToken = loginAs("employee.alice", PASSWORD_HASH, "EMPLOYEE", "E900", "Alice");

        mockMvc.perform(post(URI.create("/emplo%79ees"))
                        .header("Authorization", "Bearer " + employeeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Mallory",
                                  "employeeNo": "E777"
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void employeeRoleCannotReadTeamSummaryWithEncodedPath() throws Exception {
        String employeeToken = loginAs("employee.alice", PASSWORD_HASH, "EMPLOYEE", "E900", "Alice");

        mockMvc.perform(get(URI.create("/team-usage-summar%79"))
                        .header("Authorization", "Bearer " + employeeToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void managerCanListEmployeesWithEncodedPath() throws Exception {
        String managerToken = loginAs("manager", PASSWORD_HASH, "MANAGER", "M001", "Manager User");

        mockMvc.perform(get(URI.create("/%65mployees"))
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk());
    }

    @Test
    void encodedSlashPathIsRejected() throws Exception {
        String employeeToken = loginAs("employee.alice", PASSWORD_HASH, "EMPLOYEE", "E900", "Alice");

        mockMvc.perform(get(URI.create("/%2Femployees"))
                        .header("Authorization", "Bearer " + employeeToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    void doubleEncodedPathIsRejected() throws Exception {
        String employeeToken = loginAs("employee.alice", PASSWORD_HASH, "EMPLOYEE", "E900", "Alice");

        mockMvc.perform(get(URI.create("/%2565mployees"))
                        .header("Authorization", "Bearer " + employeeToken))
                .andExpect(status().isBadRequest());
    }

    private String loginAs(String username, String passwordHash, String role, String employeeNo, String name) throws Exception {
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

        MvcResult loginResult = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "%s",
                                  "password": "%s"
                                }
                                """.formatted(username, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readTree(loginResult.getResponse().getContentAsString())
                .path("token")
                .asText();
    }
}
