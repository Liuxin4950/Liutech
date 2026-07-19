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
  ALLOWED_ATTR: ['href', 'title', 'alt', 'src', 'class', 'id'],
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
  if (isStreaming) return processStreamingMarkdown(content)

  try {
    const html = marked.parse(content) as string
    return DOMPurify.sanitize(html, sanitizeConfig).replace(EMPTY_CODE_BLOCK_REGEX, '')
  } catch {
    // 解析失败回退为纯文本换行
    return DOMPurify.sanitize(content.replace(/\n/g, '<br>'))
  }
}

/** 流式 markdown：补全未闭合的代码块/行内代码/加粗/斜体，避免渲染错乱 */
const processStreamingMarkdown = (content: string): string => {
  if (!content) return ''

  try {
    let processed = content

    // 流式内容可能未闭合，补全后再解析
    if (countMatches(processed, /```/g) % 2 !== 0) processed += '\n```'
    if (countMatches(processed, /`/g) % 2 !== 0) processed += '`'
    if (countMatches(processed, /\*\*/g) % 2 !== 0) processed += '**'
    // 单 * 斜体：用负向断言排除 ** 中的 *
    if (countMatches(processed, /(?<!\*)\*(?!\*)/g) % 2 !== 0) processed += '*'

    const html = marked.parse(processed) as string
    return DOMPurify.sanitize(html, sanitizeConfig).replace(EMPTY_CODE_BLOCK_REGEX, '')
  } catch {
    return DOMPurify.sanitize(content.replace(/\n/g, '<br>'))
  }
}

export function useMarkdown() {
  return { processMarkdown }
}
