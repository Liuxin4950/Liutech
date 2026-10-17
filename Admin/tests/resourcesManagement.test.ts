import { readFileSync } from 'node:fs'
import { createRequire } from 'node:module'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'

const webRequire = createRequire(new URL('../../Web/package.json', import.meta.url))
const vue = webRequire('vue')
const { parse, compileScript, compileTemplate } = webRequire('@vue/compiler-sfc')
const ts = webRequire('typescript')
const source = readFileSync(new URL('../src/views/admin/ResourcesManagement.vue', import.meta.url), 'utf8')
const { descriptor } = parse(source)
const script = compileScript(descriptor, { id: 'resources-detail-regression' })
const compiled = ts.transpileModule(script.content, {
  compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 },
}).outputText
const service = { getResourceById: vi.fn() }
const router = { push: vi.fn() }
const scopes: { stop(): void }[] = []

function createPage() {
  const module = { exports: {} as any }
  const imports: Record<string, unknown> = {
    vue,
    'vue-router': { useRouter: () => router },
    'ant-design-vue': { message: { error: vi.fn(), warning: vi.fn() } },
    '@ant-design/icons-vue': {},
    '@/components/LtStatusTag.vue': {},
    '@/components/LtFilterBar.vue': {},
    '@/components/LtTableCard.vue': {},
    '../../services/resources': { __esModule: true, default: service },
    '../../utils/utils': { formatDateTime: (value: string) => value },
    '@/composables': {
      useTablePage: () => ({ selectedRowKeys: vue.ref([]), dataSource: vue.ref([]), searchParams: vue.ref({}), pagination: {}, load: vi.fn() }),
      useCrudActions: () => ({}),
      useModalForm: () => ({}),
    },
    '@/composables/useTableColumnPrefs': { useTableColumnPrefs: () => ({ prefColumns: vue.ref([]) }) },
    '@/composables/useTableExport': { useTableExport: () => ({}) },
    '@/components/TableColumnSettings.vue': {},
    '@/components/TableExportButton.vue': {},
  }
  new Function('require', 'module', 'exports', compiled)((name: string) => {
    if (!(name in imports)) throw new Error(`Unexpected component dependency: ${name}`)
    return imports[name]
  }, module, module.exports)
  const scope = vue.effectScope()
  scopes.push(scope)
  return scope.run(() => module.exports.default.setup({}, { expose() {} }))
}

function deferred() {
  let resolve!: (value: unknown) => void
  const promise = new Promise(res => { resolve = res })
  return { promise, resolve }
}

beforeEach(() => { vi.resetAllMocks() })
afterEach(() => { for (const scope of scopes.splice(0)) scope.stop() })

it('真实详情抽屉模板可编译，35 个关联文章完整保留', async () => {
  const template = compileTemplate({ source: descriptor.template.content, filename: 'ResourcesManagement.vue', id: 'resources-detail-regression' })
  expect(template.errors).toEqual([])
  const page = createPage()
  const resource = { id: 7, name: '资源', associatedPosts: Array.from({ length: 35 }, (_, index) => ({ postId: index + 1 })) }
  service.getResourceById.mockResolvedValue({ data: resource })
  await page.openDetail({ id: 7, name: '资源' })
  expect(service.getResourceById).toHaveBeenCalledWith(7)
  expect(page.detailResource.value.associatedPosts).toHaveLength(35)
  expect(page.detailLoading.value).toBe(false)
})

it('快速切换详情及关闭抽屉时，旧响应不覆盖新的资源', async () => {
  const first = deferred()
  const second = deferred()
  service.getResourceById.mockReturnValueOnce(first.promise).mockReturnValueOnce(second.promise)
  const page = createPage()
  const openingFirst = page.openDetail({ id: 1, name: '旧资源' })
  const openingSecond = page.openDetail({ id: 2, name: '新资源' })
  first.resolve({ data: { id: 1 } })
  await openingFirst
  expect(page.detailResource.value).toBeNull()
  expect(page.detailLoading.value).toBe(true)
  page.closeDetail()
  second.resolve({ data: { id: 2 } })
  await openingSecond
  expect(page.detailVisible.value).toBe(false)
  expect(page.detailResource.value).toBeNull()
  expect(page.detailLoading.value).toBe(false)
})

it('外链拒绝脚本协议、协议相对地址和URL凭据，只接受完整 HTTP(S)', () => {
  const page = createPage()
  for (const externalLink of ['javascript:alert(1)', 'data:text/html,test', '//example.com/file', 'https://user:pass@example.com']) {
    page.detailResource.value = { externalLink }
    expect(page.safeExternalLink.value).toBeUndefined()
  }
  page.detailResource.value = { externalLink: 'https://example.com/resource' }
  expect(page.safeExternalLink.value).toBe('https://example.com/resource')
})

it('关联文章使用后台编辑入口，软删文章明确显示状态并定位包含删除数据的列表', () => {
  const page = createPage()
  page.openAssociatedPost({ postId: 42, title: '测试文章', status: 'draft' })
  expect(router.push).toHaveBeenLastCalledWith({ name: 'PostsManagement', query: { edit: '42' } })
  const deleted = { postId: 43, title: '已删除文章', status: 'published', deletedAt: '2026-10-11' }
  expect(page.getPostStatusLabel(deleted)).toBe('已删除')
  page.openAssociatedPost(deleted)
  expect(router.push).toHaveBeenLastCalledWith({ name: 'PostsManagement', query: { includeDeleted: 'true', title: '已删除文章' } })
})
