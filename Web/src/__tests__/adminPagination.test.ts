import { describe, expect, it, vi } from 'vitest'
import { loadAllPages } from '../../../Admin/src/services/pagination'

describe('管理端全量选项分页', () => {
  it('超过单页上限时逐页取全，不截断排序数据', async () => {
    const fetchPage = vi.fn(async (page: number, size: number) => ({ code: 200, message: 'ok', data: {
      records: Array.from({ length: page === 3 ? 1 : 200 }, (_, i) => (page - 1) * size + i),
      total: 401, current: page, size, pages: 3
    } }))
    const records = await loadAllPages(fetchPage)
    expect(records).toHaveLength(401)
    expect(records[400]).toBe(400)
    expect(fetchPage).toHaveBeenCalledTimes(3)
    expect(fetchPage).toHaveBeenLastCalledWith(3, 200)
  })
  it('失败和中途空页不会伪装成完整列表', async () => {
    await expect(loadAllPages(async () => ({ code: 500, message: 'error', data: null as never }))).rejects.toThrow('error')
    await expect(loadAllPages(async page => ({ code: 200, message: 'ok', data: {
      records: page === 1 ? [1] : [], total: 2, current: page, size: 200, pages: 2
    } }))).rejects.toThrow('重新加载')
  })
})
