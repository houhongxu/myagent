import { useCallback, useEffect, useRef, useState } from 'react'
import { api } from '../../../api/services2/agent/agent'
import { chat } from '../../../api/services2/agent/agent-stream'
import { applyEvent, errorText } from '../utils'
import type { AgentConfig, ChatRequest, Conversation } from '../../../../types/agent'
import type { Turn } from '../types'
import { storageKey } from '../constants'

interface ActiveRun { request: ChatRequest; controller: AbortController }
function remember(id: string | null) {
  try { if (id) localStorage.setItem(storageKey, id); else localStorage.removeItem(storageKey) } catch { /* Storage may be unavailable. */ }
}
function remembered() { try { return localStorage.getItem(storageKey) } catch { return null } }

export function useAgentChat() {
  const [config, setConfig] = useState<AgentConfig>()
  const [conversations, setConversations] = useState<Conversation[]>([])
  const [selectedId, setSelectedId] = useState<string | null>(null)
  const [turns, setTurns] = useState<Turn[]>([])
  const [loading, setLoading] = useState(true)
  const [streaming, setStreaming] = useState(false)
  const [error, setError] = useState<string>()
  const active = useRef<ActiveRun>()
  const sending = useRef(false)
  const version = useRef(0)
  const mounted = useRef(false)

  const refreshList = useCallback(async () => {
    const list = await api.list()
    if (mounted.current) setConversations(list)
  }, [])

  const stop = useCallback(async () => {
    const run = active.current
    if (!run) return
    active.current = undefined
    run.controller.abort()
    sending.current = false
    setStreaming(false)
    setTurns(previous => previous.map(turn => turn.id === run.request.requestId
      ? { ...turn, status: 'cancelled', statusText: undefined, error: '已停止生成' } : turn))
    try { await api.cancel(run.request) }
    catch { if (mounted.current) setError('停止请求未确认，服务端将在连接断开或超时后停止') }
  }, [])

  useEffect(() => {
    mounted.current = true
    const current = ++version.current
    Promise.all([api.config(), api.list()]).then(async ([settings, list]) => {
      if (!mounted.current || version.current !== current) return
      setConfig(settings)
      setConversations(list)
      const id = list.find(item => item.id === remembered())?.id ?? list[0]?.id ?? null
      if (id) {
        const detail = await api.detail(id)
        if (!mounted.current || version.current !== current) return
        setSelectedId(id)
        setTurns(detail.turns)
        remember(id)
      }
    }).catch(error => {
      if (mounted.current && version.current === current) setError(errorText(error))
    }).finally(() => {
      if (mounted.current && version.current === current) setLoading(false)
    })
    return () => {
      mounted.current = false
      version.current++
      const run = active.current
      active.current = undefined
      run?.controller.abort()
      if (run) void api.cancel(run.request).catch(() => {})
    }
  }, [])

  const select = async (id: string | null) => {
    if (sending.current && !active.current) return
    const current = ++version.current
    setLoading(true)
    setError(undefined)
    await stop()
    if (version.current !== current || !mounted.current) return
    setSelectedId(id)
    setTurns([])
    remember(id)
    try {
      if (id) {
        const detail = await api.detail(id)
        if (mounted.current && version.current === current) setTurns(detail.turns)
      }
    } catch (error) {
      if (mounted.current && version.current === current) setError(errorText(error))
    } finally {
      if (mounted.current && version.current === current) setLoading(false)
    }
  }

  const send = async (value: string): Promise<boolean> => {
    const prompt = value.trim()
    if (sending.current || loading || !config?.configured || !prompt || prompt.length > config.maxMessageLength) return false
    sending.current = true
    setStreaming(true)
    setError(undefined)
    const current = version.current
    let run: ActiveRun | undefined
    try {
      let id = selectedId
      if (!id) {
        const conversation = await api.create()
        if (!mounted.current || version.current !== current) return false
        id = conversation.id
        setSelectedId(id)
        remember(id)
        setConversations(previous => [conversation, ...previous])
      }
      const request = { requestId: crypto.randomUUID(), conversationId: id, message: prompt }
      run = { request, controller: new AbortController() }
      active.current = run
      setTurns(previous => [...previous, {
        id: request.requestId, prompt, answer: '', status: 'running', tools: [], createdAt: new Date().toISOString(), statusText: '正在连接',
      }])
      const controller = run.controller
      const timeout = window.setTimeout(() => {
        if (active.current?.controller !== controller) return
        controller.abort(new Error('等待超时，请重试'))
        void api.cancel(request).catch(() => {})
      }, (config.timeoutSeconds + 15) * 1000)
      try {
        await chat(request, controller.signal, event => {
          if (!mounted.current || active.current?.controller !== controller) return
          setTurns(previous => previous.map(turn => turn.id === request.requestId ? applyEvent(turn, event) : turn))
        })
      } finally { window.clearTimeout(timeout) }
      return true
    } catch (error) {
      if (!mounted.current || (run && active.current !== run)) return false
      if (run) {
        const requestId = run.request.requestId
        setTurns(previous => previous.map(turn => turn.id === requestId
          ? { ...turn, status: 'error', statusText: undefined, error: errorText(error) } : turn))
        void api.cancel(run.request).catch(() => {})
      } else setError(errorText(error))
      return false
    } finally {
      if (!run || active.current === run) {
        active.current = undefined
        sending.current = false
        if (mounted.current) setStreaming(false)
      }
      if (mounted.current) void refreshList().catch(() => {})
    }
  }

  const rename = async (id: string, title: string) => {
    const conversation = await api.rename(id, title)
    setConversations(previous => previous.map(item => item.id === id ? conversation : item))
  }
  const remove = async (id: string) => {
    if (id === selectedId) await stop()
    await api.remove(id)
    if (id === selectedId) { version.current++; setSelectedId(null); setTurns([]); remember(null) }
    await refreshList()
  }
  return { config, conversations, selectedId, turns, loading, streaming, error, send, stop, select, rename, remove }
}
