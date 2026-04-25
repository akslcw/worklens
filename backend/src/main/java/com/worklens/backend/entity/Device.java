package com.worklens.backend.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class Device {
    private Long id;
    private Long employeeId;
    private String deviceName;
    private String macAddress;
    private LocalDateTime lastOnline;
    private LocalDateTime createdAt;
}