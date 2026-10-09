import { useCallback, useState } from 'react'
import { Alert, App as AntApp, Button, Dropdown, Input, Modal, Spin, Tag } from 'antd'
import { ArrowUpOutlined, CalculatorOutlined, CheckOutlined, ClockCircleOutlined, CommentOutlined,
  DeleteOutlined, EditOutlined, EllipsisOutlined, MenuOutlined, PlusOutlined, StopOutlined } from '@ant-design/icons'
import Sender from '@ant-design/x/es/sender'
import { useAgentChat } from './hooks/use-agent-chat'
import ChatMessages from './agent-messages'
import { suggestions } from './constants'
import type { Conversation } from '../../../types/agent'

const suggestionIcons = { time: <ClockCircleOutlined />, calculate: <CalculatorOutlined />, plan: <EditOutlined /> }

export default function AgentPage() {
  const agent = useAgentChat()
  const { message } = AntApp.useApp()
  const [input, setInput] = useState('')
  const [sidebarOpen, setSidebarOpen] = useState(false)
  const [editing, setEditing] = useState<Conversation>()
  const [title, setTitle] = useState('')
  const [saving, setSaving] = useState(false)
  const [deleting, setDeleting] = useState<Conversation>()
  const selected = agent.conversations.find(item => item.id === agent.selectedId)
  const inputTooLong = input.trim().length > (agent.config?.maxMessageLength ?? 4000)
  const disabled = agent.loading || !agent.config?.configured

  const submit = useCallback((value: string) => {
    if (disabled || agent.streaming || !value.trim() || value.trim().length > (agent.config?.maxMessageLength ?? 4000)) return
    setInput('')
    void agent.send(value)
  }, [agent.send, agent.streaming, agent.config?.maxMessageLength, disabled])

  const select = (id: string | null) => { setSidebarOpen(false); setInput(''); void agent.select(id) }
  const rename = async () => {
    if (!editing || !title.trim()) return
    setSaving(true)
    try { await agent.rename(editing.id, title.trim()); setEditing(undefined) }
    catch (error) { void message.error(error instanceof Error ? error.message : '重命名失败') }
    finally { setSaving(false) }
  }
  const remove = async () => {
    if (!deleting) return
    setSaving(true)
    try { await agent.remove(deleting.id); setDeleting(undefined) }
    catch (error) { void message.error(error instanceof Error ? error.message : '删除失败') }
    finally { setSaving(false) }
  }

  return (
    <div className="app-shell">
      {sidebarOpen ? <button className="sidebar-backdrop" aria-label="关闭会话列表" onClick={() => setSidebarOpen(false)} /> : null}
      <aside className={`sidebar ${sidebarOpen ? 'is-open' : ''}`}>
        <div className="brand"><span className="brand-mark">✦</span><strong>MyAgent</strong><span className="brand-caption">WORKSPACE</span></div>
        <Button className="new-chat" icon={<PlusOutlined />} onClick={() => select(null)} disabled={agent.loading}>新对话<span className="new-chat-plus">↗</span></Button>
        <div className="sidebar-section">你的对话 <span>{agent.conversations.length}</span></div>
        <nav className="conversation-list" aria-label="历史对话">
          {agent.conversations.map(conversation => (
            <div className={`conversation-row ${agent.selectedId === conversation.id ? 'is-selected' : ''}`} key={conversation.id}>
              <button className="conversation-select" onClick={() => select(conversation.id)} title={conversation.title}>
                <CommentOutlined /><span>{conversation.title}</span>
              </button>
              <Dropdown trigger={['click']} menu={{ items: [
                { key: 'rename', label: '重命名', icon: <EditOutlined />, onClick: () => { setEditing(conversation); setTitle(conversation.title) } },
                { key: 'delete', label: '删除', danger: true, icon: <DeleteOutlined />, onClick: () => setDeleting(conversation) },
              ] }}><Button type="text" size="small" icon={<EllipsisOutlined />} aria-label={`管理对话：${conversation.title}`} /></Dropdown>
            </div>
          ))}
          {!agent.conversations.length && !agent.loading ? <p className="empty-history">第一段对话，从这里开始。</p> : null}
        </nav>
        <div className="sidebar-bottom"><div className="local-avatar">M</div><div><strong>个人工作区</strong><span><span className="tiny-dot" /> 历史保存在本地</span></div><CheckOutlined /></div>
      </aside>

      <main className="main-panel">
        <header className="topbar">
          <div className="topbar-title"><Button type="text" className="mobile-menu" icon={<MenuOutlined />} aria-label="打开会话列表" onClick={() => setSidebarOpen(true)} />
            <span>{selected?.title || '新对话'}</span><Tag bordered={false}>通用助手</Tag></div>
          <div className="model-badge"><span className={`tiny-dot ${agent.config?.configured ? '' : 'offline'}`} />{agent.config?.model || '连接中'}</div>
        </header>

        {agent.error ? <Alert className="page-alert" type="error" showIcon message={agent.error} action={<Button size="small" onClick={() => window.location.reload()}>重新连接</Button>} /> : null}
        {agent.config && !agent.config.configured ? <Alert className="page-alert" type="info" showIcon message="连接模型后即可开始对话"
          description="在后端配置 AGENT_API_KEY、AGENT_BASE_URL 和 AGENT_MODEL，然后重启后端并刷新页面。" /> : null}

        <section className={`chat-area ${agent.turns.length ? 'has-messages' : ''}`} aria-label="对话内容">
          {agent.loading ? <div className="loading-state"><Spin /><span>正在读取对话</span></div> : agent.turns.length ? <ChatMessages turns={agent.turns} streaming={agent.streaming} retry={submit} /> : (
            <div className="welcome">
              <div className="welcome-symbol">✦<span className="small-spark">✧</span></div>
              <div className="eyebrow">想法、答案与行动</div>
              <h1>今天，有什么可以帮你？</h1>
              <p className="welcome-description">聊聊你的问题，或让工具帮你完成一件小事。<br />我们从一个想法开始。</p>
              <div className="suggestions">
                {suggestions.map(item => <button key={item.title} className="suggestion-card" disabled={disabled || agent.streaming} onClick={() => submit(item.prompt)}>
                  <span className={`suggestion-icon ${item.color}`}>{suggestionIcons[item.icon]}</span><strong>{item.title}<span>↗</span></strong><span>{item.description}</span>
                </button>)}
              </div>
              <div className="capabilities"><span>支持多轮对话</span><i />工具调用<i /><span>自动保存历史</span></div>
            </div>
          )}
        </section>

        <footer className="composer-wrap">
          <Sender className="composer" value={input} onChange={setInput} onSubmit={submit}
            placeholder="输入你的问题，Enter 发送，Shift + Enter 换行" loading={agent.streaming} onCancel={() => { void agent.stop() }} disabled={disabled}
            autoSize={{ minRows: 2, maxRows: 6 }}
            actions={agent.streaming
              ? <Button className="send-button" icon={<StopOutlined />} aria-label="停止生成" onClick={() => { void agent.stop() }} />
              : <Button type="primary" className="send-button" icon={<ArrowUpOutlined />} aria-label="发送消息" disabled={disabled || !input.trim() || inputTooLong} onClick={() => submit(input)} />}
          />
          <div className="composer-meta"><span>{agent.streaming ? <><span className="pulse-dot" /> 正在生成，可随时停止</> : 'MyAgent · 保持好奇，开始对话'}</span>
            <span className={inputTooLong ? 'text-error' : ''}>{inputTooLong ? '消息过长 ' : ''}{input.length} / {agent.config?.maxMessageLength ?? 4000}</span></div>
        </footer>
      </main>

      <Modal title="重命名对话" open={Boolean(editing)} onCancel={() => setEditing(undefined)} onOk={() => { void rename() }} confirmLoading={saving} okButtonProps={{ disabled: !title.trim() }} destroyOnHidden>
        <Input aria-label="对话名称" value={title} onChange={event => setTitle(event.target.value)} maxLength={80} onPressEnter={() => { void rename() }} />
      </Modal>
      <Modal title="删除这个对话？" open={Boolean(deleting)} onCancel={() => setDeleting(undefined)} onOk={() => { void remove() }} confirmLoading={saving} okText="删除" okButtonProps={{ danger: true }}>
        <p>“{deleting?.title}”及其历史消息会从本地存储中删除。</p>
      </Modal>
    </div>
  )
}
