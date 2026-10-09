# MyAgent

轻量的通用 Agent 参考实现。前后端独立，保留流式聊天、显式工具调用和会话历史，使用 SQLite，不需要 Redis、MySQL 或向量数据库。

## 快速启动

环境：JDK 17、Maven 3.9+、Node.js 22+、pnpm 11。

**终端一：后端**

```sh
cd backend
# 首次运行且根目录没有 .env 时，复制配置示例
test -f ../.env || cp .env.example ../.env
# 编辑根目录 .env，填写自己的 DeepSeek API Key
mvn spring-boot:run
```

**终端二：前端**

```sh
cd frontend
pnpm install --frozen-lockfile
pnpm dev
```

打开 <http://127.0.0.1:5173>。后端默认监听 `127.0.0.1:8080`，前端开发服务器代理 `/api` 到后端。未配置 Key 也能启动、查看界面和历史；发起聊天前需要配置支持 **流式 Chat Completions + tool calling** 的 OpenAI 兼容服务。

后端通过 `spring.config.import` 自动读取根目录 `.env`，也支持 `backend/.env`；从 `backend/` 目录启动时，后者覆盖前者，进程环境变量优先级最高。文件使用 `KEY=value`，不加引号或 `export`。Key 仅在后端使用，不发送给前端，`.env` 已加入忽略规则。

配置示例使用 DeepSeek：`AGENT_BASE_URL=https://api.deepseek.com`、`AGENT_COMPLETIONS_PATH=/chat/completions`、`AGENT_MODEL=deepseek-flash`。采用 `AGENT_REASONING_EFFORT=none` 关闭思考模式，适配当前精简工具循环。接口及模型名称参考 [DeepSeek 官方文档](https://api-docs.deepseek.com/)，思考开关参考 [Chat Completions API](https://api-docs.deepseek.com/api/create-chat-completion/)。

## 已实现

- Ant Design X 聊天界面：逐段输出、Markdown、工具调用卡片、停止生成、失败重试。
- 会话列表、新建、重命名、删除、刷新恢复。
- Spring AI 显式执行「模型 → 工具 → 模型」，内置时区时间查询和精确十进制四则运算。
- SQLite 自动建表，保存用户消息、回答、工具结果及回合状态。
- 只把成功完成的回合传回模型；取消或失败时保留已生成内容供查看。
- 单会话串行、全局并发上限、心跳、总超时和上游取消。

## 目录和阅读入口

```text
AGENTS.md                             # 工作区规则入口
backend/                              # 独立 Maven 项目
  AGENTS.md
  .codex/guidance/                     # 编码与 Agent 生命周期规则
  src/main/java/dev/myagent/
    business/controller/              # HTTP 入口、异常响应
    business/dto/agent/                # 独立请求与响应 DTO
    business/service/agent/           # 模型工具循环、SSE 生命周期
    business/repository/              # SQLite 存储
    business/convert/                 # 模型消息持久化转换
    config/                           # 模型、数据库、线程池配置
  src/main/resources/                 # application.yml、schema.sql、system-prompt.txt
frontend/                             # 独立 Vite 项目
  AGENTS.md
  .codex/guidance/                     # 命名、分层、页面开发规则
  types/agent.ts                      # HTTP 与 SSE 契约
  src/api/
    api-client.ts                     # 通用 Fetch 和错误处理
    services2/agent/                   # Agent API、SSE 解析、协议校验
  src/pages/agent/
    index.tsx                         # 聊天页面
    agent-messages.tsx                # AntDX 消息列表
    agent-markdown.tsx                # Markdown 渲染
    agent-tool-card.tsx               # 工具结果
    hooks/use-agent-chat.ts           # 会话与生成状态
    constants.ts                      # 固定配置、工具名称
    utils.ts                          # 事件归并、格式化
    types.ts                          # 前端派生状态
  src/app.tsx                         # 页面挂载入口
  src/main.tsx                        # React、主题初始化
```

技术版本：Spring Boot 3.4.3、Spring AI 1.0.9、React 18、Ant Design 5、Ant Design X 1.6.1。模型通过 Spring AI 的 OpenAI 兼容接口接入。

## 开发规则

从根目录 `AGENTS.md` 进入各项目 guidance。前端源码统一 `kebab-case`，区分页面、Hook、纯函数、API 和协议类型；后端 Controller 只委托 Service，DTO 独立成文件。规则随当前轻量实现维护。

## 配置

下表是未配置 `.env` 时的内置默认值；DeepSeek 示例会覆盖模型相关配置。

| 环境变量 | 默认值 | 用途 |
| --- | --- | --- |
| `AGENT_API_KEY` | 空 | 模型服务密钥；空值时禁用聊天 |
| `AGENT_BASE_URL` | `https://api.openai.com` | 服务根地址，不含 completions 路径 |
| `AGENT_COMPLETIONS_PATH` | `/v1/chat/completions` | 与根地址拼接的接口路径 |
| `AGENT_MODEL` | `gpt-4o-mini` | 模型名称 |
| `AGENT_REASONING_EFFORT` | 空 | 推理模式参数；DeepSeek 示例使用 `none`，其他提供商按其支持情况设置或留空 |
| `AGENT_DB_PATH` | `./data/agent.db` | SQLite 文件，相对后端启动目录 |
| `AGENT_HOST` | `127.0.0.1` | 后端监听地址 |
| `AGENT_PORT` | `8080` | 后端端口 |
| `AGENT_TIMEOUT_SECONDS` | `120` | 一整个回合的总超时 |
| `AGENT_BACKEND_URL` | `http://127.0.0.1:8080` | 前端开发代理目标 |

例如，服务给出的完整接口是 `https://example.com/api/v1/chat/completions`，设置 `AGENT_BASE_URL=https://example.com` 和 `AGENT_COMPLETIONS_PATH=/api/v1/chat/completions`，避免重复 `/v1`。

其他限制在 `backend/src/main/resources/application.yml`：最多 6 次工具调用、最近 8 个成功回合、最多 4 个并行生成任务。单条用户消息最多 4,000 字符。SQLite 文件及 WAL 文件会写入 `backend/data/`，已加入忽略规则。

## HTTP / SSE 协议

| 接口 | 用途 |
| --- | --- |
| `GET /api/config` | 模型名、是否已配置、限制和工具名 |
| `GET /api/conversations` | 最近 200 个会话 |
| `POST /api/conversations` | 创建会话 |
| `GET /api/conversations/{id}` | 会话详情及消息 |
| `PATCH /api/conversations/{id}` | 重命名：`{"title":"新标题"}` |
| `DELETE /api/conversations/{id}` | 删除会话及消息 |
| `POST /api/chat` | 流式生成 |
| `POST /api/chat/cancel` | 按请求 ID 停止生成 |

先创建会话，再发送聊天请求。`requestId` 是前端生成的 UUID，每次发送或重试都使用新 ID。

```json
{
  "requestId": "客户端生成的 UUID",
  "conversationId": "创建会话返回的 UUID",
  "message": "计算 6 乘以 7"
}
```

取消请求使用同一组 `requestId` 和 `conversationId`。SSE 每条事件都有相同信封：

```text
event: delta
data: {"type":"delta","requestId":"…","conversationId":"…","data":{"text":"你好"}}
```

事件依次为 `start`、若干 `status` / `delta` / `tool_start` / `tool_result`，最后为 `done` 或 `error`。HTTP 非 2xx 表示生成未被接纳；流开始后的错误通过 `error` 事件返回。连接结束但没有终态事件，前端按中断处理，不自动重放请求。

## 扩展工具

在 `AgentTools` 中新增公开方法并添加 Spring AI `@Tool`、`@ToolParam` 注解，模型会自动获得工具定义。修改 `system-prompt.txt` 描述用途。前端 `agent-tool-card.tsx` 和 `constants.ts` 可添加中文名称；未登记的工具仍可显示名称、参数和结果。

当前工具都是有界、无外部副作用的本地函数。接入业务读写工具时，在工具入口使用你的 SaaS 用户身份和授权检查；不要让模型参数自行指定可信用户或租户身份。

## 验证与构建

```sh
# backend/
mvn test
mvn -DskipTests package

# frontend/
pnpm test
pnpm build
```

后端集成测试使用本地模拟 OpenAI 流式服务，通过真实 Spring AI 适配器验证工具往返、完整上下文、持久化、取消、超时和异常结束。前端测试覆盖 UTF-8 分片、SSE 帧边界、请求匹配、显式终态和取消。测试无需真实 Key，也不访问外部模型。

构建后分别得到 `backend/target/myagent-backend-0.1.0.jar` 和 `frontend/dist/`。从 `backend/` 运行 JAR 时同样自动加载 `.env`。部署前端静态文件时，由 Web 服务器把 `/api` 代理到 Java 后端，并关闭 SSE 缓冲，读取超时应大于回合总超时。

这是单实例、单用户的参考实现，默认只监听本机。接入 SaaS 时还需对接现有登录、会话归属和权限体系；本项目没有引入租户框架、计费、RAG、工作流编排或任务队列。
