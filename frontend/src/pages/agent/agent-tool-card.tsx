import { CheckCircleOutlined, LoadingOutlined, ToolOutlined } from '@ant-design/icons'
import type { ToolObservation } from './types'
import { toolNames } from './constants'
import { pretty } from './utils'


export default function ToolCard({ tool, running }: { tool: ToolObservation; running: boolean }) {
  const finished = tool.output !== undefined
  return (
    <details className="tool-card">
      <summary>
        <span className="tool-icon"><ToolOutlined /></span>
        <span>{toolNames[tool.name] || tool.name}</span>
        <span className="tool-state">{finished ? <><CheckCircleOutlined /> 已完成</> : running ? <><LoadingOutlined /> 执行中</> : '已中断'}</span>
      </summary>
      <div className="tool-detail">
        <div className="detail-label">输入</div><pre>{pretty(tool.arguments)}</pre>
        {finished ? <><div className="detail-label">结果</div><pre>{pretty(tool.output!)}</pre></> : null}
      </div>
    </details>
  )
}
