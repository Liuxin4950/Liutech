<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { message } from 'ant-design-vue'
import { PlusOutlined, ReloadOutlined } from '@ant-design/icons-vue'
import { communityService, defaultCommunitySettings } from '@/services/community'
import type { CommunityBot, CommunityBotInput, CommunityKnowledge, CommunityRun, CommunityTask, CommunityMemory, CommunityComment, CommunityCommentThread, CommunityBackfillResult } from '@/services/community'
import PostsService, { type PostListItem } from '@/services/posts'
import CommentsService, { type Comment } from '@/services/comments'
import { formatDateTime } from '@/utils/utils'

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
const recordsLoading = ref(false)
const recordsLoaded = ref(false)
const recordsError = ref('')
const recordsScopeBotId = ref<number>()
const recordsLoadedAt = ref('')
const taskFilter = ref<'all' | 'pending' | 'failed'>('all')
let recordsRequest = 0
const compactLayout = ref(false)
let layoutQuery: MediaQueryList | undefined
const layoutChanged = (event: MediaQueryListEvent) => { compactLayout.value = event.matches }
const memories = ref<CommunityMemory[]>([])
const comments = ref<CommunityComment[]>([])
const commentsPage = ref(1)
const commentsSize = ref(20)
const commentsTotal = ref(0)
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
const pendingTasks = computed(() => tasks.value.filter(task => pendingStatuses.has(task.status)))
const failedTasks = computed(() => tasks.value.filter(task => task.status === 'FAILED'))
const visibleTasks = computed(() => taskFilter.value === 'pending' ? pendingTasks.value : taskFilter.value === 'failed' ? failedTasks.value : tasks.value)
const recordsScope = computed(() => `${recordsScopeBotId.value ? botName(recordsScopeBotId.value) : '所有角色'} · 最近各 100 条运行 / 任务`)
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
    { key: 'FAILED', title: '执行失败', count: counts.FAILED, color: '#ff4d4f' },
  ]
})
const decisionTotal = computed(() => decisionDistribution.value.reduce((sum, entry) => sum + entry.count, 0))
const participationNotice = computed(() => {
  if (!communityEnabled.value) return '全站互动已暂停：预演可用，自动参与与已排队任务等待开启。'
  if (!autoBots.value.length) return '没有可自动参与的角色：需要启用角色并将参与积极度设为大于 0。'
  if (!savedSettings.value.siteDailyTaskLimit || !savedSettings.value.botDailyTaskLimit) return '模型任务额度设置为 0：自动生成已停止，请检查互动设置。'
  if (!savedSettings.value.siteDailyCommentLimit || !savedSettings.value.botDailyCommentLimit || !savedSettings.value.postDailyCommentLimit) return '评论额度设置为 0：无法正式发表，请检查互动设置。'
  return ''
})
const statusLabels: Record<string, string> = {
  SUCCEEDED: '已发言', SKIPPED: '沉默', FAILED: '失败', PREVIEW: '预演', PENDING: '等待中',
  RUNNING: '处理中', CANCELLED: '已取消', COMPLETED: '完成', DONE: '完成', RETRY: '等待重试',
  SKIP: '沉默', COMMENT: '评论文章', REPLY: '回复评论', READY: '等待中', DECIDED: '等待发布', GENERATED: '已生成',
}
const sourceLabels: Record<string, string> = { article: '文章正文', comment: '目标评论', comments: '相关评论', knowledge: '角色资料', 'knowledge-index': '资料索引', memory: '互动记忆' }
const sourceName = (source: string) => sourceLabels[source] || source
const toolInput = (input: unknown) => typeof input === 'string' ? input : JSON.stringify(input, null, 2)
const label = (value?: string) => value ? statusLabels[value] || value : '—'
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
  { title: '状态', key: 'status', width: 110 }, { title: '选择', key: 'decision', width: 110 }, { title: '选择说明', dataIndex: 'reason', key: 'reason', ellipsis: true },
  { title: '用量', key: 'tokens', width: 150 }, { title: '详情', key: 'detail', width: 80 },
]
const taskColumns = [
  { title: '角色', key: 'bot', width: 120 },
  { title: '文章', key: 'post', width: 220 }, { title: '回复对象', key: 'comment', width: 180 },
  { title: '状态', key: 'status', width: 110 }, { title: '尝试次数', dataIndex: 'attempts', key: 'attempts', width: 100 },
  { title: '失败或沉默原因', dataIndex: 'error', key: 'error', ellipsis: true, width: 220 },
  { title: '计划时间', dataIndex: 'availableAt', key: 'availableAt', width: 180 }, { title: '领取到期', dataIndex: 'leaseUntil', key: 'leaseUntil', width: 180 },
  { title: '详情', key: 'detail', width: 80 },
]
const commentColumns = [
  { title: '时间', key: 'createdAt', width: 180 },
  { title: '文章', key: 'post', width: 180 },
  { title: '回复目标', key: 'parent', width: 110 },
  { title: '正文', dataIndex: 'content', key: 'content' },
  { title: '状态', key: 'deleted', width: 100 },
  { title: '对话', key: 'thread', width: 90 },
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
  if (postActivityTimer) clearTimeout(postActivityTimer)
  if (tab === 'preview') {
    if (!postChoices.value.length) void searchPosts('')
    if (postId.value) void loadPostActivity()
  }
})
async function loadRecords(botId?: number) {
  const request = ++recordsRequest
  recordsLoading.value = true
  recordsError.value = ''
  try {
    const [recentRuns, recentTasks] = await Promise.all([communityService.runs(botId), communityService.tasks(botId)])
    if (request !== recordsRequest) return
    runs.value = recentRuns
    tasks.value = recentTasks
    recordsScopeBotId.value = botId
    recordsLoaded.value = true
    recordsLoadedAt.value = new Date().toISOString()
  } catch (error) {
    if (request === recordsRequest) recordsError.value = errorMessage(error, '无法读取运行记录与任务状态')
  } finally { if (request === recordsRequest) recordsLoading.value = false }
}
function navigateRoleTab(tab: string, botId = selectedBotId.value) {
  if (botId) selectedBotId.value = botId
  activeTab.value = tab
  if (['knowledge', 'memory', 'runs', 'comments'].includes(tab)) void refreshSelected()
}
function contextRoleChanged() { if (['knowledge', 'memory', 'runs', 'comments'].includes(activeTab.value)) void refreshSelected() }
function showTaskFilter(filter: 'all' | 'pending' | 'failed') {
  taskFilter.value = filter
  selectedBotId.value = recordsScopeBotId.value
  activeTab.value = 'runs'
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
    const [recentRuns, recentTasks] = await Promise.all([communityService.runs(), communityService.tasks()])
    if (request !== postActivityRequest || postId.value !== id) return
    postRuns.value = recentRuns.filter(run => run.postId === id)
    postTasks.value = recentTasks.filter(task => task.postId === id)
    if (activeTab.value === 'preview' && postTasks.value.some(task => ['PENDING', 'RUNNING', 'RETRY', 'READY', 'DECIDED', 'GENERATED'].includes(task.status))) {
      postActivityTimer = setTimeout(() => { void loadPostActivity() }, 5000)
    }
  } catch (error) {
    if (request === postActivityRequest && postId.value === id) postActivityError.value = errorMessage(error, '加载文章任务失败')
  } finally { if (request === postActivityRequest) postActivityLoading.value = false }
}
onBeforeUnmount(() => {
  resetPreview()
  ++postSearchRequest
  ++postCommentsRequest
  ++postActivityRequest
  ++postStateRequest
  ++recordsRequest
  if (postSearchTimer) clearTimeout(postSearchTimer)
  if (postActivityTimer) clearTimeout(postActivityTimer)
  layoutQuery?.removeEventListener('change', layoutChanged)
})
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
    if (activeTab.value !== initialTab && ['knowledge', 'memory', 'runs', 'comments'].includes(activeTab.value)) void refreshSelected()
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
  } catch (error) { loadError.value = errorMessage(error, '加载社区 AI 设置失败') }
  finally { loading.value = false }
}
function openBot(bot?: CommunityBot) {
  editingBotId.value = bot?.id
  botForm.value = bot ? { name: bot.name, avatarUrl: bot.avatarUrl || '', personality: bot.personality || '', background: bot.background || '', systemPrompt: bot.systemPrompt || '', interests: bot.interests || '', participation: bot.participation, enabled: bot.enabled } : emptyBot()
  botModal.value = true
}
async function saveBot() {
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
  try { backfillResult.value = await communityService.backfill(10) }
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
  } else if (activeTab.value === 'runs') {
    await loadRecords(id)
  } else if (activeTab.value === 'comments') {
    comments.value = []
    commentsTotal.value = 0
    const page = commentsPage.value
    const size = commentsSize.value
    if (id) {
      const data = await communityService.comments(id, page, size)
      if (selectedBotId.value === id && commentsPage.value === page && commentsSize.value === size) {
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
  if (!validPost()) return
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
  if (!communityEnabled.value) { message.warning('请先在互动设置中开启全站自动互动'); return }
  if (inviteIds.value.length > 2) { message.warning('每次最多邀请两个角色'); return }
  if (inviteLoading.value || busy.value) return
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
      : '本次未创建任务：没有可参与的角色。请启用角色，或手动选择希望邀请的角色。'
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
      for (const record of [...tasks.value, ...postTasks.value]) if (String(record.id) === id) record.status = 'READY'
    }
    if (activeTab.value === 'runs') {
      try { await loadSelected() }
      catch (error) { message.error(`重试状态已返回，但刷新记录失败：${errorMessage(error)}`) }
    }
    if (postId.value === task.postId) await loadPostActivity()
    if (String(taskDetail.value?.id) === id) {
      const source = activeTab.value === 'runs' ? tasks.value : postTasks.value
      taskDetail.value = source.find(item => String(item.id) === id) || (result.queued ? { ...task, status: 'READY' } : task)
    }
  } catch (error) {
    retryNotices.value[id] = { queued: false, message: errorMessage(error, '重试失败'), previousError }
  } finally { retryingTaskIds.value = retryingTaskIds.value.filter(item => item !== id) }
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
  layoutQuery = window.matchMedia('(max-width: 960px)')
  compactLayout.value = layoutQuery.matches
  layoutQuery.addEventListener('change', layoutChanged)
  void load()
})
</script>

<template>
  <div class="p-24 community-page">
    <a-card :bordered="false" class="mb-16">
      <div class="page-title">
        <div>
          <h2>社区 AI 角色</h2>
          <p>配置角色，先用文章预演，再允许参与；已发表的评论、回复与失败任务都可以在这里审查。</p>
        </div>
        <a-space>
          <a-tag :color="communityEnabled ? 'green' : 'default'">{{ !configurationLoaded ? loading ? '读取配置中' : '配置未加载' : communityEnabled ? '互动已开启' : '互动已暂停' }}</a-tag>
          <a-button danger :disabled="!communityEnabled || busy" @click="pauseAll">暂停全部</a-button>
          <a-button :loading="loading" :disabled="busy" @click="load">
            <ReloadOutlined />刷新</a-button>
        </a-space>
      </div>
    </a-card>
    <a-alert v-if="loadError" type="error" :message="loadError" show-icon class="mb-16"><template #action><a-button :loading="loading" @click="load">重新加载</a-button></template></a-alert>
    <div class="console-overview mb-16">
      <a-card :bordered="false" class="overview-summary">
        <div class="overview-heading"><h3>当前状态</h3><span class="muted">{{ recordsLoaded ? recordsScope : '运行记录尚未加载' }}</span><a-button size="small" :loading="recordsLoading" @click="loadRecords(recordsScopeBotId)">刷新状态</a-button></div>
        <div class="status-grid">
          <div class="status-cell"><span class="status-label">自动参与角色</span><strong>{{ configurationLoaded ? autoBots.length : '—' }}<small v-if="configurationLoaded"> / {{ bots.length }}</small></strong><span class="muted">已启用且积极度大于 0</span></div>
          <div class="status-cell"><span class="status-label">全站互动</span><strong class="status-word" :class="{ 'status-active': communityEnabled }">{{ !configurationLoaded ? '未读取' : communityEnabled ? '已开启' : '已暂停' }}</strong><a-button type="link" size="small" @click="activeTab = 'settings'">调整开关与额度</a-button></div>
          <button class="status-cell status-button" :disabled="!recordsLoaded" @click="showTaskFilter('pending')"><span class="status-label">待处理任务</span><strong>{{ recordsLoaded ? pendingTasks.length : '—' }}</strong><span class="muted">当前查询，含等待与处理中</span></button>
          <button class="status-cell status-button" :disabled="!recordsLoaded" @click="showTaskFilter('failed')"><span class="status-label">失败任务</span><strong :class="{ 'status-failed': failedTasks.length }">{{ recordsLoaded ? failedTasks.length : '—' }}</strong><span class="muted">查看原因并单独重试</span></button>
          <div class="status-cell"><span class="status-label">已知 token 合计</span><strong>{{ recordsLoaded ? usageSummary.knownTokens.toLocaleString() : '—' }}</strong><span class="muted">当前 {{ runs.length }} 条运行，包含预演</span></div>
        </div>
        <p v-if="recordsLoaded" class="metric-note muted">卡片合计仅包含带用量标记的记录。用量缺失 {{ usageSummary.missing }} 条 · 部分提供 {{ usageSummary.partial }} 条 · 未调用模型 {{ usageSummary.notCalled }} 条<span v-if="usageSummary.legacy"> · 另有 {{ usageSummary.legacy }} 条旧记录提供 {{ usageSummary.legacyKnownTokens.toLocaleString() }} token，完整性未标注</span>。最近更新 {{ formatDateTime(recordsLoadedAt) }}。这里统计已加载记录，不代表全站累计或每日账单。</p>
        <a-alert v-if="configurationLoaded && participationNotice" type="info" :message="participationNotice" show-icon class="mt-16"><template #action><a-button size="small" @click="activeTab = 'settings'">检查设置</a-button></template></a-alert>
        <a-alert v-if="recordsError" type="error" :message="recordsError" :description="recordsLoaded ? '以下统计保留上次成功查询结果，请重新刷新。' : '暂时无法确认任务与模型用量。'" show-icon class="mt-16" />
        <a-alert v-if="failedTasks.length" type="warning" :message="`当前查询有 ${failedTasks.length} 个失败任务`" description="查看具体失败原因后可重试同一个任务。" show-icon class="mt-16"><template #action><a-button size="small" @click="showTaskFilter('failed')">查看并重试</a-button></template></a-alert>
      </a-card>
      <a-card :bordered="false" class="decision-summary">
        <h3>最近决策分布</h3>
        <p class="muted">当前查询的正式运行，排除预演；统计决策与失败，不等同于已发表评论数量。</p>
        <div v-for="entry in decisionDistribution" :key="entry.key" class="decision-row"><div class="decision-heading"><span>{{ entry.title }}</span><strong>{{ recordsLoaded ? entry.count : '—' }}</strong></div><div class="decision-track"><div class="decision-bar" :style="{ width: `${decisionTotal ? entry.count / decisionTotal * 100 : 0}%`, background: entry.color }" /></div></div>
        <p class="metric-note muted">{{ recordsLoaded ? `共 ${decisionTotal} 条可归类记录` : '等待服务返回运行记录' }}</p>
      </a-card>
    </div>
    <a-card :bordered="false" class="mb-16 workflow-card">
      <div class="workflow-path">
        <div class="workflow-step"><span class="step-number">1</span><div><a-button type="link" @click="activeTab = 'roles'">配置角色</a-button><p class="muted">身份、性格和兴趣</p></div></div>
        <div class="workflow-step"><span class="step-number">2</span><div><a-space :size="0"><a-button type="link" :disabled="!selectedBotId" @click="navigateRoleTab('knowledge')">准备资料</a-button><a-button type="link" :disabled="!selectedBotId" @click="navigateRoleTab('preview')">预演</a-button></a-space><p class="muted">先确认生成结果</p></div></div>
        <div class="workflow-step"><span class="step-number">3</span><div><a-button type="link" @click="activeTab = 'settings'">允许参与</a-button><p class="muted">全站开关、额度与旧文补评</p></div></div>
        <div class="workflow-step"><span class="step-number">4</span><div><a-button type="link" :disabled="!selectedBotId" @click="navigateRoleTab('comments')">审查讨论</a-button><p class="muted">实际发言、回复和运行结果</p></div></div>
      </div>
      <div class="role-context"><span>当前操作角色</span><a-select v-model:value="selectedBotId" :disabled="busy" :options="botOptions" show-search allow-clear option-filter-prop="label" placeholder="选择角色后准备资料、预演和审查" class="context-select" @change="contextRoleChanged" /><template v-if="selectedBot"><a-tag :color="selectedBot.enabled ? 'green' : 'default'">{{ selectedBot.enabled ? '角色已启用' : '角色已暂停，仍可预演' }}</a-tag><a-button size="small" :disabled="busy" @click="openBot(selectedBot)">编辑角色</a-button></template><span v-else class="muted">请选择一个角色以继续资料、预演或讨论审查。</span></div>
    </a-card>
    <a-tabs v-model:activeKey="activeTab" @change="tabChanged">
      <a-tab-pane key="roles" tab="角色">
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
      <a-tab-pane key="knowledge" tab="角色资料">
        <a-card :bordered="false" title="独立资料库">
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
      <a-tab-pane key="preview" tab="预演与邀请">
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
          <h3>文章互动</h3>
          <a-space wrap class="mb-16">
            <a-button :loading="postStateLoading" :disabled="!postId" @click="loadPostState">刷新文章状态</a-button>
            <a-tag v-if="postEnabled !== undefined" :color="postEnabled ? 'green' : 'default'">{{ postEnabled ? '该文章互动已开启' : '该文章互动已关闭' }}</a-tag>
          </a-space>
          <a-alert v-if="postStateError" type="error" :message="postStateError" show-icon class="mb-16" />
          <a-alert v-if="!communityEnabled" type="warning" message="全站互动已暂停。预演仍可使用；正式邀请前请到互动设置开启全站互动。" show-icon class="mb-16" />
          <p class="muted">对上方选中的文章立即发起邀请，已发布文章也可参与。角色会读取内容后决定发言或沉默，等待和额度规则仍然生效。</p>
          <a-space wrap>
            <a-button :disabled="!postId || busy || inviteLoading" @click="setPost(true)">开启该文章互动</a-button>
            <a-button :disabled="!postId || busy || inviteLoading" @click="setPost(false)">关闭该文章互动</a-button>
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
            <a-empty v-if="!postTasks.length && !postRuns.length && !postActivityLoading" description="暂无邀请任务或自动参与记录" />
            <div v-for="task in postTasks" :key="task.id" class="activity-item">
              <div class="activity-heading"><strong>{{ botName(task.botId) }}</strong><a-tag>{{ label(task.status) }}</a-tag><a-button type="link" size="small" @click="taskDetail = task; taskDetailOpen = true">任务详情</a-button><a-tooltip v-if="task.status === 'FAILED'" :title="communityEnabled ? '重新处理这个失败任务' : '全站互动已暂停，重试入队后需要开启才会执行'"><a-button type="link" size="small" :loading="retryingTaskIds.includes(String(task.id))" @click="retryTask(task)">重试</a-button></a-tooltip></div>
              <p v-if="task.availableAt" class="muted">计划处理：{{ formatDateTime(task.availableAt) }}</p>
              <p v-if="task.error || retryNotices[String(task.id)]?.previousError" class="comment-body">{{ task.error || retryNotices[String(task.id)]?.previousError }}</p>
              <p v-else-if="postRuns.find(run => String(run.taskId) === String(task.id))?.reason" class="comment-body">{{ postRuns.find(run => String(run.taskId) === String(task.id))?.reason }}</p>
              <a-alert v-if="retryNotices[String(task.id)]" class="mt-16" :type="retryNotices[String(task.id)].queued ? 'info' : 'error'" :message="retryNotices[String(task.id)].message" show-icon />
            </div>
            <div v-for="run in postRuns" :key="run.id || run.taskId" class="activity-item">
              <div class="activity-heading"><strong>{{ run.roleSnapshot?.name || botName(run.botId) }}</strong><a-tag>{{ label(run.status || run.decision) }}</a-tag><a-tag v-if="run.decision && run.status">{{ label(run.decision) }}</a-tag><a-button type="link" size="small" @click="showRun(run)">查看生成结果</a-button></div>
              <p class="comment-body">{{ run.error || run.reason || '服务未提供决策说明' }}</p>
              <a-tooltip v-if="run.content" :title="run.content"><p class="truncate">{{ run.content }}</p></a-tooltip>
            </div>
          </template>
        </a-card>
      </a-tab-pane>
      <a-tab-pane key="runs" tab="运行记录">
        <a-card :bordered="false" title="运行记录与任务处理">
          <template #extra>
            <a-space>
              <a-select :disabled="busy" v-model:value="selectedBotId" :options="botOptions" allow-clear placeholder="所有角色" class="bot-select" @change="refreshSelected" />
              <a-button :loading="busy || recordsLoading" @click="refreshSelected">刷新</a-button>
            </a-space>
          </template>
          <p class="muted">查询范围：{{ recordsScope }}。选择说明是服务返回的公开选择理由，预演和正式参与都会留下运行记录。</p>
          <a-alert v-if="recordsError" type="error" :message="recordsError" class="mb-16" show-icon />
          <a-table :columns="runColumns" :data-source="runs" :loading="busy || recordsLoading" :row-key="(run: CommunityRun) => run.id || run.taskId" :scroll="{ x: 1100 }">
            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'bot'">{{ botName(record.botId) }}</template>
              <template v-else-if="column.key === 'createdAt'">{{ formatDateTime(record.createdAt) }}</template>
              <template v-else-if="column.key === 'post'"><a-tooltip :title="postTitle(record.postId, record.postTitle)"><div class="truncate table-summary">{{ postTitle(record.postId, record.postTitle) }}</div></a-tooltip></template>
              <template v-else-if="column.key === 'reason'"><a-tooltip :title="record.reason"><div class="truncate table-summary">{{ record.reason || '—' }}</div></a-tooltip></template>
              <template v-else-if="column.key === 'status'">
                <a-tag>{{ label(record.status || record.decision) }}</a-tag>
              </template>
              <template v-else-if="column.key === 'decision'"><a-tag>{{ label(record.decision) }}</a-tag></template>
              <template v-else-if="column.key === 'tokens'"><a-tooltip :title="`${tokenUsage(record)}。输入 / 输出 token，${record.modelRounds ?? '未知'} 次模型调用；仅展示供应商返回的用量`"><div class="truncate token-cell">{{ tokenUsage(record) }}</div></a-tooltip></template>
              <template v-else-if="column.key === 'detail'">
                <a-button type="link" @click="showRun(record)">查看</a-button>
              </template>
            </template>
          </a-table>
          <div class="activity-heading"><h3>任务队列</h3><a-radio-group v-model:value="taskFilter" button-style="solid" size="small"><a-radio-button value="all">全部 {{ tasks.length }}</a-radio-button><a-radio-button value="pending">待处理 {{ pendingTasks.length }}</a-radio-button><a-radio-button value="failed">失败 {{ failedTasks.length }}</a-radio-button></a-radio-group></div>
          <p class="muted">生成记录展示角色的决策；任务标记“已发言”表示评论已正式发布。失败任务可单独重试；全站互动暂停时，入队任务会等待开启后执行。</p>
          <a-table :columns="taskColumns" :data-source="visibleTasks" :loading="busy || recordsLoading" :row-key="(task: CommunityTask) => task.id" :scroll="{ x: 1300 }">
            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'bot'">{{ botName(record.botId) }}</template>
              <template v-else-if="column.key === 'status'">{{ label(record.status) }}</template>
              <template v-else-if="column.key === 'post'"><a-tooltip :title="postTitle(record.postId, record.postTitle)"><div class="truncate table-summary">{{ postTitle(record.postId, record.postTitle) }}</div></a-tooltip></template>
              <template v-else-if="column.key === 'comment'"><a-tooltip :title="record.commentPreview"><div class="truncate table-summary">{{ record.commentId ? record.commentPreview || '评论内容暂不可用' : '评价文章' }}</div></a-tooltip></template>
              <template v-else-if="column.key === 'error'"><a-tooltip :title="record.error || retryNotices[String(record.id)]?.previousError"><div class="truncate table-summary">{{ record.error || retryNotices[String(record.id)]?.previousError || '—' }}</div></a-tooltip><a-tooltip v-if="retryNotices[String(record.id)]" :title="retryNotices[String(record.id)].message"><div class="truncate table-summary" :class="{ 'field-error': !retryNotices[String(record.id)].queued }">{{ retryNotices[String(record.id)].message }}</div></a-tooltip></template>
              <template v-else-if="column.key === 'detail'"><a-space direction="vertical" :size="0"><a-button type="link" @click="taskDetail = record; taskDetailOpen = true">查看</a-button><a-tooltip v-if="record.status === 'FAILED'" :title="communityEnabled ? '重新处理这个失败任务' : '全站互动已暂停，重试入队后需要开启才会执行'"><a-button type="link" :loading="retryingTaskIds.includes(String(record.id))" @click="retryTask(record)">重试</a-button></a-tooltip></a-space></template>
            </template>
          </a-table>
        </a-card>
      </a-tab-pane>
      <a-tab-pane key="comments" tab="评论与对话">
        <a-card :bordered="false" title="已发布评论与对话审查">
          <template #extra>
            <a-space>
              <a-select :disabled="busy" v-model:value="selectedBotId" :options="botOptions" placeholder="选择角色" class="bot-select" @change="commentsBotChanged" />
              <a-button :disabled="!selectedBotId" :loading="busy" @click="refreshSelected">刷新</a-button>
            </a-space>
          </template>
          <p class="muted">查看角色实际发布的评论，包含已删除记录。打开对话可按时间查看同一线程的真人及 AI 发言。</p>
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
                  <a-tag :color="record.deletedAt ? 'red' : 'green'">{{ record.deletedAt ? '已删除' : '已发布' }}</a-tag>
                </template>
                <template v-else-if="column.key === 'thread'">
                  <a-button type="link" @click="openThread(record.id)">查看对话</a-button>
                </template>
              </template>
            </a-table>
            <a-pagination class="review-pagination" :current="commentsPage" :page-size="commentsSize" :total="commentsTotal" :disabled="busy" :show-size-changer="true" :page-size-options="['10', '20', '50']" :show-total="(total: number) => `共 ${total} 条评论`" @change="commentsPageChanged" />
          </template>
        </a-card>
      </a-tab-pane>
      <a-tab-pane key="memory" tab="公共互动记忆">
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
      <a-tab-pane key="settings" tab="互动设置">
        <a-card :bordered="false" title="自动参与与额度">
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
            <a-alert v-if="backfillResult" class="mb-16" type="success" show-icon :message="`已检查 ${backfillResult.postCount} 篇文章，新增 ${backfillResult.queued} 个初评任务，本次未安排 ${backfillResult.skipped} 个角色与文章组合。`" description="加入队列不代表已经发表评论，请在运行记录查看处理状态、沉默原因或失败详情。" />
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
    <a-modal v-model:open="botModal" :title="editingBotId ? '编辑角色' : '创建角色'" :confirm-loading="busy" width="min(1100px, 92vw)" :body-style="{ maxHeight: '72vh', overflowY: 'auto' }" @ok="saveBot">
      <a-form layout="vertical">
        <a-row :gutter="24">
          <a-col :xs="24" :lg="12">
            <h3 class="form-group-title">身份与背景</h3>
            <a-form-item label="角色名称" required><a-input v-model:value="botForm.name" :maxlength="80" placeholder="读者看到的角色名称" /></a-form-item>
            <a-form-item label="头像地址"><a-input v-model:value="botForm.avatarUrl" :maxlength="1000" placeholder="图片地址" /></a-form-item>
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
    <a-modal v-model:open="detailOpen" :title="detailMode === 'preview' ? '预演结果（不会发布）' : '角色决策详情'" width="min(1100px, 92vw)" :body-style="{ maxHeight: '72vh', overflowY: 'auto' }" :footer="null">
      <div v-if="previewLoading" class="preview-loading"><a-spin /><p>预演请求处理中，等待服务返回角色决策与生成结果…</p><p class="muted">模型响应可能需要一些时间。关闭窗口或更换角色、文章会取消本次等待。</p></div>
      <a-alert v-if="previewError" type="error" :message="previewError" show-icon class="mb-16" />
      <a-button v-if="previewError && detailMode === 'preview'" type="primary" class="mb-16" :loading="previewLoading" @click="preview">重新预演</a-button>
      <template v-if="detail">
        <a-alert v-if="detailMode === 'preview' && !previewError" type="success" show-icon class="mb-16" :message="detail.decision === 'SKIP' ? '预演已完成：角色决定保持沉默' : '预演已完成：生成结果仅展示在此窗口'" :description="detail.reason || '服务未提供决策原因'" />
        <a-descriptions :column="{ xs: 1, sm: 2 }" bordered>
          <a-descriptions-item label="角色">{{ detail.roleSnapshot?.name || botName(detail.botId) }}</a-descriptions-item>
          <a-descriptions-item label="文章"><a-tooltip :title="postTitle(detail.postId, detail.postTitle)"><div class="truncate">{{ postTitle(detail.postId, detail.postTitle) }}</div></a-tooltip></a-descriptions-item>
          <a-descriptions-item label="决策">{{ label(detail.decision) }}</a-descriptions-item>
          <a-descriptions-item v-if="detail.publishedCommentId" label="已发布评论" :span="2"><a-button type="link" @click="openThread(detail.publishedCommentId!, detail.botId)">查看已发布评论与对话</a-button></a-descriptions-item>
          <a-descriptions-item label="回复目标"><a-tooltip v-if="detail.targetCommentId" :title="detail.targetCommentPreview || '打开对话查看完整评论'"><a-button type="link" class="truncate detail-comment-link" @click="openThread(detail.targetCommentId!, detail.botId)">{{ detail.targetAuthorName ? detail.targetAuthorName + '：' : '' }}{{ detail.targetCommentPreview || '查看目标评论' }}</a-button></a-tooltip><span v-else>文章</span></a-descriptions-item>
          <a-descriptions-item label="选择说明" :span="2">{{ detail.reason || '—' }}</a-descriptions-item>
          <a-descriptions-item label="模型">{{ detail.model || '—' }}</a-descriptions-item>
          <a-descriptions-item label="输入 / 输出 token">{{ tokenUsage(detail) }}</a-descriptions-item>
          <a-descriptions-item label="模型调用次数">{{ detail.modelRounds ?? '未记录' }}</a-descriptions-item>
        </a-descriptions>
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
          <li v-for="(trace, index) in detail.readTrace" :key="index">{{ sourceName(trace.source) }} #{{ trace.id }}：{{ trace.start ?? 0 }}–{{ trace.end ?? '—' }} / {{ trace.total ?? '—' }} <a-tag v-if="trace.truncated" color="orange">已截取</a-tag>
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
      <a-alert v-if="taskDetail && retryNotices[String(taskDetail.id)]" class="mb-16" :type="retryNotices[String(taskDetail.id)].queued ? 'info' : 'error'" :message="retryNotices[String(taskDetail.id)].message" show-icon />
      <a-descriptions v-if="taskDetail" :column="{ xs: 1, sm: 2 }" bordered>
        <a-descriptions-item label="角色">{{ botName(taskDetail.botId) }}</a-descriptions-item>
        <a-descriptions-item label="状态">{{ label(taskDetail.status) }}</a-descriptions-item>
        <a-descriptions-item label="文章" :span="2">{{ postTitle(taskDetail.postId, taskDetail.postTitle) }}</a-descriptions-item>
        <a-descriptions-item label="回复对象" :span="2"><div class="comment-body">{{ taskDetail.commentId ? taskDetail.commentPreview || '评论内容暂不可用' : '评价文章' }}</div></a-descriptions-item>
        <a-descriptions-item label="计划处理">{{ formatDateTime(taskDetail.availableAt) }}</a-descriptions-item>
        <a-descriptions-item label="尝试 / 失败次数">{{ taskDetail.attempts }} / {{ taskDetail.failures ?? '未记录' }}</a-descriptions-item>
        <a-descriptions-item label="失败或沉默原因" :span="2">{{ taskDetail.error || retryNotices[String(taskDetail.id)]?.previousError || '未提供' }}</a-descriptions-item>
        <a-descriptions-item label="任务编号">{{ taskDetail.id }}</a-descriptions-item>
        <a-descriptions-item label="领取到期">{{ formatDateTime(taskDetail.leaseUntil) }}</a-descriptions-item>
      </a-descriptions>
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
              <a-tag v-if="comment.deletedAt" color="red">已删除</a-tag>
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
.page-title h2 { margin: 0 0 8px; }
.page-title p, .muted { color: var(--lt-color-text-secondary); }
.console-overview { display: grid; grid-template-columns: minmax(0, 1fr) 300px; gap: 16px; align-items: stretch; }
.overview-summary :deep(.ant-card-body), .decision-summary :deep(.ant-card-body), .workflow-card :deep(.ant-card-body) { padding: 18px; }
.overview-heading { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; margin-bottom: 20px; }
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
.status-active { color: #389e0d; }
.status-failed { color: #cf1322; }
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
.role-context { display: flex; align-items: center; flex-wrap: wrap; gap: 12px; margin-top: 20px; padding-top: 18px; border-top: 1px solid var(--lt-color-border); }
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
