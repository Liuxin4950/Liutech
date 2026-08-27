<template>
  <aside class="writing-assistant">
    <div class="assistant-header">
      <div>
        <h3>纳西妲写作助手</h3>
        <p>生成、润色、排版和发布前检查</p>
      </div>
    </div>

    <div class="quick-actions">
      <button v-for="item in quickPrompts" :key="item.label" type="button" :disabled="loading" @click="send(item.message)">
        {{ item.label }}
      </button>
    </div>

    <textarea v-model="prompt" :disabled="loading" placeholder="告诉纳西妲你想写什么...（Enter发送，Shift+Enter换行）" rows="4" @keydown.enter.exact.prevent="send()" @keydown.shift.enter="() => {}"></textarea>
    <button type="button" class="send-btn" :disabled="!canSend" @click="send()">
      {{ loading ? '生成中...' : '发送给纳西妲' }}
    </button>

    <section v-if="showProcessCard" class="assistant-section process-card">
      <div class="section-title process-title">
        <span>执行过程</span>
        <strong>{{ currentProcessLabel }}</strong>
      </div>
      <div class="process-bar" aria-hidden="true">
        <span :style="{ width: `${processPercent}%` }"></span>
      </div>
      <div class="process-steps compact">
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
      <div v-if="contentCharCount > 0" class="trace-row content-progress">
        <span class="trace-dot"></span>
        <span>生成正文</span>
        <em>{{ contentCharCount }} 字</em>
      </div>
    </section>

    <p v-if="applyNotice" class="assistant-success">{{ applyNotice }}</p>
    <!-- AI回复区域 -->
    <section v-if="displayAnswer" class="assistant-section answer-container">
      <div class="answer-header">
        <span class="answer-title">纳西妲</span>
        <button v-if="canExpand" type="button" class="expand-btn" @click="showFullAnswer = !showFullAnswer">
          {{ showFullAnswer ? "收起" : "展开" }}
        </button>
      </div>
      <div 
        class="answer-content"
        :class="{ collapsed: canExpand && !showFullAnswer }"
        @click="canExpand && !showFullAnswer && (showFullAnswer = true)"
      >{{ previewText }}</div>
    </section>

    <!-- AI 引用的关联文章 -->
    <section v-if="articleResults.length" class="assistant-section article-results">
      <div class="answer-header">
        <span class="answer-title">{{ articleResultReason }}</span>
      </div>
      <div class="article-result-list">
        <a
          v-for="post in articleResults"
          :key="post.id"
          class="article-result-item"
          :href="`/post/${post.id}`"
          target="_blank"
          rel="noopener"
        >
          <span class="article-result-title">{{ post.title }}</span>
          <span class="article-result-arrow">›</span>
        </a>
      </div>
    </section>

    <p v-if="error" class="assistant-error">{{ error }}</p>
  </aside>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { AdminAgentService, type AdminArticleDraftSnapshot, type AgentPlanStep, type ToolEventPayload, type FieldUpdatePayload, type TempMessage } from '@/services/adminAgent'
import type { PostSummaryDTO } from '@/services/ai'

const props = defineProps<{
  draft: AdminArticleDraftSnapshot
}>()

const emit = defineEmits<{
  fieldUpdate: [payload: FieldUpdatePayload]
}>()

const prompt = ref('')
const loading = ref(false)
const answer = ref('')
const showFullAnswer = ref(false)
const history = ref<TempMessage[]>([])
const error = ref('')
type StepStatus = 'pending' | 'running' | 'completed' | 'waiting' | 'failed'
type AssistantStep = AgentPlanStep & { status: StepStatus }

const plan = ref<AssistantStep[]>([])
const toolEvents = ref<Array<ToolEventPayload & { status: 'running' | 'success' | 'failed' }>>([])
const applyNotice = ref('')
const articleResults = ref<PostSummaryDTO[]>([])
const articleResultReason = ref('')
const contentCharCount = ref(0)
let noticeTimer: number | undefined
type RequestedField = 'title' | 'summary' | 'content' | 'category' | 'tags' | 'check'
type FieldScope = {
  fields: RequestedField[]
  appendTags: boolean
}
const activeFieldScope = ref<FieldScope>({ fields: ['title', 'summary', 'content', 'category', 'tags'], appendTags: false })

const quickPrompts: Array<{label: string, message: string}> = [
  { label: '写完整文章', message: '根据当前主题（或草稿）写一篇完整的技术博客，一次性输出 HTML 正文并设置标题、摘要、分类、标签' },
  { label: '润色正文', message: '润色当前正文，保持原意和结构，改善语言表达和排版' },
  { label: '补摘要', message: '根据正文生成一段 80-150 字的摘要' },
  { label: '改标题', message: '根据正文内容生成 3 个备选标题，选最合适的一个写入标题字段' },
  { label: '选分类标签', message: '为当前文章挑选最合适的分类和 3-5 个标签' },
  { label: '续写下一节', message: '基于当前正文的最后部分，续写下一节内容' },
  { label: '发布前检查', message: '检查正文是否存在明显问题：错别字、未闭合标签、过长段落、缺失摘要' },
]

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

const canExpand = computed(() => displayAnswer.value.length > 80)
const previewText = computed(() => {
  if (showFullAnswer.value || !canExpand.value) return displayAnswer.value
  return displayAnswer.value.substring(0, 80) + '...'
})
const canSend = computed(() => prompt.value.trim().length > 0 && !loading.value)

const fallbackPlan: AgentPlanStep[] = [
  { key: 'understand', title: '理解写作目标', status: 'pending' },
  { key: 'context', title: '读取当前草稿', status: 'pending' },
  { key: 'taxonomy', title: '匹配分类和标签', status: 'pending' },
  { key: 'html', title: '生成富文本 HTML', status: 'pending' }
]

const showProcessCard = computed(() => loading.value || !!error.value)

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
  if (loading.value) return '准备中'
  if (reachedPlan.value.length) return '已完成'
  return '待开始'
})

const processPercent = computed(() => {
  const steps = plan.value.filter(step => step.key !== 'apply')
  if (!steps.length) return loading.value ? 12 : 0
  const completed = steps.filter(step => step.status === 'completed' || step.status === 'waiting').length
  const runningBonus = steps.some(step => step.status === 'running') ? 0.55 : 0
  return Math.min(100, Math.round(((completed + runningBonus) / steps.length) * 100))
})

const latestTool = computed(() => {
  return toolEvents.value[toolEvents.value.length - 1]
})

const compactToolStatus = (tool: ToolEventPayload & { status: 'running' | 'success' | 'failed' }) => {
  if (tool.status === 'running') return '执行中'
  if (tool.status === 'failed') return tool.errorMessage || '失败'
  return tool.resultSummary || '完成'
}

const statusLabel = (status: StepStatus) => {
  const labels: Record<StepStatus, string> = {
    pending: '待处理',
    running: '进行中',
    completed: '完成',
    waiting: '等待中',
    failed: '失败'
  }
  return labels[status] || status
}

const normalizePlan = (steps: AgentPlanStep[]) => {
  const source = steps.length ? steps : fallbackPlan
  plan.value = source.map((step, index) => ({
    ...step,
    status: (step.status || (index === 0 ? 'running' : 'pending')) as StepStatus
  }))
}

const ensurePlan = () => {
  if (!plan.value.length) normalizePlan(fallbackPlan)
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
  plan.value = plan.value.map(step => ({
    ...step,
    status: step.status === 'failed' ? 'failed' : 'completed'
  }))
}

const completeRunningStep = () => {
  const runningStep = plan.value.find(step => step.status === 'running')
  if (runningStep) setStepStatus(runningStep.key, 'completed')
}

const inferFieldScope = (message: string): FieldScope => {
  const text = message.toLowerCase()
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

const TOOL_STEP_MAP: Record<string, string> = {
  'admin.listCategories': 'taxonomy',
  'admin.listTags': 'taxonomy',
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

const send = async (text?: string) => {
  const content = (text || prompt.value).trim()
  if (!content || loading.value) return
  loading.value = true
  answer.value = ''; showFullAnswer.value = false
  error.value = ''
  plan.value = []
  toolEvents.value = []
  applyNotice.value = ''
  articleResults.value = []
  articleResultReason.value = ''
  contentCharCount.value = 0
  prompt.value = ''
  activeFieldScope.value = inferFieldScope(content)
  try {
    await AdminAgentService.stream({
      message: content,
      draft: props.draft,
      tempMessages: history.value,
      context: {
        page: 'admin-post-editor',
        source: 'web-create-post',
        postId: props.draft.postId,
        requestedFields: activeFieldScope.value.fields,
        appendTags: activeFieldScope.value.appendTags
      }
    }, {
      onStart: () => {
        progressTo('understand', 'running')
      },
      onData: chunk => {
        answer.value += chunk
        contentCharCount.value += chunk.length
        progressTo('html', 'running')
      },
      onArticles: (items, payload) => {
        articleResults.value = items || []
        articleResultReason.value = payload?.reason || '这些文章可以继续阅读'
      },
      onToolStart: payload => {
        const stepKey = inferStepKey(payload)
        if (stepKey) progressTo(stepKey)
        toolEvents.value.push({ ...payload, status: 'running' })
      },
      onToolResult: payload => {
        const index = toolEvents.value.findIndex(item => item.toolName === payload.toolName && item.status === 'running')
        const next = { ...payload, status: payload.success === false ? 'failed' as const : 'success' as const }
        if (index >= 0) toolEvents.value[index] = next
        else toolEvents.value.push(next)
        const stepKey = inferStepKey(payload)
        if (stepKey) {
          if (payload.success === false) setStepStatus(stepKey, 'failed')
          else completeUpTo(stepKey)
        }
      },
      onFieldUpdate: payload => {
        emitScopedUpdate(payload)
        completeWritingPlan()
      },
      onComplete: () => {
        completeRunningStep()
      },
      onError: message => {
        error.value = message
        const runningStep = plan.value.find(step => step.status === 'running')
        if (runningStep) setStepStatus(runningStep.key, 'failed')
      }
    })
  } catch (e: any) {
    error.value = e?.message || '写作助手请求失败'
    const runningStep = plan.value.find(step => step.status === 'running')
    if (runningStep) setStepStatus(runningStep.key, 'failed')
  } finally {
    loading.value = false
    if (content && answer.value) {
      history.value.push({ role: 'user', content })
      history.value.push({ role: 'assistant', content: answer.value })
    }
  }
}
</script>

<style scoped>
.writing-assistant {
  background: var(--bg-card);
  border: 1px solid var(--border-light);
  border-radius: 8px;
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  color: var(--text-main);
  transition: background-color 0.2s ease, border-color 0.2s ease, color 0.2s ease;
}

.assistant-header h3 {
  margin: 0;
  font-size: 16px;
  color: var(--text-title);
}

.assistant-header p,
.summary,
.hint {
  margin: 4px 0 0;
  color: var(--text-subtle);
  font-size: 13px;
}

.quick-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

button {
  border: 1px solid var(--border-light);
  background: var(--bg-element);
  color: var(--text-main);
  border-radius: 6px;
  padding: 7px 10px;
  cursor: pointer;
  transition: background-color 0.2s ease, border-color 0.2s ease, color 0.2s ease;
}

button:hover:not(:disabled) {
  border-color: var(--border-base);
  background: var(--bg-hover);
}

button:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.send-btn {
  background: var(--color-primary);
  color: var(--text-on-primary);
  border-color: var(--color-primary);
}

textarea {
  width: 100%;
  border: 1px solid var(--border-light);
  border-radius: 6px;
  padding: 10px;
  resize: vertical;
  background: var(--bg-element);
  color: var(--text-main);
  outline: none;
  transition: background-color 0.2s ease, border-color 0.2s ease, color 0.2s ease;
}

textarea::placeholder {
  color: var(--text-muted);
}

textarea:focus {
  border-color: var(--color-primary);
  box-shadow: 0 0 0 2px color-mix(in srgb, var(--color-primary) 22%, transparent);
}

.assistant-section {
  border-top: 1px solid var(--border-light);
  padding-top: 12px;
}

.process-card {
  background: var(--bg-soft);
  border: 1px solid var(--border-light);
  border-radius: 8px;
  padding: 12px;
}

.section-title {
  font-size: 13px;
  font-weight: 600;
  margin-bottom: 8px;
}

.process-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.process-title strong {
  max-width: 52%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--color-primary);
  font-size: 12px;
  font-weight: 600;
}

.process-steps {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.process-steps.compact {
  margin-top: 10px;
}

.process-bar {
  position: relative;
  height: 6px;
  overflow: hidden;
  border-radius: 999px;
  background: var(--border-light);
}

.process-bar span {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: var(--color-primary);
  transition: width 0.35s ease;
}

.trace-row {
  display: grid;
  grid-template-columns: 10px minmax(0, 1fr) auto;
  gap: 8px;
  align-items: center;
  font-size: 12px;
  color: var(--text-subtle);
  min-height: 22px;
}

.trace-row span:nth-child(2),
.trace-row em {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.trace-dot {
  width: 7px;
  height: 7px;
  border-radius: 999px;
  background: var(--border-base);
}

.trace-row em {
  color: var(--text-muted);
  font-style: normal;
  white-space: nowrap;
}

.trace-row.running .trace-dot {
  background: var(--color-primary);
  box-shadow: 0 0 0 4px rgba(45, 144, 205, 0.12);
}

.trace-row.success .trace-dot,
.trace-row.completed .trace-dot {
  background: var(--color-success);
}

.trace-row.waiting .trace-dot {
  background: var(--color-warning);
}

.trace-row.failed .trace-dot {
  background: var(--color-error);
}

.tool-summary {
  margin-top: 6px;
  padding-top: 6px;
  border-top: 1px dashed var(--border-light);
}

.answer {
  white-space: pre-wrap;
  font-size: 13px;
  color: var(--text-subtle);
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
  color: var(--text-main);
}
.expand-btn {
  border: 1px solid var(--color-primary);
  background: color-mix(in srgb, var(--color-primary) 8%, transparent);
  padding: 3px 10px;
  font-size: 12px;
  color: var(--color-primary);
  cursor: pointer;
  border-radius: 6px;
  transition: all 0.2s;
  font-weight: 500;
}
.expand-btn:hover {
  background: var(--color-primary);
  color: white;
}
.answer-content {
  font-size: 13px;
  line-height: 1.6;
  color: var(--text-subtle);
  background: var(--bg-soft);
  border-radius: 8px;
  padding: 10px 12px;
  white-space: pre-wrap;
  word-break: break-word;
}
.answer-content.collapsed {
  max-height: 88px;
  overflow: hidden;
  position: relative;
  cursor: pointer;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  -webkit-mask-image: linear-gradient(to bottom, black 60%, transparent 100%);
  mask-image: linear-gradient(to bottom, black 60%, transparent 100%);
}
.answer-content:not(.collapsed) {
  max-height: 360px;
  overflow-y: auto;
}

.assistant-error {
  color: var(--color-error);
  font-size: 13px;
}

.assistant-success {
  margin: 0;
  padding: 9px 10px;
  border-radius: 6px;
  background: rgba(42, 157, 143, 0.12);
  color: var(--color-success);
  font-size: 13px;
}

.article-result-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.article-result-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 8px 10px;
  border: 1px solid var(--border-light);
  border-radius: 6px;
  text-decoration: none;
  transition: all 0.2s;
}
.article-result-item:hover {
  border-color: var(--color-primary);
  background: color-mix(in srgb, var(--color-primary) 6%, transparent);
}
.article-result-title {
  font-size: 13px;
  color: var(--text-main);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.article-result-arrow {
  color: var(--text-subtle);
  font-size: 16px;
  flex-shrink: 0;
}
</style>


