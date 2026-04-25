package com.worklens.backend.entity;

import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class DailyReport {
    private Long id;
    private Long employeeId;
    private LocalDate reportDate;
    private Integer workSeconds;
    private Integer idleSeconds;
    private Integer efficiencyScore;
    private String aiComment;
    private LocalDateTime createdAt;
}