package com.worklens.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.worklens.backend.entity.AppCategory;
import com.worklens.backend.entity.AppRecord;
import com.worklens.backend.entity.DailyReport;
import com.worklens.backend.mapper.AppCategoryMapper;
import com.worklens.backend.mapper.DailyReportMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiAnalysisService {

    @Value("${deepseek.api-key}")
    private String apiKey;

    @Value("${deepseek.api-url}")
    private String apiUrl;

    @Value("${deepseek.model}")
    private String model;

    private final AppCategoryMapper appCategoryMapper;
    private final DailyReportMapper dailyReportMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final OkHttpClient httpClient = new OkHttpClient();

    public void analyze(Long employeeId, LocalDate date, List<AppRecord> records) {
        // 第一层：规则分类统计
        int workSeconds = 0;
        int idleSeconds = 0;

        for (AppRecord record : records) {
            AppCategory category = appCategoryMapper.findByAppName(record.getAppName());
            if (category != null && Boolean.TRUE.equals(category.getIsWork())) {
                workSeconds += record.getDurationSeconds();
            } else {
                idleSeconds += record.getDurationSeconds();
            }
        }

        // 第二层：AI 生成点评
        String comment = generateComment(records, workSeconds, idleSeconds);

        // 计算效率评分：工作时长占比 * 100，最高100
        int total = workSeconds + idleSeconds;
        int score = total > 0 ? Math.min(100, (int) ((double) workSeconds / total * 100)) : 0;

        // 存入数据库
        DailyReport report = new DailyReport();
        report.setEmployeeId(employeeId);
        report.setReportDate(date);
        report.setWorkSeconds(workSeconds);
        report.setIdleSeconds(idleSeconds);
        report.setEfficiencyScore(score);
        report.setAiComment(comment);
        dailyReportMapper.insertOrUpdate(report);

        log.info("员工 {} 的 {} 分析完成，效率评分：{}", employeeId, date, score);
    }

    private String generateComment(List<AppRecord> records, int workSeconds, int idleSeconds) {
        String summary = records.stream()
                .map(r -> String.format("%s(%d分钟)", r.getAppName(), r.getDurationSeconds() / 60))
                .collect(Collectors.joining("、"));

        String prompt = String.format(
                "员工今日应用使用情况：%s。工作相关时长%d分钟，非工作时长%d分钟。" +
                        "请用一句话（不超过80字）给出简短点评和建议，语气专业友好。只返回点评文字，不要其他内容。",
                summary, workSeconds / 60, idleSeconds / 60
        );

        try {
            Map<String, Object> message = new HashMap<>();
            message.put("role", "user");
            message.put("content", prompt);

            Map<String, Object> body = new HashMap<>();
            body.put("model", model);
            List<Map<String, Object>> messages = new ArrayList<>();
            messages.add(message);
            body.put("messages", messages);
            body.put("max_tokens", 200);

            String requestBody = objectMapper.writeValueAsString(body);

            Request request = new Request.Builder()
                    .url(apiUrl)
                    .addHeader("Authorization", "Bearer " + apiKey)
                    .addHeader("Content-Type", "application/json")
                    .post(RequestBody.create(requestBody, MediaType.get("application/json")))
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    log.error("DeepSeek 调用失败: {}", response.code());
                    return "AI 分析暂时不可用";
                }
                JsonNode root = objectMapper.readTree(response.body().string());
                return root.path("choices").get(0).path("message").path("content").asText();
            }
        } catch (Exception e) {
            log.error("AI 分析异常: {}", e.getMessage());
            return "AI 分析暂时不可用";
        }
    }
}