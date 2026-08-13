package com.su.worklens_backend.controller;

import com.su.worklens_backend.auth.AuthenticatedUser;
import com.su.worklens_backend.dto.LlmTestResponse;
import com.su.worklens_backend.dto.ReportHistoryResponse;
import com.su.worklens_backend.exception.ManualReportGenerationDisabledException;
import com.su.worklens_backend.service.AuthService;
import com.su.worklens_backend.service.LlmProvider;
import com.su.worklens_backend.service.ReportHistoryService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class LlmController {

    static final String TEST_PROMPT = "Please respond to this fixed WorkLens connectivity check text.";
    private static final String MANAGER_ROLE = "MANAGER";
    private static final String EMPLOYEE_ROLE = "EMPLOYEE";

    private final LlmProvider llmProvider;
    private final ReportHistoryService reportHistoryService;
    private final AuthService authService;

    public LlmController(
            LlmProvider llmProvider,
            ReportHistoryService reportHistoryService,
            AuthService authService
    ) {
        this.llmProvider = llmProvider;
        this.reportHistoryService = reportHistoryService;
        this.authService = authService;
    }

    @GetMapping("/llm/test-response")
    public LlmTestResponse getTestResponse() {
        return new LlmTestResponse(llmProvider.generateText(TEST_PROMPT));
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
        return reportHistoryService.listTeamReportHistory(authenticatedUser.getEmployeeId());
    }
}
