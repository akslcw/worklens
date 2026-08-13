package com.su.worklens_backend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.su.worklens_backend.auth.AuthenticatedUser;
import com.su.worklens_backend.dto.DetailAccessAuditLogResponse;
import com.su.worklens_backend.dto.DetailAccessRequestCreateRequest;
import com.su.worklens_backend.dto.DetailAccessRequestDecisionRequest;
import com.su.worklens_backend.dto.DetailAccessRequestResponse;
import com.su.worklens_backend.dto.EmployeeDetailAccessRequestResponse;
import com.su.worklens_backend.dto.UsageRecordResponse;
import com.su.worklens_backend.dto.UsageViewResponse;
import com.su.worklens_backend.entity.DetailAccessAuditLog;
import com.su.worklens_backend.entity.DetailAccessRequest;
import com.su.worklens_backend.entity.Employee;
import com.su.worklens_backend.entity.UsageRecord;
import com.su.worklens_backend.mapper.DetailAccessAuditLogMapper;
import com.su.worklens_backend.mapper.DetailAccessRequestMapper;
import com.su.worklens_backend.mapper.EmployeeMapper;
import com.su.worklens_backend.mapper.UsageRecordMapper;
import com.su.worklens_backend.service.AuthService;
import com.su.worklens_backend.service.DetailAccessRequestService;
import com.su.worklens_backend.service.UsageRecordService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class DetailAccessRequestServiceImpl implements DetailAccessRequestService {

    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_APPROVED = "APPROVED";
    private static final String STATUS_REJECTED = "REJECTED";
    private static final String STATUS_USED = "USED";
    private static final String AUTHORIZATION_USED_MESSAGE = "Detail access authorization has already been used";
    private static final String AUTHORIZATION_MISSING_MESSAGE = "Detail access authorization is expired or does not exist";
    private static final String MANAGER_ROLE = "MANAGER";
    private static final String EMPLOYEE_ROLE = "EMPLOYEE";

    private final DetailAccessAuditLogMapper detailAccessAuditLogMapper;
    private final DetailAccessRequestMapper detailAccessRequestMapper;
    private final EmployeeMapper employeeMapper;
    private final UsageRecordMapper usageRecordMapper;
    private final UsageRecordService usageRecordService;
    private final AuthService authService;

    public DetailAccessRequestServiceImpl(DetailAccessAuditLogMapper detailAccessAuditLogMapper,
                                          DetailAccessRequestMapper detailAccessRequestMapper,
                                          EmployeeMapper employeeMapper,
                                          UsageRecordMapper usageRecordMapper,
                                          UsageRecordService usageRecordService,
                                          AuthService authService) {
        this.detailAccessAuditLogMapper = detailAccessAuditLogMapper;
        this.detailAccessRequestMapper = detailAccessRequestMapper;
        this.employeeMapper = employeeMapper;
        this.usageRecordMapper = usageRecordMapper;
        this.usageRecordService = usageRecordService;
        this.authService = authService;
    }

    @Override
    public DetailAccessRequestResponse createDetailAccessRequest(DetailAccessRequestCreateRequest request, AuthenticatedUser authenticatedUser) {
        authService.requireRole(authenticatedUser, MANAGER_ROLE);
        Employee targetEmployee = employeeMapper.selectById(request.getTargetEmployeeId());
        if (targetEmployee == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Target employee not found");
        }

        DetailAccessRequest detailAccessRequest = new DetailAccessRequest();
        detailAccessRequest.setRequesterEmployeeId(authenticatedUser.getEmployeeId());
        detailAccessRequest.setTargetEmployeeId(targetEmployee.getId());
        detailAccessRequest.setReason(request.getReason().trim());
        detailAccessRequest.setStatus(STATUS_PENDING);
        detailAccessRequest.setCreatedAt(LocalDateTime.now());
        detailAccessRequestMapper.insert(detailAccessRequest);

        return toResponse(detailAccessRequest);
    }

    @Override
    public DetailAccessRequestResponse decideDetailAccessRequest(Long requestId, DetailAccessRequestDecisionRequest request, AuthenticatedUser authenticatedUser) {
        authService.requireRole(authenticatedUser, EMPLOYEE_ROLE);
        DetailAccessRequest detailAccessRequest = detailAccessRequestMapper.selectById(requestId);
        if (detailAccessRequest == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Detail access request not found");
        }
        if (!authenticatedUser.getEmployeeId().equals(detailAccessRequest.getTargetEmployeeId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the target employee can decide this request");
        }
        if (!STATUS_PENDING.equals(detailAccessRequest.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Detail access request has already been processed");
        }

        String normalizedDecision = request.getDecision().trim().toUpperCase();
        if (!STATUS_APPROVED.equals(normalizedDecision) && !STATUS_REJECTED.equals(normalizedDecision)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Decision must be APPROVED or REJECTED");
        }

        detailAccessRequest.setStatus(normalizedDecision);
        detailAccessRequest.setProcessedAt(LocalDateTime.now());
        detailAccessRequest.setProcessedByEmployeeId(authenticatedUser.getEmployeeId());
        detailAccessRequestMapper.updateById(detailAccessRequest);

        return toResponse(detailAccessRequest);
    }

    @Override
    public List<DetailAccessRequestResponse> listOwnDetailAccessRequests(AuthenticatedUser authenticatedUser) {
        authService.requireRole(authenticatedUser, MANAGER_ROLE);
        return detailAccessRequestMapper.selectList(
                        new LambdaQueryWrapper<DetailAccessRequest>()
                                .eq(DetailAccessRequest::getRequesterEmployeeId, authenticatedUser.getEmployeeId())
                                .orderByAsc(DetailAccessRequest::getCreatedAt, DetailAccessRequest::getId)
                ).stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public List<EmployeeDetailAccessRequestResponse> listRequestsTargetingCurrentEmployee(AuthenticatedUser authenticatedUser) {
        authService.requireRole(authenticatedUser, EMPLOYEE_ROLE);
        List<DetailAccessRequest> requests = detailAccessRequestMapper.selectList(
                new LambdaQueryWrapper<DetailAccessRequest>()
                        .eq(DetailAccessRequest::getTargetEmployeeId, authenticatedUser.getEmployeeId())
                        .orderByAsc(DetailAccessRequest::getCreatedAt, DetailAccessRequest::getId)
        );
        if (requests.isEmpty()) {
            return List.of();
        }

        Map<Long, String> requesterNamesById = employeeMapper.selectBatchIds(
                        requests.stream()
                                .map(DetailAccessRequest::getRequesterEmployeeId)
                                .distinct()
                                .toList()
                ).stream()
                .collect(Collectors.toMap(Employee::getId, Employee::getName));

        Set<Long> requestIds = requests.stream()
                .map(DetailAccessRequest::getId)
                .collect(Collectors.toSet());
        Map<Long, DetailAccessAuditLog> auditLogsByRequestId = detailAccessAuditLogMapper.selectList(
                        new LambdaQueryWrapper<DetailAccessAuditLog>()
                                .in(DetailAccessAuditLog::getDetailAccessRequestId, requestIds)
                                .orderByAsc(DetailAccessAuditLog::getViewedAt, DetailAccessAuditLog::getId)
                ).stream()
                .collect(Collectors.toMap(
                        DetailAccessAuditLog::getDetailAccessRequestId,
                        Function.identity(),
                        (existing, ignored) -> existing
                ));

        return requests.stream()
                .map(request -> toEmployeeResponse(
                        request,
                        requesterNamesById.get(request.getRequesterEmployeeId()),
                        auditLogsByRequestId.get(request.getId())
                ))
                .toList();
    }

    @Override
    @Transactional
    public List<UsageRecordResponse> viewApprovedUsageRecords(Long requestId, AuthenticatedUser authenticatedUser) {
        authService.requireRole(authenticatedUser, MANAGER_ROLE);
        DetailAccessRequest detailAccessRequest = detailAccessRequestMapper.selectById(requestId);
        validateViewAuthorization(detailAccessRequest, authenticatedUser);

        List<UsageRecordResponse> usageRecords = usageRecordMapper.selectList(
                        new LambdaQueryWrapper<UsageRecord>()
                                .eq(UsageRecord::getEmployeeId, detailAccessRequest.getTargetEmployeeId())
                                .orderByAsc(UsageRecord::getStartedAt, UsageRecord::getId)
                ).stream()
                .map(this::toUsageRecordResponse)
                .toList();

        markAuthorizationUsedAndAudit(detailAccessRequest, authenticatedUser);
        return usageRecords;
    }

    @Override
    @Transactional
    public UsageViewResponse viewApprovedUsageView(Long requestId, LocalDate date, int page, int pageSize, AuthenticatedUser authenticatedUser) {
        authService.requireRole(authenticatedUser, MANAGER_ROLE);
        DetailAccessRequest detailAccessRequest = detailAccessRequestMapper.selectById(requestId);
        validateViewAuthorization(detailAccessRequest, authenticatedUser);

        UsageViewResponse usageView = usageRecordService.getUsageViewForEmployee(
                detailAccessRequest.getTargetEmployeeId(),
                date,
                page,
                pageSize
        );

        markAuthorizationUsedAndAudit(detailAccessRequest, authenticatedUser);
        return usageView;
    }

    @Override
    public List<DetailAccessAuditLogResponse> listAccessAuditLogs(Long requestId, AuthenticatedUser authenticatedUser) {
        authService.requireRole(authenticatedUser, MANAGER_ROLE);
        DetailAccessRequest detailAccessRequest = detailAccessRequestMapper.selectById(requestId);
        if (detailAccessRequest == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Detail access request not found");
        }
        if (!authenticatedUser.getEmployeeId().equals(detailAccessRequest.getRequesterEmployeeId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the requesting manager can view access audit logs");
        }

        return detailAccessAuditLogMapper.selectList(
                        new LambdaQueryWrapper<DetailAccessAuditLog>()
                                .eq(DetailAccessAuditLog::getDetailAccessRequestId, requestId)
                                .orderByAsc(DetailAccessAuditLog::getViewedAt, DetailAccessAuditLog::getId)
                ).stream()
                .map(this::toAuditLogResponse)
                .toList();
    }

    private void validateViewAuthorization(DetailAccessRequest detailAccessRequest, AuthenticatedUser authenticatedUser) {
        if (detailAccessRequest == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, AUTHORIZATION_MISSING_MESSAGE);
        }
        if (!authenticatedUser.getEmployeeId().equals(detailAccessRequest.getRequesterEmployeeId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, AUTHORIZATION_MISSING_MESSAGE);
        }
        if (STATUS_USED.equals(detailAccessRequest.getStatus())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, AUTHORIZATION_USED_MESSAGE);
        }
        if (!STATUS_APPROVED.equals(detailAccessRequest.getStatus())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, AUTHORIZATION_MISSING_MESSAGE);
        }
    }

    private void markAuthorizationUsedAndAudit(DetailAccessRequest detailAccessRequest, AuthenticatedUser authenticatedUser) {
        DetailAccessAuditLog auditLog = new DetailAccessAuditLog();
        auditLog.setDetailAccessRequestId(detailAccessRequest.getId());
        auditLog.setViewerEmployeeId(authenticatedUser.getEmployeeId());
        auditLog.setTargetEmployeeId(detailAccessRequest.getTargetEmployeeId());
        auditLog.setViewedAt(LocalDateTime.now());
        detailAccessAuditLogMapper.insert(auditLog);

        detailAccessRequest.setStatus(STATUS_USED);
        detailAccessRequestMapper.updateById(detailAccessRequest);
    }

    private DetailAccessRequestResponse toResponse(DetailAccessRequest detailAccessRequest) {
        return new DetailAccessRequestResponse(
                detailAccessRequest.getId(),
                detailAccessRequest.getRequesterEmployeeId(),
                detailAccessRequest.getTargetEmployeeId(),
                detailAccessRequest.getReason(),
                detailAccessRequest.getStatus(),
                detailAccessRequest.getCreatedAt(),
                detailAccessRequest.getProcessedAt(),
                detailAccessRequest.getProcessedByEmployeeId()
        );
    }

    private EmployeeDetailAccessRequestResponse toEmployeeResponse(DetailAccessRequest detailAccessRequest, String requesterEmployeeName,
                                                                   DetailAccessAuditLog auditLog) {
        boolean hasBeenViewed = auditLog != null || STATUS_USED.equals(detailAccessRequest.getStatus());
        LocalDateTime viewedAt = auditLog == null ? null : auditLog.getViewedAt();
        return new EmployeeDetailAccessRequestResponse(
                detailAccessRequest.getId(),
                detailAccessRequest.getRequesterEmployeeId(),
                requesterEmployeeName,
                detailAccessRequest.getReason(),
                detailAccessRequest.getStatus(),
                detailAccessRequest.getCreatedAt(),
                detailAccessRequest.getProcessedAt(),
                hasBeenViewed,
                viewedAt
        );
    }

    private UsageRecordResponse toUsageRecordResponse(UsageRecord usageRecord) {
        return new UsageRecordResponse(
                usageRecord.getId(),
                usageRecord.getAppName(),
                usageRecord.getStartedAt(),
                usageRecord.getEndedAt(),
                usageRecord.getCreatedAt()
        );
    }

    private DetailAccessAuditLogResponse toAuditLogResponse(DetailAccessAuditLog auditLog) {
        return new DetailAccessAuditLogResponse(
                auditLog.getId(),
                auditLog.getDetailAccessRequestId(),
                auditLog.getViewerEmployeeId(),
                auditLog.getTargetEmployeeId(),
                auditLog.getViewedAt()
        );
    }
}
