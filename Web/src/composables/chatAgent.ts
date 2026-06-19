import type { Ref } from 'vue'
import type { ChatMessage, AgentToolEvent } from '@/stores/chat'
import type { AgentPlanStep } from '@/services/ai'

/**
 * Agent 事件辅助函数
 * 从 chat store 中拆分，处理 SSE agent 相关事件的附加逻辑
 */
export function useChatAgent(messages: Ref<ChatMessage[]>) {

  const attachAgentPlan = (messageId: number, steps?: AgentPlanStep[]) => {
    const message = messages.value.find(msg => msg.id === messageId)
    if (!message || !steps) return
    message.agentPlanSteps = steps
  }

  const attachAgentStart = (messageId: number, payload: { intent?: string; role?: string }) => {
    const message = messages.value.find(msg => msg.id === messageId)
    if (!message || !payload) return
    message.agentIntent = payload.intent
    message.agentRole = payload.role
    message.showAgentTrace = false
  }

  const upsertToolEvent = (
    messageId: number,
    payload: {
      toolName: string; displayName?: string; inputSummary?: string;
      success?: boolean; durationMs?: number; resultSummary?: string; errorMessage?: string
    },
    status: AgentToolEvent['status']
  ) => {
    const message = messages.value.find(msg => msg.id === messageId)
    if (!message || !payload?.toolName) return
    const next: AgentToolEvent = {
      toolName: payload.toolName,
      displayName: payload.displayName || payload.toolName,
      inputSummary: payload.inputSummary,
      success: payload.success,
      durationMs: payload.durationMs,
      resultSummary: payload.resultSummary,
      errorMessage: payload.errorMessage,
      status
    }
    const events = message.agentToolEvents || []
    const index = events.findIndex(item => item.toolName === payload.toolName && item.status === 'running')
    if (index >= 0 && status !== 'running') {
      events[index] = next
    } else {
      events.push(next)
    }
    message.agentToolEvents = events
    message.isThinking = false
  }

  const attachConfirmation = (
    messageId: number,
    payload: { actionId: number; actionType: string; title: string; description: string; riskLevel?: string }
  ) => {
    const message = messages.value.find(msg => msg.id === messageId)
    if (!message || !payload) return
    message.confirmation = {
      actionId: payload.actionId,
      actionType: payload.actionType,
      title: payload.title,
      description: payload.description,
      riskLevel: payload.riskLevel
    }
    message.isThinking = false
  }

  return {
    attachAgentPlan,
    attachAgentStart,
    upsertToolEvent,
    attachConfirmation
  }
}
