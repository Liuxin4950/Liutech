<script setup lang="ts">
import { computed, onActivated, onBeforeUnmount, onDeactivated, onMounted, ref, toRaw, watch } from 'vue'
import { message } from 'ant-design-vue'
import { PlusOutlined, ReloadOutlined, UploadOutlined } from '@ant-design/icons-vue'
import { ImageUploadService, pickUploadFile } from '@/services/upload'
import { communityService, defaultCommunitySettings } from '@/services/community'
import type { CommunityBot, CommunityBotInput, CommunityKnowledge, CommunityRun, CommunityTask, CommunityMemory, CommunityComment, CommunityCommentThread, CommunityBackfillResult, CommunityEvent, CommunityWorkerStatus } from '@/services/community'
import PostsService, { type PostListItem } from '@/services/posts'
import CommentsService, { type Comment } from '@/services/comments'
import { formatDateTime } from '@/utils/utils'
import { queueCountdown, workerObservation } from '@/utils/communityProgress'

const bots = ref<CommunityBot[]>([])
const settings = ref(defaultCommunitySettings())
const communityEnabled = ref(false)
const savedSettings = ref(defaultCommunitySettings())
const loading = ref(false)
const configurationLoaded = ref(false)
const loadError = ref('')
const busy = ref(false)
const selectedBotId = ref<number>()
const activeTab = ref('roles')
const knowledge = ref<CommunityKnowledge[]>([])
const runs = ref<CommunityRun[]>([])
const tasks = ref<CommunityTask[]>([])
const events = ref<CommunityEvent[]>([])
const postEvents = ref<CommunityEvent[]>([])
const cancellingIds = ref<string[]>([])
const withdrawingIds = ref<number[]>([])
const worker = ref<CommunityWorkerStatus>()
const workerLoading = ref(false)
const workerError = ref('')
const workerReceivedAt = ref(0)
const displayNow = ref(Date.now())
let workerRequest = 0
let workerTimer: ReturnType<typeof setTimeout> | undefined
let displayTimer: ReturnType<typeof setInterval> | undefined
const queueSamples = new WeakMap<object, number>()
const recordsLoading = ref(false)
const recordsLoaded = ref(false)
const recordsError = ref('')
const recordsScopeBotId = ref<number>()
const recordsLoadedAt = ref('')
const taskFilter = ref<'all' | 'pending' | 'failed' | 'cancelled'>('pending')
let recordsRequest = 0
let recordsTimer: ReturnType<typeof setTimeout> | undefined
let pageActive = true
const compactLayout = ref(false)
let layoutQuery: MediaQueryList | undefined
const layoutChanged = (event: MediaQueryListEvent) => { compactLayout.value = event.matches }
const memories = ref<CommunityMemory[]>([])
const comments = ref<CommunityComment[]>([])
const commentsPage = ref(1)
const commentsSize = ref(20)
const commentsTotal = ref(0)
let commentsRequest = 0
const threadOpen = ref(false)
const threadLoading = ref(false)
const thread = ref<CommunityCommentThread>()
const threadError = ref('')
const threadBotId = ref<number>()
const threadCommentId = ref<number>()
let threadRequest = 0
const detail = ref<CommunityRun>()
const detailOpen = ref(false)
const taskDetail = ref<CommunityTask>()
const taskDetailOpen = ref(false)
const detailMode = ref<'preview' | 'run'>('run')
const previewLoading = ref(false)
const previewError = ref('')
let previewRequest = 0
let previewController: AbortController | undefined
const botModal = ref(false)
const editingBotId = ref<number>()
const emptyBot = (): CommunityBotInput => ({ name: '', avatarUrl: '', personality: '', background: '', systemPrompt: '', interests: '', participation: 50, enabled: false })
const botForm = ref(emptyBot())
const botAvatarUploading = ref(false)
const knowledgeModal = ref(false)
const editingKnowledgeId = ref<number>()
const knowledgeForm = ref({ title: '', content: '' })
const fileInput = ref<HTMLInputElement>()
const postId = ref<number>()
const postEnabled = ref<boolean>()
const postStateLoading = ref(false)
const postStateError = ref('')
const postChoices = ref<PostListItem[]>([])
const postTitles = ref<Record<number, string>>({})
const selectedPost = ref<PostListItem>()
const postSearch = ref('')
const postPage = ref(0)
const postTotal = ref(0)
const postLoading = ref(false)
const postSearchError = ref('')
let postSearchRequest = 0
let postSearchTimer: ReturnType<typeof setTimeout> | undefined
const postComments = ref<Comment[]>([])
const postCommentsPage = ref(0)
const postCommentsTotal = ref(0)
const postCommentsLoading = ref(false)
const postCommentsError = ref('')
let postCommentsRequest = 0
const commentId = ref<number>()
const inviteIds = ref<number[]>([])
const inviteLoading = ref(false)
const inviteNotice = ref('')
const inviteError = ref('')
const postRuns = ref<CommunityRun[]>([])
const postTasks = ref<CommunityTask[]>([])
const postActivityLoading = ref(false)
const postActivityError = ref('')
const retryingTaskIds = ref<string[]>([])
const retryNotices = ref<Record<string, { queued: boolean; message: string; previousError?: string }>>({})
let postActivityRequest = 0
let postActivityTimer: ReturnType<typeof setTimeout> | undefined
let postStateRequest = 0
const enableScope = ref<'new' | 'recent'>()
const backfillResult = ref<CommunityBackfillResult>()
const backfillError = ref('')
const botOptions = computed(() => bots.value.map(bot => ({ value: bot.id, label: bot.name })))
const enabledBotOptions = computed(() => bots.value.filter(bot => bot.enabled).map(bot => ({ value: bot.id, label: bot.name, disabled: inviteIds.value.length >= 2 && !inviteIds.value.includes(bot.id) })))
const selectedBot = computed(() => bots.value.find(bot => bot.id === selectedBotId.value))
const postOptions = computed(() => {
  const records = selectedPost.value && !postChoices.value.some(post => post.id === selectedPost.value?.id)
    ? [selectedPost.value, ...postChoices.value] : postChoices.value
  return records.map(post => ({ value: post.id, label: `${post.title} · ${formatDateTime(post.createdAt)}` }))
})
const postCommentOptions = computed(() => postComments.value.filter(comment => comment.id && !comment.deletedAt).map(comment => ({
  value: comment.id!, label: `${comment.authorType === 'BOT' ? comment.bot?.name || botName(comment.botId!) : comment.user?.username || '已删除用户'}：${comment.content.replace(/\s+/g, ' ').slice(0, 90)}`,
})))
const enablingCommunity = computed(() => settings.value.enabled && !communityEnabled.value)
const autoBots = computed(() => bots.value.filter(bot => bot.enabled && bot.participation > 0))
const pendingStatuses = new Set(['PENDING', 'RUNNING', 'RETRY', 'READY', 'DECIDED', 'GENERATED'])
const phaseLabels: Record<string, string> = {
  CLAIMING_EVENTS: '接取待派发事件', INGESTING_EVENTS: '保存接取任务', ACQUIRING_LEASE: '获取执行权限', CLAIMING_TASK: '领取待执行任务',
  CHECKING_SOURCE: '检查来源和取消状态', PREPARING_CONTEXT: '准备文章与角色资料', MODEL_GENERATION: '模型生成中',
  VALIDATING_RESULT: '校验生成结果', PERSISTING_RUN: '保存执行记录', PERSISTING_DECISION: '保存生成结果', PUBLISHING: '提交评论', POSTPROCESSING: '保存执行记录与记忆',
  FINISHING: '完成任务', IDLE: '等待下一轮接取',
}
const workerStateLabels: Record<string, string> = {
  STARTING: '执行器启动中', IDLE: '空闲，正常轮询', POLLING: '正在接取任务', RUNNING: '正在执行任务', CANCELLING: '正在结束已取消任务',
  WAITING_LEASE: '等待其他执行器释放租约', WAITING_TASK: '等待任务到期', ERROR: '执行器遇到错误', STOPPED: '执行器已停止', STALE: '执行进展需要核对',
}
const workerObserved = computed(() => workerObservation(worker.value, workerReceivedAt.value, displayNow.value, workerError.value))
const workerFresh = computed(() => workerObserved.value.fresh)
const workerOnline = computed(() => workerObserved.value.online)
const workerTitle = computed(() => workerError.value ? '无法读取 AI 执行器' : !worker.value ? '执行器状态尚未读取'
  : !workerFresh.value ? '执行器状态需要刷新' : workerStateLabels[worker.value.state] || worker.value.state)
const phaseLabel = computed(() => phaseLabels[worker.value?.phase || ''] || worker.value?.phase || '—')
const phaseElapsedSeconds = computed(() => workerObserved.value.phaseElapsedSeconds)
const heartbeatAgeSeconds = computed(() => workerObserved.value.heartbeatAgeSeconds)
const currentWorkerTask = computed(() => [...tasks.value, ...postTasks.value].find(task => String(task.id) === String(worker.value?.currentTaskId)))
const workerTone = computed(() => workerFresh.value && worker.value?.state === 'STARTING' ? 'processing' : !workerOnline.value ? 'error' : worker.value?.busy ? 'processing' : 'success')
const workerWarning = computed(() => workerError.value || (!worker.value ? '' : !workerFresh.value ? '状态已经超过刷新间隔，当前执行进度无法确认。'
  : worker.value.state === 'STARTING' ? ''
  : !worker.value.schedulerAlive ? '执行器没有正常轮询心跳，服务健康不代表后台任务正在执行。'
  : worker.value.state === 'STALE' ? worker.value.blockReason || '当前执行阶段的进展长时间未更新，需要检查。'
  : worker.value.state === 'ERROR' ? worker.value.lastError || worker.value.blockReason || '请查看执行器诊断信息。'
  : worker.value.busy && worker.value.phaseTimeoutMs && phaseElapsedSeconds.value * 1000 > worker.value.phaseTimeoutMs
    ? '当前阶段已超过服务端设置的等待时间，请查看执行器错误或取消任务；不会自动新建重复任务。' : ''))
function durationText(seconds: number) {
  const value = Math.max(0, Math.floor(seconds))
  return value >= 3600 ? `${Math.floor(value / 3600)} 小时 ${Math.floor(value % 3600 / 60)} 分`
    : value >= 60 ? `${Math.floor(value / 60)} 分 ${value % 60} 秒` : `${value} 秒`
}
function sampledSeconds(record: CommunityEvent | CommunityTask, key: 'dueSeconds' | 'leaseRemainingSeconds' = 'dueSeconds') {
  const seconds = record[key]
  if (seconds === undefined || seconds === null) return undefined
  return queueCountdown(seconds, queueSamples.get(toRaw(record)) ?? displayNow.value, displayNow.value)
}
function plannedAt(record: CommunityEvent | CommunityTask) {
  return record.availableAtEpochMs ? formatDateTime(new Date(record.availableAtEpochMs).toISOString()) : '计划时间尚未确认'
}
function eventProgress(event: CommunityEvent) {
  if (event.status === 'DISPATCHING') return `执行器正在接取${event.leaseRemainingSeconds === undefined ? '' : `，交接租约剩余 ${durationText(sampledSeconds(event, 'leaseRemainingSeconds') || 0)}`}`
  const remaining = sampledSeconds(event)
  if (remaining === undefined) return '等待接取；到期时间尚未确认'
  if (remaining > 0) return `等待到期，约 ${durationText(remaining)} 后可接取`
  if (remaining === 0) return '正在检查是否到期，等待下一轮接取'
  if (worker.value?.state === 'STARTING') return '已到期，等待执行器完成启动后接取'
  return `已到期 ${durationText(-remaining)}，${workerOnline.value ? worker.value?.busy ? '等待当前任务处理完成' : '等待执行器接取' : '执行器状态异常，请检查连接与心跳'}`
}
function executingTask(task: CommunityTask) { return workerFresh.value && worker.value?.busy && String(task.id) === String(worker.value.currentTaskId) }
function taskProgress(task: CommunityTask) {
  if (executingTask(task)) return task.status === 'CANCELLED' ? '等待已发出的请求结束，结果不会发表' : `${phaseLabel.value} · 已耗时 ${durationText(phaseElapsedSeconds.value)}`
  if (!pendingStatuses.has(task.status)) return ''
  const remaining = sampledSeconds(task)
  if (remaining !== undefined && remaining > 0) return `${automaticRetry(task) ? '等待重试' : '计划等待'} ${durationText(remaining)}`
  const lease = sampledSeconds(task, 'leaseRemainingSeconds')
  if (lease != null && lease > 0) return `任务已被领取，租约剩余 ${durationText(lease)}`
  if (['RUNNING', 'DECIDED'].includes(task.status)) return '租约已到期，等待执行器重新接取'
  if (worker.value?.state === 'STARTING') return '等待执行器完成启动后接取'
  if (!workerOnline.value) return '尚未接取，执行器状态异常'
  return worker.value?.busy ? '已到期，等待当前任务处理完成' : '已到期，等待下一轮接取'
}
const pendingTasks = computed(() => tasks.value.filter(task => pendingStatuses.has(task.status)))
const failedTasks = computed(() => tasks.value.filter(task => task.status === 'FAILED'))
const visibleTasks = computed(() => taskFilter.value === 'pending' ? pendingTasks.value : taskFilter.value === 'failed' ? failedTasks.value : taskFilter.value === 'cancelled' ? tasks.value.filter(task => task.status === 'CANCELLED') : tasks.value)
const recordsScope = computed(() => `${recordsScopeBotId.value ? botName(recordsScopeBotId.value) : '所有角色'} · 所有待处理与失败任务、最近 100 条运行与其它终结任务`)
const usageSummary = computed(() => {
  let knownTokens = 0
  let missing = 0
  let partial = 0
  let notCalled = 0
  let legacy = 0
  let legacyKnownTokens = 0
  for (const run of runs.value) {
    if (run.modelRounds === 0) { ++notCalled; continue }
    if (run.tokenUsageAvailable === undefined && ((run.inputTokens ?? 0) > 0 || (run.outputTokens ?? 0) > 0)) {
      ++legacy
      legacyKnownTokens += (run.inputTokens ?? 0) + (run.outputTokens ?? 0)
      continue
    }
    if (run.tokenUsageAvailable !== true) { ++missing; continue }
    knownTokens += (run.inputTokens ?? 0) + (run.outputTokens ?? 0)
    if (run.tokenUsageComplete !== true || run.inputTokens === undefined || run.outputTokens === undefined) ++partial
  }
  return { knownTokens, missing, partial, notCalled, legacy, legacyKnownTokens }
})
const decisionDistribution = computed(() => {
  const counts = { COMMENT: 0, REPLY: 0, SKIP: 0, FAILED: 0 }
  for (const run of runs.value) {
    if (run.preview || run.status === 'PREVIEW') continue
    if (run.error || run.status === 'FAILED') ++counts.FAILED
    else if (run.decision && run.decision in counts) ++counts[run.decision]
  }
  return [
    { key: 'COMMENT', title: '评价文章', count: counts.COMMENT, color: '#1677ff' },
    { key: 'REPLY', title: '回复评论', count: counts.REPLY, color: '#722ed1' },
    { key: 'SKIP', title: '保持沉默', count: counts.SKIP, color: '#8c8c8c' },
    { key: 'FAILED', title: '历史失败执行', count: counts.FAILED, color: '#ff4d4f' },
  ]
})
const decisionTotal = computed(() => decisionDistribution.value.reduce((sum, entry) => sum + entry.count, 0))
const participationNotice = computed(() => {
  if (!communityEnabled.value) return '全站互动已暂停。预演仍可用；待处理任务请到任务队列取消或检查状态。'
  if (!autoBots.value.length) return '没有可自动参与的角色：需要启用角色并将参与积极度设为大于 0。'
  if (!savedSettings.value.siteDailyTaskLimit || !savedSettings.value.botDailyTaskLimit) return '模型任务额度设置为 0：自动生成已停止，请检查参与规则。'
  if (!savedSettings.value.siteDailyCommentLimit || !savedSettings.value.botDailyCommentLimit || !savedSettings.value.postDailyCommentLimit) return '评论额度设置为 0：无法正式发表，请检查参与规则。'
  return ''
})
const statusLabels: Record<string, string> = {
  SUCCEEDED: '已发言', SKIPPED: '已结束，未发表', FAILED: '失败', PREVIEW: '预演', PENDING: '等待中',
  RUNNING: '处理中', CANCELLED: '已取消', COMPLETED: '完成', DONE: '完成', RETRY: '等待重试',
  SKIP: '沉默', COMMENT: '评论文章', REPLY: '回复评论', READY: '等待中', DECIDED: '等待发布', GENERATED: '已生成',
  RETRY_SCHEDULED: '已安排自动重试', STATE_CHANGED: '状态已变化',
}
const sourceLabels: Record<string, string> = { article: '文章正文', comment: '目标评论', comments: '相关评论', knowledge: '角色资料', 'knowledge-index': '资料索引', memory: '互动记忆' }
const sourceName = (source: string) => sourceLabels[source] || source
const toolInput = (input: unknown) => typeof input === 'string' ? input : JSON.stringify(input, null, 2)
const label = (value?: string) => value ? statusLabels[value] || value : '—'
function currentTask(run: CommunityRun) {
  if (run.preview || run.status === 'PREVIEW') return undefined
  const records = activeTab.value === 'preview' ? [...postTasks.value, ...tasks.value] : [...tasks.value, ...postTasks.value]
  return records.find(task => String(task.id) === String(run.taskId))
}
function taskStatus(task: CommunityTask) {
  const status = observedTaskStatus(task)
  if (status === 'FAILED') return task.publishedCommentId ? '已发表，后处理失败' : '已停止自动重试'
  if (automaticRetry(task)) return task.publishedCommentId ? '已发表，自动恢复中' : '等待自动重试'
  if (task.publishedCommentId && pendingStatuses.has(status)) return '已发表，处理中'
  return label(status)
}
function observedTaskStatus(task: CommunityTask) { return executingTask(task) ? worker.value?.currentTaskStatus || task.status : task.status }
function automaticRetry(task: CommunityTask) {
  const status = observedTaskStatus(task)
  return status === 'RETRY' || (status === 'READY' && (task.failures ?? 0) > 0)
}
function failureCount(task: CommunityTask) {
  if (task.status === 'FAILED' && (task.failures === undefined || task.failures < 3)) {
    return `${task.failures === undefined ? '次数未记录' : `已记录 ${task.failures} 次`}（已停止，旧计数）`
  }
  return `${task.failures ?? '未记录'} / 3 次`
}
function runStatus(run: CommunityRun) {
  if (run.preview || run.status === 'PREVIEW') return run.error ? '预演失败' : '效果预演'
  if (run.publicationStatus === 'FAILED') return '本次发布失败（历史）'
  if (run.error || run.status === 'FAILED') return '本次执行失败（历史）'
  if (run.publishedCommentId || run.publicationStatus === 'SUCCEEDED' || run.status === 'SUCCEEDED') return '评论已发表'
  if (run.status === 'GENERATED') return '已生成，待发表'
  return label(run.status || run.decision)
}
const retryLabels: Record<NonNullable<CommunityTask['retryKind']>, string> = {
  regenerate: '重新生成并入队', publish: '继续发布', postprocess: '恢复发布后处理', complete: '完成沉默任务',
}
const retryLabel = (task: CommunityTask) => task.retryKind ? retryLabels[task.retryKind] : '重新入队'
const retryUsage: Record<NonNullable<CommunityTask['retryKind']>, string> = {
  regenerate: '重新调用模型会消耗新的任务额度和 token，用量以供应商返回为准。',
  publish: '复用内容重交本身不新增模型用量；若讨论变化而重新生成，则按新的调用统计用量。',
  postprocess: '只恢复发布记录和公开记忆，不新增模型调用或 token 用量。',
  complete: '只结束已保存的沉默任务，不新增模型调用、token 用量或评论。',
}
function retryDescription(task: CommunityTask) {
  const reason = task.retryReason || '将已停止的失败任务重新加入处理队列，继续使用原任务。'
  const description = task.retryKind ? `${reason} ${retryUsage[task.retryKind]}` : reason
  return communityEnabled.value ? description : `${description} 全站互动已暂停，开启后才会执行。`
}
function retryNotice(task: CommunityTask) {
  const notice = retryNotices.value[String(task.id)]
  if (!notice) return undefined
  if (task.status === 'SUCCEEDED') return notice.queued ? '人工重试已完成，评论已发表且后处理成功。' : '任务当前已完成，请以最新任务状态为准。'
  if (task.status === 'SKIPPED') return notice.queued ? '人工重试已结束，本任务未再发表新评论。' : '任务当前已结束，请以最新任务状态为准。'
  if (!notice.queued) return `上次人工重试请求：${notice.message}`
  if (task.status === 'FAILED') return '人工重试后任务仍未完成，请查看当前失败原因。'
  return notice.message
}
function retryNoticeType(task: CommunityTask) {
  if (task.status === 'SUCCEEDED') return 'success'
  if (task.status === 'SKIPPED') return 'info'
  return retryNotices.value[String(task.id)]?.queued && task.status !== 'FAILED' ? 'info' : 'error'
}
const botName = (id: number) => bots.value.find(bot => bot.id === id)?.name || '角色信息暂不可用'
const postTitle = (id: number, title?: string) => title || postTitles.value[id] || '文章标题暂不可用'
function tokenUsage(run: CommunityRun) {
  if (run.modelRounds === 0) return '未调用模型'
  if (run.tokenUsageAvailable === undefined) return (run.inputTokens ?? 0) > 0 || (run.outputTokens ?? 0) > 0
    ? `${run.inputTokens ?? '未记录'} / ${run.outputTokens ?? '未记录'}（旧记录，完整性未标注）` : '用量未记录'
  if (run.tokenUsageAvailable === false || (run.inputTokens === undefined && run.outputTokens === undefined)) return '供应商未提供'
  const usage = `${run.inputTokens ?? '未提供'} / ${run.outputTokens ?? '未提供'}`
  return run.tokenUsageComplete === false ? `${usage}（部分）` : usage
}
const runColumns = [
  { title: '时间', dataIndex: 'createdAt', key: 'createdAt', width: 180 },
  { title: '角色', key: 'bot', width: 120 }, { title: '文章', key: 'post', width: 220 },
  { title: '本次执行', key: 'status', width: 145 }, { title: '任务当前状态', key: 'taskStatus', width: 150 }, { title: '选择', key: 'decision', width: 110 }, { title: '选择说明', dataIndex: 'reason', key: 'reason', ellipsis: true },
  { title: '用量', key: 'tokens', width: 150 }, { title: '详情', key: 'detail', width: 80 },
]
const taskColumns = [
  { title: '角色', key: 'bot', width: 120 },
  { title: '文章与触发', key: 'post', width: 220 },
  { title: '状态与执行进度', key: 'status', width: 270 },
  { title: '失败或沉默原因', dataIndex: 'error', key: 'error', ellipsis: true, width: 220 },
  { title: '下次处理时间', key: 'availableAt', width: 180 },
  { title: '操作', key: 'detail', width: 150, fixed: 'right' as const },
]
const eventColumns = [
  { title: '角色', key: 'bot', width: 120 }, { title: '文章', key: 'post', width: 220 },
  { title: '接取进度', key: 'progress', width: 380 }, { title: '操作', key: 'action', width: 100, fixed: 'right' as const },
]
const commentColumns = [
  { title: '时间', key: 'createdAt', width: 180 },
  { title: '文章', key: 'post', width: 180 },
  { title: '回复目标', key: 'parent', width: 110 },
  { title: '正文', dataIndex: 'content', key: 'content' },
  { title: '状态', key: 'deleted', width: 100 },
  { title: '操作', key: 'thread', width: 180, fixed: 'right' as const },
]
const limitFields: { key: keyof ReturnType<typeof defaultCommunitySettings>; title: string; min: number; max: number }[] = [
  { key: 'minDelaySeconds', title: '最短等待（秒）', min: 0, max: 3600 },
  { key: 'maxDelaySeconds', title: '最长等待（秒）', min: 0, max: 3600 },
  { key: 'cooldownSeconds', title: '同角色发言间隔（秒）', min: 0, max: 3600 },
  { key: 'botDailyCommentLimit', title: '每角色每日评论', min: 0, max: 1000 },
  { key: 'siteDailyCommentLimit', title: '全站每日评论', min: 0, max: 10000 },
  { key: 'postDailyCommentLimit', title: '每文章每日评论', min: 0, max: 1000 },
  { key: 'botDailyTaskLimit', title: '每角色每日模型任务', min: 0, max: 1000 },
  { key: 'siteDailyTaskLimit', title: '全站每日模型任务', min: 0, max: 10000 },
  { key: 'maxChainComments', title: '一轮最多 AI 发言', min: 1, max: 4 },
]
function errorMessage(error: any, fallback = '操作失败，请重试') {
  if (error?.response?.data?.message) return error.response.data.message as string
  if (['ECONNABORTED', 'ETIMEDOUT'].includes(error?.code)) return '请求超时，服务未在等待时间内返回结果，请检查模型配置或稍后重试'
  if (error?.isAxiosError && !error.response) return '无法连接服务，请检查主后端和 AI 服务是否可用'
  return error?.message || fallback
}
function resetPreview() {
  ++previewRequest
  previewController?.abort()
  previewController = undefined
  previewLoading.value = false
  previewError.value = ''
  if (detailMode.value === 'preview') { detail.value = undefined; detailOpen.value = false }
}
watch([selectedBotId, postId, commentId], resetPreview)
watch(postId, (id) => {
  postEnabled.value = undefined
  postStateError.value = ''
  ++postStateRequest
  postStateLoading.value = false
  commentId.value = undefined
  postComments.value = []
  postCommentsPage.value = 0
  postCommentsTotal.value = 0
  postCommentsError.value = ''
  ++postCommentsRequest
  postCommentsLoading.value = false
  postRuns.value = []
  postTasks.value = []
  postEvents.value = []
  postActivityError.value = ''
  ++postActivityRequest
  postActivityLoading.value = false
  if (postActivityTimer) clearTimeout(postActivityTimer)
  inviteNotice.value = ''
  inviteError.value = ''
  selectedPost.value = postChoices.value.find(post => post.id === id)
  if (id) { void loadPostComments(); void loadPostState(); void loadPostActivity() }
})
watch(detailOpen, (open) => { if (!open && detailMode.value === 'preview' && previewLoading.value) resetPreview() })
watch(activeTab, (tab) => {
  if (recordsTimer) clearTimeout(recordsTimer)
  if (postActivityTimer) clearTimeout(postActivityTimer)
  if (tab === 'preview') {
    if (!postChoices.value.length) void searchPosts('')
    if (postId.value) void loadPostActivity()
  }
})
async function queryActivity(botId?: number, articleId?: number) {
  const [runResult, taskResult, eventResult] = await Promise.allSettled([
    communityService.runs(botId, articleId), communityService.tasks(botId, articleId), communityService.events(botId, articleId),
  ])
  const failures = [
    runResult.status === 'rejected' ? `运行记录：${errorMessage(runResult.reason)}` : '',
    taskResult.status === 'rejected' ? `AI 任务：${errorMessage(taskResult.reason)}` : '',
    eventResult.status === 'rejected' ? `待接收事件：${errorMessage(eventResult.reason)}` : '',
  ].filter(Boolean)
  const recentTasks = taskResult.status === 'fulfilled' ? taskResult.value : []
  const recentEvents = eventResult.status === 'fulfilled'
    ? eventResult.value.filter(event => !recentTasks.some(task => String(task.eventId) === String(event.id))) : []
  for (const record of [...recentTasks, ...recentEvents]) queueSamples.set(toRaw(record), Date.now())
  return {
    recentRuns: runResult.status === 'fulfilled' ? runResult.value : [], recentTasks,
    recentEvents,
    error: failures.join('；'), complete: !failures.length,
  }
}
async function loadRecords(botId?: number) {
  if (recordsTimer) clearTimeout(recordsTimer)
  const request = ++recordsRequest
  recordsLoading.value = true
  recordsError.value = ''
  try {
    const { recentRuns, recentTasks, recentEvents, error, complete } = await queryActivity(botId)
    if (request !== recordsRequest) return
    runs.value = recentRuns
    tasks.value = recentTasks
    events.value = recentEvents
    recordsError.value = error
    if (taskDetail.value) taskDetail.value = recentTasks.find(task => String(task.id) === String(taskDetail.value!.id)) || taskDetail.value
    if (detail.value && detailMode.value === 'run' && detail.value.id !== undefined) {
      detail.value = recentRuns.find(run => String(run.id) === String(detail.value!.id)) || detail.value
    }
    recordsScopeBotId.value = botId
    recordsLoaded.value = complete
    recordsLoadedAt.value = new Date().toISOString()
    if (pageActive && !document.hidden && (activeTab.value === 'tasks' || activeTab.value === 'runs' && (pendingTasks.value.length || events.value.length))) {
      recordsTimer = setTimeout(() => { void loadRecords(recordsScopeBotId.value) }, 5000)
    }
  } catch (error) {
    if (request === recordsRequest) recordsError.value = errorMessage(error, '无法读取执行记录与任务状态')
  } finally { if (request === recordsRequest) recordsLoading.value = false }
}
function navigateRoleTab(tab: string, botId = selectedBotId.value) {
  if (botId) selectedBotId.value = botId
  activeTab.value = tab
  if (['knowledge', 'memory', 'tasks', 'runs', 'comments'].includes(tab)) void refreshSelected()
}
function contextRoleChanged() { if (['knowledge', 'memory', 'tasks', 'runs', 'comments'].includes(activeTab.value)) void refreshSelected() }
function showTaskFilter(filter: 'all' | 'pending' | 'failed' | 'cancelled') {
  taskFilter.value = filter
  activeTab.value = 'tasks'
  void loadRecords(recordsScopeBotId.value)
}
async function searchPosts(query: string, append = false) {
  if (append && (postLoading.value || postChoices.value.length >= postTotal.value)) return
  const request = ++postSearchRequest
  const page = append ? postPage.value + 1 : 1
  postSearch.value = query
  postLoading.value = true
  postSearchError.value = ''
  try {
    const { data } = await PostsService.getPostList({ page, size: 20, title: query.trim() || undefined, status: 'published', includeDeleted: false })
    if (request !== postSearchRequest) return
    postChoices.value = append ? [...postChoices.value, ...data.records] : data.records
    for (const record of data.records) postTitles.value[record.id] = record.title
    postPage.value = page
    postTotal.value = data.total
  } catch (error) {
    if (request === postSearchRequest) postSearchError.value = errorMessage(error, '查询文章失败')
  } finally { if (request === postSearchRequest) postLoading.value = false }
}
function searchPostChanged(query: string) {
  if (postSearchTimer) clearTimeout(postSearchTimer)
  // 输入一变就作废旧请求，避免旧标题查询覆盖新的下拉列表。
  ++postSearchRequest
  postSearch.value = query
  postLoading.value = true
  postSearchTimer = setTimeout(() => { void searchPosts(query) }, 250)
}
function postDropdownChanged(open: boolean) {
  if (open && !postChoices.value.length && !postLoading.value) void searchPosts(postSearch.value)
}
async function loadPostComments(append = false) {
  const id = postId.value
  if (!id || (append && (postCommentsLoading.value || postComments.value.length >= postCommentsTotal.value))) return
  const request = ++postCommentsRequest
  const page = append ? postCommentsPage.value + 1 : 1
  postCommentsLoading.value = true
  postCommentsError.value = ''
  try {
    const { data } = await CommentsService.getCommentList({ postId: id, page, size: 30, includeDeleted: false, status: 'active' })
    if (request !== postCommentsRequest || id !== postId.value) return
    postComments.value = append ? [...postComments.value, ...data.records] : data.records
    postCommentsPage.value = page
    postCommentsTotal.value = data.total
  } catch (error) {
    if (request === postCommentsRequest && id === postId.value) postCommentsError.value = errorMessage(error, '加载文章评论失败')
  } finally { if (request === postCommentsRequest) postCommentsLoading.value = false }
}
async function loadPostActivity() {
  const id = postId.value
  if (!id) return
  if (postActivityTimer) clearTimeout(postActivityTimer)
  const request = ++postActivityRequest
  postActivityLoading.value = true
  postActivityError.value = ''
  try {
    const { recentRuns, recentTasks, recentEvents, error } = await queryActivity(undefined, id)
    if (request !== postActivityRequest || postId.value !== id) return
    postRuns.value = recentRuns.filter(run => run.postId === id)
    postTasks.value = recentTasks.filter(task => task.postId === id)
    postEvents.value = recentEvents
    postActivityError.value = error
    if (taskDetail.value) taskDetail.value = recentTasks.find(task => String(task.id) === String(taskDetail.value!.id)) || taskDetail.value
    if (detail.value && detailMode.value === 'run' && detail.value.id !== undefined) {
      detail.value = recentRuns.find(run => String(run.id) === String(detail.value!.id)) || detail.value
    }
    if (pageActive && !document.hidden && activeTab.value === 'preview' && (postEvents.value.length || postTasks.value.some(task => pendingStatuses.has(task.status)))) {
      postActivityTimer = setTimeout(() => { void loadPostActivity() }, 5000)
    }
  } catch (error) {
    if (request === postActivityRequest && postId.value === id) postActivityError.value = errorMessage(error, '加载文章任务失败')
  } finally { if (request === postActivityRequest) postActivityLoading.value = false }
}
function visibilityChanged() {
  if (recordsTimer) clearTimeout(recordsTimer)
  if (postActivityTimer) clearTimeout(postActivityTimer)
  if (workerTimer) clearTimeout(workerTimer)
  if (!pageActive || document.hidden) return
  void loadWorker()
  if (['tasks', 'runs'].includes(activeTab.value)) void loadRecords(recordsScopeBotId.value)
  if (postId.value && activeTab.value === 'preview') void loadPostActivity()
}
onBeforeUnmount(() => {
  pageActive = false
  resetPreview()
  ++postSearchRequest
  ++postCommentsRequest
  ++postActivityRequest
  ++postStateRequest
  ++recordsRequest
  ++workerRequest
  if (recordsTimer) clearTimeout(recordsTimer)
  if (postSearchTimer) clearTimeout(postSearchTimer)
  if (postActivityTimer) clearTimeout(postActivityTimer)
  if (workerTimer) clearTimeout(workerTimer)
  if (displayTimer) clearInterval(displayTimer)
  layoutQuery?.removeEventListener('change', layoutChanged)
  document.removeEventListener('visibilitychange', visibilityChanged)
})
onActivated(() => {
  pageActive = true
  if (!document.hidden) void loadWorker()
  if (!document.hidden && ['tasks', 'runs'].includes(activeTab.value)) void loadRecords(recordsScopeBotId.value)
  if (!document.hidden && postId.value && activeTab.value === 'preview') void loadPostActivity()
})
onDeactivated(() => {
  pageActive = false
  if (recordsTimer) clearTimeout(recordsTimer)
  if (postActivityTimer) clearTimeout(postActivityTimer)
  if (workerTimer) clearTimeout(workerTimer)
})
async function loadWorker() {
  if (workerTimer) clearTimeout(workerTimer)
  const request = ++workerRequest
  workerLoading.value = true
  try {
    const status = await communityService.worker()
    if (request !== workerRequest) return
    if (!status?.observedAt) throw new Error('AI 服务未返回有效的执行器状态')
    worker.value = status
    workerReceivedAt.value = Date.now()
    displayNow.value = Date.now()
    workerError.value = ''
  } catch (error) { if (request === workerRequest) workerError.value = errorMessage(error, '无法读取执行器状态') }
  finally {
    if (request === workerRequest) {
      workerLoading.value = false
      if (pageActive && !document.hidden) workerTimer = setTimeout(() => { void loadWorker() }, 5000)
    }
  }
}
async function openWorkerTask() {
  const id = worker.value?.currentTaskId
  if (!id) return
  recordsScopeBotId.value = undefined
  taskFilter.value = 'all'
  activeTab.value = 'tasks'
  await loadRecords()
  const task = tasks.value.find(item => String(item.id) === String(id))
  if (task) { taskDetail.value = task; taskDetailOpen.value = true }
  else message.info('这条任务已经结束或状态已变化，请查看最新队列和执行记录。')
}
async function action(work: () => Promise<void>, success?: string) {
  if (busy.value) return
  const initialTab = activeTab.value
  busy.value = true
  try { await work(); if (success) message.success(success) }
  catch (error: any) {
    if (!error?.isBusiness && ![401, 403].includes(error?.response?.status)) {
      let failure = error?.message || '操作失败，请重试'
      if (error?.isAxiosError) {
        failure = error.response?.data?.message || (error.response
          ? error.response.status >= 500 ? '服务暂时不可用，请稍后重试' : '请求失败，请稍后重试'
          : ['ECONNABORTED', 'ETIMEDOUT'].includes(error.code) ? '请求超时，请稍后重试' : '无法连接服务，请稍后重试')
      }
      message.error(failure)
    }
  }
  finally {
    busy.value = false
    if (activeTab.value !== initialTab && ['knowledge', 'memory', 'tasks', 'runs', 'comments'].includes(activeTab.value)) void refreshSelected()
  }
}
async function load() {
  loading.value = true
  loadError.value = ''
  try {
    const result = await Promise.all([communityService.bots(), communityService.settings()])
    bots.value = result[0]
    settings.value = { ...defaultCommunitySettings(), ...result[1] }
    communityEnabled.value = settings.value.enabled
    savedSettings.value = { ...settings.value }
    configurationLoaded.value = true
    if (!bots.value.some(bot => bot.id === selectedBotId.value)) selectedBotId.value = bots.value[0]?.id
    await loadRecords(recordsLoaded.value ? recordsScopeBotId.value : undefined)
    await loadWorker()
  } catch (error) { loadError.value = errorMessage(error, '加载评论角色与参与规则失败') }
  finally { loading.value = false }
}
function openBot(bot?: CommunityBot) {
  editingBotId.value = bot?.id
  botForm.value = bot ? { name: bot.name, avatarUrl: bot.avatarUrl || '', personality: bot.personality || '', background: bot.background || '', systemPrompt: bot.systemPrompt || '', interests: bot.interests || '', participation: bot.participation, enabled: bot.enabled } : emptyBot()
  botModal.value = true
}
async function uploadBotAvatar(info: any) {
  const file = pickUploadFile(info)
  if (!file || botAvatarUploading.value || busy.value) return
  if (!['image/png', 'image/jpeg', 'image/gif', 'image/webp'].includes(file.type)) { message.warning('请选择 PNG / JPG / GIF / WEBP 图片'); return }
  if (file.size > 5 * 1024 * 1024) { message.warning('图片不能超过 5MB'); return }
  botAvatarUploading.value = true
  try {
    botForm.value.avatarUrl = (await ImageUploadService.uploadImage(file)).fileUrl
    message.success('头像已上传，保存角色后生效')
  } catch (error: any) {
    if (!error?.isBusiness) message.error(errorMessage(error, '头像上传失败，请重试'))
  } finally { botAvatarUploading.value = false }
}
async function saveBot() {
  if (botAvatarUploading.value) return
  if (!botForm.value.name.trim() || !botForm.value.personality.trim()) { message.warning('请填写角色名称和性格'); return }
  await action(async () => {
    const saved = editingBotId.value ? await communityService.updateBot(editingBotId.value, botForm.value) : await communityService.createBot(botForm.value)
    botModal.value = false
    bots.value = editingBotId.value ? bots.value.map(bot => bot.id === saved.id ? saved : bot) : [...bots.value, saved]
    selectedBotId.value = saved.id
  }, '角色已保存')
}
async function toggleBot(bot: CommunityBot) {
  await action(async () => {
    const saved = await communityService.updateBot(bot.id, { name: bot.name, avatarUrl: bot.avatarUrl, personality: bot.personality, background: bot.background, systemPrompt: bot.systemPrompt || '', interests: bot.interests, participation: bot.participation, enabled: !bot.enabled })
    bots.value = bots.value.map(item => item.id === saved.id ? saved : item)
  }, bot.enabled ? '角色已暂停' : communityEnabled.value ? '角色已启用' : '角色已启用，评论互动目前暂停')
}
async function removeBot(botId: number) {
  await action(async () => {
    await communityService.deleteBot(botId)
    bots.value = bots.value.filter(bot => bot.id !== botId)
    if (selectedBotId.value === botId) { selectedBotId.value = bots.value[0]?.id; knowledge.value = []; memories.value = []; comments.value = [] }
  }, '角色已删除')
}
async function saveSettings() {
  if (settings.value.minDelaySeconds > settings.value.maxDelaySeconds) { message.warning('最短等待不能大于最长等待'); return }
  if (enablingCommunity.value && !enableScope.value) { message.warning('请选择开启后如何处理已发布文章'); return }
  const shouldBackfill = enablingCommunity.value && enableScope.value === 'recent'
  await action(async () => {
    settings.value = await communityService.saveSettings(settings.value)
    communityEnabled.value = settings.value.enabled
    savedSettings.value = { ...settings.value }
    message.success(shouldBackfill ? '全站互动已开启，正在邀请角色评价最近 10 篇文章' : '设置已保存')
    if (shouldBackfill) await backfillRecent()
    enableScope.value = undefined
  })
}
async function backfillRecent() {
  backfillError.value = ''
  backfillResult.value = undefined
  try { backfillResult.value = await communityService.backfill(10); await loadRecords(recordsScopeBotId.value) }
  catch (error) { backfillError.value = `全站互动已开启，但补评未成功：${errorMessage(error)}` }
}
async function retryBackfill() { await action(backfillRecent) }
async function pauseAll() {
  await action(async () => { settings.value = await communityService.saveSettings({ ...savedSettings.value, enabled: false }); communityEnabled.value = false; savedSettings.value = { ...settings.value } }, '社区 AI 已暂停')
}
async function loadSelected() {
  const id = selectedBotId.value
  if (activeTab.value === 'knowledge') {
    knowledge.value = []
    if (id) { const data = await communityService.knowledge(id); if (selectedBotId.value === id) knowledge.value = data }
  } else if (activeTab.value === 'memory') {
    memories.value = []
    if (id) { const data = await communityService.memory(id); if (selectedBotId.value === id) memories.value = data }
  } else if (['tasks', 'runs'].includes(activeTab.value)) {
    await loadRecords(recordsScopeBotId.value)
  } else if (activeTab.value === 'comments') {
    const request = ++commentsRequest
    comments.value = []
    commentsTotal.value = 0
    const page = commentsPage.value
    const size = commentsSize.value
    if (id) {
      const data = await communityService.comments(id, page, size)
      if (request === commentsRequest && selectedBotId.value === id && commentsPage.value === page && commentsSize.value === size) {
        comments.value = data.records
        commentsTotal.value = data.total
      }
    }
  }
}
const refreshSelected = () => action(loadSelected)
async function tabChanged() { await refreshSelected() }
function openKnowledge(entry?: CommunityKnowledge) {
  if (!selectedBotId.value) { message.warning('请先选择角色'); return }
  editingKnowledgeId.value = entry?.id
  knowledgeForm.value = { title: entry?.title || '', content: entry?.content || '' }
  knowledgeModal.value = true
}
async function importText(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  if (!/\.(txt|md|markdown)$/i.test(file.name)) { message.warning('请选择 TXT 或 Markdown 文件'); return }
  if (file.size > 200000) { message.warning('资料文件不能超过 200 KB'); return }
  try {
    knowledgeForm.value = { title: file.name.replace(/\.[^.]+$/, ''), content: await file.text() }
    editingKnowledgeId.value = undefined
    knowledgeModal.value = true
  } catch { message.error('读取文件失败') }
}
async function saveKnowledge() {
  const id = selectedBotId.value
  if (!id || !knowledgeForm.value.title.trim() || !knowledgeForm.value.content.trim()) { message.warning('请填写资料标题和正文'); return }
  await action(async () => { await communityService.saveKnowledge(id, knowledgeForm.value, editingKnowledgeId.value); knowledgeModal.value = false; await loadSelected() }, '资料已保存')
}
async function removeKnowledge(id: number) {
  if (!selectedBotId.value) return
  await action(async () => { await communityService.deleteKnowledge(selectedBotId.value!, id); await loadSelected() }, '资料已删除')
}
function validPost() { if (!postId.value || !Number.isSafeInteger(postId.value)) { message.warning('请先选择已发布文章'); return false }; return true }
async function preview() {
  if (!validPost() || !selectedBotId.value) { if (!selectedBotId.value) message.warning('请选择角色'); return }
  if (previewLoading.value) return
  const request = ++previewRequest
  const input = { botId: selectedBotId.value, postId: postId.value!, commentId: commentId.value }
  previewController?.abort()
  previewController = new AbortController()
  previewLoading.value = true
  previewError.value = ''
  detail.value = undefined
  detailMode.value = 'preview'
  detailOpen.value = true
  try {
    const result = await communityService.preview(input, previewController.signal)
    if (request !== previewRequest) return
    detail.value = result
    if (result.error || result.status === 'FAILED') previewError.value = result.error || result.reason || '预演失败，服务未返回具体原因'
  } catch (error) {
    if (request === previewRequest) previewError.value = errorMessage(error, '预演失败，请检查 AI 服务与模型配置')
  } finally { if (request === previewRequest) { previewLoading.value = false; previewController = undefined } }
}
async function loadPostState() {
  const id = postId.value
  if (!id) return
  const request = ++postStateRequest
  postStateLoading.value = true
  postStateError.value = ''
  try {
    const state = await communityService.postEnabled(id)
    if (request === postStateRequest && postId.value === id) postEnabled.value = state.enabled
  } catch (error) {
    if (request === postStateRequest && postId.value === id) postStateError.value = errorMessage(error, '读取文章互动状态失败')
  } finally { if (request === postStateRequest) postStateLoading.value = false }
}
async function setPost(enabled: boolean) {
  if (!validPost() || inviteLoading.value || busy.value || postStateLoading.value || postEnabled.value === enabled) return
  await action(async () => {
    const id = postId.value!
    ++postStateRequest
    postStateLoading.value = false
    await communityService.setPostEnabled(id, enabled)
    if (postId.value === id) { postEnabled.value = enabled; postStateError.value = '' }
  }, enabled ? '文章互动已开启' : '文章互动已关闭')
}
async function invite() {
  if (!validPost()) return
  if (!communityEnabled.value) { message.warning('请先在参与规则中开启全站自动互动'); return }
  if (inviteIds.value.length > 2) { message.warning('每次最多邀请两个角色'); return }
  if (inviteLoading.value || busy.value || postStateLoading.value || postEnabled.value === undefined) return
  const id = postId.value!
  const ids = [...inviteIds.value]
  inviteLoading.value = true
  inviteNotice.value = ''
  inviteError.value = ''
  try {
    if (postEnabled.value === false) {
      ++postStateRequest
      await communityService.setPostEnabled(id, true)
      if (postId.value === id) postEnabled.value = true
    }
    const result = await communityService.invite(id, ids)
    if (postId.value !== id) return
    inviteNotice.value = result.queued > 0
      ? `已加入 ${result.queued} 个邀请任务。角色将按设置等待 ${savedSettings.value.minDelaySeconds}–${savedSettings.value.maxDelaySeconds} 秒后处理，结果显示在下方。`
      : '本次未新增任务：已安排过的角色不会重复入队，或没有可参与的角色。请在任务队列查看现有任务；失败任务可重试原任务。'
    await loadPostActivity()
  } catch (error) {
    if (postId.value === id) inviteError.value = errorMessage(error, '邀请失败')
  } finally { inviteLoading.value = false }
}
async function retryTask(task: CommunityTask) {
  const id = String(task.id)
  if (task.status !== 'FAILED' || retryingTaskIds.value.includes(id)) return
  const previousError = task.error || retryNotices.value[id]?.previousError
  retryingTaskIds.value = [...retryingTaskIds.value, id]
  delete retryNotices.value[id]
  try {
    const result = await communityService.retryTask(task.id)
    retryNotices.value[id] = {
      queued: result.queued,
      previousError,
      message: result.queued
        ? `${result.reason || '重试已加入队列'}${communityEnabled.value ? '，请等待处理结果。' : '。全站互动目前暂停，开启后才会执行。'}`
        : result.reason || '当前任务未能加入重试队列，请刷新查看最新状态。',
    }
    if (result.queued) {
      // 服务确认重新入队后，同步服务端已经清零的本轮失败和当前错误。
      for (const record of [...tasks.value, ...postTasks.value, ...(taskDetail.value ? [taskDetail.value] : [])]) if (String(record.id) === id) {
        record.status = 'READY'
        record.failures = 0
        record.error = undefined
        record.leaseUntil = undefined
        record.availableAt = undefined
      }
    }
    if (['tasks', 'runs'].includes(activeTab.value)) {
      try { await loadSelected() }
      catch (error) { message.error(`重试状态已返回，但刷新记录失败：${errorMessage(error)}`) }
    }
    if (!['tasks', 'runs'].includes(activeTab.value) && postId.value === task.postId) await loadPostActivity()
    if (String(taskDetail.value?.id) === id) {
      const source = ['tasks', 'runs'].includes(activeTab.value) ? tasks.value : postTasks.value
      taskDetail.value = source.find(item => String(item.id) === id) || (result.queued ? { ...task, status: 'READY', failures: 0, error: undefined, leaseUntil: undefined } : task)
    }
  } catch (error) {
    retryNotices.value[id] = { queued: false, message: errorMessage(error, '重试失败'), previousError }
  } finally { retryingTaskIds.value = retryingTaskIds.value.filter(item => item !== id) }
}
function canCancelTask(task: CommunityTask) {
  const status = observedTaskStatus(task)
  return !task.publishedCommentId && (pendingStatuses.has(status) || status === 'FAILED')
}
async function refreshQueues() {
  await Promise.all([loadRecords(recordsScopeBotId.value), loadWorker()])
  if (postId.value) await loadPostActivity()
}
async function cancelTask(task: CommunityTask) {
  const key = 'task:' + task.id
  if (!canCancelTask(task) || cancellingIds.value.includes(key)) return
  cancellingIds.value = [...cancellingIds.value, key]
  try {
    const result = await communityService.cancelTask(task.id)
    if (result.cancelled) message.success(result.reason || '任务已取消')
    else message.warning(result.reason || '任务未能取消，请查看最新状态')
    await refreshQueues()
  } catch (error: any) { if (!error?.isBusiness) message.error(errorMessage(error, '取消失败')) }
  finally { cancellingIds.value = cancellingIds.value.filter(id => id !== key) }
}
async function cancelEvent(event: CommunityEvent) {
  const key = 'event:' + event.id
  if (cancellingIds.value.includes(key)) return
  cancellingIds.value = [...cancellingIds.value, key]
  try {
    const result = await communityService.cancelEvent(event.id)
    if (result.data.cancelled) message.success(result.data.reason || '任务已取消')
    else message.warning(result.data.reason || '任务状态已变化，请查看最新状态')
    await refreshQueues()
  } catch (error: any) { if (!error?.isBusiness) message.error(errorMessage(error, '取消失败')) }
  finally { cancellingIds.value = cancellingIds.value.filter(id => id !== key) }
}
async function withdrawComment(id: number) {
  if (withdrawingIds.value.includes(id)) return
  withdrawingIds.value = [...withdrawingIds.value, id]
  try {
    await communityService.withdrawComment(id)
    message.success('评论及其回复已撤回')
    if (activeTab.value === 'comments') await loadSelected()
    if (threadOpen.value && threadCommentId.value) await openThread(threadCommentId.value, threadBotId.value)
    await refreshQueues()
  } catch (error: any) { if (!error?.isBusiness) message.error(errorMessage(error, '撤回失败')) }
  finally { withdrawingIds.value = withdrawingIds.value.filter(item => item !== id) }
}
async function clearMemory() {
  if (!selectedBotId.value) return
  await action(async () => { await communityService.clearMemory(selectedBotId.value!); await loadSelected() }, '角色记忆已清空')
}
function showRun(run: CommunityRun) { resetPreview(); detailMode.value = run.preview ? 'preview' : 'run'; detail.value = run; detailOpen.value = true }
function reviewBot(botId: number) { selectedBotId.value = botId; activeTab.value = 'comments'; commentsPage.value = 1; void refreshSelected() }
function commentsBotChanged() { commentsPage.value = 1; void refreshSelected() }
function commentsPageChanged(page: number, size: number) { commentsPage.value = page; commentsSize.value = size; void refreshSelected() }
function authorName(comment: CommunityComment) {
  return comment.authorType === 'BOT' ? comment.bot?.name || botName(comment.botId!) : comment.user?.username || '已删除用户'
}
function replyTarget(comment: CommunityComment) {
  if (!comment.parentId) return '文章'
  const parent = thread.value?.comments.find(item => item.id === comment.parentId)
  return parent ? `${authorName(parent)}：${parent.content}` : '较早的评论（当前线程范围内未提供）'
}
async function openThread(commentId: number, botId = selectedBotId.value) {
  const request = ++threadRequest
  threadOpen.value = true
  threadLoading.value = true
  thread.value = undefined
  threadError.value = ''
  threadBotId.value = botId
  threadCommentId.value = commentId
  try {
    const data = await communityService.thread(commentId)
    if (request === threadRequest) thread.value = data
  } catch (error: any) {
    if (request === threadRequest) threadError.value = error?.response?.data?.message || error?.message || '加载对话失败，请重试'
  } finally { if (request === threadRequest) threadLoading.value = false }
}
function settingsValue(key: keyof ReturnType<typeof defaultCommunitySettings>) { return settings.value[key] as number }
function updateSetting(key: keyof ReturnType<typeof defaultCommunitySettings>, value: number | null) { if (value !== null) Object.assign(settings.value, { [key]: value }) }
onMounted(() => {
  displayTimer = setInterval(() => { if (pageActive && !document.hidden) displayNow.value = Date.now() }, 1000)
  layoutQuery = window.matchMedia('(max-width: 960px)')
  compactLayout.value = layoutQuery.matches
  layoutQuery.addEventListener('change', layoutChanged)
  document.addEventListener('visibilitychange', visibilityChanged)
  void load()
})
</script>

<template>
  <div class="p-24 community-page">
    <a-card :bordered="false" class="mb-16">
      <div class="page-title">
        <div>
          <h2>评论角色</h2>
          <p>预演效果、管理任务，审查和撤回角色发言。</p>
        </div>
        <a-space wrap>
          <a-tag :color="communityEnabled ? 'green' : 'default'">{{ !configurationLoaded ? loading ? '读取配置中' : '配置未加载' : communityEnabled ? '互动已开启' : '互动已暂停' }}</a-tag>
          <a-tag v-if="worker || workerError" :color="workerOnline ? worker?.busy ? 'blue' : 'green' : 'red'">{{ workerTitle }}</a-tag>
          <a-button @click="showTaskFilter('pending')">任务队列 {{ recordsLoaded ? events.length + pendingTasks.length : '—' }}</a-button>
          <a-button danger :disabled="!communityEnabled || busy" @click="pauseAll">暂停全部</a-button>
          <a-button :loading="loading" :disabled="busy" @click="load">
            <ReloadOutlined />刷新</a-button>
        </a-space>
      </div>
    </a-card>
    <a-alert v-if="loadError" type="error" :message="loadError" show-icon class="mb-16"><template #action><a-button :loading="loading" @click="load">重新加载</a-button></template></a-alert>
    <a-card :bordered="false" class="mb-16 workflow-card">
      <div class="role-context"><span>当前操作角色</span><a-select v-model:value="selectedBotId" :disabled="busy" :options="botOptions" show-search allow-clear option-filter-prop="label" placeholder="选择角色后准备资料、预演和审查" class="context-select" @change="contextRoleChanged" /><template v-if="selectedBot"><a-tag :color="selectedBot.enabled ? 'green' : 'default'">{{ selectedBot.enabled ? '角色已启用' : '角色已暂停，仍可预演' }}</a-tag><a-button size="small" :disabled="busy" @click="openBot(selectedBot)">编辑角色</a-button></template><span v-else class="muted">请选择一个角色以继续资料、预演或讨论审查。</span></div>
    </a-card>
    <a-tabs v-model:activeKey="activeTab" @change="tabChanged">
      <a-tab-pane key="roles" tab="角色配置">
        <div class="roles-layout">
        <a-card :bordered="false" title="角色列表">
          <template #extra>
            <a-button type="primary" :disabled="busy" @click="openBot()">
              <PlusOutlined />创建角色</a-button>
          </template>
          <a-empty v-if="!bots.length && !loading" description="创建第一个角色开始预演" />
          <div class="bot-grid">
            <a-card v-for="bot in bots" :key="bot.id" size="small" :class="{ 'bot-card-selected': selectedBotId === bot.id }">
              <div class="bot-heading">
                <a-avatar :src="bot.avatarUrl">{{ bot.name.charAt(0) }}</a-avatar>
                <a-tooltip :title="bot.name"><a-button type="link" class="bot-name-button" @click="selectedBotId = bot.id"><strong class="truncate">{{ bot.name }}</strong></a-button></a-tooltip>
                <a-tag :color="bot.enabled ? 'green' : 'default'">{{ bot.enabled ? '启用' : '暂停' }}</a-tag>
              </div>
              <a-tooltip :title="bot.personality"><p class="bot-description role-excerpt">{{ bot.personality }}</p></a-tooltip>
              <p class="muted">兴趣：{{ bot.interests || '不限' }} · 积极度 {{ bot.participation }}%</p>
              <a-space wrap>
                <a-button size="small" :disabled="busy" @click="openBot(bot)">编辑</a-button>
                <a-button size="small" :disabled="busy" @click="toggleBot(bot)">{{ bot.enabled ? '暂停' : '启用' }}</a-button>
                <a-button size="small" @click="navigateRoleTab('preview', bot.id)">预演</a-button>
                <a-button size="small" @click="navigateRoleTab('knowledge', bot.id)">资料</a-button>
                <a-button size="small" @click="reviewBot(bot.id)">评论与对话</a-button>
                <a-popconfirm title="删除角色后将停止其发言，确定删除？" @confirm="removeBot(bot.id)">
                  <a-button size="small" danger :disabled="busy">删除</a-button>
                </a-popconfirm>
              </a-space>
            </a-card>
          </div>
        </a-card>
        <a-card :bordered="false" class="role-next-actions">
          <template #title><a-tooltip :title="selectedBot?.name"><div class="truncate">{{ selectedBot ? `${selectedBot.name} · 下一步` : '选择角色继续' }}</div></a-tooltip></template>
          <details :open="!compactLayout"><summary class="role-actions-summary">展开角色资料、预演与审查入口</summary>
          <template v-if="selectedBot">
            <div class="bot-heading"><a-avatar :size="48" :src="selectedBot.avatarUrl">{{ selectedBot.name.charAt(0) }}</a-avatar><div><strong>{{ selectedBot.name }}</strong><p class="muted">{{ selectedBot.enabled ? '已允许正式参与' : '尚未允许正式参与' }}</p></div></div>
            <a-tooltip :title="selectedBot.background"><p class="role-excerpt">{{ selectedBot.background || '未设置身份背景，可在编辑角色中补充。' }}</p></a-tooltip>
            <p class="muted">兴趣：{{ selectedBot.interests || '不限' }} · 积极度 {{ selectedBot.participation }}%</p>
            <a-alert v-if="!selectedBot.enabled" type="info" show-icon message="可以先预演，确认后再启用角色。" class="mb-16" />
            <a-alert v-else-if="selectedBot.participation === 0" type="info" show-icon message="这个角色的积极度为 0，不自动参与；仍可预演与手动邀请。" class="mb-16" />
            <div class="next-action-buttons"><a-button @click="navigateRoleTab('knowledge')">准备这个角色的资料</a-button><a-button type="primary" @click="navigateRoleTab('preview')">选择文章，预演看看</a-button><a-button v-if="!selectedBot.enabled" :loading="busy" @click="toggleBot(selectedBot)">允许角色正式参与</a-button><a-button @click="navigateRoleTab('comments')">查看实际评论与对话</a-button></div>
            <p class="muted metric-note">{{ communityEnabled ? '全站已开启。正式参与仍遵守文章开关、额度与等待规则。' : '全站目前暂停。角色启用后，还需要开启全站互动才会自动参与。' }}</p>
          </template>
          <a-empty v-else description="从左侧选择角色，或创建一个新角色" />
          </details>
        </a-card>
        </div>
      </a-tab-pane>
      <a-tab-pane key="knowledge" tab="角色知识">
        <a-card :bordered="false" title="角色知识库">
          <template #extra>
            <a-space>
              <a-select :disabled="busy" v-model:value="selectedBotId" :options="botOptions" placeholder="选择角色" class="bot-select" @change="refreshSelected" />
              <a-button :disabled="!selectedBotId || busy" @click="openKnowledge()">添加资料</a-button>
              <a-button :disabled="!selectedBotId || busy" @click="fileInput?.click()">导入文件</a-button>
            </a-space>
          </template>
          <input ref="fileInput" type="file" accept=".txt,.md,.markdown,text/plain,text/markdown" hidden @change="importText" />
          <p class="muted">{{ selectedBot?.name || '每个角色' }}只使用自己的资料。支持 TXT、Markdown，可编辑导入后的正文。</p>
          <a-spin :spinning="busy">
            <a-empty v-if="!knowledge.length" description="暂无资料" />
            <a-list v-if="knowledge.length" :data-source="knowledge">
              <template #renderItem="{ item }">
                <a-list-item>
                  <a-list-item-meta :title="item.title" :description="`${item.content.length} 字`" />
                  <template #actions>
                    <a-button type="link" @click="openKnowledge(item)">编辑</a-button>
                    <a-popconfirm title="确定删除这条资料？" @confirm="removeKnowledge(item.id)">
                      <a-button type="link" danger>删除</a-button>
                    </a-popconfirm>
                  </template>
                </a-list-item>
              </template>
            </a-list>
          </a-spin>
        </a-card>
      </a-tab-pane>
      <a-tab-pane key="preview" tab="效果预演">
        <a-card :bordered="false" title="先看看角色会说什么">
          <a-alert class="mb-16" type="info" show-icon message="预演只生成结果，不发表，也不触发角色互聊。全站或角色暂停时仍可预演。" description="预演会调用模型并消耗任务次数和 token，与自动互动共享模型处理容量。结果可能是评论、回复或保持沉默，具体原因会完整显示。" />
          <a-form layout="vertical">
            <a-row :gutter="16">
              <a-col :xs="24" :lg="5">
                <a-form-item label="角色">
                  <a-select :disabled="busy" v-model:value="selectedBotId" :options="botOptions" placeholder="选择角色" />
                </a-form-item>
              </a-col>
              <a-col :xs="24" :lg="10">
                <a-form-item label="已发布文章">
                  <a-select v-model:value="postId" :options="postOptions" :loading="postLoading" :filter-option="false" show-search allow-clear placeholder="搜索文章标题并选择" @search="searchPostChanged" @dropdown-visible-change="postDropdownChanged">
                    <template #option="{ label: optionLabel }"><a-tooltip :title="optionLabel"><span class="truncate select-option">{{ optionLabel }}</span></a-tooltip></template>
                    <template #notFoundContent><a-spin v-if="postLoading" size="small" /><span v-else>{{ postSearchError || '没有找到已发布文章' }}</span></template>
                  </a-select>
                  <p v-if="postSearchError" class="field-error">{{ postSearchError }}</p>
                  <a-button v-if="postChoices.length < postTotal" type="link" size="small" :loading="postLoading" @click="searchPosts(postSearch, true)">加载更多文章（已载入 {{ postChoices.length }} / {{ postTotal }}）</a-button>
                </a-form-item>
              </a-col>
              <a-col :xs="24" :lg="9">
                <a-form-item label="回复哪条评论（可选）">
                  <a-select v-model:value="commentId" :options="postCommentOptions" :loading="postCommentsLoading" :disabled="!postId" show-search allow-clear option-filter-prop="label" placeholder="留空评价文章；可选作者及评论内容"><template #option="{ value, label: optionLabel }"><a-tooltip :title="postComments.find(comment => comment.id === value)?.content"><span class="truncate select-option">{{ optionLabel }}</span></a-tooltip></template></a-select>
                  <p v-if="postCommentsError" class="field-error">{{ postCommentsError }} <a-button type="link" size="small" @click="loadPostComments()">重试</a-button></p>
                  <p v-else-if="postId && !postCommentsLoading && !postComments.length" class="muted">这篇文章还没有可回复的评论，可直接预演文章评价。</p>
                  <a-button v-if="postComments.length < postCommentsTotal" type="link" size="small" :loading="postCommentsLoading" @click="loadPostComments(true)">加载更多评论（{{ postComments.length }} / {{ postCommentsTotal }}）</a-button>
                </a-form-item>
              </a-col>
            </a-row>
            <div v-if="selectedPost" class="selected-post mb-16">
              <strong>{{ selectedPost.title }}</strong> <a-tag color="green">已发布 · 公开可读</a-tag>
              <p class="muted">{{ formatDateTime(selectedPost.createdAt) }} · {{ selectedPost.author?.username || '作者信息未提供' }}</p>
              <a-tooltip :title="selectedPost.summary || '文章未设置摘要'"><p class="truncate">{{ selectedPost.summary || '文章未设置摘要' }}</p></a-tooltip>
            </div>
            <a-button type="primary" :loading="previewLoading" :disabled="!postId || !selectedBotId" @click="preview">预演，不发布</a-button>
          </a-form>
          <a-divider />
          <h3>预演后：邀请角色正式参与</h3>
          <a-space wrap class="mb-16">
            <a-button :loading="postStateLoading" :disabled="!postId" @click="loadPostState">刷新文章状态</a-button>
            <a-tag v-if="postEnabled !== undefined" :color="postEnabled ? 'green' : 'default'">{{ postEnabled ? '该文章互动已开启' : '该文章互动已关闭' }}</a-tag>
          </a-space>
          <a-alert v-if="postStateError" type="error" :message="postStateError" show-icon class="mb-16" />
          <a-alert v-if="!communityEnabled" type="warning" message="全站互动已暂停。预演仍可使用；正式邀请前请到参与规则开启全站互动。" show-icon class="mb-16"><template #action><a-button @click="activeTab = 'settings'">查看参与规则</a-button></template></a-alert>
          <p class="muted">文章开关只控制是否允许参与，不创建任务。每个角色对同一篇文章仅安排一次初评，已取消或沉默的初评不会再次邀请；新的评论讨论仍可触发参与。角色会读取内容后决定发言或沉默，等待和额度规则仍然生效。</p>
          <a-space wrap>
            <a-button :disabled="!postId || busy || inviteLoading || postStateLoading || postEnabled !== false" @click="setPost(true)">开启该文章互动</a-button>
            <a-button :disabled="!postId || busy || inviteLoading || postStateLoading || postEnabled !== true" @click="setPost(false)">关闭该文章互动</a-button>
          </a-space>
          <div class="invite-form">
            <a-select :disabled="busy || inviteLoading" v-model:value="inviteIds" mode="multiple" :options="enabledBotOptions" placeholder="最多选择两名角色；留空自动选择" class="invite-select" />
            <a-button type="primary" :loading="inviteLoading" :disabled="busy || !communityEnabled || !postId || postStateLoading || postEnabled === undefined" @click="invite">{{ postEnabled === false ? '开启互动并立即邀请' : '立即邀请' }}</a-button>
          </div>
          <a-alert v-if="inviteNotice" class="mt-16" type="info" :message="inviteNotice" show-icon />
          <a-alert v-if="inviteError" class="mt-16" type="error" :message="inviteError" show-icon />
          <template v-if="postId">
            <div class="activity-heading"><h3>这篇文章的处理结果</h3><a-button :loading="postActivityLoading" @click="loadPostActivity">刷新结果</a-button></div>
            <p class="muted">等待中的任务每 5 秒刷新一次。发言失败、沉默和成功都会显示真实结果；仅显示最近任务与运行记录。</p>
            <a-alert v-if="postActivityError" type="error" :message="postActivityError" show-icon class="mb-16" />
            <a-empty v-if="!postEvents.length && !postTasks.length && !postRuns.length && !postActivityLoading" description="暂无邀请任务或自动参与记录" />
            <div v-for="event in postEvents" :key="'event:' + event.id" class="activity-item"><div class="activity-heading"><strong>{{ botName(event.botId) }}</strong><a-tag>{{ event.status === 'DISPATCHING' ? '正在交接' : '等待 AI 接收' }}</a-tag><a-button danger size="small" :loading="cancellingIds.includes('event:' + event.id)" @click="cancelEvent(event)">取消任务</a-button></div><p class="muted">{{ eventProgress(event) }} · 计划处理：{{ plannedAt(event) }}</p></div>
            <div v-for="task in postTasks" :key="task.id" class="activity-item">
              <div class="activity-heading"><strong>{{ botName(task.botId) }}</strong><a-tag>{{ taskStatus(task) }}</a-tag><a-button type="link" size="small" @click="taskDetail = task; taskDetailOpen = true">任务详情</a-button><a-popconfirm v-if="canCancelTask(task)" title="取消此任务并阻止发布？" @confirm="cancelTask(task)"><a-button type="link" danger size="small" :loading="cancellingIds.includes('task:' + task.id)">取消任务</a-button></a-popconfirm><a-tooltip v-if="task.status === 'FAILED'" :title="retryDescription(task)"><a-button type="link" size="small" :loading="retryingTaskIds.includes(String(task.id))" @click="retryTask(task)">{{ retryLabel(task) }}</a-button></a-tooltip></div>
              <p v-if="task.availableAt && pendingStatuses.has(task.status)" class="muted">{{ automaticRetry(task) ? '计划自动重试' : '下次处理' }}：{{ plannedAt(task) }}</p>
              <p class="muted">模型调用 {{ task.attempts }} 次 · 本轮失败：{{ failureCount(task) }}</p>
              <p v-if="task.error" class="comment-body">{{ task.error }}</p>
              <p v-else-if="postRuns.find(run => String(run.taskId) === String(task.id))?.reason" class="comment-body">{{ postRuns.find(run => String(run.taskId) === String(task.id))?.reason }}</p>
              <a-alert v-if="retryNotices[String(task.id)]" class="mt-16" :type="retryNoticeType(task)" :message="retryNotice(task)" show-icon />
            </div>
            <div v-for="run in postRuns" :key="run.id || run.taskId" class="activity-item">
              <div class="activity-heading"><strong>{{ run.roleSnapshot?.name || botName(run.botId) }}</strong><a-tag>{{ runStatus(run) }}</a-tag><a-tag v-if="run.decision && run.status">{{ label(run.decision) }}</a-tag><a-button type="link" size="small" @click="showRun(run)">查看本次执行</a-button></div>
              <p v-if="currentTask(run)" class="muted">任务当前状态：{{ taskStatus(currentTask(run)!) }}</p>
              <p class="comment-body">{{ run.error || run.reason || '服务未提供决策说明' }}</p>
              <a-tooltip v-if="run.content" :title="run.content"><p class="truncate">{{ run.content }}</p></a-tooltip>
            </div>
          </template>
        </a-card>
      </a-tab-pane>
      <a-tab-pane key="comments" tab="评论对话">
        <a-card :bordered="false" title="已发布评论与对话审查">
          <template #extra>
            <a-space>
              <a-select :disabled="busy" v-model:value="selectedBotId" :options="botOptions" placeholder="选择角色" class="bot-select" @change="commentsBotChanged" />
              <a-button :disabled="!selectedBotId" :loading="busy" @click="refreshSelected">刷新</a-button>
            </a-space>
          </template>
          <p class="muted">查看角色实际发布的评论，包含已撤回记录。撤回会同时隐藏其回复（包含真人回复）；发布回执与已消耗额度保留。打开对话可按时间查看同一线程的真人及 AI 发言。</p>
          <a-empty v-if="!selectedBotId" description="请选择要审查的角色" />
          <template v-else>
            <a-table :columns="commentColumns" :data-source="comments" :loading="busy" :pagination="false" :row-key="(comment: CommunityComment) => comment.id" :scroll="{ x: 1000 }">
              <template #bodyCell="{ column, record }">
                <template v-if="column.key === 'createdAt'">{{ formatDateTime(record.createdAt) }}</template>
                <template v-else-if="column.key === 'post'"><a-tooltip :title="postTitle(record.postId, record.postTitle)"><div class="truncate table-summary">{{ postTitle(record.postId, record.postTitle) }}</div></a-tooltip></template>
                <template v-else-if="column.key === 'parent'">
                  <a-tooltip v-if="record.parentId" :title="record.parentPreview || '打开对话查看回复对象'"><a-button type="link" class="table-summary truncate" @click="openThread(record.parentId)">{{ record.parentPreview || '查看回复对象' }}</a-button></a-tooltip>
                  <span v-else>文章</span>
                </template>
                <template v-else-if="column.key === 'content'"><a-tooltip :title="record.content"><div class="truncate table-summary">{{ record.content }}</div></a-tooltip></template>
                <template v-else-if="column.key === 'deleted'">
                  <a-tag :color="record.deletedAt ? 'red' : 'green'">{{ record.deletedAt ? '已撤回' : '已发布' }}</a-tag>
                </template>
                <template v-else-if="column.key === 'thread'">
                  <a-space><a-button type="link" @click="openThread(record.id)">查看对话</a-button><a-popconfirm v-if="!record.deletedAt" title="撤回这条 AI 评论及其回复（包含真人回复）？已消耗的额度不会返还。" @confirm="withdrawComment(record.id)"><a-button type="link" danger :loading="withdrawingIds.includes(record.id)">撤回</a-button></a-popconfirm></a-space>
                </template>
              </template>
            </a-table>
            <a-pagination class="review-pagination" :current="commentsPage" :page-size="commentsSize" :total="commentsTotal" :disabled="busy" :show-size-changer="true" :page-size-options="['10', '20', '50']" :show-total="(total: number) => `共 ${total} 条评论`" @change="commentsPageChanged" />
          </template>
        </a-card>
      </a-tab-pane>
      <a-tab-pane key="tasks" tab="任务队列">
        <a-card :bordered="false" title="待处理与可取消任务">
          <template #extra><a-space wrap><a-select v-model:value="recordsScopeBotId" :options="botOptions" allow-clear placeholder="所有角色" class="bot-select" @change="loadRecords(recordsScopeBotId)" /><a-button :loading="recordsLoading || workerLoading" @click="refreshQueues">刷新队列</a-button></a-space></template>
          <section class="worker-monitor" aria-label="自动评论执行器">
            <div class="worker-heading"><h3>实时执行</h3><a-badge :status="workerTone" :text="workerTitle" /><span v-if="worker" class="muted">心跳：{{ heartbeatAgeSeconds === undefined ? '尚未收到' : durationText(heartbeatAgeSeconds) + '前' }} · 每 {{ (worker.pollIntervalMs || 5000) / 1000 }} 秒轮询</span></div>
            <a-alert v-if="workerWarning" type="error" :message="workerWarning" show-icon class="mt-16" />
            <div v-if="worker?.currentTaskId" class="worker-current-task">
              <div><strong>{{ botName(worker.currentBotId!) }}</strong><span>{{ workerFresh ? '正在处理' : '上次处理' }}</span><strong>{{ currentWorkerTask ? postTitle(currentWorkerTask.postId, currentWorkerTask.postTitle) : `文章 #${worker.currentPostId || '—'}` }}</strong><a-button type="link" size="small" @click="openWorkerTask">查看这个任务</a-button><a-popconfirm v-if="currentWorkerTask && canCancelTask(currentWorkerTask)" title="取消当前任务并阻止发布？已送出的模型请求仍可能产生用量。" @confirm="cancelTask(currentWorkerTask!)"><a-button danger size="small" :loading="cancellingIds.includes('task:' + currentWorkerTask.id)">取消当前任务</a-button></a-popconfirm></div>
              <p><a-spin v-if="workerOnline && worker.busy" size="small" /><strong>{{ phaseLabel }}</strong><span>已耗时 {{ durationText(phaseElapsedSeconds) }}</span><span v-if="worker.currentModel">模型 {{ worker.currentModel }}</span><span v-if="worker.phaseTimeoutMs">本阶段上限 {{ durationText(worker.phaseTimeoutMs / 1000) }}</span></p>
              <a-alert v-if="worker.state === 'CANCELLING'" type="info" show-icon message="任务已经取消，等待已发出的模型请求结束；结果不会发表。" />
            </div>
            <p v-else-if="workerFresh && worker?.busy" class="worker-idle"><a-spin size="small" />{{ phaseLabel }} · 已耗时 {{ durationText(phaseElapsedSeconds) }}</p>
            <p v-else class="worker-idle">{{ worker?.blockReason || (workerOnline ? '正在按轮询间隔检查任务，当前没有执行中的任务。' : '执行器状态无法确认，请刷新或检查 AI 服务。') }}</p>
            <div v-if="worker?.queue && worker.database?.available" class="worker-queue-summary"><span>已到期待接取 <strong>{{ worker.queue.readyCount }}</strong></span><span>等待到期 <strong>{{ worker.queue.delayedCount }}</strong></span><span>已领取 <strong>{{ worker.queue.leasedCount }}</strong></span><span>停止的失败 <strong>{{ worker.queue.failedCount }}</strong></span><span>主服务待接收 <strong>{{ events.length }}</strong></span></div>
            <p v-if="worker?.lastTaskFinishedAt" class="muted worker-last-task">最近处理：{{ label(worker.lastTaskOutcome) }} · {{ formatDateTime(worker.lastTaskFinishedAt) }}<span v-if="worker.lastTaskId"> · 任务 {{ worker.lastTaskId }}</span></p>
            <details v-if="worker" class="worker-diagnostics"><summary>执行器诊断与最近错误</summary><p>实例 {{ worker.instanceId }} · 最近轮询 {{ formatDateTime(worker.lastPollStartedAt) }} · 最近完成轮询 {{ formatDateTime(worker.lastPollFinishedAt) }}</p><p>执行租约：{{ worker.leaseState === 'OWNED_ELSEWHERE' ? '其他实例持有' : worker.leaseOwned ? '本实例持有' : '当前未持有' }}<span v-if="worker.leaseRemainingSeconds != null"> · 剩余 {{ durationText(worker.leaseRemainingSeconds) }}</span></p><p v-if="worker.lastError">最近错误：{{ worker.lastError }} · {{ formatDateTime(worker.lastErrorAt) }} · 连续失败 {{ worker.consecutivePollFailures || 0 }} 次</p><p v-if="worker.database">数据库连接 {{ worker.database.available ? '正常' : '不可用' }} · 数据库时间 {{ worker.database.now || '未知' }} · 会话时区 {{ worker.database.sessionTimeZone || '未知' }} · 系统时区 {{ worker.database.systemTimeZone || '未知' }}</p></details>
          </section>
          <p class="muted">{{ recordsScope }} · 活动任务优先显示，包含尚未被 AI 接收的事件。</p>
          <a-alert v-if="recordsError" type="error" :message="recordsError" class="mb-16" show-icon />
          <div class="activity-heading"><span class="muted">筛选任务</span><a-radio-group v-model:value="taskFilter" button-style="solid" size="small"><a-radio-button value="all">全部 {{ recordsLoaded ? tasks.length + events.length : '—' }}</a-radio-button><a-radio-button value="pending">待处理 {{ recordsLoaded ? pendingTasks.length + events.length : '—' }}</a-radio-button><a-radio-button value="failed">失败 {{ recordsLoaded ? failedTasks.length : '—' }}</a-radio-button><a-radio-button value="cancelled">已取消</a-radio-button></a-radio-group></div>
          <details class="memory-record-details"><summary>任务操作与自动重试规则</summary><p class="muted">任务队列展示当前结果。待处理任务可取消，已发布评论请在评论对话中撤回。取消会阻止后续发布；正在进行的模型调用可能仍产生用量。失败任务只能重试原任务。</p><p class="muted">本页可见时每 5 秒刷新。首次失败后最多自动重试 2 次，本轮失败 3 次后停止；实际模型轮数和 token 看运行详情。</p></details>
          <h3 v-if="visibleTasks.length" class="queue-section-title">AI 处理任务</h3>
          <p v-else-if="recordsLoaded && !recordsLoading" class="muted">当前筛选没有已接取的处理任务；待接收事件会在接取后进入这里。</p>
          <a-table v-if="visibleTasks.length" :columns="taskColumns" :data-source="visibleTasks" :loading="busy || recordsLoading" :row-key="(task: CommunityTask) => task.id" :scroll="{ x: 1160 }">
            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'bot'">{{ botName(record.botId) }}</template>
              <template v-else-if="column.key === 'status'"><a-tag :color="executingTask(record) ? 'blue' : undefined">{{ taskStatus(record) }}</a-tag><p v-if="taskProgress(record)" class="task-progress">{{ taskProgress(record) }}</p></template>
              <template v-else-if="column.key === 'failures'">{{ failureCount(record) }}</template>
              <template v-else-if="column.key === 'availableAt'"><span v-if="pendingStatuses.has(record.status)">{{ automaticRetry(record) ? '自动重试：' : '' }}{{ plannedAt(record) }}</span><span v-else>—</span></template>
              <template v-else-if="column.key === 'post'"><a-tooltip :title="postTitle(record.postId, record.postTitle)"><div class="truncate table-summary">{{ postTitle(record.postId, record.postTitle) }}</div></a-tooltip></template>
              <template v-else-if="column.key === 'comment'"><a-tooltip :title="record.commentPreview"><div class="truncate table-summary">{{ record.commentId ? record.commentPreview || '触发评论内容暂不可用' : '文章触发' }}</div></a-tooltip></template>
              <template v-else-if="column.key === 'error'"><a-tooltip :title="record.error"><div class="truncate table-summary">{{ record.error || '—' }}</div></a-tooltip><a-tooltip v-if="retryNotices[String(record.id)]" :title="retryNotice(record)"><div class="truncate table-summary" :class="{ 'field-error': !retryNotices[String(record.id)].queued }">{{ retryNotice(record) }}</div></a-tooltip></template>
              <template v-else-if="column.key === 'detail'"><a-space direction="vertical" :size="0"><a-button type="link" @click="taskDetail = record; taskDetailOpen = true">查看</a-button><a-tooltip v-if="record.status === 'FAILED'" :title="retryDescription(record)"><a-button type="link" :loading="retryingTaskIds.includes(String(record.id))" @click="retryTask(record)">{{ retryLabel(record) }}</a-button></a-tooltip><a-popconfirm v-if="canCancelTask(record)" title="取消此任务并阻止发布？正在进行的模型调用可能仍产生用量。" @confirm="cancelTask(record)"><a-button type="link" danger :loading="cancellingIds.includes('task:' + record.id)">取消任务</a-button></a-popconfirm></a-space></template>
            </template>
          </a-table>
          <template v-if="events.length && ['all', 'pending'].includes(taskFilter)">
            <h3 class="queue-section-title">等待 AI 接收</h3>
            <a-table :columns="eventColumns" :data-source="events" :row-key="(event: CommunityEvent) => String(event.id)" size="small" :pagination="{ pageSize: 5, showSizeChanger: true, pageSizeOptions: ['5', '10', '20'] }" :scroll="{ x: 820 }">
              <template #bodyCell="{ column, record }"><template v-if="column.key === 'bot'">{{ botName(record.botId) }}</template><template v-else-if="column.key === 'post'"><a-tooltip :title="postTitle(record.postId, record.postTitle)"><span class="truncate table-summary">{{ postTitle(record.postId, record.postTitle) }}</span></a-tooltip></template><template v-else-if="column.key === 'progress'"><p class="task-progress">{{ eventProgress(record) }}</p><span class="muted">{{ plannedAt(record) }}</span></template><template v-else-if="column.key === 'action'"><a-popconfirm title="取消这条待接收任务？取消后不会重新邀请同一初评。" @confirm="cancelEvent(record)"><a-button type="link" danger :loading="cancellingIds.includes('event:' + record.id)">取消</a-button></a-popconfirm></template></template>
            </a-table>
          </template>
        </a-card>
      </a-tab-pane>
      <a-tab-pane key="runs" tab="执行记录">
        <a-card :bordered="false" title="执行历史与当前任务">
          <template #extra>
            <a-space>
              <a-select v-model:value="recordsScopeBotId" :options="botOptions" allow-clear placeholder="所有角色" class="bot-select" @change="loadRecords(recordsScopeBotId)" />
              <a-button :loading="busy || recordsLoading" @click="refreshSelected">刷新</a-button>
            </a-space>
          </template>
          <p class="muted">查询范围：{{ recordsScope }}。每轮模型生成留下独立记录；发布结果更新该轮记录，旧失败生成记录保留。请以「任务当前状态」判断是否仍需处理。</p>
          <a-alert v-if="recordsError" type="error" :message="recordsError" class="mb-16" show-icon />
          <a-table :columns="runColumns" :data-source="runs" :loading="busy || recordsLoading" :row-key="(run: CommunityRun) => run.id || run.taskId" :scroll="{ x: 1280 }">
            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'bot'">{{ botName(record.botId) }}</template>
              <template v-else-if="column.key === 'createdAt'">{{ formatDateTime(record.createdAt) }}</template>
              <template v-else-if="column.key === 'post'"><a-tooltip :title="postTitle(record.postId, record.postTitle)"><div class="truncate table-summary">{{ postTitle(record.postId, record.postTitle) }}</div></a-tooltip></template>
              <template v-else-if="column.key === 'reason'"><a-tooltip :title="record.error || record.reason"><div class="truncate table-summary">{{ record.error || record.reason || '—' }}</div></a-tooltip></template>
              <template v-else-if="column.key === 'status'">
                <a-tag :color="record.status === 'FAILED' ? 'orange' : undefined">{{ runStatus(record) }}</a-tag>
              </template>
              <template v-else-if="column.key === 'taskStatus'"><a-tag v-if="currentTask(record)" :color="currentTask(record)!.status === 'FAILED' ? 'red' : currentTask(record)!.status === 'SUCCEEDED' ? 'green' : undefined">{{ taskStatus(currentTask(record)!) }}</a-tag><span v-else class="muted">{{ record.preview || record.status === 'PREVIEW' ? '预演不入队' : '当前查询未含该任务' }}</span></template>
              <template v-else-if="column.key === 'decision'"><a-tag>{{ label(record.decision) }}</a-tag></template>
              <template v-else-if="column.key === 'tokens'"><a-tooltip :title="`${tokenUsage(record)}。输入 / 输出 token，${record.modelRounds ?? '未知'} 次模型调用；仅展示供应商返回的用量`"><div class="truncate token-cell">{{ tokenUsage(record) }}</div></a-tooltip></template>
              <template v-else-if="column.key === 'detail'">
                <a-button type="link" @click="showRun(record)">查看</a-button>
              </template>
            </template>
          </a-table>
        </a-card>
      </a-tab-pane>
      <a-tab-pane key="memory" tab="互动记忆">
        <a-card :bordered="false" title="角色记得的公开讨论">
          <template #extra>
            <a-space>
              <a-select :disabled="busy" v-model:value="selectedBotId" :options="botOptions" placeholder="选择角色" class="bot-select" @change="refreshSelected" />
              <a-popconfirm title="清空该角色的全部互动记忆？此操作不可恢复。" @confirm="clearMemory">
                <a-button danger :disabled="!selectedBotId || busy">清空记忆</a-button>
              </a-popconfirm>
            </a-space>
          </template>
          <a-spin :spinning="busy">
            <a-empty v-if="!memories.length" description="暂无互动记忆" />
            <a-list v-if="memories.length" :data-source="memories">
              <template #renderItem="{ item }">
                <a-list-item>
                  <a-list-item-meta>
                    <template #title><a-tooltip :title="postTitle(item.sourcePostId, item.postTitle)"><div class="truncate">{{ postTitle(item.sourcePostId, item.postTitle) }}</div></a-tooltip></template>
                    <template #description>
                      <a-tooltip :title="item.summary"><p class="truncate">{{ item.summary }}</p></a-tooltip>
                      <span class="muted">{{ formatDateTime(item.createdAt) }} · {{ item.participants?.length || 0 }} 位公开讨论参与者</span>
                      <a-tooltip v-if="item.sourceCommentPreview" :title="item.sourceCommentPreview"><p class="truncate">相关评论：{{ item.sourceCommentPreview }}</p></a-tooltip>
                      <a-button v-if="item.sourceCommentId || item.sourceCommentIds?.length" type="link" @click="openThread(item.sourceCommentId || item.sourceCommentIds[0])">查看关联对话</a-button>
                      <details class="memory-record-details"><summary>查看记录编号和关联范围</summary><p>记忆编号：{{ item.id }} · 来源文章编号：{{ item.sourcePostId }}</p><p>关联评论编号：{{ item.sourceCommentIds?.join('、') || item.sourceCommentId || '未提供' }}</p></details>
                    </template>
                  </a-list-item-meta>
                </a-list-item>
              </template>
            </a-list>
          </a-spin>
        </a-card>
      </a-tab-pane>
      <a-tab-pane key="settings" tab="参与规则">
        <a-card :bordered="false" title="全站开关、参与频率与额度">
          <a-form layout="vertical">
            <a-form-item label="全站自动互动">
              <a-switch v-model:checked="settings.enabled" checked-children="开启" un-checked-children="暂停" />
            </a-form-item>
            <a-alert v-if="selectedBot && !selectedBot.enabled" class="mb-16" type="info" show-icon :message="`当前角色“${selectedBot.name}”尚未启用；全站开启后，这个角色仍不会自动参与。`"><template #action><a-button :loading="busy" @click="toggleBot(selectedBot)">启用这个角色</a-button></template></a-alert>
            <a-alert v-else-if="selectedBot?.participation === 0" class="mb-16" type="info" show-icon :message="`当前角色“${selectedBot.name}”的积极度为 0，只能预演或手动邀请。`"><template #action><a-button @click="openBot(selectedBot)">调整积极度</a-button></template></a-alert>
            <a-form-item v-if="enablingCommunity" label="开启后如何处理已发布文章" required>
              <a-radio-group v-model:value="enableScope" class="enable-options">
                <a-radio value="new">只参与新文章与新的评论讨论</a-radio>
                <a-radio value="recent">同时评价最近 10 篇已发布文章</a-radio>
              </a-radio-group>
              <p class="muted">补评会创建真实模型任务，遵守文章开关和额度。已经安排过初评或已有 AI 评价的文章会跳过；角色也可以保持沉默。</p>
            </a-form-item>
            <a-alert v-if="settings.enabled && !bots.some(bot => bot.enabled && bot.participation > 0)" type="warning" class="mb-16" show-icon message="还没有可参与的角色。请先启用至少一个角色，并把参与积极度设为大于 0。" />
            <a-form-item v-if="communityEnabled" label="已发布文章补评">
              <p class="muted">可单独邀请角色评价最近 10 篇已发布文章。此操作会消耗模型任务与 token，已经安排过的初评不会重复入队。</p>
              <a-button :loading="busy" @click="retryBackfill">补评最近 10 篇文章</a-button>
            </a-form-item>
            <a-alert v-if="backfillResult" class="mb-16" type="success" show-icon :message="`已检查 ${backfillResult.postCount} 篇文章，新增 ${backfillResult.queued} 个初评任务，本次未安排 ${backfillResult.skipped} 个角色与文章组合。`" description="加入队列不代表已经发表评论，请在执行记录查看处理状态、沉默原因或失败详情。" />
            <a-alert v-if="backfillError" type="error" class="mb-16" :message="backfillError" show-icon><template #action><a-button :loading="busy" :disabled="!communityEnabled" @click="retryBackfill">重试补评</a-button></template></a-alert>
            <p class="muted">额度按北京时间每日计算。模型任务包含沉默和失败，额度为 0 时停止相应参与。</p>
            <a-row :gutter="16">
              <a-col v-for="field in limitFields" :key="field.key" :xs="24" :sm="12" :lg="8">
                <a-form-item :label="field.title">
                  <a-input-number :value="settingsValue(field.key)" :min="field.min" :max="field.max" :precision="0" style="width:100%" @update:value="updateSetting(field.key, $event)" />
                </a-form-item>
              </a-col>
            </a-row>
            <a-button type="primary" :loading="busy" :disabled="enablingCommunity && !enableScope" @click="saveSettings">{{ enablingCommunity && enableScope === 'recent' ? '开启并补评最近 10 篇' : '保存设置' }}</a-button>
          </a-form>
        </a-card>
      </a-tab-pane>
    </a-tabs>
    <details class="runtime-details"><summary>运行统计与模型用量 <span class="muted">展开查看最近查询的统计</span></summary>
    <div class="console-overview mb-16">
      <a-card :bordered="false" class="overview-summary">
        <div class="overview-heading"><h3>当前状态</h3><span class="muted">{{ recordsLoaded ? recordsScope : '执行记录尚未加载' }}</span><a-button size="small" :loading="recordsLoading" @click="loadRecords(recordsScopeBotId)">刷新状态</a-button></div>
        <div class="status-grid">
          <div class="status-cell"><span class="status-label">自动参与角色</span><strong>{{ configurationLoaded ? autoBots.length : '—' }}<small v-if="configurationLoaded"> / {{ bots.length }}</small></strong><span class="muted">已启用且积极度大于 0</span></div>
          <div class="status-cell"><span class="status-label">全站互动</span><strong class="status-word" :class="{ 'status-active': communityEnabled }">{{ !configurationLoaded ? '未读取' : communityEnabled ? '已开启' : '已暂停' }}</strong><a-button type="link" size="small" @click="activeTab = 'settings'">调整开关与额度</a-button></div>
          <button class="status-cell status-button" :disabled="!recordsLoaded" @click="showTaskFilter('pending')"><span class="status-label">待处理任务</span><strong>{{ recordsLoaded ? events.length + pendingTasks.length : '—' }}</strong><span class="muted">当前查询，含等待与处理中</span></button>
          <button class="status-cell status-button" :disabled="!recordsLoaded" @click="showTaskFilter('failed')"><span class="status-label">已停止的失败任务</span><strong :class="{ 'status-failed': failedTasks.length }">{{ recordsLoaded ? failedTasks.length : '—' }}</strong><span class="muted">自动重试结束，待人工处理</span></button>
          <div class="status-cell"><span class="status-label">已知 token 合计</span><strong>{{ recordsLoaded ? usageSummary.knownTokens.toLocaleString() : '—' }}</strong><span class="muted">当前 {{ runs.length }} 条运行，包含预演</span></div>
        </div>
        <p v-if="recordsLoaded" class="metric-note muted">卡片合计仅包含带用量标记的记录。用量缺失 {{ usageSummary.missing }} 条 · 部分提供 {{ usageSummary.partial }} 条 · 未调用模型 {{ usageSummary.notCalled }} 条<span v-if="usageSummary.legacy"> · 另有 {{ usageSummary.legacy }} 条旧记录提供 {{ usageSummary.legacyKnownTokens.toLocaleString() }} token，完整性未标注</span>。最近更新 {{ formatDateTime(recordsLoadedAt) }}。这里统计已加载记录，不代表全站累计或每日账单。</p>
        <a-alert v-if="configurationLoaded && participationNotice" type="info" :message="participationNotice" show-icon class="mt-16"><template #action><a-button size="small" @click="activeTab = 'settings'">检查设置</a-button></template></a-alert>
        <a-alert v-if="recordsError" type="error" :message="recordsError" :description="recordsLoaded ? '本次查询不完整，请重新刷新。' : '暂时无法确认任务与模型用量。'" show-icon class="mt-16" />
        <a-alert v-if="failedTasks.length" type="warning" :message="`当前查询有 ${failedTasks.length} 个任务已停止自动重试`" description="先查看失败阶段和原因，再手动重新入队。重试后成功的任务不会计入这里；历史失败执行记录会保留。" show-icon class="mt-16"><template #action><a-button size="small" @click="showTaskFilter('failed')">处理失败任务</a-button></template></a-alert>
      </a-card>
      <a-card :bordered="false" class="decision-summary">
        <h3>最近执行结果</h3>
        <p class="muted">当前查询的正式生成，排除预演。失败生成记录会保留；同轮发布结果更新原记录，重交不重复计算模型用量。</p>
        <div v-for="entry in decisionDistribution" :key="entry.key" class="decision-row"><div class="decision-heading"><span>{{ entry.title }}</span><strong>{{ recordsLoaded ? entry.count : '—' }}</strong></div><div class="decision-track"><div class="decision-bar" :style="{ width: `${decisionTotal ? entry.count / decisionTotal * 100 : 0}%`, background: entry.color }" /></div></div>
        <p class="metric-note muted">{{ recordsLoaded ? `共 ${decisionTotal} 条可归类记录` : '等待服务返回执行记录' }}</p>
      </a-card>
    </div>
    </details>
    <a-modal v-model:open="botModal" :title="editingBotId ? '编辑角色' : '创建角色'" :confirm-loading="busy" :ok-button-props="{ disabled: botAvatarUploading }" :cancel-button-props="{ disabled: botAvatarUploading }" :mask-closable="!botAvatarUploading" :closable="!botAvatarUploading" :keyboard="!botAvatarUploading" width="min(1100px, 92vw)" :body-style="{ maxHeight: '72vh', overflowY: 'auto' }" @ok="saveBot">
      <a-form layout="vertical">
        <a-row :gutter="24">
          <a-col :xs="24" :lg="12">
            <h3 class="form-group-title">身份与背景</h3>
            <a-form-item label="角色名称" required><a-input v-model:value="botForm.name" :maxlength="80" placeholder="读者看到的角色名称" /></a-form-item>
            <a-form-item label="头像">
              <div class="bot-avatar-field">
                <a-avatar :size="64" :src="botForm.avatarUrl || undefined">{{ botForm.name.trim().charAt(0) || '角' }}</a-avatar>
                <div class="bot-avatar-actions">
                  <a-upload-dragger
                    :disabled="botAvatarUploading || busy"
                    name="file"
                    :show-upload-list="false"
                    accept="image/png,image/jpeg,image/gif,image/webp"
                    :max-count="1"
                    :before-upload="() => false"
                    @change="uploadBotAvatar"
                  >
                    <a-button :loading="botAvatarUploading"><UploadOutlined />{{ botForm.avatarUrl ? '更换头像' : '上传头像' }}</a-button>
                    <p class="muted">拖入图片或点击选择</p>
                  </a-upload-dragger>
                  <a-button v-if="botForm.avatarUrl" :disabled="botAvatarUploading" type="link" size="small" @click="botForm.avatarUrl = ''">移除</a-button>
                  <p class="muted">支持 PNG / JPG / GIF / WEBP，不超过 5MB。不上传时显示角色名首字。</p>
                </div>
              </div>
            </a-form-item>
            <a-form-item label="身份 / 背景"><a-textarea v-model:value="botForm.background" :rows="6" :maxlength="10000" show-count placeholder="角色是谁，有什么经历、立场和知识背景" /></a-form-item>
          </a-col>
          <a-col :xs="24" :lg="12">
            <h3 class="form-group-title">性格与参与方式</h3>
            <a-form-item label="性格与表达方式" required><a-textarea v-model:value="botForm.personality" :rows="6" :maxlength="10000" show-count placeholder="例如：温和、好奇，喜欢用具体例子解释技术" /></a-form-item>
            <a-form-item label="兴趣"><a-input v-model:value="botForm.interests" :maxlength="1000" placeholder="例如：编程、音乐、日常生活" /></a-form-item>
            <a-form-item label="参与积极度"><a-slider v-model:value="botForm.participation" :min="0" :max="100" /><span class="muted">{{ botForm.participation }}% · 0 时不自动参与，仍可预演</span></a-form-item>
          </a-col>
        </a-row>
        <h3 class="form-group-title">行为提示词</h3>
        <a-form-item label="自定义提示词">
          <a-textarea v-model:value="botForm.systemPrompt" :rows="8" :maxlength="10000" show-count placeholder="角色的行为与回复要求，例如：回应具体观点，遇到不确定的知识先查资料，没有值得补充的内容时保持沉默" />
        </a-form-item>
        <a-form-item label="允许正式参与">
          <a-switch v-model:checked="botForm.enabled" />
          <p v-if="!communityEnabled" class="muted">全站评论互动目前暂停。保存角色启用状态后，开启全站互动才会自动参与。</p>
        </a-form-item>
      </a-form>
    </a-modal>
    <a-modal v-model:open="knowledgeModal" :title="editingKnowledgeId ? '编辑资料' : '添加资料'" width="min(1100px, 92vw)" :body-style="{ maxHeight: '72vh', overflowY: 'auto' }" :confirm-loading="busy" @ok="saveKnowledge">
      <a-form layout="vertical">
        <a-form-item label="标题" required>
          <a-input v-model:value="knowledgeForm.title" :maxlength="200" />
        </a-form-item>
        <a-form-item label="正文" required>
          <a-textarea v-model:value="knowledgeForm.content" :rows="14" :maxlength="200000" show-count />
        </a-form-item>
      </a-form>
    </a-modal>
    <a-modal v-model:open="detailOpen" :title="detailMode === 'preview' ? '预演结果（不会发布）' : '本次执行详情'" :mask-closable="!previewLoading" width="min(1100px, 92vw)" :body-style="{ maxHeight: '72vh', overflowY: 'auto' }" :footer="null">
      <div v-if="previewLoading" class="preview-loading"><a-spin /><p>预演请求处理中，等待服务返回角色决策与生成结果…</p><p class="muted">模型响应可能需要一些时间。关闭窗口或更换角色、文章会取消本次等待。</p></div>
      <a-alert v-if="previewError" type="error" :message="previewError" show-icon class="mb-16" />
      <a-button v-if="previewError && detailMode === 'preview'" type="primary" class="mb-16" :loading="previewLoading" @click="preview">重新预演</a-button>
      <template v-if="detail">
        <a-alert v-if="detailMode === 'preview' && !previewError" type="success" show-icon class="mb-16" :message="detail.decision === 'SKIP' ? '预演已完成：角色决定保持沉默' : '预演已完成：生成结果仅展示在此窗口'" :description="detail.reason || '服务未提供决策原因'" />
        <a-descriptions :column="{ xs: 1, sm: 2 }" bordered>
          <a-descriptions-item label="角色">{{ detail.roleSnapshot?.name || botName(detail.botId) }}</a-descriptions-item>
          <a-descriptions-item label="文章"><a-tooltip :title="postTitle(detail.postId, detail.postTitle)"><div class="truncate">{{ postTitle(detail.postId, detail.postTitle) }}</div></a-tooltip></a-descriptions-item>
          <a-descriptions-item label="决策">{{ label(detail.decision) }}</a-descriptions-item>
          <a-descriptions-item label="本次执行">{{ runStatus(detail) }}</a-descriptions-item>
          <a-descriptions-item v-if="currentTask(detail)" label="任务当前状态">{{ taskStatus(currentTask(detail)!) }}</a-descriptions-item>
          <a-descriptions-item v-if="detail.publishedCommentId" label="已发布评论" :span="2"><a-button type="link" @click="openThread(detail.publishedCommentId!, detail.botId)">查看已发布评论与对话</a-button></a-descriptions-item>
          <a-descriptions-item label="回复目标"><a-tooltip v-if="detail.targetCommentId" :title="detail.targetCommentPreview || '打开对话查看完整评论'"><a-button type="link" class="truncate detail-comment-link" @click="openThread(detail.targetCommentId!, detail.botId)">{{ detail.targetAuthorName ? detail.targetAuthorName + '：' : '' }}{{ detail.targetCommentPreview || '查看目标评论' }}</a-button></a-tooltip><span v-else>文章</span></a-descriptions-item>
          <a-descriptions-item label="选择说明" :span="2">{{ detail.reason || '—' }}</a-descriptions-item>
          <a-descriptions-item label="模型">{{ detail.model || '—' }}</a-descriptions-item>
          <a-descriptions-item label="输入 / 输出 token">{{ tokenUsage(detail) }}</a-descriptions-item>
          <a-descriptions-item label="生成尝试次数">{{ detail.modelRounds ?? '未记录' }}</a-descriptions-item>
        </a-descriptions>
        <a-alert v-if="detailMode === 'run' && (detail.error || detail.status === 'FAILED')" class="mt-16" type="info" show-icon message="这是本轮生成或提交的记录；请结合任务当前状态判断是否仍需处理。" :description="currentTask(detail) ? `关联任务当前：${taskStatus(currentTask(detail)!)}。只有当前已停止的失败任务才可手动重新入队。` : '当前查询范围未包含关联任务；请在任务队列查看它的最新状态。'" />
        <p class="muted">用量汇总本次执行的多轮调用，只统计模型供应商返回的 usage；缺失或部分返回时会明确标注。</p>
        <details v-if="detail.roleSnapshot" class="role-snapshot">
          <summary>本次执行的角色配置（版本 {{ detail.roleSnapshot.version ?? '—' }}）</summary>
          <a-descriptions :column="1" bordered class="mt-16">
            <a-descriptions-item label="名称">{{ detail.roleSnapshot.name }}</a-descriptions-item>
            <a-descriptions-item label="身份 / 背景"><div class="comment-body">{{ detail.roleSnapshot.background || '—' }}</div></a-descriptions-item>
            <a-descriptions-item label="性格与表达方式"><div class="comment-body">{{ detail.roleSnapshot.personality || '—' }}</div></a-descriptions-item>
            <a-descriptions-item label="自定义提示词"><div class="comment-body">{{ detail.roleSnapshot.systemPrompt || '—' }}</div></a-descriptions-item>
          </a-descriptions>
        </details>
        <a-alert v-if="detail.error && !previewError" type="error" :message="detail.error" class="mt-16" />
        <h3>发言内容</h3>
        <a-alert v-if="detail.contentTruncated" type="info" message="生成内容超过评论长度限制，服务已截取可发表的部分。" class="mb-16" show-icon />
        <div class="preview-content">{{ detail.content || (detail.error || previewError ? '本次未生成可用的发言内容。' : detail.decision === 'SKIP' ? '角色选择保持沉默。' : '服务未返回发言内容。') }}</div>
        <h3>读取来源与范围</h3>
        <a-empty v-if="!detail.readTrace?.length" description="没有读取记录" />
        <ul v-else class="trace-list">
          <li v-for="(trace, index) in detail.readTrace" :key="index">{{ sourceName(trace.source) }}：已读取 {{ trace.start ?? 0 }}–{{ trace.end ?? '—' }} / {{ trace.total ?? '—' }} <a-tag v-if="trace.truncated" color="orange">已截取</a-tag><details><summary>查看来源编号</summary>{{ trace.id }}</details>
          </li>
        </ul>
        <h3>使用的工具</h3>
        <a-empty v-if="!detail.toolTrace?.length" description="未使用工具" />
        <ul v-else class="trace-list">
          <li v-for="(tool, index) in detail.toolTrace" :key="index">{{ tool.name }} · {{ tool.status === 'SUCCEEDED' ? '成功' : tool.status === 'FAILED' ? '失败' : label(tool.status) }}<details v-if="tool.input">
              <summary>查看参数</summary>
              <pre>{{ toolInput(tool.input) }}</pre>
            </details>
          </li>
        </ul>
      </template>
    </a-modal>
    <a-modal v-model:open="taskDetailOpen" title="任务详情" width="min(1100px, 92vw)" :body-style="{ maxHeight: '72vh', overflowY: 'auto' }" :footer="null">
      <a-alert v-if="taskDetail && taskProgress(taskDetail)" :type="executingTask(taskDetail) ? 'info' : !workerOnline && pendingStatuses.has(taskDetail.status) ? 'warning' : 'info'" :message="taskProgress(taskDetail)" show-icon class="mb-16" />
      <a-alert v-if="taskDetail && retryNotices[String(taskDetail.id)]" class="mb-16" :type="retryNoticeType(taskDetail)" :message="retryNotice(taskDetail)" show-icon />
      <a-alert v-if="taskDetail?.status === 'FAILED'" type="warning" show-icon class="mb-16" message="该任务已停止自动重试，可手动重新入队。" :description="retryDescription(taskDetail)"><template #action><a-button :loading="retryingTaskIds.includes(String(taskDetail.id))" @click="retryTask(taskDetail)">{{ retryLabel(taskDetail) }}</a-button></template></a-alert>
      <a-popconfirm v-if="taskDetail && canCancelTask(taskDetail)" title="取消此任务并阻止发布？正在进行的模型调用可能仍产生用量。" @confirm="cancelTask(taskDetail!)"><a-button danger class="mb-16" :loading="cancellingIds.includes('task:' + taskDetail.id)">取消任务</a-button></a-popconfirm>
      <a-descriptions v-if="taskDetail" :column="{ xs: 1, sm: 2 }" bordered>
        <a-descriptions-item label="角色">{{ botName(taskDetail.botId) }}</a-descriptions-item>
        <a-descriptions-item label="当前状态">{{ taskStatus(taskDetail) }}</a-descriptions-item>
        <a-descriptions-item label="文章" :span="2">{{ postTitle(taskDetail.postId, taskDetail.postTitle) }}</a-descriptions-item>
        <a-descriptions-item label="触发线索" :span="2"><div class="comment-body">{{ taskDetail.commentId ? taskDetail.commentPreview || '触发评论内容暂不可用' : '文章触发' }}</div></a-descriptions-item>
        <a-descriptions-item :label="automaticRetry(taskDetail) ? '计划自动重试' : '下次处理时间'">{{ pendingStatuses.has(taskDetail.status) ? plannedAt(taskDetail) : '—' }}</a-descriptions-item>
        <a-descriptions-item label="生成尝试次数">{{ taskDetail.attempts }}</a-descriptions-item>
        <a-descriptions-item label="本轮失败次数">{{ failureCount(taskDetail) }}</a-descriptions-item>
        <a-descriptions-item v-if="taskDetail.publishedCommentId" label="评论已发表"><a-button type="link" @click="openThread(taskDetail.publishedCommentId!, taskDetail.botId)">查看已发布评论</a-button></a-descriptions-item>
        <a-descriptions-item label="当前失败或沉默原因" :span="2">{{ taskDetail.error || '无' }}</a-descriptions-item>
      </a-descriptions>
      <p v-if="taskDetail" class="muted">触发线索是角色开始阅读讨论的入口。实际选择评价文章、回复哪条评论或保持沉默，请查看对应执行记录。</p>
      <a-alert v-if="taskDetail?.status === 'FAILED' && (taskDetail.failures === undefined || taskDetail.failures < 3)" class="mt-16" type="info" show-icon message="这条任务已经停止。部分旧记录采用不同的失败计数口径，请以当前状态为准，不会继续自动重试。" />
      <p v-if="taskDetail" class="muted">现行规则：首次失败后最多自动重试 2 次，本轮累计失败 3 次后停止。人工重新入队时清零本轮失败计数，生成尝试次数继续累计；历史执行记录保留。</p>
      <details v-if="taskDetail" class="memory-record-details"><summary>查看任务编号与诊断信息</summary><p>任务编号：{{ taskDetail.id }} · 领取到期：{{ formatDateTime(taskDetail.leaseUntil) }}</p><p v-if="retryNotices[String(taskDetail.id)]?.previousError">人工重试前的失败原因：{{ retryNotices[String(taskDetail.id)].previousError }}</p></details>
    </a-modal>
    <a-modal v-model:open="threadOpen" title="评论对话审查" width="min(1100px, 92vw)" :body-style="{ maxHeight: '72vh', overflowY: 'auto' }" :footer="null">
      <a-spin :spinning="threadLoading">
        <a-alert v-if="threadError" type="error" :message="threadError" show-icon />
        <template v-if="thread">
          <p>{{ postTitle(thread.postId, thread.postTitle) }} · 共 {{ thread.total }} 条对话</p>
          <a-alert v-if="thread.truncated" class="mb-16" type="info" :message="`此线程共 ${thread.total} 条，仅显示最近 200 条。更早的回复目标可能不在当前范围内。`" show-icon />
          <a-empty v-if="!thread.comments.length" description="暂无对话记录" />
          <div v-for="comment in thread.comments" :key="comment.id" class="thread-comment" :class="{ 'thread-focus': comment.id === threadCommentId }">
            <div class="thread-heading">
              <strong>{{ authorName(comment) }}</strong>
              <a-tag :color="comment.authorType !== 'BOT' ? 'default' : comment.botId === threadBotId ? 'blue' : 'purple'">{{ comment.authorType !== 'BOT' ? '真人' : comment.botId === threadBotId ? '当前 AI' : '其他 AI' }}</a-tag>
              <a-tag v-if="comment.deletedAt" color="red">已撤回</a-tag>
              <a-popconfirm v-if="comment.authorType === 'BOT' && !comment.deletedAt" title="撤回这条 AI 评论及其回复（包含真人回复）？" @confirm="withdrawComment(comment.id)"><a-button type="link" danger size="small" :loading="withdrawingIds.includes(comment.id)">撤回</a-button></a-popconfirm>
              <span class="muted">{{ formatDateTime(comment.createdAt) }}</span>
            </div>
            <a-tooltip :title="replyTarget(comment)"><p class="muted truncate">回复 {{ replyTarget(comment) }}</p></a-tooltip>
            <div class="comment-body">{{ comment.content }}</div>
            <p v-if="comment.deletedAt" class="muted">删除时间：{{ formatDateTime(comment.deletedAt) }}</p>
          </div>
        </template>
      </a-spin>
    </a-modal>
  </div>
</template>

<style scoped>
.page-title { display: flex; justify-content: space-between; align-items: center; gap: 16px; flex-wrap: wrap; }
.page-title h2 { margin: 0 0 4px; font-size: 20px; }
.page-title p { margin: 0; }
.page-title > .ant-space { max-width: 100%; }
.community-page > .ant-card:first-child :deep(.ant-card-body) { padding: 14px 18px; }
.runtime-details { margin-top: 16px; }
.runtime-details > summary { cursor: pointer; padding: 12px 16px; background: var(--lt-color-bg-container); border-radius: 8px; }
.runtime-details > summary .muted { margin-left: 12px; font-size: 12px; }
.runtime-details .console-overview { margin-top: 12px; }
.queue-section-title { margin: 16px 0 8px; font-size: 15px; }
.worker-monitor { margin-bottom: 16px; padding: 14px 16px; border: 1px solid var(--lt-color-border); border-radius: 8px; background: var(--lt-color-bg-layout); }
.worker-heading { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }
.worker-heading h3 { margin: 0; font-size: 16px; }
.worker-heading > .muted { font-size: 12px; }
.worker-current-task { margin-top: 12px; }
.worker-current-task > div, .worker-current-task > p { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; margin-bottom: 8px; }
.worker-current-task > p > span { color: var(--lt-color-text-secondary); font-size: 12px; }
.worker-idle { display: flex; align-items: center; gap: 8px; margin: 12px 0 8px; }
.worker-queue-summary { display: flex; flex-wrap: wrap; gap: 8px 20px; font-size: 12px; color: var(--lt-color-text-secondary); }
.worker-queue-summary strong { color: var(--lt-color-text); font-variant-numeric: tabular-nums; }
.worker-last-task { margin: 8px 0 0; font-size: 12px; overflow-wrap: anywhere; }
.worker-diagnostics { margin-top: 8px; font-size: 12px; }
.worker-diagnostics summary { cursor: pointer; color: var(--lt-color-text-secondary); }
.worker-diagnostics p { margin: 8px 0 0; overflow-wrap: anywhere; }
.task-progress { margin: 4px 0 0; font-size: 12px; color: var(--lt-color-text-secondary); white-space: normal; }
.page-title p, .muted { color: var(--lt-color-text-secondary); }
.console-overview { display: grid; grid-template-columns: minmax(0, 1fr) 300px; gap: 16px; align-items: start; }
.overview-summary :deep(.ant-card-body), .decision-summary :deep(.ant-card-body), .workflow-card :deep(.ant-card-body) { padding: 18px; }
.overview-heading { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; margin-bottom: 12px; }
.overview-heading h3 { margin: 0; }
.overview-heading > .muted { flex: 1; font-size: 12px; }
.status-grid { display: grid; grid-template-columns: repeat(5, minmax(0, 1fr)); gap: 12px; }
.status-cell { display: flex; flex-direction: column; align-items: flex-start; gap: 6px; padding: 12px; min-width: 0; background: var(--lt-color-bg-layout); border-radius: 8px; }
.status-label { font-size: 13px; color: var(--lt-color-text-secondary); }
.status-cell > strong { font-size: 24px; line-height: 1.2; font-variant-numeric: tabular-nums; }
.status-cell > strong small { font-size: 14px; font-weight: normal; color: var(--lt-color-text-secondary); }
.status-cell > strong.status-word { font-size: 22px; }
.status-cell > .muted { font-size: 12px; }
.status-cell .ant-btn { padding-left: 0; }
.status-active { color: var(--lt-color-success-text); }
.status-failed { color: var(--lt-color-error-text); }
.status-button { appearance: none; border: 1px solid transparent; color: inherit; font: inherit; text-align: left; cursor: pointer; }
.status-button:hover, .status-button:focus-visible { border-color: var(--lt-color-primary); }
.status-button:disabled { cursor: default; }
.metric-note { margin: 16px 0 0; font-size: 12px; line-height: 1.7; }
.decision-summary h3 { margin-top: 0; }
.decision-summary > :deep(.ant-card-body) > .muted { font-size: 12px; }
.decision-row { margin-top: 14px; }
.decision-heading { display: flex; justify-content: space-between; margin-bottom: 6px; font-size: 13px; }
.decision-track { height: 8px; border-radius: 4px; overflow: hidden; background: var(--lt-color-bg-layout); }
.decision-bar { height: 100%; border-radius: 4px; }
.workflow-path { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 16px; }
.workflow-step { display: flex; align-items: flex-start; gap: 10px; }
.workflow-step .ant-btn { padding-left: 0; padding-right: 8px; height: auto; font-weight: 600; }
.workflow-step p { margin: 6px 0 0; font-size: 12px; }
.step-number { display: grid; place-items: center; flex: 0 0 28px; height: 28px; border-radius: 50%; background: var(--lt-color-bg-layout); color: var(--lt-color-primary); font-weight: 600; }
.role-context { display: flex; align-items: center; flex-wrap: wrap; gap: 12px; margin-top: 0; padding-top: 0; }
.context-select { width: min(340px, 100%); }
.roles-layout { display: grid; grid-template-columns: minmax(0, 1fr) 320px; gap: 16px; align-items: start; }
.role-next-actions .bot-heading p { margin: 6px 0 0; font-size: 12px; }
.role-excerpt { display: -webkit-box; -webkit-box-orient: vertical; -webkit-line-clamp: 3; overflow: hidden; overflow-wrap: anywhere; white-space: pre-wrap; }
.role-next-actions .role-excerpt { margin-top: 16px; }
.next-action-buttons { display: flex; flex-direction: column; gap: 12px; }
.role-actions-summary { display: none; cursor: pointer; color: var(--lt-color-primary); }
.bot-card-selected { border-color: var(--lt-color-primary); }
.bot-name-button { padding: 0; height: auto; min-width: 0; }
.bot-name-button strong { display: block; max-width: 160px; }
.bot-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(290px, 1fr)); gap: 16px; }
.bot-heading { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.bot-heading > div { min-width: 0; flex: 1; overflow-wrap: anywhere; }
.bot-description { margin-top: 16px; white-space: pre-wrap; }
.bot-select { width: 170px; }
.invite-form { display: flex; gap: 12px; flex-wrap: wrap; margin-top: 20px; }
.invite-select { width: min(440px, 100%); }
.selected-post { padding: 16px; background: var(--lt-color-bg-layout); border-radius: 8px; }
.selected-post p { margin-bottom: 0; }
.field-error { margin: 8px 0 0; color: var(--lt-color-error, #ff4d4f); }
.activity-heading { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; margin-top: 20px; }
.activity-heading h3 { margin: 0; flex: 1; }
.activity-item { padding: 12px 16px; margin-top: 12px; border: 1px solid var(--lt-color-border); border-radius: 8px; }
.activity-item .activity-heading { margin-top: 0; }
.activity-item p { margin: 8px 0 0; }
.enable-options { display: flex; flex-direction: column; gap: 12px; }
.form-group-title { padding-bottom: 10px; border-bottom: 1px solid var(--lt-color-border); }
.bot-avatar-field { display: flex; align-items: center; gap: 16px; }
.bot-avatar-actions { min-width: 0; }
.bot-avatar-actions p { margin: 6px 0 0; font-size: 12px; }
.preview-loading { padding: 32px; text-align: center; }
.truncate { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.table-summary { max-width: 320px; }
.token-cell { max-width: 160px; }
.detail-comment-link { max-width: min(440px, 65vw); }
.select-option { display: block; }
.memory-summary, .preview-content { white-space: pre-wrap; overflow-wrap: anywhere; }
.preview-content { padding: 16px; background: var(--lt-color-bg-layout); border-radius: 8px; }
.trace-list { padding-left: 20px; }
.trace-list li { margin-bottom: 8px; overflow-wrap: anywhere; }
.trace-list pre { white-space: pre-wrap; }
.comment-body { white-space: pre-wrap; overflow-wrap: anywhere; }
.review-pagination { margin-top: 20px; text-align: right; }
.role-snapshot { margin-top: 16px; }
.role-snapshot summary { cursor: pointer; }
.memory-record-details { font-size: 12px; margin-top: 8px; }
.memory-record-details summary { cursor: pointer; }
.thread-comment { padding: 16px; border: 1px solid var(--lt-color-border); border-radius: 8px; margin-top: 12px; }
.thread-focus { border-color: var(--lt-color-primary); background: var(--lt-color-bg-layout); }
.thread-heading { display: flex; align-items: center; flex-wrap: wrap; gap: 8px; }
@media (max-width: 1300px) {
  .bot-grid { grid-template-columns: repeat(auto-fill, minmax(250px, 1fr)); }
}
@media (max-width: 1100px) { .status-grid { grid-template-columns: repeat(3, minmax(0, 1fr)); } }
@media (max-width: 960px) {
  .console-overview, .roles-layout { grid-template-columns: minmax(0, 1fr); }
  .role-actions-summary { display: block; }
  .role-next-actions { order: -1; }
  .workflow-path { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .decision-summary :deep(.ant-card-body) { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); column-gap: 24px; }
  .decision-summary h3, .decision-summary :deep(.ant-card-body) > p { grid-column: 1 / -1; }
}
@media (max-width: 600px) {
  .community-page { padding: 12px !important; }
  .status-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .status-grid .status-cell:last-child { grid-column: 1 / -1; }
  .role-context { align-items: flex-start; }
  .context-select, .bot-select { width: 100%; }
  .bot-grid { grid-template-columns: minmax(0, 1fr); }
  .workflow-path { column-gap: 12px; row-gap: 20px; }
  .status-cell { padding: 12px; }
}
</style>
