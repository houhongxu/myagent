import type { AgentEvent } from '../../../../types/agent'

/** Strict envelope validation keeps malformed or unrelated events out of the current conversation. */
export function isEvent(value: unknown): value is AgentEvent {
  if (!value || typeof value !== 'object') return false
  const event = value as Record<string, unknown>
  if (typeof event.requestId !== 'string' || typeof event.conversationId !== 'string' || !event.data || typeof event.data !== 'object') return false
  const data = event.data as Record<string, unknown>
  switch (event.type) {
    case 'start': case 'done': return true
    case 'delta': case 'status': return typeof data.text === 'string'
    case 'tool_start': case 'tool_result': return typeof data.id === 'string' && typeof data.name === 'string'
      && typeof data.arguments === 'string' && (event.type === 'tool_start' || typeof data.output === 'string')
    case 'error': return typeof data.code === 'string' && typeof data.message === 'string' && typeof data.retryable === 'boolean'
    default: return false
  }
}

