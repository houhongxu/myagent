package dev.myagent.business.service.agent;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import dev.myagent.business.dto.agent.AgentEvent;
import dev.myagent.business.dto.agent.AgentToolResult;
import org.reactivestreams.Subscription;
import org.springframework.ai.chat.messages.Message;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** One turn owns cancellation, output and resources. No socket write delays upstream cancellation. */
public final class AgentTurn {
    public interface Completion {
        void save(String state, String answer, String error, List<AgentToolResult> tools, List<Message> history);
    }
    public final String requestId;
    public final String conversationId;
    public final SseEmitter emitter;
    private final Completion completion;
    private final AtomicBoolean terminal = new AtomicBoolean();
    private final AtomicReference<Subscription> subscription = new AtomicReference<>();
    private final AtomicReference<Future<?>> worker = new AtomicReference<>();
    private final AtomicReference<Future<?>> heartbeat = new AtomicReference<>();
    private final AtomicReference<Future<?>> deadline = new AtomicReference<>();
    private final ReentrantLock sendLock = new ReentrantLock();
    private final Object stateLock = new Object();
    private final StringBuilder answer = new StringBuilder();
    private final List<AgentToolResult> tools = new ArrayList<>();
    private volatile Thread workerThread;

    public AgentTurn(String requestId, String conversationId, long timeoutMillis, Completion completion) {
        this.requestId = requestId;
        this.conversationId = conversationId;
        this.completion = completion;
        emitter = new SseEmitter(timeoutMillis) {
            @Override
            protected void extendResponse(ServerHttpResponse response) {
                super.extendResponse(response);
                response.getHeaders().set("Cache-Control", "no-cache, no-store, no-transform");
                response.getHeaders().set("X-Accel-Buffering", "no");
            }
        };
        emitter.onTimeout(() -> fail("timeout", "生成超时，请重试"));
        emitter.onError(error -> fail("cancelled", "连接已断开"));
        emitter.onCompletion(() -> fail("cancelled", "连接已断开"));
    }

    public void ensureActive() {
        if (terminal.get() || Thread.currentThread().isInterrupted()) throw new CancellationException();
    }

    public boolean finished() { return terminal.get(); }
    public void markWorker() { workerThread = Thread.currentThread(); }

    public void attachSubscription(Subscription value) {
        subscription.set(value);
        if (terminal.get()) value.cancel();
    }
    public void attachWorker(Future<?> value) { attach(worker, value); }
    public void attachHeartbeat(Future<?> value) { attach(heartbeat, value); }
    public void attachDeadline(Future<?> value) { attach(deadline, value); }
    private void attach(AtomicReference<Future<?>> slot, Future<?> value) {
        slot.set(value);
        if (terminal.get()) value.cancel(true);
    }

    public void delta(String text) {
        synchronized (stateLock) {
            ensureActive();
            if (answer.length() + text.length() > 24000) throw new IllegalStateException("Output limit reached");
            answer.append(text);
        }
        emit("delta", Map.of("text", text));
    }

    public void toolResult(AgentToolResult result) {
        synchronized (stateLock) {
            ensureActive();
            tools.add(result);
        }
        emit("tool_result", result);
    }

    public void emit(String type, Object data) {
        sendLock.lock();
        try {
            ensureActive();
            send(type, data);
        } catch (IOException | IllegalStateException exception) {
            fail("cancelled", "连接已断开");
            throw new CancellationException();
        } finally { sendLock.unlock(); }
    }

    public void beat() {
        if (terminal.get() || !sendLock.tryLock()) return;
        try {
            if (!terminal.get()) emitter.send(SseEmitter.event().comment("heartbeat"));
        } catch (IOException | IllegalStateException exception) {
            fail("cancelled", "连接已断开");
        } finally { sendLock.unlock(); }
    }

    public void complete(List<Message> messages) { finish("done", null, null, messages); }
    public void fail(String code, String error) {
        finish("cancelled".equals(code) ? "cancelled" : "error", code, error, List.of());
    }

    private void finish(String state, String code, String error, List<Message> history) {
        if (!terminal.compareAndSet(false, true)) return;
        Subscription current = subscription.getAndSet(null);
        if (current != null) current.cancel();
        cancel(heartbeat, false);
        cancel(deadline, false);
        cancel(worker, Thread.currentThread() != workerThread);
        String text;
        List<AgentToolResult> results;
        synchronized (stateLock) {
            text = answer.toString();
            results = List.copyOf(tools);
        }
        try {
            completion.save(state, text, error, results, history);
        } catch (RuntimeException exception) {
            state = "error";
            code = "storage_failed";
            error = "保存对话失败，请检查后端存储";
        }
        if (sendLock.tryLock()) {
            try {
                if ("done".equals(state)) send("done", Map.of());
                else send("error", Map.of("code", code, "message", error, "retryable", !"cancelled".equals(state)));
            } catch (IOException | IllegalStateException ignored) {
                // The database commit and delivery of a terminal network frame cannot be atomic.
            } finally { sendLock.unlock(); }
        }
        emitter.complete();
    }

    private void cancel(AtomicReference<Future<?>> slot, boolean interrupt) {
        Future<?> future = slot.getAndSet(null);
        if (future != null) future.cancel(interrupt);
    }

    private void send(String type, Object data) throws IOException {
        emitter.send(SseEmitter.event().name(type).data(new AgentEvent(type, requestId, conversationId, data)));
    }
}
