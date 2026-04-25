package com.worklens.backend.mapper;

import com.worklens.backend.entity.DailyReport;
import org.apache.ibatis.annotations.Mapper;
import java.time.LocalDate;
import java.util.List;

@Mapper
public interface DailyReportMapper {
    int insertOrUpdate(DailyReport report);
    DailyReport findByEmployeeIdAndDate(Long employeeId, LocalDate date);
    List<DailyReport> findByEmployeeId(Long employeeId);
    List<DailyReport> findAll();
}