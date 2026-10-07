import { beforeEach, expect, it, vi } from 'vitest'
import { message } from 'ant-design-vue'
import { useCrudActions } from '../src/composables/useCrudActions'
import { createHttpClient } from '../src/services/httpClient'

vi.mock('../src/router', () => ({ default: { currentRoute: { value: { path: '/' } }, push: vi.fn() } }))
vi.mock('../src/utils/auth', () => ({ getToken: () => null, removeToken: vi.fn() }))

vi.mock('ant-design-vue', () => ({
  Button: { name: 'TestButton' },
  message: {
    success: vi.fn(),
    warning: vi.fn(),
    error: vi.fn(),
    destroy: vi.fn(),
  },
}))

beforeEach(() => { vi.clearAllMocks() })

function deferred<T = void>() {
  let resolve!: (value: T | PromiseLike<T>) => void
  let reject!: (reason?: unknown) => void
  const promise = new Promise<T>((res, rej) => { resolve = res; reject = rej })
  return { promise, resolve, reject }
}

function undoButton() {
  const options = vi.mocked(message.success).mock.calls.find(([content]) => typeof content === 'object')?.[0] as any
  expect(options).toBeDefined()
  return options.content().children[1].props.onClick as () => void
}

async function waitForUndo() {
  await vi.waitFor(() => expect(message.destroy).toHaveBeenCalled())
  await new Promise(resolve => setTimeout(resolve, 0))
}

it('单条恢复 fallback 全部完成才按实际数量提示，撤销使用删除时的 ID 快照', async () => {
  const ids = [1, 2]
  const restoreFn = vi.fn().mockResolvedValue({})
  const onRefresh = vi.fn()
  const actions = useCrudActions({
    batchDeleteFn: vi.fn().mockResolvedValue({}), restoreFn, onRefresh,
    clearSelection: () => ids.splice(0),
  })
  await actions.handleBatchDelete(ids)
  expect(ids).toEqual([])
  const undo = undoButton()
  undo()
  undo()
  await waitForUndo()
  expect(restoreFn.mock.calls).toEqual([[1], [2]])
  expect(message.success).toHaveBeenCalledWith('已撤销删除，共恢复 2 条')
  expect(onRefresh).toHaveBeenCalledTimes(2)
  expect(actions.loading.value).toBe(false)
})

it('部分撤销失败显示真实数量，保留业务错误提示并刷新已经恢复的数据', async () => {
  const restoreFn = vi.fn().mockImplementation((id: number) => id === 1
    ? Promise.resolve({}) : Promise.reject({ isBusiness: true }))
  const onRefresh = vi.fn()
  const actions = useCrudActions({ batchDeleteFn: vi.fn().mockResolvedValue({}), restoreFn, onRefresh })
  await actions.handleBatchDelete([1, 2])
  undoButton()()
  await waitForUndo()
  expect(message.warning).toHaveBeenCalledWith('已恢复 1 条，1 条撤销失败')
  expect(message.success).not.toHaveBeenCalledWith('已撤销删除，共恢复 2 条')
  expect(message.error).not.toHaveBeenCalled()
  expect(onRefresh).toHaveBeenCalledTimes(2)
})

it('HTTP 200 的业务失败仍由现有拦截器拒绝，撤销统计包含该失败', async () => {
  const client = createHttpClient({ baseURL: '/api', normalizeResponse: true })
  client.defaults.adapter = async config => ({
    config, status: 200, statusText: 'OK', headers: {},
    data: config.url?.endsWith('/1')
      ? { code: 200, message: 'ok', data: '恢复成功' }
      : { code: 400, message: '记录已被彻底删除', data: null },
  })
  const actions = useCrudActions({
    batchDeleteFn: vi.fn().mockResolvedValue({}),
    restoreFn: async id => (await client.put(`/restore/${id}`)).data,
    onRefresh: vi.fn(),
  })
  await actions.handleBatchDelete([1, 2])
  undoButton()()
  await waitForUndo()
  expect(message.error).toHaveBeenCalledExactlyOnceWith('记录已被彻底删除')
  expect(message.warning).toHaveBeenCalledWith('已恢复 1 条，1 条撤销失败')
  expect(message.success).not.toHaveBeenCalledWith('已撤销删除，共恢复 2 条')
})

it('code=200/data=false 的单条恢复算失败，不能计入恢复数', async () => {
  const actions = useCrudActions({
    batchDeleteFn: vi.fn().mockResolvedValue({}),
    restoreFn: vi.fn().mockResolvedValueOnce({ code: 200, message: 'ok', data: true })
      .mockResolvedValueOnce({ code: 200, message: 'ok', data: false }),
    onRefresh: vi.fn(),
  })
  await actions.handleBatchDelete([1, 2])
  undoButton()()
  await waitForUndo()
  expect(message.warning).toHaveBeenCalledWith('已恢复 1 条，1 条撤销失败')
  expect(message.success).not.toHaveBeenCalledWith('已撤销删除，共恢复 2 条')
})

it.each([
  ['deleteFn', 'handleDelete', 1],
  ['batchDeleteFn', 'handleBatchDelete', [1, 2]],
  ['restoreFn', 'handleRestore', 1],
  ['batchRestoreFn', 'handleBatchRestore', [1, 2]],
  ['permanentDeleteFn', 'handlePermanentDelete', 1],
  ['batchPermanentDeleteFn', 'handleBatchPermanentDelete', [1, 2]],
] as const)('%s 返回 data=false 时不虚报成功，不清空选择或刷新', async (callback, handler, ids) => {
  const onRefresh = vi.fn()
  const clearSelection = vi.fn()
  const actions = useCrudActions({
    [callback]: vi.fn().mockResolvedValue({ code: 200, message: 'ok', data: false }),
    onRefresh, clearSelection,
  })
  await (actions[handler] as (value: number | readonly number[]) => Promise<void>)(ids)
  expect(message.success).not.toHaveBeenCalled()
  expect(message.error).toHaveBeenCalledOnce()
  expect(onRefresh).not.toHaveBeenCalled()
  expect(clearSelection).not.toHaveBeenCalled()
  expect(actions.loading.value).toBe(false)
})

it('全部撤销失败不会虚报成功，也不会刷新未变更的数据', async () => {
  const restoreFn = vi.fn().mockRejectedValue(new Error('network'))
  const onRefresh = vi.fn()
  const actions = useCrudActions({ batchDeleteFn: vi.fn().mockResolvedValue({}), restoreFn, onRefresh })
  await actions.handleBatchDelete([1, 2])
  undoButton()()
  await waitForUndo()
  expect(message.error).toHaveBeenCalledWith('撤销失败，2 条均未恢复')
  expect(vi.mocked(message.success).mock.calls).toHaveLength(1)
  expect(onRefresh).toHaveBeenCalledTimes(1)
  expect(actions.loading.value).toBe(false)
})

it('仅配置批量恢复 API 也支持撤销，缺少逐项结果时不编造恢复数量', async () => {
  const batchRestoreFn = vi.fn().mockResolvedValue({})
  const actions = useCrudActions({ batchDeleteFn: vi.fn().mockResolvedValue({}), batchRestoreFn, onRefresh: vi.fn() })
  await actions.handleBatchDelete([1, 2])
  undoButton()()
  await waitForUndo()
  expect(batchRestoreFn).toHaveBeenCalledWith([1, 2])
  expect(message.success).toHaveBeenCalledWith('已撤销删除')
  expect(message.success).not.toHaveBeenCalledWith('已撤销删除，共恢复 2 条')
})

it('批量恢复 API 拒绝时不提示成功，业务错误由拦截器负责', async () => {
  const batchRestoreFn = vi.fn().mockRejectedValue({ isBusiness: true })
  const onRefresh = vi.fn()
  const actions = useCrudActions({ batchDeleteFn: vi.fn().mockResolvedValue({}), batchRestoreFn, onRefresh })
  await actions.handleBatchDelete([1, 2])
  undoButton()()
  await waitForUndo()
  expect(message.success).not.toHaveBeenCalledWith('已撤销删除')
  expect(message.error).not.toHaveBeenCalled()
  expect(onRefresh).toHaveBeenCalledTimes(1)
  expect(actions.loading.value).toBe(false)
})

it('操作处理中统一拒绝重复的 CRUD 提交，完成后释放加载状态', async () => {
  const pending = deferred()
  const deleteFn = vi.fn().mockReturnValue(pending.promise)
  const batchDeleteFn = vi.fn()
  const restoreFn = vi.fn()
  const permanentDeleteFn = vi.fn()
  const batchPermanentDeleteFn = vi.fn()
  const actions = useCrudActions({ deleteFn, batchDeleteFn, restoreFn, permanentDeleteFn, batchPermanentDeleteFn, onRefresh: vi.fn() })
  const deleting = actions.handleDelete(1)
  expect(actions.loading.value).toBe(true)
  await actions.handleDelete(1)
  await actions.handleBatchDelete([1])
  await actions.handleRestore(1)
  await actions.handleBatchRestore([1])
  await actions.handlePermanentDelete(1)
  await actions.handleBatchPermanentDelete([1])
  expect(deleteFn).toHaveBeenCalledTimes(1)
  for (const callback of [batchDeleteFn, restoreFn, permanentDeleteFn, batchPermanentDeleteFn]) {
    expect(callback).not.toHaveBeenCalled()
  }
  pending.resolve()
  await deleting
  expect(actions.loading.value).toBe(false)
})

it('删除后的刷新未完成时，点击撤销不会消费入口，刷新完成后可以撤销一次', async () => {
  const refresh = deferred()
  const restoreFn = vi.fn().mockResolvedValue({})
  const onRefresh = vi.fn().mockReturnValueOnce(refresh.promise).mockResolvedValue(undefined)
  const actions = useCrudActions({ deleteFn: vi.fn().mockResolvedValue({}), restoreFn, onRefresh })
  const deleting = actions.handleDelete(1)
  await vi.waitFor(() => expect(onRefresh).toHaveBeenCalledTimes(1))
  const undo = undoButton()
  undo()
  expect(restoreFn).not.toHaveBeenCalled()
  expect(message.destroy).not.toHaveBeenCalled()
  refresh.resolve()
  await deleting
  undo()
  undo()
  await waitForUndo()
  expect(restoreFn).toHaveBeenCalledTimes(1)
  expect(message.success).toHaveBeenCalledWith('已撤销删除')
  expect(actions.loading.value).toBe(false)
})

it('直接批量恢复去重 ID，成功后清空选择；fallback 部分失败保留选择', async () => {
  const clearSelection = vi.fn()
  const batchRestoreFn = vi.fn().mockResolvedValue({})
  const actions = useCrudActions({ batchRestoreFn, onRefresh: vi.fn(), clearSelection })
  await actions.handleBatchRestore([1, 1, 2])
  expect(batchRestoreFn).toHaveBeenCalledWith([1, 2])
  expect(clearSelection).toHaveBeenCalledOnce()

  clearSelection.mockClear()
  const fallback = useCrudActions({
    restoreFn: vi.fn().mockResolvedValueOnce({}).mockRejectedValueOnce(new Error('network')),
    onRefresh: vi.fn(), clearSelection,
  })
  await fallback.handleBatchRestore([1, 2])
  expect(message.warning).toHaveBeenCalledWith('已恢复 1 条，1 条恢复失败')
  expect(clearSelection).not.toHaveBeenCalled()
})

it('失败后释放操作锁，空选择不发送请求', async () => {
  const batchDeleteFn = vi.fn().mockRejectedValueOnce({ isBusiness: true }).mockResolvedValueOnce({})
  const actions = useCrudActions({ batchDeleteFn, batchRestoreFn: vi.fn(), batchPermanentDeleteFn: vi.fn(), onRefresh: vi.fn() })
  await actions.handleBatchDelete([])
  await actions.handleBatchRestore([])
  await actions.handleBatchPermanentDelete([])
  expect(batchDeleteFn).not.toHaveBeenCalled()
  expect(message.warning).toHaveBeenCalledTimes(3)
  await actions.handleBatchDelete([1])
  expect(actions.loading.value).toBe(false)
  expect(message.error).not.toHaveBeenCalled()
  await actions.handleBatchDelete([1])
  expect(batchDeleteFn).toHaveBeenCalledTimes(2)
})
