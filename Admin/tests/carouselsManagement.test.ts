import { readFileSync } from 'node:fs'
import { createRequire } from 'node:module'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'

const webRequire = createRequire(new URL('../../Web/package.json', import.meta.url))
const vue = webRequire('vue')
const { parse, compileScript } = webRequire('@vue/compiler-sfc')
const ts = webRequire('typescript')
const source = readFileSync(new URL('../src/views/admin/CarouselsManagement.vue', import.meta.url), 'utf8')
const { descriptor } = parse(source)
const script = compileScript(descriptor, { id: 'carousels-sort-regression' })
const compiled = ts.transpileModule(script.content, {
  compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 },
}).outputText
const service = {
  getCarouselList: vi.fn(),
  createCarousel: vi.fn(),
  updateCarousel: vi.fn(),
  deleteCarousel: vi.fn(),
  batchDeleteCarousels: vi.fn(),
  updateCarouselStatus: vi.fn(),
  updateSortOrder: vi.fn(),
  restoreCarousel: vi.fn(),
  permanentDeleteCarousel: vi.fn(),
  batchPermanentDeleteCarousels: vi.fn(),
}
const upload = vi.fn()
const scopes: { stop(): void }[] = []
const record = (id = 1) => ({ id, title: `轮播 ${id}`, imageUrl: `/uploads/images/${id}.png`, sortOrder: id, status: 1 })

function createPage() {
  const load = vi.fn().mockResolvedValue(undefined)
  const pagination = vue.reactive({ current: 1, pageSize: 500, total: 0, showSizeChanger: true, showQuickJumper: true })
  const module = { exports: {} as any }
  const imports: Record<string, unknown> = {
    vue,
    'ant-design-vue': { message: { error: vi.fn(), warning: vi.fn(), success: vi.fn() } },
    '@ant-design/icons-vue': {},
    '@/components/LtStatusTag.vue': {},
    '@/components/LtFilterBar.vue': {},
    '@/components/LtTableCard.vue': {},
    '@/composables': {
      useTablePage: () => ({
        loading: vue.ref(false),
        selectedRowKeys: vue.ref([]),
        dataSource: vue.ref([]),
        searchParams: vue.ref({ status: undefined, includeDeleted: false }),
        pagination,
        load,
        handleSearch: vi.fn(),
        handleReset: vi.fn(),
        handleTableChange: vi.fn(),
        onSelectChange: vi.fn(),
        clearSelection: vi.fn(),
      }),
      useCrudActions: () => ({
        handleDelete: vi.fn(),
        handleBatchDelete: vi.fn(),
        handleRestore: vi.fn(),
        handlePermanentDelete: vi.fn(),
        handleBatchPermanentDelete: vi.fn(),
      }),
      useModalForm: (value: any) => ({
        modalVisible: vue.ref(false),
        modalTitle: vue.ref(''),
        isEdit: vue.ref(false),
        editingId: vue.ref(null),
        confirmLoading: vue.ref(false),
        formRef: vue.ref(null),
        formModel: vue.ref(value.defaultForm()),
        handleCancel: vi.fn(),
      }),
    },
    '@/composables/useTableColumnPrefs': { useTableColumnPrefs: () => ({ prefColumns: vue.ref([]) }) },
    '@/composables/useTableExport': { useTableExport: () => ({}) },
    '@/components/TableColumnSettings.vue': {},
    '@/components/TableExportButton.vue': {},
    '../../services/carousel': { __esModule: true, default: service },
    '../../utils/utils': { formatDateTime: (value: string) => value, formatRelativeTime: (value: string) => value },
    '../../services/upload': { ImageUploadService: { uploadImage: upload }, pickUploadFile: (value: any) => value.file },
  }
  new Function('require', 'module', 'exports', compiled)((name: string) => {
    if (!(name in imports)) throw new Error(`Unexpected component dependency: ${name}`)
    return imports[name]
  }, module, module.exports)
  const scope = vue.effectScope()
  scopes.push(scope)
  const page = scope.run(() => module.exports.default.setup({}, { expose() {} }))
  return { page, load, pagination }
}

beforeEach(() => {
  vi.resetAllMocks()
})
afterEach(() => { for (const scope of scopes.splice(0)) scope.stop(); vi.restoreAllMocks() })

it('全量加载后关闭每页大小切换，保持 dataSource 全集语义', () => {
  const { page, pagination } = createPage()
  expect(pagination.showSizeChanger).toBe(false)
  expect(pagination.showQuickJumper).toBe(false)
  expect(page.sortFiltered.value).toBe(false)
})

it('筛选生效、列表加载中或分页截断时拒绝排序，不提交部分列表', async () => {
  const { page, pagination } = createPage()
  page.dataSource.value = [record(1), record(2), record(3)]

  page.searchParams.value.status = 0
  await page.handleSortChange(2, 'up')
  expect(service.updateSortOrder).not.toHaveBeenCalled()

  page.searchParams.value.status = undefined
  page.searchParams.value.includeDeleted = true
  await page.handleSortChange(2, 'up')
  expect(service.updateSortOrder).not.toHaveBeenCalled()

  page.searchParams.value.includeDeleted = false
  pagination.total = 30
  expect(page.sortFiltered.value).toBe(true)
  await page.handleSortChange(2, 'up')
  expect(service.updateSortOrder).not.toHaveBeenCalled()

  pagination.total = 3
  page.loading.value = true
  await page.handleSortChange(2, 'up')
  expect(service.updateSortOrder).not.toHaveBeenCalled()
  expect(page.dataSource.value.map((item: any) => item.id)).toEqual([1, 2, 3])
})

it('排序提交重排后的完整 id 列表，成功后刷新列表', async () => {
  service.updateSortOrder.mockResolvedValueOnce({ code: 200, data: true })
  const { page, load } = createPage()
  page.dataSource.value = [record(1), record(2), record(3)]
  await page.handleSortChange(2, 'up')
  expect(service.updateSortOrder).toHaveBeenCalledTimes(1)
  expect(service.updateSortOrder).toHaveBeenCalledWith([2, 1, 3])
  await vi.waitFor(() => expect(load).toHaveBeenCalledOnce())
})

it('排序失败后回滚展示顺序并重新加载列表', async () => {
  service.updateSortOrder.mockRejectedValueOnce(new Error('network'))
  const { page, load } = createPage()
  page.dataSource.value = [record(1), record(2), record(3)]
  await page.handleSortChange(3, 'up')
  expect(service.updateSortOrder).toHaveBeenCalledWith([1, 3, 2])
  await vi.waitFor(() => expect(load).toHaveBeenCalledOnce())
  expect(page.dataSource.value.map((item: any) => item.id)).toEqual([1, 2, 3])
  expect(page.sortLoading.value).toBe(false)
})

it('保存失败时立即回滚，即使重载未完成或失败也不保留未保存顺序', async () => {
  service.updateSortOrder.mockRejectedValueOnce(new Error('network'))
  const { page, load } = createPage()
  let finishReload!: () => void
  // useTablePage.load 会捕获重载错误，失败时保留现有列表并正常返回。
  load.mockImplementationOnce(() => new Promise<void>((resolve) => { finishReload = resolve }))
  page.dataSource.value = [record(1), record(2), record(3)]
  const sorting = page.handleSortChange(2, 'up')
  await vi.waitFor(() => expect(load).toHaveBeenCalledOnce())
  expect(page.dataSource.value.map((item: any) => item.id)).toEqual([1, 2, 3])
  expect(page.sortLoading.value).toBe(true)
  await page.handleSortChange(3, 'up')
  expect(service.updateSortOrder).toHaveBeenCalledTimes(1)
  finishReload()
  await sorting
  expect(page.dataSource.value.map((item: any) => item.id)).toEqual([1, 2, 3])
  expect(page.sortLoading.value).toBe(false)
})

it('排序失败不会用原顺序覆盖期间新查询返回的列表', async () => {
  let failSort!: (reason: Error) => void
  service.updateSortOrder.mockImplementationOnce(() => new Promise((_resolve, reject) => { failSort = reject }))
  const { page } = createPage()
  page.dataSource.value = [record(1), record(2), record(3)]
  const sorting = page.handleSortChange(2, 'up')
  page.dataSource.value = [record(4)]
  failSort(new Error('network'))
  await sorting
  expect(page.dataSource.value.map((item: any) => item.id)).toEqual([4])
})
