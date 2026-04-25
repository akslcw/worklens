package com.worklens.backend.mapper;

import com.worklens.backend.entity.AppRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface AppRecordMapper {
    int insertBatch(List<AppRecord> records);
    List<AppRecord> findByDeviceIdAndDate(Long deviceId, LocalDate date);
    List<AppRecord> findByEmployeeIdAndDate(@Param("employeeId") Long employeeId, @Param("date") LocalDate date);
    AppRecord selectByUniqueKey(@Param("deviceId") Long deviceId,
                                @Param("appName") String appName,
                                @Param("recordDate") LocalDate recordDate);}