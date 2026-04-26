package com.worklens.backend.service.impl;

import com.worklens.backend.entity.DailyReport;
import com.worklens.backend.mapper.DailyReportMapper;
import com.worklens.backend.service.DailyReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DailyReportServiceImpl implements DailyReportService {

    private final DailyReportMapper dailyReportMapper;

    @Override
    public DailyReport getByEmployeeAndDate(Long employeeId, String date) {
        return dailyReportMapper.findByEmployeeIdAndDate(employeeId, LocalDate.parse(date));
    }

    @Override
    public List<DailyReport> listByEmployee(Long employeeId) {
        return dailyReportMapper.findByEmployeeId(employeeId);
    }

    @Override
    public List<DailyReport> listAll() {
        return dailyReportMapper.findAll();
    }

    @Override
    public List<Map<String, Object>> listAllWithEmployee() {
        return dailyReportMapper.findAllWithEmployee();
    }
}