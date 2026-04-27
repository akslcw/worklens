package com.worklens.backend.dto;

import lombok.Data;

@Data
public class HeartbeatDTO {
    private String macAddress;
    private String currentApp;
    private String currentWindow;
}