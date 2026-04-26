package com.worklens.backend.service;

import com.worklens.backend.entity.DailyReport;
import java.util.List;
import java.util.Map;

public interface DailyReportService {
    DailyReport getByEmployeeAndDate(Long employeeId, String date);
    List<DailyReport> listByEmployee(Long employeeId);
    List<DailyReport> listAll();
    List<Map<String, Object>> listAllWithEmployee();
}