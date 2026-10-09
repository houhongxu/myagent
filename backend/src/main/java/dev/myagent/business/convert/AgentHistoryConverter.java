package dev.myagent.business.convert;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Component;

/** Explicit persistence format: complete assistant tool calls always travel with their results. */
@Component
public class AgentHistoryConverter {
    private final ObjectMapper mapper;
    public AgentHistoryConverter(ObjectMapper mapper) { this.mapper = mapper; }

    public record StoredMessage(String role, String text, List<AssistantMessage.ToolCall> calls,
                                List<ToolResponseMessage.ToolResponse> responses) {}

    public String encode(List<Message> messages) {
        List<StoredMessage> stored = messages.stream().map(message -> {
            if (message instanceof UserMessage) return new StoredMessage("user", message.getText(), null, null);
            if (message instanceof AssistantMessage assistant)
                return new StoredMessage("assistant", assistant.getText(), assistant.getToolCalls(), null);
            if (message instanceof ToolResponseMessage tool)
                return new StoredMessage("tool", "", null, tool.getResponses());
            throw new IllegalArgumentException("Only conversation messages can be persisted");
        }).toList();
        return write(stored);
    }

    public List<Message> decode(String json) {
        List<StoredMessage> stored = read(json, new TypeReference<>() {});
        List<Message> messages = new ArrayList<>();
        for (StoredMessage message : stored) {
            messages.add(switch (message.role()) {
                case "user" -> new UserMessage(message.text());
                case "assistant" -> new AssistantMessage(message.text(), Map.of(), message.calls());
                case "tool" -> new ToolResponseMessage(message.responses());
                default -> throw new IllegalStateException("Unknown stored message role");
            });
        }
        return messages;
    }

    public String write(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("Unable to store conversation", exception); }
    }

    public <T> T read(String json, TypeReference<T> type) {
        try { return mapper.readValue(json, type); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("Unable to read conversation", exception); }
    }
}
