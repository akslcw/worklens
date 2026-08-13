package com.su.worklens_backend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.su.worklens_backend.auth.AuthenticatedUser;
import com.su.worklens_backend.dto.AppUsageRatioResponse;
import com.su.worklens_backend.dto.ReportDetailItemResponse;
import com.su.worklens_backend.dto.TeamUsageSummaryResponse;
import com.su.worklens_backend.dto.UsageAppCardResponse;
import com.su.worklens_backend.dto.UsageRecordRequest;
import com.su.worklens_backend.dto.UsageRecordResponse;
import com.su.worklens_backend.dto.UsageReportViewResponse;
import com.su.worklens_backend.dto.UsageSegmentResponse;
import com.su.worklens_backend.dto.UsageViewResponse;
import com.su.worklens_backend.entity.UsageRecord;
import com.su.worklens_backend.mapper.UsageRecordMapper;
import com.su.worklens_backend.service.AuthService;
import com.su.worklens_backend.service.UsageRecordService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.sql.Types;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class UsageRecordServiceImpl implements UsageRecordService {

    private static final long ADJACENT_RECORD_TOLERANCE_SECONDS = 15;
    private static final String EMPLOYEE_ROLE = "EMPLOYEE";
    private static final String MANAGER_ROLE = "MANAGER";

    private final UsageRecordMapper usageRecordMapper;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final AuthService authService;
    private final Clock clock;

    public UsageRecordServiceImpl(UsageRecordMapper usageRecordMapper, JdbcTemplate jdbcTemplate, ObjectMapper objectMapper,
                                  AuthService authService, Clock clock) {
        this.usageRecordMapper = usageRecordMapper;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.authService = authService;
        this.clock = clock;
    }

    @Override
    public List<UsageRecordResponse> listUsageRecords(AuthenticatedUser authenticatedUser) {
        authService.requireRole(authenticatedUser, EMPLOYEE_ROLE);
        return usageRecordMapper.selectList(
                        new LambdaQueryWrapper<UsageRecord>()
                                .eq(UsageRecord::getEmployeeId, authenticatedUser.getEmployeeId())
                                .orderByAsc(UsageRecord::getStartedAt, UsageRecord::getId)
                ).stream()
                .map(this::toUsageRecordResponse)
                .toList();
    }

    @Override
    public UsageViewResponse getUsageView(AuthenticatedUser authenticatedUser, LocalDate date, int page, int pageSize) {
        authService.requireRole(authenticatedUser, EMPLOYEE_ROLE);
        return getUsageViewForEmployee(authenticatedUser.getEmployeeId(), date, page, pageSize);
    }

    @Override
    public UsageViewResponse getUsageViewForEmployee(Long employeeId, LocalDate date, int page, int pageSize) {
        int normalizedPage = Math.max(1, page);
        int normalizedPageSize = Math.min(10, Math.max(1, pageSize));
        LocalDateTime dayStart = date.atStartOfDay();
        LocalDateTime nextDayStart = date.plusDays(1).atStartOfDay();

        List<UsageRecord> rawRecords = usageRecordMapper.selectList(
                new LambdaQueryWrapper<UsageRecord>()
                        .eq(UsageRecord::getEmployeeId, employeeId)
                        .ge(UsageRecord::getStartedAt, dayStart)
                        .lt(UsageRecord::getStartedAt, nextDayStart)
                        .orderByAsc(UsageRecord::getStartedAt, UsageRecord::getId)
        );

        if (!rawRecords.isEmpty()) {
            List<UsageAppCardResponse> cards = buildUsageAppCards(rawRecords);
            int fromIndex = Math.min((normalizedPage - 1) * normalizedPageSize, cards.size());
            int toIndex = Math.min(fromIndex + normalizedPageSize, cards.size());
            return UsageViewResponse.liveUsage(
                    date,
                    normalizedPage,
                    normalizedPageSize,
                    cards.size(),
                    cards.subList(fromIndex, toIndex)
            );
        }

        Optional<UsageReportViewResponse> report = findCoveringReport(employeeId, date);
        return UsageViewResponse.report(date, report.orElse(null));
    }

    @Override
    public UsageRecordResponse createUsageRecord(UsageRecordRequest request, AuthenticatedUser authenticatedUser) {
        authService.requireRole(authenticatedUser, EMPLOYEE_ROLE);
        validateRecordWindow(request);
        String appName = sanitizeAppName(request.getAppName());

        String clientRecordId = normalizeClientRecordId(request.getClientRecordId());
        if (clientRecordId != null) {
            UsageRecord existing = findByIdempotencyKey(authenticatedUser.getEmployeeId(), clientRecordId);
            if (existing != null) {
                return toUsageRecordResponse(existing);
            }
        }

        UsageRecord latestRecord = usageRecordMapper.selectOne(
                new LambdaQueryWrapper<UsageRecord>()
                        .eq(UsageRecord::getEmployeeId, authenticatedUser.getEmployeeId())
                        .orderByDesc(UsageRecord::getEndedAt, UsageRecord::getId)
                        .last("LIMIT 1")
        );
        if (canMergeIntoLatestRecord(latestRecord, appName, request)) {
            boolean changed = false;
            if (request.getEndedAt().isAfter(latestRecord.getEndedAt())) {
                latestRecord.setEndedAt(request.getEndedAt());
                changed = true;
            }
            if (clientRecordId != null && !clientRecordId.equals(latestRecord.getClientRecordId())) {
                latestRecord.setClientRecordId(clientRecordId);
                changed = true;
            }
            if (changed) {
                usageRecordMapper.updateById(latestRecord);
            }
            return toUsageRecordResponse(latestRecord);
        }

        UsageRecord usageRecord = new UsageRecord();
        usageRecord.setEmployeeId(authenticatedUser.getEmployeeId());
        usageRecord.setAppName(appName);
        usageRecord.setStartedAt(request.getStartedAt());
        usageRecord.setEndedAt(request.getEndedAt());
        usageRecord.setCreatedAt(LocalDateTime.now(clock));
        usageRecord.setClientRecordId(clientRecordId);
        try {
            usageRecordMapper.insert(usageRecord);
        } catch (DuplicateKeyException exception) {
            UsageRecord existing = findByIdempotencyKey(authenticatedUser.getEmployeeId(), clientRecordId);
            if (existing != null) {
                return toUsageRecordResponse(existing);
            }
            throw exception;
        }

        return toUsageRecordResponse(usageRecord);
    }

    private UsageRecord findByIdempotencyKey(Long employeeId, String clientRecordId) {
        if (clientRecordId == null) {
            return null;
        }
        return usageRecordMapper.selectOne(
                new LambdaQueryWrapper<UsageRecord>()
                        .eq(UsageRecord::getEmployeeId, employeeId)
                        .eq(UsageRecord::getClientRecordId, clientRecordId)
                        .last("LIMIT 1")
        );
    }

    private String normalizeClientRecordId(String clientRecordId) {
        if (clientRecordId == null || clientRecordId.isBlank()) {
            return null;
        }
        return clientRecordId.trim();
    }

    /**
     * M3: app names are client-controlled strings that later reach LLM
     * prompts. Strip control characters (including newlines), collapse
     * whitespace and cap the length so a forged name cannot inject
     * instructions or break the prompt layout.
     */
    static String sanitizeAppName(String appName) {
        String cleaned = appName
                .replaceAll("\\p{Cc}", " ")
                .replaceAll("\\s+", " ")
                .trim();
        return cleaned.length() > 60 ? cleaned.substring(0, 60) : cleaned;
    }

    /**
     * M1: rejects records whose window is implausible for the desktop
     * collector (segments are at most a few minutes long) so a forged payload
     * cannot poison personal or team statistics.
     */
    private void validateRecordWindow(UsageRecordRequest request) {
        LocalDateTime now = LocalDateTime.now(clock);
        if (request.getStartedAt().isAfter(now.plusMinutes(15))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "startedAt must not be in the future");
        }
        if (request.getStartedAt().isBefore(now.minusDays(60))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "startedAt is too far in the past");
        }
        if (Duration.between(request.getStartedAt(), request.getEndedAt()).compareTo(Duration.ofHours(12)) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Record duration must not exceed 12 hours");
        }
    }

    @Override
    public TeamUsageSummaryResponse getTeamUsageSummary(AuthenticatedUser authenticatedUser) {
        authService.requireRole(authenticatedUser, MANAGER_ROLE);
        LocalDate today = LocalDate.now(clock);
        Map<String, Object> totals = jdbcTemplate.queryForMap(
                """
                        SELECT COALESCE(SUM(FLOOR(EXTRACT(EPOCH FROM (ended_at - started_at)) / 60)), 0)::bigint
                                   AS total_usage_minutes,
                               COUNT(DISTINCT employee_id)::bigint AS active_employee_count
                        FROM usage_records
                        WHERE started_at >= ?::timestamp
                          AND started_at < (?::date + 1)::timestamp
                        """,
                new Object[]{today, today},
                new int[]{Types.TIMESTAMP, Types.DATE}
        );
        long totalUsageMinutes = ((Number) totals.get("total_usage_minutes")).longValue();
        int activeEmployeeCount = ((Number) totals.get("active_employee_count")).intValue();
        if (activeEmployeeCount == 0) {
            return new TeamUsageSummaryResponse(BigDecimal.ZERO, 0L, 0, List.of());
        }
        if (totalUsageMinutes == 0) {
            return new TeamUsageSummaryResponse(BigDecimal.ZERO, 0L, activeEmployeeCount, List.of());
        }

        BigDecimal teamAverageUsageMinutes = BigDecimal.valueOf(totalUsageMinutes)
                .divide(BigDecimal.valueOf(activeEmployeeCount), 2, RoundingMode.HALF_UP)
                .stripTrailingZeros();

        List<AppUsageRatioResponse> appUsageRatios = jdbcTemplate.queryForList(
                        """
                                SELECT app_name,
                                       SUM(FLOOR(EXTRACT(EPOCH FROM (ended_at - started_at)) / 60))::bigint AS usage_minutes
                                FROM usage_records
                                WHERE started_at >= ?::timestamp
                                  AND started_at < (?::date + 1)::timestamp
                                GROUP BY app_name
                                HAVING SUM(FLOOR(EXTRACT(EPOCH FROM (ended_at - started_at)) / 60)) > 0
                                ORDER BY usage_minutes DESC, app_name ASC
                                """,
                        new Object[]{today, today},
                        new int[]{Types.TIMESTAMP, Types.DATE}
                ).stream()
                .map(row -> {
                    long usageMinutes = ((Number) row.get("usage_minutes")).longValue();
                    return new AppUsageRatioResponse(
                        row.get("app_name").toString(),
                        usageMinutes,
                        BigDecimal.valueOf(usageMinutes)
                                .divide(BigDecimal.valueOf(totalUsageMinutes), 4, RoundingMode.HALF_UP)
                    );
                })
                .toList();

        return new TeamUsageSummaryResponse(teamAverageUsageMinutes, totalUsageMinutes, activeEmployeeCount, appUsageRatios);
    }

    private List<UsageAppCardResponse> buildUsageAppCards(List<UsageRecord> rawRecords) {
        Map<String, List<UsageRecord>> recordsByApp = rawRecords.stream()
                .collect(Collectors.groupingBy(
                        UsageRecord::getAppName,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        return recordsByApp.entrySet().stream()
                .map(entry -> {
                    List<UsageSegmentResponse> segments = mergeSegments(entry.getValue());
                    long durationSeconds = segments.stream()
                            .mapToLong(segment -> Duration.between(segment.getStartedAt(), segment.getEndedAt()).getSeconds())
                            .sum();
                    return new UsageAppCardResponse(entry.getKey(), durationSeconds, segments);
                })
                .sorted(Comparator.comparingLong(UsageAppCardResponse::getDurationSeconds).reversed()
                        .thenComparing(UsageAppCardResponse::getAppName))
                .toList();
    }

    private List<UsageSegmentResponse> mergeSegments(List<UsageRecord> records) {
        List<UsageSegmentResponse> segments = new ArrayList<>();
        for (UsageRecord record : records) {
            if (segments.isEmpty()) {
                segments.add(new UsageSegmentResponse(record.getStartedAt(), record.getEndedAt()));
                continue;
            }

            UsageSegmentResponse latestSegment = segments.get(segments.size() - 1);
            long gapSeconds = Duration.between(latestSegment.getEndedAt(), record.getStartedAt()).getSeconds();
            if (gapSeconds >= 0 && gapSeconds <= ADJACENT_RECORD_TOLERANCE_SECONDS) {
                if (record.getEndedAt().isAfter(latestSegment.getEndedAt())) {
                    latestSegment.setEndedAt(record.getEndedAt());
                }
            } else {
                segments.add(new UsageSegmentResponse(record.getStartedAt(), record.getEndedAt()));
            }
        }
        return segments;
    }

    private Optional<UsageReportViewResponse> findCoveringReport(Long employeeId, LocalDate date) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                        SELECT report_scope, period_type, period_start_date, period_end_date, summary, detail_json::text AS detail_json
                        FROM llm_reports
                        WHERE report_scope = 'EMPLOYEE'
                          AND target_employee_id = ?
                          AND period_start_date <= ?
                          AND period_end_date >= ?
                        ORDER BY CASE period_type
                                     WHEN 'DAILY' THEN 1
                                     WHEN 'WEEKLY' THEN 2
                                     WHEN 'MONTHLY' THEN 3
                                     ELSE 4
                                 END,
                                 generated_at DESC,
                                 id DESC
                        LIMIT 1
                        """,
                employeeId,
                date,
                date
        );
        if (rows.isEmpty()) {
            return Optional.empty();
        }

        Map<String, Object> row = rows.get(0);
        return Optional.of(new UsageReportViewResponse(
                row.get("report_scope").toString(),
                row.get("period_type").toString(),
                ((Date) row.get("period_start_date")).toLocalDate(),
                ((Date) row.get("period_end_date")).toLocalDate(),
                row.get("summary").toString(),
                parseReportDetails(row.get("detail_json").toString())
        ));
    }

    private List<ReportDetailItemResponse> parseReportDetails(String detailJson) {
        try {
            return objectMapper.readValue(detailJson, new TypeReference<>() {
            });
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to parse report detail JSON", exception);
        }
    }

    private boolean canMergeIntoLatestRecord(UsageRecord latestRecord, String appName, UsageRecordRequest request) {
        if (latestRecord == null) {
            return false;
        }
        if (!latestRecord.getAppName().equals(appName)) {
            return false;
        }
        long gapSeconds = Duration.between(latestRecord.getEndedAt(), request.getStartedAt()).getSeconds();
        return gapSeconds >= 0 && gapSeconds <= ADJACENT_RECORD_TOLERANCE_SECONDS;
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
}
