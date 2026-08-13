package com.su.worklens_backend.controller;

import com.su.worklens_backend.auth.AuthenticatedUser;
import com.su.worklens_backend.dto.LlmTestResponse;
import com.su.worklens_backend.dto.ReportHistoryResponse;
import com.su.worklens_backend.exception.ManualReportGenerationDisabledException;
import com.su.worklens_backend.service.AuthService;
import com.su.worklens_backend.service.LlmProvider;
import com.su.worklens_backend.service.ReportHistoryService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RestController
public class LlmController {

    static final String TEST_PROMPT = "Please respond to this fixed WorkLens connectivity check text.";
    private static final String MANAGER_ROLE = "MANAGER";
    private static final String EMPLOYEE_ROLE = "EMPLOYEE";
    private static final Duration TEST_RESPONSE_MIN_INTERVAL = Duration.ofMinutes(10);

    private final LlmProvider llmProvider;
    private final ReportHistoryService reportHistoryService;
    private final AuthService authService;
    private final Clock clock;
    private final Map<Long, Instant> testResponseLastCallByUser = new ConcurrentHashMap<>();

    public LlmController(
            LlmProvider llmProvider,
            ReportHistoryService reportHistoryService,
            AuthService authService,
            Clock clock
    ) {
        this.llmProvider = llmProvider;
        this.reportHistoryService = reportHistoryService;
        this.authService = authService;
        this.clock = clock;
    }

    @GetMapping("/llm/test-response")
    public LlmTestResponse getTestResponse(HttpServletRequest request) {
        AuthenticatedUser authenticatedUser = authService.getAuthenticatedUser(request);
        authService.requireRole(authenticatedUser, MANAGER_ROLE);

        Instant now = clock.instant();
        Instant lastCall = testResponseLastCallByUser.get(authenticatedUser.getAuthUserId());
        if (lastCall != null && Duration.between(lastCall, now).compareTo(TEST_RESPONSE_MIN_INTERVAL) < 0) {
            throw new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "Test response is rate limited; try again later"
            );
        }
        testResponseLastCallByUser.put(authenticatedUser.getAuthUserId(), now);
        return new LlmTestResponse(llmProvider.generateText(TEST_PROMPT));
    }

    /** Test hook: resets the per-user rate-limit state. */
    public void clearTestResponseRateLimit() {
        testResponseLastCallByUser.clear();
    }

    @PostMapping("/llm/employee-report")
    public void generateEmployeeReport() {
        throw new ManualReportGenerationDisabledException();
    }

    @PostMapping("/llm/team-report")
    public void generateTeamReport() {
        throw new ManualReportGenerationDisabledException();
    }

    @GetMapping("/llm/employee-report-history")
    public List<ReportHistoryResponse> getEmployeeReportHistory(HttpServletRequest httpServletRequest) {
        AuthenticatedUser authenticatedUser = authService.getAuthenticatedUser(httpServletRequest);
        authService.requireRole(authenticatedUser, EMPLOYEE_ROLE);
        return reportHistoryService.listEmployeeReportHistory(authenticatedUser.getEmployeeId());
    }

    @GetMapping("/llm/team-report-history")
    public List<ReportHistoryResponse> getTeamReportHistory(HttpServletRequest httpServletRequest) {
        AuthenticatedUser authenticatedUser = authService.getAuthenticatedUser(httpServletRequest);
        authService.requireRole(authenticatedUser, MANAGER_ROLE);
        return reportHistoryService.listTeamReportHistory();
    }
}
