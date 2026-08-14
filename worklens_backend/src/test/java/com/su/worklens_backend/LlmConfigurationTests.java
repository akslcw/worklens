package com.su.worklens_backend;

import com.su.worklens_backend.config.LlmConfiguration;
import com.su.worklens_backend.exception.LlmProviderTimeoutException;
import com.su.worklens_backend.service.LlmProvider;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

class LlmConfigurationTests {

    @Test
    void llmProviderWithBlankApiKeyStillBootsAndFailsCallsClearly() {
        LlmProvider provider = new LlmConfiguration().llmProvider(
                "https://api.deepseek.com",
                "   ",
                "deepseek-v4-flash",
                Duration.ofSeconds(1),
                Duration.ofSeconds(1)
        );

        assertThatThrownBy(() -> provider.generateText("prompt"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("DeepSeek API key is not configured");
    }

    @Test
    void llmProviderUsesConfiguredTimeoutsForHangingUpstream() throws Exception {
        try (HangingTcpServer server = HangingTcpServer.start()) {
            LlmProvider provider = new LlmConfiguration().llmProvider(
                    "http://127.0.0.1:" + server.port(),
                    "test-api-key",
                    "deepseek-v4-flash",
                    Duration.ofMillis(200),
                    Duration.ofMillis(400)
            );

            long startedAt = System.nanoTime();

            assertTimeoutPreemptively(Duration.ofSeconds(3), () ->
                    assertThatThrownBy(() -> provider.generateText("timeout prompt"))
                            .isInstanceOf(LlmProviderTimeoutException.class)
                            .hasMessage("DeepSeek API request timed out")
            );

            long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
            assertThat(elapsedMillis).isLessThan(2_000L);
        }
    }

    private static final class HangingTcpServer implements AutoCloseable {

        private final ServerSocket serverSocket;
        private final ExecutorService executorService;
        private final List<Socket> acceptedSockets;
        private final Future<?> acceptLoop;

        private HangingTcpServer() throws IOException {
            this.serverSocket = new ServerSocket(0);
            this.executorService = Executors.newSingleThreadExecutor();
            this.acceptedSockets = new CopyOnWriteArrayList<>();
            this.acceptLoop = executorService.submit(() -> {
                while (!serverSocket.isClosed()) {
                    try {
                        Socket socket = serverSocket.accept();
                        acceptedSockets.add(socket);
                    } catch (IOException exception) {
                        if (!serverSocket.isClosed()) {
                            throw exception;
                        }
                    }
                }
                return null;
            });
        }

        static HangingTcpServer start() throws IOException {
            return new HangingTcpServer();
        }

        int port() {
            return serverSocket.getLocalPort();
        }

        @Override
        public void close() throws Exception {
            List<Exception> closeFailures = new ArrayList<>();
            for (Socket socket : acceptedSockets) {
                try {
                    socket.close();
                } catch (IOException exception) {
                    closeFailures.add(exception);
                }
            }
            try {
                serverSocket.close();
            } catch (IOException exception) {
                closeFailures.add(exception);
            }
            acceptLoop.cancel(true);
            executorService.shutdownNow();
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                closeFailures.add(new IllegalStateException("Timed out waiting for hanging TCP server to stop"));
            }
            if (!closeFailures.isEmpty()) {
                throw closeFailures.get(0);
            }
        }
    }
}
