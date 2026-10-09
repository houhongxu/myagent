# 文件组织与命名

## 命名

- 所有源码文件和目录使用 `kebab-case`，包括 React 组件和 Hook 文件。
- 页面入口用 `index.tsx`；根组件用 `app.tsx`；Hook 文件用 `use-xxx.ts`。
- React 组件/类型标识符使用 PascalCase，函数/变量使用 camelCase。
- 不保留只为兼容旧路径的空转导出文件。

## 分层

| 路径 | 职责 |
| --- | --- |
| `src/pages/agent/` | Agent 页面及页面专属组件 |
| `src/pages/agent/hooks/` | React 状态、副作用与请求编排 |
| `src/pages/agent/types.ts` | 仅前端使用的消息状态等派生类型 |
| `src/pages/agent/constants.ts` | 字面量、固定配置和映射表，不放函数、Hook 或 JSX |
| `src/pages/agent/utils.ts` | 纯数据转换、格式化与事件归并，不发请求 |
| `src/api/services2/agent/` | HTTP 请求、SSE 协议解析与验证 |
| `src/api/api-client.ts` | 通用 Fetch、HTTP 错误处理 |
| `types/agent.ts` | 与后端一致的请求、响应及 SSE 契约 |
| `src/components/`、`src/hooks/` | 只有出现跨页面复用时才建立 |

API 返回后端原始字段；协议解析可留在 API 层，展示转换放 utils，交互编排放 Hook。页面不得重复定义同义 DTO 或直接发请求。

不为了习惯重命名服务端字段，不全量抓取业务数据再模拟服务端查询。会话侧栏使用后端提供的最近 200 条轻量列表；详情按 ID 加载。
