package com.worklens.backend.service;

import com.worklens.backend.dto.UploadBatchDTO;
import com.worklens.backend.dto.UploadRecordDTO;
import com.worklens.backend.entity.AppRecord;
import java.util.List;

public interface AppRecordService {
    void upload(UploadBatchDTO dto);
    List<AppRecord> listByEmployeeAndDate(Long employeeId, String date);
    List<AppRecord> listByEmployeeAndDateRange(Long employeeId, String startDate, String endDate);
}