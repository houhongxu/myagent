import type { AgentConfig, ChatRequest, Conversation, ConversationDetail } from '../../../../types/agent'
import { checked, json } from '../../api-client'

export const api = {
  config: () => json<AgentConfig>('/config'),
  list: () => json<Conversation[]>('/conversations'),
  create: () => json<Conversation>('/conversations', { method: 'POST' }),
  detail: (id: string) => json<ConversationDetail>(`/conversations/${id}`),
  rename: (id: string, title: string) => json<Conversation>(`/conversations/${id}`, { method: 'PATCH', body: JSON.stringify({ title }) }),
  remove: async (id: string) => { await checked(await fetch(`/api/conversations/${id}`, { method: 'DELETE', signal: AbortSignal.timeout(15000) })) },
  cancel: (request: ChatRequest) => json<{ cancelled: boolean }>('/chat/cancel', {
    method: 'POST', body: JSON.stringify({ requestId: request.requestId, conversationId: request.conversationId }), signal: AbortSignal.timeout(10000),
  }),
}

