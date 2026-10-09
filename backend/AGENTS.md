# Backend guidance

Java 17 + Spring Boot 3.4.3 + Spring AI 1.0.9 + SQLite。保持单 Maven 模块，采用 JdbcTemplate，不引入 MySQL、MyBatis、多租户或基础设施依赖。

## 命令

在 `backend/` 中执行：`mvn spring-boot:run`、`mvn test`、`mvn -DskipTests package`。

## 规则入口

- 修改 Java、SQL、配置或目录时，读取 [.codex/guidance/coding-conventions.md](.codex/guidance/coding-conventions.md)。
- 修改 Agent、SSE、工具、历史或取消行为时，读取 [.codex/guidance/agent-lifecycle.md](.codex/guidance/agent-lifecycle.md)。

分层、包命名、配置和异步约定只在上述文件维护。交付前核对适用 guidance 并运行相关验证。
