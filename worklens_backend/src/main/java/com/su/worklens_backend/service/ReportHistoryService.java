package com.su.worklens_backend.service;

import com.su.worklens_backend.dto.ReportHistoryResponse;

import java.util.List;

public interface ReportHistoryService {

    List<ReportHistoryResponse> listEmployeeReportHistory(Long employeeId);

    List<ReportHistoryResponse> listTeamReportHistory();
}
