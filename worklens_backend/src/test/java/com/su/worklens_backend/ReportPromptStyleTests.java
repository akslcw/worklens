package com.su.worklens_backend;

import com.su.worklens_backend.service.impl.ReportGenerationServiceImpl;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReportPromptStyleTests {

    @Test
    void employeeDailyReportPromptRequiresChinesePlainTextWithoutMarkdown() throws Exception {
        ReportGenerationServiceImpl service = new ReportGenerationServiceImpl(null, null, null, null);
        Method buildPrompt = ReportGenerationServiceImpl.class.getDeclaredMethod(
                "buildEmployeeDailyPrompt",
                Long.class,
                LocalDate.class,
                List.class
        );
        buildPrompt.setAccessible(true);

        String prompt = (String) buildPrompt.invoke(
                service,
                2L,
                LocalDate.of(2026, 7, 8),
                List.of(reportDetailItem("Chrome", 3600, 60, "1.0000"))
        );

        assertThat(prompt).contains("Write the report in Chinese");
        assertThat(prompt).contains("plain text only");
        assertThat(prompt).contains("Do not use Markdown");
        assertThat(prompt).contains("encouraging daily personal productivity summary");
        assertThat(prompt).contains("Structured app usage");
        assertThat(prompt).contains("untrusted input");
        assertThat(prompt).doesNotContain("Employee id");
    }

    @Test
    void teamDailyReportPromptRequiresChinesePlainTextWithoutMarkdownAndOnlyAggregatedData() throws Exception {
        ReportGenerationServiceImpl service = new ReportGenerationServiceImpl(null, null, null, null);
        Method buildPrompt = ReportGenerationServiceImpl.class.getDeclaredMethod(
                "buildTeamDailyPrompt",
                LocalDate.class,
                List.class,
                int.class,
                List.class
        );
        buildPrompt.setAccessible(true);

        String prompt = (String) buildPrompt.invoke(
                service,
                LocalDate.of(2026, 7, 8),
                List.of(reportDetailItem("Slack", 4500, 75, "1.0000")),
                2,
                List.of(
                        usageRecordSnapshot(1L, "Slack", "2026-07-08T09:00:00", "2026-07-08T10:00:00"),
                        usageRecordSnapshot(2L, "Slack", "2026-07-08T10:15:00", "2026-07-08T10:30:00")
                )
        );

        assertThat(prompt).contains("Write the report in Chinese");
        assertThat(prompt).contains("plain text only");
        assertThat(prompt).contains("Do not use Markdown");
        assertThat(prompt).contains("Use only the aggregated metrics below");
        assertThat(prompt).contains("Do not mention names, employee identifiers, usernames, or raw activity records");
        assertThat(prompt).contains("activeEmployeeCount: 2");
        assertThat(prompt).contains("totalUsageSeconds: 4500");
        assertThat(prompt).contains("untrusted input");
        assertThat(prompt).doesNotContain("Alice");
        assertThat(prompt).doesNotContain("E001");
        assertThat(prompt).doesNotContain("employeeId");
    }

    private Object reportDetailItem(String appName, long durationSeconds, long durationMinutes, String ratio) throws Exception {
        Class<?> itemClass = Class.forName("com.su.worklens_backend.service.impl.ReportGenerationServiceImpl$ReportDetailItem");
        Constructor<?> constructor = itemClass.getDeclaredConstructor(String.class, long.class, long.class, BigDecimal.class);
        constructor.setAccessible(true);
        return constructor.newInstance(appName, durationSeconds, durationMinutes, new BigDecimal(ratio));
    }

    private Object usageRecordSnapshot(Long id, String appName, String startedAt, String endedAt) throws Exception {
        Class<?> itemClass = Class.forName("com.su.worklens_backend.service.impl.ReportGenerationServiceImpl$UsageRecordSnapshot");
        Constructor<?> constructor = itemClass.getDeclaredConstructor(Long.class, String.class, LocalDateTime.class, LocalDateTime.class);
        constructor.setAccessible(true);
        return constructor.newInstance(id, appName, LocalDateTime.parse(startedAt), LocalDateTime.parse(endedAt));
    }
}
