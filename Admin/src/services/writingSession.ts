import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { openWritingPreviewLink, sanitizeWritingPreview, useWritingReview } from './writingReview'
import type { WritingDraftSnapshot } from './writingReview'
import type { WritingActivityPayload, WritingActivityStage, WritingArticleItem, WritingFieldUpdatePayload, WritingStreamHandlers, WritingToolEventPayload } from './writingStream'

export type WritingLocalActivity = Omit<WritingActivityPayload, 'stage'> & {
  stage: WritingActivityStage | 'creating_category' | 'creating_tag' | 'refreshing_taxonomy'
}
export interface WritingFieldScope { fields: Array<'title' | 'summary' | 'content' | 'category' | 'tags' | 'check'>; appendTags: boolean }
export interface WritingHistoryMessage { role: 'user' | 'assistant'; content: string }
export interface WritingSessionRequest {
  message: string
  draft: WritingDraftSnapshot
  tempMessages: WritingHistoryMessage[]
  context: Record<string, unknown>
}
interface WritingActivityItem extends WritingLocalActivity { model?: string; details?: string }

const FIELD_LABELS: Record<string, string> = {
  title: '标题', summary: '摘要', content: '正文', contentHtml: '正文', contentPatch: '正文',
  categoryId: '分类', categoryName: '分类', suggestedCategoryName: '分类',
  tagIds: '标签', tagNames: '标签', suggestedTagNames: '标签'
}
const fieldLabel = (field: string) => FIELD_LABELS[field] || field
const formatDuration = (value?: number) => value === undefined ? '' : value < 1000 ? `${Math.round(value)}ms` : `${(value / 1000).toFixed(1)}s`

export function inferWritingFieldScope(message: string): WritingFieldScope {
  const text = message.toLowerCase()
  const has = (...words: string[]) => words.some(word => text.includes(word.toLowerCase()))
  const forbidChanges = /(?:不要|别|无需|不需要|不)(?:进行)?(?:修改|更改|改动|改写|重写)(?!标题|摘要|分类|标签)/.test(text)
    || /不改(?!标题|摘要|分类|标签)|仅检查|只检查|只提出(?:建议|问题|检查结果)/.test(text)
  const checkOnly = forbidChanges || (has('检查', '审查', '校对', '审阅', '审核', '看看有没有问题')
    && !has('修复', '修改', '纠正', '纠错', '改正', '改成', '改写', '重写', '写入', '应用', '替换', '润色', '补充', '添加', '生成'))
  if (checkOnly) return { fields: ['check'], appendTags: false }
  const fields: WritingFieldScope['fields'] = []
  if (has('标题', '题目', 'seo')) fields.push('title')
  if (has('摘要', '简介', '概述', 'seo', '描述')) fields.push('summary')
  if (has('正文', '富文本', 'html', '排版', '格式', '润色', '续写', '扩写', '改写', '章节', '代码', '段落', '纠错', '错字', '错别字', '语病', '语法', '纠正', '改正')) fields.push('content')
  if (has('分类', '栏目')) fields.push('category')
  if (has('标签', 'tag')) fields.push('tags')
  // 没有明确字段的修复默认只动正文；“全文”指正文范围，不等于允许改元信息。
  if (!fields.length && has('修改', '修复', '更改', '改一下', '重写')) fields.push('content')
  const fullArticle = has('写一篇', '生成一篇', '新文章', '完整文章', '全部字段', '应用全部')
  let requested: WritingFieldScope['fields'] = fullArticle ? ['title', 'summary', 'content', 'category', 'tags'] : fields.length ? fields : ['content']
  const forbiddenClause = Array.from(text.matchAll(/(?:不要|别|无需|不需要)(?:修改|更改|改动|改写|动)?([^，。；;\n]{1,24})/g)).map(match => match[1]).join(' ')
  const names = { title: '标题|题目', summary: '摘要|简介', category: '分类|栏目', tags: '标签|tag' }
  requested = requested.filter(field => {
    if (field === 'content' || field === 'check') return true
    return !new RegExp(names[field]).test(forbiddenClause) && !new RegExp(`(?:${names[field]})(?:不改|不变|保持原样|保持不变)`).test(text)
  })
  return { fields: requested.length ? requested : ['check'], appendTags: requested.includes('tags') && has('加', '增加', '添加', '补', '补充', '追加', '再来') }
}

export function filterWritingFieldUpdate(payload: WritingFieldUpdatePayload, scope: WritingFieldScope, original: WritingDraftSnapshot): WritingFieldUpdatePayload {
  if (scope.fields.includes('check')) return {}
  const next: WritingFieldUpdatePayload = {}
  if (scope.fields.includes('title')) next.title = payload.title
  if (scope.fields.includes('summary')) next.summary = payload.summary
  if (scope.fields.includes('content')) { next.contentHtml = payload.contentHtml; next.contentPatch = payload.contentPatch }
  if (scope.fields.includes('category')) {
    next.categoryId = payload.categoryId; next.categoryName = payload.categoryName; next.suggestedCategoryName = payload.suggestedCategoryName
  }
  if (scope.fields.includes('tags')) {
    next.tagIds = payload.tagIds && scope.appendTags ? Array.from(new Set([...(original.tagIds || []), ...payload.tagIds])) : payload.tagIds
    next.tagNames = payload.tagNames; next.suggestedTagNames = payload.suggestedTagNames
  }
  return Object.fromEntries(Object.entries(next).filter(([, value]) => value !== undefined && value !== null))
}

export function appendWritingHistory(history: WritingHistoryMessage[], message: string, answer: string, proposal: WritingFieldUpdatePayload): WritingHistoryMessage[] {
  const fields = Array.from(new Set(Object.keys(proposal).filter(key => key !== 'fields').map(fieldLabel)))
  const edits = proposal.contentPatch?.edits.length
  // 整文HTML或补丁原文不加入下一轮历史；下一轮会提供最新完整草稿。
  const conclusion = fields.length ? `本轮建议修改${fields.join('、')}${edits ? `，局部修改 ${edits} 处` : ''}；以编辑器当前草稿为准，待用户确认采纳。`
    : answer.replace(/<[^>]*>/g, '').trim().slice(0, 1200) || '本轮回复完成，没有文章字段修改。'
  return [...history.slice(-12), { role: 'user' as const, content: message.slice(0, 1200) }, { role: 'assistant' as const, content: conclusion }].slice(-14)
}

/** 两端共用请求生命周期、状态展示和局部修改预览，页面只负责各自的样式和真实CRUD。 */
export function useWritingSession<T extends WritingDraftSnapshot>(options: {
  getDraft: () => T
  getLocalActivities?: () => WritingLocalActivity[] | undefined
  onApply: (update: WritingFieldUpdatePayload, original: T) => void
  stream: (request: WritingSessionRequest, handlers: WritingStreamHandlers, signal: AbortSignal) => Promise<void>
  source: string
}) {
  const prompt = ref('')
  const loading = ref(false)
  const answer = ref('')
  const showFullAnswer = ref(false)
  const history = ref<WritingHistoryMessage[]>([])
  const activity = ref<WritingActivityItem[]>([])
  const generatedChars = ref(0)
  const streamError = ref('')
  const finished = ref(false)
  const articles = ref<WritingArticleItem[]>([])
  const articleResultReason = ref('')
  const contentMode = ref<'patch' | 'replace'>(options.getDraft().content?.trim() ? 'patch' : 'replace')
  const review = useWritingReview(options.getDraft, options.onApply)
  let controller: AbortController | null = null
  let serial = 0
  const lastLocalError = ref('')
  let unified = false

  const upsertActivity = (payload: WritingLocalActivity) => {
    const index = activity.value.findIndex(item => item.activityId === payload.activityId)
    if (index < 0) activity.value.push({ ...payload })
    else activity.value[index] = { ...activity.value[index], ...payload }
  }
  const closeRunning = (status: 'cancelled' | 'failed' | 'completed') => {
    activity.value = activity.value.map(item => item.status === 'running' && !item.activityId.startsWith('local:') ? { ...item, status } : item)
  }
  if (options.getLocalActivities) watch(options.getLocalActivities, items => {
    const currentIds = new Set((items || []).map(item => item.activityId))
    activity.value = activity.value.map(item => item.activityId.startsWith('local:') && item.status === 'running' && !currentIds.has(item.activityId)
      ? { ...item, status: 'cancelled', message: '创建结果不再写入当前编辑器' } : item)
    for (const item of items || []) upsertActivity(item)
    const latest = items && items[items.length - 1]
    lastLocalError.value = latest?.status === 'failed' ? latest.message : ''
  }, { deep: true, immediate: true })

  const stop = () => {
    controller?.abort()
    controller = null
    loading.value = false
    review.fail()
    streamError.value = '已停止生成，原稿保持不变'
    closeRunning('cancelled')
  }
  const reset = () => {
    controller?.abort(); controller = null; loading.value = false
    review.reset(); history.value = []; answer.value = ''; activity.value = []
    articles.value = []; articleResultReason.value = ''; streamError.value = ''; finished.value = false
    lastLocalError.value = ''
    contentMode.value = options.getDraft().content?.trim() ? 'patch' : 'replace'
  }
  watch(() => options.getDraft().postId ?? null, reset, { flush: 'sync' })
  watch(() => !!options.getDraft().content?.trim(), (hasContent, previouslyHadContent) => {
    if (hasContent && !previouslyHadContent) contentMode.value = 'patch'
    if (!hasContent) contentMode.value = 'replace'
  })
  onBeforeUnmount(() => { controller?.abort(); controller = null })

  const send = async (text?: string, mode?: 'patch' | 'replace') => {
    const message = (text || prompt.value).trim()
    if (!message || loading.value) return
    const active = new AbortController()
    controller = active
    loading.value = true
    const original = review.begin()
    const scope = inferWritingFieldScope(message)
    if (mode) contentMode.value = mode
    else if (/写一篇|生成一篇|新文章|完整文章|全文重写|整篇重写/.test(message)) contentMode.value = 'replace'
    unified = false
    const requestedContentMode = contentMode.value
    answer.value = ''; showFullAnswer.value = false; generatedChars.value = 0
    activity.value = (options.getLocalActivities?.() || []).map(item => ({ ...item }))
    streamError.value = ''; finished.value = false; articles.value = []; articleResultReason.value = ''; prompt.value = ''
    lastLocalError.value = ''
    const current = () => controller === active && !active.signal.aborted
    const fail = (message: string) => {
      if (!current()) return
      review.fail(); streamError.value = message; closeRunning('failed')
    }
    const legacyStart = (payload: WritingToolEventPayload) => upsertActivity({
      activityId: `legacy-tool:${++serial}`, stage: 'updating_fields', status: 'running', message: payload.displayName || payload.toolName,
      toolName: payload.toolName, startedAt: payload.startedAt
    })
    try {
      await options.stream({ message, draft: original, tempMessages: history.value.slice(-14), context: {
        page: 'admin-post-editor', source: options.source, postId: original.postId,
        requestedFields: scope.fields, appendTags: scope.appendTags, contentMode: requestedContentMode
      } }, {
        onStart: payload => {
          if (!current()) return
          review.setRevision(payload.baseRevision)
          unified = !!payload.requestId
          activity.value.push({ activityId: `connection:${++serial}`, stage: 'reading_draft', status: 'completed', message: '已连接写作助手', model: payload.model })
        },
        onActivity: payload => { if (current()) upsertActivity(payload) },
        onData: chunk => { if (current()) { answer.value += chunk; generatedChars.value += chunk.length } },
        onToolStart: payload => { if (current()) legacyStart(payload) },
        onToolResult: payload => {
          if (!current()) return
          const existing = [...activity.value].reverse().find(item => item.toolName === payload.toolName && item.status === 'running')
          upsertActivity({ activityId: existing?.activityId || `legacy-tool:${++serial}`, stage: existing?.stage || 'updating_fields',
            status: payload.success === false ? 'failed' : 'completed', message: payload.displayName || payload.toolName,
            toolName: payload.toolName, startedAt: existing?.startedAt, finishedAt: payload.finishedAt, durationMs: payload.durationMs })
        },
        onFieldUpdate: payload => {
          if (!current()) return
          try {
            if (unified && requestedContentMode === 'patch' && payload.contentHtml !== undefined) throw new Error('局部修改未返回段落补丁，请重新生成')
            review.stage(filterWritingFieldUpdate(payload, scope, original))
          }
          catch (error) { fail(error instanceof Error ? error.message : '正文修改建议无效'); throw error }
        },
        onArticles: (items, payload) => { if (current()) { articles.value = items; articleResultReason.value = payload.reason || '关联文章' } },
        onComplete: () => { if (current()) { review.complete(); finished.value = true; if (!unified) closeRunning('cancelled') } },
        onError: fail
      }, active.signal)
    } catch (error) {
      if (current()) fail(error instanceof Error ? error.message : '写作助手请求失败')
    } finally {
      if (current()) {
        if (review.succeeded.value) history.value = appendWritingHistory(history.value, message, answer.value, review.pending.value)
        controller = null; loading.value = false
      }
    }
  }
  const handleKeydown = (event: KeyboardEvent) => {
    if (event.key !== 'Enter' || event.isComposing || event.shiftKey || event.ctrlKey || event.altKey || event.metaKey) return
    event.preventDefault(); void send()
  }
  const displayAnswer = computed(() => {
    if (review.pending.value.contentPatch) return review.applied.value ? '本轮局部修改已应用，可在编辑器检查或撤销。' : `已准备 ${review.pending.value.contentPatch.edits.length} 处局部修改，请检查对照后应用。`
    if (review.pending.value.contentHtml) return review.applied.value ? '本轮修改已应用，可在编辑器检查或撤销。' : '完整正文已生成，请检查预览后应用。'
    return answer.value
  })
  const canExpand = computed(() => displayAnswer.value.length > 80)
  const previewText = computed(() => showFullAnswer.value || !canExpand.value ? displayAnswer.value : `${displayAnswer.value.slice(0, 80)}...`)
  const patchPreviews = computed(() => review.pending.value.contentPatch?.edits.map((edit, index) => ({
    id: index, before: sanitizeWritingPreview(edit.before), after: sanitizeWritingPreview(edit.after)
  })) || [])
  const previewHtml = computed(() => review.pending.value.contentPatch ? '' : sanitizeWritingPreview(review.pending.value.contentHtml || ''))
  const statusLine = computed(() => {
    const localRunning = [...activity.value].reverse().find(item => item.activityId.startsWith('local:') && item.status === 'running')
    if (localRunning) return localRunning.message
    if (streamError.value) return streamError.value
    if (loading.value) return [...activity.value].reverse().find(item => item.status === 'running')?.message || '正在等待模型响应…'
    if (lastLocalError.value) return lastLocalError.value
    if (!finished.value) return activity.value.length ? '等待写作请求' : ''
    if (review.applied.value) return '本轮修改已应用'
    return review.hasChanges.value ? `建议已就绪，待采纳${patchPreviews.value.length ? ` · ${patchPreviews.value.length} 处局部修改` : ''}` : '本轮回复完成，原稿未修改'
  })
  const activityStatus = (item: WritingActivityItem) => ({ completed: 'success', cancelled: 'cancelled', failed: 'failed', running: 'running' })[item.status]
  const activityMeta = (item: WritingActivityItem) => item.model || formatDuration(item.durationMs) || ({ running: '进行中', completed: '完成', failed: '失败', cancelled: '已取消' })[item.status]
  const apply = () => {
    try { review.apply(); if (review.applied.value) contentMode.value = review.proposedContent.value?.trim() || options.getDraft().content?.trim() ? 'patch' : 'replace' }
    catch (error) { review.fail(); streamError.value = error instanceof Error ? error.message : '无法应用本轮修改' }
  }
  return { prompt, loading, answer, showFullAnswer, history, activity, generatedChars, streamError, finished, articles, articleResultReason,
    contentMode, review, pendingUpdate: review.pending, draftConflict: review.conflict, canApply: review.canApply, hasChanges: review.hasChanges,
    missingMedia: review.missingMedia, applied: review.applied, previewHtml, patchPreviews, displayAnswer, canExpand, previewText,
    statusLine, showProcessCard: computed(() => loading.value || activity.value.length > 0 || !!streamError.value), canSend: computed(() => !!prompt.value.trim() && !loading.value),
    activityStatus, activityMeta, activityTitle: (item: WritingActivityItem) => item.message,
    activityInput: (_item: WritingActivityItem) => '', activityOutcome: (_item: WritingActivityItem) => '',
    stop, send, handleKeydown, apply, reset, openWritingPreviewLink }
}

export const writingQuickPrompts: Array<{ label: string; message: string; mode?: 'patch' | 'replace' }> = [
  { label: '局部纠错', mode: 'patch', message: '只修复正文中有问题的段落、错别字和语病，其他段落与媒体保持原样' },
  { label: '润色正文', mode: 'patch', message: '局部润色当前正文，保持原意和结构，只修改需要改善的段落' },
  { label: '补摘要', message: '根据正文生成一段 80-150 字的摘要' },
  { label: '改标题', message: '根据正文内容生成 3 个备选标题，选最合适的一个作为标题建议' },
  { label: '选分类标签', message: '为当前文章挑选最合适的分类和 3-5 个标签；新分类或标签先提出建议，等待确认创建' },
  { label: '续写下一节', mode: 'patch', message: '基于当前正文的最后部分，局部追加下一节内容，保留已有正文' },
  { label: '发布前检查', message: '检查正文是否存在明显问题：错别字、未闭合标签、过长段落、缺失摘要，只提出检查结果' },
  { label: '写完整文章', mode: 'replace', message: '根据当前主题写一篇完整文章，生成 HTML 正文并提出标题、摘要、分类、标签建议' }
]
