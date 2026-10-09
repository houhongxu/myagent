package dev.myagent.business.dto.agent;

/** Common envelope for every SSE event. */
public record AgentEvent(String type, String requestId, String conversationId, Object data) {}
