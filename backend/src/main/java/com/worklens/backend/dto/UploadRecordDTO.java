package com.worklens.backend.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class UploadRecordDTO {
    private String macAddress;
    private String appName;
    private String windowTitle;
    private LocalDateTime startTime;
    private Integer durationSeconds;
}