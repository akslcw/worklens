package com.su.worklens_backend.config;

import com.su.worklens_backend.service.LlmProvider;
import com.su.worklens_backend.service.impl.DeepSeekLlmProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class LlmConfiguration {

    @Bean
    public LlmProvider llmProvider(
            RestTemplateBuilder restTemplateBuilder,
            @Value("${worklens.llm.deepseek.base-url}") String baseUrl,
            @Value("${worklens.llm.deepseek.api-key}") String apiKey,
            @Value("${worklens.llm.deepseek.model}") String model,
            @Value("${worklens.llm.deepseek.connect-timeout}") Duration connectTimeout,
            @Value("${worklens.llm.deepseek.read-timeout}") Duration readTimeout
    ) {
        if (apiKey == null || apiKey.isBlank()) {
            // Keep the application bootable without a real key (documented
            // behaviour): every LLM call fails with a clear message and the
            // report pipeline retains its source data.
            return prompt -> {
                throw new IllegalStateException("DeepSeek API key is not configured");
            };
        }
        return new DeepSeekLlmProvider(
                restTemplateBuilder
                        .setConnectTimeout(connectTimeout)
                        .setReadTimeout(readTimeout)
                        .build(),
                baseUrl,
                apiKey,
                model
        );
    }
}
