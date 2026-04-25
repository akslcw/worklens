package com.worklens.backend.entity;

import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class AppRecord {
    private Long id;
    private Long deviceId;
    private String appName;
    private String windowTitle;
    private Integer durationSeconds;
    private LocalDate recordDate;
    private LocalDateTime createdAt;
}