import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import CommentForm from '@/components/CommentForm.vue'
import { CommentService } from '@/services/comment'

// Mock dependencies
vi.mock('@/services/comment', () => ({
  CommentService: {
    createComment: vi.fn()
  }
}))

vi.mock('@/composables/useErrorHandler', () => ({
  useErrorHandler: () => ({
    handleAsync: vi.fn(async (fn: Function) => {
      try {
        return await fn()
      } catch {
        return null
      }
    })
  })
}))

vi.mock('@/stores/user', () => ({
  useUserStore: vi.fn(() => ({
    isLoggedIn: true,
    userInfo: { id: 1, username: 'testuser' }
  }))
}))

const mockCommentService = vi.mocked(CommentService)

describe('CommentForm', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  const createWrapper = (props = {}) => {
    return mount(CommentForm, {
      props: {
        postId: 1,
        ...props
      },
      global: {
        stubs: {
          'router-link': { template: '<a><slot /></a>' }
        }
      }
    })
  }

  describe('rendering', () => {
    it('should render comment form', () => {
      const wrapper = createWrapper()
      expect(wrapper.find('.comment-form').exists()).toBe(true)
    })

    it('should show "发表评论" header when no parentId', () => {
      const wrapper = createWrapper()
      expect(wrapper.text()).toContain('发表评论')
    })

    it('should show "回复评论" header when parentId provided', () => {
      const wrapper = createWrapper({ parentId: 5 })
      expect(wrapper.text()).toContain('回复评论')
    })

    it('should show cancel button when parentId provided', () => {
      const wrapper = createWrapper({ parentId: 5 })
      expect(wrapper.find('.cancel-btn').exists()).toBe(true)
    })

    it('should not show cancel button when no parentId', () => {
      const wrapper = createWrapper()
      expect(wrapper.find('.cancel-btn').exists()).toBe(false)
    })

    it('should have a textarea', () => {
      const wrapper = createWrapper()
      expect(wrapper.find('textarea').exists()).toBe(true)
    })

    it('should show character counter', () => {
      const wrapper = createWrapper()
      expect(wrapper.text()).toContain('0/1000')
    })

    it('should show submit button with correct text for comment', () => {
      const wrapper = createWrapper()
      expect(wrapper.text()).toContain('发表评论')
    })

    it('should show submit button with correct text for reply', () => {
      const wrapper = createWrapper({ parentId: 5 })
      expect(wrapper.text()).toContain('回复')
    })
  })

  describe('textarea interaction', () => {
    it('should update character counter on input', async () => {
      const wrapper = createWrapper()
      const textarea = wrapper.find('textarea')
      await textarea.setValue('Hello')
      expect(wrapper.text()).toContain('5/1000')
    })

    it('should have placeholder text for comment', () => {
      const wrapper = createWrapper()
      const textarea = wrapper.find('textarea')
      expect(textarea.attributes('placeholder')).toContain('评论')
    })

    it('should have placeholder text for reply', () => {
      const wrapper = createWrapper({ parentId: 5 })
      const textarea = wrapper.find('textarea')
      expect(textarea.attributes('placeholder')).toContain('回复')
    })

    it('should have maxlength of 1000', () => {
      const wrapper = createWrapper()
      const textarea = wrapper.find('textarea')
      expect(textarea.attributes('maxlength')).toBe('1000')
    })
  })

  describe('submit behavior', () => {
    it('should call CommentService.createComment on submit', async () => {
      const mockComment = { id: 1, postId: 1, content: 'test', createdAt: '2025-01-01', user: { id: 1, username: 'test' }, children: [] }
      mockCommentService.createComment.mockResolvedValue(mockComment as any)

      const wrapper = createWrapper({ postId: 1 })
      const textarea = wrapper.find('textarea')
      await textarea.setValue('Great article!')

      const form = wrapper.find('form')
      await form.trigger('submit')

      // Wait for async operations
      await vi.dynamicImportSettled()
      await new Promise(r => setTimeout(r, 10))

      expect(mockCommentService.createComment).toHaveBeenCalledWith({
        postId: 1,
        content: 'Great article!',
        parentId: undefined
      })
    })

    it('should include parentId when replying', async () => {
      const mockComment = { id: 2, postId: 1, content: 'reply', parentId: 5, createdAt: '2025-01-01', user: { id: 1, username: 'test' }, children: [] }
      mockCommentService.createComment.mockResolvedValue(mockComment as any)

      const wrapper = createWrapper({ postId: 1, parentId: 5 })
      const textarea = wrapper.find('textarea')
      await textarea.setValue('Nice reply')

      const form = wrapper.find('form')
      await form.trigger('submit')

      await vi.dynamicImportSettled()
      await new Promise(r => setTimeout(r, 10))

      expect(mockCommentService.createComment).toHaveBeenCalledWith({
        postId: 1,
        content: 'Nice reply',
        parentId: 5
      })
    })

    it('should emit commentCreated after successful submit', async () => {
      const mockComment = { id: 1, postId: 1, content: 'test', createdAt: '2025-01-01', user: { id: 1, username: 'test' }, children: [] }
      mockCommentService.createComment.mockResolvedValue(mockComment as any)

      const wrapper = createWrapper()
      await wrapper.find('textarea').setValue('Good article')
      await wrapper.find('form').trigger('submit')

      await vi.dynamicImportSettled()
      await new Promise(r => setTimeout(r, 10))

      expect(wrapper.emitted('commentCreated')).toBeTruthy()
    })

    it('should emit cancel after successful reply submit', async () => {
      const mockComment = { id: 2, postId: 1, content: 'reply', parentId: 5, createdAt: '2025-01-01', user: { id: 1, username: 'test' }, children: [] }
      mockCommentService.createComment.mockResolvedValue(mockComment as any)

      const wrapper = createWrapper({ parentId: 5 })
      await wrapper.find('textarea').setValue('reply')
      await wrapper.find('form').trigger('submit')

      await vi.dynamicImportSettled()
      await new Promise(r => setTimeout(r, 10))

      expect(wrapper.emitted('cancel')).toBeTruthy()
    })

    it('should clear textarea after successful submit', async () => {
      const mockComment = { id: 1, postId: 1, content: 'test', createdAt: '2025-01-01', user: { id: 1, username: 'test' }, children: [] }
      mockCommentService.createComment.mockResolvedValue(mockComment as any)

      const wrapper = createWrapper()
      await wrapper.find('textarea').setValue('Some comment')
      await wrapper.find('form').trigger('submit')

      await vi.dynamicImportSettled()
      await new Promise(r => setTimeout(r, 10))

      expect(wrapper.find('textarea').element.value).toBe('')
    })
  })

  describe('cancel', () => {
    it('should emit cancel when cancel button is clicked', async () => {
      const wrapper = createWrapper({ parentId: 5 })
      await wrapper.find('.cancel-btn').trigger('click')
      expect(wrapper.emitted('cancel')).toBeTruthy()
    })
  })
})
