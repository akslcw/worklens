package com.worklens.backend.service.impl;

import com.worklens.backend.dto.UploadBatchDTO;
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
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AppRecordServiceImpl implements AppRecordService {

    private final AppRecordMapper appRecordMapper;
    private final DeviceMapper deviceMapper;

    @Override
    public void upload(UploadBatchDTO dto) {
        Device device = deviceMapper.selectByMac(dto.getMacAddress());
        if (device == null) {
            log.warn("未知设备上报数据, MAC: {}", dto.getMacAddress());
            return;
        }

        for (UploadRecordDTO item : dto.getRecords()) {
            AppRecord existing = appRecordMapper.selectByUniqueKey(
                    device.getId(), item.getAppName(), item.getRecordDate()
            );
            if (existing != null) {
                // 累加时长，不跳过
                existing.setDurationSeconds(existing.getDurationSeconds() + item.getDurationSeconds());
                appRecordMapper.updateDuration(existing.getId(), existing.getDurationSeconds());
                log.info("累加时长: deviceId={}, app={}, +{}s",
                        device.getId(), item.getAppName(), item.getDurationSeconds());
            } else {
                AppRecord record = new AppRecord();
                record.setDeviceId(device.getId());
                record.setAppName(item.getAppName());
                record.setWindowTitle(item.getWindowTitle());
                record.setDurationSeconds(item.getDurationSeconds());
                record.setRecordDate(item.getRecordDate());
                appRecordMapper.insertBatch(List.of(record));
            }
        }
    }

    @Override
    public List<AppRecord> listByEmployeeAndDate(Long employeeId, String date) {
        return appRecordMapper.findByEmployeeIdAndDate(employeeId, LocalDate.parse(date));
    }
}