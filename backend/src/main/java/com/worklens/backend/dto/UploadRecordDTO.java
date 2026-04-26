package com.worklens.backend.dto;

import lombok.Data;
import java.time.LocalDate;

@Data
public class UploadRecordDTO {
    private String appName;
    private String windowTitle;
    private Integer durationSeconds;
    private LocalDate recordDate;
}