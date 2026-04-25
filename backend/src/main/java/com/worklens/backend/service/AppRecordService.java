package com.worklens.backend.service;

import com.worklens.backend.dto.UploadRecordDTO;
import com.worklens.backend.entity.AppRecord;
import java.util.List;

public interface AppRecordService {
    void upload(UploadRecordDTO dto);
    List<AppRecord> listByEmployeeAndDate(Long employeeId, String date);
}