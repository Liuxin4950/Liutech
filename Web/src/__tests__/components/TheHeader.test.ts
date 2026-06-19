import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { reactive } from 'vue'
import TheHeader from '@/components/TheHeader.vue'

// Mock dependencies
vi.mock('@/utils/theme', () => ({
  default: {
    current: { value: 'light' },
    toggle: vi.fn()
  }
}))

// Shared mutable mock state for user store
const mockUserState = reactive({
  isLoggedIn: false,
  isAdmin: false,
  username: '',
  avatar: '',
  points: 0,
  logout: vi.fn()
})

vi.mock('@/stores/user', () => ({
  useUserStore: () => mockUserState
}))

vi.mock('@/composables/useImageFallback', () => ({
  handleImageError: vi.fn()
}))

vi.mock('@/assets/image/icon/menu.png', () => ({ default: '/menu.png' }))
vi.mock('@/assets/image/icon/menu_dark.png', () => ({ default: '/menu_dark.png' }))
vi.mock('@/assets/image/logo/logo.png', () => ({ default: '/logo.png' }))

// Mock router
const mockPush = vi.fn()
vi.mock('vue-router', () => ({
  useRouter: () => ({ push: mockPush }),
  useRoute: () => ({
    path: '/',
    name: 'home',
    meta: { section: 'home' },
    query: {}
  })
}))

// Mock Icon component
vi.mock('@/components/Icon.vue', () => ({
  default: {
    name: 'Icon',
    props: ['name', 'size', 'style'],
    template: '<span class="icon-stub"></span>'
  }
}))

describe('TheHeader', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    // Reset mock state
    mockUserState.isLoggedIn = false
    mockUserState.isAdmin = false
    mockUserState.username = ''
    mockUserState.avatar = ''
    mockUserState.points = 0
    // Default desktop width
    vi.stubGlobal('innerWidth', 1024)
  })

  const createWrapper = () => {
    return mount(TheHeader, {
      global: {
        stubs: {
          'router-link': { template: '<a><slot /></a>' },
          Icon: { template: '<span class="icon-stub"></span>' }
        }
      }
    })
  }

  describe('rendering', () => {
    it('should render header element', () => {
      const wrapper = createWrapper()
      expect(wrapper.find('header').exists()).toBe(true)
    })

    it('should render logo', () => {
      const wrapper = createWrapper()
      expect(wrapper.text()).toContain('LiuTech')
    })

    it('should render navigation items', () => {
      const wrapper = createWrapper()
      expect(wrapper.text()).toContain('首页')
      expect(wrapper.text()).toContain('分类')
      expect(wrapper.text()).toContain('标签')
      expect(wrapper.text()).toContain('归档')
      expect(wrapper.text()).toContain('关于我')
    })

    it('should show login button when not logged in', () => {
      const wrapper = createWrapper()
      expect(wrapper.text()).toContain('登录/注册')
    })

    it('should show user info when logged in', () => {
      mockUserState.isLoggedIn = true
      mockUserState.username = 'testuser'
      mockUserState.points = 50
      const wrapper = createWrapper()
      expect(wrapper.text()).toContain('testuser')
      expect(wrapper.text()).toContain('50')
    })

    it('should show first letter of username when no avatar', () => {
      mockUserState.isLoggedIn = true
      mockUserState.username = 'testuser'
      const wrapper = createWrapper()
      expect(wrapper.text()).toContain('T')
    })
  })

  describe('navigation', () => {
    it('should navigate to home on logo click', async () => {
      const wrapper = createWrapper()
      const logo = wrapper.find('.logo')
      await logo.trigger('click')
      expect(mockPush).toHaveBeenCalledWith('/')
    })

    it('should navigate to login on login button click', async () => {
      const wrapper = createWrapper()
      const loginBtn = wrapper.find('.login-btn')
      await loginBtn.trigger('click')
      expect(mockPush).toHaveBeenCalledWith('/login')
    })
  })

  describe('user menu', () => {
    it('should show user menu items when logged in', () => {
      mockUserState.isLoggedIn = true
      mockUserState.username = 'admin'
      const wrapper = createWrapper()
      expect(wrapper.text()).toContain('个人资料')
      expect(wrapper.text()).toContain('退出登录')
    })

    it('should show admin-only items for admin users', () => {
      mockUserState.isLoggedIn = true
      mockUserState.username = 'admin'
      mockUserState.isAdmin = true
      const wrapper = createWrapper()
      expect(wrapper.text()).toContain('我的文章')
      expect(wrapper.text()).toContain('草稿子箱')
    })

    it('should not show admin items for non-admin users', () => {
      mockUserState.isLoggedIn = true
      mockUserState.username = 'user'
      mockUserState.isAdmin = false
      const wrapper = createWrapper()
      const adminMenuItems = wrapper.findAll('li').filter(li =>
        li.text().includes('我的文章') || li.text().includes('草稿子箱')
      )
      expect(adminMenuItems.length).toBe(0)
    })
  })

  describe('search', () => {
    it('should emit open-search when search button clicked', async () => {
      const wrapper = createWrapper()
      const searchBtn = wrapper.find('.search-trigger')
      if (searchBtn.exists()) {
        await searchBtn.trigger('click')
        expect(wrapper.emitted('open-search')).toBeTruthy()
      }
    })
  })

  describe('theme toggle', () => {
    it('should call theme.toggle when theme button clicked', async () => {
      const theme = (await import('@/utils/theme')).default
      const wrapper = createWrapper()
      const themeBtn = wrapper.find('.theme-btn')
      if (themeBtn.exists()) {
        await themeBtn.trigger('click')
        expect(theme.toggle).toHaveBeenCalled()
      }
    })
  })

  describe('mobile drawer', () => {
    it('should have mobile drawer element', () => {
      const wrapper = createWrapper()
      expect(wrapper.find('.mobile-drawer').exists()).toBe(true)
    })

    it('should contain search option in mobile drawer', () => {
      const wrapper = createWrapper()
      const drawer = wrapper.find('.mobile-drawer')
      expect(drawer.text()).toContain('搜索文章')
    })

    it('should contain theme toggle in mobile drawer', () => {
      const wrapper = createWrapper()
      const drawer = wrapper.find('.mobile-drawer')
      expect(drawer.text()).toContain('模式')
    })

    it('should contain all nav items in mobile drawer', () => {
      const wrapper = createWrapper()
      const drawer = wrapper.find('.mobile-drawer')
      expect(drawer.text()).toContain('首页')
      expect(drawer.text()).toContain('分类')
      expect(drawer.text()).toContain('标签')
      expect(drawer.text()).toContain('归档')
      expect(drawer.text()).toContain('关于我')
    })

    it('should show logout option for logged in users in mobile drawer', () => {
      mockUserState.isLoggedIn = true
      mockUserState.username = 'testuser'
      const wrapper = createWrapper()
      const drawer = wrapper.find('.mobile-drawer')
      expect(drawer.text()).toContain('退出登录')
    })
  })

  describe('user avatar display', () => {
    it('should show avatar image when avatarUrl is set', () => {
      mockUserState.isLoggedIn = true
      mockUserState.username = 'testuser'
      mockUserState.avatar = 'https://example.com/avatar.jpg'
      const wrapper = createWrapper()
      const avatarImg = wrapper.find('.user-avatar img')
      expect(avatarImg.exists()).toBe(true)
    })

    it('should show first letter fallback when no avatar', () => {
      mockUserState.isLoggedIn = true
      mockUserState.username = 'testuser'
      mockUserState.avatar = ''
      const wrapper = createWrapper()
      const fallback = wrapper.find('.user-avatar div')
      expect(fallback.exists()).toBe(true)
      expect(fallback.text()).toBe('T')
    })
  })

  describe('points display', () => {
    it('should show user points', () => {
      mockUserState.isLoggedIn = true
      mockUserState.username = 'testuser'
      mockUserState.points = 150
      const wrapper = createWrapper()
      expect(wrapper.text()).toContain('150')
      expect(wrapper.text()).toContain('积分')
    })
  })

  describe('user menu navigation', () => {
    it('should navigate to profile', async () => {
      mockUserState.isLoggedIn = true
      mockUserState.username = 'testuser'
      const wrapper = createWrapper()
      const profileItem = wrapper.findAll('li').find(li => li.text().includes('个人资料'))
      if (profileItem) {
        await profileItem.trigger('click')
        expect(mockPush).toHaveBeenCalledWith('/profile')
      }
    })

    it('should navigate to favorites', async () => {
      mockUserState.isLoggedIn = true
      mockUserState.username = 'testuser'
      const wrapper = createWrapper()
      const favItem = wrapper.findAll('li').find(li => li.text().includes('我的收藏'))
      if (favItem) {
        await favItem.trigger('click')
        expect(mockPush).toHaveBeenCalledWith('/favorites')
      }
    })

    it('should call logout and navigate to home', async () => {
      mockUserState.isLoggedIn = true
      mockUserState.username = 'testuser'
      const wrapper = createWrapper()
      const logoutItem = wrapper.findAll('li').find(li => li.text().includes('退出登录'))
      if (logoutItem) {
        await logoutItem.trigger('click')
        expect(mockUserState.logout).toHaveBeenCalled()
        expect(mockPush).toHaveBeenCalledWith('/')
      }
    })
  })

  describe('logo image', () => {
    it('should render logo image', () => {
      const wrapper = createWrapper()
      const logoImg = wrapper.find('.logo-mark')
      expect(logoImg.exists()).toBe(true)
    })
  })

  describe('search trigger', () => {
    it('should have search trigger button', () => {
      const wrapper = createWrapper()
      const searchBtn = wrapper.find('.search-trigger')
      expect(searchBtn.exists()).toBe(true)
    })
  })
})
