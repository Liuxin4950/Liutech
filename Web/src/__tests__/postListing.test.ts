import { effectScope, nextTick, reactive, ref } from 'vue'
import { describe, expect, it, vi } from 'vitest'
const mocks = vi.hoisted(() => ({ getPostList: vi.fn(), route: { query: {} as Record<string, string> }, push: vi.fn() }))
vi.mock('@/services/post', () => ({ PostService: { getPostList: mocks.getPostList } }))
vi.mock('vue-router', () => ({ useRoute: () => mocks.route, useRouter: () => ({ push: mocks.push }) }))
import { usePostListing } from '@/composables/usePostListing'

describe('分类和标签列表', () => {
  it('快速切换后旧列表不能覆盖新结果；排序重置页码', async () => {
    mocks.route = reactive({ query: { page: '2' } })
    const resolves: ((value: unknown) => void)[] = []
    mocks.getPostList.mockImplementation(() => new Promise(resolve => resolves.push(resolve)))
    const scope = effectScope()
    const id = ref(1)
    const listing = scope.run(() => usePostListing('tagId', id))!
    id.value = 2
    await nextTick()
    resolves[1]!({ records: [{ id: 22 }], total: 1 })
    await nextTick()
    resolves[0]!({ records: [{ id: 11 }], total: 1 })
    await nextTick()
    expect(listing.posts.value[0]?.id).toBe(22)
    listing.changeSort('popular')
    expect(mocks.push).toHaveBeenCalledWith({ query: { sort: 'popular', page: undefined } })
    scope.stop()
  })
})
