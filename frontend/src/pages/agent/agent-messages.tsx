import { useMemo } from 'react'
import Bubble from '@ant-design/x/es/bubble'
import type { BubbleListProps } from '@ant-design/x/es/bubble/BubbleList'
import { Alert, Button } from 'antd'
import { CopyOutlined, ReloadOutlined, UserOutlined } from '@ant-design/icons'
import type { Turn } from './types'
import Markdown from './agent-markdown'
import ToolCard from './agent-tool-card'

export default function ChatMessages({ turns, streaming, retry }: { turns: Turn[]; streaming: boolean; retry: (prompt: string) => void }) {
  const items = useMemo<NonNullable<BubbleListProps['items']>>(() => turns.flatMap((turn, index) => [
    {
      key: `${turn.id}-user`, role: 'user', placement: 'end' as const,
      avatar: { icon: <UserOutlined />, style: { background: '#e9e8f3', color: '#706c96' } },
      content: <span className="user-message">{turn.prompt}</span>,
      styles: { content: { background: '#efedf9' } },
    },
    {
      key: `${turn.id}-assistant`, role: 'assistant', placement: 'start' as const, variant: 'borderless' as const,
      avatar: { icon: <span className="spark">✦</span>, style: { background: '#635bdb' } },
      header: <span className="assistant-label">MyAgent</span>,
      styles: { content: { maxWidth: '100%', minWidth: 0 }, footer: { width: '100%' } },
      content: <div className="answer-content">
        {turn.tools.map(tool => <ToolCard key={tool.id} tool={tool} running={turn.status === 'running'} />)}
        {turn.answer ? <Markdown text={turn.answer} streaming={turn.status === 'running'} /> : null}
        {turn.status === 'running' ? <div className="generation-status" role="status"><span className="pulse-dot" />{turn.statusText || '正在生成'}</div> : null}
        {turn.status === 'cancelled' ? <div className="muted small" role="status">已停止生成</div> : null}
        {turn.status === 'error' ? <Alert type="error" showIcon message={turn.error || '生成已中断'}
          action={!streaming && index === turns.length - 1 ? <Button size="small" icon={<ReloadOutlined />} onClick={() => retry(turn.prompt)}>重试</Button> : undefined} /> : null}
      </div>,
      footer: turn.answer && turn.status !== 'running'
        ? <Button type="text" size="small" className="copy-button" icon={<CopyOutlined />} aria-label="复制回答"
          onClick={() => { void navigator.clipboard.writeText(turn.answer).catch(() => {}) }}>复制</Button> : undefined,
    },
  ]), [turns, streaming, retry])
  return <Bubble.List className="message-list" items={items} autoScroll />
}
