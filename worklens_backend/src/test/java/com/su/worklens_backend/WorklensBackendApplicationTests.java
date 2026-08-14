package com.su.worklens_backend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@SpringBootTest
class WorklensBackendApplicationTests extends PostgresIntegrationTestSupport {

    private static final String PASSWORD = "Password123!";
    private static final String PASSWORD_HASH = "pbkdf2_sha256$120000$d29ya2xlbnMtc2FsdC0wMQ==$y7dDc5YjVRKR+v1GlPwEumSMa6Wa4bMH0h23Tk8Tx64=";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void cleanDatabase() {
        try {
            jdbcTemplate.execute("TRUNCATE TABLE employees RESTART IDENTITY CASCADE");
            jdbcTemplate.execute("TRUNCATE TABLE auth_tokens RESTART IDENTITY CASCADE");
            jdbcTemplate.execute("TRUNCATE TABLE auth_users RESTART IDENTITY CASCADE");
            jdbcTemplate.execute("TRUNCATE TABLE auth_login_attempts");
        } catch (Exception ignored) {
        }
    }

    @Test
    void loginAttemptSchemaExists() {
        Integer tableCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE table_schema = 'public'
                  AND table_name = 'auth_login_attempts'
                """,
                Integer.class
        );

        assertThat(tableCount).isEqualTo(1);
    }

    @Test
    void healthEndpointReturnsOkWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(content().string("OK"));
    }

    @Test
    void loginReturnsTokenAndCurrentUserForManager() throws Exception {
        long employeeId = insertUser("manager", PASSWORD_HASH, "MANAGER", "M001", "Manager User");

        MvcResult loginResult = login("manager", PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.role").value("MANAGER"))
                .andExpect(jsonPath("$.username").value("manager"))
                .andExpect(jsonPath("$.displayName").value("Manager User"))
                .andReturn();

        String token = readToken(loginResult);

        mockMvc.perform(get("/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.employeeId").value(employeeId))
                .andExpect(jsonPath("$.username").value("manager"))
                .andExpect(jsonPath("$.displayName").value("Manager User"))
                .andExpect(jsonPath("$.role").value("MANAGER"));
    }

    @Test
    void authMeRecognizesEmployeeRole() throws Exception {
        long employeeId = insertUser("employee.alice", PASSWORD_HASH, "EMPLOYEE", "E001", "Alice");

        MvcResult loginResult = login("employee.alice", PASSWORD)
                .andExpect(status().isOk())
                .andReturn();

        String token = readToken(loginResult);

        mockMvc.perform(get("/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.employeeId").value(employeeId))
                .andExpect(jsonPath("$.username").value("employee.alice"))
                .andExpect(jsonPath("$.displayName").value("Alice"))
                .andExpect(jsonPath("$.role").value("EMPLOYEE"));
    }

    @Test
    void loginRejectsWrongPassword() throws Exception {
        insertUser("manager", PASSWORD_HASH, "MANAGER", "M001", "Manager User");

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  \"username\": \"manager\",
                                  \"password\": \"wrong-password\"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.message").value("Invalid username or password"));
    }

    @Test
    void loginRejectsUnknownUsernameWithSamePublicError() throws Exception {
        login("missing-user", "wrong-password")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.message").value("Invalid username or password"));
    }

    @Test
    void loginLocksUsernameAfterFiveConsecutiveFailures() throws Exception {
        insertUser("manager", PASSWORD_HASH, "MANAGER", "M001", "Manager User");

        for (int attempt = 1; attempt < 5; attempt++) {
            login("manager", "wrong-password-" + attempt)
                    .andExpect(status().isUnauthorized());
        }

        login("manager", "wrong-password-5")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("LOGIN_LOCKED"))
                .andExpect(jsonPath("$.message").value("Too many failed login attempts. Try again in 15 minutes."));
    }

    @Test
    void successfulLoginClearsPreviousFailures() throws Exception {
        insertUser("manager", PASSWORD_HASH, "MANAGER", "M001", "Manager User");

        for (int attempt = 1; attempt <= 3; attempt++) {
            login("manager", "wrong-password-" + attempt)
                    .andExpect(status().isUnauthorized());
        }
        Integer failuresBeforeSuccess = jdbcTemplate.queryForObject(
                "SELECT COALESCE(MAX(failed_attempts), 0) FROM auth_login_attempts WHERE username = ?",
                Integer.class,
                "manager"
        );
        assertThat(failuresBeforeSuccess).isEqualTo(3);

        login("manager", PASSWORD).andExpect(status().isOk());

        Integer rowsAfterSuccess = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM auth_login_attempts WHERE username = ?",
                Integer.class,
                "manager"
        );
        assertThat(rowsAfterSuccess).isZero();
    }

    @Test
    void expiredLoginLockAllowsAuthenticationAndClearsAttemptState() throws Exception {
        insertUser("manager", PASSWORD_HASH, "MANAGER", "M001", "Manager User");
        jdbcTemplate.update(
                """
                INSERT INTO auth_login_attempts (username, failed_attempts, locked_until, updated_at)
                VALUES (?, 5, CURRENT_TIMESTAMP - INTERVAL '1 minute', CURRENT_TIMESTAMP)
                """,
                "manager"
        );

        login("manager", PASSWORD).andExpect(status().isOk());

        Integer remainingRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM auth_login_attempts WHERE username = ?",
                Integer.class,
                "manager"
        );
        assertThat(remainingRows).isZero();
    }

    @Test
    void newestLoginInvalidatesPreviousToken() throws Exception {
        insertUser("manager", PASSWORD_HASH, "MANAGER", "M001", "Manager User");
        String firstToken = loginAndReadToken("manager", PASSWORD);
        String secondToken = loginAndReadToken("manager", PASSWORD);

        mockMvc.perform(get("/auth/me")
                        .header("Authorization", "Bearer " + firstToken))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/auth/me")
                        .header("Authorization", "Bearer " + secondToken))
                .andExpect(status().isOk());
    }

    @Test
    void authMeRequiresToken() throws Exception {
        mockMvc.perform(get("/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginRejectsUserWithoutEmployeeBinding() throws Exception {
        jdbcTemplate.update(
                "INSERT INTO auth_users (username, password_hash, role, created_at) VALUES (?, ?, ?, CURRENT_TIMESTAMP)",
                "orphan.user",
                PASSWORD_HASH,
                "EMPLOYEE"
        );

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "orphan.user",
                                  "password": "Password123!"
                                }
                                """))
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
        return employeeId;
    }

    private ResultActions login(String username, String password) throws Exception {
        return mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "username": "%s",
                          "password": "%s"
                        }
                        """.formatted(username, password)));
    }

    private String loginAndReadToken(String username, String password) throws Exception {
        return readToken(login(username, password).andReturn());
    }

    private String readToken(MvcResult result) throws Exception {
        JsonNode jsonNode = objectMapper.readTree(result.getResponse().getContentAsString());
        return jsonNode.path("token").asText();
    }
}
