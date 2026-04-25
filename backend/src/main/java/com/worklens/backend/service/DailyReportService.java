package com.worklens.backend.service;

import com.worklens.backend.entity.DailyReport;
import java.util.List;

public interface DailyReportService {
    DailyReport getByEmployeeAndDate(Long employeeId, String date);
    List<DailyReport> listByEmployee(Long employeeId);
}