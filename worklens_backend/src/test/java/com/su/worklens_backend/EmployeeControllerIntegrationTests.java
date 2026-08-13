package com.su.worklens_backend;

import com.fasterxml.jackson.databind.JsonNode;
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
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@SpringBootTest
class EmployeeControllerIntegrationTests extends PostgresIntegrationTestSupport {

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
    void createEmployeeRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/employees")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Alice",
                                  "employeeNo": "E001"
                                }
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void employeeRoleCannotCreateEmployee() throws Exception {
        insertUser("employee.alice", PASSWORD_HASH, "EMPLOYEE", "E900", "Alice");
        String employeeToken = loginAndReadToken("employee.alice", PASSWORD);

        mockMvc.perform(post("/employees")
                        .header("Authorization", "Bearer " + employeeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Alice",
                                  "employeeNo": "E001"
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void managerCanCreateEmployee() throws Exception {
        String managerToken = insertManagerAndLogin();

        MvcResult createResult = mockMvc.perform(post("/employees")
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Alice",
                                  "employeeNo": "E001"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("Alice"))
                .andExpect(jsonPath("$.employeeNo").value("E001"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.initialPassword").isNotEmpty())
                .andReturn();

        String initialPassword = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .path("initialPassword")
                .asText();
        assertThat(initialPassword)
                .hasSizeGreaterThanOrEqualTo(16)
                .isNotEqualTo("worklens123");

        Integer accountCount = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM auth_users au
                        JOIN employees e ON e.id = au.employee_id
                        WHERE au.username = 'E001'
                          AND au.role = 'EMPLOYEE'
                          AND e.employee_no = 'E001'
                          AND au.must_change_password = TRUE
                        """,
                Integer.class
        );
        assertThat(accountCount).isEqualTo(1);

        login("E001", initialPassword)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("E001"))
                .andExpect(jsonPath("$.role").value("EMPLOYEE"))
                .andExpect(jsonPath("$.mustChangePassword").value(true));
    }

    @Test
    void employeeWithInitialPasswordMustChangePasswordBeforeBusinessAccess() throws Exception {
        String managerToken = insertManagerAndLogin();
        JsonNode createdEmployee = createEmployeePayload(managerToken, "Alice", "E001");
        String initialPassword = createdEmployee.path("initialPassword").asText();
        String employeeToken = loginAndReadToken("E001", initialPassword);

        mockMvc.perform(get("/usage-records")
                        .header("Authorization", "Bearer " + employeeToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/auth/change-password")
                        .header("Authorization", "Bearer " + employeeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "currentPassword": "%s",
                                  "newPassword": "Changed123!"
                                }
                                """.formatted(initialPassword)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mustChangePassword").value(false));

        login("E001", "Changed123!")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mustChangePassword").value(false));
    }

    @Test
    void managerCanResetEmployeePasswordToUniqueTemporaryPassword() throws Exception {
        String managerToken = insertManagerAndLogin();
        JsonNode createdEmployee = createEmployeePayload(managerToken, "Alice", "E001");
        long employeeId = createdEmployee.path("id").asLong();
        String initialPassword = createdEmployee.path("initialPassword").asText();
        String employeeToken = loginAndReadToken("E001", initialPassword);
        changePassword(employeeToken, initialPassword, "Changed123!");

        MvcResult resetResult = mockMvc.perform(post("/employees/{id}/reset-password", employeeId)
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("E001"))
                .andExpect(jsonPath("$.initialPassword").isNotEmpty())
                .andExpect(jsonPath("$.mustChangePassword").value(true))
                .andReturn();

        String resetPassword = objectMapper.readTree(resetResult.getResponse().getContentAsString())
                .path("initialPassword")
                .asText();
        assertThat(resetPassword)
                .hasSizeGreaterThanOrEqualTo(16)
                .isNotEqualTo(initialPassword)
                .isNotEqualTo("worklens123");

        login("E001", resetPassword)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mustChangePassword").value(true));
    }

    @Test
    void employeeRoleCannotListEmployees() throws Exception {
        insertUser("employee.alice", PASSWORD_HASH, "EMPLOYEE", "E900", "Alice");
        String employeeToken = loginAndReadToken("employee.alice", PASSWORD);

        mockMvc.perform(get("/employees")
                        .header("Authorization", "Bearer " + employeeToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void managerCanListEmployees() throws Exception {
        String managerToken = insertManagerAndLogin();
        createEmployee(managerToken, "Alice", "E001");
        createEmployee(managerToken, "Bob", "E002");

        MvcResult result = mockMvc.perform(get("/employees")
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        assertThat(responseBody).contains("\"name\":\"Alice\"");
        assertThat(responseBody).contains("\"employeeNo\":\"E001\"");
        assertThat(responseBody).contains("\"name\":\"Bob\"");
        assertThat(responseBody).contains("\"employeeNo\":\"E002\"");
    }

    @Test
    void managerCanGetEmployeeById() throws Exception {
        String managerToken = insertManagerAndLogin();
        long employeeId = createEmployee(managerToken, "Alice", "E001");

        mockMvc.perform(get("/employees/{id}", employeeId)
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(employeeId))
                .andExpect(jsonPath("$.name").value("Alice"))
                .andExpect(jsonPath("$.employeeNo").value("E001"));
    }

    @Test
    void managerCanUpdateEmployee() throws Exception {
        String managerToken = insertManagerAndLogin();
        long employeeId = createEmployee(managerToken, "Alice", "E001");

        mockMvc.perform(put("/employees/{id}", employeeId)
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Alice Zhang",
                                  "employeeNo": "E009"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(employeeId))
                .andExpect(jsonPath("$.name").value("Alice Zhang"))
                .andExpect(jsonPath("$.employeeNo").value("E009"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty());
    }

    @Test
    void managerCanDeleteEmployee() throws Exception {
        String managerToken = insertManagerAndLogin();
        long employeeId = createEmployee(managerToken, "Alice", "E001");

        mockMvc.perform(delete("/employees/{id}", employeeId)
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/employees/{id}", employeeId)
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isNotFound());

        Map<String, Object> persisted = jdbcTemplate.queryForMap(
                "SELECT deleted_at FROM employees WHERE id = ?",
                employeeId
        );
        assertThat(persisted.get("deleted_at")).isNotNull();

        Integer activeCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM employees WHERE deleted_at IS NULL",
                Integer.class
        );
        assertThat(activeCount).isEqualTo(1);
    }

    @Test
    void deletingEmployeeSoftDeletesAndPreservesUsageAndAccessData() throws Exception {
        String managerToken = insertManagerAndLogin();
        long employeeId = createEmployee(managerToken, "Alice", "E001");
        String employeeToken = loginAndReadToken("E001", readInitialPassword(managerToken, "E001"));

        jdbcTemplate.update(
                "INSERT INTO usage_records (employee_id, app_name, started_at, ended_at, created_at) VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)",
                employeeId, "Chrome", java.sql.Timestamp.valueOf("2026-07-08 09:00:00"), java.sql.Timestamp.valueOf("2026-07-08 10:00:00")
        );
        jdbcTemplate.update(
                "INSERT INTO detail_access_requests (requester_employee_id, target_employee_id, reason, status, created_at) VALUES (?, ?, ?, 'PENDING', CURRENT_TIMESTAMP)",
                employeeId, employeeId, "Audit record retention"
        );

        mockMvc.perform(delete("/employees/{id}", employeeId)
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isNoContent());

        Integer usageCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM usage_records WHERE employee_id = ?",
                Integer.class,
                employeeId
        );
        Integer requestCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM detail_access_requests WHERE target_employee_id = ?",
                Integer.class,
                employeeId
        );
        assertThat(usageCount).isEqualTo(1);
        assertThat(requestCount).isEqualTo(1);

        Map<String, Object> account = jdbcTemplate.queryForMap(
                "SELECT employee_id FROM auth_users WHERE username = 'E001'"
        );
        assertThat(account.get("employee_id")).isNull();

        mockMvc.perform(get("/usage-records")
                        .header("Authorization", "Bearer " + employeeToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void updatingEmployeeNoSyncsLoginUsername() throws Exception {
        String managerToken = insertManagerAndLogin();
        long employeeId = createEmployee(managerToken, "Alice", "E001");
        String initialPassword = readInitialPassword(managerToken, "E001");

        mockMvc.perform(put("/employees/{id}", employeeId)
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Alice Zhang",
                                  "employeeNo": "E009"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.employeeNo").value("E009"));

        login("E009", initialPassword)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("E009"));
        login("E001", initialPassword)
                .andExpect(status().isUnauthorized());
    }

    @Test
    void duplicateEmployeeNoReturnsConflict() throws Exception {
        String managerToken = insertManagerAndLogin();
        createEmployee(managerToken, "Alice", "E001");
        long bobEmployeeId = createEmployee(managerToken, "Bob", "E002");

        mockMvc.perform(post("/employees")
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Carol",
                                  "employeeNo": "E001"
                                }
                                """))
                .andExpect(status().isConflict());

        mockMvc.perform(put("/employees/{id}", bobEmployeeId)
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Bob",
                                  "employeeNo": "E001"
                                }
                                """))
                .andExpect(status().isConflict());
    }

    private String readInitialPassword(String managerToken, String employeeNo) throws Exception {
        List<Map<String, Object>> created = jdbcTemplate.queryForList(
                "SELECT e.id FROM employees e WHERE e.employee_no = ? AND e.deleted_at IS NULL",
                employeeNo
        );
        assertThat(created).hasSize(1);
        MvcResult resetResult = mockMvc.perform(post("/employees/{id}/reset-password", created.get(0).get("id"))
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(resetResult.getResponse().getContentAsString())
                .path("initialPassword")
                .asText();
    }

    private void insertUser(String username, String passwordHash, String role, String employeeNo, String name) {
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
    }

    private String insertManagerAndLogin() throws Exception {
        insertUser("manager", PASSWORD_HASH, "MANAGER", "M001", "Manager User");
        return loginAndReadToken("manager", PASSWORD);
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

    private void changePassword(String token, String currentPassword, String newPassword) throws Exception {
        mockMvc.perform(post("/auth/change-password")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "currentPassword": "%s",
                                  "newPassword": "%s"
                                }
                                """.formatted(currentPassword, newPassword)))
                .andExpect(status().isOk());
    }

    private long createEmployee(String token, String name, String employeeNo) throws Exception {
        return createEmployeePayload(token, name, employeeNo).path("id").asLong();
    }

    private JsonNode createEmployeePayload(String token, String name, String employeeNo) throws Exception {
        MvcResult result = mockMvc.perform(post("/employees")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "%s",
                                  "employeeNo": "%s"
                                }
                                """.formatted(name, employeeNo)))
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private String readToken(MvcResult result) throws Exception {
        JsonNode jsonNode = objectMapper.readTree(result.getResponse().getContentAsString());
        return jsonNode.path("token").asText();
    }
}
