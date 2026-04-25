package com.worklens.backend.mapper;

import com.worklens.backend.entity.AppRecord;
import org.apache.ibatis.annotations.Mapper;
import java.time.LocalDate;
import java.util.List;

@Mapper
public interface AppRecordMapper {
    int insertBatch(List<AppRecord> records);
    List<AppRecord> findByDeviceIdAndDate(Long deviceId, LocalDate date);
    List<AppRecord> findByEmployeeIdAndDate(Long employeeId, LocalDate date);
}