package dev.myagent.business.dto.agent;

import java.util.UUID;
import jakarta.validation.constraints.NotNull;

/** Cancel exactly one active turn. */
public record AgentCancelDto(@NotNull UUID requestId, @NotNull UUID conversationId) {}
