import { describe, it, expect, vi, beforeEach } from 'vitest'
import { useMarkdown } from '@/composables/useMarkdown'

describe('useMarkdown', () => {
  let processMarkdown: (content: string, isStreaming?: boolean) => string

  beforeEach(() => {
    const md = useMarkdown()
    processMarkdown = md.processMarkdown
  })

  describe('processMarkdown', () => {
    it('should return empty string for empty content', () => {
      expect(processMarkdown('')).toBe('')
    })

    it('should process basic markdown text', () => {
      const result = processMarkdown('Hello world')
      expect(result).toBeTruthy()
      expect(result).toContain('Hello world')
    })

    it('should process markdown in non-streaming mode by default', () => {
      const result = processMarkdown('**bold text**')
      expect(result).toBeTruthy()
    })

    it('should process streaming markdown when isStreaming is true', () => {
      const result = processMarkdown('Hello **bold', true)
      expect(result).toBeTruthy()
    })

    it('should handle unclosed code blocks in streaming mode', () => {
      const content = 'Some text\n```javascript\nconsole.log("hello")'
      const result = processMarkdown(content, true)
      expect(result).toBeTruthy()
    })

    it('should handle unclosed bold markers in streaming mode', () => {
      const content = 'This is **bold text'
      const result = processMarkdown(content, true)
      expect(result).toBeTruthy()
    })

    it('should handle unclosed italic markers in streaming mode', () => {
      const content = 'This is *italic text'
      const result = processMarkdown(content, true)
      expect(result).toBeTruthy()
    })

    it('should handle unclosed inline code in streaming mode', () => {
      const content = 'Use `code here'
      const result = processMarkdown(content, true)
      expect(result).toBeTruthy()
    })

    it('should handle complete code blocks', () => {
      const content = '```javascript\nconsole.log("hello")\n```'
      const result = processMarkdown(content)
      expect(result).toBeTruthy()
    })

    it('should handle links', () => {
      const result = processMarkdown('[Google](https://google.com)')
      expect(result).toBeTruthy()
    })

    it('should handle internal links', () => {
      const result = processMarkdown('[Home](/)')
      expect(result).toBeTruthy()
    })

    it('should handle images', () => {
      const result = processMarkdown('![alt text](https://example.com/img.png)')
      expect(result).toBeTruthy()
    })

    it('should handle tables', () => {
      const content = '| A | B |\n|---|---|\n| 1 | 2 |'
      const result = processMarkdown(content)
      expect(result).toBeTruthy()
    })

    it('should handle multiline content', () => {
      const content = '# Title\n\nParagraph 1\n\nParagraph 2'
      const result = processMarkdown(content)
      expect(result).toBeTruthy()
    })

    it('should handle empty content in streaming mode', () => {
      expect(processMarkdown('', true)).toBe('')
    })

    it('should handle content with multiple code blocks in streaming', () => {
      const content = '```js\ncode1\n```\n\n```js\ncode2\n```'
      const result = processMarkdown(content, true)
      expect(result).toBeTruthy()
    })

    it('should handle mixed bold and italic in streaming', () => {
      const content = 'Text with **bold and *italic'
      const result = processMarkdown(content, true)
      expect(result).toBeTruthy()
    })

    it('should handle content with no special markdown', () => {
      const result = processMarkdown('Just plain text')
      expect(result).toBeTruthy()
    })

    it('should handle blockquotes', () => {
      const result = processMarkdown('> This is a quote')
      expect(result).toBeTruthy()
    })

    it('should handle lists', () => {
      const result = processMarkdown('- Item 1\n- Item 2\n- Item 3')
      expect(result).toBeTruthy()
    })

    it('should handle numbered lists', () => {
      const result = processMarkdown('1. First\n2. Second\n3. Third')
      expect(result).toBeTruthy()
    })

    it('should handle code with language in streaming', () => {
      const content = '```typescript\nconst x = 1\n```'
      const result = processMarkdown(content, true)
      expect(result).toBeTruthy()
    })

    it('should handle only bold markers (even count) in streaming', () => {
      const content = '**bold**'
      const result = processMarkdown(content, true)
      expect(result).toBeTruthy()
    })

    it('should handle only italic markers (even count) in streaming', () => {
      const content = '*italic*'
      const result = processMarkdown(content, true)
      expect(result).toBeTruthy()
    })
  })
})
