package com.su.worklens_backend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.su.worklens_backend.dto.ReportHistoryResponse;
import com.su.worklens_backend.entity.LlmReport;
import com.su.worklens_backend.mapper.LlmReportMapper;
import com.su.worklens_backend.service.ReportHistoryService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * History queries follow the archive pipeline's schema (report_scope +
 * period_type + period dates). Team reports are shared across managers, so
 * the team history no longer filters by the legacy requester column.
 */
@Service
public class ReportHistoryServiceImpl implements ReportHistoryService {

    private static final String EMPLOYEE_SCOPE = "EMPLOYEE";
    private static final String TEAM_SCOPE = "TEAM";

    private final LlmReportMapper llmReportMapper;

    public ReportHistoryServiceImpl(LlmReportMapper llmReportMapper) {
        this.llmReportMapper = llmReportMapper;
    }

    @Override
    public List<ReportHistoryResponse> listEmployeeReportHistory(Long employeeId) {
        return llmReportMapper.selectList(
                        new LambdaQueryWrapper<LlmReport>()
                                .eq(LlmReport::getReportScope, EMPLOYEE_SCOPE)
                                .eq(LlmReport::getTargetEmployeeId, employeeId)
                                .orderByDesc(LlmReport::getPeriodStartDate, LlmReport::getId)
                ).stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public List<ReportHistoryResponse> listTeamReportHistory() {
        return llmReportMapper.selectList(
                        new LambdaQueryWrapper<LlmReport>()
                                .eq(LlmReport::getReportScope, TEAM_SCOPE)
                                .orderByDesc(LlmReport::getPeriodStartDate, LlmReport::getId)
                ).stream()
                .map(this::toResponse)
                .toList();
    }

    private ReportHistoryResponse toResponse(LlmReport report) {
        return new ReportHistoryResponse(
                report.getReportType(),
                report.getPeriodType(),
                report.getSummary(),
                report.getPeriodStartedAt(),
                report.getPeriodEndedAt(),
                report.getPeriodStartDate(),
                report.getPeriodEndDate(),
                report.getCreatedAt()
        );
    }
}
