<script setup lang="ts">
import { computed, ref } from 'vue'
import { message } from 'ant-design-vue'
import {
  SendOutlined,
  FileAddOutlined,
  CheckCircleOutlined,
  EditOutlined,
} from '@ant-design/icons-vue'
import AgentService from '../../services/agent'
import type {
  AdminArticleDraftSnapshot,
  AgentPlanStep,
  ArticleResultItem,
  ConfirmationRequiredPayload,
  AgentActionResult,
  AgentStartPayload,
  ToolEventPayload,
} from '../../types/agent'

const props = defineProps<{
  draft: AdminArticleDraftSnapshot
}>()

const emit = defineEmits<{
  applyDraft: [draft: Partial<AdminArticleDraftSnapshot>]
  actionDone: [result?: AgentActionResult]
}>()

const prompt = ref('')
const answer = ref('')
const loading = ref(false)
const plan = ref<AgentPlanStep[]>([])
const articles = ref<ArticleResultItem[]>([])
const confirmation = ref<ConfirmationRequiredPayload | null>(null)
const toolEvents = ref<Array<ToolEventPayload & { status: 'running' | 'success' | 'failed' }>>([])
const agentStart = ref<AgentStartPayload | null>(null)

const canSend = computed(() => prompt.value.trim().length > 0 && !loading.value)
const currentPlanIndex = computed(() => {
  const running = plan.value.findIndex(item => item.status === 'running')
  return running >= 0 ? running : Math.max(0, plan.value.length - 1)
})

const quickPrompts = [
  '帮我润色当前文章',
  '根据当前内容生成摘要',
  '帮我补一个更吸引人的标题',
  '保存为草稿',
  '发布这篇文章',
]

const send = async (text?: string) => {
  const content = (text || prompt.value).trim()
  if (!content || loading.value) return
  loading.value = true
  answer.value = ''
  plan.value = []
  articles.value = []
  confirmation.value = null
  toolEvents.value = []
  agentStart.value = null
  prompt.value = ''

  try {
    await AgentService.stream(
      {
        message: content,
        draft: props.draft,
        context: {
          page: 'admin-post-editor',
          postId: props.draft.postId,
        },
      },
      {
        onData: (chunk) => {
          answer.value += chunk
        },
        onPlan: (steps) => {
          plan.value = steps
        },
        onStart: (payload) => {
          agentStart.value = payload
        },
        onToolStart: (payload) => {
          toolEvents.value.push({ ...payload, status: 'running' })
        },
        onToolResult: (payload) => {
          const index = toolEvents.value.findIndex(item => item.toolName === payload.toolName && item.status === 'running')
          const next = { ...payload, status: payload.success === false ? 'failed' as const : 'success' as const }
          if (index >= 0) {
            toolEvents.value[index] = next
          } else {
            toolEvents.value.push(next)
          }
        },
        onArticles: (items) => {
          articles.value = items
        },
        onConfirmation: (payload) => {
          confirmation.value = payload
        },
        onError: (msg) => {
          message.error(msg)
        },
      },
    )
  } catch (error: any) {
    message.error(error?.message || 'Agent 请求失败')
  } finally {
    loading.value = false
  }
}

const confirmAction = async () => {
  if (!confirmation.value) return
  try {
    loading.value = true
    const result = await AgentService.confirmAction(confirmation.value.actionId)
    if (result.success) {
      message.success(result.message || '操作成功')
      confirmation.value = null
      emit('actionDone', result)
    } else {
      message.error(result.message || '操作失败')
    }
  } catch (error: any) {
    message.error(error?.message || '确认失败')
  } finally {
    loading.value = false
  }
}

const applyAnswerToContent = () => {
  if (!answer.value.trim()) return
  emit('applyDraft', { content: [props.draft.content, answer.value.trim()].filter(Boolean).join('\n\n') })
}

const applyAnswerToSummary = () => {
  if (!answer.value.trim()) return
  emit('applyDraft', { summary: answer.value.trim().slice(0, 500) })
}

const applyPreview = () => {
  const preview = confirmation.value?.preview as AdminArticleDraftSnapshot | undefined
  if (!preview) return
  emit('applyDraft', preview)
}
</script>

<template>
  <aside class="agent-sidebar">
    <div class="agent-header">
      <div>
        <h3>看板娘 Agent</h3>
        <p>写作、草稿和发布辅助</p>
      </div>
    </div>

    <div class="quick-actions">
      <a-button
        v-for="item in quickPrompts"
        :key="item"
        size="small"
        @click="send(item)"
        :disabled="loading"
      >
        {{ item }}
      </a-button>
    </div>

    <a-textarea
      v-model:value="prompt"
      :rows="4"
      placeholder="告诉我你想怎么处理这篇文章..."
      :disabled="loading"
    />
    <a-button type="primary" block class="send-button" :disabled="!canSend" :loading="loading" @click="send()">
      <template #icon><SendOutlined /></template>
      发送给 Agent
    </a-button>

    <div v-if="plan.length" class="agent-section">
      <div class="section-title">思考与执行</div>
      <div v-if="agentStart" class="agent-meta">
        <a-tag color="blue">{{ agentStart.intent || 'AGENT' }}</a-tag>
        <a-tag>{{ agentStart.role || 'unknown' }}</a-tag>
      </div>
      <a-steps direction="vertical" size="small" :current="currentPlanIndex">
        <a-step v-for="step in plan" :key="step.key" :title="step.title" :description="step.status" />
      </a-steps>
    </div>

    <div v-if="toolEvents.length" class="agent-section">
      <div class="section-title">工具调用</div>
      <div class="tool-list">
        <div v-for="(tool, index) in toolEvents" :key="`${tool.toolName}-${index}`" class="tool-item" :class="tool.status">
          <span class="tool-dot"></span>
          <div class="tool-main">
            <div class="tool-title">{{ tool.displayName || tool.toolName }}</div>
            <div class="tool-desc">
              {{ tool.status === 'running' ? (tool.inputSummary || '执行中') : tool.status === 'success' ? (tool.resultSummary || '完成') : (tool.errorMessage || '失败') }}
            </div>
          </div>
        </div>
      </div>
    </div>

    <div v-if="answer" class="agent-section">
      <div class="section-title">回复</div>
      <div class="answer-box">{{ answer }}</div>
      <a-space wrap class="answer-actions">
        <a-button size="small" @click="applyAnswerToContent">
          <template #icon><EditOutlined /></template>
          追加到正文
        </a-button>
        <a-button size="small" @click="applyAnswerToSummary">作为摘要</a-button>
      </a-space>
    </div>

    <div v-if="articles.length" class="agent-section">
      <div class="section-title">文章结果</div>
      <a-list size="small" :data-source="articles">
        <template #renderItem="{ item }">
          <a-list-item>
            <a :href="item.adminUrl || item.url" target="_blank">{{ item.title }}</a>
          </a-list-item>
        </template>
      </a-list>
    </div>

    <a-alert
      v-if="confirmation"
      class="agent-section"
      type="warning"
      show-icon
      :message="confirmation.title"
      :description="confirmation.description"
    />
    <div v-if="confirmation" class="confirm-actions">
      <a-button size="small" @click="applyPreview">
        <template #icon><FileAddOutlined /></template>
        应用预览到表单
      </a-button>
      <a-popconfirm title="确认让 Agent 执行该操作吗？" @confirm="confirmAction">
        <a-button type="primary" size="small" :loading="loading">
          <template #icon><CheckCircleOutlined /></template>
          确认执行
        </a-button>
      </a-popconfirm>
    </div>
  </aside>
</template>

<style scoped>
.agent-sidebar {
  width: 320px;
  flex: 0 0 320px;
  border-left: 1px solid #f0f0f0;
  padding-left: 16px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  max-height: 72vh;
  overflow-y: auto;
}

.agent-header h3 {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
}

.agent-header p {
  margin: 4px 0 0;
  color: #8c8c8c;
  font-size: 12px;
}

.quick-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.send-button {
  margin-top: -4px;
}

.agent-section {
  border-top: 1px solid #f0f0f0;
  padding-top: 12px;
}

.section-title {
  font-size: 13px;
  color: #595959;
  margin-bottom: 8px;
  font-weight: 600;
}

.agent-meta {
  margin-bottom: 8px;
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

.tool-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.tool-item {
  display: flex;
  gap: 8px;
  align-items: flex-start;
  padding: 8px;
  border: 1px solid #f0f0f0;
  border-radius: 6px;
  background: #fafafa;
}

.tool-dot {
  width: 8px;
  height: 8px;
  border-radius: 999px;
  margin-top: 6px;
  background: #d9d9d9;
  flex: 0 0 8px;
}

.tool-item.running .tool-dot {
  background: #1677ff;
}

.tool-item.success .tool-dot {
  background: #52c41a;
}

.tool-item.failed .tool-dot {
  background: #ff4d4f;
}

.tool-main {
  min-width: 0;
}

.tool-title {
  font-size: 13px;
  color: #262626;
}

.tool-desc {
  margin-top: 2px;
  font-size: 12px;
  color: #8c8c8c;
  word-break: break-word;
}

.answer-box {
  white-space: pre-wrap;
  background: #fafafa;
  border: 1px solid #f0f0f0;
  border-radius: 6px;
  padding: 10px;
  font-size: 13px;
  line-height: 1.6;
}

.answer-actions,
.confirm-actions {
  margin-top: 8px;
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}
</style>
