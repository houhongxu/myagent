package dev.myagent.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("agent")
public record AgentProperties(
        String apiKey, @NotBlank String baseUrl, @NotBlank String completionsPath,
        @NotBlank String model, String reasoningEffort, @NotBlank String dbPath,
        @Min(128) @Max(8192) int maxTokens,
        @Min(1) @Max(12) int maxToolCalls,
        @Min(1) @Max(20) int historyTurns,
        @Min(1) @Max(300) int timeoutSeconds,
        @Min(1) @Max(8) int maxConcurrentStreams) {
    public boolean configured() {
        return apiKey != null && !apiKey.isBlank();
    }
}
