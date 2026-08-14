package com.su.worklens_backend.scheduler;

import com.su.worklens_backend.service.EmployeeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * Permanently purges soft-deleted employees once their retention window
 * (WORKLENS_RETENTION_DAYS, default 30) has elapsed, balancing the right to
 * erasure with a recovery window for accidental deletions.
 */
@Component
public class RetentionScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(RetentionScheduler.class);

    private final EmployeeService employeeService;
    private final Clock clock;
    private final int retentionDays;

    public RetentionScheduler(
            EmployeeService employeeService,
            Clock clock,
            @Value("${worklens.retention.days:30}") int retentionDays
    ) {
        this.employeeService = employeeService;
        this.clock = clock;
        this.retentionDays = retentionDays;
    }

    @Scheduled(cron = "${worklens.retention.cron:0 0 2 * * *}", zone = "${worklens.reports.zone:Asia/Hong_Kong}")
    public void purgeExpiredEmployees() {
        LocalDateTime cutoff = LocalDateTime.now(clock).minusDays(retentionDays);
        int purged = employeeService.purgeExpiredEmployees(cutoff);
        if (purged > 0) {
            LOGGER.info("Purged {} soft-deleted employees older than {} days.", purged, retentionDays);
        }
    }
}
