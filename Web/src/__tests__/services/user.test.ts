import { describe, it, expect, vi, beforeEach } from 'vitest'
import { UserService } from '@/services/user'
import * as api from '@/services/api'

vi.mock('@/services/api', () => ({
  get: vi.fn(),
  post: vi.fn(),
  put: vi.fn()
}))

const mockGet = vi.mocked(api.get)
const mockPost = vi.mocked(api.post)
const mockPut = vi.mocked(api.put)

describe('UserService', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
  })

  // ---------- login ----------
  describe('login', () => {
    it('should POST /user/login and save token', async () => {
      mockPost.mockResolvedValue({ code: 200, message: 'ok', data: { token: 'abc123' } })

      const result = await UserService.login({ username: 'test', password: 'pass' })

      expect(mockPost).toHaveBeenCalledWith('/user/login', { username: 'test', password: 'pass' })
      expect(localStorage.getItem('token')).toBe('abc123')
      expect(result).toEqual({ token: 'abc123' })
    })

    it('should not save token if response has no token', async () => {
      mockPost.mockResolvedValue({ code: 200, message: 'ok', data: { token: '' } })
      await UserService.login({ username: 'test', password: 'pass' })
      expect(localStorage.getItem('token')).toBeNull()
    })

    it('should throw on error', async () => {
      mockPost.mockRejectedValue(new Error('invalid credentials'))
      await expect(UserService.login({ username: 'x', password: 'y' })).rejects.toThrow()
    })
  })

  // ---------- register ----------
  describe('register', () => {
    it('should POST /user/register', async () => {
      const userData = { username: 'new', email: 'new@test.com', code: '123456', password: 'test123' }
      const mockResponse = { id: 1, username: 'new', email: 'new@test.com', points: 0 }
      mockPost.mockResolvedValue({ code: 200, message: 'ok', data: mockResponse })

      const result = await UserService.register(userData)

      expect(mockPost).toHaveBeenCalledWith('/user/register', userData)
      expect(result).toEqual(mockResponse)
    })
  })

  // ---------- getCurrentUser ----------
  describe('getCurrentUser', () => {
    it('should GET /user/current with timestamp param', async () => {
      const mockUser = { id: 1, username: 'test', email: 't@t.com', points: 100 }
      mockGet.mockResolvedValue({ code: 200, message: 'ok', data: mockUser })

      const result = await UserService.getCurrentUser()

      expect(mockGet).toHaveBeenCalledWith('/user/current', expect.objectContaining({ _t: expect.any(Number) }))
      expect(result).toEqual(mockUser)
    })
  })

  // ---------- changePassword ----------
  describe('changePassword', () => {
    it('should PUT /user/password', async () => {
      mockPut.mockResolvedValue({ code: 200, message: 'ok', data: null })
      const data = { oldPassword: 'old', newPassword: 'new', confirmPassword: 'new' }
      await UserService.changePassword(data)
      expect(mockPut).toHaveBeenCalledWith('/user/password', data)
    })
  })

  // ---------- updateProfile ----------
  describe('updateProfile', () => {
    it('should PUT /user/profile and return updated user', async () => {
      const updated = { id: 1, username: 'test', email: 't@t.com', points: 100, nickname: 'New' }
      mockPut.mockResolvedValue({ code: 200, message: 'ok', data: updated })

      const result = await UserService.updateProfile({ nickname: 'New' })

      expect(mockPut).toHaveBeenCalledWith('/user/profile', { nickname: 'New' })
      expect(result).toEqual(updated)
    })
  })

  // ---------- logout ----------
  describe('logout', () => {
    it('should remove token and user from localStorage', () => {
      localStorage.setItem('token', 'abc')
      localStorage.setItem('user', '{}')
      UserService.logout()
      expect(localStorage.getItem('token')).toBeNull()
      expect(localStorage.getItem('user')).toBeNull()
    })
  })

  // ---------- isLoggedIn ----------
  describe('isLoggedIn', () => {
    it('should return true when token exists', () => {
      localStorage.setItem('token', 'abc')
      expect(UserService.isLoggedIn()).toBe(true)
    })

    it('should return false when no token', () => {
      expect(UserService.isLoggedIn()).toBe(false)
    })
  })

  // ---------- getToken ----------
  describe('getToken', () => {
    it('should return token from localStorage', () => {
      localStorage.setItem('token', 'my-token')
      expect(UserService.getToken()).toBe('my-token')
    })

    it('should return null when no token', () => {
      expect(UserService.getToken()).toBeNull()
    })
  })

  // ---------- getUserStats ----------
  describe('getUserStats', () => {
    it('should GET /user/stats', async () => {
      const stats = { username: 'test', email: 't@t.com', points: 50, commentCount: 10, postCount: 5, draftCount: 2, viewCount: 100 }
      mockGet.mockResolvedValue({ code: 200, message: 'ok', data: stats })

      const result = await UserService.getUserStats()

      expect(mockGet).toHaveBeenCalledWith('/user/stats')
      expect(result).toEqual(stats)
    })
  })

  // ---------- checkin ----------
  describe('checkin', () => {
    it('should POST /user/checkin', async () => {
      const response = { pointsEarned: 10, totalPoints: 100, consecutiveDays: 3, checkinDate: '2025-01-01' }
      mockPost.mockResolvedValue({ code: 200, message: 'ok', data: response })

      const result = await UserService.checkin()

      expect(mockPost).toHaveBeenCalledWith('/user/checkin', {})
      expect(result).toEqual(response)
    })
  })

  // ---------- getCheckinStatus ----------
  describe('getCheckinStatus', () => {
    it('should GET /user/checkin/status', async () => {
      const status = { hasCheckedInToday: false, consecutiveDays: 0, totalCheckins: 0 }
      mockGet.mockResolvedValue({ code: 200, message: 'ok', data: status })

      const result = await UserService.getCheckinStatus()

      expect(mockGet).toHaveBeenCalledWith('/user/checkin/status')
      expect(result).toEqual(status)
    })
  })

  // ---------- getProfile ----------
  describe('getProfile', () => {
    it('should GET /user/profile', async () => {
      const profile = { name: 'Test', title: 'Dev', avatar: '', bio: '', stats: { posts: 1, comments: 2, views: 3 } }
      mockGet.mockResolvedValue({ code: 200, message: 'ok', data: profile })

      const result = await UserService.getProfile()
      expect(mockGet).toHaveBeenCalledWith('/user/profile')
      expect(result).toEqual(profile)
    })
  })

  // ---------- getAuthorProfile ----------
  describe('getAuthorProfile', () => {
    it('should GET /user/author/profile', async () => {
      mockGet.mockResolvedValue({ code: 200, message: 'ok', data: { name: 'Author' } })
      const result = await UserService.getAuthorProfile()
      expect(mockGet).toHaveBeenCalledWith('/user/author/profile')
      expect(result).toEqual({ name: 'Author' })
    })
  })

  // ---------- sendForgotPasswordCode ----------
  describe('sendForgotPasswordCode', () => {
    it('should POST /user/forgot-password', async () => {
      mockPost.mockResolvedValue({ code: 200, message: 'ok', data: null })
      await UserService.sendForgotPasswordCode('test@test.com')
      expect(mockPost).toHaveBeenCalledWith('/user/forgot-password', { email: 'test@test.com' })
    })
  })

  // ---------- sendRegisterCode ----------
  describe('sendRegisterCode', () => {
    it('should POST /user/register/send-code', async () => {
      mockPost.mockResolvedValue({ code: 200, message: 'ok', data: null })
      await UserService.sendRegisterCode('test@test.com')
      expect(mockPost).toHaveBeenCalledWith('/user/register/send-code', { email: 'test@test.com' })
    })
  })

  // ---------- resetPassword ----------
  describe('resetPassword', () => {
    it('should POST /user/reset-password', async () => {
      mockPost.mockResolvedValue({ code: 200, message: 'ok', data: null })
      const data = { email: 'test@test.com', code: '123', newPassword: 'new' }
      await UserService.resetPassword(data)
      expect(mockPost).toHaveBeenCalledWith('/user/reset-password', data)
    })
  })

  // ---------- sendEmailLoginCode ----------
  describe('sendEmailLoginCode', () => {
    it('should POST /user/login/email/send', async () => {
      mockPost.mockResolvedValue({ code: 200, message: 'ok', data: null })
      await UserService.sendEmailLoginCode('test@test.com')
      expect(mockPost).toHaveBeenCalledWith('/user/login/email/send', { email: 'test@test.com' })
    })
  })

  // ---------- verifyEmailLogin ----------
  describe('verifyEmailLogin', () => {
    it('should POST /user/login/email/verify and save token', async () => {
      mockPost.mockResolvedValue({ code: 200, message: 'ok', data: { token: 'email-token' } })

      const result = await UserService.verifyEmailLogin({ email: 'test@test.com', code: '123' })

      expect(mockPost).toHaveBeenCalledWith('/user/login/email/verify', { email: 'test@test.com', code: '123' })
      expect(localStorage.getItem('token')).toBe('email-token')
      expect(result).toEqual({ token: 'email-token' })
    })

    it('should throw on verify error', async () => {
      mockPost.mockRejectedValue(new Error('invalid code'))
      await expect(UserService.verifyEmailLogin({ email: 'test@test.com', code: '000' })).rejects.toThrow('invalid code')
    })

    it('should not save token when response has no token', async () => {
      mockPost.mockResolvedValue({ code: 200, message: 'ok', data: { token: '' } })
      await UserService.verifyEmailLogin({ email: 'test@test.com', code: '123' })
      expect(localStorage.getItem('token')).toBeNull()
    })
  })

  // ---------- error paths ----------
  describe('error paths', () => {
    it('register should throw on error', async () => {
      mockPost.mockRejectedValue(new Error('username taken'))
      await expect(UserService.register({ username: 'dup', email: 'd@d.com', code: '123', password: 'test123' })).rejects.toThrow('username taken')
    })

    it('getCurrentUser should throw on error', async () => {
      mockGet.mockRejectedValue(new Error('unauthorized'))
      await expect(UserService.getCurrentUser()).rejects.toThrow('unauthorized')
    })

    it('changePassword should throw on error', async () => {
      mockPut.mockRejectedValue(new Error('wrong password'))
      await expect(UserService.changePassword({ oldPassword: 'x', newPassword: 'y', confirmPassword: 'y' })).rejects.toThrow('wrong password')
    })

    it('updateProfile should throw on error', async () => {
      mockPut.mockRejectedValue(new Error('validation'))
      await expect(UserService.updateProfile({ nickname: '' })).rejects.toThrow('validation')
    })

    it('getUserStats should throw on error', async () => {
      mockGet.mockRejectedValue(new Error('server error'))
      await expect(UserService.getUserStats()).rejects.toThrow('server error')
    })

    it('checkin should throw on error', async () => {
      mockPost.mockRejectedValue(new Error('already checked in'))
      await expect(UserService.checkin()).rejects.toThrow('already checked in')
    })

    it('getCheckinStatus should throw on error', async () => {
      mockGet.mockRejectedValue(new Error('server error'))
      await expect(UserService.getCheckinStatus()).rejects.toThrow('server error')
    })

    it('getProfile should throw on error', async () => {
      mockGet.mockRejectedValue(new Error('not found'))
      await expect(UserService.getProfile()).rejects.toThrow('not found')
    })

    it('getAuthorProfile should throw on error', async () => {
      mockGet.mockRejectedValue(new Error('not found'))
      await expect(UserService.getAuthorProfile()).rejects.toThrow('not found')
    })

    it('sendForgotPasswordCode should throw on error', async () => {
      mockPost.mockRejectedValue(new Error('rate limited'))
      await expect(UserService.sendForgotPasswordCode('test@test.com')).rejects.toThrow('rate limited')
    })

    it('sendRegisterCode should throw on error', async () => {
      mockPost.mockRejectedValue(new Error('rate limited'))
      await expect(UserService.sendRegisterCode('test@test.com')).rejects.toThrow('rate limited')
    })

    it('resetPassword should throw on error', async () => {
      mockPost.mockRejectedValue(new Error('invalid code'))
      await expect(UserService.resetPassword({ email: 't@t.com', code: '000', newPassword: 'new' })).rejects.toThrow('invalid code')
    })

    it('sendEmailLoginCode should throw on error', async () => {
      mockPost.mockRejectedValue(new Error('rate limited'))
      await expect(UserService.sendEmailLoginCode('test@test.com')).rejects.toThrow('rate limited')
    })
  })

  // ---------- convenience exports ----------
  describe('convenience exports', () => {
    it('should export getUserStats, getProfile, getAuthorProfile', async () => {
      const { getUserStats, getProfile, getAuthorProfile } = await import('@/services/user')
      expect(typeof getUserStats).toBe('function')
      expect(typeof getProfile).toBe('function')
      expect(typeof getAuthorProfile).toBe('function')
    })

    it('should export sendForgotPasswordCode, sendRegisterCode, resetPassword, sendEmailLoginCode', async () => {
      const mod = await import('@/services/user')
      expect(typeof mod.sendForgotPasswordCode).toBe('function')
      expect(typeof mod.sendRegisterCode).toBe('function')
      expect(typeof mod.resetPassword).toBe('function')
      expect(typeof mod.sendEmailLoginCode).toBe('function')
    })
  })
})
