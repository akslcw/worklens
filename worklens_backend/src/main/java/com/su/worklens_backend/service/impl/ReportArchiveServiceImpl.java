package com.su.worklens_backend.service.impl;

import com.su.worklens_backend.service.EmployeeDailyReportArchiveRequest;
import com.su.worklens_backend.service.EmployeeMonthlyReportArchiveRequest;
import com.su.worklens_backend.service.EmployeeWeeklyReportArchiveRequest;
import com.su.worklens_backend.service.ReportArchiveService;
import com.su.worklens_backend.service.TeamDailyReportArchiveRequest;
import com.su.worklens_backend.service.TeamMonthlyReportArchiveRequest;
import com.su.worklens_backend.service.TeamWeeklyReportArchiveRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Archives generated reports and removes only the source data that was
 * actually consumed by an archived report pair (employee + team).
 *
 * Every inserted report stores the ids of its source records/reports in
 * {@code source_record_ids BIGINT[]}. A source row is deleted only when it is
 * a member of the consuming report's id array (and, for raw usage records,
 * when the owning employee's report also covers it). Late-arriving rows that
 * were never part of a report therefore survive re-runs untouched, and the
 * whole pipeline is idempotent: retries converge without duplicating reports
 * or destroying uncovered data.
 */
@Service
public class ReportArchiveServiceImpl implements ReportArchiveService {

    private static final String EMPLOYEE_SCOPE = "EMPLOYEE";
    private static final String TEAM_SCOPE = "TEAM";
    private static final String DAILY_PERIOD = "DAILY";
    private static final String WEEKLY_PERIOD = "WEEKLY";
    private static final String MONTHLY_PERIOD = "MONTHLY";
    private static final String RAW_USAGE_SOURCE = "RAW_USAGE";
    private static final String DAILY_REPORTS_SOURCE = "DAILY_REPORTS";
    private static final String WEEKLY_REPORTS_SOURCE = "WEEKLY_REPORTS";

    private final JdbcTemplate jdbcTemplate;

    public ReportArchiveServiceImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional
    public void archiveDailyReports(List<EmployeeDailyReportArchiveRequest> employeeReports, TeamDailyReportArchiveRequest teamReport) {
        for (EmployeeDailyReportArchiveRequest report : employeeReports) {
            insertEmployeeDailyReport(report);
        }
        if (teamReport != null) {
            insertTeamDailyReport(teamReport);
        }
        if (!employeeReports.isEmpty() || teamReport != null) {
            LocalDate reportDate = teamReport != null ? teamReport.reportDate() : employeeReports.get(0).reportDate();
            deleteCoveredDailySourceRecords(reportDate);
        }
    }

    @Override
    @Transactional
    public void archiveWeeklyReports(List<EmployeeWeeklyReportArchiveRequest> employeeReports, TeamWeeklyReportArchiveRequest teamReport) {
        for (EmployeeWeeklyReportArchiveRequest report : employeeReports) {
            insertEmployeeWeeklyReport(report);
        }
        if (teamReport != null) {
            insertTeamWeeklyReport(teamReport);
        }
        if (!employeeReports.isEmpty() || teamReport != null) {
            deleteCoveredWeeklySourceReports(
                    employeeReports.isEmpty() ? teamReport.periodStartDate() : employeeReports.get(0).periodStartDate(),
                    employeeReports.isEmpty() ? teamReport.periodEndDate() : employeeReports.get(0).periodEndDate()
            );
        }
    }

    @Override
    @Transactional
    public void archiveMonthlyReports(List<EmployeeMonthlyReportArchiveRequest> employeeReports, TeamMonthlyReportArchiveRequest teamReport) {
        for (EmployeeMonthlyReportArchiveRequest report : employeeReports) {
            insertEmployeeMonthlyReport(report);
        }
        if (teamReport != null) {
            insertTeamMonthlyReport(teamReport);
        }
        if (!employeeReports.isEmpty() || teamReport != null) {
            deleteCoveredMonthlySourceReports(
                    employeeReports.isEmpty() ? teamReport.periodStartDate() : employeeReports.get(0).periodStartDate(),
                    employeeReports.isEmpty() ? teamReport.periodEndDate() : employeeReports.get(0).periodEndDate()
            );
        }
    }

    @Override
    @Transactional
    public void archiveEmployeeDailyReports(List<EmployeeDailyReportArchiveRequest> reports) {
        archiveDailyReports(reports, null);
    }

    @Override
    @Transactional
    public void archiveEmployeeDailyReport(
            Long employeeId,
            LocalDate reportDate,
            LocalDateTime periodStartedAt,
            LocalDateTime periodEndedAt,
            String detailJson,
            String summary,
            int sourceCount,
            List<Long> sourceRecordIds
    ) {
        archiveEmployeeDailyReports(List.of(new EmployeeDailyReportArchiveRequest(
                employeeId,
                reportDate,
                periodStartedAt,
                periodEndedAt,
                detailJson,
                summary,
                sourceCount,
                sourceRecordIds
        )));
    }

    private void insertEmployeeDailyReport(EmployeeDailyReportArchiveRequest report) {
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update(
                """
                        INSERT INTO llm_reports (
                            report_type,
                            requester_employee_id,
                            target_employee_id,
                            summary,
                            period_started_at,
                            period_ended_at,
                            created_at,
                            report_scope,
                            period_type,
                            period_start_date,
                            period_end_date,
                            detail_json,
                            source_layer,
                            source_count,
                            generated_at,
                            source_record_ids
                        )
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?::bigint[])
                        ON CONFLICT (report_scope, period_type, target_employee_id, period_start_date, period_end_date)
                        WHERE report_scope = 'EMPLOYEE'
                        DO NOTHING
                        """,
                "EMPLOYEE_DAILY",
                report.employeeId(),
                report.employeeId(),
                report.summary(),
                Timestamp.valueOf(report.periodStartedAt()),
                Timestamp.valueOf(report.periodEndedAt()),
                Timestamp.valueOf(now),
                EMPLOYEE_SCOPE,
                DAILY_PERIOD,
                report.reportDate(),
                report.reportDate(),
                report.detailJson(),
                RAW_USAGE_SOURCE,
                report.sourceCount(),
                Timestamp.valueOf(now),
                toPgLongArray(report.sourceRecordIds())
        );
    }

    private void insertTeamDailyReport(TeamDailyReportArchiveRequest report) {
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update(
                """
                        INSERT INTO llm_reports (
                            report_type,
                            requester_employee_id,
                            target_employee_id,
                            summary,
                            period_started_at,
                            period_ended_at,
                            created_at,
                            report_scope,
                            period_type,
                            period_start_date,
                            period_end_date,
                            detail_json,
                            source_layer,
                            source_count,
                            generated_at,
                            source_record_ids
                        )
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?::bigint[])
                        ON CONFLICT (report_scope, period_type, period_start_date, period_end_date)
                        WHERE report_scope = 'TEAM'
                        DO NOTHING
                        """,
                "TEAM_DAILY",
                null,
                null,
                report.summary(),
                Timestamp.valueOf(report.periodStartedAt()),
                Timestamp.valueOf(report.periodEndedAt()),
                Timestamp.valueOf(now),
                TEAM_SCOPE,
                DAILY_PERIOD,
                report.reportDate(),
                report.reportDate(),
                report.detailJson(),
                RAW_USAGE_SOURCE,
                report.sourceCount(),
                Timestamp.valueOf(now),
                toPgLongArray(report.sourceRecordIds())
        );
    }

    private void insertEmployeeWeeklyReport(EmployeeWeeklyReportArchiveRequest report) {
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update(
                """
                        INSERT INTO llm_reports (
                            report_type,
                            requester_employee_id,
                            target_employee_id,
                            summary,
                            period_started_at,
                            period_ended_at,
                            created_at,
                            report_scope,
                            period_type,
                            period_start_date,
                            period_end_date,
                            detail_json,
                            source_layer,
                            source_count,
                            generated_at,
                            source_record_ids
                        )
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?::bigint[])
                        ON CONFLICT (report_scope, period_type, target_employee_id, period_start_date, period_end_date)
                        WHERE report_scope = 'EMPLOYEE'
                        DO NOTHING
                        """,
                "EMPLOYEE_WEEKLY",
                report.employeeId(),
                report.employeeId(),
                report.summary(),
                Timestamp.valueOf(report.periodStartedAt()),
                Timestamp.valueOf(report.periodEndedAt()),
                Timestamp.valueOf(now),
                EMPLOYEE_SCOPE,
                WEEKLY_PERIOD,
                report.periodStartDate(),
                report.periodEndDate(),
                report.detailJson(),
                DAILY_REPORTS_SOURCE,
                report.sourceCount(),
                Timestamp.valueOf(now),
                toPgLongArray(report.sourceReportIds())
        );
    }

    private void insertTeamWeeklyReport(TeamWeeklyReportArchiveRequest report) {
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update(
                """
                        INSERT INTO llm_reports (
                            report_type,
                            requester_employee_id,
                            target_employee_id,
                            summary,
                            period_started_at,
                            period_ended_at,
                            created_at,
                            report_scope,
                            period_type,
                            period_start_date,
                            period_end_date,
                            detail_json,
                            source_layer,
                            source_count,
                            generated_at,
                            source_record_ids
                        )
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?::bigint[])
                        ON CONFLICT (report_scope, period_type, period_start_date, period_end_date)
                        WHERE report_scope = 'TEAM'
                        DO NOTHING
                        """,
                "TEAM_WEEKLY",
                null,
                null,
                report.summary(),
                Timestamp.valueOf(report.periodStartedAt()),
                Timestamp.valueOf(report.periodEndedAt()),
                Timestamp.valueOf(now),
                TEAM_SCOPE,
                WEEKLY_PERIOD,
                report.periodStartDate(),
                report.periodEndDate(),
                report.detailJson(),
                DAILY_REPORTS_SOURCE,
                report.sourceCount(),
                Timestamp.valueOf(now),
                toPgLongArray(report.sourceReportIds())
        );
    }

    private void insertEmployeeMonthlyReport(EmployeeMonthlyReportArchiveRequest report) {
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update(
                """
                        INSERT INTO llm_reports (
                            report_type,
                            requester_employee_id,
                            target_employee_id,
                            summary,
                            period_started_at,
                            period_ended_at,
                            created_at,
                            report_scope,
                            period_type,
                            period_start_date,
                            period_end_date,
                            detail_json,
                            source_layer,
                            source_count,
                            generated_at,
                            source_record_ids
                        )
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?::bigint[])
                        ON CONFLICT (report_scope, period_type, target_employee_id, period_start_date, period_end_date)
                        WHERE report_scope = 'EMPLOYEE'
                        DO NOTHING
                        """,
                "EMPLOYEE_MONTHLY",
                report.employeeId(),
                report.employeeId(),
                report.summary(),
                Timestamp.valueOf(report.periodStartedAt()),
                Timestamp.valueOf(report.periodEndedAt()),
                Timestamp.valueOf(now),
                EMPLOYEE_SCOPE,
                MONTHLY_PERIOD,
                report.periodStartDate(),
                report.periodEndDate(),
                report.detailJson(),
                WEEKLY_REPORTS_SOURCE,
                report.sourceCount(),
                Timestamp.valueOf(now),
                toPgLongArray(report.sourceReportIds())
        );
    }

    private void insertTeamMonthlyReport(TeamMonthlyReportArchiveRequest report) {
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update(
                """
                        INSERT INTO llm_reports (
                            report_type,
                            requester_employee_id,
                            target_employee_id,
                            summary,
                            period_started_at,
                            period_ended_at,
                            created_at,
                            report_scope,
                            period_type,
                            period_start_date,
                            period_end_date,
                            detail_json,
                            source_layer,
                            source_count,
                            generated_at,
                            source_record_ids
                        )
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?::bigint[])
                        ON CONFLICT (report_scope, period_type, period_start_date, period_end_date)
                        WHERE report_scope = 'TEAM'
                        DO NOTHING
                        """,
                "TEAM_MONTHLY",
                null,
                null,
                report.summary(),
                Timestamp.valueOf(report.periodStartedAt()),
                Timestamp.valueOf(report.periodEndedAt()),
                Timestamp.valueOf(now),
                TEAM_SCOPE,
                MONTHLY_PERIOD,
                report.periodStartDate(),
                report.periodEndDate(),
                report.detailJson(),
                WEEKLY_REPORTS_SOURCE,
                report.sourceCount(),
                Timestamp.valueOf(now),
                toPgLongArray(report.sourceReportIds())
        );
    }

    /**
     * Deletes raw usage records of the given day only when the record id is
     * covered by BOTH the team daily report and its employee's daily report.
     * Late-arriving records that were never part of a report survive.
     */
    private void deleteCoveredDailySourceRecords(LocalDate reportDate) {
        jdbcTemplate.update(
                """
                        DELETE FROM usage_records r
                        WHERE r.started_at >= ?::timestamp
                          AND r.started_at < (?::date + 1)::timestamp
                          AND r.id IN (
                              SELECT unnest(source_record_ids)
                              FROM llm_reports tr
                              WHERE tr.report_scope = 'TEAM'
                                AND tr.period_type = 'DAILY'
                                AND tr.period_start_date = ?
                                AND tr.period_end_date = ?
                          )
                          AND r.id IN (
                              SELECT unnest(source_record_ids)
                              FROM llm_reports er
                              WHERE er.report_scope = 'EMPLOYEE'
                                AND er.period_type = 'DAILY'
                                AND er.target_employee_id = r.employee_id
                                AND er.period_start_date = ?
                                AND er.period_end_date = ?
                          )
                        """,
                reportDate,
                reportDate,
                reportDate,
                reportDate,
                reportDate,
                reportDate
        );
    }

    /**
     * Deletes daily reports of the given week only when they are members of
     * the consuming weekly report's source ids: team dailies via the team
     * weekly, employee dailies via their employee's weekly. Late-generated
     * dailies that were never part of a weekly report survive.
     */
    private void deleteCoveredWeeklySourceReports(LocalDate periodStartDate, LocalDate periodEndDate) {
        jdbcTemplate.update(
                """
                        DELETE FROM llm_reports d
                        WHERE d.period_type = 'DAILY'
                          AND d.period_start_date >= ?
                          AND d.period_end_date <= ?
                          AND (
                              (
                                  d.report_scope = 'TEAM'
                                  AND d.id IN (
                                      SELECT unnest(source_record_ids)
                                      FROM llm_reports tw
                                      WHERE tw.report_scope = 'TEAM'
                                        AND tw.period_type = 'WEEKLY'
                                        AND tw.period_start_date = ?
                                        AND tw.period_end_date = ?
                                  )
                              )
                              OR
                              (
                                  d.report_scope = 'EMPLOYEE'
                                  AND d.id IN (
                                      SELECT unnest(source_record_ids)
                                      FROM llm_reports ew
                                      WHERE ew.report_scope = 'EMPLOYEE'
                                        AND ew.period_type = 'WEEKLY'
                                        AND ew.target_employee_id = d.target_employee_id
                                        AND ew.period_start_date = ?
                                        AND ew.period_end_date = ?
                                  )
                              )
                          )
                        """,
                periodStartDate,
                periodEndDate,
                periodStartDate,
                periodEndDate,
                periodStartDate,
                periodEndDate
        );
    }

    /**
     * Deletes weekly reports of the given month only when they are members of
     * the consuming monthly report's source ids: team weeklies via the team
     * monthly, employee weeklies via their employee's monthly. Late-generated
     * weeklies that were never part of a monthly report survive.
     */
    private void deleteCoveredMonthlySourceReports(LocalDate periodStartDate, LocalDate periodEndDate) {
        jdbcTemplate.update(
                """
                        DELETE FROM llm_reports w
                        WHERE w.period_type = 'WEEKLY'
                          AND w.period_start_date >= ?
                          AND w.period_end_date <= ?
                          AND (
                              (
                                  w.report_scope = 'TEAM'
                                  AND w.id IN (
                                      SELECT unnest(source_record_ids)
                                      FROM llm_reports tm
                                      WHERE tm.report_scope = 'TEAM'
                                        AND tm.period_type = 'MONTHLY'
                                        AND tm.period_start_date = ?
                                        AND tm.period_end_date = ?
                                  )
                              )
                              OR
                              (
                                  w.report_scope = 'EMPLOYEE'
                                  AND w.id IN (
                                      SELECT unnest(source_record_ids)
                                      FROM llm_reports em
                                      WHERE em.report_scope = 'EMPLOYEE'
                                        AND em.period_type = 'MONTHLY'
                                        AND em.target_employee_id = w.target_employee_id
                                        AND em.period_start_date = ?
                                        AND em.period_end_date = ?
                                  )
                              )
                          )
                        """,
                periodStartDate,
                periodEndDate,
                periodStartDate,
                periodEndDate,
                periodStartDate,
                periodEndDate
        );
    }

    private String toPgLongArray(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return null;
        }
        StringBuilder builder = new StringBuilder("{");
        for (int index = 0; index < ids.size(); index++) {
            if (index > 0) {
                builder.append(',');
            }
            builder.append(ids.get(index));
        }
        return builder.append('}').toString();
    }
}
