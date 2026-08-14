package com.su.worklens_backend;

import com.su.worklens_backend.controller.LlmController;
import com.su.worklens_backend.exception.LlmProviderException;
import com.su.worklens_backend.exception.LlmProviderTimeoutException;
import com.su.worklens_backend.service.LlmProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@SpringBootTest
class LlmFailureHandlingIntegrationTests extends PostgresIntegrationTestSupport {

    private static final String PASSWORD = "Password123!";
    private static final String PASSWORD_HASH = "pbkdf2_sha256$120000$d29ya2xlbnMtc2FsdC0wMQ==$y7dDc5YjVRKR+v1GlPwEumSMa6Wa4bMH0h23Tk8Tx64=";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private LlmController llmController;

    @MockitoBean
    private LlmProvider llmProvider;

    @BeforeEach
    void cleanDatabase() {
        llmController.clearTestResponseRateLimit();
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
    void timeoutFailureReturns504WithClearMessage() throws Exception {
        insertUser("manager", PASSWORD_HASH, "MANAGER", "M001", "Manager User");
        String managerToken = loginAndReadToken("manager", PASSWORD);
        given(llmProvider.generateText(anyString()))
                .willThrow(new LlmProviderTimeoutException("DeepSeek API request timed out"));

        mockMvc.perform(get("/llm/test-response")
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.code").value("LLM_TIMEOUT"))
                .andExpect(jsonPath("$.message").value("DeepSeek API request timed out"));
    }

    @Test
    void providerFailureReturns502WithClearMessage() throws Exception {
        insertUser("manager", PASSWORD_HASH, "MANAGER", "M001", "Manager User");
        String managerToken = loginAndReadToken("manager", PASSWORD);
        given(llmProvider.generateText(anyString()))
                .willThrow(new LlmProviderException("DeepSeek API request failed"));

        mockMvc.perform(get("/llm/test-response")
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("LLM_PROVIDER_ERROR"))
                .andExpect(jsonPath("$.message").value("DeepSeek API request failed"));
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
