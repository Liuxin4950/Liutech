<script setup lang="ts">
import { computed, ref } from 'vue'
import { message } from 'ant-design-vue'
import { SendOutlined } from '@ant-design/icons-vue'
import AgentService from '../../services/agent'
import type {
  AdminArticleDraftSnapshot,
  ArticleResultItem,
  ToolEventPayload,
  FieldUpdatePayload,
  TempMessage,
} from '../../types/agent'

const props = defineProps<{
  draft: AdminArticleDraftSnapshot
}>()

const emit = defineEmits<{
  fieldUpdate: [payload: FieldUpdatePayload]
}>()

const prompt = ref('')
const answer = ref('')
const showFullAnswer = ref(false)
const loading = ref(false)
const history = ref<TempMessage[]>([])
const applyNotice = ref('')
let noticeTimer: number | undefined

// ============== 真实活动时间线 ==============
// 时间线只由后端 SSE 事件驱动（start / tool-start / tool-result / field-update /
// article-results / data / error），前端不预设步骤、不猜进度、不出现百分比。
type ToolActivityStatus = 'running' | 'success' | 'failed'

interface ActivityBase {
  /** 渲染用的稳定 key */
  id: number
}

/** 连接提示（start 事件） */
interface NoticeActivity extends ActivityBase {
  type: 'notice'
  text: string
  /** start 事件里的真实模型名，取不到就不展示 */
  model?: string
}

/** 一次真实的工具调用 */
interface ToolActivity extends ActivityBase {
  type: 'tool'
  toolName: string
  displayName: string
  inputSummary?: string
  status: ToolActivityStatus
  /** tool-start 上报的真实开始时间（epoch 毫秒） */
  startedAt?: number
  /** tool-result 上报的真实耗时（毫秒） */
  durationMs?: number
  resultSummary?: string
  errorMessage?: string
}

/** 一次真实的字段写入 */
interface FieldActivity extends ActivityBase {
  type: 'field'
  /** 本次真实写入的字段名 */
  fields: string[]
}

/** 一次真实的文章检索结果 */
interface ArticlesActivity extends ActivityBase {
  type: 'articles'
  count: number
}

/** 后端真实错误文案（error 事件或请求异常） */
interface ErrorActivity extends ActivityBase {
  type: 'error'
  text: string
}

type ActivityItem = NoticeActivity | ToolActivity | FieldActivity | ArticlesActivity | ErrorActivity

const activity = ref<ActivityItem[]>([])
/** 正文真实累计字数（data 事件分片长度之和） */
const generatedChars = ref(0)
/** 是否已收到正文分片，用于区分"等模型响应"与"正在生成正文" */
const receivingData = ref(false)
/** 后端真实错误文案，有值时状态行直接显示它 */
const streamError = ref('')
/** 是否收到终态事件（complete / error） */
const finished = ref(false)
let activitySeq = 0

/** 时间线条目 id：只用于 v-for 的 key */
const nextActivityId = () => {
  activitySeq += 1
  return activitySeq
}

const articles = ref<ArticleResultItem[]>([])
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
// 时间线一旦有内容就保留展示，便于结束后回看这一轮真实做了什么
const showProcessCard = computed(() => loading.value || activity.value.length > 0)

/** 真实工具调用条目（调用次数、失败次数都直接数它，保证与展示一致） */
const toolItems = computed(() => activity.value.filter((item): item is ToolActivity => item.type === 'tool'))
const toolFailCount = computed(() => toolItems.value.filter(item => item.status === 'failed').length)

/** 累计写入过的字段标签（去重），用于结束后的汇总 */
const writtenFieldLabels = computed(() => {
  const labels = new Set<string>()
  activity.value.forEach(item => {
    if (item.type === 'field') item.fields.forEach(field => labels.add(fieldLabel(field)))
  })
  return labels
})

/** 结束后的真实汇总：完成 · 调用工具 N 次 · 写入 M 个字段 · 生成 X 字 */
const summaryText = computed(() => {
  const parts = [
    '完成',
    `调用工具 ${toolItems.value.length} 次`,
    `AI 写入 ${writtenFieldLabels.value.size} 个字段`,
    `生成 ${generatedChars.value} 字`,
  ]
  let text = parts.join(' · ')
  if (toolFailCount.value > 0) text += `（${toolFailCount.value} 次失败）`
  return text
})

/**
 * 状态行：替代原来的假百分比。
 * 运行中显示最近一条正在执行的真实工具，结束后显示真实汇总，有错误直接显示后端文案。
 */
const statusLine = computed(() => {
  if (streamError.value) return streamError.value
  if (loading.value) {
    const running = [...activity.value].reverse().find(
      (item): item is ToolActivity => item.type === 'tool' && item.status === 'running'
    )
    if (running) return `正在${running.displayName || running.toolName}…`
    if (receivingData.value) return '正在生成正文…'
    return '已连接，等待模型响应…'
  }
  // 没有终态事件就还没真正结束（例如请求根本没发出去）
  if (!finished.value) return '已连接，等待模型响应…'
  return summaryText.value
})

// ============== 时间线展示辅助 ==============
/** 字段名 → 中文标签（只做展示映射；表里没有的名字原样显示，不猜语义） */
const FIELD_LABELS: Record<string, string> = {
  title: '标题',
  summary: '摘要',
  content: '正文',
  contentHtml: '正文',
  categoryId: '分类',
  categoryName: '分类',
  suggestedCategoryName: '分类',
  tagIds: '标签',
  tagNames: '标签',
  suggestedTagNames: '标签',
}

/** 老后端没有 fields 数组时，按 payload 里真实出现的字段键推断（不补任何固定步骤） */
const FIELD_KEYS = [
  'title', 'summary', 'contentHtml', 'categoryId', 'categoryName',
  'tagIds', 'tagNames', 'suggestedCategoryName', 'suggestedTagNames',
] as const

const fieldLabel = (field: string): string => FIELD_LABELS[field] || field

/** 本次 field-update 真实写入的字段名：优先用后端给的 fields，缺失时按出现的键推断 */
const resolveWrittenFields = (payload: FieldUpdatePayload): string[] => {
  const explicit = payload.fields
  if (Array.isArray(explicit) && explicit.length) {
    return Array.from(new Set(explicit.filter(field => typeof field === 'string')))
  }
  const record = payload as Record<string, unknown>
  return FIELD_KEYS.filter(key => record[key] !== undefined && record[key] !== null)
}

/** 真实耗时格式化：不足 1 秒显示毫秒，否则保留一位小数的秒 */
const formatDuration = (ms?: number): string => {
  if (typeof ms !== 'number' || !Number.isFinite(ms)) return ''
  if (ms < 1000) return `${Math.round(ms)}ms`
  return `${(ms / 1000).toFixed(1)}s`
}

/** 真实耗时：优先后端上报的 durationMs，缺失时用本次条目记录的 startedAt 与 finishedAt 相减 */
const resolveDurationMs = (payload: ToolEventPayload, startedAt?: number): number | undefined => {
  // 优先用后端算好的耗时；为 0 时（旧后端曾因跨线程取不到开始时间恒为 0）用真实时间戳补算
  if (typeof payload.durationMs === 'number' && Number.isFinite(payload.durationMs) && payload.durationMs > 0) {
    return payload.durationMs
  }
  if (typeof startedAt === 'number' && typeof payload.finishedAt === 'number') {
    return Math.max(0, payload.finishedAt - startedAt)
  }
  return typeof payload.durationMs === 'number' && Number.isFinite(payload.durationMs) ? payload.durationMs : undefined
}

/** 行的状态类：驱动圆点与文字配色 */
const activityStatus = (item: ActivityItem): string => {
  if (item.type === 'tool') return item.status
  if (item.type === 'field' || item.type === 'articles') return 'success'
  if (item.type === 'error') return 'failed'
  return ''
}

/** 行主文案 */
const activityTitle = (item: ActivityItem): string => {
  switch (item.type) {
    case 'notice': return item.text
    case 'tool': return item.displayName || item.toolName
    case 'field': return `已写入：${Array.from(new Set(item.fields.map(fieldLabel))).join('、')}`
    case 'articles': return `找到 ${item.count} 篇相关文章`
    case 'error': return item.text
  }
}

/** 行副文案 1：工具的真实入参摘要（后端没给就不显示） */
const activityInput = (item: ActivityItem): string => (item.type === 'tool' ? item.inputSummary || '' : '')

/** 行副文案 2：工具的真实结果摘要或失败原因 */
const activityOutcome = (item: ActivityItem): string => {
  if (item.type !== 'tool') return ''
  if (item.status === 'failed') return item.errorMessage || '执行失败'
  return item.resultSummary || ''
}

/** 行右侧：真实耗时 / 执行状态；连接提示显示真实模型名 */
const activityMeta = (item: ActivityItem): string => {
  if (item.type === 'notice') return item.model || ''
  if (item.type !== 'tool') return ''
  if (item.status === 'running') {
    // 流已经结束却还停在"执行中"，说明结果事件没到，如实说明而不是假装成功
    return finished.value && !loading.value ? '未收到结果' : '执行中'
  }
  return formatDuration(item.durationMs) || (item.status === 'failed' ? '失败' : '完成')
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

/**
 * 输入框按键处理：Enter 发送，Shift+Enter 换行。
 *
 * 注意不要写成 `@keydown.enter.exact.prevent` + `@keydown.shift.enter` 两条监听：
 * `a-textarea` 是**组件**，Vue 会把同一事件的两个监听合成数组塞进 `onKeydown` prop，
 * 而组件期望的是函数 —— 运行时会报 prop 类型不匹配，Enter 发送也可能失效。
 * 合并成一个处理函数既避开这个问题，逻辑也更直观。
 */
const handleKeydown = (event: KeyboardEvent) => {
  if (event.key !== 'Enter') return
  // Shift/Ctrl/Alt/Meta + Enter 一律放行（换行或其它输入法行为）
  if (event.shiftKey || event.ctrlKey || event.altKey || event.metaKey) return
  event.preventDefault()
  void send()
}

const send = async (text?: string) => {
  const content = (text || prompt.value).trim()
  if (!content || loading.value) return
  loading.value = true
  answer.value = ''; showFullAnswer.value = false
  activity.value = []
  generatedChars.value = 0
  receivingData.value = false
  streamError.value = ''
  finished.value = false
  articles.value = []
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
        onStart: (payload) => {
          // start 事件带真实模型名；取不到就不显示，不编
          activity.value.push({
            id: nextActivityId(),
            type: 'notice',
            text: '已连接，等待模型响应',
            model: payload?.model || undefined,
          })
        },
        onData: (chunk) => {
          answer.value += chunk
          generatedChars.value += chunk.length
          if (chunk) receivingData.value = true
        },
        onToolStart: (payload) => {
          activity.value.push({
            id: nextActivityId(),
            type: 'tool',
            toolName: payload.toolName,
            displayName: payload.displayName,
            inputSummary: payload.inputSummary,
            status: 'running',
            startedAt: payload.startedAt,
          })
        },
        onToolResult: (payload) => {
          // 同一个工具可能被连续调用多次，从后往前找最近一条还在执行的同工具条目
          let index = -1
          for (let i = activity.value.length - 1; i >= 0; i -= 1) {
            const item = activity.value[i]
            if (item.type === 'tool' && item.toolName === payload.toolName && item.status === 'running') {
              index = i
              break
            }
          }
          const current = index >= 0 ? activity.value[index] as ToolActivity : undefined
          const next: ToolActivity = {
            id: current ? current.id : nextActivityId(),
            type: 'tool',
            toolName: payload.toolName || current?.toolName || '',
            displayName: payload.displayName || current?.displayName || '',
            // 结果事件里没有的字段沿用开始事件里的真实值，避免把入参摘要抹掉
            inputSummary: payload.inputSummary ?? current?.inputSummary,
            status: payload.success === false ? 'failed' : 'success',
            startedAt: current?.startedAt,
            durationMs: resolveDurationMs(payload, current?.startedAt),
            resultSummary: payload.resultSummary,
            errorMessage: payload.errorMessage,
          }
          if (current) activity.value[index] = next
          // 没收到过 tool-start 时也要如实展示结果，不静默丢弃
          else activity.value.push(next)
        },
        onArticles: (items) => {
          articles.value = items
          activity.value.push({ id: nextActivityId(), type: 'articles', count: items.length })
        },
        onFieldUpdate: (payload) => {
          emitScopedUpdate(payload)
          const fields = resolveWrittenFields(payload)
          if (fields.length) {
            activity.value.push({ id: nextActivityId(), type: 'field', fields })
          }
        },
        onError: (msg) => {
          message.error(msg)
          streamError.value = msg
          activity.value.push({ id: nextActivityId(), type: 'error', text: msg })
        },
        onComplete: () => {
          finished.value = true
        },
      },
    )
  } catch (error: any) {
    const text = error?.message || '写作助手请求失败'
    streamError.value = text
    activity.value.push({ id: nextActivityId(), type: 'error', text })
    if (!error?.isBusiness) message.error('Agent 请求失败')
  } finally {
    loading.value = false
    // 保存本轮对话上下文，支持多轮连续写作（"接着上一轮继续"）
    if (content && answer.value) {
      history.value.push({ role: 'user', content })
      history.value.push({ role: 'assistant', content: answer.value })
    }
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
      @keydown="handleKeydown"
    />
    <a-button type="primary" block class="send-button" :disabled="!canSend" :loading="loading" @click="send()">
      <template #icon><SendOutlined /></template>
      发送给 Agent
    </a-button>

    <div v-if="showProcessCard" class="agent-section process-card">
      <div class="process-title">
        <span>执行过程</span>
        <strong class="process-status" :class="{ 'is-error': !!streamError }">{{ statusLine }}</strong>
      </div>
      <div class="activity-list">
        <div
          v-for="item in activity"
          :key="item.id"
          class="trace-row activity-item"
          :class="activityStatus(item)"
        >
          <span class="trace-dot"></span>
          <div class="activity-main">
            <div class="activity-title">{{ activityTitle(item) }}</div>
            <div v-if="activityInput(item)" class="activity-sub">{{ activityInput(item) }}</div>
            <div v-if="activityOutcome(item)" class="activity-sub">{{ activityOutcome(item) }}</div>
          </div>
          <em class="activity-meta">{{ activityMeta(item) }}</em>
        </div>
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
  flex-wrap: wrap;
  font-size: 13px;
  font-weight: 600;
  margin-bottom: 8px;
}

/* 状态行：替代原来的假百分比，文案可换行（结束后的汇总比运行中的动作长） */
.process-title strong.process-status {
  max-width: 100%;
  margin-left: auto;
  text-align: right;
  white-space: normal;
  line-height: 1.5;
  color: var(--lt-color-primary);
  font-size: 12px;
}

.process-status.is-error {
  color: var(--lt-color-error);
}

/* 真实活动时间线：每一行都来自后端 SSE 事件 */
.activity-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.activity-item {
  align-items: start;
}

.activity-item .trace-dot {
  margin-top: 6px;
}

.activity-main {
  min-width: 0;
}

.activity-title {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--lt-color-text-secondary);
}

.activity-sub {
  margin-top: 2px;
  font-size: 11px;
  line-height: 1.5;
  color: var(--lt-color-text-tertiary);
  white-space: normal;
  word-break: break-word;
}

.activity-meta {
  white-space: nowrap;
  color: var(--lt-color-text-tertiary);
}

.activity-item.running .activity-meta {
  color: var(--lt-color-primary);
}

.activity-item.success .activity-meta {
  color: var(--lt-color-success);
}

.activity-item.failed .activity-title,
.activity-item.failed .activity-sub,
.activity-item.failed .activity-meta {
  color: var(--lt-color-error);
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
