/** HTTP and SSE contracts; field names match the backend DTOs. */
export interface Conversation { id: string; title: string; createdAt: string; updatedAt: string }
export interface ToolCall { id: string; name: string; arguments: string }
export interface ToolResult extends ToolCall { output: string }
export interface TurnResult {
  id: string; prompt: string; answer: string; status: 'running' | 'done' | 'cancelled' | 'error'
  error?: string | null; tools: ToolResult[]; createdAt: string
}
export interface ConversationDetail { conversation: Conversation; turns: TurnResult[] }
export interface AgentConfig { configured: boolean; model: string; maxMessageLength: number; timeoutSeconds: number; tools: string[] }
export interface ChatRequest { requestId: string; conversationId: string; message: string }
type Envelope<T extends string, D> = { type: T; requestId: string; conversationId: string; data: D }
export type AgentEvent =
  | Envelope<'start' | 'done', Record<string, never>>
  | Envelope<'delta' | 'status', { text: string }>
  | Envelope<'tool_start', ToolCall>
  | Envelope<'tool_result', ToolResult>
  | Envelope<'error', { code: string; message: string; retryable: boolean }>
