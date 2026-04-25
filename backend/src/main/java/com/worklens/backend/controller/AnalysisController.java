package com.worklens.backend.controller;

import com.worklens.backend.common.result.Result;
import com.worklens.backend.service.ScheduledTaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/analysis")
@RequiredArgsConstructor
public class AnalysisController {

    private final ScheduledTaskService scheduledTaskService;

    @PostMapping("/trigger/{employeeId}")
    public Result<Void> trigger(
            @PathVariable Long employeeId,
            @RequestParam(required = false) String date) {
        LocalDate targetDate = date != null ? LocalDate.parse(date) : LocalDate.now();
        scheduledTaskService.triggerForEmployee(employeeId, targetDate);
        return Result.success();
    }
}