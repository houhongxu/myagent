package dev.myagent.business.dto.agent;

import java.util.List;

/** Conversation with display history. */
public record AgentConversationDetail(AgentConversation conversation, List<AgentTurnResult> turns) {}
