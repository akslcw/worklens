package com.worklens.backend.dto;

import lombok.Data;
import java.util.List;

@Data
public class UploadBatchDTO {
    private String macAddress;
    private String deviceName;
    private Long employeeId;
    private List<UploadRecordDTO> records;
}