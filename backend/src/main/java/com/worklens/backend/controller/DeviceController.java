package com.worklens.backend.controller;

import com.worklens.backend.common.result.Result;
import com.worklens.backend.entity.Device;
import com.worklens.backend.mapper.DeviceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/devices")
@RequiredArgsConstructor
public class DeviceController {

    private final DeviceMapper deviceMapper;

    @GetMapping
    public Result<List<Device>> listAll() {
        return Result.success(deviceMapper.findAll());
    }
}