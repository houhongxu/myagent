# Backend

JDK 17 + Maven 3.9+。首次运行参见[根目录启动说明](../README.md#快速启动)，填写根目录 `.env` 或 `backend/.env` 后：

```sh
mvn spring-boot:run
```

后端自动加载 `.env`，无需手动 `source`。DeepSeek 配置见 `.env.example`。默认地址 `http://127.0.0.1:8080`；SQLite 自动创建到 `data/agent.db`。没有 Key 时仍可管理会话，聊天接口返回 503。

```sh
mvn test
mvn -DskipTests package
java -jar target/myagent-backend-0.1.0.jar
```

从 `business/controller/AgentController` → `business/service/agent/AgentService` → `business/service/agent/AgentTurn` 阅读一次请求；持久化位于 `business/repository/ConversationRepository`。工具在 `business/service/agent/AgentTools`，提示词在 `src/main/resources/system-prompt.txt`。
