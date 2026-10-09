export const storageKey = 'myagent.conversation.v1'

export const toolNames: Record<string, string> = { current_time: '时间查询', calculate: '计算器' }

export const suggestions = [
  { icon: 'time', title: '查询当前时间', description: '看看上海和伦敦现在几点', prompt: '查询上海和伦敦的当前时间，告诉我时差。', color: 'peach' },
  { icon: 'calculate', title: '帮我算一笔账', description: '把多步骤计算交给工具', prompt: '用计算器帮我算一下：129.9 乘以 3，再加上 25.5，结果是多少？', color: 'purple' },
  { icon: 'plan', title: '一起整理思路', description: '从一个想法到清晰的行动计划', prompt: '我想养成每天阅读的习惯，帮我设计一个简单可执行的两周计划。', color: 'green' },
] as const
