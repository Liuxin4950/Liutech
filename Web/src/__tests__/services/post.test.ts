import { describe, it, expect, vi, beforeEach } from 'vitest'
import { PostService } from '@/services/post'
import * as api from '@/services/api'

// Mock api module
vi.mock('@/services/api', () => ({
  get: vi.fn(),
  post: vi.fn(),
  put: vi.fn(),
  del: vi.fn(),
  getAxiosInstance: vi.fn(() => ({
    get: vi.fn()
  }))
}))

const mockGet = vi.mocked(api.get)
const mockPost = vi.mocked(api.post)
const mockPut = vi.mocked(api.put)
const mockDel = vi.mocked(api.del)

describe('PostService', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  // ---------- getPostList ----------
  describe('getPostList', () => {
    it('should call GET /posts with default params', async () => {
      const mockData = { records: [], total: 0, size: 10, current: 1, pages: 0 }
      mockGet.mockResolvedValue({ code: 200, message: 'ok', data: mockData })

      const result = await PostService.getPostList()

      expect(mockGet).toHaveBeenCalledWith('/posts', {})
      expect(result).toEqual(mockData)
    })

    it('should pass query params and transform sortBy popular to hot', async () => {
      const mockData = { records: [], total: 0, size: 10, current: 1, pages: 0 }
      mockGet.mockResolvedValue({ code: 200, message: 'ok', data: mockData })

      await PostService.getPostList({ page: 2, size: 20, sortBy: 'popular' })

      expect(mockGet).toHaveBeenCalledWith('/posts', { page: 2, size: 20, sort: 'hot' })
    })

    it('should pass sortBy latest as-is', async () => {
      mockGet.mockResolvedValue({ code: 200, message: 'ok', data: {} })
      await PostService.getPostList({ sortBy: 'latest' })
      expect(mockGet).toHaveBeenCalledWith('/posts', { sort: 'latest' })
    })

    it('should throw on error', async () => {
      mockGet.mockRejectedValue(new Error('network'))
      await expect(PostService.getPostList()).rejects.toThrow('network')
    })
  })

  // ---------- getPostDetail ----------
  describe('getPostDetail', () => {
    it('should call GET /posts/:id', async () => {
      const mockPost = { id: 1, title: 'Test', content: 'Hello' }
      mockGet.mockResolvedValue({ code: 200, message: 'ok', data: mockPost })

      const result = await PostService.getPostDetail(1)

      expect(mockGet).toHaveBeenCalledWith('/posts/1')
      expect(result).toEqual(mockPost)
    })
  })

  // ---------- getPostDetailForAdmin ----------
  describe('getPostDetailForAdmin', () => {
    it('should call GET /admin/posts/:id', async () => {
      mockGet.mockResolvedValue({ code: 200, message: 'ok', data: { id: 5 } })
      const result = await PostService.getPostDetailForAdmin(5)
      expect(mockGet).toHaveBeenCalledWith('/admin/posts/5')
      expect(result).toEqual({ id: 5 })
    })
  })

  // ---------- getLatestPosts ----------
  describe('getLatestPosts', () => {
    it('should call GET /posts/latest with default limit 10', async () => {
      mockGet.mockResolvedValue({ code: 200, message: 'ok', data: [] })
      await PostService.getLatestPosts()
      expect(mockGet).toHaveBeenCalledWith('/posts/latest', { limit: 10 })
    })

    it('should accept custom limit', async () => {
      mockGet.mockResolvedValue({ code: 200, message: 'ok', data: [] })
      await PostService.getLatestPosts(5)
      expect(mockGet).toHaveBeenCalledWith('/posts/latest', { limit: 5 })
    })
  })

  // ---------- createPost ----------
  describe('createPost', () => {
    it('should call POST /posts with post data', async () => {
      const postData = { title: 'New', content: 'Body', categoryId: 1, status: 'published' as const }
      const mockResponse = { id: 10, title: 'New', status: 'published', createdAt: '2025-01-01' }
      mockPost.mockResolvedValue({ code: 200, message: 'ok', data: mockResponse })

      const result = await PostService.createPost(postData)

      expect(mockPost).toHaveBeenCalledWith('/posts', postData)
      expect(result).toEqual(mockResponse)
    })
  })

  // ---------- getDraftList ----------
  describe('getDraftList', () => {
    it('should call GET /posts/drafts', async () => {
      mockGet.mockResolvedValue({ code: 200, message: 'ok', data: { records: [] } })
      await PostService.getDraftList({ page: 1 })
      expect(mockGet).toHaveBeenCalledWith('/posts/drafts', { page: 1 })
    })
  })

  // ---------- updatePost ----------
  describe('updatePost', () => {
    it('should call PUT /posts/:id', async () => {
      mockPut.mockResolvedValue({ code: 200, message: 'ok', data: true })
      const result = await PostService.updatePost(3, { title: 'Updated' })
      expect(mockPut).toHaveBeenCalledWith('/posts/3', { title: 'Updated' })
      expect(result).toBe(true)
    })
  })

  // ---------- deletePost ----------
  describe('deletePost', () => {
    it('should call DELETE /posts/:id', async () => {
      mockDel.mockResolvedValue({ code: 200, message: 'ok', data: undefined })
      await PostService.deletePost(7)
      expect(mockDel).toHaveBeenCalledWith('/posts/7')
    })
  })

  // ---------- publishPost ----------
  describe('publishPost', () => {
    it('should call PUT /posts/:id/publish', async () => {
      mockPut.mockResolvedValue({ code: 200, message: 'ok', data: true })
      const result = await PostService.publishPost(2)
      expect(mockPut).toHaveBeenCalledWith('/posts/2/publish')
      expect(result).toBe(true)
    })
  })

  // ---------- unpublishPost ----------
  describe('unpublishPost', () => {
    it('should call PUT /posts/:id/unpublish', async () => {
      mockPut.mockResolvedValue({ code: 200, message: 'ok', data: undefined })
      await PostService.unpublishPost(2)
      expect(mockPut).toHaveBeenCalledWith('/posts/2/unpublish')
    })
  })

  // ---------- getMyPosts ----------
  describe('getMyPosts', () => {
    it('should call GET /posts/my', async () => {
      mockGet.mockResolvedValue({ code: 200, message: 'ok', data: { records: [] } })
      await PostService.getMyPosts()
      expect(mockGet).toHaveBeenCalledWith('/posts/my', {})
    })
  })

  // ---------- likePost ----------
  describe('likePost', () => {
    it('should call POST /posts/:id/like', async () => {
      mockPost.mockResolvedValue({ code: 200, message: 'ok', data: undefined })
      await PostService.likePost(4)
      expect(mockPost).toHaveBeenCalledWith('/posts/4/like')
    })
  })

  // ---------- favoritePost ----------
  describe('favoritePost', () => {
    it('should call POST /posts/:id/favorite', async () => {
      mockPost.mockResolvedValue({ code: 200, message: 'ok', data: undefined })
      await PostService.favoritePost(4)
      expect(mockPost).toHaveBeenCalledWith('/posts/4/favorite')
    })
  })

  // ---------- uploadAttachment ----------
  describe('uploadAttachment', () => {
    it('should POST multipart form data to /upload/resource', async () => {
      const mockResponse = { resourceId: 1, fileUrl: '/file.pdf', fileName: 'file.pdf', fileSize: 1024 }
      mockPost.mockResolvedValue({ code: 200, message: 'ok', data: mockResponse })

      const file = new File(['content'], 'file.pdf', { type: 'application/pdf' })
      const result = await PostService.uploadAttachment(file, 'draft-key-1')

      expect(mockPost).toHaveBeenCalledWith('/upload/resource', expect.any(FormData), expect.objectContaining({
        headers: { 'Content-Type': 'multipart/form-data' }
      }))
      expect(result).toEqual(mockResponse)
    })
  })

  // ---------- getPostAttachments ----------
  describe('getPostAttachments', () => {
    it('should call GET /upload/attachments/post/:postId', async () => {
      mockGet.mockResolvedValue({ code: 200, message: 'ok', data: [] })
      await PostService.getPostAttachments(10)
      expect(mockGet).toHaveBeenCalledWith('/upload/attachments/post/10')
    })
  })

  // ---------- deleteAttachment ----------
  describe('deleteAttachment', () => {
    it('should call DELETE /upload/attachments/:resourceId', async () => {
      mockDel.mockResolvedValue({ code: 200, message: 'ok', data: undefined })
      await PostService.deleteAttachment(99)
      expect(mockDel).toHaveBeenCalledWith('/upload/attachments/99')
    })
  })

  // ---------- purchaseResource ----------
  describe('purchaseResource', () => {
    it('should call POST /resource/purchase/:resourceId', async () => {
      mockPost.mockResolvedValue({ code: 200, message: 'ok', data: undefined })
      await PostService.purchaseResource(5)
      expect(mockPost).toHaveBeenCalledWith('/resource/purchase/5')
    })
  })

  // ---------- updateAttachmentMeta ----------
  describe('updateAttachmentMeta', () => {
    it('should call PUT /upload/attachments/:resourceId/meta with params', async () => {
      mockPut.mockResolvedValue({ code: 200, message: 'ok', data: undefined })
      await PostService.updateAttachmentMeta(3, 1, 100)
      expect(mockPut).toHaveBeenCalledWith('/upload/attachments/3/meta', null, { params: { downloadType: 1, pointsNeeded: 100 } })
    })
  })

  // ---------- getFavoritePosts ----------
  describe('getFavoritePosts', () => {
    it('should call GET /posts/favorites', async () => {
      mockGet.mockResolvedValue({ code: 200, message: 'ok', data: { records: [] } })
      await PostService.getFavoritePosts({ page: 1 })
      expect(mockGet).toHaveBeenCalledWith('/posts/favorites', { page: 1 })
    })
  })

  // ---------- createExternalLinkResource ----------
  describe('createExternalLinkResource', () => {
    it('should POST form data to /upload/resource/external', async () => {
      const mockResponse = { resourceId: 2, fileUrl: '', fileName: 'link', fileSize: 0 }
      mockPost.mockResolvedValue({ code: 200, message: 'ok', data: mockResponse })

      const result = await PostService.createExternalLinkResource(
        'My Link', 'desc', 'https://example.com', 'note', 'draft-key', 'link', 0, 0
      )

      expect(mockPost).toHaveBeenCalledWith('/upload/resource/external', expect.any(FormData), expect.objectContaining({
        headers: { 'Content-Type': 'multipart/form-data' }
      }))
      expect(result).toEqual(mockResponse)
    })

    it('should handle empty description and purchasedNote', async () => {
      mockPost.mockResolvedValue({ code: 200, message: 'ok', data: {} })
      await PostService.createExternalLinkResource(
        'Link', '', 'https://example.com', '', 'key', 'link', 1, 50
      )
      expect(mockPost).toHaveBeenCalled()
    })
  })

  // ---------- downloadResource ----------
  describe('downloadResource', () => {
    it('should download resource via blob', async () => {
      const blobData = new Blob(['file content'])
      const mockAxiosInstance = {
        get: vi.fn().mockResolvedValue({ data: blobData })
      }
      const apiMod = await import('@/services/api')
      vi.mocked(apiMod.getAxiosInstance).mockReturnValue(mockAxiosInstance as any)

      // Mock DOM methods for download
      const mockClick = vi.fn()
      const mockAnchor = { href: '', download: '', click: mockClick } as any
      const originalCreateElement = document.createElement.bind(document)
      const createElementSpy = vi.spyOn(document, 'createElement').mockImplementation((tag: string) => {
        if (tag === 'a') return mockAnchor
        return originalCreateElement(tag)
      })
      const appendChildSpy = vi.spyOn(document.body, 'appendChild').mockReturnValue(null as any)
      const removeChildSpy = vi.spyOn(document.body, 'removeChild').mockReturnValue(null as any)

      await PostService.downloadResource(1, 'test.pdf')

      expect(mockAxiosInstance.get).toHaveBeenCalledWith('/resource/download/1', { responseType: 'blob' })
      expect(mockClick).toHaveBeenCalled()
      createElementSpy.mockRestore()
      appendChildSpy.mockRestore()
      removeChildSpy.mockRestore()
    })

    it('should throw on download error', async () => {
      const mockAxiosInstance = {
        get: vi.fn().mockRejectedValue(new Error('download failed'))
      }
      const apiMod = await import('@/services/api')
      vi.mocked(apiMod.getAxiosInstance).mockReturnValue(mockAxiosInstance as any)

      await expect(PostService.downloadResource(1, 'file.pdf')).rejects.toThrow('download failed')
    })
  })

  // ---------- error paths ----------
  describe('error paths', () => {
    it('getPostDetail should throw on error', async () => {
      mockGet.mockRejectedValue(new Error('not found'))
      await expect(PostService.getPostDetail(999)).rejects.toThrow('not found')
    })

    it('getPostDetailForAdmin should throw on error', async () => {
      mockGet.mockRejectedValue(new Error('forbidden'))
      await expect(PostService.getPostDetailForAdmin(1)).rejects.toThrow('forbidden')
    })

    it('createPost should throw on error', async () => {
      mockPost.mockRejectedValue(new Error('validation'))
      await expect(PostService.createPost({ title: '', content: '', categoryId: 1, status: 'draft' })).rejects.toThrow('validation')
    })

    it('updatePost should throw on error', async () => {
      mockPut.mockRejectedValue(new Error('conflict'))
      await expect(PostService.updatePost(1, {})).rejects.toThrow('conflict')
    })

    it('deletePost should throw on error', async () => {
      mockDel.mockRejectedValue(new Error('not found'))
      await expect(PostService.deletePost(999)).rejects.toThrow('not found')
    })

    it('likePost should throw on error', async () => {
      mockPost.mockRejectedValue(new Error('unauthorized'))
      await expect(PostService.likePost(1)).rejects.toThrow('unauthorized')
    })

    it('favoritePost should throw on error', async () => {
      mockPost.mockRejectedValue(new Error('unauthorized'))
      await expect(PostService.favoritePost(1)).rejects.toThrow('unauthorized')
    })

    it('uploadAttachment should throw on error', async () => {
      mockPost.mockRejectedValue(new Error('too large'))
      const file = new File(['x'], 'x.pdf')
      await expect(PostService.uploadAttachment(file, 'key')).rejects.toThrow('too large')
    })

    it('getPostAttachments should throw on error', async () => {
      mockGet.mockRejectedValue(new Error('server error'))
      await expect(PostService.getPostAttachments(1)).rejects.toThrow('server error')
    })

    it('deleteAttachment should throw on error', async () => {
      mockDel.mockRejectedValue(new Error('not found'))
      await expect(PostService.deleteAttachment(999)).rejects.toThrow('not found')
    })

    it('purchaseResource should throw on error', async () => {
      mockPost.mockRejectedValue(new Error('insufficient points'))
      await expect(PostService.purchaseResource(1)).rejects.toThrow('insufficient points')
    })

    it('updateAttachmentMeta should throw on error', async () => {
      mockPut.mockRejectedValue(new Error('server error'))
      await expect(PostService.updateAttachmentMeta(1, 0, 0)).rejects.toThrow('server error')
    })

    it('getFavoritePosts should throw on error', async () => {
      mockGet.mockRejectedValue(new Error('server error'))
      await expect(PostService.getFavoritePosts()).rejects.toThrow('server error')
    })

    it('createExternalLinkResource should throw on error', async () => {
      mockPost.mockRejectedValue(new Error('invalid link'))
      await expect(PostService.createExternalLinkResource('n', 'd', 'link', 'note', 'k', 'link', 0, 0)).rejects.toThrow('invalid link')
    })

    it('publishPost should throw on error', async () => {
      mockPut.mockRejectedValue(new Error('not found'))
      await expect(PostService.publishPost(999)).rejects.toThrow('not found')
    })

    it('unpublishPost should throw on error', async () => {
      mockPut.mockRejectedValue(new Error('not found'))
      await expect(PostService.unpublishPost(999)).rejects.toThrow('not found')
    })

    it('getMyPosts should throw on error', async () => {
      mockGet.mockRejectedValue(new Error('unauthorized'))
      await expect(PostService.getMyPosts()).rejects.toThrow('unauthorized')
    })

    it('getDraftList should throw on error', async () => {
      mockGet.mockRejectedValue(new Error('server error'))
      await expect(PostService.getDraftList()).rejects.toThrow('server error')
    })

    it('getLatestPosts should throw on error', async () => {
      mockGet.mockRejectedValue(new Error('server error'))
      await expect(PostService.getLatestPosts()).rejects.toThrow('server error')
    })
  })
})
