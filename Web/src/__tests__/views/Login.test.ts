import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import Login from '@/views/Login.vue'

// Mock dependencies
vi.mock('@/stores/user', () => ({
  useUserStore: vi.fn(() => ({
    isLoggedIn: false,
    userInfo: null,
    login: vi.fn(),
    register: vi.fn(),
    emailLogin: vi.fn(),
    isLoading: false
  }))
}))

vi.mock('@/composables/useErrorHandler', () => ({
  useErrorHandler: () => ({
    handleFormSubmit: vi.fn(async (fn: Function) => {
      try {
        return await fn()
      } catch {
        return null
      }
    }),
    showSuccess: vi.fn(),
    clearError: vi.fn(),
    showSuccessToast: vi.fn()
  })
}))

vi.mock('@/services/user', () => ({
  sendEmailLoginCode: vi.fn()
}))

vi.mock('@/components/Icon.vue', () => ({
  default: {
    template: '<span></span>',
    props: ['name', 'size']
  }
}))

describe('Login', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  const createWrapper = () => {
    return mount(Login, {
      global: {
        stubs: {
          'router-link': { template: '<a><slot /></a>' },
          'router-view': true
        }
      }
    })
  }

  describe('rendering', () => {
    it('should render the login page', () => {
      const wrapper = createWrapper()
      expect(wrapper.find('.login-page').exists()).toBe(true)
    })

    it('should have mode-tab buttons', () => {
      const wrapper = createWrapper()
      const tabs = wrapper.findAll('.mode-tab')
      expect(tabs.length).toBe(2)
      expect(tabs[0].text()).toContain('登录')
      expect(tabs[1].text()).toContain('注册')
    })
  })

  describe('registration validation', () => {
    it('should show error for empty password', async () => {
      const wrapper = createWrapper()

      // Switch to register mode
      const registerTab = wrapper.findAll('.mode-tab')[1]
      await registerTab.trigger('click')
      await wrapper.vm.$nextTick()

      // Fill in all required fields except password
      const inputs = wrapper.findAll('.login-form input')
      await inputs[0].setValue('testuser')  // username
      await inputs[1].setValue('test@example.com')  // email
      await inputs[2].setValue('123456')  // code
      // password left empty

      // Submit the form
      const form = wrapper.find('.login-form')
      await form.trigger('submit')
      await wrapper.vm.$nextTick()

      expect(wrapper.text()).toContain('请输入密码')
    })

    it('should show error for password less than 6 chars', async () => {
      const wrapper = createWrapper()

      const registerTab = wrapper.findAll('.mode-tab')[1]
      await registerTab.trigger('click')
      await wrapper.vm.$nextTick()

      const inputs = wrapper.findAll('.login-form input')
      await inputs[0].setValue('testuser')
      await inputs[1].setValue('test@example.com')
      await inputs[2].setValue('123456')
      await inputs[3].setValue('123')  // password < 6

      const form = wrapper.find('.login-form')
      await form.trigger('submit')
      await wrapper.vm.$nextTick()

      expect(wrapper.text()).toContain('密码至少6位')
    })

    it('should show error for empty username', async () => {
      const wrapper = createWrapper()

      const registerTab = wrapper.findAll('.mode-tab')[1]
      await registerTab.trigger('click')
      await wrapper.vm.$nextTick()

      const inputs = wrapper.findAll('.login-form input')
      // username left empty
      await inputs[1].setValue('test@example.com')
      await inputs[2].setValue('123456')
      await inputs[3].setValue('123456')

      const form = wrapper.find('.login-form')
      await form.trigger('submit')
      await wrapper.vm.$nextTick()

      expect(wrapper.text()).toContain('请输入用户名')
    })

    it('should show error for invalid email', async () => {
      const wrapper = createWrapper()

      const registerTab = wrapper.findAll('.mode-tab')[1]
      await registerTab.trigger('click')
      await wrapper.vm.$nextTick()

      const inputs = wrapper.findAll('.login-form input')
      await inputs[0].setValue('testuser')
      await inputs[1].setValue('notanemail')  // invalid email
      await inputs[2].setValue('123456')
      await inputs[3].setValue('123456')

      const form = wrapper.find('.login-form')
      await form.trigger('submit')
      await wrapper.vm.$nextTick()

      expect(wrapper.text()).toContain('请输入有效的邮箱地址')
    })
  })

  describe('login validation', () => {
    it('should show error for empty username', async () => {
      const wrapper = createWrapper()

      // Default mode is login with password
      const inputs = wrapper.findAll('.login-form input')
      await inputs[0].setValue('')  // empty username
      await inputs[1].setValue('password123')

      const form = wrapper.find('.login-form')
      await form.trigger('submit')
      await wrapper.vm.$nextTick()

      expect(wrapper.text()).toContain('请输入用户名')
    })

    it('should show error for empty password', async () => {
      const wrapper = createWrapper()

      const inputs = wrapper.findAll('.login-form input')
      await inputs[0].setValue('testuser')
      await inputs[1].setValue('')  // empty password

      const form = wrapper.find('.login-form')
      await form.trigger('submit')
      await wrapper.vm.$nextTick()

      expect(wrapper.text()).toContain('请输入密码')
    })
  })

  describe('email login validation', () => {
    it('should show error for invalid email', async () => {
      const wrapper = createWrapper()

      // Switch to email login mode
      const emailTab = wrapper.findAll('.mode-tab-sm')[1]
      await emailTab.trigger('click')
      await wrapper.vm.$nextTick()

      // Fill in the email login form
      const emailForm = wrapper.findAll('.login-form')[1]
      const emailInput = emailForm.find('input[type="email"]')
      await emailInput.setValue('notanemail')

      await emailForm.trigger('submit')
      await wrapper.vm.$nextTick()

      expect(wrapper.text()).toContain('请输入有效的邮箱地址')
    })

    it('should show error for empty code', async () => {
      const wrapper = createWrapper()

      const emailTab = wrapper.findAll('.mode-tab-sm')[1]
      await emailTab.trigger('click')
      await wrapper.vm.$nextTick()

      const emailForm = wrapper.findAll('.login-form')[1]
      const inputs = emailForm.findAll('input')
      await inputs[0].setValue('test@example.com')  // valid email
      await inputs[1].setValue('')  // empty code

      await emailForm.trigger('submit')
      await wrapper.vm.$nextTick()

      expect(wrapper.text()).toContain('请输入验证码')
    })
  })
})
