package com.worklens.backend.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class Employee {
    private Long id;
    private String name;
    private String employeeNo;
    private LocalDateTime createdAt;
}