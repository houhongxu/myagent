# Frontend

Node.js 22+、pnpm 11。React 18 + TypeScript + Ant Design X，使用 Vite 构建。

```sh
pnpm install --frozen-lockfile
pnpm dev
```

默认地址 `http://127.0.0.1:5173`，`/api` 由 Vite 代理到 `http://127.0.0.1:8080`。修改后端地址时，复制 `.env.example` 为 `.env` 并调整 `AGENT_BACKEND_URL`。模型 Key 只配置在后端。

```sh
pnpm test
pnpm build
```

`src/pages/agent/hooks/use-agent-chat.ts` 管理会话与生成状态；`src/api/services2/agent/agent-stream.ts` 使用 Fetch + `eventsource-parser` 读取 POST SSE；`src/pages/agent/` 负责 AntDX 消息、Markdown 和工具结果展示。

生产构建输出 `dist/`，部署时需配置同源 `/api` 代理。[完整说明](../README.md)
