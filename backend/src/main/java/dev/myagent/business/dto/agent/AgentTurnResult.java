package dev.myagent.business.dto.agent;

import java.util.List;

/** Persisted turn, including partial output on failure or cancellation. */
public record AgentTurnResult(String id, String prompt, String answer, String status,
                              String error, List<AgentToolResult> tools, String createdAt) {}
