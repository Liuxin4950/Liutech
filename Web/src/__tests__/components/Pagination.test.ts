import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import Pagination from '@/components/Pagination.vue'

// Mock breakpoints
vi.mock('@/utils/breakpoints', () => ({
  BREAKPOINT_MD: 768,
  BREAKPOINT_SM: 480,
  BREAKPOINT_LG: 1024
}))

describe('Pagination', () => {
  beforeEach(() => {
    // Default desktop width
    vi.stubGlobal('innerWidth', 1024)
    vi.stubGlobal('addEventListener', vi.fn())
    vi.stubGlobal('removeEventListener', vi.fn())
  })

  const createWrapper = (props = {}) => {
    return mount(Pagination, {
      props: {
        currentPage: 1,
        totalPages: 10,
        ...props
      }
    })
  }

  describe('rendering', () => {
    it('should render pagination when totalPages >= 1', () => {
      const wrapper = createWrapper()
      expect(wrapper.find('.pagination-container').exists()).toBe(true)
    })

    it('should not render when totalPages is 0', () => {
      const wrapper = createWrapper({ totalPages: 0 })
      expect(wrapper.find('.pagination-container').exists()).toBe(false)
    })

    it('should show page info text', () => {
      const wrapper = createWrapper({ currentPage: 3, totalPages: 10 })
      expect(wrapper.text()).toContain('第 3 页')
      expect(wrapper.text()).toContain('共 10 页')
    })

    it('should render previous and next buttons', () => {
      const wrapper = createWrapper()
      const buttons = wrapper.findAll('button')
      expect(buttons.length).toBeGreaterThanOrEqual(2)
      expect(wrapper.text()).toContain('上一页')
      expect(wrapper.text()).toContain('下一页')
    })
  })

  describe('page navigation', () => {
    it('should disable previous button on first page', () => {
      const wrapper = createWrapper({ currentPage: 1 })
      const prevBtn = wrapper.findAll('button').find(b => b.text().includes('上一页'))
      expect(prevBtn?.attributes('disabled')).toBeDefined()
    })

    it('should disable next button on last page', () => {
      const wrapper = createWrapper({ currentPage: 10, totalPages: 10 })
      const nextBtn = wrapper.findAll('button').find(b => b.text().includes('下一页'))
      expect(nextBtn?.attributes('disabled')).toBeDefined()
    })

    it('should emit pageChange when clicking next', async () => {
      const wrapper = createWrapper({ currentPage: 1, totalPages: 10 })
      const nextBtn = wrapper.findAll('button').find(b => b.text().includes('下一页'))
      await nextBtn?.trigger('click')
      expect(wrapper.emitted('pageChange')).toEqual([[2]])
    })

    it('should emit pageChange when clicking previous', async () => {
      const wrapper = createWrapper({ currentPage: 5, totalPages: 10 })
      const prevBtn = wrapper.findAll('button').find(b => b.text().includes('上一页'))
      await prevBtn?.trigger('click')
      expect(wrapper.emitted('pageChange')).toEqual([[4]])
    })

    it('should not emit when clicking previous on first page', async () => {
      const wrapper = createWrapper({ currentPage: 1 })
      const prevBtn = wrapper.findAll('button').find(b => b.text().includes('上一页'))
      await prevBtn?.trigger('click')
      expect(wrapper.emitted('pageChange')).toBeUndefined()
    })

    it('should emit pageChange when clicking a page number', async () => {
      const wrapper = createWrapper({ currentPage: 1, totalPages: 5 })
      // Find page number buttons (not prev/next)
      const pageButtons = wrapper.findAll('button').filter(b => {
        const text = b.text()
        return text !== '上一页' && text !== '下一页' && text !== '...'
      })
      if (pageButtons.length > 1) {
        await pageButtons[1].trigger('click') // click page 2
        expect(wrapper.emitted('pageChange')).toEqual([[2]])
      }
    })
  })

  describe('page number display', () => {
    it('should show all pages when total <= maxVisiblePages', () => {
      const wrapper = createWrapper({ currentPage: 1, totalPages: 5, maxVisiblePages: 7 })
      const pageText = wrapper.text()
      for (let i = 1; i <= 5; i++) {
        expect(pageText).toContain(String(i))
      }
    })

    it('should show ellipsis when total > maxVisiblePages', () => {
      const wrapper = createWrapper({ currentPage: 1, totalPages: 20, maxVisiblePages: 7 })
      expect(wrapper.text()).toContain('...')
    })

    it('should show ellipsis at end when current page is near start', () => {
      const wrapper = createWrapper({ currentPage: 2, totalPages: 20, maxVisiblePages: 7 })
      expect(wrapper.text()).toContain('...')
      expect(wrapper.text()).toContain('20')
    })

    it('should show ellipsis at start when current page is near end', () => {
      const wrapper = createWrapper({ currentPage: 19, totalPages: 20, maxVisiblePages: 7 })
      expect(wrapper.text()).toContain('...')
      expect(wrapper.text()).toContain('1')
    })

    it('should show ellipsis on both sides when current page is in middle', () => {
      const wrapper = createWrapper({ currentPage: 10, totalPages: 20, maxVisiblePages: 7 })
      const ellipsisCount = wrapper.findAll('button').filter(b => b.text() === '...').length
      expect(ellipsisCount).toBe(2)
    })
  })

  describe('props', () => {
    it('should respect showPageNumbers=false', () => {
      const wrapper = createWrapper({ showPageNumbers: false })
      // Page number buttons should not be rendered
      const pageButtons = wrapper.findAll('button').filter(b => {
        const text = b.text()
        return text !== '上一页' && text !== '下一页'
      })
      expect(pageButtons.length).toBe(0)
    })

    it('should use custom maxVisiblePages', () => {
      const wrapper = createWrapper({ currentPage: 1, totalPages: 20, maxVisiblePages: 5 })
      // Should show fewer pages
      const pageButtons = wrapper.findAll('button').filter(b => {
        const num = parseInt(b.text())
        return !isNaN(num)
      })
      expect(pageButtons.length).toBeLessThanOrEqual(5)
    })
  })
})
