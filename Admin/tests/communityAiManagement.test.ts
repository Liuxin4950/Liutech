import { readFileSync } from 'node:fs'
import { createRequire } from 'node:module'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import type { CommunityBot, CommunityRun, CommunityTask, CommunityWorkerStatus } from '../src/services/community'
import { queueCountdown, workerObservation } from '../src/utils/communityProgress'

// Admin 的现有测试配置运行于 Node；编译真实 SFC setup，复用 Web 的 Vue/TS 依赖。
const webRequire = createRequire(new URL('../../Web/package.json', import.meta.url))
const vue = webRequire('vue')
const { parse, compileScript, compileTemplate } = webRequire('@vue/compiler-sfc')
const { baseParse } = webRequire('@vue/compiler-dom')
const ts = webRequire('typescript')
const source = readFileSync(new URL('../src/views/admin/CommunityAiManagement.vue', import.meta.url), 'utf8')
const { descriptor } = parse(source)
const script = compileScript(descriptor, { id: 'community-management-regression' })
const compiled = ts.transpileModule(script.content, {
  compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 },
}).outputText

const service = {
  bots: vi.fn(),
  settings: vi.fn(),
  saveSettings: vi.fn(),
  createBot: vi.fn(),
  updateBot: vi.fn(),
  knowledge: vi.fn().mockResolvedValue([]),
  memory: vi.fn().mockResolvedValue([]),
  comments: vi.fn().mockResolvedValue({ records: [], total: 0 }),
  runs: vi.fn().mockResolvedValue([]),
  run: vi.fn(),
  tasks: vi.fn().mockResolvedValue([]),
  events: vi.fn().mockResolvedValue([]),
  worker: vi.fn(),
  retryTask: vi.fn(),
}
const settingsDefaults = () => ({
  enabled: false, botRelayEnabled: true, minDelaySeconds: 20, maxDelaySeconds: 90, cooldownSeconds: 30,
  botDailyCommentLimit: 20, siteDailyCommentLimit: 100, postDailyCommentLimit: 20,
  botDailyTaskLimit: 40, siteDailyTaskLimit: 200, maxChainComments: 4,
})
const scopes: { stop(): void }[] = []
function createPage() {
  const module = { exports: {} as any }
  const imports: Record<string, unknown> = {
    vue: { ...vue, onMounted() {}, onActivated() {}, onDeactivated() {}, onBeforeUnmount() {} },
    'ant-design-vue': { message: { error: vi.fn(), success: vi.fn(), warning: vi.fn() } },
    '@ant-design/icons-vue': {},
    '@/services/upload': {},
    '@/services/community': { communityService: service, defaultCommunitySettings: settingsDefaults },
    '@/services/posts': { __esModule: true, default: {} },
    '@/services/comments': { __esModule: true, default: {} },
    '@/utils/utils': { formatDateTime: (value: string) => value },
    '@/utils/communityProgress': { queueCountdown, workerObservation },
  }
  new Function('require', 'module', 'exports', compiled)((name: string) => {
    if (!(name in imports)) throw new Error(`Unexpected component dependency: ${name}`)
    return imports[name]
  }, module, module.exports)
  const scope = vue.effectScope()
  scopes.push(scope)
  return scope.run(() => module.exports.default.setup({}, { expose() {} }))
}
const bot = (id: number, name: string): CommunityBot => ({
  id, name, avatarUrl: `/${id}.png`, personality: `${name}的表达方式`, background: `${name}的背景`,
  systemPrompt: '', interests: '', participation: 50, enabled: true, version: 1,
})
const run = (id: number, data: Partial<CommunityRun> = {}): CommunityRun => ({
  id, taskId: id, botId: 1, postId: 1, ...data,
})
const task = (id: number, status: string, data: Partial<CommunityTask> = {}): CommunityTask => ({
  id, botId: 1, postId: 1, status, attempts: 1, ...data,
})
const workerStatus = (data: Partial<CommunityWorkerStatus> = {}): CommunityWorkerStatus => ({
  instanceId: 'local-regression', state: 'IDLE', phase: 'IDLE', busy: false, schedulerAlive: true,
  observedAt: new Date().toISOString(), pollIntervalMs: 5000, heartbeatAgeMs: 0, phaseElapsedMs: 0, ...data,
})
beforeEach(() => {
  vi.resetAllMocks()
  service.bots.mockResolvedValue([])
  service.settings.mockResolvedValue(settingsDefaults())
  service.saveSettings.mockImplementation(async (settings) => ({ ...settings }))
  service.knowledge.mockResolvedValue([])
  service.memory.mockResolvedValue([])
  service.comments.mockResolvedValue({ records: [], total: 0 })
  service.runs.mockResolvedValue([])
  service.run.mockImplementation(async (id) => run(id))
  service.tasks.mockResolvedValue([])
  service.events.mockResolvedValue([])
  service.worker.mockResolvedValue(workerStatus())
  service.retryTask.mockResolvedValue({ queued: true, reason: '已重新入队' })
  vi.stubGlobal('document', { hidden: true })
})
afterEach(() => { for (const scope of scopes.splice(0)) scope.stop(); vi.unstubAllGlobals() })

it('卡片选择区域是原生按钮，覆盖头像和描述，并同步右侧角色与后续操作', async () => {
  const result = compileTemplate({ source: descriptor.template.content, filename: 'CommunityAiManagement.vue', id: 'community-management-regression' })
  expect(result.errors).toEqual([])
  const findSelectButton = (node: any): any => node.tag === 'button' && node.props?.some((prop: any) => prop.name === 'class' && prop.value?.content === 'bot-select-area')
    ? node : (node.children || []).map(findSelectButton).find(Boolean)
  const selection = findSelectButton(result.ast)
  expect(selection.props.find((prop: any) => prop.name === 'on' && prop.arg?.content === 'click')?.exp.loc.source).toBe('selectBot(bot.id)')
  const hasDescription = (node: any): boolean => node.props?.some((prop: any) => prop.value?.content === 'bot-description role-excerpt')
    || (node.children || []).some(hasDescription)
  expect(hasDescription(selection)).toBe(true)
  const page = createPage()
  page.bots.value = [bot(1, '哆啦A梦'), bot(2, '黑塔')]
  page.selectedBotId.value = 1
  page.commentsPage.value = 4
  page.selectBot(2)
  expect(page.selectedBot.value.name).toBe('黑塔')
  expect(page.selectedBot.value.avatarUrl).toBe('/2.png')
  expect(page.commentsPage.value).toBe(1)
  page.navigateRoleTab('knowledge')
  await vue.nextTick()
  expect(service.knowledge).toHaveBeenCalledWith(2)
})

it('接力开关绑定既有设置并说明范围，运行详情显示规则与模型来源', () => {
  const result = compileTemplate({ source: descriptor.template.content, filename: 'CommunityAiManagement.vue', id: 'community-light-regression' })
  expect(result.errors).toEqual([])
  expect(result.code).toContain('checked: _ctx.settings.botRelayEnabled')
  expect(result.code).toContain('((_ctx.settings.botRelayEnabled) = $event)')
  expect(descriptor.template.content).toContain('不影响真人回复和多角色文章初评')
  expect(descriptor.template.content).toContain("detail.decisionSource === 'RULE'")
})

it('保存关闭接力的配置时保留 false 并通过现有设置接口提交', async () => {
  const source = readFileSync(new URL('../src/services/community.ts', import.meta.url), 'utf8')
  const compiled = ts.transpileModule(source, {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 },
  }).outputText
  const put = vi.fn().mockImplementation(async (_url, data) => ({ data }))
  const module = { exports: {} as any }
  new Function('require', 'module', 'exports', compiled)((name: string) => {
    if (name === './api') return { put }
    if (name === './aiClient') return { aiApi: {} }
    throw new Error(`Unexpected service dependency: ${name}`)
  }, module, module.exports)
  const settings = { ...module.exports.defaultCommunitySettings(), botRelayEnabled: false, version: 7 }
  const saved = await module.exports.communityService.saveSettings(settings)
  expect(put).toHaveBeenCalledWith('/admin/community/settings', expect.objectContaining({ botRelayEnabled: false }))
  expect(saved.botRelayEnabled).toBe(false)
  expect(saved.siteDailyCommentLimit).toBe(settings.siteDailyCommentLimit)
  expect(saved).not.toHaveProperty('version')
})

const contextSnapshot = (text: string): NonNullable<CommunityRun['contextSnapshot']> => ({
  version: 1, prepared: true, requestStarted: true,
  sections: [{ source: 'comment', id: 3, kind: 'trigger', content: text, authorType: 'HUMAN' },
    { source: 'comment', id: 2, kind: 'parent', content: 'AI 原话', authorType: 'BOT', ownComment: true },
    { source: 'memory', id: 'memory', kind: 'memory', content: '历史摘要' }],
  messages: [{ role: 'user', content: text }],
})
it('运行详情按需读取历史正文并区分触发、前文和记忆，旧记录不回填当前内容', async () => {
  const page = createPage()
  service.run.mockResolvedValue(run(1, { contextSnapshot: contextSnapshot('当时的真人原话') }))
  await page.showRun(run(1))
  expect(service.run).toHaveBeenCalledWith(1)
  expect(page.contextGroups.value.find((group: any) => group.key === 'trigger').sections[0].content).toBe('当时的真人原话')
  expect(page.contextGroups.value.find((group: any) => group.key === 'parent').sections[0].ownComment).toBe(true)
  expect(page.contextGroups.value.find((group: any) => group.key === 'memory').sections[0].content).toBe('历史摘要')
  service.run.mockResolvedValue(run(2, { readTrace: [{ source: 'comment', id: 3 }] }))
  await page.showRun(run(2))
  expect(page.contextState()).toContain('未保存上下文正文')
  expect(page.inputSnapshot.value).toBeUndefined()
})
it('快速切换详情和关闭窗口时，迟到数据不会覆盖新记录', async () => {
  const page = createPage()
  let resolveFirst!: (value: CommunityRun) => void
  let resolveSecond!: (value: CommunityRun) => void
  service.run.mockImplementation((id) => new Promise<CommunityRun>(resolve => { if (id === 1) resolveFirst = resolve; else resolveSecond = resolve }))
  const first = page.showRun(run(1))
  const second = page.showRun(run(2))
  resolveSecond(run(2, { contextSnapshot: contextSnapshot('第二次输入') }))
  await second
  resolveFirst(run(1, { contextSnapshot: contextSnapshot('第一次输入') }))
  await first
  expect(page.detail.value.id).toBe(2)
  expect(page.inputSnapshot.value.messages[0].content).toBe('第二次输入')
  const closed = page.showRun(run(1))
  page.detailOpen.value = false
  await vue.nextTick()
  resolveFirst(run(1, { contextSnapshot: contextSnapshot('关闭后的迟到内容') }))
  await closed
  expect(page.inputSnapshot.value).toBeUndefined()
  expect(page.runDetailLoading.value).toBe(false)
})
it('详情加载失败可重试，规则拒绝不冒充模型读取，复制保留原消息顺序', async () => {
  const page = createPage()
  service.run.mockRejectedValue(new Error('暂时不可连接'))
  await page.showRun(run(1))
  expect(page.contextState()).toContain('读取失败')
  const saved = contextSnapshot('<script>原始文本</script>')
  saved.requestStarted = false
  saved.messages = [{ role: 'system', content: '系统规则' }, { role: 'user', content: '本轮原文' }]
  service.run.mockResolvedValue(run(1, { contextSnapshot: saved }))
  await page.showRun(page.detail.value)
  expect(page.runDetailError.value).toBe('')
  expect(page.contextState()).toContain('未发起模型请求')
  const copy = vi.fn().mockResolvedValue(undefined)
  vi.stubGlobal('navigator', { clipboard: { writeText: copy } })
  await page.copyContextInput()
  expect(copy).toHaveBeenCalledWith('[1 system]\n系统规则\n\n[2 user]\n本轮原文')
  expect(descriptor.template.content).toContain('{{ part.content }}')
  expect(descriptor.template.content).not.toContain('v-html="part.content"')
})

it('编辑其他角色也同步操作上下文，忙碌时阻止卡片切换', () => {
  const page = createPage()
  page.bots.value = [bot(1, '哆啦A梦'), bot(2, '黑塔')]
  page.selectedBotId.value = 1
  page.openBot(page.bots.value[1])
  expect(page.selectedBot.value.id).toBe(2)
  expect(page.editingBotId.value).toBe(2)
  page.busy.value = true
  page.selectBot(1)
  expect(page.selectedBot.value.id).toBe(2)
})

it('token 按正式与预演分开，旧记录、缺失、部分提供和未调用模型不会冒充完整用量', () => {
  const page = createPage()
  page.runs.value = [
    run(1, { tokenUsageAvailable: true, tokenUsageComplete: true, inputTokens: 100, outputTokens: 20, modelRounds: 1 }),
    run(2, { preview: true, tokenUsageAvailable: true, tokenUsageComplete: false, outputTokens: 30, modelRounds: 2 }),
    run(3, { tokenUsageAvailable: false }),
    run(4, { inputTokens: 90, outputTokens: 10 }),
    run(5, { modelRounds: 0 }),
    run(6, { tokenUsageAvailable: true, tokenUsageComplete: true }),
  ]
  expect(page.usageSummary.value).toMatchObject({ inputTokens: 100, outputTokens: 50, knownTokens: 150, complete: 1, partial: 1, missing: 2, legacy: 1, legacyKnownTokens: 100, notCalled: 1 })
  expect(page.usageBreakdown.value.map((entry: any) => [entry.records.length, entry.usage.knownTokens])).toEqual([[5, 120], [1, 30]])
  expect(page.modelRoundSummary.value).toEqual({ total: 3, missing: 3 })
})

it('决策分布排除预演并保留发布失败前的选择，任务恢复不与当前停止失败混淆', () => {
  const page = createPage()
  page.runs.value = [run(1, { decision: 'COMMENT' }), run(2, { decision: 'REPLY', publicationStatus: 'FAILED' }), run(3, { decision: 'SKIP' }), run(4, { status: 'PREVIEW', decision: 'COMMENT' })]
  page.tasks.value = [task(1, 'READY'), task(2, 'READY', { failures: 1 }), task(3, 'RUNNING'), task(4, 'DECIDED'), task(5, 'SUCCEEDED'), task(6, 'SKIPPED'), task(7, 'FAILED'), task(8, 'CANCELLED')]
  expect(page.decisionDistribution.value.map((entry: any) => entry.count)).toEqual([1, 1, 1, 0, 0])
  expect(page.pendingTasks.value).toHaveLength(4)
  expect(page.failedTasks.value).toHaveLength(1)
  expect(page.taskDistribution.value.map((entry: any) => entry.count)).toEqual([1, 1, 1, 1, 0, 1, 1, 1, 0, 1])
})

it('角色明细按发表评论 ID 去重，查询单角色时不会给未查询角色填充零值', () => {
  const page = createPage()
  page.bots.value = [bot(1, '哆啦A梦'), bot(2, '黑塔')]
  page.runs.value = [run(1, { publishedCommentId: 9 }), run(2, { publishedCommentId: 9 }), run(3, { botId: 2, preview: true })]
  expect(page.roleMetrics.value.map((entry: any) => [entry.id, entry.formal, entry.preview, entry.published])).toEqual([[1, 2, 0, 1], [2, 0, 1, 0]])
  page.recordsScopeBotId.value = 2
  expect(page.roleMetrics.value).toHaveLength(1)
  expect(page.roleMetrics.value[0].id).toBe(2)
})

it('查询角色任务时保留最新事件接口，并移除已经交接为 AI 任务的事件', async () => {
  const page = createPage()
  service.runs.mockResolvedValueOnce([run(1, { botId: 2 })])
  service.tasks.mockResolvedValueOnce([task(1, 'READY', { botId: 2, eventId: 10 })])
  service.events.mockResolvedValueOnce([
    { id: 10, botId: 2, postId: 1, status: 'DISPATCHING' },
    { id: 11, botId: 2, postId: 1, status: 'READY' },
  ])
  await page.loadRecords(2)
  expect(service.runs).toHaveBeenCalledWith(2, undefined)
  expect(service.tasks).toHaveBeenCalledWith(2, undefined)
  expect(service.events).toHaveBeenCalledWith(2, undefined)
  expect(page.events.value.map((event: any) => event.id)).toEqual([11])
  expect(page.recordsLoaded.value).toBe(true)
  expect(page.recordsScopeBotId.value).toBe(2)
  expect(page.recordsScope.value).toContain('所有待处理与失败任务')
})

it('部分查询失败仍保留本次成功返回的任务与事件，不把旧数据或完整统计冒充当前事实', async () => {
  const page = createPage()
  page.runs.value = [run(99)]
  page.recordsLoaded.value = true
  service.runs.mockRejectedValueOnce(new Error('运行服务暂时不可用'))
  service.tasks.mockResolvedValueOnce([task(2, 'FAILED', { botId: 2 })])
  service.events.mockResolvedValueOnce([{ id: 12, botId: 2, postId: 1, status: 'READY' }])
  await page.loadRecords(2)
  expect(page.runs.value).toEqual([])
  expect(page.tasks.value.map((task: any) => task.id)).toEqual([2])
  expect(page.events.value.map((event: any) => event.id)).toEqual([12])
  expect(page.recordsLoaded.value).toBe(false)
  expect(page.recordsError.value).toContain('运行记录：运行服务暂时不可用')
  expect(page.recordsScopeBotId.value).toBe(2)
  expect(page.recordsLoading.value).toBe(false)
})

it('执行器查询失败会使保留的旧心跳失效，不把健康接口或旧快照解释为正在运行', async () => {
  const page = createPage()
  await page.loadWorker()
  expect(page.workerFresh.value).toBe(true)
  expect(page.workerOnline.value).toBe(true)
  service.worker.mockRejectedValueOnce(new Error('执行器状态读取失败'))
  await page.loadWorker()
  expect(page.worker.value.instanceId).toBe('local-regression')
  expect(page.workerFresh.value).toBe(false)
  expect(page.workerOnline.value).toBe(false)
  expect(page.workerError.value).toContain('执行器状态读取失败')
})

it('取消资格遵守执行器最新任务状态，已发表任务不能通过旧排队状态再次取消', async () => {
  const page = createPage()
  const queued = task(4, 'READY')
  expect(page.canCancelTask(queued)).toBe(true)
  expect(page.canCancelTask(task(5, 'DECIDED', { publishedCommentId: 10 }))).toBe(false)
  service.worker.mockResolvedValueOnce(workerStatus({ state: 'RUNNING', phase: 'MODEL_GENERATION', busy: true, currentTaskId: '4', currentTaskStatus: 'CANCELLED' }))
  await page.loadWorker()
  expect(page.observedTaskStatus(queued)).toBe('CANCELLED')
  expect(page.canCancelTask(queued)).toBe(false)
})

it('队列刷新保留已读取的真实上下文，同时更新同次执行的发布结果', async () => {
  const page = createPage()
  const snapshot = contextSnapshot('真实调用时的上下文')
  service.run.mockResolvedValue(run(1, { contextSnapshot: snapshot, publicationStatus: 'PENDING' }))
  await page.showRun(run(1))
  service.runs.mockResolvedValue([run(1, { publicationStatus: 'SUCCEEDED', publishedCommentId: 31 })])
  await page.loadRecords()
  expect(page.detail.value.contextSnapshot).toEqual(snapshot)
  expect(page.detail.value.publicationStatus).toBe('SUCCEEDED')
  expect(page.detail.value.publishedCommentId).toBe(31)
  expect(page.contextState()).toContain('已发起模型请求')
  expect(service.run).toHaveBeenCalledTimes(1)
})

it('文章处理结果刷新也保留当前执行详情的上下文正文', async () => {
  const page = createPage()
  page.postId.value = 1
  await vue.nextTick()
  const snapshot = contextSnapshot('文章邀请任务的真实上下文')
  service.run.mockResolvedValue(run(1, { contextSnapshot: snapshot }))
  await page.showRun(run(1))
  service.runs.mockResolvedValue([run(1, { publicationStatus: 'FAILED', publicationError: '来源评论已删除' })])
  await page.loadPostActivity()
  expect(page.detail.value.contextSnapshot).toEqual(snapshot)
  expect(page.detail.value.publicationStatus).toBe('FAILED')
  expect(page.detail.value.publicationError).toBe('来源评论已删除')
})

it('配置未读取、读取中或读取失败时不能用默认值覆盖参与规则', async () => {
  const page = createPage()
  await page.saveSettings()
  expect(service.saveSettings).not.toHaveBeenCalled()
  page.configurationLoaded.value = true
  page.loading.value = true
  await page.saveSettings()
  expect(service.saveSettings).not.toHaveBeenCalled()
  page.loading.value = false
  page.loadError.value = '参与规则暂时无法读取'
  await page.saveSettings()
  expect(service.saveSettings).not.toHaveBeenCalled()
  page.loadError.value = ''
  page.settings.value.siteDailyCommentLimit = 23
  await page.saveSettings()
  expect(service.saveSettings).toHaveBeenCalledTimes(1)
  expect(service.saveSettings).toHaveBeenCalledWith(expect.objectContaining({ siteDailyCommentLimit: 23 }))
  expect(page.savedSettings.value.siteDailyCommentLimit).toBe(23)
})

it('配置未就绪时不能创建角色，读取成功后保存原表单内容', async () => {
  const page = createPage()
  page.botForm.value = { ...bot(1, '新角色'), name: '新角色', personality: '谨慎回答' }
  service.createBot.mockResolvedValue(bot(3, '新角色'))
  await page.saveBot()
  expect(service.createBot).not.toHaveBeenCalled()
  page.configurationLoaded.value = true
  await page.saveBot()
  expect(service.createBot).toHaveBeenCalledTimes(1)
  expect(service.createBot).toHaveBeenCalledWith(expect.objectContaining({ name: '新角色', personality: '谨慎回答' }))
  expect(page.selectedBotId.value).toBe(3)
})

it('历史失败预演不会显示绿色完成提示，旧 PREVIEW 状态仍能识别预演', async () => {
  const page = createPage()
  const failed = run(1, { preview: true, status: 'FAILED', error: '模型连接失败', contextSnapshot: contextSnapshot('失败请求输入') })
  await page.showRun(failed)
  expect(page.detailMode.value).toBe('preview')
  expect(page.runStatus(page.detail.value)).toContain('失败')
  const templateAst = baseParse(descriptor.template.content)
  const findCompletionAlert = (node: any): any => node.tag === 'a-alert'
    && node.props?.some((prop: any) => prop.name === 'type' && prop.value?.content === 'success')
    && node.props?.some((prop: any) => prop.name === 'bind' && prop.arg?.content === 'message' && prop.exp?.content.includes('预演已完成'))
    ? node : (node.children || []).map(findCompletionAlert).find(Boolean)
  const alert = findCompletionAlert(templateAst)
  expect(alert).toBeDefined()
  const condition = alert.props.find((prop: any) => prop.name === 'if').exp.content
  const isVisible = new Function('ctx', `with (ctx) { return !!(${condition}) }`)
  expect(isVisible(vue.proxyRefs(page))).toBe(false)
  await page.showRun(run(2, { status: 'PREVIEW', decision: 'COMMENT', content: '预演内容', contextSnapshot: contextSnapshot('成功输入') }))
  expect(page.detailMode.value).toBe('preview')
  expect(isVisible(vue.proxyRefs(page))).toBe(true)
})

it('配置未就绪或全站暂停时不重排失败任务，恢复后才提交重试', async () => {
  const page = createPage()
  const failed = task(7, 'FAILED', { error: '原始生成错误' })
  page.bots.value = [bot(1, '讨论角色')]
  page.communityEnabled.value = true
  await page.retryTask(failed)
  expect(service.retryTask).not.toHaveBeenCalled()
  page.configurationLoaded.value = true
  page.communityEnabled.value = false
  await page.retryTask(failed)
  expect(service.retryTask).not.toHaveBeenCalled()
  page.communityEnabled.value = true
  await page.retryTask(failed)
  expect(service.retryTask).toHaveBeenCalledTimes(1)
  expect(service.retryTask).toHaveBeenCalledWith(7)
  expect(page.retryNotices.value['7'].queued).toBe(true)
  expect(page.retryNotices.value['7'].message).not.toContain('开启后才会执行')
})

it('切换当前角色同步清空旧资料、记忆、评论与分页数量', () => {
  const page = createPage()
  page.selectedBotId.value = 1
  page.knowledge.value = [{ id: 11, botId: 1, title: '角色一资料', content: '旧资料', version: 1 }]
  page.memories.value = [{ id: 12, botId: 1, sourcePostId: 1, summary: '角色一记忆' }]
  page.comments.value = [{ id: 13, botId: 1, postId: 1, content: '角色一评论' }]
  page.commentsTotal.value = 35
  page.commentsPage.value = 2
  page.selectedBotId.value = 2
  expect(page.knowledge.value).toEqual([])
  expect(page.memories.value).toEqual([])
  expect(page.comments.value).toEqual([])
  expect(page.commentsTotal.value).toBe(0)
  expect(page.commentsPage.value).toBe(1)
})

it('角色切走再切回时旧知识请求不能覆盖新请求的结果', async () => {
  const page = createPage()
  page.selectedBotId.value = 1
  page.activeTab.value = 'knowledge'
  let resolveOld!: (data: any[]) => void
  service.knowledge.mockImplementationOnce(() => new Promise(resolve => { resolveOld = resolve }))
  const oldRequest = page.loadSelected()
  page.selectedBotId.value = 2
  page.selectedBotId.value = 1
  const current = [{ id: 22, botId: 1, title: '最新资料', content: '新正文', version: 2 }]
  service.knowledge.mockResolvedValueOnce(current)
  await page.loadSelected()
  resolveOld([{ id: 21, botId: 1, title: '旧资料', content: '过期正文', version: 1 }])
  await oldRequest
  expect(page.knowledge.value).toEqual(current)
})

it('执行器已经确认取消时待处理数量和筛选列表与取消资格一致', async () => {
  const page = createPage()
  page.tasks.value = [task(4, 'READY')]
  expect(page.pendingTasks.value).toHaveLength(1)
  service.worker.mockResolvedValueOnce(workerStatus({ state: 'CANCELLING', phase: 'MODEL_GENERATION', busy: true, currentTaskId: '4', currentTaskStatus: 'CANCELLED' }))
  await page.loadWorker()
  expect(page.canCancelTask(page.tasks.value[0])).toBe(false)
  expect(page.pendingTasks.value).toHaveLength(0)
  expect(page.visibleTasks.value).toHaveLength(0)
})

it('切换查询角色等待响应期间旧记录继续使用原来的查询范围', async () => {
  const page = createPage()
  page.recordsScopeBotId.value = 1
  page.runs.value = [run(1)]
  let resolveNew!: (records: CommunityRun[]) => void
  service.runs.mockImplementationOnce(() => new Promise(resolve => { resolveNew = resolve }))
  const request = page.loadRecords(2)
  expect(page.recordsLoading.value).toBe(true)
  expect(page.recordsScopeBotId.value).toBe(1)
  expect(page.runs.value[0].botId).toBe(1)
  resolveNew([run(2, { botId: 2 })])
  await request
  expect(page.recordsScopeBotId.value).toBe(2)
  expect(page.runs.value[0].botId).toBe(2)
})

it('生成前规则拦截单独统计，不冒充模型沉默或生成失败', () => {
  const page = createPage()
  const blocked = run(1, {
    decisionSource: 'RULE', failureStage: 'PRECHECK', status: 'FAILED', decision: 'SKIP',
    error: '文章发表额度已耗尽', modelRounds: 0,
  })
  page.runs.value = [blocked,
    run(2, { decisionSource: 'RULE', failureStage: 'PRECHECK', status: 'SKIPPED', modelRounds: 0 }),
    run(3, { decisionSource: 'MODEL', decision: 'SKIP', modelRounds: 1 }),
    run(4, { decisionSource: 'MODEL', status: 'FAILED', failureStage: 'MODEL_GENERATION', error: '模型请求失败', modelRounds: 1 }),
  ]
  const counts = Object.fromEntries(page.decisionDistribution.value.map((entry: any) => [entry.key, entry.count]))
  expect(counts).toEqual({ COMMENT: 0, REPLY: 0, SKIP: 1, RULE: 2, FAILED: 1 })
  expect(page.failedRun(blocked)).toBe(false)
  expect(page.runStatus(blocked)).toContain('规则拦截')
  expect(page.roleMetrics.value[0].runFailures).toBe(1)
  expect(page.usageSummary.value.notCalled).toBe(2)
})

it('发布失败保留模型的回复选择，并在执行结果中展示失败', () => {
  const page = createPage()
  const generated = run(1, { decisionSource: 'MODEL', decision: 'REPLY', targetCommentId: 41, publicationStatus: 'FAILED', publicationError: '回复目标已删除', modelRounds: 1 })
  page.runs.value = [generated]
  expect(page.decisionDistribution.value.find((entry: any) => entry.key === 'REPLY').count).toBe(1)
  expect(page.decisionDistribution.value.find((entry: any) => entry.key === 'FAILED').count).toBe(0)
  expect(page.failedRun(generated)).toBe(true)
  expect(page.runStatus(generated)).toContain('发布失败')
  expect(page.roleMetrics.value[0].runFailures).toBe(1)
})

it('无效 token 和轮数不污染合计，单条显示与缺失及部分用量口径一致', () => {
  const page = createPage()
  const records = [
    run(1, { tokenUsageAvailable: true, tokenUsageComplete: true, inputTokens: 100, outputTokens: 20, modelRounds: 2 }),
    run(2, { tokenUsageAvailable: true, tokenUsageComplete: true, inputTokens: NaN, outputTokens: 8, modelRounds: NaN }),
    run(3, { tokenUsageAvailable: true, tokenUsageComplete: true, inputTokens: 5, outputTokens: Infinity, modelRounds: Infinity }),
    run(4, { tokenUsageAvailable: true, tokenUsageComplete: true, inputTokens: -4, outputTokens: 4, modelRounds: -2 }),
    run(5, { tokenUsageAvailable: true, tokenUsageComplete: true, inputTokens: '200', outputTokens: '12', modelRounds: '3' } as any),
    run(6, { tokenUsageAvailable: true, tokenUsageComplete: true, inputTokens: 90, outputTokens: 10, modelRounds: 0 }),
    run(7, { inputTokens: Infinity, outputTokens: 7 }),
    run(8, { tokenUsageAvailable: true, tokenUsageComplete: true, inputTokens: 3, outputTokens: 6, modelRounds: 1 }),
  ]
  page.runs.value = records
  expect(page.usageSummary.value).toMatchObject({ inputTokens: 108, outputTokens: 38, knownTokens: 146,
    available: 5, complete: 2, partial: 3, missing: 1, notCalled: 1, legacy: 1, legacyKnownTokens: 7 })
  expect(page.modelRoundSummary.value).toEqual({ total: 3, missing: 5 })
  expect(page.tokenUsage(records[0])).toBe('100 / 20')
  expect(page.tokenUsage(records[1])).toBe('未提供 / 8（部分）')
  expect(page.tokenUsage(records[2])).toBe('5 / 未提供（部分）')
  expect(page.tokenUsage(records[3])).toBe('未提供 / 4（部分）')
  expect(page.tokenUsage(records[4])).toBe('供应商未提供可靠用量')
  expect(page.tokenUsage(records[5])).toBe('未调用模型')
  expect(page.tokenUsage(records[6])).toBe('未记录 / 7（旧记录，完整性未标注）')
  expect(records.map((record) => page.tokenUsage(record)).join(' ')).not.toMatch(/NaN|Infinity|-4|200/)
})

it('只有待接收事件的历史角色也纳入角色统计，任务和运行的发布回执去重', () => {
  const page = createPage()
  page.bots.value = [bot(1, '现有角色')]
  page.events.value = [{ id: 80, botId: 8, postId: 1 }, { id: 81, botId: 8, postId: 2 }]
  page.runs.value = [run(1, { publishedCommentId: 31 }), run(2, { publishedCommentId: 31 }),
    run(3, { preview: true, publishedCommentId: 90 }), run(4, { publishedCommentId: NaN })]
  page.tasks.value = [task(1, 'SUCCEEDED', { publishedCommentId: 31 }), task(2, 'FAILED', { publishedCommentId: 32 }), task(3, 'FAILED', { publishedCommentId: -1 })]
  const existing = page.roleMetrics.value.find((role: any) => role.id === 1)
  const historical = page.roleMetrics.value.find((role: any) => role.id === 8)
  expect(existing.published).toBe(2)
  expect(historical).toMatchObject({ id: 8, waitingEvents: 2, formal: 0, pending: 0, published: 0 })
  expect(historical.enabled).toBeUndefined()
})

it('带评论回执的后处理与失败独立归类，不计为等待发表或重新生成', () => {
  const page = createPage()
  page.tasks.value = [
    task(1, 'READY', { publishedCommentId: 31, failures: 1 }),
    task(2, 'RUNNING', { publishedCommentId: 32 }),
    task(3, 'DECIDED', { publishedCommentId: 33 }),
    task(4, 'FAILED', { publishedCommentId: 34 }),
    task(5, 'SUCCEEDED', { publishedCommentId: 35 }),
    task(6, 'DECIDED'),
  ]
  const counts = Object.fromEntries(page.taskDistribution.value.map((entry: any) => [entry.key, entry.count]))
  expect(counts).toMatchObject({ postprocessing: 3, postprocessFailed: 1, publishing: 1, succeeded: 1, retry: 0, waiting: 0, running: 0, failed: 0 })
  expect(page.taskStatus(page.tasks.value[0])).toContain('已发表')
  expect(page.taskStatus(page.tasks.value[3])).toContain('后处理失败')
})

it('部分读取失败时任务状态明细独立按任务可用性显示，不能把失败当零任务', async () => {
  const page = createPage()
  service.runs.mockResolvedValueOnce([run(1, { modelRounds: 1 })])
  service.tasks.mockRejectedValueOnce(new Error('任务服务不可用'))
  await page.loadRecords()
  expect(page.recordsAvailability.value).toEqual({ runs: true, tasks: false, events: true })
  expect(page.runStatsReady.value).toBe(true)
  expect(page.recordsLoaded.value).toBe(false)
  expect(page.recordsError.value).toContain('任务服务不可用')

  const templateAst = baseParse(descriptor.template.content)
  const containsHeading = (node: any): boolean => node.tag === 'h4' && node.children?.some((child: any) => child.content === '任务状态明细')
    || (node.children || []).some(containsHeading)
  const findTaskSection = (node: any, ancestors: any[] = []): any => node.tag === 'section' && containsHeading(node)
    ? { node, ancestors } : (node.children || []).map((child: any) => findTaskSection(child, [...ancestors, node])).find(Boolean)
  const section = findTaskSection(templateAst)
  expect(section).toBeDefined()
  const condition = section.node.props.find((prop: any) => prop.name === 'if').exp.content
  const isVisible = new Function('ctx', `with (ctx) { return !!(${condition}) }`)
  expect(isVisible(vue.proxyRefs(page))).toBe(false)
  expect(section.ancestors.some((ancestor: any) => ancestor.props?.some((prop: any) => prop.name === 'if' && prop.exp?.content === 'runStatsReady'))).toBe(false)

  service.runs.mockRejectedValueOnce(new Error('运行服务不可用'))
  service.tasks.mockResolvedValueOnce([task(9, 'READY')])
  await page.loadRecords()
  expect(page.recordsAvailability.value).toEqual({ runs: false, tasks: true, events: true })
  expect(page.runStatsReady.value).toBe(false)
  expect(isVisible(vue.proxyRefs(page))).toBe(true)
  expect(page.tasks.value).toHaveLength(1)
})

it('全站或角色暂停时允许纯后处理和沉默收尾，生成与发布仍需恢复参与', async () => {
  const page = createPage()
  page.configurationLoaded.value = true
  page.communityEnabled.value = false
  page.bots.value = [{ ...bot(1, '暂停角色'), enabled: false }]
  const postprocess = task(1, 'FAILED', { retryKind: 'postprocess', publishedCommentId: 31 })
  const complete = task(2, 'FAILED', { retryKind: 'complete' })
  expect(page.canRetryTask(postprocess)).toBe(true)
  expect(page.canRetryTask(complete)).toBe(true)
  expect(page.canRetryTask(task(3, 'FAILED', { retryKind: 'regenerate' }))).toBe(false)
  expect(page.canRetryTask(task(4, 'FAILED', { retryKind: 'publish' }))).toBe(false)
  await page.retryTask(postprocess)
  await page.retryTask(complete)
  expect(service.retryTask.mock.calls.map(([id]) => id)).toEqual([1, 2])
  expect(page.retryNotices.value['1'].queued).toBe(true)
  expect(page.retryNotices.value['2'].queued).toBe(true)
})

it('概览审查与对话入口经过统一导航并读取所选范围的数据', async () => {
  const page = createPage()
  page.bots.value = [bot(1, '当前角色'), bot(2, '查询角色')]
  page.selectedBotId.value = 1
  page.recordsScopeBotId.value = 2
  page.activeTab.value = 'overview'
  service.runs.mockResolvedValue([run(2, { botId: 2 })])
  service.comments.mockResolvedValue({ records: [{ id: 41, postId: 1, botId: 1, content: '实际评论' }], total: 1 })
  const templateAst = baseParse(descriptor.template.content)
  const findFlow = (node: any): any => node.tag === 'div' && node.props?.some((prop: any) => prop.name === 'class' && prop.value?.content === 'console-flow')
    ? node : (node.children || []).map(findFlow).find(Boolean)
  const flow = findFlow(templateAst)
  const clickExpression = (tab: string) => flow.children.find((node: any) => node.tag === 'a-button'
    && node.props?.some((prop: any) => prop.name === 'on' && prop.arg?.content === 'click' && prop.exp?.content === `navigateRoleTab('${tab}')`))
    ?.props.find((prop: any) => prop.name === 'on' && prop.arg?.content === 'click').exp.content
  const runsClick = clickExpression('runs')
  const commentsClick = clickExpression('comments')
  expect(runsClick).toBeDefined()
  expect(commentsClick).toBeDefined()
  new Function('ctx', `with (ctx) { ${runsClick} }`)(vue.proxyRefs(page))
  await vi.waitFor(() => expect(page.busy.value).toBe(false))
  expect(page.activeTab.value).toBe('runs')
  expect(service.runs).toHaveBeenCalledWith(2, undefined)
  expect(page.runs.value[0].botId).toBe(2)
  new Function('ctx', `with (ctx) { ${commentsClick} }`)(vue.proxyRefs(page))
  await vi.waitFor(() => expect(page.busy.value).toBe(false))
  expect(page.activeTab.value).toBe('comments')
  expect(service.comments).toHaveBeenCalledWith(1, 1, 20)
  expect(page.comments.value[0].id).toBe(41)
  expect(page.commentsTotal.value).toBe(1)
})

 it('轮询保留快照但不会保留已恢复成功运行的旧失败字段', async () => {
  const page = createPage()
  const snapshot = contextSnapshot('当次输入')
  page.detail.value = run(1, { status: 'FAILED', error: '上次发布失败', publicationError: '上次发布失败', contextSnapshot: snapshot })
  page.detailMode.value = 'run'
  service.runs.mockResolvedValue([run(1, { status: 'SUCCEEDED', publicationStatus: 'SUCCEEDED', publishedCommentId: 9 })])
  await page.loadRecords()
  expect(page.detail.value.contextSnapshot).toEqual(snapshot)
  expect(page.detail.value.error).toBeUndefined()
  expect(page.detail.value.publicationError).toBeUndefined()
  expect(page.runStatus(page.detail.value)).toBe('评论已发表')
})

it('切换角色后迟到的知识读取失败不打断新角色的有效数据', async () => {
  const page = createPage()
  let rejectOld!: (error: Error) => void
  service.knowledge.mockImplementationOnce(() => new Promise((_, reject) => { rejectOld = reject }))
  page.activeTab.value = 'knowledge'
  page.selectedBotId.value = 1
  const old = page.loadSelected()
  page.selectedBotId.value = 2
  service.knowledge.mockResolvedValueOnce([{ id: 2, botId: 2, title: '新角色资料', content: '有效正文' }])
  await page.loadSelected()
  rejectOld(new Error('旧角色读取失败'))
  await expect(old).resolves.toBeUndefined()
  expect(page.knowledge.value[0].botId).toBe(2)
})
