import { beforeEach, expect, it, vi } from 'vitest'
import { message } from 'ant-design-vue'
import { useTablePage } from '../src/composables/useTablePage'

vi.mock('ant-design-vue', () => ({ message: { error: vi.fn() } }))
beforeEach(() => vi.clearAllMocks())

const response = (ids: number[], total = ids.length) => ({
  code: 200,
  data: { records: ids.map(id => ({ id })), total },
})
function deferred<T>() {
  let resolve!: (value: T) => void
  let reject!: (reason: unknown) => void
  const promise = new Promise<T>((res, rej) => { resolve = res; reject = rej })
  return { promise, resolve, reject }
}
function createPage(loadFn: (params: Record<string, unknown>) => Promise<ReturnType<typeof response>>) {
  return useTablePage({ loadFn, defaultSearchParams: { keyword: '' }, autoLoad: false })
}

it('慢旧响应不能覆盖新筛选的数据、总数或 loading', async () => {
  const oldRequest = deferred<ReturnType<typeof response>>()
  const currentRequest = deferred<ReturnType<typeof response>>()
  const loadFn = vi.fn().mockReturnValueOnce(oldRequest.promise).mockReturnValueOnce(currentRequest.promise)
  const page = createPage(loadFn)
  page.searchParams.value.keyword = 'old'
  const oldLoad = page.load()
  page.searchParams.value.keyword = 'new'
  const currentLoad = page.load()
  oldRequest.resolve(response([1], 50))
  await oldLoad
  expect(page.dataSource.value).toEqual([])
  expect(page.pagination.total).toBe(0)
  expect(page.loading.value).toBe(true)
  currentRequest.resolve(response([2], 20))
  await currentLoad
  expect(page.dataSource.value).toEqual([{ id: 2 }])
  expect(page.pagination.total).toBe(20)
  expect(page.loading.value).toBe(false)
  expect(loadFn.mock.calls.map(([params]) => params.keyword)).toEqual(['old', 'new'])
})

it('新响应先返回后，迟到的旧响应仍被忽略', async () => {
  const oldRequest = deferred<ReturnType<typeof response>>()
  const currentRequest = deferred<ReturnType<typeof response>>()
  const page = createPage(vi.fn().mockReturnValueOnce(oldRequest.promise).mockReturnValueOnce(currentRequest.promise))
  const oldLoad = page.load()
  const currentLoad = page.load()
  currentRequest.resolve(response([2], 20))
  await currentLoad
  oldRequest.resolve(response([1], 50))
  await oldLoad
  expect(page.dataSource.value).toEqual([{ id: 2 }])
  expect(page.pagination.total).toBe(20)
})

it('过时请求失败不弹新的加载错误，也不结束当前请求的 loading', async () => {
  const oldRequest = deferred<ReturnType<typeof response>>()
  const currentRequest = deferred<ReturnType<typeof response>>()
  const page = createPage(vi.fn().mockReturnValueOnce(oldRequest.promise).mockReturnValueOnce(currentRequest.promise))
  const oldLoad = page.load()
  const currentLoad = page.load()
  oldRequest.reject(new Error('old request failed'))
  await oldLoad
  expect(message.error).not.toHaveBeenCalled()
  expect(page.loading.value).toBe(true)
  currentRequest.resolve(response([2]))
  await currentLoad
})

it('删除末页最后一项后重载有效页，并清除隐藏选择', async () => {
  const loadFn = vi.fn().mockResolvedValueOnce(response([], 10)).mockResolvedValueOnce(response([1, 2], 10))
  const page = createPage(loadFn)
  page.pagination.current = 2
  page.selectedRowKeys.value = [11]
  await page.load()
  expect(loadFn.mock.calls.map(([params]) => params.page)).toEqual([2, 1])
  expect(page.pagination.current).toBe(1)
  expect(page.pagination.total).toBe(10)
  expect(page.dataSource.value).toEqual([{ id: 1 }, { id: 2 }])
  expect(page.selectedRowKeys.value).toEqual([])
  expect(page.loading.value).toBe(false)
})

it('当前筛选无结果时回到第一页，不无限重载', async () => {
  const loadFn = vi.fn().mockResolvedValue(response([]))
  const page = createPage(loadFn)
  page.pagination.current = 3
  await page.load()
  expect(loadFn).toHaveBeenCalledTimes(2)
  expect(page.pagination.current).toBe(1)
  expect(page.dataSource.value).toEqual([])
})

it('搜索、重置、翻页都清除上一结果集的选择', async () => {
  const page = createPage(vi.fn().mockResolvedValue(response([1], 10)))
  page.selectedRowKeys.value = [1]
  page.handleSearch()
  expect(page.selectedRowKeys.value).toEqual([])
  page.selectedRowKeys.value = [1]
  page.handleReset()
  expect(page.selectedRowKeys.value).toEqual([])
  page.selectedRowKeys.value = [1]
  page.handleTableChange({ current: 2, pageSize: 5 })
  expect(page.selectedRowKeys.value).toEqual([])
  await vi.waitFor(() => expect(page.loading.value).toBe(false))
})

it('刷新同一结果集时只保留仍存在的行选择', async () => {
  const page = createPage(vi.fn().mockResolvedValue(response([1, 2], 10)))
  page.selectedRowKeys.value = [1, 3]
  await page.load()
  expect(page.selectedRowKeys.value).toEqual([1])
})
