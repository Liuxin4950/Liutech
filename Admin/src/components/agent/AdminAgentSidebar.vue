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
  TempMessage,
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
const showFullAnswer = ref(false)
const loading = ref(false)
const history = ref<TempMessage[]>([])
const applyNotice = ref('')
let noticeTimer: number | undefined
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

// 处理回答显示：长HTML正文只显示提示，不显示原始源码
const displayAnswer = computed(() => {
  if (!answer.value) return ''
  if (answer.value.length < 200) return answer.value
  const editorHasContent = props.draft.content && props.draft.content.trim().length > 0
  const textHasHtml = answer.value.includes('<p') || answer.value.includes('<h') || answer.value.includes('<pre')
  if (textHasHtml && editorHasContent) {
    const firstTag = answer.value.indexOf('<')
    if (firstTag > 10) {
      return answer.value.substring(0, firstTag).trim() + '\n\n✓ 正文已写入编辑器，可直接在富文本框查看编辑'
    }
    return '✓ 正文已写入编辑器，可直接在富文本框查看编辑'
  }
  return answer.value
})

const canExpandAnswer = computed(() => displayAnswer.value.length > 80)
const answerPreviewText = computed(() => {
  if (showFullAnswer.value || !canExpandAnswer.value) return displayAnswer.value
  return displayAnswer.value.substring(0, 80) + '...'
})
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
    showApplyNotice(hasConcreteUpdate
      ? `已自动应用：${activeFieldScope.value.fields.filter(field => field !== 'check').join('、')}`
      : '已生成可确认创建的分类/标签建议')
  }
}

const showApplyNotice = (message: string) => {
  applyNotice.value = message
  if (noticeTimer) window.clearTimeout(noticeTimer)
  noticeTimer = window.setTimeout(() => {
    applyNotice.value = ''
  }, 2600)
}

const compactToolStatus = (tool: ToolEventPayload & { status: 'running' | 'success' | 'failed' }) => {
  if (tool.status === 'running') return '执行中'
  if (tool.status === 'failed') return tool.errorMessage || '失败'
  return tool.resultSummary || '完成'
}

// 快速指令：字段级指令（含关键词让 inferFieldScope 只更新对应字段）+ Admin 特有操作（草稿/发布）
const quickPrompts = [
  { label: '写完整文章', message: '根据当前主题（或草稿）写一篇完整的技术博客，一次性输出 HTML 正文并设置标题、摘要、分类、标签' },
  { label: '润色正文', message: '润色当前正文，保持原意和结构，改善语言表达和排版' },
  { label: '补摘要', message: '根据正文生成一段 80-150 字的摘要' },
  { label: '改标题', message: '根据正文内容生成 3 个备选标题，选最合适的一个写入标题字段' },
  { label: '选分类标签', message: '为当前文章挑选最合适的分类和 3-5 个标签' },
  { label: '续写下一节', message: '基于当前正文的最后部分，续写下一节内容' },
  { label: '发布前检查', message: '检查正文是否存在明显问题：错别字、未闭合标签、过长段落、缺失摘要' },
  { label: '保存为草稿', message: '保存为草稿' },
  { label: '发布这篇文章', message: '发布这篇文章' },
]

const send = async (text?: string) => {
  const content = (text || prompt.value).trim()
  if (!content || loading.value) return
  loading.value = true
  answer.value = ''; showFullAnswer.value = false
  plan.value = []
  articles.value = []
  confirmation.value = null
  toolEvents.value = []
  agentStart.value = null
  applyNotice.value = ''
  prompt.value = ''
  activeFieldScope.value = inferFieldScope(content)

  try {
    await AgentService.stream(
      {
        message: content,
        draft: props.draft,
        tempMessages: history.value,
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
        // SSE 事件（2026-07-09 起后端已全部实现）：写作步骤追踪 onPlan/onToolStart/onToolResult、
        // 字段自动写入 onWritingDraft/onFieldUpdate、确认流程 onConfirmation、文章结果 onArticles。
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
    // 保存本轮对话上下文，支持多轮连续写作（"接着上一轮继续"）
    if (content && answer.value) {
      history.value.push({ role: 'user', content })
      history.value.push({ role: 'assistant', content: answer.value })
    }
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
        :key="item.label"
        size="small"
        @click="send(item.message)"
        :disabled="loading"
      >
        {{ item.label }}
      </a-button>
    </div>

    <a-textarea
      v-model:value="prompt"
      :rows="4"
      placeholder="告诉我你想怎么处理这篇文章...（Enter发送，Shift+Enter换行）"
      :disabled="loading"
      @keydown.enter.exact.prevent="send()"
      @keydown.shift.enter="() => {}"
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

    <p v-if="applyNotice" class="apply-notice">{{ applyNotice }}</p>

    <!-- AI回复区域 -->
    <div v-if="displayAnswer" class="agent-section answer-container">
      <div class="answer-header">
        <span class="answer-title">回复</span>
        <button v-if="canExpandAnswer" type="button" class="expand-btn" @click="showFullAnswer = !showFullAnswer">
          {{ showFullAnswer ? '收起' : '展开' }}
        </button>
      </div>
      <div 
        class="answer-box"
        :class="{ collapsed: canExpandAnswer && !showFullAnswer }"
        @click="canExpandAnswer && !showFullAnswer && (showFullAnswer = true)"
      >{{ answerPreviewText }}</div>
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
    </div>
  </aside>
</template>

<style scoped>
.agent-sidebar {
  width: 340px;
  flex: 0 0 340px;
  border-left: 1px solid var(--lt-color-border-secondary);
  padding-left: 16px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  max-height: 72vh;
  overflow-y: auto !important;
}

.agent-header h3 {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: var(--lt-color-text);
}

.agent-header p {
  margin: 4px 0 0;
  color: var(--lt-color-text-tertiary);
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
  border-top: 1px solid var(--lt-color-border-secondary);
  padding-top: 12px;
}

.section-title {
  font-size: 13px;
  color: var(--lt-color-text-secondary);
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
  background: var(--lt-color-bg-spotlight);
  border: 1px solid var(--lt-color-border-secondary);
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
  overflow: hidden !important;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--lt-color-primary);
  font-size: 12px;
}

.process-bar {
  height: 6px;
  overflow: hidden !important;
  border-radius: 999px;
  background: var(--lt-color-border-secondary);
}

.process-bar span {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: var(--lt-color-primary);
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
  color: var(--lt-color-text-tertiary);
}

.trace-row span:nth-child(2),
.trace-row em {
  min-width: 0;
  overflow: hidden !important;
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
  background: var(--lt-color-border);
}

.trace-row.running .trace-dot {
  background: var(--lt-color-primary);
  box-shadow: 0 0 0 4px var(--lt-color-primary-bg);
}

.trace-row.success .trace-dot,
.trace-row.completed .trace-dot {
  background: var(--lt-color-success);
}

.trace-row.waiting .trace-dot {
  background: var(--lt-color-warning);
}

.trace-row.failed .trace-dot {
  background: var(--lt-color-error);
}

.tool-summary {
  margin-top: 6px;
  padding-top: 6px;
  border-top: 1px dashed var(--lt-color-border-secondary);
}

.answer-container {
  margin-top: 4px;
}
.answer-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 6px;
}
.answer-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--lt-color-text);
}
.expand-btn {
  border: 1px solid var(--lt-color-primary);
  background: var(--lt-color-primary-bg);
  padding: 3px 10px;
  font-size: 12px;
  color: var(--lt-color-primary);
  cursor: pointer;
  border-radius: 6px;
  transition: all 0.2s;
  font-weight: 500;
}
.expand-btn:hover {
  background: var(--lt-color-primary);
  color: var(--lt-color-text-inverse);
}
.answer-box {
  margin-top: 0;
}
.answer-box.collapsed {
  -webkit-line-clamp: 3;
}
.answer-box {
  font-size: 12px;
  line-height: 1.6;
  color: var(--lt-color-text-secondary);
  background: var(--lt-color-bg-spotlight);
  border-radius: 8px;
  padding: 10px 12px;
  white-space: pre-wrap;
  word-break: break-word;
}
.answer-box.collapsed {
  max-height: 88px;
  overflow: hidden;
  position: relative;
  cursor: pointer;
  display: -webkit-box;
  -webkit-line-clamp: 4;
  -webkit-box-orient: vertical;
  -webkit-mask-image: linear-gradient(to bottom, black 60%, transparent 100%);
  mask-image: linear-gradient(to bottom, black 60%, transparent 100%);
}
.answer-box:not(.collapsed) {
  max-height: 360px;
  overflow-y: auto;
}
.answer-box:not(.collapsed)::-webkit-scrollbar {
  width: 4px;
}
.answer-box:not(.collapsed)::-webkit-scrollbar-thumb {
  background: var(--lt-color-border);
  border-radius: 2px;
}

.answer-box:not(.collapsed) {
  max-height: 400px !important;
  overflow-y: auto !important;
  cursor: default;
}

.answer-box.collapsed::after {
  content: '';
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  height: 30px;
  background: linear-gradient(transparent, var(--lt-color-bg-spotlight));
  pointer-events: none;
}

.confirm-actions {
  margin-top: 8px;
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.apply-notice {
  margin: 0;
  padding: 9px 10px;
  border-radius: 6px;
  background: var(--lt-color-success-bg);
  color: var(--lt-color-success);
  font-size: 13px;
}
</style>
