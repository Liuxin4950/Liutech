import { reactive, nextTick } from 'vue'
import { createPinia, setActivePinia } from 'pinia'
import { mount, flushPromises } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => ({ user: null as any, activities: vi.fn(), del: vi.fn(), get: vi.fn(), stats: vi.fn(), confirm: vi.fn() }))
vi.mock('@/stores/user', () => ({ useUserStore: () => mocks.user }))
vi.mock('@/services/userActivity', () => ({ getActivities: mocks.activities }))
vi.mock('@/services/api', () => ({ get: mocks.get, del: mocks.del, post: vi.fn(), put: vi.fn() }))
vi.mock('@/services/user', () => ({ UserService: { getUserStats: mocks.stats } }))
vi.mock('@/composables/useErrorHandler', () => ({ useErrorHandler: () => ({
  confirm: mocks.confirm, showToastSuccess: vi.fn(), showToastError: vi.fn(),
  handleAsync: async (action: () => Promise<void>, options: any) => {
    try { await action() } catch { options.onError?.() } finally { options.onFinally?.() }
  }
}) }))
vi.mock('@/utils/errorHandler', () => ({ showSuccess: vi.fn(), showError: vi.fn() }))
vi.mock('vue-router', () => ({ useRouter: () => ({ push: vi.fn() }) }))

import UserActivities from '@/components/UserActivities.vue'
import ProfileLibrary from '@/components/ProfileLibrary.vue'
import Profile from '@/views/Profile.vue'
import ViewHistory from '@/views/ViewHistory.vue'
import PostService from '@/services/post'
import { usePostInteractionStore } from '@/stores/postInteraction'

const page = (records: any[], current = 1, pages = 1) => ({ records, total: records.length, current, size: 5, pages })
const options = { global: { stubs: { RouterLink: { template: '<a><slot /></a>' }, Icon: true, CheckinCard: true } } }

beforeEach(() => {
  vi.clearAllMocks()
  setActivePinia(createPinia())
  mocks.user = reactive({ isLoggedIn: true, userInfo: { id: 1, username: 'reader' } })
  mocks.stats.mockResolvedValue({ points: 10, viewCount: 1 })
  mocks.del.mockResolvedValue({ data: true })
  mocks.confirm.mockResolvedValue(true)
})

describe('个人页内容同步', () => {
  it('清空成功后浏览动态消失，收藏动态保留；失败不会误清空', async () => {
    mocks.activities.mockResolvedValueOnce(page([
      { id: 'view-1', type: 'view', title: '浏览记录', occurredAt: '2026-09-30T10:00:00' },
      { id: 'favorite-1', type: 'favorite', title: '收藏文章', occurredAt: '2026-09-30T09:00:00' }
    ])).mockResolvedValue(page([{ id: 'favorite-1', type: 'favorite', title: '收藏文章', occurredAt: '2026-09-30T09:00:00' }]))
    const wrapper = mount(UserActivities, options)
    await flushPromises()
    expect(wrapper.text()).toContain('浏览记录')
    await PostService.clearViewHistory()
    await flushPromises()
    expect(wrapper.text()).not.toContain('浏览记录')
    expect(wrapper.text()).toContain('收藏文章')
    const revision = usePostInteractionStore().historyRevision
    mocks.del.mockRejectedValueOnce(new Error('offline'))
    await expect(PostService.clearViewHistory()).rejects.toThrow('offline')
    expect(usePostInteractionStore().historyRevision).toBe(revision)
    wrapper.unmount()
  })

  it('个人页清空入口同步刷新浏览统计，移除成就装饰', async () => {
    mocks.activities.mockResolvedValue(page([]))
    mocks.get.mockResolvedValue({ data: page([]) })
    const wrapper = mount(Profile, options)
    await flushPromises()
    expect(wrapper.text()).toContain('收藏文章')
    expect(wrapper.text()).not.toContain('成就徽章')
    const button = wrapper.findAll('button').find(item => item.text() === '清空浏览记录')!
    await button.trigger('click')
    await flushPromises()
    expect(mocks.del).toHaveBeenCalledWith('/posts/view-history')
    expect(mocks.stats).toHaveBeenCalledTimes(2)
    wrapper.unmount()
  })

  it('切换到已购资源后，晚返回的收藏不能覆盖新面板', async () => {
    let finishFavorites!: (value: any) => void
    mocks.get.mockImplementation((url: string) => url === '/posts/favorites'
      ? new Promise(resolve => { finishFavorites = resolve })
      : Promise.resolve({ data: page([{ id: 1, resourceId: 2, resourceName: '实用工具.zip', pointsUsed: 10, purchasedAt: '2026-09-30T10:00:00', available: false }]) }))
    const wrapper = mount(ProfileLibrary, options)
    await nextTick()
    await wrapper.find('#purchases-tab').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('实用工具.zip')
    expect(wrapper.text()).toContain('资源已下架')
    finishFavorites({ data: page([{ id: 9, title: '过期收藏' }]) })
    await flushPromises()
    expect(wrapper.text()).not.toContain('过期收藏')
    expect(wrapper.text()).toContain('实用工具.zip')
    wrapper.unmount()
  })

  it('退出登录时清除当前动态，并忽略旧请求', async () => {
    let finish!: (value: any) => void
    mocks.activities.mockImplementation(() => new Promise(resolve => { finish = resolve }))
    const wrapper = mount(UserActivities, options)
    mocks.user.isLoggedIn = false
    mocks.user.userInfo = null
    await nextTick()
    finish(page([{ id: 'view-1', type: 'view', title: '旧用户文章', occurredAt: '2026-09-30T10:00:00' }]))
    await flushPromises()
    expect(wrapper.text()).not.toContain('旧用户文章')
    expect(wrapper.text()).not.toContain('正在加载')
    wrapper.unmount()
  })
  it('浏览历史旧 GET 在清空之后完成也不能回填列表', async () => {
    let finish!: (value: any) => void
    mocks.get.mockImplementation(() => new Promise(resolve => { finish = resolve }))
    const wrapper = mount(ViewHistory, { global: { stubs: { Icon: true, ArticleList: {
      props: ['posts'], template: '<div><span v-for="post in posts" :key="post.id">{{ post.title }}</span></div>'
    } } } })
    await wrapper.find('.clear-btn').trigger('click')
    await flushPromises()
    expect(mocks.del).toHaveBeenCalledWith('/posts/view-history')
    finish({ data: page([{ id: 99, title: '过期历史' }]) })
    await flushPromises()
    expect(wrapper.text()).not.toContain('过期历史')
    wrapper.unmount()
  })

})
