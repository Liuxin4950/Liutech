import { describe, it, expect } from 'vitest'
import { handleImageError, errImg } from '@/composables/useImageFallback'

describe('useImageFallback', () => {
  describe('errImg', () => {
    it('should export a string (image path)', () => {
      expect(typeof errImg).toBe('string')
      expect(errImg.length).toBeGreaterThan(0)
    })
  })

  describe('handleImageError', () => {
    it('should set img.src to errImg on error', () => {
      const img = document.createElement('img')
      img.src = 'https://example.com/broken.jpg'

      const event = new Event('error')
      Object.defineProperty(event, 'target', { value: img, writable: false })

      handleImageError(event)

      expect(img.src).toContain('err.png')
    })

    it('should work with img elements that have different initial src', () => {
      const img = document.createElement('img')
      img.src = '/some/other/path.png'

      const event = new Event('error')
      Object.defineProperty(event, 'target', { value: img, writable: false })

      handleImageError(event)

      expect(img.src).toContain('err.png')
    })
  })
})
