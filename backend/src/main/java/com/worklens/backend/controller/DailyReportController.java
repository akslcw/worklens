package com.worklens.backend.controller;

import com.worklens.backend.common.result.Result;
import com.worklens.backend.entity.DailyReport;
import com.worklens.backend.service.DailyReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class DailyReportController {

    private final DailyReportService dailyReportService;

    @GetMapping
    public Result<?> listAll() {
        return Result.success(dailyReportService.listAllWithEmployee());
    }
    @GetMapping("/{employeeId}")
    public Result<List<DailyReport>> listByEmployee(@PathVariable Long employeeId) {
        return Result.success(dailyReportService.listByEmployee(employeeId));
    }

    @GetMapping("/{employeeId}/{date}")
    public Result<DailyReport> getByEmployeeAndDate(
            @PathVariable Long employeeId,
            @PathVariable String date) {
        return Result.success(dailyReportService.getByEmployeeAndDate(employeeId, date));
    }
}