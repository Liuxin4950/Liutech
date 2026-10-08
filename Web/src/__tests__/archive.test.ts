import { afterEach, describe, expect, it, vi } from 'vitest'
import { PostService, type PageResponse, type PostListItem } from '@/services/post'

vi.mock('@/services/api', () => ({ get: vi.fn(), post: vi.fn(), put: vi.fn(), del: vi.fn() }))
vi.mock('@/stores/postInteraction', () => ({ usePostInteractionStore: vi.fn() }))

const record = (id: number) => ({ id, title: `文章 ${id}`, createdAt: '2026-10-08T00:00:00' } as PostListItem)
const pageResult = (records: PostListItem[], total: number, current: number, size = 200): PageResponse<PostListItem> => ({
  records, total, current, size, pages: Math.ceil(total / size)
})

afterEach(() => vi.restoreAllMocks())

describe('文章归档分页', () => {
  it('超过 1000 篇仍按合法页大小读取完整列表', async () => {
    const getPage = vi.spyOn(PostService, 'getPostList').mockImplementation(async ({ page = 1, size = 200 } = {}) => {
      const start = (page - 1) * size
      return pageResult(Array.from({ length: Math.min(size, 1201 - start) }, (_, i) => record(start + i + 1)), 1201, page, size)
    })

    const records = await PostService.getArchivePosts()
    expect(records).toHaveLength(1201)
    expect(records[records.length - 1]?.id).toBe(1201)
    expect(getPage).toHaveBeenCalledTimes(7)
    expect(getPage).toHaveBeenLastCalledWith({ page: 7, size: 200, sortBy: 'latest' })
  })

  it('空归档只请求第一页', async () => {
    const getPage = vi.spyOn(PostService, 'getPostList').mockResolvedValue(pageResult([], 0, 1))
    await expect(PostService.getArchivePosts()).resolves.toEqual([])
    expect(getPage).toHaveBeenCalledTimes(1)
  })

  it('中途请求失败不返回部分归档', async () => {
    vi.spyOn(PostService, 'getPostList')
      .mockResolvedValueOnce(pageResult([record(1)], 201, 1))
      .mockRejectedValueOnce(new Error('网络中断'))
    await expect(PostService.getArchivePosts()).rejects.toThrow('网络中断')
  })

  it('翻页期间文章总数变化时提示重载', async () => {
    vi.spyOn(PostService, 'getPostList')
      .mockResolvedValueOnce(pageResult([record(1)], 201, 1))
      .mockResolvedValueOnce(pageResult([record(2)], 202, 2))
    await expect(PostService.getArchivePosts()).rejects.toThrow('重新加载归档')
  })

  it('分页重复或缺失文章不能伪装成完整归档', async () => {
    const getPage = vi.spyOn(PostService, 'getPostList')
      .mockResolvedValueOnce(pageResult([record(1)], 201, 1))
      .mockResolvedValueOnce(pageResult([record(1)], 201, 2))
    await expect(PostService.getArchivePosts()).rejects.toThrow('重新加载归档')
    getPage.mockResolvedValue(pageResult([record(1)], 2, 1))
    await expect(PostService.getArchivePosts()).rejects.toThrow('未加载完整')
  })
})
