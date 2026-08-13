package com.su.worklens_backend.controller;

import com.su.worklens_backend.auth.AuthenticatedUser;
import com.su.worklens_backend.dto.TeamUsageSummaryResponse;
import com.su.worklens_backend.service.AuthService;
import com.su.worklens_backend.service.UsageRecordService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TeamUsageSummaryController {

    private final UsageRecordService usageRecordService;
    private final AuthService authService;

    public TeamUsageSummaryController(UsageRecordService usageRecordService, AuthService authService) {
        this.usageRecordService = usageRecordService;
        this.authService = authService;
    }

    @GetMapping("/team-usage-summary")
    public TeamUsageSummaryResponse getTeamUsageSummary(HttpServletRequest httpServletRequest) {
        AuthenticatedUser authenticatedUser = authService.getAuthenticatedUser(httpServletRequest);
        return usageRecordService.getTeamUsageSummary(authenticatedUser);
    }
}
