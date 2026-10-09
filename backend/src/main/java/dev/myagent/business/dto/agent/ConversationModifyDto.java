package dev.myagent.business.dto.agent;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Rename a conversation. */
public record ConversationModifyDto(@NotBlank @Size(max = 80) String title) {}
