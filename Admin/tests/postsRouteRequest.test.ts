import { readFileSync } from 'node:fs'
import { createRequire } from 'node:module'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'

const webRequire = createRequire(new URL('../../Web/package.json', import.meta.url))
const vue = webRequire('vue')
const { parse, compileScript } = webRequire('@vue/compiler-sfc')
const ts = webRequire('typescript')
const source = readFileSync(new URL('../src/views/admin/PostsManagement.vue', import.meta.url), 'utf8')
const { descriptor } = parse(source)
const script = compileScript(descriptor, { id: 'posts-route-regression' })
const compiled = ts.transpileModule(script.content, {
  compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 },
}).outputText
const posts = { getPostById: vi.fn() }
const router = { replace: vi.fn().mockResolvedValue(undefined) }
const warning = vi.fn()
const scopes: { stop(): void }[] = []

async function createPage(query: Record<string, string> = {}) {
  const route = vue.reactive({ name: 'PostsManagement', query })
  const load = vi.fn().mockResolvedValue(undefined)
  const form = {
    modalVisible: vue.ref(false), isEdit: vue.ref(false), editingId: vue.ref(null),
    modalTitle: vue.ref(''), formRef: vue.ref(null), formModel: vue.ref({ title: '', content: '' }),
    draftSavedAt: vue.ref(null), confirmLoading: vue.ref(false),
  }
  let mount!: () => Promise<void>
  const module = { exports: {} as any }
  const imports: Record<string, unknown> = {
    vue: { ...vue, onMounted(callback: () => Promise<void>) { mount = callback }, onBeforeUnmount() {} },
    'vue-router': { useRoute: () => route, useRouter: () => router },
    'ant-design-vue': { message: { error: vi.fn(), warning, success: vi.fn() } },
    '@ant-design/icons-vue': {},
    '@/components/LtStatusTag.vue': {},
    '@/services/writingReview': { useWritingUndo: () => ({ entries: vue.ref([]), record: vi.fn(), undoField: vi.fn(), undoRound: vi.fn(), reset: vi.fn() }) },
    '@/composables': {
      useTablePage: () => ({ load, selectedRowKeys: vue.ref([]), dataSource: vue.ref([]), searchParams: vue.ref({ title: '', status: 'draft', categoryId: 99 }), pagination: {}, clearSelection: vi.fn() }),
      useCrudActions: () => ({}), useModalForm: () => form,
    },
    '@/composables/useTableColumnPrefs': { useTableColumnPrefs: () => ({ prefColumns: vue.ref([]) }) },
    '@/composables/useTableExport': { useTableExport: () => ({}) },
    '@/components/TableColumnSettings.vue': {}, '@/components/TableExportButton.vue': {},
    '../../services/posts': { __esModule: true, default: posts },
    '../../services/categories': { __esModule: true, default: {} },
    '../../services/tags': { __esModule: true, default: {} },
    '../../services/series': { __esModule: true, default: {} },
    '../../services/pagination': { loadAllPages: vi.fn().mockResolvedValue([]) },
    '../../utils/utils': { formatDateTime: (value: string) => value },
    '../../components/TinyMCEEditor.vue': {}, '../../services/upload': {},
    '../../components/agent/AdminAgentSidebar.vue': {},
  }
  new Function('require', 'module', 'exports', compiled)((name: string) => {
    if (!(name in imports)) throw new Error(`Unexpected component dependency: ${name}`)
    return imports[name]
  }, module, module.exports)
  const scope = vue.effectScope()
  scopes.push(scope)
  const page = scope.run(() => module.exports.default.setup({}, { expose() {} }))
  await mount()
  return { page, route, form, load }
}

beforeEach(() => { vi.clearAllMocks() })
afterEach(() => { for (const scope of scopes.splice(0)) scope.stop() })

it('关联跳转直接按 ID 获取详情并消耗查询参数，无需在第一页寻找文章', async () => {
  posts.getPostById.mockResolvedValue({ data: { id: 42, title: '文章', content: '完整正文', status: 'published' } })
  const { form } = await createPage({ edit: '42' })
  expect(posts.getPostById).toHaveBeenCalledWith(42)
  expect(form.modalVisible.value).toBe(true)
  expect(form.formModel.value).toMatchObject({ id: 42, content: '完整正文' })
  expect(router.replace).toHaveBeenCalledWith({ name: 'PostsManagement', query: {} })
})

it('回收站定位清除旧筛选，KeepAlive 中新的关联编辑请求可执行', async () => {
  const { page, route, form } = await createPage({ includeDeleted: 'true', title: '已删除文章' })
  expect(page.searchParams.value).toEqual({ includeDeleted: true, title: '已删除文章' })
  posts.getPostById.mockResolvedValue({ data: { id: 43, title: '其它文章', status: 'draft' } })
  route.name = 'ResourcesManagement'
  route.query = {}
  await vue.nextTick()
  route.name = 'PostsManagement'
  route.query = { edit: '43' }
  await vi.waitFor(() => expect(form.modalVisible.value).toBe(true))
  expect(form.editingId.value).toBe(43)
})

it('不存在或已删除文章不会打开空编辑窗，已有编辑草稿不被关联跳转覆盖', async () => {
  posts.getPostById.mockRejectedValue({ isBusiness: true })
  const { form, route } = await createPage({ edit: '404' })
  expect(form.modalVisible.value).toBe(false)
  expect(router.replace).toHaveBeenCalledWith({ name: 'PostsManagement', query: {} })
  form.modalVisible.value = true
  form.isEdit.value = true
  form.editingId.value = 1
  form.formModel.value = { title: '未保存内容', content: '草稿' }
  posts.getPostById.mockClear()
  route.query = { edit: '2' }
  await vue.nextTick()
  expect(warning).toHaveBeenCalledWith('请先完成或关闭当前文章编辑，再打开关联文章')
  expect(posts.getPostById).not.toHaveBeenCalled()
  expect(form.formModel.value.title).toBe('未保存内容')
})
