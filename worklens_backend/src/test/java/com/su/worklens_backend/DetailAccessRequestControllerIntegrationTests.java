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
import org.springframework.web.server.ResponseStatusException;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@SpringBootTest
class DetailAccessRequestControllerIntegrationTests extends PostgresIntegrationTestSupport {

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
        truncateIfExists("TRUNCATE TABLE llm_reports RESTART IDENTITY CASCADE");
        truncateIfExists("TRUNCATE TABLE usage_records RESTART IDENTITY CASCADE");
        truncateIfExists("TRUNCATE TABLE detail_access_audit_logs RESTART IDENTITY CASCADE");
        truncateIfExists("TRUNCATE TABLE detail_access_requests RESTART IDENTITY CASCADE");
        truncateIfExists("TRUNCATE TABLE auth_tokens RESTART IDENTITY CASCADE");
        truncateIfExists("TRUNCATE TABLE auth_login_attempts RESTART IDENTITY CASCADE");
        truncateIfExists("TRUNCATE TABLE auth_users RESTART IDENTITY CASCADE");
        truncateIfExists("TRUNCATE TABLE employees RESTART IDENTITY CASCADE");
    }

    @Test
    void createDetailAccessRequestRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/detail-access-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestPayload(2L)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void employeeCannotCreateDetailAccessRequest() throws Exception {
        insertUser("employee.alice", PASSWORD_HASH, "EMPLOYEE", "E001", "Alice");
        insertEmployee("E002", "Bob");
        String employeeToken = loginAndReadToken("employee.alice", PASSWORD);

        mockMvc.perform(post("/detail-access-requests")
                        .header("Authorization", "Bearer " + employeeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestPayload(2L)))
                .andExpect(status().isForbidden());
    }

    @Test
    void managerCanCreatePendingDetailAccessRequest() throws Exception {
        long managerEmployeeId = insertUser("manager", PASSWORD_HASH, "MANAGER", "M001", "Manager User");
        long targetEmployeeId = insertEmployee("E002", "Bob");
        String managerToken = loginAndReadToken("manager", PASSWORD);

        MvcResult result = mockMvc.perform(post("/detail-access-requests")
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestPayload(targetEmployeeId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.requesterEmployeeId").value(managerEmployeeId))
                .andExpect(jsonPath("$.targetEmployeeId").value(targetEmployeeId))
                .andExpect(jsonPath("$.reason").value("Quarterly compliance review"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.processedAt").doesNotExist())
                .andExpect(jsonPath("$.processedByEmployeeId").doesNotExist())
                .andReturn();

        long requestId = readId(result);
        Map<String, Object> persisted = jdbcTemplate.queryForMap(
                """
                        SELECT requester_employee_id, target_employee_id, reason, status, created_at, processed_at, processed_by_employee_id
                        FROM detail_access_requests
                        WHERE id = ?
                        """,
                requestId
        );

        assertThat(((Number) persisted.get("requester_employee_id")).longValue()).isEqualTo(managerEmployeeId);
        assertThat(((Number) persisted.get("target_employee_id")).longValue()).isEqualTo(targetEmployeeId);
        assertThat(persisted.get("reason")).isEqualTo("Quarterly compliance review");
        assertThat(persisted.get("status")).isEqualTo("PENDING");
        assertThat(((Timestamp) persisted.get("created_at")).toLocalDateTime()).isBeforeOrEqualTo(LocalDateTime.now());
        assertThat(persisted.get("processed_at")).isNull();
        assertThat(persisted.get("processed_by_employee_id")).isNull();
    }

    @Test
    void decisionRequiresAuthentication() throws Exception {
        long requesterEmployeeId = insertEmployee("M001", "Manager User");
        long targetEmployeeId = insertEmployee("E001", "Alice");
        long requestId = insertDetailAccessRequest(requesterEmployeeId, targetEmployeeId, "Quarterly compliance review", "PENDING");

        mockMvc.perform(patch("/detail-access-requests/{id}/decision", requestId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validDecisionPayload("APPROVED")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void managerCannotApproveDetailAccessRequest() throws Exception {
        long managerEmployeeId = insertUser("manager", PASSWORD_HASH, "MANAGER", "M001", "Manager User");
        long targetEmployeeId = insertUser("employee.bob", PASSWORD_HASH, "EMPLOYEE", "E002", "Bob");
        long requestId = insertDetailAccessRequest(managerEmployeeId, targetEmployeeId, "Quarterly compliance review", "PENDING");
        String managerToken = loginAndReadToken("manager", PASSWORD);

        mockMvc.perform(patch("/detail-access-requests/{id}/decision", requestId)
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validDecisionPayload("APPROVED")))
                .andExpect(status().isForbidden());
    }

    @Test
    void otherEmployeeCannotApproveDetailAccessRequest() throws Exception {
        long managerEmployeeId = insertUser("manager", PASSWORD_HASH, "MANAGER", "M001", "Manager User");
        long targetEmployeeId = insertUser("employee.bob", PASSWORD_HASH, "EMPLOYEE", "E002", "Bob");
        insertUser("employee.alice", PASSWORD_HASH, "EMPLOYEE", "E003", "Alice");
        long requestId = insertDetailAccessRequest(managerEmployeeId, targetEmployeeId, "Quarterly compliance review", "PENDING");
        String aliceToken = loginAndReadToken("employee.alice", PASSWORD);

        mockMvc.perform(patch("/detail-access-requests/{id}/decision", requestId)
                        .header("Authorization", "Bearer " + aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validDecisionPayload("APPROVED")))
                .andExpect(status().isForbidden());
    }

    @Test
    void targetEmployeeCanApproveOwnDetailAccessRequest() throws Exception {
        long managerEmployeeId = insertUser("manager", PASSWORD_HASH, "MANAGER", "M001", "Manager User");
        long targetEmployeeId = insertUser("employee.bob", PASSWORD_HASH, "EMPLOYEE", "E002", "Bob");
        long requestId = insertDetailAccessRequest(managerEmployeeId, targetEmployeeId, "Quarterly compliance review", "PENDING");
        String bobToken = loginAndReadToken("employee.bob", PASSWORD);

        mockMvc.perform(patch("/detail-access-requests/{id}/decision", requestId)
                        .header("Authorization", "Bearer " + bobToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validDecisionPayload("APPROVED")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(requestId))
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.processedAt").isNotEmpty())
                .andExpect(jsonPath("$.processedByEmployeeId").value(targetEmployeeId));

        Map<String, Object> persisted = jdbcTemplate.queryForMap(
                "SELECT status, processed_at, processed_by_employee_id FROM detail_access_requests WHERE id = ?",
                requestId
        );

        assertThat(persisted.get("status")).isEqualTo("APPROVED");
        assertThat(persisted.get("processed_at")).isNotNull();
        assertThat(((Number) persisted.get("processed_by_employee_id")).longValue()).isEqualTo(targetEmployeeId);
    }

    @Test
    void targetEmployeeCanRejectOwnDetailAccessRequest() throws Exception {
        long managerEmployeeId = insertUser("manager", PASSWORD_HASH, "MANAGER", "M001", "Manager User");
        long targetEmployeeId = insertUser("employee.bob", PASSWORD_HASH, "EMPLOYEE", "E002", "Bob");
        long requestId = insertDetailAccessRequest(managerEmployeeId, targetEmployeeId, "Quarterly compliance review", "PENDING");
        String bobToken = loginAndReadToken("employee.bob", PASSWORD);

        mockMvc.perform(patch("/detail-access-requests/{id}/decision", requestId)
                        .header("Authorization", "Bearer " + bobToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validDecisionPayload("REJECTED")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(requestId))
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.processedAt").isNotEmpty())
                .andExpect(jsonPath("$.processedByEmployeeId").value(targetEmployeeId));
    }

    @Test
    void requesterManagerCanViewApprovedUsageRecordsOnceAndAuditIt() throws Exception {
        long managerEmployeeId = insertUser("manager", PASSWORD_HASH, "MANAGER", "M001", "Manager User");
        long targetEmployeeId = insertUser("employee.bob", PASSWORD_HASH, "EMPLOYEE", "E002", "Bob");
        insertUsageRecord(targetEmployeeId, "Slack", "2026-07-04T09:00:00", "2026-07-04T09:30:00");
        insertUsageRecord(targetEmployeeId, "Chrome", "2026-07-04T10:00:00", "2026-07-04T10:45:00");
        long requestId = insertProcessedDetailAccessRequest(managerEmployeeId, targetEmployeeId, "Quarterly compliance review", "APPROVED", targetEmployeeId);
        String managerToken = loginAndReadToken("manager", PASSWORD);

        mockMvc.perform(get("/detail-access-requests/{id}/usage-records", requestId)
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].appName").value("Slack"))
                .andExpect(jsonPath("$[1].appName").value("Chrome"))
                .andExpect(jsonPath("$[0].employeeId").doesNotExist())
                .andExpect(jsonPath("$[0].username").doesNotExist());

        Map<String, Object> requestState = jdbcTemplate.queryForMap(
                "SELECT status FROM detail_access_requests WHERE id = ?",
                requestId
        );
        assertThat(requestState.get("status")).isEqualTo("USED");

        List<Map<String, Object>> auditLogs = jdbcTemplate.queryForList(
                """
                        SELECT detail_access_request_id, viewer_employee_id, target_employee_id, viewed_at
                        FROM detail_access_audit_logs
                        WHERE detail_access_request_id = ?
                        """,
                requestId
        );
        assertThat(auditLogs).hasSize(1);
        assertThat(((Number) auditLogs.get(0).get("viewer_employee_id")).longValue()).isEqualTo(managerEmployeeId);
        assertThat(((Number) auditLogs.get(0).get("target_employee_id")).longValue()).isEqualTo(targetEmployeeId);
        assertThat(auditLogs.get(0).get("viewed_at")).isNotNull();
    }

    @Test
    void requesterManagerCanViewApprovedUsageViewAsLiveCardsOnceAndAuditIt() throws Exception {
        long managerEmployeeId = insertUser("manager", PASSWORD_HASH, "MANAGER", "M001", "Manager User");
        long targetEmployeeId = insertUser("employee.bob", PASSWORD_HASH, "EMPLOYEE", "E002", "Bob");
        long otherEmployeeId = insertUser("employee.alice", PASSWORD_HASH, "EMPLOYEE", "E003", "Alice");
        insertUsageRecord(targetEmployeeId, "Chrome", "2026-07-08T09:00:00", "2026-07-08T10:00:00");
        insertUsageRecord(targetEmployeeId, "Chrome", "2026-07-08T10:00:10", "2026-07-08T10:15:00");
        insertUsageRecord(targetEmployeeId, "Slack", "2026-07-08T11:00:00", "2026-07-08T11:30:00");
        insertUsageRecord(otherEmployeeId, "Teams", "2026-07-08T09:00:00", "2026-07-08T12:00:00");
        long requestId = insertProcessedDetailAccessRequest(managerEmployeeId, targetEmployeeId, "Quarterly compliance review", "APPROVED", targetEmployeeId);
        String managerToken = loginAndReadToken("manager", PASSWORD);

        mockMvc.perform(get("/detail-access-requests/{id}/usage-view", requestId)
                        .queryParam("date", "2026-07-08")
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("LIVE_USAGE"))
                .andExpect(jsonPath("$.date").value("2026-07-08"))
                .andExpect(jsonPath("$.totalApps").value(2))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].appName").value("Chrome"))
                .andExpect(jsonPath("$.items[0].durationSeconds").value(4500))
                .andExpect(jsonPath("$.items[0].segments.length()").value(1))
                .andExpect(jsonPath("$.items[1].appName").value("Slack"))
                .andExpect(jsonPath("$.items[1].durationSeconds").value(1800))
                .andExpect(jsonPath("$.items[0].employeeId").doesNotExist())
                .andExpect(jsonPath("$.items[0].username").doesNotExist())
                .andExpect(jsonPath("$.report").doesNotExist());

        assertRequestUsedAndAudited(requestId, managerEmployeeId, targetEmployeeId);
    }

    @Test
    void requesterManagerCanViewApprovedUsageViewAsRolledReportOnceAndAuditIt() throws Exception {
        long managerEmployeeId = insertUser("manager", PASSWORD_HASH, "MANAGER", "M001", "Manager User");
        long targetEmployeeId = insertUser("employee.bob", PASSWORD_HASH, "EMPLOYEE", "E002", "Bob");
        insertEmployeeReport(targetEmployeeId, "EMPLOYEE", "WEEKLY", "2026-07-06", "2026-07-12", """
                [
                  {"appName":"Chrome","durationSeconds":5400,"durationMinutes":90,"ratio":0.75},
                  {"appName":"Slack","durationSeconds":1800,"durationMinutes":30,"ratio":0.25}
                ]
                """, "Weekly usage summary");
        long requestId = insertProcessedDetailAccessRequest(managerEmployeeId, targetEmployeeId, "Quarterly compliance review", "APPROVED", targetEmployeeId);
        String managerToken = loginAndReadToken("manager", PASSWORD);

        mockMvc.perform(get("/detail-access-requests/{id}/usage-view", requestId)
                        .queryParam("date", "2026-07-08")
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("REPORT"))
                .andExpect(jsonPath("$.date").value("2026-07-08"))
                .andExpect(jsonPath("$.items").doesNotExist())
                .andExpect(jsonPath("$.report.reportScope").value("EMPLOYEE"))
                .andExpect(jsonPath("$.report.periodType").value("WEEKLY"))
                .andExpect(jsonPath("$.report.periodStartDate").value("2026-07-06"))
                .andExpect(jsonPath("$.report.periodEndDate").value("2026-07-12"))
                .andExpect(jsonPath("$.report.summary").value("Weekly usage summary"))
                .andExpect(jsonPath("$.report.details.length()").value(2))
                .andExpect(jsonPath("$.report.details[0].appName").value("Chrome"))
                .andExpect(jsonPath("$.report.details[0].durationSeconds").value(5400))
                .andExpect(jsonPath("$.report.targetEmployeeId").doesNotExist())
                .andExpect(jsonPath("$.report.requesterEmployeeId").doesNotExist());

        assertRequestUsedAndAudited(requestId, managerEmployeeId, targetEmployeeId);
    }

    @Test
    void requesterManagerCannotViewWithUsedAuthorization() throws Exception {
        long managerEmployeeId = insertUser("manager", PASSWORD_HASH, "MANAGER", "M001", "Manager User");
        long targetEmployeeId = insertUser("employee.bob", PASSWORD_HASH, "EMPLOYEE", "E002", "Bob");
        long requestId = insertProcessedDetailAccessRequest(managerEmployeeId, targetEmployeeId, "Quarterly compliance review", "USED", targetEmployeeId);
        String managerToken = loginAndReadToken("manager", PASSWORD);

        mockMvc.perform(get("/detail-access-requests/{id}/usage-records", requestId)
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isForbidden())
                .andExpect(result -> {
                    assertThat(result.getResolvedException()).isInstanceOf(ResponseStatusException.class);
                    ResponseStatusException exception = (ResponseStatusException) result.getResolvedException();
                    assertThat(exception.getReason()).isEqualTo("Detail access authorization has already been used");
                });
    }

    @Test
    void requesterManagerCannotViewWithExpiredOrMissingAuthorization() throws Exception {
        insertUser("manager", PASSWORD_HASH, "MANAGER", "M001", "Manager User");
        String managerToken = loginAndReadToken("manager", PASSWORD);

        mockMvc.perform(get("/detail-access-requests/{id}/usage-records", 9999L)
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isForbidden())
                .andExpect(result -> {
                    assertThat(result.getResolvedException()).isInstanceOf(ResponseStatusException.class);
                    ResponseStatusException exception = (ResponseStatusException) result.getResolvedException();
                    assertThat(exception.getReason()).isEqualTo("Detail access authorization is expired or does not exist");
                });
    }

    @Test
    void requesterManagerCanListAccessAuditLogsForOwnRequest() throws Exception {
        long managerEmployeeId = insertUser("manager", PASSWORD_HASH, "MANAGER", "M001", "Manager User");
        long targetEmployeeId = insertUser("employee.bob", PASSWORD_HASH, "EMPLOYEE", "E002", "Bob");
        long requestId = insertProcessedDetailAccessRequest(managerEmployeeId, targetEmployeeId, "Quarterly compliance review", "USED", targetEmployeeId);
        insertAccessAuditLog(requestId, managerEmployeeId, targetEmployeeId, "2026-07-04T11:00:00");
        String managerToken = loginAndReadToken("manager", PASSWORD);

        mockMvc.perform(get("/detail-access-requests/{id}/access-logs", requestId)
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].detailAccessRequestId").value(requestId))
                .andExpect(jsonPath("$[0].viewerEmployeeId").value(managerEmployeeId))
                .andExpect(jsonPath("$[0].targetEmployeeId").value(targetEmployeeId))
                .andExpect(jsonPath("$[0].viewedAt").value("2026-07-04T11:00:00"));
    }

    @Test
    void listOwnDetailAccessRequestsRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/detail-access-requests"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void employeeCannotListDetailAccessRequests() throws Exception {
        insertUser("employee.alice", PASSWORD_HASH, "EMPLOYEE", "E001", "Alice");
        String employeeToken = loginAndReadToken("employee.alice", PASSWORD);

        mockMvc.perform(get("/detail-access-requests")
                        .header("Authorization", "Bearer " + employeeToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void managerCanListOnlyOwnDetailAccessRequests() throws Exception {
        long managerAliceEmployeeId = insertUser("manager.alice", PASSWORD_HASH, "MANAGER", "M001", "Manager Alice");
        long managerBobEmployeeId = insertUser("manager.bob", PASSWORD_HASH, "MANAGER", "M002", "Manager Bob");
        long targetEmployeeId = insertUser("employee.bob", PASSWORD_HASH, "EMPLOYEE", "E001", "Employee Bob");
        insertDetailAccessRequest(managerAliceEmployeeId, targetEmployeeId, "Quarterly compliance review", "PENDING");
        insertProcessedDetailAccessRequest(managerAliceEmployeeId, targetEmployeeId, "Incident follow-up", "APPROVED", targetEmployeeId);
        insertDetailAccessRequest(managerBobEmployeeId, targetEmployeeId, "Another manager request", "PENDING");
        String aliceToken = loginAndReadToken("manager.alice", PASSWORD);

        mockMvc.perform(get("/detail-access-requests")
                        .header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].requesterEmployeeId").value(managerAliceEmployeeId))
                .andExpect(jsonPath("$[0].reason").value("Quarterly compliance review"))
                .andExpect(jsonPath("$[0].status").value("PENDING"))
                .andExpect(jsonPath("$[1].requesterEmployeeId").value(managerAliceEmployeeId))
                .andExpect(jsonPath("$[1].reason").value("Incident follow-up"))
                .andExpect(jsonPath("$[1].status").value("APPROVED"));
    }

    @Test
    void listRequestsTargetingMeRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/detail-access-requests/targeting-me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void managerCannotListRequestsTargetingEmployee() throws Exception {
        insertUser("manager.alice", PASSWORD_HASH, "MANAGER", "M001", "Manager Alice");
        String managerToken = loginAndReadToken("manager.alice", PASSWORD);

        mockMvc.perform(get("/detail-access-requests/targeting-me")
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void employeeCanListOnlyRequestsTargetingSelfWithViewStatus() throws Exception {
        long managerAliceEmployeeId = insertUser("manager.alice", PASSWORD_HASH, "MANAGER", "M001", "Manager Alice");
        long managerBobEmployeeId = insertUser("manager.bob", PASSWORD_HASH, "MANAGER", "M002", "Manager Bob");
        long managerCarolEmployeeId = insertUser("manager.carol", PASSWORD_HASH, "MANAGER", "M003", "Manager Carol");
        long employeeBobId = insertUser("employee.bob", PASSWORD_HASH, "EMPLOYEE", "E001", "Employee Bob");
        long employeeAliceId = insertUser("employee.alice", PASSWORD_HASH, "EMPLOYEE", "E002", "Employee Alice");
        insertDetailAccessRequest(managerAliceEmployeeId, employeeBobId, "Pending review", "PENDING");
        insertProcessedDetailAccessRequest(managerBobEmployeeId, employeeBobId, "Approved but unread", "APPROVED", employeeBobId);
        long usedRequestId = insertProcessedDetailAccessRequest(managerCarolEmployeeId, employeeBobId, "Approved and viewed", "USED", employeeBobId);
        insertAccessAuditLog(usedRequestId, managerCarolEmployeeId, employeeBobId, "2026-07-04T12:00:00");
        insertProcessedDetailAccessRequest(managerAliceEmployeeId, employeeBobId, "Rejected request", "REJECTED", employeeBobId);
        insertDetailAccessRequest(managerAliceEmployeeId, employeeAliceId, "Other employee request", "PENDING");
        String bobToken = loginAndReadToken("employee.bob", PASSWORD);

        mockMvc.perform(get("/detail-access-requests/targeting-me")
                        .header("Authorization", "Bearer " + bobToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[0].requesterEmployeeId").value(managerAliceEmployeeId))
                .andExpect(jsonPath("$[0].requesterEmployeeName").value("Manager Alice"))
                .andExpect(jsonPath("$[0].reason").value("Pending review"))
                .andExpect(jsonPath("$[0].status").value("PENDING"))
                .andExpect(jsonPath("$[0].hasBeenViewed").value(false))
                .andExpect(jsonPath("$[1].requesterEmployeeId").value(managerBobEmployeeId))
                .andExpect(jsonPath("$[1].requesterEmployeeName").value("Manager Bob"))
                .andExpect(jsonPath("$[1].reason").value("Approved but unread"))
                .andExpect(jsonPath("$[1].status").value("APPROVED"))
                .andExpect(jsonPath("$[1].hasBeenViewed").value(false))
                .andExpect(jsonPath("$[2].requesterEmployeeId").value(managerCarolEmployeeId))
                .andExpect(jsonPath("$[2].requesterEmployeeName").value("Manager Carol"))
                .andExpect(jsonPath("$[2].reason").value("Approved and viewed"))
                .andExpect(jsonPath("$[2].status").value("USED"))
                .andExpect(jsonPath("$[2].hasBeenViewed").value(true))
                .andExpect(jsonPath("$[2].viewedAt").value("2026-07-04T12:00:00"))
                .andExpect(jsonPath("$[3].requesterEmployeeId").value(managerAliceEmployeeId))
                .andExpect(jsonPath("$[3].requesterEmployeeName").value("Manager Alice"))
                .andExpect(jsonPath("$[3].reason").value("Rejected request"))
                .andExpect(jsonPath("$[3].status").value("REJECTED"))
                .andExpect(jsonPath("$[3].hasBeenViewed").value(false));
    }

    @Test
    void concurrentViewsOfApprovedAuthorizationAllowExactlyOneViewer() throws Exception {
        long managerEmployeeId = insertUser("manager", PASSWORD_HASH, "MANAGER", "M001", "Manager User");
        long targetEmployeeId = insertUser("employee.bob", PASSWORD_HASH, "EMPLOYEE", "E002", "Bob");
        insertUsageRecord(targetEmployeeId, "Slack", "2026-07-04T09:00:00", "2026-07-04T09:30:00");
        long requestId = insertProcessedDetailAccessRequest(managerEmployeeId, targetEmployeeId, "Quarterly compliance review", "APPROVED", targetEmployeeId);
        String managerToken = loginAndReadToken("manager", PASSWORD);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);
            Callable<Integer> viewRequest = () -> {
                ready.countDown();
                start.await(10, TimeUnit.SECONDS);
                MvcResult result = mockMvc.perform(get("/detail-access-requests/{id}/usage-records", requestId)
                                .header("Authorization", "Bearer " + managerToken))
                        .andReturn();
                return result.getResponse().getStatus();
            };
            Future<Integer> firstView = executor.submit(viewRequest);
            Future<Integer> secondView = executor.submit(viewRequest);

            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<Integer> statuses = List.of(
                    firstView.get(30, TimeUnit.SECONDS),
                    secondView.get(30, TimeUnit.SECONDS)
            );
            assertThat(statuses).containsExactlyInAnyOrder(200, 403);
        } finally {
            executor.shutdownNow();
        }

        assertRequestUsedAndAudited(requestId, managerEmployeeId, targetEmployeeId);
    }

    private long insertUser(String username, String passwordHash, String role, String employeeNo, String name) {
        long employeeId = insertEmployee(employeeNo, name);
        jdbcTemplate.update(
                "INSERT INTO auth_users (username, password_hash, role, employee_id, created_at) VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)",
                username,
                passwordHash,
                role,
                employeeId
        );
        return employeeId;
    }

    private long insertEmployee(String employeeNo, String name) {
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
        assertThat(employeeId).isNotNull();
        return employeeId;
    }

    private long insertDetailAccessRequest(long requesterEmployeeId, long targetEmployeeId, String reason, String status) {
        jdbcTemplate.update(
                """
                        INSERT INTO detail_access_requests (
                            requester_employee_id,
                            target_employee_id,
                            reason,
                            status,
                            created_at,
                            processed_at,
                            processed_by_employee_id
                        ) VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP, NULL, NULL)
                        """,
                requesterEmployeeId,
                targetEmployeeId,
                reason,
                status
        );
        Long requestId = jdbcTemplate.queryForObject(
                """
                        SELECT id
                        FROM detail_access_requests
                        WHERE requester_employee_id = ? AND target_employee_id = ? AND reason = ?
                        ORDER BY id DESC
                        LIMIT 1
                        """,
                Long.class,
                requesterEmployeeId,
                targetEmployeeId,
                reason
        );
        assertThat(requestId).isNotNull();
        return requestId;
    }

    private long insertProcessedDetailAccessRequest(long requesterEmployeeId, long targetEmployeeId, String reason, String status,
                                                    long processedByEmployeeId) {
        jdbcTemplate.update(
                """
                        INSERT INTO detail_access_requests (
                            requester_employee_id,
                            target_employee_id,
                            reason,
                            status,
                            created_at,
                            processed_at,
                            processed_by_employee_id
                        ) VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, ?)
                        """,
                requesterEmployeeId,
                targetEmployeeId,
                reason,
                status,
                processedByEmployeeId
        );
        Long requestId = jdbcTemplate.queryForObject(
                """
                        SELECT id
                        FROM detail_access_requests
                        WHERE requester_employee_id = ? AND target_employee_id = ? AND reason = ?
                        ORDER BY id DESC
                        LIMIT 1
                        """,
                Long.class,
                requesterEmployeeId,
                targetEmployeeId,
                reason
        );
        assertThat(requestId).isNotNull();
        return requestId;
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

    private void insertAccessAuditLog(long requestId, long viewerEmployeeId, long targetEmployeeId, String viewedAt) {
        jdbcTemplate.update(
                """
                        INSERT INTO detail_access_audit_logs (
                            detail_access_request_id,
                            viewer_employee_id,
                            target_employee_id,
                            viewed_at
                        ) VALUES (?, ?, ?, ?)
                        """,
                requestId,
                viewerEmployeeId,
                targetEmployeeId,
                LocalDateTime.parse(viewedAt)
        );
    }

    private void insertEmployeeReport(
            long employeeId,
            String reportScope,
            String periodType,
            String periodStartDate,
            String periodEndDate,
            String detailJson,
            String summary
    ) {
        jdbcTemplate.update(
                """
                        INSERT INTO llm_reports (
                            report_type, requester_employee_id, target_employee_id, summary,
                            period_started_at, period_ended_at, created_at, report_scope, period_type,
                            period_start_date, period_end_date, detail_json, source_layer, source_count, generated_at
                        )
                        VALUES (?, ?, ?, ?, ?::date, ?::date, CURRENT_TIMESTAMP,
                                ?, ?, ?::date, ?::date, ?::jsonb, 'DAILY_REPORTS', 1, CURRENT_TIMESTAMP)
                        """,
                reportScope + "_" + periodType,
                employeeId,
                employeeId,
                summary,
                periodStartDate,
                periodEndDate,
                reportScope,
                periodType,
                periodStartDate,
                periodEndDate,
                detailJson
        );
    }

    private void assertRequestUsedAndAudited(long requestId, long managerEmployeeId, long targetEmployeeId) {
        Map<String, Object> requestState = jdbcTemplate.queryForMap(
                "SELECT status FROM detail_access_requests WHERE id = ?",
                requestId
        );
        assertThat(requestState.get("status")).isEqualTo("USED");

        List<Map<String, Object>> auditLogs = jdbcTemplate.queryForList(
                """
                        SELECT detail_access_request_id, viewer_employee_id, target_employee_id, viewed_at
                        FROM detail_access_audit_logs
                        WHERE detail_access_request_id = ?
                        """,
                requestId
        );
        assertThat(auditLogs).hasSize(1);
        assertThat(((Number) auditLogs.get(0).get("viewer_employee_id")).longValue()).isEqualTo(managerEmployeeId);
        assertThat(((Number) auditLogs.get(0).get("target_employee_id")).longValue()).isEqualTo(targetEmployeeId);
        assertThat(auditLogs.get(0).get("viewed_at")).isNotNull();
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

    private long readId(MvcResult result) throws Exception {
        JsonNode jsonNode = objectMapper.readTree(result.getResponse().getContentAsString());
        return jsonNode.path("id").asLong();
    }

    private String readToken(MvcResult result) throws Exception {
        JsonNode jsonNode = objectMapper.readTree(result.getResponse().getContentAsString());
        return jsonNode.path("token").asText();
    }

    private String validRequestPayload(long targetEmployeeId) {
        return """
                {
                  "targetEmployeeId": %d,
                  "reason": "Quarterly compliance review"
                }
                """.formatted(targetEmployeeId);
    }

    private String validDecisionPayload(String decision) {
        return """
                {
                  "decision": "%s"
                }
                """.formatted(decision);
    }

    private void truncateIfExists(String sql) {
        try {
            jdbcTemplate.execute(sql);
        } catch (Exception ignored) {
        }
    }
}
