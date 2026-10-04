import { flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { effectScope, type EffectScope } from 'vue'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import Swal from 'sweetalert2'
import { CategoryService } from '@/services/category'
import { TagService, type Tag } from '@/services/tag'
import { usePostEditor } from '@/composables/usePostEditor'

vi.mock('vue-router', () => ({ useRoute: () => ({ query: {}, fullPath: '/create-post' }), useRouter: () => ({ push: vi.fn(), back: vi.fn() }) }))

let scope: EffectScope
const editorForTest = () => {
  scope = effectScope()
  return scope.run(() => usePostEditor())!
}

beforeEach(() => {
  setActivePinia(createPinia())
  localStorage.clear()
  vi.spyOn(Swal, 'fire').mockResolvedValue({ isConfirmed: false, isDenied: false, isDismissed: true })
  vi.spyOn(CategoryService, 'getCategories').mockResolvedValue([])
  vi.spyOn(TagService, 'getTags').mockResolvedValue([])
})
afterEach(() => { scope?.stop(); vi.restoreAllMocks() })

describe('用户确认 AI 分类与标签建议', () => {
  it('显示真实创建与刷新状态，阻止重复点击，拿到 ID 后才选中', async () => {
    let finish!: (category: { id: number, name: string }) => void
    const create = vi.spyOn(CategoryService, 'createCategory').mockImplementation(() => new Promise(resolve => { finish = resolve }))
    const editor = editorForTest()
    editor.aiSuggestedCategoryName.value = 'Vue'
    const pending = editor.createAiSuggestedCategory('Vue')
    await flushPromises()
    expect(editor.localActivities.value[0]).toMatchObject({ stage: 'creating_category', status: 'running' })
    expect(editor.form.value.categoryId).toBe('')
    await editor.createAiSuggestedCategory('Vue')
    expect(create).toHaveBeenCalledTimes(1)
    finish({ id: 21, name: 'Vue' })
    await pending
    expect(editor.form.value.categoryId).toBe('21')
    expect(editor.categories.value.some(category => category.id === 21)).toBe(true)
    expect(editor.aiSuggestedCategoryName.value).toBe('')
    expect(editor.localActivities.value[0]).toMatchObject({ status: 'completed', message: expect.stringContaining('文章待保存') })
  })

  it('批量部分失败保留未完成建议和已成功的绑定，不报告全部成功', async () => {
    const remoteTags: Tag[] = []
    vi.mocked(TagService.getTags).mockImplementation(async () => [...remoteTags])
    vi.spyOn(TagService, 'createTag').mockImplementation(async ({ name }) => {
      if (name === '失败标签') throw new Error('network interrupted')
      const tag = { id: 22, name, postCount: 0 }
      remoteTags.push(tag)
      return tag
    })
    const editor = editorForTest()
    editor.aiSuggestedTagNames.value = ['成功标签', '失败标签', '未开始标签']
    await editor.createAllAiSuggestedTags()
    expect(editor.selectedTags.value.map(tag => tag.name)).toEqual(['成功标签'])
    expect(editor.aiSuggestedTagNames.value).toEqual(['失败标签', '未开始标签'])
    expect(editor.localActivities.value[0]).toMatchObject({ status: 'failed', message: expect.stringContaining('已处理 1 项') })
    expect(Swal.fire).not.toHaveBeenCalledWith('成功', expect.anything(), 'success')
    expect(editor.creatingAiSuggestion.value).toBe('')
  })

  it('重试先读取最新列表，复用响应丢失后已经创建的同名项目', async () => {
    vi.mocked(TagService.getTags).mockResolvedValue([{ id: 23, name: '已有标签', postCount: 0 }])
    const create = vi.spyOn(TagService, 'createTag')
    const editor = editorForTest()
    editor.aiSuggestedTagNames.value = ['已有标签']
    await editor.createAiSuggestedTag('已有标签')
    expect(create).not.toHaveBeenCalled()
    expect(editor.selectedTags.value.map(tag => tag.id)).toEqual([23])
    expect(editor.aiSuggestedTagNames.value).toEqual([])
  })

  it('没有可验证的 ID 就保留建议并报告失败', async () => {
    vi.spyOn(CategoryService, 'createCategory').mockResolvedValue({ name: '未确认分类' } as any)
    const editor = editorForTest()
    editor.aiSuggestedCategoryName.value = '未确认分类'
    await editor.createAiSuggestedCategory('未确认分类')
    expect(editor.form.value.categoryId).toBe('')
    expect(editor.aiSuggestedCategoryName.value).toBe('未确认分类')
    expect(editor.localActivities.value[0]).toMatchObject({ status: 'failed' })
  })

  it('切换文章后的旧创建响应不能绑定新文章或更新状态', async () => {
    let finish!: (category: { id: number, name: string }) => void
    vi.spyOn(CategoryService, 'createCategory').mockImplementation(() => new Promise(resolve => { finish = resolve }))
    const editor = editorForTest()
    editor.editingPostId.value = 1
    editor.aiSuggestedCategoryName.value = '旧文章分类'
    const pending = editor.createAiSuggestedCategory('旧文章分类')
    await flushPromises()
    editor.editingPostId.value = 2
    editor.form.value.categoryId = '99'
    finish({ id: 24, name: '旧文章分类' })
    await pending
    expect(editor.form.value.categoryId).toBe('99')
    expect(editor.localActivities.value).toEqual([])
    expect(editor.creatingAiSuggestion.value).toBe('')
  })

  it('创建等待期间用户手动选择分类，成功后保留用户的新选择', async () => {
    let finish!: (category: { id: number, name: string }) => void
    vi.spyOn(CategoryService, 'createCategory').mockImplementation(() => new Promise(resolve => { finish = resolve }))
    const editor = editorForTest()
    editor.aiSuggestedCategoryName.value = 'AI分类'
    const pending = editor.createAiSuggestedCategory('AI分类')
    await flushPromises()
    editor.form.value.categoryId = '77'
    finish({ id: 25, name: 'AI分类' })
    await pending
    expect(editor.form.value.categoryId).toBe('77')
    expect(editor.localActivities.value[0]).toMatchObject({ status: 'completed', message: expect.stringContaining('保留你刚修改的选择') })
  })

  it('父编辑器再次验证局部补丁，未修改的 HTML 字节保留且可整轮撤销', () => {
    const editor = editorForTest()
    const prefix = '<section data-custom="must-remain" style="font-weight: 600">原始排版</section>'
    const before = '<p>错别字</p>'
    const suffix = '<iframe src="/existing/embed" data-custom="must-remain"></iframe>'
    editor.form.value.content = prefix + before + suffix
    editor.form.value.title = '原标题'
    const original = { ...editor.adminDraftSnapshot.value }
    editor.handleFieldUpdate({ title: '新标题', contentPatch: { baseRevision: 'test-revision', edits: [{ before, after: '<p>正确文字</p>' }] },
      contentHtml: prefix + '<p>正确文字</p>' + suffix }, original)
    expect(editor.form.value.content).toBe(prefix + '<p>正确文字</p>' + suffix)
    expect(editor.form.value.title).toBe('新标题')
    editor.undoAiRound()
    expect(editor.form.value.content).toBe(prefix + before + suffix)
    expect(editor.form.value.title).toBe('原标题')
  })

  it('局部补丁不匹配时所有字段保持原值，采纳前用户改过原稿也不会回写', () => {
    const editor = editorForTest()
    editor.form.value.title = '原标题'
    editor.form.value.content = '<p>原稿</p>'
    const original = { ...editor.adminDraftSnapshot.value }
    editor.handleFieldUpdate({ title: '不应应用', contentPatch: { baseRevision: 'test-revision', edits: [{ before: '<p>不存在</p>', after: '<p>修改</p>' }] } }, original)
    expect(editor.form.value.title).toBe('原标题')
    expect(editor.form.value.content).toBe('<p>原稿</p>')
    editor.form.value.content = '<p>用户新改稿</p>'
    editor.handleFieldUpdate({ title: '也不应应用', contentHtml: '<p>旧请求生成稿</p>' }, original)
    expect(editor.form.value.title).toBe('原标题')
    expect(editor.form.value.content).toBe('<p>用户新改稿</p>')
  })
})
