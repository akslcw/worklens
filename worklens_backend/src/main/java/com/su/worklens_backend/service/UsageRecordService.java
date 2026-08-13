package com.su.worklens_backend.service;

import com.su.worklens_backend.auth.AuthenticatedUser;
import com.su.worklens_backend.dto.TeamUsageSummaryResponse;
import com.su.worklens_backend.dto.UsageRecordRequest;
import com.su.worklens_backend.dto.UsageRecordResponse;
import com.su.worklens_backend.dto.UsageViewResponse;

import java.time.LocalDate;
import java.util.List;

public interface UsageRecordService {

    List<UsageRecordResponse> listUsageRecords(AuthenticatedUser authenticatedUser);

    UsageViewResponse getUsageView(AuthenticatedUser authenticatedUser, LocalDate date, int page, int pageSize);

    UsageViewResponse getUsageViewForEmployee(Long employeeId, LocalDate date, int page, int pageSize);

    UsageRecordResponse createUsageRecord(UsageRecordRequest request, AuthenticatedUser authenticatedUser);

    TeamUsageSummaryResponse getTeamUsageSummary(AuthenticatedUser authenticatedUser);
}
