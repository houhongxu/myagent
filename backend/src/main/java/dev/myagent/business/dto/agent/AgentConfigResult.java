package dev.myagent.business.dto.agent;

import java.util.List;

/** Public configuration; never contains model credentials. */
public record AgentConfigResult(boolean configured, String model, int maxMessageLength,
                                int timeoutSeconds, List<String> tools) {}
