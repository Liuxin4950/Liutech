import { marked } from 'marked'
import DOMPurify from 'dompurify'
import hljs from 'highlight.js/lib/core'
import javascript from 'highlight.js/lib/languages/javascript'
import typescript from 'highlight.js/lib/languages/typescript'
import java from 'highlight.js/lib/languages/java'
import python from 'highlight.js/lib/languages/python'
import css from 'highlight.js/lib/languages/css'
import sql from 'highlight.js/lib/languages/sql'
import bash from 'highlight.js/lib/languages/bash'
import json from 'highlight.js/lib/languages/json'
import xml from 'highlight.js/lib/languages/xml'
import markdown from 'highlight.js/lib/languages/markdown'

hljs.registerLanguage('javascript', javascript)
hljs.registerLanguage('typescript', typescript)
hljs.registerLanguage('java', java)
hljs.registerLanguage('python', python)
hljs.registerLanguage('css', css)
hljs.registerLanguage('sql', sql)
hljs.registerLanguage('bash', bash)
hljs.registerLanguage('json', json)
hljs.registerLanguage('xml', xml)
hljs.registerLanguage('markdown', markdown)

export function useMarkdown() {
  // Security configuration for DOMPurify
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

  // Configure marked options
  marked.setOptions({
    breaks: true,
    gfm: true
  })

  // Custom renderer for better control
  const renderer = new marked.Renderer()

  // Custom code block renderer with syntax highlighting
  renderer.code = (code: string, language?: string) => {
    const validLanguage = language && hljs.getLanguage(language) ? language : 'plaintext'
    const highlighted = hljs.highlight(code, { language: validLanguage }).value
    return `<pre><code class="hljs language-${validLanguage}">${highlighted}</code></pre>`
  }

  // Custom link renderer for security
  renderer.link = (href, title, text) => {
    const titleAttr = title ? ` title="${title}"` : ''
    if (href && !href.match(/^(https?:\/\/|mailto:|\/)/)) {
      return text
    }
    // 内部链接走 Vue Router，不刷新页面
    if (href && href.startsWith('/')) {
      return `<a href="${href}"${titleAttr}>${text}</a>`
    }
    return `<a href="${href}"${titleAttr} target="_blank" rel="noopener noreferrer">${text}</a>`
  }

  // Custom image renderer
  renderer.image = (href, title, text) => {
    const titleAttr = title ? ` title="${title}"` : ''
    const altAttr = text ? ` alt="${text}"` : ''
    return `<img src="${href}"${altAttr}${titleAttr} loading="lazy">`
  }

  
  marked.use({ renderer })

  /**
   * Process markdown content to HTML
   * @param content - Raw markdown content
   * @param isStreaming - Whether content is being streamed
   * @returns Sanitized HTML
   */
  const processMarkdown = (content: string, isStreaming: boolean = false): string => {
    if (!content) return ''

    if (isStreaming) {
      return processStreamingMarkdown(content)
    }

    try {
      const html = marked.parse(content) as string
      let sanitizedHtml = DOMPurify.sanitize(html, sanitizeConfig)
      sanitizedHtml = sanitizedHtml.replace(/<pre><code[^>]*>\s*<\/code><\/pre>/g, '')
      return sanitizedHtml
    } catch {
      return DOMPurify.sanitize(content.replace(/\n/g, '<br>'))
    }
  }

  /**
   * Process markdown for streaming content
   * Handles incomplete markdown gracefully
   */
  const processStreamingMarkdown = (content: string): string => {
    if (!content) return ''

    try {
      // For streaming, we need to handle incomplete markdown structures
      let processedContent = content

      // Handle unclosed code blocks
      const codeBlockCount = (content.match(/```/g) || []).length
      if (codeBlockCount % 2 !== 0) {
        processedContent += '\n```' // Close unclosed code block
      }

      // Handle unclosed inline code
      const inlineCodeCount = (content.match(/`/g) || []).length
      if (inlineCodeCount % 2 !== 0) {
        processedContent += '`' // Close unclosed inline code
      }

      // Handle unclosed bold/italic
      const boldCount = (content.match(/\*\*/g) || []).length
      if (boldCount % 2 !== 0) {
        processedContent += '**'
      }

      const italicCount = (content.match(/(?<!\*)\*(?!\*)/g) || []).length
      if (italicCount % 2 !== 0) {
        processedContent += '*'
      }

      // Parse and sanitize
      const html = marked.parse(processedContent) as string
      let sanitizedHtml = DOMPurify.sanitize(html, sanitizeConfig)

      // 移除空的代码块
      sanitizedHtml = sanitizedHtml.replace(/<pre><code[^>]*>\s*<\/code><\/pre>/g, '')

      return sanitizedHtml
    } catch {
      // Fallback to plain text with line breaks
      return DOMPurify.sanitize(content.replace(/\n/g, '<br>'))
    }
  }

  return {
    processMarkdown
  }
}