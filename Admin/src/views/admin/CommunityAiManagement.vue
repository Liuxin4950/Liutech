<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { message } from 'ant-design-vue'
import { PlusOutlined, ReloadOutlined } from '@ant-design/icons-vue'
import { communityService, defaultCommunitySettings } from '@/services/community'
import type { CommunityBot, CommunityBotInput, CommunityKnowledge, CommunityRun, CommunityTask, CommunityMemory, CommunityComment, CommunityCommentThread } from '@/services/community'
import { formatDateTime } from '@/utils/utils'

const bots = ref<CommunityBot[]>([])
const settings = ref(defaultCommunitySettings())
const communityEnabled = ref(false)
const savedSettings = ref(defaultCommunitySettings())
const loading = ref(false)
const busy = ref(false)
const selectedBotId = ref<number>()
const activeTab = ref('roles')
const knowledge = ref<CommunityKnowledge[]>([])
const runs = ref<CommunityRun[]>([])
const tasks = ref<CommunityTask[]>([])
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
watch(postId, () => { postEnabled.value = undefined })
const commentId = ref<number>()
const inviteIds = ref<number[]>([])
const botOptions = computed(() => bots.value.map(bot => ({ value: bot.id, label: bot.name })))
const enabledBotOptions = computed(() => bots.value.filter(bot => bot.enabled).map(bot => ({ value: bot.id, label: bot.name, disabled: inviteIds.value.length >= 2 && !inviteIds.value.includes(bot.id) })))
const selectedBot = computed(() => bots.value.find(bot => bot.id === selectedBotId.value))
const statusLabels: Record<string, string> = {
  SUCCEEDED: '已发言', SKIPPED: '沉默', FAILED: '失败', PREVIEW: '预演', PENDING: '等待中',
  RUNNING: '处理中', CANCELLED: '已取消', COMPLETED: '完成', DONE: '完成', RETRY: '等待重试',
  SKIP: '沉默', COMMENT: '评论文章', REPLY: '回复评论', READY: '等待中', DECIDED: '等待发布', GENERATED: '已生成',
}
const sourceLabels: Record<string, string> = { article: '文章正文', comment: '目标评论', comments: '相关评论', knowledge: '角色资料', 'knowledge-index': '资料索引', memory: '互动记忆' }
const sourceName = (source: string) => sourceLabels[source] || source
const toolInput = (input: unknown) => typeof input === 'string' ? input : JSON.stringify(input, null, 2)
const label = (value?: string) => value ? statusLabels[value] || value : '—'
const botName = (id: number) => bots.value.find(bot => bot.id === id)?.name || `角色 #${id}`
const runColumns = [
  { title: '时间', dataIndex: 'createdAt', key: 'createdAt', width: 180 },
  { title: '角色', key: 'bot', width: 120 }, { title: '文章', dataIndex: 'postId', key: 'postId', width: 90 },
  { title: '状态', key: 'status', width: 110 }, { title: '决策原因', dataIndex: 'reason', key: 'reason', ellipsis: true },
  { title: '用量', key: 'tokens', width: 150 }, { title: '详情', key: 'detail', width: 80 },
]
const taskColumns = [
  { title: '任务', dataIndex: 'id', key: 'id', width: 110 }, { title: '角色', key: 'bot', width: 120 },
  { title: '文章', dataIndex: 'postId', key: 'postId', width: 90 }, { title: '评论', dataIndex: 'commentId', key: 'commentId', width: 90 },
  { title: '状态', key: 'status', width: 110 }, { title: '尝试次数', dataIndex: 'attempts', key: 'attempts', width: 100 },
  { title: '失败或沉默原因', dataIndex: 'error', key: 'error', ellipsis: true, width: 220 },
  { title: '计划时间', dataIndex: 'availableAt', key: 'availableAt', width: 180 }, { title: '领取到期', dataIndex: 'leaseUntil', key: 'leaseUntil', width: 180 },
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
  try {
    const result = await Promise.all([communityService.bots(), communityService.settings()])
    bots.value = result[0]
    settings.value = { ...defaultCommunitySettings(), ...result[1] }
    communityEnabled.value = settings.value.enabled
    savedSettings.value = { ...settings.value }
    if (!bots.value.some(bot => bot.id === selectedBotId.value)) selectedBotId.value = bots.value[0]?.id
  } catch (error: any) { if (!error?.isBusiness) message.error('加载社区 AI 设置失败') }
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
    await load()
    selectedBotId.value = saved.id
  }, '角色已保存')
}
async function toggleBot(bot: CommunityBot) {
  await action(async () => { await communityService.updateBot(bot.id, { name: bot.name, avatarUrl: bot.avatarUrl, personality: bot.personality, background: bot.background, systemPrompt: bot.systemPrompt || '', interests: bot.interests, participation: bot.participation, enabled: !bot.enabled }); await load() }, bot.enabled ? '角色已暂停' : communityEnabled.value ? '角色已启用' : '角色已启用，评论互动目前暂停')
}
async function removeBot(botId: number) {
  await action(async () => { await communityService.deleteBot(botId); await load(); knowledge.value = []; memories.value = [] }, '角色已删除')
}
async function saveSettings() {
  if (settings.value.minDelaySeconds > settings.value.maxDelaySeconds) { message.warning('最短等待不能大于最长等待'); return }
  await action(async () => { settings.value = await communityService.saveSettings(settings.value); communityEnabled.value = settings.value.enabled; savedSettings.value = { ...settings.value } }, '设置已保存')
}
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
    const data = await Promise.all([communityService.runs(id), communityService.tasks(id)])
    if (selectedBotId.value === id) { runs.value = data[0]; tasks.value = data[1] }
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
function validPost() { if (!postId.value || !Number.isSafeInteger(postId.value)) { message.warning('请输入文章 ID'); return false }; return true }
async function preview() {
  if (!validPost() || !selectedBotId.value) { if (!selectedBotId.value) message.warning('请选择角色'); return }
  await action(async () => {
    detail.value = await communityService.preview({ botId: selectedBotId.value!, postId: postId.value!, commentId: commentId.value || undefined })
    detailOpen.value = true
  })
}
async function loadPostState() {
  if (!validPost()) return
  const id = postId.value!
  await action(async () => {
    const state = await communityService.postEnabled(id)
    if (postId.value === id) postEnabled.value = state.enabled
  })
}
async function setPost(enabled: boolean) {
  if (!validPost()) return
  await action(async () => { const id = postId.value!; await communityService.setPostEnabled(id, enabled); if (postId.value === id) postEnabled.value = enabled }, enabled ? '文章互动已开启' : '文章互动已关闭')
}
async function invite() {
  if (!validPost()) return
  if (inviteIds.value.length > 2) { message.warning('每次最多邀请两个角色'); return }
  await action(async () => { await communityService.invite(postId.value!, inviteIds.value) }, '邀请已加入队列，请在运行记录查看结果')
}
async function clearMemory() {
  if (!selectedBotId.value) return
  await action(async () => { await communityService.clearMemory(selectedBotId.value!); await loadSelected() }, '角色记忆已清空')
}
function showRun(run: CommunityRun) { detail.value = run; detailOpen.value = true }
function reviewBot(botId: number) { selectedBotId.value = botId; activeTab.value = 'comments'; commentsPage.value = 1; void refreshSelected() }
function commentsBotChanged() { commentsPage.value = 1; void refreshSelected() }
function commentsPageChanged(page: number, size: number) { commentsPage.value = page; commentsSize.value = size; void refreshSelected() }
function authorName(comment: CommunityComment) {
  return comment.authorType === 'BOT' ? comment.bot?.name || `角色 #${comment.botId}` : comment.user?.username || `用户 #${comment.userId || '已删除'}`
}
function replyTarget(comment: CommunityComment) {
  if (!comment.parentId) return '文章'
  const parent = thread.value?.comments.find(item => item.id === comment.parentId)
  return parent ? `${authorName(parent)} · #${parent.id}` : `评论 #${comment.parentId}`
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
onMounted(load)
</script>

<template>
  <div class="p-24 community-page">
    <a-card :bordered="false" class="mb-16">
      <div class="page-title">
        <div>
          <h2>社区 AI 角色</h2>
          <p>管理角色身份、提示词与资料，启用后参与文章和评论讨论，发言与对话可在此审查。</p>
        </div>
        <a-space>
          <a-tag :color="communityEnabled ? 'green' : 'default'">{{ communityEnabled ? '互动已开启' : '互动已暂停' }}</a-tag>
          <a-button danger :disabled="!communityEnabled || busy" @click="pauseAll">暂停全部</a-button>
          <a-button :loading="loading" :disabled="busy" @click="load">
            <ReloadOutlined />刷新</a-button>
        </a-space>
      </div>
    </a-card>
    <a-tabs v-model:activeKey="activeTab" @change="tabChanged">
      <a-tab-pane key="roles" tab="角色">
        <a-card :bordered="false" title="角色列表">
          <template #extra>
            <a-button type="primary" :disabled="busy" @click="openBot()">
              <PlusOutlined />创建角色</a-button>
          </template>
          <a-empty v-if="!bots.length && !loading" description="创建第一个角色开始预演" />
          <div class="bot-grid">
            <a-card v-for="bot in bots" :key="bot.id" size="small">
              <div class="bot-heading">
                <a-avatar :src="bot.avatarUrl">{{ bot.name.charAt(0) }}</a-avatar>
                <strong>{{ bot.name }}</strong>
                <a-tag :color="bot.enabled ? 'green' : 'default'">{{ bot.enabled ? '启用' : '暂停' }}</a-tag>
              </div>
              <p class="bot-description">{{ bot.personality }}</p>
              <p class="muted">兴趣：{{ bot.interests || '不限' }} · 积极度 {{ bot.participation }}%</p>
              <a-space wrap>
                <a-button size="small" :disabled="busy" @click="openBot(bot)">编辑</a-button>
                <a-button size="small" :disabled="busy" @click="toggleBot(bot)">{{ bot.enabled ? '暂停' : '启用' }}</a-button>
                <a-button size="small" @click="selectedBotId = bot.id; activeTab = 'knowledge'; refreshSelected()">资料</a-button>
                <a-button size="small" @click="reviewBot(bot.id)">评论与对话</a-button>
                <a-popconfirm title="删除角色后将停止其发言，确定删除？" @confirm="removeBot(bot.id)">
                  <a-button size="small" danger :disabled="busy">删除</a-button>
                </a-popconfirm>
              </a-space>
            </a-card>
          </div>
        </a-card>
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
          <a-form layout="vertical">
            <a-row :gutter="16">
              <a-col :xs="24" :sm="8">
                <a-form-item label="角色">
                  <a-select :disabled="busy" v-model:value="selectedBotId" :options="botOptions" placeholder="选择角色" />
                </a-form-item>
              </a-col>
              <a-col :xs="24" :sm="8">
                <a-form-item label="文章 ID">
                  <a-input-number v-model:value="postId" :min="1" :precision="0" style="width:100%" />
                </a-form-item>
              </a-col>
              <a-col :xs="24" :sm="8">
                <a-form-item label="评论 ID（可选）">
                  <a-input-number v-model:value="commentId" :min="1" :precision="0" style="width:100%" />
                </a-form-item>
              </a-col>
            </a-row>
            <a-button type="primary" :loading="busy" @click="preview">预演，不发布</a-button>
          </a-form>
          <a-divider />
          <h3>文章互动</h3>
          <a-space class="mb-16">
            <a-button :disabled="busy" @click="loadPostState">查看当前状态</a-button>
            <a-tag v-if="postEnabled !== undefined" :color="postEnabled ? 'green' : 'default'">{{ postEnabled ? '该文章互动已开启' : '该文章互动已关闭' }}</a-tag>
          </a-space>
          <p class="muted">使用上方文章 ID。邀请后角色可以选择沉默，已发布的文章不会自动补评。</p>
          <a-space wrap>
            <a-button :disabled="busy" @click="setPost(true)">开启该文章互动</a-button>
            <a-button :disabled="busy" @click="setPost(false)">关闭该文章互动</a-button>
          </a-space>
          <div class="invite-form">
            <a-select :disabled="busy" v-model:value="inviteIds" mode="multiple" :options="enabledBotOptions" placeholder="最多选择两名角色；留空按兴趣邀请" style="min-width:300px;max-width:100%" />
            <a-button :disabled="busy || !communityEnabled" @click="invite">正式邀请</a-button>
          </div>
        </a-card>
      </a-tab-pane>
      <a-tab-pane key="runs" tab="运行记录">
        <a-card :bordered="false" title="最近 100 条记录">
          <template #extra>
            <a-space>
              <a-select :disabled="busy" v-model:value="selectedBotId" :options="botOptions" allow-clear placeholder="所有角色" class="bot-select" @change="refreshSelected" />
              <a-button :loading="busy" @click="refreshSelected">刷新</a-button>
            </a-space>
          </template>
          <a-table :columns="runColumns" :data-source="runs" :loading="busy" :row-key="(run: CommunityRun) => run.id || run.taskId" :scroll="{ x: 900 }">
            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'bot'">{{ botName(record.botId) }}</template>
              <template v-else-if="column.key === 'status'">
                <a-tag>{{ label(record.status || record.decision) }}</a-tag>
              </template>
              <template v-else-if="column.key === 'tokens'">{{ record.inputTokens || 0 }} / {{ record.outputTokens || 0 }}</template>
              <template v-else-if="column.key === 'detail'">
                <a-button type="link" @click="showRun(record)">查看</a-button>
              </template>
            </template>
          </a-table>
          <h3>任务队列</h3>
          <p class="muted">生成记录展示角色的决策；任务标记“已发言”表示评论已正式发布。</p>
          <a-table :columns="taskColumns" :data-source="tasks" :row-key="(task: CommunityTask) => task.id" :scroll="{ x: 1300 }">
            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'bot'">{{ botName(record.botId) }}</template>
              <template v-else-if="column.key === 'status'">{{ label(record.status) }}</template>
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
                <template v-else-if="column.key === 'post'">{{ record.postTitle || '文章' }} · #{{ record.postId }}</template>
                <template v-else-if="column.key === 'parent'">
                  <a-button v-if="record.parentId" type="link" @click="openThread(record.parentId)">#{{ record.parentId }}</a-button>
                  <span v-else>文章</span>
                </template>
                <template v-else-if="column.key === 'content'"><div class="comment-body">{{ record.content }}</div></template>
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
                  <a-list-item-meta :title="`文章 #${item.sourcePostId}${item.sourceCommentId ? ' · 评论 #' + item.sourceCommentId : ''}`">
                    <template #description>
                      <p class="memory-summary">{{ item.summary }}</p>
                      <span class="muted">{{ item.createdAt }} · 参与者：{{ item.participants?.join('、') || '—' }}</span>
                      <p v-if="item.sourceCommentIds?.length" class="muted">关联评论：{{ item.sourceCommentIds.map((id: number) => '#' + id).join('、') }}</p>
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
            <p class="muted">额度按北京时间每日计算。模型任务包含沉默和失败，额度为 0 时停止相应参与。</p>
            <a-row :gutter="16">
              <a-col v-for="field in limitFields" :key="field.key" :xs="24" :sm="12" :lg="8">
                <a-form-item :label="field.title">
                  <a-input-number :value="settingsValue(field.key)" :min="field.min" :max="field.max" :precision="0" style="width:100%" @update:value="updateSetting(field.key, $event)" />
                </a-form-item>
              </a-col>
            </a-row>
            <a-button type="primary" :loading="busy" @click="saveSettings">保存设置</a-button>
          </a-form>
        </a-card>
      </a-tab-pane>
    </a-tabs>
    <a-modal v-model:open="botModal" :title="editingBotId ? '编辑角色' : '创建角色'" :confirm-loading="busy" :width="650" @ok="saveBot">
      <a-form layout="vertical">
        <a-form-item label="名称" required>
          <a-input v-model:value="botForm.name" :maxlength="80" />
        </a-form-item>
        <a-form-item label="头像地址">
          <a-input v-model:value="botForm.avatarUrl" :maxlength="1000" placeholder="图片地址" />
        </a-form-item>
        <a-form-item label="性格与表达方式" required>
          <a-textarea v-model:value="botForm.personality" :rows="3" :maxlength="10000" placeholder="例如：温和、好奇，喜欢用具体例子解释技术" />
        </a-form-item>
        <a-form-item label="身份 / 背景">
          <a-textarea v-model:value="botForm.background" :rows="3" :maxlength="10000" placeholder="角色是谁，有什么经历、立场和知识背景" />
        </a-form-item>
        <a-form-item label="自定义提示词">
          <a-textarea v-model:value="botForm.systemPrompt" :rows="6" :maxlength="10000" show-count placeholder="角色的行为与回复要求，例如：回应具体观点，遇到不确定的知识先查资料，没有值得补充的内容时保持沉默" />
        </a-form-item>
        <a-form-item label="兴趣">
          <a-input v-model:value="botForm.interests" :maxlength="1000" placeholder="例如：编程、音乐、日常生活" />
        </a-form-item>
        <a-form-item label="参与积极度">
          <a-slider v-model:value="botForm.participation" :min="0" :max="100" />
          <span class="muted">{{ botForm.participation }}%</span>
        </a-form-item>
        <a-form-item label="允许正式参与">
          <a-switch v-model:checked="botForm.enabled" />
          <p v-if="!communityEnabled" class="muted">全站评论互动目前暂停。保存角色启用状态后，开启全站互动才会自动参与。</p>
        </a-form-item>
      </a-form>
    </a-modal>
    <a-modal v-model:open="knowledgeModal" :title="editingKnowledgeId ? '编辑资料' : '添加资料'" :width="800" :confirm-loading="busy" @ok="saveKnowledge">
      <a-form layout="vertical">
        <a-form-item label="标题" required>
          <a-input v-model:value="knowledgeForm.title" :maxlength="200" />
        </a-form-item>
        <a-form-item label="正文" required>
          <a-textarea v-model:value="knowledgeForm.content" :rows="14" :maxlength="200000" show-count />
        </a-form-item>
      </a-form>
    </a-modal>
    <a-modal v-model:open="detailOpen" title="角色决策详情" :width="850" :footer="null">
      <template v-if="detail">
        <a-descriptions :column="2" bordered>
          <a-descriptions-item label="角色">{{ detail.roleSnapshot?.name || botName(detail.botId) }}</a-descriptions-item>
          <a-descriptions-item label="文章">#{{ detail.postId }}</a-descriptions-item>
          <a-descriptions-item label="决策">{{ label(detail.decision) }}</a-descriptions-item>
          <a-descriptions-item v-if="detail.publishedCommentId" label="已发布评论" :span="2"><a-button type="link" @click="openThread(detail.publishedCommentId!, detail.botId)">#{{ detail.publishedCommentId }} · 查看对话</a-button></a-descriptions-item>
          <a-descriptions-item label="回复目标"><a-button v-if="detail.targetCommentId" type="link" @click="openThread(detail.targetCommentId!, detail.botId)">#{{ detail.targetCommentId }} · 查看对话</a-button><span v-else>—</span></a-descriptions-item>
          <a-descriptions-item label="原因" :span="2">{{ detail.reason || '—' }}</a-descriptions-item>
          <a-descriptions-item label="模型">{{ detail.model || '—' }}</a-descriptions-item>
          <a-descriptions-item label="输入 / 输出">{{ detail.inputTokens || 0 }} / {{ detail.outputTokens || 0 }}</a-descriptions-item>
        </a-descriptions>
        <details v-if="detail.roleSnapshot" class="role-snapshot">
          <summary>本次执行的角色配置（版本 {{ detail.roleSnapshot.version ?? '—' }}）</summary>
          <a-descriptions :column="1" bordered class="mt-16">
            <a-descriptions-item label="名称">{{ detail.roleSnapshot.name }}</a-descriptions-item>
            <a-descriptions-item label="身份 / 背景"><div class="comment-body">{{ detail.roleSnapshot.background || '—' }}</div></a-descriptions-item>
            <a-descriptions-item label="性格与表达方式"><div class="comment-body">{{ detail.roleSnapshot.personality || '—' }}</div></a-descriptions-item>
            <a-descriptions-item label="自定义提示词"><div class="comment-body">{{ detail.roleSnapshot.systemPrompt || '—' }}</div></a-descriptions-item>
          </a-descriptions>
        </details>
        <a-alert v-if="detail.error" type="error" :message="detail.error" class="mt-16" />
        <h3>发言内容</h3>
        <div class="preview-content">{{ detail.content || '角色选择保持沉默。' }}</div>
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
    <a-modal v-model:open="threadOpen" title="评论对话审查" :width="900" :footer="null">
      <a-spin :spinning="threadLoading">
        <a-alert v-if="threadError" type="error" :message="threadError" show-icon />
        <template v-if="thread">
          <p>{{ thread.postTitle || '文章' }} · #{{ thread.postId }} · 根评论 #{{ thread.rootCommentId }} · 共 {{ thread.total }} 条</p>
          <a-alert v-if="thread.truncated" class="mb-16" type="info" :message="`此线程共 ${thread.total} 条，仅显示最近 200 条。更早的回复目标可能不在当前范围内。`" show-icon />
          <a-empty v-if="!thread.comments.length" description="暂无对话记录" />
          <div v-for="comment in thread.comments" :key="comment.id" class="thread-comment" :class="{ 'thread-focus': comment.id === threadCommentId }">
            <div class="thread-heading">
              <strong>{{ authorName(comment) }}</strong>
              <a-tag :color="comment.authorType !== 'BOT' ? 'default' : comment.botId === threadBotId ? 'blue' : 'purple'">{{ comment.authorType !== 'BOT' ? '真人' : comment.botId === threadBotId ? '当前 AI' : '其他 AI' }}</a-tag>
              <a-tag v-if="comment.deletedAt" color="red">已删除</a-tag>
              <span class="muted">#{{ comment.id }} · {{ formatDateTime(comment.createdAt) }}</span>
            </div>
            <p class="muted">回复 {{ replyTarget(comment) }}</p>
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
.bot-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(290px, 1fr)); gap: 16px; }
.bot-heading { display: flex; align-items: center; gap: 8px; }
.bot-description { margin-top: 16px; white-space: pre-wrap; }
.bot-select { width: 170px; }
.invite-form { display: flex; gap: 12px; flex-wrap: wrap; margin-top: 20px; }
.memory-summary, .preview-content { white-space: pre-wrap; overflow-wrap: anywhere; }
.preview-content { padding: 16px; background: var(--lt-color-bg-layout); border-radius: 8px; }
.trace-list { padding-left: 20px; }
.trace-list li { margin-bottom: 8px; overflow-wrap: anywhere; }
.trace-list pre { white-space: pre-wrap; }
.comment-body { white-space: pre-wrap; overflow-wrap: anywhere; }
.review-pagination { margin-top: 20px; text-align: right; }
.role-snapshot { margin-top: 16px; }
.role-snapshot summary { cursor: pointer; }
.thread-comment { padding: 16px; border: 1px solid var(--lt-color-border); border-radius: 8px; margin-top: 12px; }
.thread-focus { border-color: var(--lt-color-primary); background: var(--lt-color-bg-layout); }
.thread-heading { display: flex; align-items: center; flex-wrap: wrap; gap: 8px; }
</style>
