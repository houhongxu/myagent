import { describe, expect, it } from 'vitest'
import { readAgentStream } from '../src/api/services2/agent/agent-stream'
import { applyEvent } from '../src/pages/agent/utils'
import type { AgentEvent } from '../types/agent'
import type { Turn } from '../src/pages/agent/types'

const request = { requestId: 'request-1', conversationId: 'conversation-1', message: 'hello' }
function event(type: string, data = {}) { return { type, ...request, data } }
function frame(type: string, data = {}) { return `event: ${type}\r\ndata: ${JSON.stringify(event(type, data))}\r\n\r\n` }
function stream(text: string) {
  const bytes = new TextEncoder().encode(text)
  return new ReadableStream<Uint8Array>({ start(controller) {
    // Split every UTF-8 byte and every CRLF boundary, as a real network is permitted to do.
    for (const byte of bytes) controller.enqueue(new Uint8Array([byte]))
    controller.close()
  } })
}

describe('SSE protocol', () => {
  it('preserves split UTF-8, ignores heartbeats and accepts an explicit completion', async () => {
    const events: AgentEvent[] = []
    await readAgentStream(stream(frame('start') + ': heartbeat\r\n\r\n' + frame('delta', { text: '你好，世界🌏' }) + frame('done')),
      request, new AbortController().signal, value => events.push(value))
    expect(events.map(item => item.type)).toEqual(['start', 'delta', 'done'])
    expect(events[1].data).toEqual({ text: '你好，世界🌏' })
  })
  it('rejects a stream that ends without a terminal event', async () => {
    await expect(readAgentStream(stream(frame('start') + frame('delta', { text: 'partial' })), request,
      new AbortController().signal, () => {})).rejects.toThrow('连接中断')
  })
  it('rejects another conversation and missing start', async () => {
    await expect(readAgentStream(stream(frame('delta', { text: 'wrong order' })), request,
      new AbortController().signal, () => {})).rejects.toThrow('顺序')
    const wrong = frame('start').replaceAll('conversation-1', 'conversation-2')
    await expect(readAgentStream(stream(wrong), request, new AbortController().signal, () => {})).rejects.toThrow('不匹配')
  })
  it('accepts multiline JSON data fields and rejects duplicate start', async () => {
    const json = JSON.stringify(event('delta', { text: '你好' })).replace(',"message"', ',\n"message"')
    const multiline = `event: delta\ndata: ${json.replaceAll('\n', '\ndata: ')}\n\n`
    const events: AgentEvent[] = []
    await readAgentStream(stream(frame('start') + multiline + frame('done')), request,
      new AbortController().signal, value => events.push(value))
    expect(events[1].data).toEqual({ text: '你好' })
    await expect(readAgentStream(stream(frame('start') + frame('start')), request,
      new AbortController().signal, () => {})).rejects.toThrow('顺序')
  })
  it('cancels a pending reader immediately', async () => {
    const controller = new AbortController()
    let cancelled = false
    const body = new ReadableStream<Uint8Array>({ cancel() { cancelled = true } })
    const reading = readAgentStream(body, request, controller.signal, () => {})
    controller.abort()
    await expect(reading).rejects.toThrow()
    expect(cancelled).toBe(true)
  })
})

describe('tool event rendering state', () => {
  it('matches tool results by ID and preserves partial output on error', () => {
    let turn: Turn = { id: 'request-1', prompt: '', answer: '', status: 'running', tools: [], createdAt: '' }
    turn = applyEvent(turn, event('tool_start', { id: 'a', name: 'calculate', arguments: '{}' }) as AgentEvent)
    turn = applyEvent(turn, event('tool_start', { id: 'b', name: 'current_time', arguments: '{}' }) as AgentEvent)
    turn = applyEvent(turn, event('tool_result', { id: 'b', name: 'current_time', arguments: '{}', output: 'UTC' }) as AgentEvent)
    turn = applyEvent(turn, event('delta', { text: 'partial' }) as AgentEvent)
    turn = applyEvent(turn, event('error', { code: 'cancelled', message: 'stopped', retryable: false }) as AgentEvent)
    expect(turn.tools[0].output).toBeUndefined()
    expect(turn.tools[1].output).toBe('UTC')
    expect(turn.answer).toBe('partial')
    expect(turn.status).toBe('cancelled')
  })
})
