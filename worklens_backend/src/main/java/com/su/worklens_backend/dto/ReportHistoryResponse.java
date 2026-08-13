package com.su.worklens_backend.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class ReportHistoryResponse {

    private String reportType;
    private String periodType;
    private String summary;
    private LocalDateTime periodStartedAt;
    private LocalDateTime periodEndedAt;
    private LocalDate periodStartDate;
    private LocalDate periodEndDate;
    private LocalDateTime createdAt;

    public ReportHistoryResponse() {
    }

    public ReportHistoryResponse(String reportType, String periodType, String summary,
                                 LocalDateTime periodStartedAt, LocalDateTime periodEndedAt,
                                 LocalDate periodStartDate, LocalDate periodEndDate,
                                 LocalDateTime createdAt) {
        this.reportType = reportType;
        this.periodType = periodType;
        this.summary = summary;
        this.periodStartedAt = periodStartedAt;
        this.periodEndedAt = periodEndedAt;
        this.periodStartDate = periodStartDate;
        this.periodEndDate = periodEndDate;
        this.createdAt = createdAt;
    }

    public String getReportType() {
        return reportType;
    }

    public void setReportType(String reportType) {
        this.reportType = reportType;
    }

    public String getPeriodType() {
        return periodType;
    }

    public void setPeriodType(String periodType) {
        this.periodType = periodType;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public LocalDateTime getPeriodStartedAt() {
        return periodStartedAt;
    }

    public void setPeriodStartedAt(LocalDateTime periodStartedAt) {
        this.periodStartedAt = periodStartedAt;
    }

    public LocalDateTime getPeriodEndedAt() {
        return periodEndedAt;
    }

    public void setPeriodEndedAt(LocalDateTime periodEndedAt) {
        this.periodEndedAt = periodEndedAt;
    }

    public LocalDate getPeriodStartDate() {
        return periodStartDate;
    }

    public void setPeriodStartDate(LocalDate periodStartDate) {
        this.periodStartDate = periodStartDate;
    }

    public LocalDate getPeriodEndDate() {
        return periodEndDate;
    }

    public void setPeriodEndDate(LocalDate periodEndDate) {
        this.periodEndDate = periodEndDate;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
