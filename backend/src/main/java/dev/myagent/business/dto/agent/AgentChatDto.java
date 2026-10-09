package dev.myagent.business.dto.agent;

import java.util.UUID;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Start one turn. Both identifiers must be UUIDs. */
public record AgentChatDto(@NotNull UUID requestId, @NotNull UUID conversationId,
                           @NotBlank @Size(max = 4000) String message) {}
