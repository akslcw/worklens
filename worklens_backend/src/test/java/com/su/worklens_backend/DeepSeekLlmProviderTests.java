package com.su.worklens_backend;

import com.su.worklens_backend.exception.LlmProviderException;
import com.su.worklens_backend.exception.LlmProviderTimeoutException;
import com.su.worklens_backend.service.impl.DeepSeekLlmProvider;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.net.SocketTimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class DeepSeekLlmProviderTests {

    @Test
    void generateTextPostsPromptToDeepSeekAndReturnsAssistantMessage() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        DeepSeekLlmProvider provider = new DeepSeekLlmProvider(
                builder.build(),
                "https://api.deepseek.com",
                "test-api-key",
                "deepseek-v4-flash"
        );

        server.expect(requestTo("https://api.deepseek.com/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer test-api-key"))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("""
                        {
                          "model": "deepseek-v4-flash",
                          "messages": [
                            {
                              "role": "user",
                              "content": "Summarize this fixed text."
                            }
                          ]
                        }
                        """, true))
                .andRespond(withSuccess("""
                        {
                          "choices": [
                            {
                              "message": {
                                "content": "This is the generated response."
                              }
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        String response = provider.generateText("Summarize this fixed text.");

        assertThat(response).isEqualTo("This is the generated response.");
        server.verify();
    }

    @Test
    void generateTextWrapsTimeoutAsLlmProviderTimeoutException() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        DeepSeekLlmProvider provider = new DeepSeekLlmProvider(
                builder.build(),
                "https://api.deepseek.com",
                "test-api-key",
                "deepseek-v4-flash"
        );

        server.expect(requestTo("https://api.deepseek.com/chat/completions"))
                .andRespond(request -> {
                    throw new ResourceAccessException("Read timed out", new SocketTimeoutException("Read timed out"));
                });

        assertThatThrownBy(() -> provider.generateText("timeout prompt"))
                .isInstanceOf(LlmProviderTimeoutException.class)
                .hasMessage("DeepSeek API request timed out");
    }

    @Test
    void generateTextWrapsHttpFailureAsLlmProviderException() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        DeepSeekLlmProvider provider = new DeepSeekLlmProvider(
                builder.build(),
                "https://api.deepseek.com",
                "test-api-key",
                "deepseek-v4-flash"
        );

        server.expect(requestTo("https://api.deepseek.com/chat/completions"))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {
                                  "error": {
                                    "message": "upstream failed"
                                  }
                                }
                                """));

        assertThatThrownBy(() -> provider.generateText("server error prompt"))
                .isInstanceOf(LlmProviderException.class)
                .hasMessage("DeepSeek API request failed");
    }
}
