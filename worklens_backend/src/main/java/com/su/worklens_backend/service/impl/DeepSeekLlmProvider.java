package com.su.worklens_backend.service.impl;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.su.worklens_backend.exception.LlmProviderException;
import com.su.worklens_backend.exception.LlmProviderTimeoutException;
import com.su.worklens_backend.service.LlmProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.net.SocketTimeoutException;
import java.util.List;

public class DeepSeekLlmProvider implements LlmProvider {

    private final RestClient restClient;
    private final String baseUrl;
    private final String apiKey;
    private final String model;

    public DeepSeekLlmProvider(RestClient restClient, String baseUrl, String apiKey, String model) {
        this.restClient = restClient;
        this.baseUrl = trimTrailingSlash(baseUrl);
        this.apiKey = apiKey;
        this.model = model;
    }

    @Override
    public String generateText(String prompt) {
        if (!StringUtils.hasText(apiKey)) {
            throw new IllegalStateException("DeepSeek API key is not configured");
        }

        DeepSeekChatCompletionRequest requestBody = new DeepSeekChatCompletionRequest(
                model,
                List.of(new DeepSeekMessage("user", prompt))
        );

        DeepSeekChatCompletionResponse response;
        try {
            response = restClient.post()
                    .uri(baseUrl + "/chat/completions")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(DeepSeekChatCompletionResponse.class);
        } catch (RuntimeException exception) {
            if (isTimeout(exception)) {
                throw new LlmProviderTimeoutException("DeepSeek API request timed out", exception);
            }
            throw new LlmProviderException("DeepSeek API request failed", exception);
        }

        if (response == null || response.choices() == null || response.choices().isEmpty()) {
            throw new LlmProviderException("DeepSeek API response did not contain any choices");
        }

        DeepSeekMessage message = response.choices().get(0).message();
        if (message == null || !StringUtils.hasText(message.content())) {
            throw new LlmProviderException("DeepSeek API response did not contain any assistant message");
        }

        return message.content().trim();
    }

    private boolean isTimeout(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof SocketTimeoutException
                    || current instanceof java.net.http.HttpTimeoutException) {
                return true;
            }
            String message = current.getMessage();
            if (message != null && message.toLowerCase().contains("timed out")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private String trimTrailingSlash(String url) {
        if (!StringUtils.hasText(url)) {
            return "";
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private record DeepSeekChatCompletionRequest(String model, List<DeepSeekMessage> messages) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record DeepSeekChatCompletionResponse(List<DeepSeekChoice> choices) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record DeepSeekChoice(DeepSeekMessage message) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record DeepSeekMessage(String role, String content) {
    }
}
