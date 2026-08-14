package com.su.worklens_backend.config;

import com.su.worklens_backend.service.LlmProvider;
import com.su.worklens_backend.service.impl.DeepSeekLlmProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
public class LlmConfiguration {

    @Bean
    public LlmProvider llmProvider(
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

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeout);
        requestFactory.setReadTimeout(readTimeout);
        RestClient restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .build();

        return new DeepSeekLlmProvider(
                restClient,
                baseUrl,
                apiKey,
                model
        );
    }
}
