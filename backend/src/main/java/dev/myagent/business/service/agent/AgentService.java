package dev.myagent.business.service.agent;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import dev.myagent.business.dto.agent.*;
import dev.myagent.config.AgentProperties;
import dev.myagent.business.repository.ConversationRepository;
import dev.myagent.business.convert.AgentHistoryConverter;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.MessageAggregator;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class AgentService {
    private static final Logger log = LoggerFactory.getLogger(AgentService.class);
    private final AgentProperties properties;
    private final ConversationRepository conversations;
    private final AgentHistoryConverter codec;
    private final OpenAiChatModel model;
    private final ThreadPoolTaskExecutor executor;
    private final ThreadPoolTaskScheduler timer;
    private final ConcurrentHashMap<String, AgentTurn> active = new ConcurrentHashMap<>();
    private final ToolCallingManager tools = ToolCallingManager.builder()
            .toolExecutionExceptionProcessor(exception -> { throw exception; }).build();
    private final String systemPrompt;

    public AgentService(AgentProperties properties, ConversationRepository conversations, AgentHistoryConverter codec,
                        OpenAiChatModel model, ThreadPoolTaskExecutor executor, ThreadPoolTaskScheduler timer) throws IOException {
        this.properties = properties;
        this.conversations = conversations;
        this.codec = codec;
        this.model = model;
        this.executor = executor;
        this.timer = timer;
        systemPrompt = new ClassPathResource("system-prompt.txt").getContentAsString(StandardCharsets.UTF_8);
    }

    /** Serialize admission only, not generation. One active turn per conversation. */
    public synchronized SseEmitter chat(String requestId, String conversationId, String message) {
        if (!properties.configured()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "请先配置后端 AGENT_API_KEY");
        String id = conversationId;
        requireIdle(id);
        if (active.size() >= properties.maxConcurrentStreams())
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "当前生成任务较多，请稍后重试");
        if (conversations.hasRequest(requestId))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "请求已提交，请使用新的 requestId");
        List<Message> history = conversations.history(id, properties.historyTurns());
        conversations.start(requestId, id, message.strip());
        AgentTurn turn = new AgentTurn(requestId, id,
                (properties.timeoutSeconds() + 5L) * 1000, (state, answer, error, results, messages) -> {
                    try { conversations.finish(requestId, id, state, answer, error, results, messages); }
                    finally { active.remove(id); }
                });
        active.put(id, turn);
        try {
            turn.attachDeadline(timer.schedule(() -> turn.fail("timeout", "生成超时，请重试"),
                    Instant.now().plusSeconds(properties.timeoutSeconds())));
            turn.attachHeartbeat(timer.scheduleAtFixedRate(turn::beat, Instant.now().plusSeconds(10), Duration.ofSeconds(10)));
            turn.attachWorker(executor.submit(() -> run(turn, message.strip(), history)));
        } catch (RejectedExecutionException exception) {
            turn.fail("busy", "当前生成任务较多，请稍后重试");
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "当前生成任务较多，请稍后重试");
        }
        return turn.emitter;
    }

    public AgentCancelResult cancel(String requestId, String conversationId) {
        AgentTurn turn = active.get(conversationId);
        if (turn == null || !turn.requestId.equals(requestId)) return new AgentCancelResult(false);
        turn.fail("cancelled", "已停止生成");
        return new AgentCancelResult(true);
    }

    public AgentConfigResult getConfig() {
        return new AgentConfigResult(properties.configured(), properties.model(), 4000,
                properties.timeoutSeconds(), List.of("current_time", "calculate"));
    }

    public AgentConversation createConversation() { return conversations.create(); }

    public AgentConversationDetail getConversation(String id) { return conversations.detail(id); }

    public List<AgentConversation> listConversations() { return conversations.list(); }

    public AgentConversation modifyConversation(String id, String title) { return conversations.rename(id, title); }

    public synchronized void deleteConversation(String id) {
        requireIdle(id);
        conversations.delete(id);
    }

    private void requireIdle(String id) {
        if (active.containsKey(id)) throw new ResponseStatusException(HttpStatus.CONFLICT, "此对话正在生成，请先停止");
    }

    private void run(AgentTurn turn, String message, List<Message> history) {
        turn.markWorker();
        try {
            turn.emit("start", Map.of());
            turn.emit("status", Map.of("text", "正在思考"));
            turn.complete(generate(turn, message, history));
        } catch (CancellationException exception) {
            turn.fail("cancelled", "已停止生成");
        } catch (Exception exception) {
            if (turn.finished()) return;
            log.warn("Agent request {} failed ({})", turn.requestId, exception.getClass().getSimpleName());
            turn.fail("generation_failed", "生成失败，请检查模型配置或稍后重试");
        }
    }

    /** Explicit model → tool → model loop with bounded, generic tools. */
    private List<Message> generate(AgentTurn turn, String message, List<Message> history) {
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(systemPrompt));
        messages.addAll(history);
        int start = messages.size();
        messages.add(new UserMessage(message));
        var options = OpenAiChatOptions.builder().internalToolExecutionEnabled(false)
                .toolCallbacks(ToolCallbacks.from(new AgentTools())).build();
        int toolCount = 0;
        while (true) {
            turn.ensureActive();
            if (messages.stream().mapToInt(m -> m.toString().length()).sum() > 96000)
                throw new IllegalStateException("Context limit reached");
            Prompt prompt = new Prompt(messages, options);
            ChatResponse response = stream(turn, prompt);
            if (!response.hasToolCalls()) {
                messages.add(response.getResult().getOutput());
                List<Message> completed = List.copyOf(messages.subList(start, messages.size()));
                if (codec.encode(completed).length() > 64000) throw new IllegalStateException("History limit reached");
                return completed;
            }
            var calls = response.getResult().getOutput().getToolCalls();
            toolCount += calls.size();
            if (toolCount > properties.maxToolCalls()) throw new IllegalStateException("Tool limit reached");
            for (var call : calls) {
                if (call.arguments().length() > 4096) throw new IllegalStateException("Tool arguments too large");
                turn.emit("tool_start", Map.of("id", call.id(), "name", call.name(), "arguments", call.arguments()));
            }
            turn.ensureActive();
            messages = new ArrayList<>(tools.executeToolCalls(prompt, response).conversationHistory());
            Message last = messages.get(messages.size() - 1);
            if (last instanceof ToolResponseMessage results) {
                for (var result : results.getResponses()) {
                    String args = calls.stream().filter(call -> call.id().equals(result.id())).findFirst().orElseThrow().arguments();
                    turn.toolResult(new AgentToolResult(result.id(), result.name(), args, result.responseData()));
                }
            }
            turn.emit("status", Map.of("text", "正在整理结果"));
        }
    }

    private ChatResponse stream(AgentTurn turn, Prompt prompt) {
        AtomicReference<ChatResponse> result = new AtomicReference<>();
        var stream = new MessageAggregator().aggregate(model.stream(prompt), result::set).doOnSubscribe(turn::attachSubscription);
        for (ChatResponse chunk : stream.toIterable(1)) {
            turn.ensureActive();
            if (chunk.getResult() == null) continue;
            String text = chunk.getResult().getOutput().getText();
            if (text != null && !text.isEmpty()) turn.delta(text);
        }
        ChatResponse response = result.get();
        if (response == null || response.getResult() == null) throw new IllegalStateException("Empty model response");
        String finish = response.getResult().getMetadata().getFinishReason();
        if (!"stop".equalsIgnoreCase(finish) && !"tool_calls".equalsIgnoreCase(finish))
            throw new IllegalStateException("Model did not finish normally");
        return response;
    }

    @PreDestroy
    void shutdown() {
        List.copyOf(active.values()).forEach(turn -> turn.fail("cancelled", "服务正在关闭"));
    }
}
