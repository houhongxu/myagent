import type { AgentEvent } from '../../../types/agent'
import type { Turn } from './types'

export function errorText(error: unknown) {
  return error instanceof Error ? error.message : '请求失败，请稍后重试'
}

export function pretty(text: string) {
  try { return JSON.stringify(JSON.parse(text), null, 2) }
  catch { return text }
}

export function applyEvent(turn: Turn, event: AgentEvent): Turn {
  switch (event.type) {
    case 'delta': return { ...turn, answer: turn.answer + event.data.text, statusText: '正在回答' }
    case 'status': return { ...turn, statusText: event.data.text }
    case 'tool_start': return { ...turn, tools: [...turn.tools, event.data], statusText: '正在调用工具' }
    case 'tool_result': return { ...turn, tools: turn.tools.map(tool => tool.id === event.data.id ? event.data : tool) }
    case 'done': return { ...turn, status: 'done', statusText: undefined }
    case 'error': return { ...turn, status: event.data.code === 'cancelled' ? 'cancelled' : 'error', error: event.data.message, statusText: undefined }
    default: return turn
  }
}
