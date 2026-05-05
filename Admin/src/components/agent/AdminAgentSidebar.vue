<script setup lang="ts">
import { computed, ref } from 'vue'
import { message } from 'ant-design-vue'
import { SendOutlined, CheckCircleOutlined, FileAddOutlined } from '@ant-design/icons-vue'
import AgentService from '../../services/agent'
import type {
  AdminArticleDraftSnapshot,
  AgentPlanStep,
  ArticleResultItem,
  ConfirmationRequiredPayload,
  AgentActionResult,
  AgentStartPayload,
  ToolEventPayload,
  WritingDraftPayload,
  FieldUpdatePayload,
} from '../../types/agent'

const props = defineProps<{
  draft: AdminArticleDraftSnapshot
}>()

const emit = defineEmits<{
  fieldUpdate: [payload: FieldUpdatePayload]
  actionDone: [result?: AgentActionResult]
}>()

const prompt = ref('')
const answer = ref('')
const loading = ref(false)
type StepStatus = 'pending' | 'running' | 'completed' | 'waiting' | 'failed'
type AssistantStep = AgentPlanStep & { status: StepStatus }

const plan = ref<AssistantStep[]>([])
const articles = ref<ArticleResultItem[]>([])
const confirmation = ref<ConfirmationRequiredPayload | null>(null)
const toolEvents = ref<Array<ToolEventPayload & { status: 'running' | 'success' | 'failed' }>>([])
const agentStart = ref<AgentStartPayload | null>(null)
type RequestedField = 'title' | 'summary' | 'content' | 'category' | 'tags' | 'check'
type FieldScope = {
  fields: RequestedField[]
  appendTags: boolean
}
const activeFieldScope = ref<FieldScope>({ fields: ['title', 'summary', 'content', 'category', 'tags'], appendTags: false })

const canSend = computed(() => prompt.value.trim().length > 0 && !loading.value)
const showProcessCard = computed(() => loading.value)
const reachedPlan = computed(() => {
  const reached = plan.value.filter(step => step.status !== 'pending' && step.key !== 'apply')
  if (reached.length) return reached
  return plan.value.length ? [plan.value[0]] : []
})
const compactPlan = computed(() => {
  const runningIndex = reachedPlan.value.findIndex(step => step.status === 'running' || step.status === 'waiting')
  if (runningIndex >= 0) return reachedPlan.value.slice(Math.max(0, runningIndex - 2), runningIndex + 1)
  return reachedPlan.value.slice(-3)
})
const currentProcessLabel = computed(() => {
  const active = plan.value.find(step => step.status === 'running')
  if (active) return active.title
  return loading.value ? '准备中' : '已完成'
})
const processPercent = computed(() => {
  const steps = plan.value.filter(step => step.key !== 'apply')
  if (!steps.length) return loading.value ? 12 : 0
  const completed = steps.filter(step => step.status === 'completed' || step.status === 'waiting').length
  const runningBonus = steps.some(step => step.status === 'running') ? 0.55 : 0
  return Math.min(100, Math.round(((completed + runningBonus) / steps.length) * 100))
})
const latestTool = computed(() => {
  const safeTools = toolEvents.value.filter(tool => tool.toolName !== 'admin.generateWritingHtml' || tool.status === 'running')
  return safeTools[safeTools.length - 1]
})

const normalizePlan = (steps: AgentPlanStep[]) => {
  plan.value = steps.map((step, index) => ({
    ...step,
    status: (step.status || (index === 0 ? 'running' : 'pending')) as StepStatus,
  }))
}

const ensurePlan = () => {
  if (!plan.value.length) {
    normalizePlan([
      { key: 'understand', title: '理解写作目标', status: 'pending' },
      { key: 'context', title: '读取当前草稿', status: 'pending' },
      { key: 'taxonomy', title: '匹配分类和标签', status: 'pending' },
      { key: 'html', title: '生成富文本 HTML', status: 'pending' },
      { key: 'apply', title: '写入表单字段', status: 'pending' },
    ])
  }
}

const setStepStatus = (key: string, status: StepStatus) => {
  ensurePlan()
  plan.value = plan.value.map(step => step.key === key ? { ...step, status } : step)
}

const progressTo = (key: string, status: StepStatus = 'running') => {
  ensurePlan()
  const target = plan.value.find(step => step.key === key)
  if (!target || target.status === 'failed') return
  if (target.status === status || target.status === 'completed') return
  plan.value = plan.value.map(step => step.key === key ? { ...step, status } : step)
}

const completeUpTo = (key: string) => {
  ensurePlan()
  const index = plan.value.findIndex(step => step.key === key)
  if (index < 0) return
  plan.value = plan.value.map((step, i) => {
    if (i <= index && step.status !== 'failed') return { ...step, status: 'completed' }
    return step
  })
}

const completeWritingPlan = () => {
  ensurePlan()
  plan.value = plan.value.map(step => {
    if (step.key === 'apply') return { ...step, status: 'waiting' }
    return { ...step, status: step.status === 'failed' ? 'failed' : 'completed' }
  })
}

const completeRunningStep = () => {
  const runningStep = plan.value.find(step => step.status === 'running')
  if (runningStep) setStepStatus(runningStep.key, 'completed')
}

const statusLabel = (status: StepStatus) => ({
  pending: '待处理',
  running: '进行中',
  completed: '完成',
  waiting: '已写入',
  failed: '失败',
}[status] || status)

const TOOL_STEP_MAP: Record<string, string> = {
  'admin.listCategories': 'taxonomy',
  'admin.listTags': 'taxonomy',
  'admin.generateWritingHtml': 'html',
  'public.getArticleDetail': 'context',
}

const inferStepKey = (payload: ToolEventPayload) => {
  if (payload.toolName && TOOL_STEP_MAP[payload.toolName]) return TOOL_STEP_MAP[payload.toolName]
  const text = `${payload.displayName || ''} ${payload.inputSummary || ''}`.toLowerCase()
  if (text.includes('分类') || text.includes('标签')) return 'taxonomy'
  if (text.includes('html') || text.includes('富文本')) return 'html'
  if (text.includes('文章') || text.includes('草稿')) return 'context'
  return ''
}

const inferFieldScope = (messageText: string): FieldScope => {
  const text = messageText.toLowerCase()
  const hasAny = (...words: string[]) => words.some(word => text.includes(word.toLowerCase()))
  const all = hasAny('写一篇', '生成一篇', '新文章', '完整文章', '整篇', '全文', '全部', '应用全部')
  const checkOnly = hasAny('发布前检查', '检查一下', '帮我检查', '审查一下', '看看有没有问题') && !hasAny('修复', '修改', '改成', '写入', '应用')
  if (checkOnly) return { fields: ['check'], appendTags: false }
  const fields: RequestedField[] = []
  if (hasAny('标题', '题目', 'seo')) fields.push('title')
  if (hasAny('摘要', '简介', '概述', 'seo', '描述')) fields.push('summary')
  if (hasAny('正文', '富文本', 'html', '排版', '格式', '润色', '续写', '扩写', '改写', '章节', '代码', '段落')) fields.push('content')
  if (hasAny('分类', '栏目')) fields.push('category')
  if (hasAny('标签', 'tag')) fields.push('tags')
  const appendTags = fields.includes('tags') && hasAny('加', '增加', '添加', '补', '补充', '追加', '再来')
  return { fields: all || fields.length === 0 ? ['title', 'summary', 'content', 'category', 'tags'] : fields, appendTags }
}

const filterFieldUpdate = (payload: FieldUpdatePayload, scope = activeFieldScope.value): FieldUpdatePayload => {
  if (scope.fields.includes('check')) return {}
  const next: FieldUpdatePayload = {}
  if (scope.fields.includes('title')) next.title = payload.title
  if (scope.fields.includes('summary')) next.summary = payload.summary
  if (scope.fields.includes('content')) next.contentHtml = payload.contentHtml
  if (scope.fields.includes('category')) {
    next.categoryId = payload.categoryId
    next.categoryName = payload.categoryName
    next.suggestedCategoryName = payload.suggestedCategoryName
  }
  if (scope.fields.includes('tags')) {
    next.tagIds = payload.tagIds
    next.tagNames = payload.tagNames
    next.suggestedTagNames = payload.suggestedTagNames
  }
  return Object.fromEntries(Object.entries(next).filter(([, value]) => value !== undefined && value !== null)) as FieldUpdatePayload
}

const emitScopedUpdate = (payload: FieldUpdatePayload) => {
  const scoped = filterFieldUpdate(payload)
  const hasConcreteUpdate = !!(scoped.title || scoped.summary || scoped.contentHtml || scoped.categoryId || scoped.tagIds?.length)
  const hasSuggestion = !!(scoped.suggestedCategoryName || scoped.suggestedTagNames?.length)
  if (hasConcreteUpdate || hasSuggestion) {
    emit('fieldUpdate', scoped)
  }
}

const compactToolStatus = (tool: ToolEventPayload & { status: 'running' | 'success' | 'failed' }) => {
  if (tool.status === 'running') return '执行中'
  if (tool.status === 'failed') return tool.errorMessage || '失败'
  return tool.resultSummary || '完成'
}

const quickPrompts = [
  '帮我润色当前文章',
  '根据当前内容生成摘要',
  '帮我补一个更吸引人的标题',
  '整理成技术博客 HTML',
  '发布前检查',
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
  activeFieldScope.value = inferFieldScope(content)

  try {
    await AgentService.stream(
      {
        message: content,
        draft: props.draft,
        context: {
          page: 'admin-post-editor',
          postId: props.draft.postId,
          requestedFields: activeFieldScope.value.fields,
          appendTags: activeFieldScope.value.appendTags,
        },
      },
      {
        onData: (chunk) => {
          answer.value += chunk
          progressTo('html', 'running')
        },
        onPlan: (steps) => {
          normalizePlan(steps)
        },
        onStart: (payload) => {
          agentStart.value = payload
        },
        onToolStart: (payload) => {
          const stepKey = inferStepKey(payload)
          if (stepKey) progressTo(stepKey)
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
          const stepKey = inferStepKey(payload)
          if (stepKey) {
            if (payload.success === false) setStepStatus(stepKey, 'failed')
            else completeUpTo(stepKey)
          }
        },
        onArticles: (items) => {
          articles.value = items
        },
        onConfirmation: (payload) => {
          confirmation.value = payload
        },
        onWritingDraft: (payload) => {
          const fields: FieldUpdatePayload = {}
          if (payload.title) fields.title = payload.title
          if (payload.summary) fields.summary = payload.summary
          if (payload.contentHtml) fields.contentHtml = payload.contentHtml
          if (payload.categoryId) fields.categoryId = payload.categoryId
          if (payload.categoryName) fields.categoryName = payload.categoryName
          if (payload.tagIds?.length) fields.tagIds = payload.tagIds
          if (payload.tagNames?.length) fields.tagNames = payload.tagNames
          if (payload.suggestedCategoryName) fields.suggestedCategoryName = payload.suggestedCategoryName
          if (payload.suggestedTagNames?.length) fields.suggestedTagNames = payload.suggestedTagNames
          emitScopedUpdate(fields)
          completeWritingPlan()
        },
        onFieldUpdate: (payload) => {
          emitScopedUpdate(payload)
          completeWritingPlan()
        },
        onError: (msg) => {
          message.error(msg)
          const runningStep = plan.value.find(step => step.status === 'running')
          if (runningStep) setStepStatus(runningStep.key, 'failed')
        },
        onComplete: () => {
          completeRunningStep()
        },
      },
    )
  } catch (error: any) {
    message.error(error?.message || 'Agent 请求失败')
    const runningStep = plan.value.find(step => step.status === 'running')
    if (runningStep) setStepStatus(runningStep.key, 'failed')
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

const applyPreview = () => {
  const preview = confirmation.value?.preview as AdminArticleDraftSnapshot | undefined
  if (!preview) return
  // Convert preview to field updates
  const fields: FieldUpdatePayload = {}
  if ('title' in preview && preview.title) fields.title = preview.title as string
  if ('summary' in preview && preview.summary) fields.summary = preview.summary as string
  if ('contentHtml' in preview && (preview as any).contentHtml) fields.contentHtml = (preview as any).contentHtml as string
  if ('content' in preview && (preview as any).content) fields.contentHtml = (preview as any).content as string
  if ('categoryId' in preview && (preview as any).categoryId) fields.categoryId = (preview as any).categoryId as number
  if ('tagIds' in preview && (preview as any).tagIds?.length) fields.tagIds = (preview as any).tagIds as number[]
  if (Object.keys(fields).length > 0) {
    emit('fieldUpdate', fields)
  }
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

    <div v-if="showProcessCard" class="agent-section process-card">
      <div class="process-title">
        <span>执行过程</span>
        <strong>{{ currentProcessLabel }}</strong>
      </div>
      <div v-if="agentStart" class="agent-meta">
        <a-tag color="blue">{{ agentStart.intent || 'AGENT' }}</a-tag>
        <a-tag>{{ agentStart.role || 'unknown' }}</a-tag>
      </div>
      <div class="process-bar" aria-hidden="true">
        <span :style="{ width: `${processPercent}%` }"></span>
      </div>
      <div class="compact-steps">
        <div v-for="step in compactPlan" :key="step.key" class="trace-row" :class="step.status">
          <span class="trace-dot"></span>
          <span>{{ step.title }}</span>
          <em>{{ statusLabel(step.status) }}</em>
        </div>
      </div>
      <div v-if="latestTool" class="trace-row tool-summary" :class="latestTool.status">
        <span class="trace-dot"></span>
        <span>{{ latestTool.displayName || latestTool.toolName }}</span>
        <em>{{ compactToolStatus(latestTool) }}</em>
      </div>
    </div>

    <div v-if="answer" class="agent-section">
      <div class="section-title">回复</div>
      <div class="answer-box">{{ answer }}</div>
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

.process-card {
  background: #fafafa;
  border: 1px solid #f0f0f0;
  border-radius: 8px;
  padding: 12px;
}

.process-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  font-size: 13px;
  font-weight: 600;
  margin-bottom: 8px;
}

.process-title strong {
  max-width: 56%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: #1677ff;
  font-size: 12px;
}

.process-bar {
  height: 6px;
  overflow: hidden;
  border-radius: 999px;
  background: #f0f0f0;
}

.process-bar span {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: #1677ff;
  transition: width 0.35s ease;
}

.compact-steps {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-top: 10px;
}

.trace-row {
  display: grid;
  grid-template-columns: 10px minmax(0, 1fr) auto;
  gap: 8px;
  align-items: center;
  min-height: 22px;
  font-size: 12px;
  color: #8c8c8c;
}

.trace-row span:nth-child(2),
.trace-row em {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.trace-row em {
  font-style: normal;
}

.trace-dot {
  width: 7px;
  height: 7px;
  border-radius: 999px;
  background: #d9d9d9;
}

.trace-row.running .trace-dot {
  background: #1677ff;
  box-shadow: 0 0 0 4px rgba(22, 119, 255, 0.12);
}

.trace-row.success .trace-dot,
.trace-row.completed .trace-dot {
  background: #52c41a;
}

.trace-row.waiting .trace-dot {
  background: #faad14;
}

.trace-row.failed .trace-dot {
  background: #ff4d4f;
}

.tool-summary {
  margin-top: 6px;
  padding-top: 6px;
  border-top: 1px dashed #f0f0f0;
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

.confirm-actions {
  margin-top: 8px;
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}
</style>
