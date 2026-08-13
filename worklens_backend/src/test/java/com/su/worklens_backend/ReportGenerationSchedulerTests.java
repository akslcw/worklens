package com.su.worklens_backend;

import com.su.worklens_backend.scheduler.ReportGenerationScheduler;
import com.su.worklens_backend.service.ReportGenerationService;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class ReportGenerationSchedulerTests {

    @Test
    void dailyReportGenerationUsesConfiguredCronAndHongKongTimezone() throws Exception {
        Method method = ReportGenerationScheduler.class.getDeclaredMethod("generateDailyReports");

        Scheduled scheduled = method.getAnnotation(Scheduled.class);

        assertThat(scheduled).isNotNull();
        assertThat(scheduled.cron()).isEqualTo("${worklens.reports.daily-cron:0 55 23 * * *}");
        assertThat(scheduled.zone()).isEqualTo("${worklens.reports.zone:Asia/Hong_Kong}");
    }

    @Test
    void dailyReportGenerationUsesCurrentDateFromClock() {
        ReportGenerationService reportGenerationService = mock(ReportGenerationService.class);
        Clock clock = Clock.fixed(
                Instant.parse("2026-07-08T15:55:00Z"),
                ZoneId.of("Asia/Hong_Kong")
        );
        ReportGenerationScheduler scheduler = new ReportGenerationScheduler(reportGenerationService, clock);

        scheduler.generateDailyReports();

        verify(reportGenerationService).generateDailyReports(LocalDate.of(2026, 7, 8));
    }

    @Test
    void weeklyReportGenerationUsesStaggeredCronAndHongKongTimezone() throws Exception {
        Method method = ReportGenerationScheduler.class.getDeclaredMethod("generateWeeklyReports");

        Scheduled scheduled = method.getAnnotation(Scheduled.class);

        assertThat(scheduled).isNotNull();
        assertThat(scheduled.cron()).isEqualTo("${worklens.reports.weekly-cron:0 30 0 * * MON}");
        assertThat(scheduled.zone()).isEqualTo("${worklens.reports.zone:Asia/Hong_Kong}");
    }

    @Test
    void weeklyReportGenerationRunsOnMondayForTheWeekEndingSunday() {
        ReportGenerationService reportGenerationService = mock(ReportGenerationService.class);
        Clock clock = Clock.fixed(
                Instant.parse("2026-07-12T16:30:00Z"),
                ZoneId.of("Asia/Hong_Kong")
        );
        ReportGenerationScheduler scheduler = new ReportGenerationScheduler(reportGenerationService, clock);

        scheduler.generateWeeklyReports();

        verify(reportGenerationService).generateWeeklyReports(LocalDate.of(2026, 7, 12));
    }

    @Test
    void monthlyReportGenerationUsesStaggeredCronAndHongKongTimezone() throws Exception {
        Method method = ReportGenerationScheduler.class.getDeclaredMethod("generateMonthlyReports");

        Scheduled scheduled = method.getAnnotation(Scheduled.class);

        assertThat(scheduled).isNotNull();
        assertThat(scheduled.cron()).isEqualTo("${worklens.reports.monthly-cron:0 0 1 * * *}");
        assertThat(scheduled.zone()).isEqualTo("${worklens.reports.zone:Asia/Hong_Kong}");
    }

    @Test
    void monthlyReportGenerationRunsOnFirstDayForPreviousMonthEnd() {
        ReportGenerationService reportGenerationService = mock(ReportGenerationService.class);
        Clock clock = Clock.fixed(
                Instant.parse("2026-07-31T17:00:00Z"),
                ZoneId.of("Asia/Hong_Kong")
        );
        ReportGenerationScheduler scheduler = new ReportGenerationScheduler(reportGenerationService, clock);

        scheduler.generateMonthlyReports();

        verify(reportGenerationService).generateMonthlyReports(LocalDate.of(2026, 7, 31));
    }

    @Test
    void monthlyReportGenerationSkipsWhenYesterdayIsNotMonthEnd() {
        ReportGenerationService reportGenerationService = mock(ReportGenerationService.class);
        Clock clock = Clock.fixed(
                Instant.parse("2026-08-01T17:00:00Z"),
                ZoneId.of("Asia/Hong_Kong")
        );
        ReportGenerationScheduler scheduler = new ReportGenerationScheduler(reportGenerationService, clock);

        scheduler.generateMonthlyReports();

        verify(reportGenerationService, never()).generateMonthlyReports(LocalDate.of(2026, 8, 1));
    }

    @Test
    void retryTaskUsesConfiguredCronAndHongKongTimezone() throws Exception {
        Method method = ReportGenerationScheduler.class.getDeclaredMethod("retryMissingReports");

        Scheduled scheduled = method.getAnnotation(Scheduled.class);

        assertThat(scheduled).isNotNull();
        assertThat(scheduled.cron()).isEqualTo("${worklens.reports.retry-cron:0 10 0 * * *}");
        assertThat(scheduled.zone()).isEqualTo("${worklens.reports.zone:Asia/Hong_Kong}");
    }

    @Test
    void retryTaskCoversLastSevenDaysFourClosedWeeksAndTwoClosedMonths() {
        ReportGenerationService reportGenerationService = mock(ReportGenerationService.class);
        Clock clock = Clock.fixed(
                Instant.parse("2026-07-12T16:10:00Z"),
                ZoneId.of("Asia/Hong_Kong")
        );
        ReportGenerationScheduler scheduler = new ReportGenerationScheduler(reportGenerationService, clock);

        scheduler.retryMissingReports();

        verify(reportGenerationService).generateDailyReports(LocalDate.of(2026, 7, 12));
        verify(reportGenerationService).generateDailyReports(LocalDate.of(2026, 7, 6));
        verify(reportGenerationService, times(7)).generateDailyReports(any(LocalDate.class));
        verify(reportGenerationService).generateWeeklyReports(LocalDate.of(2026, 7, 12));
        verify(reportGenerationService).generateWeeklyReports(LocalDate.of(2026, 6, 21));
        verify(reportGenerationService, times(4)).generateWeeklyReports(any(LocalDate.class));
        verify(reportGenerationService).generateMonthlyReports(LocalDate.of(2026, 6,30));
        verify(reportGenerationService).generateMonthlyReports(LocalDate.of(2026, 5, 31));
    }

    @Test
    void retryTaskOnSundayOnlyRetriesWeeksStrictlyBeforeToday() {
        ReportGenerationService reportGenerationService = mock(ReportGenerationService.class);
        Clock clock = Clock.fixed(
                Instant.parse("2026-07-11T16:10:00Z"),
                ZoneId.of("Asia/Hong_Kong")
        );
        ReportGenerationScheduler scheduler = new ReportGenerationScheduler(reportGenerationService, clock);

        scheduler.retryMissingReports();

        verify(reportGenerationService).generateWeeklyReports(LocalDate.of(2026, 7, 5));
        verify(reportGenerationService, never()).generateWeeklyReports(LocalDate.of(2026, 7, 12));
    }
}
