import { describe, it, expect, vi, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useUserStore } from '@/stores/user'
import { UserService } from '@/services/user'

// Mock UserService
vi.mock('@/services/user', () => ({
  UserService: {
    login: vi.fn(),
    register: vi.fn(),
    getCurrentUser: vi.fn(),
    logout: vi.fn(),
    isLoggedIn: vi.fn(),
    verifyEmailLogin: vi.fn()
  }
}))

// Mock errorHandler
vi.mock('@/utils/errorHandler', () => ({
  showErrorToast: vi.fn()
}))

const mockUserService = vi.mocked(UserService)

describe('useUserStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    localStorage.clear()
  })

  describe('initial state', () => {
    it('should have null userInfo and false isLoading', () => {
      const store = useUserStore()
      expect(store.userInfo).toBeNull()
      expect(store.isLoading).toBe(false)
    })
  })

  describe('computed properties', () => {
    it('isLoggedIn should be false when no user info', () => {
      const store = useUserStore()
      expect(store.isLoggedIn).toBe(false)
    })

    it('isLoggedIn should be true when user info exists and token exists', () => {
      mockUserService.isLoggedIn.mockReturnValue(true)
      const store = useUserStore()
      store.userInfo = { username: 'test', email: 't@t.com', points: 10 }
      expect(store.isLoggedIn).toBe(true)
    })

    it('username should return empty string when no user', () => {
      const store = useUserStore()
      expect(store.username).toBe('')
    })

    it('username should return user username', () => {
      const store = useUserStore()
      store.userInfo = { username: 'testuser', email: 't@t.com', points: 0 }
      expect(store.username).toBe('testuser')
    })

    it('avatar should return empty string when no user', () => {
      const store = useUserStore()
      expect(store.avatar).toBe('')
    })

    it('avatar should return avatarUrl', () => {
      const store = useUserStore()
      store.userInfo = { username: 'test', email: 't@t.com', points: 0, avatarUrl: '/img.png' }
      expect(store.avatar).toBe('/img.png')
    })

    it('points should return 0 when no user', () => {
      const store = useUserStore()
      expect(store.points).toBe(0)
    })

    it('points should return user points', () => {
      const store = useUserStore()
      store.userInfo = { username: 'test', email: 't@t.com', points: 500 }
      expect(store.points).toBe(500)
    })

    it('isAdmin should be true when role is admin', () => {
      const store = useUserStore()
      store.userInfo = { username: 'admin', email: 'a@a.com', points: 0, role: 'admin' }
      expect(store.isAdmin).toBe(true)
    })

    it('isAdmin should be false when role is user', () => {
      const store = useUserStore()
      store.userInfo = { username: 'user', email: 'u@u.com', points: 0, role: 'user' }
      expect(store.isAdmin).toBe(false)
    })

    it('isAdmin should be case insensitive', () => {
      const store = useUserStore()
      store.userInfo = { username: 'admin', email: 'a@a.com', points: 0, role: 'ADMIN' }
      expect(store.isAdmin).toBe(true)
    })
  })

  describe('login', () => {
    it('should call UserService.login and fetchUserInfo on success', async () => {
      mockUserService.login.mockResolvedValue({ token: 'tok' })
      mockUserService.getCurrentUser.mockResolvedValue({
        id: 1, username: 'test', email: 't@t.com', points: 10
      })

      const store = useUserStore()
      const result = await store.login('test', 'pass')

      expect(mockUserService.login).toHaveBeenCalledWith({ username: 'test', password: 'pass' })
      expect(result).toBe(true)
      expect(store.userInfo?.username).toBe('test')
    })

    it('should throw on login failure', async () => {
      mockUserService.login.mockRejectedValue(new Error('invalid'))

      const store = useUserStore()
      await expect(store.login('bad', 'creds')).rejects.toThrow('invalid')
    })

    it('should set isLoading during login', async () => {
      let resolveLogin: (v: any) => void
      mockUserService.login.mockImplementation(() => new Promise(r => { resolveLogin = r }))

      const store = useUserStore()
      const promise = store.login('test', 'pass')

      expect(store.isLoading).toBe(true)

      resolveLogin!({ token: 'tok' })
      mockUserService.getCurrentUser.mockResolvedValue({ id: 1, username: 'test', email: 't@t.com', points: 0 })
      await promise

      expect(store.isLoading).toBe(false)
    })
  })

  describe('register', () => {
    it('should call UserService.register and set userInfo', async () => {
      const userData = { username: 'new', email: 'new@test.com', code: '123' }
      const newUser = { id: 2, username: 'new', email: 'new@test.com', points: 0 }
      mockUserService.register.mockResolvedValue(newUser)

      const store = useUserStore()
      const result = await store.register(userData)

      expect(mockUserService.register).toHaveBeenCalledWith(userData)
      expect(store.userInfo).toEqual(newUser)
      expect(result).toBe(true)
    })
  })

  describe('logout', () => {
    it('should clear userInfo and call UserService.logout', () => {
      const store = useUserStore()
      store.userInfo = { username: 'test', email: 't@t.com', points: 10 }
      store.logout()

      expect(mockUserService.logout).toHaveBeenCalled()
      expect(store.userInfo).toBeNull()
    })
  })

  describe('emailLogin', () => {
    it('should call verifyEmailLogin and fetchUserInfo', async () => {
      mockUserService.verifyEmailLogin.mockResolvedValue({ token: 'email-tok' })
      mockUserService.getCurrentUser.mockResolvedValue({
        id: 1, username: 'test', email: 't@t.com', points: 10
      })

      const store = useUserStore()
      const result = await store.emailLogin('test@test.com', '123')

      expect(mockUserService.verifyEmailLogin).toHaveBeenCalledWith({ email: 'test@test.com', code: '123' })
      expect(result).toBe(true)
    })
  })

  describe('fetchUserInfo', () => {
    it('should set userInfo to null when not logged in', async () => {
      mockUserService.isLoggedIn.mockReturnValue(false)

      const store = useUserStore()
      await store.fetchUserInfo()

      expect(store.userInfo).toBeNull()
    })

    it('should fetch user info when logged in', async () => {
      mockUserService.isLoggedIn.mockReturnValue(true)
      const user = { id: 1, username: 'test', email: 't@t.com', points: 50 }
      mockUserService.getCurrentUser.mockResolvedValue(user)

      const store = useUserStore()
      await store.fetchUserInfo()

      expect(store.userInfo).toEqual(user)
    })

    it('should use cache and skip fetch when data is fresh', async () => {
      mockUserService.isLoggedIn.mockReturnValue(true)
      const user = { id: 1, username: 'test', email: 't@t.com', points: 50 }
      mockUserService.getCurrentUser.mockResolvedValue(user)

      const store = useUserStore()
      await store.fetchUserInfo()
      await store.fetchUserInfo() // second call should use cache

      expect(mockUserService.getCurrentUser).toHaveBeenCalledTimes(1)
    })

    it('should force refresh when forceRefresh is true', async () => {
      mockUserService.isLoggedIn.mockReturnValue(true)
      const user = { id: 1, username: 'test', email: 't@t.com', points: 50 }
      mockUserService.getCurrentUser.mockResolvedValue(user)

      const store = useUserStore()
      await store.fetchUserInfo()
      await store.fetchUserInfo(true)

      expect(mockUserService.getCurrentUser).toHaveBeenCalledTimes(2)
    })
  })

  describe('initUserState', () => {
    it('should fetch user info when logged in', async () => {
      mockUserService.isLoggedIn.mockReturnValue(true)
      mockUserService.getCurrentUser.mockResolvedValue({
        id: 1, username: 'test', email: 't@t.com', points: 0
      })

      const store = useUserStore()
      await store.initUserState()

      expect(mockUserService.getCurrentUser).toHaveBeenCalled()
    })

    it('should not fetch when not logged in', async () => {
      mockUserService.isLoggedIn.mockReturnValue(false)

      const store = useUserStore()
      await store.initUserState()

      expect(mockUserService.getCurrentUser).not.toHaveBeenCalled()
    })
  })

  describe('updateUserInfo', () => {
    it('should merge partial user info', () => {
      const store = useUserStore()
      store.userInfo = { username: 'test', email: 't@t.com', points: 10 }
      store.updateUserInfo({ nickname: 'NewNick', points: 20 })

      expect(store.userInfo?.nickname).toBe('NewNick')
      expect(store.userInfo?.points).toBe(20)
      expect(store.userInfo?.username).toBe('test')
    })

    it('should do nothing when userInfo is null', () => {
      const store = useUserStore()
      store.updateUserInfo({ nickname: 'test' })
      expect(store.userInfo).toBeNull()
    })
  })
})
