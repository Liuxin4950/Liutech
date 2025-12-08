import { marked } from 'marked'
import DOMPurify from 'dompurify'
import hljs from 'highlight.js'
import { ref, computed } from 'vue'

interface MarkdownCache {
  [key: string]: string
}

export function useMarkdown() {
  // Cache for processed markdown to improve performance
  const cache = ref<MarkdownCache>({})
  const maxCacheSize = 1000

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
    // Only allow http, https, mailto protocols
    if (href && !href.match(/^(https?:\/\/|mailto:|\/)/)) {
      return text
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

    // For streaming, we need special handling
    if (isStreaming) {
      return processStreamingMarkdown(content)
    }

    // Check cache first
    const cacheKey = content
    if (cache.value[cacheKey]) {
      return cache.value[cacheKey]
    }

    try {
      // Parse markdown to HTML
      const html = marked.parse(content) as string

      // Sanitize HTML
      const sanitizedHtml = DOMPurify.sanitize(html, sanitizeConfig)

      // Cache result (with size limit)
      if (Object.keys(cache.value).length >= maxCacheSize) {
        // Remove oldest entries (simple FIFO)
        const keys = Object.keys(cache.value)
        for (let i = 0; i < 100; i++) {
          delete cache.value[keys[i]]
        }
      }
      cache.value[cacheKey] = sanitizedHtml

      return sanitizedHtml
    } catch (error) {
      console.error('Markdown processing error:', error)
      // Fallback to sanitized plain text
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
      return DOMPurify.sanitize(html, sanitizeConfig)
    } catch (error) {
      console.error('Streaming markdown processing error:', error)
      // Fallback to plain text with line breaks
      return DOMPurify.sanitize(content.replace(/\n/g, '<br>'))
    }
  }

  /**
   * Clear the markdown cache
   */
  const clearCache = () => {
    cache.value = {}
  }

  /**
   * Get cache statistics
   */
  const getCacheStats = () => {
    return {
      size: Object.keys(cache.value).length,
      maxSize: maxCacheSize
    }
  }

  return {
    processMarkdown,
    clearCache,
    getCacheStats
  }
}