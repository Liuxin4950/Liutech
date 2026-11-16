import { marked } from 'marked'
import DOMPurify from 'dompurify'
import { hljs } from '@/utils/highlightLanguages'

/** 匹配空代码块（<pre><code></code></pre>），渲染后清理掉避免多余空白 */
const EMPTY_CODE_BLOCK_REGEX = /<pre><code[^>]*>\s*<\/code><\/pre>/g

/** 统计字符串中匹配某正则的次数（match 为 null 时返回 0） */
const countMatches = (str: string, pattern: RegExp): number =>
  (str.match(pattern) || []).length

/** DOMPurify 安全配置：白名单标签/属性，禁用脚本相关 */
const sanitizeConfig = {
  ALLOWED_TAGS: [
    'h1', 'h2', 'h3', 'h4', 'h5', 'h6',
    'p', 'br', 'strong', 'em', 'u', 's', 'del', 'ins',
    'ul', 'ol', 'li', 'dl', 'dt', 'dd',
    'blockquote', 'code', 'pre',
    'a', 'img',
    'table', 'thead', 'tbody', 'tr', 'th', 'td',
    'div', 'span'
  ],
  ALLOWED_ATTR: ['href', 'title', 'alt', 'src', 'class'],
  ALLOW_DATA_ATTR: false,
  FORBID_TAGS: ['script', 'object', 'embed', 'iframe', 'form', 'input', 'button'],
  FORBID_ATTR: ['onclick', 'onload', 'onerror', 'onmouseover']
}

// 配置 marked：启用 GFM 与换行转 <br>
marked.setOptions({
  breaks: true,
  gfm: true
})

// 自定义渲染器：代码高亮、链接安全、图片懒加载
const renderer = new marked.Renderer()

renderer.code = (code: string, language?: string) => {
  const validLanguage = language && hljs.getLanguage(language) ? language : 'plaintext'
  const highlighted = hljs.highlight(code, { language: validLanguage }).value
  return `<pre><code class="hljs language-${validLanguage}">${highlighted}</code></pre>`
}

renderer.link = (href, title, text) => {
  const titleAttr = title ? ` title="${title}"` : ''
  // 仅允许 http(s)/mailto/相对路径，其余丢弃链接只留文本
  if (href && !href.match(/^(https?:\/\/|mailto:|\/)/)) {
    return text
  }
  // 内部链接走 Vue Router，不刷新页面
  if (href && href.startsWith('/')) {
    return `<a href="${href}"${titleAttr}>${text}</a>`
  }
  return `<a href="${href}"${titleAttr} target="_blank" rel="noopener noreferrer">${text}</a>`
}

renderer.image = (href, title, text) => {
  const titleAttr = title ? ` title="${title}"` : ''
  const altAttr = text ? ` alt="${text}"` : ''
  return `<img src="${href}"${altAttr}${titleAttr} loading="lazy">`
}

marked.use({ renderer })

/** 把 markdown 转成已净化的 HTML；流式内容自动补全未闭合标记 */
const processMarkdown = (content: string, isStreaming = false): string => {
  if (!content) return ''
  if (isStreaming) return renderFragment(completeStreamingTail(content))

  return renderFragment(content)
}

const renderFragment = (markdown: string): string => {
  try {
    const html = marked.parse(markdown) as string
    return DOMPurify.sanitize(html, sanitizeConfig).replace(EMPTY_CODE_BLOCK_REGEX, '')
  } catch {
    // 解析失败回退为纯文本换行
    return DOMPurify.sanitize(markdown.replace(/\n/g, '<br>'))
  }
}

interface FenceState { char: string; len: number }

const FENCE_LINE = /^ {0,3}(`{3,}|~{3,})(.*)$/

/** 逐行扫描围栏状态；返回行扫描器便于切块与补全共用同一口径 */
const scanFence = (line: string, fence: FenceState | null): FenceState | null => {
  const match = line.match(FENCE_LINE)
  if (!match) return fence
  const marker = match[1]!
  if (!fence) return { char: marker[0]!, len: marker.length }
  if (marker[0] === fence.char && marker.length >= fence.len && match[2]!.trim() === '') return null
  return fence
}

/**
 * 补全流式尾部未闭合的围栏/行内代码/加粗/斜体。
 * 只在代码围栏与行内代码之外统计强调标记，避免代码内容里的 ` 与 ** 触发误补全。
 */
export const completeStreamingTail = (tail: string): string => {
  if (!tail) return ''

  let fence: FenceState | null = null
  const proseLines: string[] = []
  for (const line of tail.split('\n')) {
    const wasInFence = fence !== null
    fence = scanFence(line, fence)
    // 围栏内的内容与围栏标记行都不参与强调统计
    if (!wasInFence && !FENCE_LINE.test(line)) proseLines.push(line)
  }
  let prose = proseLines.join('\n')

  // 行内代码：同长度反引号串配对；未闭合的串按其长度补全，且不参与强调统计
  let openRun: { len: number; start: number } | null = null
  const closedSpans: Array<[number, number]> = []
  for (const match of prose.matchAll(/`+/g)) {
    const len = match[0].length
    if (!openRun) {
      openRun = { len, start: match.index }
    } else if (len === openRun.len) {
      closedSpans.push([openRun.start, match.index + len])
      openRun = null
    }
  }
  let suffix = ''
  if (openRun) {
    suffix += '`'.repeat(openRun.len)
    prose = prose.slice(0, openRun.start)
  }
  for (let i = closedSpans.length - 1; i >= 0; i--) {
    const [start, end] = closedSpans[i]!
    prose = prose.slice(0, start) + prose.slice(end)
  }

  if (countMatches(prose, /\*\*/g) % 2 !== 0) suffix += '**'
  if (countMatches(prose + suffix, /(?<!\*)\*(?!\*)/g) % 2 !== 0) suffix += '*'
  if (fence) suffix = '\n' + fence.char.repeat(fence.len) + suffix
  return tail + suffix
}

/**
 * 流式增量渲染器：以「围栏外的空行」为安全边界把内容切成已定稿块，
 * 定稿块只解析一次并缓存 HTML，每次追加只重解析末尾未定稿尾部。
 * 内容只增不减；一旦新内容不是旧内容的前缀（切换消息）即整体重置。
 * 流结束后调用方应改用 processMarkdown 全文解析，保证最终排版与整文一致。
 */
export class StreamingMarkdownRenderer {
  private segments: string[] = []
  private covered = 0
  private lastContent = ''

  reset(): void {
    this.segments = []
    this.covered = 0
    this.lastContent = ''
  }

  render(content: string): string {
    if (!content) {
      this.reset()
      return ''
    }
    if (!content.startsWith(this.lastContent)) this.reset()
    this.lastContent = content

    const cut = this.findSafeCut(content)
    if (cut > this.covered) {
      this.segments.push(renderFragment(content.slice(this.covered, cut)))
      this.covered = cut
    }
    const tail = content.slice(this.covered)
    const tailHtml = tail ? renderFragment(completeStreamingTail(tail)) : ''
    return this.segments.join('') + tailHtml
  }

  /** 返回最后一个「围栏外空行之后」的位置；扫描从已覆盖位置开始，围栏状态必为闭合 */
  private findSafeCut(content: string): number {
    let fence: FenceState | null = null
    let cut = this.covered
    let pos = this.covered
    while (pos < content.length) {
      const newline = content.indexOf('\n', pos)
      const end = newline < 0 ? content.length : newline + 1
      const line = content.slice(pos, newline < 0 ? content.length : newline)
      fence = scanFence(line, fence)
      if (!fence && line.trim() === '') cut = end
      pos = end
    }
    return cut
  }
}

export function useMarkdown() {
  return { processMarkdown }
}
