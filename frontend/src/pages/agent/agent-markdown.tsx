import { memo } from 'react'
import XMarkdown, { type ComponentProps } from '@ant-design/x-markdown'
import '@ant-design/x-markdown/es/XMarkdown/index.css'

const components = { a: ({ children }: ComponentProps) => <span>{children}</span> }
const dompurifyConfig = { FORBID_TAGS: ['img', 'iframe', 'video', 'audio', 'form', 'input'], FORBID_ATTR: ['style'] }

export default memo(function Markdown({ text, streaming }: { text: string; streaming: boolean }) {
  return <XMarkdown content={text} escapeRawHtml components={components} dompurifyConfig={dompurifyConfig}
    streaming={{ hasNextChunk: streaming }} className="answer-markdown" />
})
