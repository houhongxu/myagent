package dev.myagent.business.dto.agent;

/** A completed tool call and its result. */
public record AgentToolResult(String id, String name, String arguments, String output) {}
