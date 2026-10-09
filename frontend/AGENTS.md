# Frontend guidance

React 18 + TypeScript + Vite + Ant Design X。使用 pnpm，前端模型密钥始终由后端管理。

## 命令

在 `frontend/` 中执行：`pnpm dev`、`pnpm typecheck`、`pnpm test`、`pnpm build`。

## 规则入口

- 修改 TypeScript、组件、Hook、API、类型或目录时，读取 [.codex/guidance/file-layering.md](.codex/guidance/file-layering.md)。
- 修改页面、交互、异步行为或测试时，读取 [.codex/guidance/development.md](.codex/guidance/development.md)。

规范保留文件组织、接口契约和交互约束，不强制引入业务路由、权限体系、双语资源或额外状态库。

交付前核对适用 guidance、文件名和导入路径，并运行相关验证。
