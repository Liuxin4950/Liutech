import { defineComponent, nextTick } from 'vue'
import type { Component } from 'vue'
import { createPinia } from 'pinia'
import { createMemoryHistory, createRouter, RouterView } from 'vue-router'
import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { PostDetail } from '@/services/post'

const mocks = vi.hoisted(() => ({ getPostDetail: vi.fn() }))
vi.mock('@/services/post', () => ({ PostService: { getPostDetail: mocks.getPostDetail } }))
vi.mock('@vueuse/head', () => ({ useHead: vi.fn() }))
vi.mock('@/utils/auth', () => ({ isLoggedIn: () => false }))
vi.mock('@/stores/user', () => ({ useUserStore: () => ({ isLoggedIn: false }) }))
vi.mock('@/composables/useErrorHandler', () => ({ useErrorHandler: () => ({
  showSuccessToast: vi.fn(), showError: vi.fn(),
  handleAsync: async (action: () => Promise<void>, options: any) => {
    try { await action() } catch { options.onError?.() } finally { options.onFinally?.() }
  }
}) }))
vi.mock('@/composables/useLenis', () => ({ useNestedLenis: vi.fn(), getLenis: () => null }))

import SeriesNavigation from '@/components/SeriesNavigation.vue'
import SeriesCatalog from '@/components/SeriesCatalog.vue'
import TableOfContents from '@/components/TableOfContents.vue'
import PostDetailView from '@/views/PostDetail.vue'

const series = { id: 7, name: 'Vue 学习' }
const items = [
  { id: 11, title: '第一篇：入门', sort: 1, current: true },
  { id: 22, title: '第二篇：响应式', sort: 2, current: false },
  { id: 33, title: '第三篇：组件', sort: 3, current: false }
]

async function createTestRouter(component: Component = defineComponent({ template: '<div />' })) {
  const router = createRouter({ history: createMemoryHistory(), routes: [
    { path: '/post/:id', component },
    { path: '/series-detail/:id', component: defineComponent({ template: '<div />' }) }
  ] })
  await router.push('/post/11')
  await router.isReady()
  return router
}

beforeEach(() => { vi.clearAllMocks() })

describe('阅读导航的目录与系列区分', () => {
  let content: HTMLElement
  beforeEach(() => {
    vi.useFakeTimers()
    content = document.createElement('div')
    content.className = 'rich-content'
    document.body.appendChild(content)
    vi.spyOn(HTMLElement.prototype, 'getBoundingClientRect').mockReturnValue({
      width: 800, height: 30, top: 0, bottom: 30, left: 0, right: 800, x: 0, y: 0, toJSON: () => ({})
    })
  })
  afterEach(() => {
    content.remove()
    vi.clearAllTimers()
    vi.useRealTimers()
  })

  it.each([false, true])('无标题时显示提示，系列存在状态为 %s', async (hasSeries) => {
    content.innerHTML = '<p>只有正文，没有标题层级</p>'
    const router = await createTestRouter()
    const wrapper = mount(TableOfContents, {
      slots: hasSeries ? { series: () => '系列篇目' } : {},
      global: { plugins: [router] }
    })
    wrapper.vm.extractHeadings()
    await nextTick()
    expect(wrapper.find('.toc-empty').text()).toBe('本文暂无可跳转目录')
    expect(wrapper.findAll('.toc-link')).toHaveLength(0)
    if (hasSeries) expect(wrapper.find('.toc-nav').text()).toContain('系列篇目')

    // 异步正文更新为带标题后，应恢复目录并移除空提示。
    content.innerHTML = '<h2>开始实践</h2><h3>配置主题</h3>'
    wrapper.vm.extractHeadings()
    await nextTick()
    expect(wrapper.find('.toc-empty').exists()).toBe(false)
    expect(wrapper.findAll('.toc-link').map(link => link.text())).toEqual(['开始实践', '配置主题'])
    expect(wrapper.find('.toc-level-1').text()).toBe('开始实践')
    wrapper.unmount()
  })

  it('窄屏默认折叠，展开后仍能看到无目录提示', async () => {
    content.innerHTML = '<p>正文</p>'
    const wrapper = mount(TableOfContents, { props: { collapsedBelow: 1680, embedded: true } })
    wrapper.vm.extractHeadings()
    await nextTick()
    expect(wrapper.find('.toggle-btn').attributes('aria-expanded')).toBe('false')
    await wrapper.find('.toggle-btn').trigger('click')
    expect(wrapper.find('.toggle-btn').attributes('aria-expanded')).toBe('true')
    expect(wrapper.find('.toc-empty').isVisible()).toBe(true)
    wrapper.unmount()
  })

  it('系列名称前有可见的所属系列标识，并保留篇目链接和当前文章状态', async () => {
    const router = await createTestRouter()
    const wrapper = mount(SeriesCatalog, { props: { series, items }, global: { plugins: [router] } })
    expect(wrapper.find('.series-label').text()).toBe('所属系列')
    expect(wrapper.find('.series-heading').text()).toBe('Vue 学习')
    expect(wrapper.find('a[aria-current="page"]').attributes('href')).toBe('/post/11')
    wrapper.unmount()
  })
})

describe('系列切换入口', () => {
  it.each([
    [11, '第 1 / 3 篇', null, 22, '已是第一篇'],
    [22, '第 2 / 3 篇', 11, 33, ''],
    [33, '第 3 / 3 篇', 22, null, '已是最后一篇']
  ])('文章 %i 的前后边界正确，链接保留系列来源', async (currentPostId, progress, previous, next, boundary) => {
    const router = await createTestRouter()
    const wrapper = mount(SeriesNavigation, {
      props: { series, items, currentPostId: currentPostId as number },
      global: { plugins: [router], stubs: { Icon: true } }
    })
    expect(wrapper.text()).toContain(progress)
    const articleLinks = wrapper.findAll('nav a').map(link => link.attributes('href'))
    expect(articleLinks).toEqual([previous, next].filter(id => id !== null)
      .map(id => `/post/${id}?from=series&seriesId=7`))
    if (boundary) expect(wrapper.find('[aria-disabled="true"]').text()).toContain(boundary)
    wrapper.unmount()
  })

  it('只有一篇时没有可跳转的前后链接；换到新系列后不残留旧标题和路径', async () => {
    const router = await createTestRouter()
    const wrapper = mount(SeriesNavigation, {
      props: { series, items, currentPostId: 22 },
      global: { plugins: [router], stubs: { Icon: true } }
    })
    await wrapper.setProps({
      series: { id: 8, name: '独立专题' },
      items: [{ id: 44, title: '专题文章', sort: 1, current: true }],
      currentPostId: 44
    })
    expect(wrapper.text()).toContain('独立专题')
    expect(wrapper.text()).toContain('第 1 / 1 篇')
    expect(wrapper.text()).toContain('已是第一篇')
    expect(wrapper.text()).toContain('已是最后一篇')
    expect(wrapper.findAll('nav a')).toHaveLength(0)
    expect(wrapper.find('a').attributes('href')).toBe('/series-detail/8')
    expect(wrapper.text()).not.toContain('Vue 学习')
    expect(wrapper.text()).not.toContain('第一篇：入门')
    wrapper.unmount()
  })

  it('当前文章不在目录时不误跳到其它文章或标记为首尾', async () => {
    const router = await createTestRouter()
    const wrapper = mount(SeriesNavigation, {
      props: { series, items, currentPostId: 99 },
      global: { plugins: [router], stubs: { Icon: true } }
    })
    expect(wrapper.findAll('nav a')).toHaveLength(0)
    expect(wrapper.text()).toContain('共 3 篇')
    expect(wrapper.text()).not.toContain('已是第一篇')
    expect(wrapper.text()).not.toContain('已是最后一篇')
    wrapper.unmount()
  })
})

describe('文章详情的系列入口', () => {
  it('正文前后同步切换；路由复用时重新加载，并在无系列文章隐藏两处入口', async () => {
    const detail = (id: number): PostDetail => ({
      id, title: `文章 ${id}`, content: '<p>正文内容</p>', category: { id: 1, name: '学习笔记' },
      author: { id: 1, username: 'liuxin' }, commentCount: 0, viewCount: 0, likeCount: 0,
      favoriteCount: 0, likeStatus: 0, favoriteStatus: 0, status: 'published', createdAt: '2026-10-10',
      // 保留旧 current 标记，验证导航确实依据当前文章 ID 更新。
      series: id === 33 ? null : series, seriesCatalog: id === 33 ? [] : items
    })
    mocks.getPostDetail.mockImplementation((id: number) => Promise.resolve(detail(id)))
    const router = await createTestRouter(PostDetailView)
    const wrapper = mount(RouterView, { global: {
      plugins: [router, createPinia()],
      stubs: { Icon: true, CommentSection: true, LoadingState: true, TableOfContents: {
        template: '<div><slot name="series" /></div>'
      } }
    } })
    await flushPromises()
    expect(wrapper.findAllComponents(SeriesNavigation)).toHaveLength(2)
    expect(wrapper.find('.post-header .series-navigation.is-compact').exists()).toBe(true)
    expect(wrapper.find('.post-header .series-navigation__title').exists()).toBe(false)
    const article = wrapper.find('article').element
    expect(wrapper.find('[aria-label="正文前的系列文章导航"]').element
      .compareDocumentPosition(article) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy()
    expect(wrapper.find('[aria-label="正文后的系列文章导航"]').element
      .compareDocumentPosition(article) & Node.DOCUMENT_POSITION_PRECEDING).toBeTruthy()
    expect(wrapper.find('.series-catalog').exists()).toBe(true)

    await wrapper.find('[aria-label="正文前的系列文章导航"] nav a').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.path).toBe('/post/22')
    expect(router.currentRoute.value.query).toEqual({ from: 'series', seriesId: '7' })
    expect(mocks.getPostDetail).toHaveBeenCalledWith(22)
    wrapper.findAllComponents(SeriesNavigation).forEach(navigation => {
      expect(navigation.text()).toContain('第 2 / 3 篇')
      expect(navigation.findAll('nav a').map(link => link.attributes('href')))
        .toEqual(['/post/11?from=series&seriesId=7', '/post/33?from=series&seriesId=7'])
    })

    await router.push('/post/33')
    await flushPromises()
    expect(wrapper.findAllComponents(SeriesNavigation)).toHaveLength(0)
    expect(wrapper.find('.series-catalog').exists()).toBe(false)
    wrapper.unmount()
  })
})
