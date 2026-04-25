package com.worklens.backend.entity;

import lombok.Data;

@Data
public class AppCategory {
    private Long id;
    private String appName;
    private String category;
    private Boolean isWork;
}