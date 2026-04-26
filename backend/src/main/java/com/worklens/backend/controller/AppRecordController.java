package com.worklens.backend.controller;

import com.worklens.backend.common.result.Result;
import com.worklens.backend.dto.UploadBatchDTO;
import com.worklens.backend.dto.UploadRecordDTO;
import com.worklens.backend.service.AppRecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/records")
@RequiredArgsConstructor
public class AppRecordController {

    private final AppRecordService appRecordService;

    @PostMapping("/upload")
    public Result<Void> upload(@RequestBody UploadBatchDTO dto) {
        appRecordService.upload(dto);
        return Result.success();
    }
    @GetMapping("/{employeeId}")
    public Result<?> listByEmployeeAndDate(
            @PathVariable Long employeeId,
            @RequestParam String date) {
        return Result.success(appRecordService.listByEmployeeAndDate(employeeId, date));
    }
}