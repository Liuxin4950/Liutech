import { readFileSync } from 'node:fs'
import { createRequire } from 'node:module'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import type { CommunityBot, CommunityRun, CommunityTask } from '../src/services/community'

// Admin 的现有测试配置运行于 Node；编译真实 SFC setup，复用 Web 的 Vue/TS 依赖。
const webRequire = createRequire(new URL('../../Web/package.json', import.meta.url))
const vue = webRequire('vue')
const { parse, compileScript, compileTemplate } = webRequire('@vue/compiler-sfc')
const ts = webRequire('typescript')
const source = readFileSync(new URL('../src/views/admin/CommunityAiManagement.vue', import.meta.url), 'utf8')
const { descriptor } = parse(source)
const script = compileScript(descriptor, { id: 'community-management-regression' })
const compiled = ts.transpileModule(script.content, {
  compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 },
}).outputText

const service = {
  knowledge: vi.fn().mockResolvedValue([]),
  comments: vi.fn().mockResolvedValue({ records: [], total: 0 }),
  runs: vi.fn().mockResolvedValue([]),
  tasks: vi.fn().mockResolvedValue([]),
}
const scopes: { stop(): void }[] = []
function createPage() {
  const module = { exports: {} as any }
  const imports: Record<string, unknown> = {
    vue: { ...vue, onMounted() {}, onActivated() {}, onDeactivated() {}, onBeforeUnmount() {} },
    'ant-design-vue': { message: { error: vi.fn(), success: vi.fn(), warning: vi.fn() } },
    '@ant-design/icons-vue': {},
    '@/services/upload': {},
    '@/services/community': { communityService: service, defaultCommunitySettings: () => ({ enabled: false }) },
    '@/services/posts': { __esModule: true, default: {} },
    '@/services/comments': { __esModule: true, default: {} },
    '@/utils/utils': { formatDateTime: (value: string) => value },
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
beforeEach(() => { vi.clearAllMocks(); vi.stubGlobal('document', { hidden: true }) })
afterEach(() => { for (const scope of scopes.splice(0)) scope.stop(); vi.unstubAllGlobals() })

it('卡片选择区域是原生按钮，覆盖头像和描述，并同步右侧角色与后续操作', async () => {
  const result = compileTemplate({ source: descriptor.template.content, filename: 'CommunityAiManagement.vue', id: 'community-management-regression' })
  expect(result.errors).toEqual([])
  const findSelectButton = (node: any): any => node.tag === 'button' && node.props?.some((prop: any) => prop.name === 'class' && prop.value?.content === 'bot-select-area')
    ? node : (node.children || []).map(findSelectButton).find(Boolean)
  const selection = findSelectButton(result.ast)
  expect(selection.props.find((prop: any) => prop.name === 'on' && prop.arg?.content === 'click')?.exp.loc.source).toBe('selectBot(bot.id)')
  expect(selection.children.some((node: any) => node.props?.some((prop: any) => prop.value?.content === 'bot-description role-excerpt'))).toBe(true)
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

it('决策分布排除预演，将发布失败归入历史失败，任务恢复不与当前停止失败混淆', () => {
  const page = createPage()
  page.runs.value = [run(1, { decision: 'COMMENT' }), run(2, { decision: 'REPLY', publicationStatus: 'FAILED' }), run(3, { decision: 'SKIP' }), run(4, { status: 'PREVIEW', decision: 'COMMENT' })]
  page.tasks.value = [task(1, 'READY'), task(2, 'READY', { failures: 1 }), task(3, 'RUNNING'), task(4, 'DECIDED'), task(5, 'SUCCEEDED'), task(6, 'SKIPPED'), task(7, 'FAILED'), task(8, 'CANCELLED')]
  expect(page.decisionDistribution.value.map((entry: any) => entry.count)).toEqual([1, 0, 1, 1])
  expect(page.pendingTasks.value).toHaveLength(4)
  expect(page.failedTasks.value).toHaveLength(1)
  expect(page.taskDistribution.value.map((entry: any) => entry.count)).toEqual([1, 1, 1, 1, 1, 1, 1, 1])
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
