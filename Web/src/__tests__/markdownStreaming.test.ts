/**
 * Markdown 流式渲染专项测试
 *
 * 覆盖：
 * - completeStreamingTail：围栏/行内代码/强调的补全只在正确作用域生效，
 *   代码内容里的 ` 与 ** 不再触发误补全（旧奇偶计数实现的真实伪影）。
 * - StreamingMarkdownRenderer：定稿块缓存（不重复解析）、围栏内空行不切块、
 *   内容只增不减的前缀假设、切换消息时重置。
 */
import { describe, expect, it, vi } from 'vitest'
import { marked } from 'marked'
import {
  StreamingMarkdownRenderer,
  completeStreamingTail,
  useMarkdown
} from '@/composables/useMarkdown'

const { processMarkdown } = useMarkdown()

describe('completeStreamingTail', () => {
  it('未闭合围栏只补围栏，代码内容里的反引号与 ** 不参与补全', () => {
    const tail = '说明\n```js\nconst a = `x`\n**not bold**\n'
    expect(completeStreamingTail(tail)).toBe(tail + '\n```')
  })

  it('已闭合围栏内的奇数个反引号不触发补全', () => {
    const tail = '```\na ` b\n```\n'
    expect(completeStreamingTail(tail)).toBe(tail)
  })

  it('未闭合行内代码按原长度补全', () => {
    expect(completeStreamingTail('看这段 ``code')).toBe('看这段 ``code``')
  })

  it('未闭合加粗与斜体分别补全', () => {
    expect(completeStreamingTail('这是 **重点')).toBe('这是 **重点**')
    expect(completeStreamingTail('这是 *重点')).toBe('这是 *重点*')
  })

  it('行内代码里的 ** 不参与加粗计数', () => {
    const tail = '行内 `**` 之后正常文字'
    expect(completeStreamingTail(tail)).toBe(tail)
  })

  it('平衡内容原样返回', () => {
    const tail = '完整 **加粗** 与 `code` 和 *斜体*\n'
    expect(completeStreamingTail(tail)).toBe(tail)
  })

  it('~~~ 围栏同样识别', () => {
    const tail = '~~~\ncode\n'
    expect(completeStreamingTail(tail)).toBe(tail + '\n~~~')
  })
})

describe('StreamingMarkdownRenderer', () => {
  it('逐段追加的最终结果与整文解析一致', () => {
    const full = '第一段内容\n\n第二段内容\n\n```js\nconst a = 1\n\nconst b = 2\n```\n\n结尾段落'
    const renderer = new StreamingMarkdownRenderer()
    let html = ''
    for (let i = 1; i <= full.length; i++) {
      html = renderer.render(full.slice(0, i))
    }
    expect(html.trim()).toBe(processMarkdown(full).trim())
  })

  it('定稿块只解析一次，追加内容不重复解析前缀', () => {
    const spy = vi.spyOn(marked, 'parse')
    try {
      const renderer = new StreamingMarkdownRenderer()
      const prefix = '第一段已经完整结束\n\n'
      renderer.render(prefix)
      const callsAfterPrefix = spy.mock.calls.length
      const parsedPrefix = spy.mock.calls.some(call => String(call[0]).includes('第一段已经完整结束'))
      expect(parsedPrefix).toBe(true)

      const growingTail = '后续尾部不断追加'.repeat(8) + 'x'.repeat(60)
      for (let i = 1; i <= 60; i++) {
        renderer.render(prefix + growingTail.slice(0, i))
      }
      const reparsedPrefix = spy.mock.calls.slice(callsAfterPrefix)
        .some(call => String(call[0]).includes('第一段已经完整结束'))
      expect(reparsedPrefix).toBe(false)
    } finally {
      spy.mockRestore()
    }
  })

  it('围栏内的空行不作为切块边界', () => {
    const renderer = new StreamingMarkdownRenderer()
    const html = renderer.render('```js\na\n\nb\n```\n\n后续')
    expect(html.match(/<pre>/g)?.length).toBe(1)
    expect(html).toContain('后续')
  })

  it('新内容不是旧内容前缀时重置缓存', () => {
    const renderer = new StreamingMarkdownRenderer()
    renderer.render('旧消息第一段\n\n旧消息第二段\n\n')
    const html = renderer.render('另一条完全不同的消息')
    expect(html).not.toContain('旧消息')
    expect(html).toContain('另一条完全不同的消息')
  })

  it('空内容返回空串并重置', () => {
    const renderer = new StreamingMarkdownRenderer()
    renderer.render('有内容\n\n')
    expect(renderer.render('')).toBe('')
    expect(renderer.render('重新开始')).toContain('重新开始')
  })

  it('流式中途的未闭合围栏渲染为完整代码块而不是散乱文本', () => {
    const renderer = new StreamingMarkdownRenderer()
    const html = renderer.render('前言\n\n```python\nprint(1)\n')
    expect(html).toContain('<pre>')
    expect(html).toContain('print')
  })
})

describe('长回复渲染成本', () => {
  it('增量解析的总输入量远低于逐分片全量解析', () => {
    const blocks = Array.from({ length: 40 }, (_, i) => `第 ${i} 段正文内容，包含一些中文文字。\n\n`).join('')
    const spy = vi.spyOn(marked, 'parse')
    const totalParsedChars = () => spy.mock.calls.reduce((sum, call) => sum + String(call[0]).length, 0)
    try {
      const renderer = new StreamingMarkdownRenderer()
      let pos = 0
      while (pos < blocks.length) {
        pos = Math.min(blocks.length, pos + 12)
        renderer.render(blocks.slice(0, pos))
      }
      const incrementalChars = totalParsedChars()
      spy.mockClear()

      pos = 0
      while (pos < blocks.length) {
        pos = Math.min(blocks.length, pos + 12)
        processMarkdown(blocks.slice(0, pos), true)
      }
      const fullChars = totalParsedChars()
      // 全量路径每个分片重新解析整篇（O(n²)）；增量路径定稿块只解析一次，
      // 总输入量应至少低一个数量级
      expect(incrementalChars).toBeLessThan(fullChars / 10)
    } finally {
      spy.mockRestore()
    }
  })
})