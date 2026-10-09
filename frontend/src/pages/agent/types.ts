import type { ToolCall, TurnResult } from '../../../types/agent'

export interface ToolObservation extends ToolCall { output?: string }
export interface Turn extends Omit<TurnResult, 'tools'> {
  tools: ToolObservation[]
  statusText?: string
}
