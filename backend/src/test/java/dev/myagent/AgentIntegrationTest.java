package dev.myagent;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.myagent.business.service.agent.AgentTools;
import dev.myagent.config.AgentProperties;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Exercises the real Spring AI OpenAI adapter against a local streaming HTTP fixture. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AgentIntegrationTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final ConcurrentHashMap<String, JsonNode> MODEL_REQUESTS = new ConcurrentHashMap<>();
    private static final java.util.concurrent.ExecutorService FIXTURE_POOL = Executors.newCachedThreadPool();
    private static final HttpServer MODEL = startModel();
    private static final String DB = database();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired AgentProperties properties;

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("agent.api-key", () -> "local-test-key");
        registry.add("agent.base-url", () -> "http://127.0.0.1:" + MODEL.getAddress().getPort());
        registry.add("agent.model", () -> "test-model");
        registry.add("agent.completions-path", () -> "/v1/chat/completions");
        registry.add("agent.reasoning-effort", () -> "none");
        registry.add("agent.db-path", () -> DB);
        registry.add("agent.timeout-seconds", () -> 3);
    }

    @AfterAll
    static void cleanup() {
        MODEL.stop(0);
        FIXTURE_POOL.shutdownNow();
    }

    @Test
    void toolsStreamAndCompleteHistorySurviveSubsequentRequests() throws Exception {
        String id = create();
        String request = UUID.randomUUID().toString();
        HttpResponse<String> response = send("POST", "/api/chat", chat(request, id, "tool-check"));
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("content-type").orElse("")).contains("text/event-stream");
        assertThat(response.body()).contains("event:start", "event:tool_start", "event:tool_result", "event:delta", "event:done");
        JsonNode detail = JSON.readTree(send("GET", "/api/conversations/" + id, null).body());
        assertThat(detail.at("/turns/0/status").asText()).isEqualTo("done");
        assertThat(detail.at("/turns/0/answer").asText()).isEqualTo("结果是 42。你好🌏");
        assertThat(detail.at("/turns/0/tools/0/output").asText()).contains("42");
        assertThat(send("POST", "/api/chat", chat(request, id, "duplicate")).statusCode()).isEqualTo(409);

        assertThat(send("POST", "/api/chat", chat(UUID.randomUUID().toString(), id, "followup-check")).body()).contains("event:done");
        JsonNode messages = MODEL_REQUESTS.get("followup-check").get("messages");
        assertThat(MODEL_REQUESTS.get("followup-check").path("reasoning_effort").asText()).isEqualTo("none");
        assertThat(messages.toString()).contains("tool_call_id", "call_calculate", "tool-check", "42");

        assertThat(send("PATCH", "/api/conversations/" + id, Map.of("title", "计算记录")).statusCode()).isEqualTo(200);
        assertThat(send("GET", "/api/conversations", null).body()).contains("计算记录");
        assertThat(send("DELETE", "/api/conversations/" + id, null).statusCode()).isEqualTo(204);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM turns WHERE conversation_id=?", Integer.class, id)).isZero();
    }

    @Test
    void cancellationAndConcurrentAdmissionDoNotPoisonHistory() throws Exception {
        String id = create();
        String request = UUID.randomUUID().toString();
        String prompt = "stall-" + UUID.randomUUID();
        var pending = http.sendAsync(request("POST", "/api/chat", chat(request, id, prompt)), HttpResponse.BodyHandlers.ofString());
        awaitModel(prompt);
        assertThat(send("POST", "/api/chat", chat(UUID.randomUUID().toString(), id, "busy")).statusCode()).isEqualTo(409);
        assertThat(send("DELETE", "/api/conversations/" + id, null).statusCode()).isEqualTo(409);
        assertThat(send("POST", "/api/chat/cancel", Map.of("requestId", request, "conversationId", id)).body()).contains("true");
        assertThat(pending.get(5, java.util.concurrent.TimeUnit.SECONDS).body()).contains("event:error", "cancelled");
        assertThat(send("POST", "/api/chat/cancel", Map.of("requestId", request, "conversationId", id)).body()).contains("false");
        assertThat(JSON.readTree(send("GET", "/api/conversations/" + id, null).body()).at("/turns/0/status").asText()).isEqualTo("cancelled");
        String followup = "after-cancel-" + UUID.randomUUID();
        assertThat(send("POST", "/api/chat", chat(UUID.randomUUID().toString(), id, followup)).body()).contains("event:done");
        assertThat(MODEL_REQUESTS.get(followup).get("messages").toString()).doesNotContain(prompt);
    }

    @Test
    void totalTimeoutStopsAStreamEvenWhenTokensKeepArriving() throws Exception {
        String id = create();
        long started = System.nanoTime();
        HttpResponse<String> response = send("POST", "/api/chat", chat(UUID.randomUUID().toString(), id, "stall-timeout"));
        assertThat(response.body()).contains("event:error", "timeout").doesNotContain("event:done");
        assertThat(Duration.ofNanos(System.nanoTime() - started).toSeconds()).isLessThan(7);
        assertThat(JSON.readTree(send("GET", "/api/conversations/" + id, null).body()).at("/turns/0/status").asText()).isEqualTo("error");
    }

    @Test
    void abnormalModelCompletionCannotCommitPartialHistory() throws Exception {
        String id = create();
        String result = send("POST", "/api/chat", chat(UUID.randomUUID().toString(), id, "bad-finish")).body();
        assertThat(result).contains("event:error").doesNotContain("event:done");
        assertThat(JSON.readTree(send("GET", "/api/conversations/" + id, null).body()).at("/turns/0/answer").asText()).isNotBlank();
        assertThat(jdbc.queryForObject("SELECT history_json FROM turns WHERE conversation_id=?", String.class, id)).isEqualTo("[]");
    }

    @Test
    void validatesInputAndDoesNotExposeKeysInConfiguration() throws Exception {
        assertThat(send("POST", "/api/chat", Map.of("requestId", "wrong", "message", "hi")).statusCode()).isEqualTo(400);
        assertThat(send("GET", "/api/config", null).body()).contains("test-model").doesNotContain(properties.apiKey());
        assertThat(send("GET", "/api/conversations/" + UUID.randomUUID(), null).statusCode()).isEqualTo(404);
    }

    @Test
    void arithmeticAvoidsFloatingPointErrorsAndRejectsUnboundedExpressions() {
        AgentTools tools = new AgentTools();
        assertThat(tools.calculate("0.1", "add", "0.2")).containsEntry("result", "0.3");
        assertThat(tools.calculate("10", "divide", "0")).containsKey("error");
        assertThat(tools.calculate("1e999999", "multiply", "2")).containsKey("error");
        assertThat(tools.currentTime("not-a-timezone")).containsKey("error");
    }

    private String create() throws Exception {
        HttpResponse<String> response = send("POST", "/api/conversations", Map.of());
        assertThat(response.statusCode()).isEqualTo(201);
        return JSON.readTree(response.body()).get("id").asText();
    }
    private Map<String, String> chat(String requestId, String conversationId, String message) {
        return Map.of("requestId", requestId, "conversationId", conversationId, "message", message);
    }
    private HttpRequest request(String method, String path, Object body) throws Exception {
        return HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)))
                .build();
    }
    private HttpResponse<String> send(String method, String path, Object body) throws Exception {
        return http.send(request(method, path, body), HttpResponse.BodyHandlers.ofString());
    }
    private void awaitModel(String prompt) throws InterruptedException {
        for (int i = 0; i < 100 && !MODEL_REQUESTS.containsKey(prompt); i++) Thread.sleep(10);
        assertThat(MODEL_REQUESTS).containsKey(prompt);
    }
    private static String database() {
        try { return Files.createTempDirectory("myagent-test-").resolve("agent.db").toString(); }
        catch (IOException exception) { throw new IllegalStateException(exception); }
    }
    private static HttpServer startModel() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.setExecutor(FIXTURE_POOL);
            server.createContext("/v1/chat/completions", AgentIntegrationTest::model);
            server.start();
            return server;
        } catch (IOException exception) { throw new IllegalStateException(exception); }
    }

    private static void model(HttpExchange exchange) throws IOException {
        JsonNode body = JSON.readTree(exchange.getRequestBody());
        String prompt = "";
        for (JsonNode message : body.get("messages")) if (message.path("role").asText().equals("user")) prompt = message.path("content").asText();
        MODEL_REQUESTS.put(prompt, body);
        exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
        exchange.sendResponseHeaders(200, 0);
        try {
            if (prompt.startsWith("stall-")) {
                for (int i = 0; i < 100; i++) {
                    chunk(exchange, Map.of("content", "·"), null);
                    Thread.sleep(100);
                }
            } else if (prompt.equals("tool-check") && !body.get("messages").get(body.get("messages").size() - 1).path("role").asText().equals("tool")) {
                chunk(exchange, Map.of("role", "assistant", "tool_calls", List.of(Map.of("index", 0, "id", "call_calculate", "type", "function",
                        "function", Map.of("name", "calculate", "arguments", "{\"left\":\"6\",")))), null);
                chunk(exchange, Map.of("tool_calls", List.of(Map.of("index", 0, "function", Map.of("arguments", "\"operation\":\"multiply\",\"right\":\"7\"}")))), null);
                chunk(exchange, Map.of(), "tool_calls");
            } else {
                chunk(exchange, Map.of("role", "assistant", "content", "结果是 "), null);
                chunk(exchange, Map.of("content", "42。你好🌏"), null);
                chunk(exchange, Map.of(), prompt.equals("bad-finish") ? "length" : "stop");
            }
            exchange.getResponseBody().write("data: [DONE]\n\n".getBytes(StandardCharsets.UTF_8));
        } catch (InterruptedException exception) { Thread.currentThread().interrupt(); }
        catch (IOException ignored) { /* Cancelled clients close their upstream request. */ }
        finally { exchange.close(); }
    }
    private static void chunk(HttpExchange exchange, Map<String, Object> delta, String finish) throws IOException {
        var choice = new java.util.HashMap<String, Object>();
        choice.put("index", 0); choice.put("delta", delta); choice.put("finish_reason", finish);
        var chunk = Map.of("id", "fixture", "object", "chat.completion.chunk", "created", 1, "model", "test-model", "choices", List.of(choice));
        exchange.getResponseBody().write(("data: " + JSON.writeValueAsString(chunk) + "\n\n").getBytes(StandardCharsets.UTF_8));
        exchange.getResponseBody().flush();
    }
}
