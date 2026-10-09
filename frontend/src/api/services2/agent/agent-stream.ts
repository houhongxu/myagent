import { EventSourceParserStream } from 'eventsource-parser/stream'
import type { AgentEvent, ChatRequest } from '../../../../types/agent'
import { ApiError, checked } from '../../api-client'
import { isEvent } from './agent-schema'

/** POST SSE, UTF-8 safe, explicit start/end, no automatic retries. */
export async function readAgentStream(body: ReadableStream<Uint8Array>, request: ChatRequest,
  signal: AbortSignal, onEvent: (event: AgentEvent) => void) {
  const reader = body.pipeThrough(new TextDecoderStream())
    .pipeThrough(new EventSourceParserStream({ onError: 'terminate', maxBufferSize: 1_048_576 })).getReader()
  const abort = () => { void reader.cancel().catch(() => {}) }
  signal.addEventListener('abort', abort, { once: true })
  let started = false
  try {
    while (true) {
      signal.throwIfAborted()
      const { done, value } = await reader.read()
      signal.throwIfAborted()
      if (done) throw new ApiError('连接中断，回答未完成，请重试')
      if (!value.data) continue
      let event: unknown
      try { event = JSON.parse(value.data) } catch { throw new ApiError('无法解析服务端事件') }
      if (!isEvent(event) || event.type !== value.event || event.requestId !== request.requestId || event.conversationId !== request.conversationId)
        throw new ApiError('服务端事件与当前对话不匹配')
      if ((!started && event.type !== 'start') || (started && event.type === 'start')) throw new ApiError('服务端事件顺序异常')
      started = true
      onEvent(event)
      if (event.type === 'done' || event.type === 'error') return
    }
  } finally {
    signal.removeEventListener('abort', abort)
    await reader.cancel().catch(() => {})
    reader.releaseLock()
  }
}

export async function chat(request: ChatRequest, signal: AbortSignal, onEvent: (event: AgentEvent) => void) {
  const response = await checked(await fetch('/api/chat', {
    method: 'POST', headers: { 'Content-Type': 'application/json', Accept: 'text/event-stream' }, body: JSON.stringify(request), signal,
  }))
  if (!response.body || !response.headers.get('Content-Type')?.includes('text/event-stream')) throw new ApiError('服务端未返回事件流')
  await readAgentStream(response.body, request, signal, onEvent)
}
