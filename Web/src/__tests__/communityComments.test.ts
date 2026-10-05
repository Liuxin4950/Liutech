import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { addCommentToTree, commentAuthorName, commentAuthorAvatar, mergeCommentTree } from '@/services/commentTree'
import type { Comment } from '@/services/comment'
import CommentSection from '@/components/CommentSection.vue'
import CommentForm from '@/components/CommentForm.vue'
import CommentItem from '@/components/CommentItem.vue'

const api = vi.hoisted(() => ({ tree: vi.fn(), create: vi.fn() }))
vi.mock('@/services/comment', () => ({ CommentService: { getTreeComments: api.tree, createComment: api.create } }))
vi.mock('@/stores/user', () => ({ useUserStore: () => ({ isLoggedIn: true }) }))
vi.mock('@/composables/useErrorHandler', () => ({ useErrorHandler: () => ({
  handleAsync: async (fn: () => Promise<unknown>, options: { onFinally?: () => void }) => { try { return await fn() } finally { options.onFinally?.() } },
}) }))
const comment = (id: number, children: Comment[] = [], parentId?: number): Comment => ({
  id, postId: 7, parentId, content: `内容 ${id}`, createdAt: '2026-10-05T10:00:00',
  user: { id: 1, username: '读者' }, children,
})
const sectionOptions = { props: { postId: 7 }, global: { stubs: { CommentForm: true, CommentItem: true, Icon: true, LoadingState: true } } }
let hidden = false
const wrappers: ReturnType<typeof mount>[] = []
function setHidden(value: boolean) { hidden = value; document.dispatchEvent(new Event('visibilitychange')) }

beforeEach(() => {
  vi.useFakeTimers()
  hidden = false
  Object.defineProperty(document, 'hidden', { configurable: true, get: () => hidden })
  api.tree.mockReset().mockResolvedValue([comment(1)])
  api.create.mockReset().mockResolvedValue(comment(3))
})
afterEach(() => { for (const wrapper of wrappers.splice(0)) wrapper.unmount(); vi.useRealTimers(); delete (document as any).hidden })

describe('评论树更新', () => {
  it('保持现有父子对象并更新内容，让展开与回复组件保留', () => {
    const child = comment(2, [], 1)
    const parent = comment(1, [child])
    const current = [parent]
    const children = parent.children
    mergeCommentTree(current, [{ ...comment(1), content: '新内容', children: [{ ...comment(2, [], 1), content: '新回复' }, comment(3, [], 1)] }])
    expect(current[0]).toBe(parent)
    expect(parent.children).toBe(children)
    expect(parent.children[0]).toBe(child)
    expect(parent.content).toBe('新内容')
    expect(child.content).toBe('新回复')
    expect(parent.children.map(item => item.id)).toEqual([2, 3])
  })
  it('删除快照中消失的评论，但保留请求途中刚发布的回复', () => {
    const parent = comment(1, [comment(2, [], 1), comment(3, [], 1)])
    const current = [parent, comment(4)]
    mergeCommentTree(current, [comment(1)], new Set([3]))
    expect(current.map(item => item.id)).toEqual([1])
    expect(parent.children.map(item => item.id)).toEqual([3])
  })
  it('本人创建的回复重复到达只插入一次，机器人展示不依赖真人账号', () => {
    const tree = [comment(1)]
    addCommentToTree(tree, comment(2, [], 1)); addCommentToTree(tree, comment(2, [], 1))
    expect(tree[0]!.children).toHaveLength(1)
    const bot = { ...comment(3), authorType: 'BOT' as const, user: null, bot: { id: 9, name: '小雪', avatarUrl: '/snow.png' } }
    expect(commentAuthorName(bot)).toBe('小雪')
    expect(commentAuthorAvatar(bot)).toBe('/snow.png')
  })
})

describe('可见页面刷新评论', () => {
  it('每十秒刷新，隐藏和卸载停止，重新显示立即刷新', async () => {
    const wrapper = mount(CommentSection, sectionOptions); wrappers.push(wrapper)
    await flushPromises()
    expect(api.tree).toHaveBeenCalledTimes(1)
    await vi.advanceTimersByTimeAsync(10000)
    expect(api.tree).toHaveBeenCalledTimes(2)
    setHidden(true)
    await vi.advanceTimersByTimeAsync(30000)
    expect(api.tree).toHaveBeenCalledTimes(2)
    setHidden(false)
    await flushPromises()
    expect(api.tree).toHaveBeenCalledTimes(3)
    wrapper.unmount(); wrappers.pop()
    await vi.advanceTimersByTimeAsync(20000)
    expect(api.tree).toHaveBeenCalledTimes(3)
  })
  it('忽略上篇文章的迟到结果，取消旧请求', async () => {
    let resolveOld!: (comments: Comment[]) => void
    api.tree.mockImplementationOnce(() => new Promise<Comment[]>(resolve => { resolveOld = resolve }))
    const wrapper = mount(CommentSection, sectionOptions); wrappers.push(wrapper)
    const oldSignal = api.tree.mock.calls[0]![1] as AbortSignal
    api.tree.mockResolvedValue([{ ...comment(10), postId: 8 }])
    await wrapper.setProps({ postId: 8 })
    await flushPromises()
    resolveOld([comment(1)])
    await flushPromises()
    expect(oldSignal.aborted).toBe(true)
    const displayed = wrapper.findAllComponents({ name: 'CommentItem' })
    expect(displayed.map(item => item.props('comment').id)).toEqual([10])
  })
  it('进行中的刷新不会覆盖本人的新评论', async () => {
    const wrapper = mount(CommentSection, sectionOptions); wrappers.push(wrapper)
    await flushPromises()
    let resolveRefresh!: (comments: Comment[]) => void
    api.tree.mockImplementationOnce(() => new Promise<Comment[]>(resolve => { resolveRefresh = resolve }))
    await vi.advanceTimersByTimeAsync(10000)
    wrapper.findComponent({ name: 'CommentForm' }).vm.$emit('commentCreated', comment(3))
    resolveRefresh([comment(1)])
    await flushPromises()
    expect(wrapper.findAllComponents({ name: 'CommentItem' }).map(item => item.props('comment').id)).toContain(3)
  })
})

describe('自然发表评论与回复', () => {
  it('输入框不展示 AI 邀请控件，正常评论不发送提及 ID', async () => {
    const wrapper = mount(CommentForm, { props: { postId: 7 }, global: { stubs: { RouterLink: true } } }); wrappers.push(wrapper)
    await wrapper.find('textarea').setValue('来聊聊文章吧')
    expect(wrapper.find('fieldset').exists()).toBe(false)
    expect(wrapper.find('input[type="checkbox"]').exists()).toBe(false)
    expect(wrapper.text()).not.toContain('提及 AI')
    await wrapper.find('form').trigger('submit')
    await flushPromises()
    expect(api.create).toHaveBeenCalledWith({ postId: 7, content: '来聊聊文章吧', parentId: undefined })
    expect((wrapper.find('textarea').element as HTMLTextAreaElement).value).toBe('')
  })
  it('直接回复 AI 使用同一表单，提交父评论 ID 并保留 AI 作者标识', async () => {
    const bot = { ...comment(11), authorType: 'BOT' as const, user: null, bot: { id: 9, name: '小雪' } }
    const wrapper = mount(CommentItem, { props: { comment: bot, postId: 7 }, global: { stubs: { RouterLink: true, Icon: true } } }); wrappers.push(wrapper)
    expect(wrapper.find('.ai-badge').text()).toBe('AI')
    await wrapper.find('.reply-btn').trigger('click')
    await wrapper.find('textarea').setValue('我也这样想')
    expect(wrapper.find('input[type="checkbox"]').exists()).toBe(false)
    await wrapper.find('form').trigger('submit')
    await flushPromises()
    expect(api.create).toHaveBeenCalledWith({ postId: 7, content: '我也这样想', parentId: 11 })
    expect(wrapper.find('textarea').exists()).toBe(false)
  })
  it('文章切换后迟到的发布结果不会清空新文章草稿或关闭新回复框', async () => {
    let resolveCreate!: (result: Comment) => void
    api.create.mockImplementationOnce(() => new Promise<Comment>(resolve => { resolveCreate = resolve }))
    const wrapper = mount(CommentForm, { props: { postId: 7 }, global: { stubs: { RouterLink: true } } }); wrappers.push(wrapper)
    await wrapper.find('textarea').setValue('旧文章评论')
    await wrapper.find('form').trigger('submit')
    await wrapper.setProps({ postId: 8 })
    await wrapper.find('textarea').setValue('新文章草稿')
    resolveCreate(comment(3))
    await flushPromises()
    expect((wrapper.find('textarea').element as HTMLTextAreaElement).value).toBe('新文章草稿')
    expect(wrapper.emitted('commentCreated')).toBeUndefined()
  })

})
