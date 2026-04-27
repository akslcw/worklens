package com.worklens.backend.controller;

import com.worklens.backend.common.result.Result;
import com.worklens.backend.dto.HeartbeatDTO;
import com.worklens.backend.mapper.DeviceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/heartbeat")
@RequiredArgsConstructor
public class HeartbeatController {

    private final DeviceMapper deviceMapper;

    @PostMapping
    public Result<Void> heartbeat(@RequestBody HeartbeatDTO dto) {
        deviceMapper.updateHeartbeat(
                dto.getMacAddress(),
                dto.getCurrentApp(),
                dto.getCurrentWindow()
        );
        return Result.success();
    }
}