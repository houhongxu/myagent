# 后端组织与编码

## 目录与命名

- 根包为 `dev.myagent`；包名全小写，不用下划线或驼峰。Java 类名 PascalCase，文件名与类名一致。
- `business/controller/`：HTTP 入口和异常响应。Controller 只校验/拆解参数并委托 Service，不直接调用 Repository 或组装业务数据。
- `business/service/agent/`：Agent 与会话编排、工具调用、流生命周期。
- `business/dto/agent/`：独立请求/响应 DTO，一个公开 DTO 一个文件；可使用 Java record。
- `business/repository/`：SQLite SQL、行映射及事务。
- `business/convert/`：模型消息与存储结构转换。
- `config/`：模型、数据库、线程池 Bean 及 ConfigurationProperties。
- 只在确有跨 Service 编排、独立实体映射等需求时新增 manager/entity 层。

## 职责与实现

- 请求 DTO 使用 `AgentChatDto` 等“领域 + 动作 + Dto”命名；不把多个 HTTP DTO 塞进一个容器类。
- Service 接收显式参数，使用构造器注入；不隐式读取请求/租户 ThreadLocal。
- 字段成组配置用 `@ConfigurationProperties`，密钥读取环境变量或被忽略的本地 `.env`，不硬编码。
- SQL 使用参数绑定；关联写入使用事务，关键更新/删除检查影响行数。时间使用 java.time，数据库时间采用 UTC。
- 优先卫语句、清晰命名和小方法；注释说明契约、边界或并发原因，避免逐行翻译代码。
- 不为简单转换增加 MapStruct，不为基本空值判断增加工具依赖，不机械创建接口与实现类两层。
- 异步使用注入的有界线程池；业务代码不用公共线程池或随意新建线程。
- HTTP 异常由统一 handler 输出，SSE 开始后的错误由终态事件输出；不泄漏供应商原始响应、Key 或用户完整内容。
