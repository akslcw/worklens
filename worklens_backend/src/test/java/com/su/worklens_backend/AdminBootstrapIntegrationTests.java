package com.su.worklens_backend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.su.worklens_backend.config.AdminBootstrap;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Decision 2A: the initial MANAGER account is bootstrapped from
 * WORKLENS_ADMIN_* environment variables when no manager exists.
 */
@AutoConfigureMockMvc
@SpringBootTest(properties = {
        "worklens.admin.username=admin",
        "worklens.admin.name=Administrator",
        "worklens.admin.password=AdminPass123!",
})
class AdminBootstrapIntegrationTests extends PostgresIntegrationTestSupport {

    private static final String PASSWORD_HASH = "pbkdf2_sha256$120000$d29ya2xlbnMtc2FsdC0wMQ==$y7dDc5YjVRKR+v1GlPwEumSMa6Wa4bMH0h23Tk8Tx64=";

    @Autowired
    private AdminBootstrap adminBootstrap;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void bootstrapsManagerWhenNoneExistsAndLoginWorks() throws Exception {
        truncate("TRUNCATE TABLE detail_access_audit_logs RESTART IDENTITY CASCADE");
        truncate("TRUNCATE TABLE detail_access_requests RESTART IDENTITY CASCADE");
        truncate("TRUNCATE TABLE usage_records RESTART IDENTITY CASCADE");
        truncate("TRUNCATE TABLE llm_reports RESTART IDENTITY CASCADE");
        truncate("TRUNCATE TABLE auth_tokens RESTART IDENTITY CASCADE");
        truncate("TRUNCATE TABLE auth_users RESTART IDENTITY CASCADE");
        truncate("TRUNCATE TABLE auth_login_attempts RESTART IDENTITY CASCADE");
        truncate("TRUNCATE TABLE employees RESTART IDENTITY CASCADE");

        adminBootstrap.bootstrapInitialAdministrator();

        Map<String, Object> manager = jdbcTemplate.queryForMap(
                """
                        SELECT au.username, au.role, au.must_change_password, au.employee_id, e.employee_no
                        FROM auth_users au
                        JOIN employees e ON e.id = au.employee_id
                        WHERE au.username = 'admin'
                        """
        );
        assertThat(manager.get("role")).isEqualTo("MANAGER");
        assertThat(manager.get("must_change_password")).isEqualTo(true);
        assertThat(manager.get("employee_no")).isEqualTo("admin");

        MvcResult loginResult = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "admin",
                                  "password": "AdminPass123!"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("MANAGER"))
                .andExpect(jsonPath("$.mustChangePassword").value(true))
                .andReturn();
        JsonNode payload = objectMapper.readTree(loginResult.getResponse().getContentAsString());
        assertThat(payload.path("token").asText()).isNotBlank();
    }

    @Test
    void skipsBootstrapWhenManagerAlreadyExists() {
        truncate("TRUNCATE TABLE detail_access_audit_logs RESTART IDENTITY CASCADE");
        truncate("TRUNCATE TABLE detail_access_requests RESTART IDENTITY CASCADE");
        truncate("TRUNCATE TABLE usage_records RESTART IDENTITY CASCADE");
        truncate("TRUNCATE TABLE llm_reports RESTART IDENTITY CASCADE");
        truncate("TRUNCATE TABLE auth_tokens RESTART IDENTITY CASCADE");
        truncate("TRUNCATE TABLE auth_users RESTART IDENTITY CASCADE");
        truncate("TRUNCATE TABLE auth_login_attempts RESTART IDENTITY CASCADE");
        truncate("TRUNCATE TABLE employees RESTART IDENTITY CASCADE");

        jdbcTemplate.update(
                "INSERT INTO employees (name, employee_no, created_at) VALUES ('Existing Manager', 'M001', CURRENT_TIMESTAMP)"
        );
        Long employeeId = jdbcTemplate.queryForObject(
                "SELECT id FROM employees WHERE employee_no = 'M001'",
                Long.class
        );
        jdbcTemplate.update(
                "INSERT INTO auth_users (username, password_hash, role, employee_id, created_at) VALUES ('M001', ?, 'MANAGER', ?, CURRENT_TIMESTAMP)",
                PASSWORD_HASH,
                employeeId
        );

        adminBootstrap.bootstrapInitialAdministrator();

        Integer managerCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM auth_users WHERE role = 'MANAGER'",
                Integer.class
        );
        assertThat(managerCount).isEqualTo(1);
        Integer adminCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM auth_users WHERE username = 'admin'",
                Integer.class
        );
        assertThat(adminCount).isZero();
    }

    private void truncate(String sql) {
        try {
            jdbcTemplate.execute(sql);
        } catch (Exception ignored) {
        }
    }
}
