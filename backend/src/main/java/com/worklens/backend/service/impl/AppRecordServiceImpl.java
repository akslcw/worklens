package com.worklens.backend.service.impl;

import com.worklens.backend.dto.UploadRecordDTO;
import com.worklens.backend.entity.AppRecord;
import com.worklens.backend.entity.Device;
import com.worklens.backend.mapper.AppRecordMapper;
import com.worklens.backend.mapper.DeviceMapper;
import com.worklens.backend.service.AppRecordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.ArrayList;

@Slf4j
@Service
@RequiredArgsConstructor
public class AppRecordServiceImpl implements AppRecordService {

    private final AppRecordMapper appRecordMapper;
    private final DeviceMapper deviceMapper;

    @Override
    public void upload(UploadRecordDTO dto) {
        Device device = deviceMapper.selectByMac(dto.getMacAddress());
        if (device == null) {
            log.warn("未知设备上报数据, MAC: {}", dto.getMacAddress());
            return;
        }

        AppRecord existing = appRecordMapper.selectByUniqueKey(
                device.getId(), dto.getAppName(), dto.getStartTime().toLocalDate()
        );
        if (existing != null) {
            log.info("重复上报，跳过: deviceId={}, app={}", device.getId(), dto.getAppName());
            return;
        }

        AppRecord record = new AppRecord();
        record.setDeviceId(device.getId());
        record.setAppName(dto.getAppName());
        record.setWindowTitle(dto.getWindowTitle());
        record.setDurationSeconds(dto.getDurationSeconds());
        record.setRecordDate(dto.getStartTime().toLocalDate());

        List<AppRecord> records = new ArrayList<>();
        records.add(record);
        appRecordMapper.insertBatch(records);}

    @Override
    public List<AppRecord> listByEmployeeAndDate(Long employeeId, String date) {
        return appRecordMapper.findByEmployeeIdAndDate(employeeId, LocalDate.parse(date));
    }
}