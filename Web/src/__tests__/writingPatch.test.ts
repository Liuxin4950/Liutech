import { describe, expect, it, vi } from 'vitest'
import { applyWritingContentPatch, resolveWritingContentUpdate, useWritingReview } from '@/services/writingReview'
import { appendWritingHistory, inferWritingFieldScope } from '@/services/writingSession'
import type { WritingHistoryMessage } from '@/services/writingSession'

const patch = (before: string, after: string) => ({ baseRevision: 'revision', edits: [{ before, after }] })

describe('局部正文修改', () => {
  it('只替换命中片段，其它段落/媒体/原始HTML字节保持一致', () => {
    const prefix = `<P class='kept' STYLE='text-align:center'>原始字节 &amp; 样式</P>\n`
    const suffix = `<audio controls src='/uploads/audio.mp3'></audio><iframe src='https://original.invalid/embed'></iframe>\n<p>文章尾部</p>`
    const original = prefix + '<p>只有这段有措字</p>' + suffix
    expect(applyWritingContentPatch(original, patch('<p>只有这段有措字</p>', '<p>只有这段有错字</p>')))
      .toBe(prefix + '<p>只有这段有错字</p>' + suffix)
    expect(original).toBe(prefix + '<p>只有这段有措字</p>' + suffix)
  })
  it('多个乱序补丁基于同一原稿原子拼接，不让较早替换影响后续位置', () => {
    const original = '<p>第一措</p>\n<p>保留</p>\n<p>第二措</p>'
    expect(applyWritingContentPatch(original, { baseRevision: 'r', edits: [
      { before: '<p>第二措</p>', after: '<p>第二错（扩展）</p>' }, { before: '<p>第一措</p>', after: '<p>第一错</p>' }
    ] })).toBe('<p>第一错</p>\n<p>保留</p>\n<p>第二错（扩展）</p>')
  })
  it('重复锚点、缺失锚点和重叠补丁全部拒绝', () => {
    expect(() => applyWritingContentPatch('<p>相同</p><p>相同</p>', patch('<p>相同</p>', '<p>新</p>'))).toThrow('唯一')
    expect(() => applyWritingContentPatch('<p>原稿</p>', patch('不存在', '新'))).toThrow('唯一')
    expect(() => applyWritingContentPatch('<p>原稿</p>', { baseRevision: 'r', edits: [{ before: '<p>原稿</p>', after: '<p>新</p>' }, { before: '原稿', after: '另一稿' }] })).toThrow('重叠')
    expect(() => applyWritingContentPatch('aaaa', patch('aaa', '新'))).toThrow('唯一')
  })
  it('新资源仅在after净化，原稿未改的资源不重新序列化', () => {
    const original = `<img src='/uploads/original.png'><p>原稿</p>`
    const result = applyWritingContentPatch(original, patch('<p>原稿</p>', '<p onmouseover="alert(1)">改稿</p><img src="https://exfil.invalid/private"><script>alert(1)</script>'))
    expect(result.startsWith(`<img src='/uploads/original.png'>`)).toBe(true)
    expect(result).not.toMatch(/onmouseover|script|exfil.invalid/)
    expect(result).toContain('新图片请手动添加')
  })
  it('拒绝超出32段、超长结果和净化后意外留空的补丁，明确删除仍可用', () => {
    expect(() => applyWritingContentPatch('原稿', { baseRevision: 'r', edits: Array.from({ length: 33 }, () => ({ before: '原稿', after: '新稿' })) })).toThrow('格式')
    expect(() => applyWritingContentPatch('<p>原稿</p>', patch('<p>原稿</p>', `<p>${'字'.repeat(200000)}</p>`))).toThrow('长度')
    expect(() => applyWritingContentPatch('<p>原稿</p>', patch('<p>原稿</p>', '<script>alert(1)</script>'))).toThrow('净化后为空')
    expect(applyWritingContentPatch('<p>删除</p><p>保留</p>', patch('<p>删除</p>', ''))).toBe('<p>保留</p>')
  })
  it('版本匹配且完整成功后才采纳，输出同时保留补丁供父编辑器复核', () => {
    const original = { postId: 1, title: '原标题', content: `<P class='keep'>原样</P><p>措字</p>` }
    const apply = vi.fn()
    const review = useWritingReview(() => original, apply)
    review.begin(); review.setRevision('revision')
    expect(() => review.stage({ title: '新标题', contentPatch: { ...patch('<p>措字</p>', '<p>错字</p>'), baseRevision: 'expired' } })).toThrow('过期')
    expect(review.pending.value).toEqual({})
    review.stage({ title: '新标题', contentPatch: patch('<p>措字</p>', '<p>错字</p>') })
    review.apply(); expect(apply).not.toHaveBeenCalled()
    review.complete(); review.apply()
    const update = apply.mock.calls[0]![0]
    expect(update.contentHtml).toBe(`<P class='keep'>原样</P><p>错字</p>`)
    expect(resolveWritingContentUpdate(update, original.content)).toBe(update.contentHtml)
    expect(() => resolveWritingContentUpdate({ ...update, contentHtml: '另一份结果' }, original.content)).toThrow('不一致')
  })
  it('原稿修改后补丁不可覆盖，补丁只净化after仍可统计移除媒体', () => {
    let draft = { content: '<p>错字<img src="/uploads/1.png"></p><p>保持</p>' }
    const review = useWritingReview(() => draft, vi.fn())
    review.begin(); review.setRevision('revision')
    review.stage({ contentPatch: patch('<p>错字<img src="/uploads/1.png"></p>', '<p>正字</p>') })
    review.complete()
    expect(review.missingMedia.value).toBe(1)
    draft = { content: '<p>用户手改</p>' }
    expect(review.canApply.value).toBe(false)
  })
})

describe('写作范围和连续写作上下文', () => {
  it('纠错/修复默认只改正文，全文重写也不会隐式改分类标签', () => {
    for (const message of ['帮我修复几个错字', '纠错', '修改有问题的地方', '全文重写']) expect(inferWritingFieldScope(message).fields).toEqual(['content'])
    expect(inferWritingFieldScope('帮我检查一下').fields).toEqual(['check'])
    for (const message of ['检查这篇文章是否存在错别字', '检查一下正文，不要修改', '审查正文结构', '校对全文', '只提出检查结果，不改正文']) {
      expect(inferWritingFieldScope(message).fields).toEqual(['check'])
    }
    expect(inferWritingFieldScope('检查并修复正文错别字').fields).toEqual(['content'])
    for (const message of ['检查并纠错', '校对并改正错字']) expect(inferWritingFieldScope(message).fields).toEqual(['content'])
    expect(inferWritingFieldScope('润色正文不要修改标题').fields).toEqual(['content'])
    expect(inferWritingFieldScope('修复标题和分类').fields).toEqual(['title', 'category'])
    expect(inferWritingFieldScope('写一篇完整文章').fields).toHaveLength(5)
  })
  it('20轮工具-only成功仍保留最近7轮摘要，不保存正文或补丁全文', () => {
    let history: WritingHistoryMessage[] = []
    for (let index = 0; index < 20; index++) history = appendWritingHistory(history, `第${index}轮纠错`, '', { contentPatch: patch('<p>密集原稿正文</p>', '<p>密集改稿正文</p>') })
    expect(history).toHaveLength(14)
    expect(history[0]?.content).toBe('第13轮纠错')
    expect(history[13]?.content).toContain('局部修改 1 处')
    expect(JSON.stringify(history)).not.toContain('密集原稿正文')
    expect(JSON.stringify(history)).not.toContain('密集改稿正文')
  })
})
