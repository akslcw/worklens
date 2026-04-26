package com.worklens.backend.mapper;

import com.worklens.backend.entity.DailyReport;
import org.apache.ibatis.annotations.Mapper;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Mapper
public interface DailyReportMapper {
    int insertOrUpdate(DailyReport report);
    DailyReport findByEmployeeIdAndDate(Long employeeId, LocalDate date);
    List<DailyReport> findByEmployeeId(Long employeeId);
    List<DailyReport> findAll();
    List<Map<String, Object>> findAllWithEmployee();
}