package com.worklens.backend.service;

import com.worklens.backend.entity.AppRecord;
import com.worklens.backend.entity.Employee;
import com.worklens.backend.mapper.EmployeeMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduledTaskService {

    private final EmployeeMapper employeeMapper;
    private final AppRecordService appRecordService;
    private final AiAnalysisService aiAnalysisService;

    @Scheduled(cron = "0 30 23 * * ?")
    public void dailyAnalysis() {
        log.info("开始执行每日 AI 分析任务...");
        LocalDate today = LocalDate.now();
        List<Employee> employees = employeeMapper.findAll();
        for (Employee employee : employees) {
            try {
                List<AppRecord> records = appRecordService.listByEmployeeAndDate(
                        employee.getId(), today.toString()
                );
                if (records.isEmpty()) {
                    log.info("员工 {} 今天没有使用记录，跳过", employee.getName());
                    continue;
                }
                aiAnalysisService.analyze(employee.getId(), today, records);
            } catch (Exception e) {
                log.error("员工 {} 分析失败: {}", employee.getName(), e.getMessage());
            }
        }
        log.info("每日 AI 分析任务完成");
    }

    public void triggerForEmployee(Long employeeId, LocalDate date) {
        List<AppRecord> records = appRecordService.listByEmployeeAndDate(
                employeeId, date.toString()
        );
        if (records.isEmpty()) {
            log.warn("员工 {} 在 {} 没有使用记录", employeeId, date);
            return;
        }
        aiAnalysisService.analyze(employeeId, date, records);
    }
}