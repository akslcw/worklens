package com.worklens.backend.service.impl;

import com.worklens.backend.common.exception.BusinessException;
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
            throw new BusinessException(400, "设备未注册，请先在管理后台添加员工和设备");
        }

        for (UploadRecordDTO item : dto.getRecords()) {
            AppRecord record = new AppRecord();
            record.setDeviceId(device.getId());
            record.setAppName(item.getAppName());
            record.setWindowTitle(item.getWindowTitle());
            record.setDurationSeconds(item.getDurationSeconds());
            record.setRecordDate(item.getRecordDate());
            appRecordMapper.insertBatch(List.of(record));
        }
    }

    @Override
    public List<AppRecord> listByEmployeeAndDate(Long employeeId, String date) {
        return appRecordMapper.findByEmployeeIdAndDate(employeeId, LocalDate.parse(date));
    }

    @Override
    public List<AppRecord> listByEmployeeAndDateRange(Long employeeId, String startDate, String endDate) {
        return appRecordMapper.findByEmployeeIdAndDateRange(
                employeeId,
                LocalDate.parse(startDate),
                LocalDate.parse(endDate)
        );
    }
}