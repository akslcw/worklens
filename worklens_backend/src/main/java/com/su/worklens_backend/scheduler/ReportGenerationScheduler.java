package com.su.worklens_backend.scheduler;

import com.su.worklens_backend.service.ReportGenerationService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;

/**
 * Report tasks are staggered so each layer always consumes the completed
 * output of the layer below it, even with the single-threaded scheduler:
 *
 * <pre>
 *   daily   23:55 every day          (raw usage records of the same day)
 *   weekly  00:30 Monday             (daily reports of the week ending yesterday/Sunday)
 *   monthly 01:00 on the 1st         (weekly reports of the month ending yesterday)
 * </pre>
 *
 * Weekly and monthly compute the report date as "yesterday" because they run
 * on the first day after the reporting period closes.
 */
@Component
public class ReportGenerationScheduler {

    private final ReportGenerationService reportGenerationService;
    private final Clock clock;

    public ReportGenerationScheduler(ReportGenerationService reportGenerationService, Clock clock) {
        this.reportGenerationService = reportGenerationService;
        this.clock = clock;
    }

    @Scheduled(cron = "${worklens.reports.daily-cron:0 55 23 * * *}", zone = "${worklens.reports.zone:Asia/Hong_Kong}")
    public void generateDailyReports() {
        reportGenerationService.generateDailyReports(LocalDate.now(clock));
    }

    @Scheduled(cron = "${worklens.reports.weekly-cron:0 30 0 * * MON}", zone = "${worklens.reports.zone:Asia/Hong_Kong}")
    public void generateWeeklyReports() {
        reportGenerationService.generateWeeklyReports(LocalDate.now(clock).minusDays(1));
    }

    @Scheduled(cron = "${worklens.reports.monthly-cron:0 0 1 * * *}", zone = "${worklens.reports.zone:Asia/Hong_Kong}")
    public void generateMonthlyReports() {
        LocalDate reportDate = LocalDate.now(clock).minusDays(1);
        if (reportDate.equals(reportDate.withDayOfMonth(reportDate.lengthOfMonth()))) {
            reportGenerationService.generateMonthlyReports(reportDate);
        }
    }

    /**
     * Convergence pass for periods whose LLM generation failed earlier. All
     * generation entry points are idempotent (existing reports are skipped),
     * so retrying is cheap when everything already succeeded. Only fully
     * closed periods are retried: past days, weeks ending on a Sunday strictly
     * before today, and fully elapsed months.
     */
    @Scheduled(cron = "${worklens.reports.retry-cron:0 10 0 * * *}", zone = "${worklens.reports.zone:Asia/Hong_Kong}")
    public void retryMissingReports() {
        LocalDate today = LocalDate.now(clock);

        for (int daysBack = 1; daysBack <= 7; daysBack++) {
            reportGenerationService.generateDailyReports(today.minusDays(daysBack));
        }

        LocalDate latestClosedWeekEnd = today.minusDays(1);
        while (latestClosedWeekEnd.getDayOfWeek() != DayOfWeek.SUNDAY) {
            latestClosedWeekEnd = latestClosedWeekEnd.minusDays(1);
        }
        for (int weeksBack = 0; weeksBack < 4; weeksBack++) {
            reportGenerationService.generateWeeklyReports(latestClosedWeekEnd.minusWeeks(weeksBack));
        }

        LocalDate previousMonth = today.minusMonths(1);
        LocalDate previousMonthEnd = previousMonth.withDayOfMonth(previousMonth.lengthOfMonth());
        reportGenerationService.generateMonthlyReports(previousMonthEnd);
        LocalDate monthBefore = previousMonthEnd.minusMonths(1);
        reportGenerationService.generateMonthlyReports(monthBefore.withDayOfMonth(monthBefore.lengthOfMonth()));
    }
}
