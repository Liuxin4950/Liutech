import { flushPromises, mount } from '@vue/test-utils'
import type { VueWrapper } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { Announcement } from '@/services/announcement'

const mocks = vi.hoisted(() => ({
  getDetail: vi.fn(),
  store: {
    isLatestLoading: false,
    latestAnnouncements: [] as Announcement[],
    initAnnouncements: vi.fn(),
    fetchLatestAnnouncements: vi.fn(),
    refreshLatestAnnouncements: vi.fn(),
  },
}))
vi.mock('@/stores/announcement', () => ({ useAnnouncementStore: () => mocks.store }))
vi.mock('@/services/announcement', () => ({ AnnouncementService: { getAnnouncementById: mocks.getDetail } }))
vi.mock('@/composables/useLenis', () => ({ useNestedLenis: vi.fn() }))

import AnnouncementCard from '@/components/AnnouncementCard.vue'

const announcement = (id: number): Announcement => ({
  id, title: `公告 ${id}`, content: '<p>公告正文</p>', type: 1, typeName: '系统',
  priority: 3, priorityName: '高', status: 1, statusName: '已发布', isTop: 1,
  viewCount: 4, createdAt: '2026-08-07T14:25:00', isValid: true,
  startTime: '2026-08-07T00:00:00', endTime: '2026-12-31T23:59:59',
})

let wrapper: VueWrapper | undefined
let host: HTMLElement | undefined
const mountCard = () => {
  host = document.createElement('div')
  document.body.append(host)
  wrapper = mount(AnnouncementCard, { attachTo: host, global: { stubs: { Icon: true } } })
  return wrapper
}
const dialog = () => document.querySelector<HTMLElement>('[role="dialog"]')
const closeButton = () => document.querySelector<HTMLButtonElement>('[aria-label="关闭公告"]')!
const clickAnnouncement = async (card: VueWrapper, index = 0) => {
  const button = card.findAll<HTMLButtonElement>('.announcement-item')[index]!
  button.element.focus()
  await button.trigger('click')
  await flushPromises()
  return button.element
}

beforeEach(() => {
  vi.resetAllMocks()
  mocks.store.latestAnnouncements = [announcement(1), announcement(2)]
  mocks.store.initAnnouncements.mockResolvedValue(undefined)
  mocks.getDetail.mockResolvedValue(announcement(1))
})

afterEach(() => {
  wrapper?.unmount()
  wrapper = undefined
  host?.remove()
  host = undefined
})

describe('公告详情交互', () => {
  it('每次打开获取最新详情，保留发布与有效时间、浏览量，并净化正文', async () => {
    mocks.getDetail.mockResolvedValue({
      ...announcement(1), viewCount: 9,
      content: '<p>可读正文 <a href="https://example.com">链接</a></p><script>alert(1)</script><img src="/safe.png" onerror="alert(1)"><a href="javascript:alert(1)">危险链接</a>',
    })
    const card = mountCard()
    await clickAnnouncement(card)
    const element = dialog()!
    expect(mocks.getDetail).toHaveBeenCalledWith(1)
    expect(element.getAttribute('aria-modal')).toBe('true')
    expect(element.querySelector('h2')?.id).toBe(element.getAttribute('aria-labelledby'))
    expect(element.querySelector('.modal-content-text')?.textContent).toContain('可读正文')
    expect(element.querySelector('script')).toBeNull()
    expect(element.querySelector('[onerror]')).toBeNull()
    expect(element.querySelector('a[href^="javascript:"]')).toBeNull()
    expect(element.textContent).toContain('9 次浏览')
    expect(element.textContent).toContain('发布于')
    expect(element.textContent).toContain('开始')
    expect(element.textContent).toContain('结束')
    expect(element.textContent).not.toContain('高')
  })

  it('关闭后的延迟详情响应不能重新填入已关闭弹窗', async () => {
    let resolve!: (value: Announcement) => void
    mocks.getDetail.mockImplementation(() => new Promise<Announcement>(done => { resolve = done }))
    const card = mountCard()
    const opener = await clickAnnouncement(card)
    closeButton().click()
    await flushPromises()
    expect(dialog()).toBeNull()
    expect(document.activeElement).toBe(opener)
    resolve({ ...announcement(1), title: '过期结果' })
    await flushPromises()
    expect(dialog()).toBeNull()
  })

  it('先后打开不同公告时，旧请求晚到不能覆盖当前公告', async () => {
    const pending = new Map<number, (value: Announcement) => void>()
    mocks.getDetail.mockImplementation((id: number) => new Promise<Announcement>(resolve => { pending.set(id, resolve) }))
    const card = mountCard()
    await clickAnnouncement(card, 0)
    closeButton().click()
    await flushPromises()
    await clickAnnouncement(card, 1)
    pending.get(2)!({ ...announcement(2), title: '第二条最新详情' })
    await flushPromises()
    pending.get(1)!({ ...announcement(1), title: '第一条过期详情' })
    await flushPromises()
    expect(dialog()?.querySelector('h2')?.textContent).toBe('第二条最新详情')
    expect(dialog()?.textContent).not.toContain('第一条过期详情')
  })

  it('键盘焦点在弹窗内循环，ESC 关闭后恢复公告按钮焦点', async () => {
    mocks.getDetail.mockResolvedValue({ ...announcement(1), content: '<p><a href="https://example.com">阅读链接</a></p>' })
    const card = mountCard()
    const opener = await clickAnnouncement(card)
    expect(document.activeElement).toBe(closeButton())
    const link = dialog()!.querySelector<HTMLAnchorElement>('a')!
    vi.spyOn(closeButton(), 'getClientRects').mockReturnValue([{}] as unknown as DOMRectList)
    vi.spyOn(link, 'getClientRects').mockReturnValue([{}] as unknown as DOMRectList)
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Tab', shiftKey: true, cancelable: true }))
    expect(document.activeElement).toBe(link)
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Tab', cancelable: true }))
    expect(document.activeElement).toBe(closeButton())
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', cancelable: true }))
    await flushPromises()
    expect(dialog()).toBeNull()
    expect(document.activeElement).toBe(opener)
  })
})
