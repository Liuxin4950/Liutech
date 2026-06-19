import { describe, it, expect, vi, beforeEach } from 'vitest'
import { Ai } from '@/services/ai'
import * as api from '@/services/api'
import type { AiChatRequest } from '@/services/ai-types'

vi.mock('@/services/api', () => ({
  post: vi.fn(),
  ServiceType: { MAIN: 'main', AI: 'ai' }
}))

const mockPost = vi.mocked(api.post)

describe('Ai', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  describe('chat', () => {
    it('should POST to /chat with default chatType', async () => {
      const request: AiChatRequest = { message: 'Hello' }
      const aiResponse = {
        success: true,
        message: 'Hi there!',
        mode: 'guest' as const
      }
      mockPost.mockResolvedValue({ code: 200, message: 'ok', data: aiResponse })

      const result = await Ai.chat(request)

      expect(mockPost).toHaveBeenCalledWith('/chat', { message: 'Hello' }, {
        serviceType: 'ai'
      })
      expect(result).toEqual(aiResponse)
    })

    it('should POST to /writing when chatType is writing', async () => {
      const request: AiChatRequest = { message: 'Help me write', chatType: 'writing' }
      const aiResponse = {
        success: true,
        message: 'Here is your article...',
        mode: 'user' as const
      }
      mockPost.mockResolvedValue({ code: 200, message: 'ok', data: aiResponse })

      const result = await Ai.chat(request)

      expect(mockPost).toHaveBeenCalledWith('/writing', { message: 'Help me write' }, {
        serviceType: 'ai'
      })
      expect(result).toEqual(aiResponse)
    })

    it('should strip chatType from request body', async () => {
      const request: AiChatRequest = { message: 'test', chatType: 'chat', model: 'gpt-4' }
      mockPost.mockResolvedValue({ code: 200, message: 'ok', data: { success: true, message: 'ok' } })

      await Ai.chat(request)

      const sentBody = mockPost.mock.calls[0][1]
      expect(sentBody).not.toHaveProperty('chatType')
      expect(sentBody).toHaveProperty('message', 'test')
      expect(sentBody).toHaveProperty('model', 'gpt-4')
    })

    it('should pass conversationId when provided', async () => {
      const request: AiChatRequest = { message: 'follow up', conversationId: 42 }
      mockPost.mockResolvedValue({ code: 200, message: 'ok', data: { success: true, message: 'ok' } })

      await Ai.chat(request)

      const sentBody = mockPost.mock.calls[0][1]
      expect(sentBody).toHaveProperty('conversationId', 42)
    })

    it('should throw on error', async () => {
      mockPost.mockRejectedValue(new Error('AI service down'))
      await expect(Ai.chat({ message: 'hi' })).rejects.toThrow('AI service down')
    })

    it('should include article results in response', async () => {
      const aiResponse = {
        success: true,
        message: 'Found articles',
        articleResults: {
          source: 'search',
          query: 'test',
          items: [{ id: 1, title: 'Article 1', viewCount: 100, likeCount: 10 }]
        }
      }
      mockPost.mockResolvedValue({ code: 200, message: 'ok', data: aiResponse })

      const result = await Ai.chat({ message: 'find articles' })

      expect(result.articleResults?.items).toHaveLength(1)
      expect(result.articleResults?.items[0].title).toBe('Article 1')
    })

    it('should include plan steps in response', async () => {
      const aiResponse = {
        success: true,
        message: 'Working on it',
        plan: [
          { key: 'search', title: 'Searching articles', status: 'done' },
          { key: 'analyze', title: 'Analyzing results', status: 'running' }
        ]
      }
      mockPost.mockResolvedValue({ code: 200, message: 'ok', data: aiResponse })

      const result = await Ai.chat({ message: 'complex task' })

      expect(result.plan).toHaveLength(2)
      expect(result.plan![0].status).toBe('done')
    })
  })
})
