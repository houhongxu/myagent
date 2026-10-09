package dev.myagent.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.sql.DataSource;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteDataSource;

@Configuration
@EnableConfigurationProperties(AgentProperties.class)
public class AppConfig {
    @Bean
    DataSource dataSource(AgentProperties properties) throws IOException {
        Path path = Path.of(properties.dbPath()).toAbsolutePath();
        Files.createDirectories(path.getParent());
        SQLiteConfig config = new SQLiteConfig();
        config.enforceForeignKeys(true);
        config.setBusyTimeout(5000);
        config.setJournalMode(SQLiteConfig.JournalMode.WAL);
        SQLiteDataSource source = new SQLiteDataSource(config);
        source.setUrl("jdbc:sqlite:" + path);
        return source;
    }

    @Bean
    OpenAiChatModel chatModel(AgentProperties properties) {
        OpenAiApi api = OpenAiApi.builder()
                .baseUrl(properties.baseUrl()).completionsPath(properties.completionsPath())
                .apiKey(properties.configured() ? properties.apiKey() : "not-configured").build();
        return OpenAiChatModel.builder().openAiApi(api)
                .defaultOptions(OpenAiChatOptions.builder().model(properties.model())
                        .reasoningEffort(properties.reasoningEffort() == null || properties.reasoningEffort().isBlank()
                                ? null : properties.reasoningEffort())
                        .maxTokens(properties.maxTokens()).internalToolExecutionEnabled(false).build())
                .retryTemplate(RetryTemplate.builder().maxAttempts(1).build()).build();
    }

    @Bean
    ThreadPoolTaskExecutor agentExecutor(AgentProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.maxConcurrentStreams());
        executor.setMaxPoolSize(properties.maxConcurrentStreams());
        executor.setQueueCapacity(0);
        executor.setThreadNamePrefix("agent-");
        return executor;
    }

    @Bean
    ThreadPoolTaskScheduler agentTimer() {
        ThreadPoolTaskScheduler timer = new ThreadPoolTaskScheduler();
        timer.setPoolSize(2);
        timer.setRemoveOnCancelPolicy(true);
        timer.setThreadNamePrefix("agent-timer-");
        return timer;
    }
}
