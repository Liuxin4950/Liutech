import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import ForgotPassword from '@/views/ForgotPassword.vue'

// Mock dependencies
vi.mock('vue-router', () => ({
  useRouter: vi.fn(() => ({
    push: vi.fn()
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
    showSuccessToast: vi.fn()
  })
}))

vi.mock('@/services/user', () => ({
  sendForgotPasswordCode: vi.fn(),
  resetPassword: vi.fn()
}))

vi.mock('@/components/Icon.vue', () => ({
  default: {
    template: '<span></span>',
    props: ['name', 'size']
  }
}))

describe('ForgotPassword', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  const createWrapper = () => {
    return mount(ForgotPassword, {
      global: {
        stubs: {
          'router-link': { template: '<a><slot /></a>' },
          'router-view': true
        }
      }
    })
  }

  /** Helper: advance component to step 2 and return the wrapper */
  const createStep2Wrapper = async () => {
    const wrapper = createWrapper()
    await wrapper.setData({ step: 2 })
    await wrapper.vm.$nextTick()
    return wrapper
  }

  describe('rendering', () => {
    it('should render the forgot password page', () => {
      const wrapper = createWrapper()
      expect(wrapper.find('.login-page').exists()).toBe(true)
    })

    it('should show step 1 by default', () => {
      const wrapper = createWrapper()
      expect(wrapper.find('.login-form').exists()).toBe(true)
      expect(wrapper.text()).toContain('重置密码')
    })
  })

  describe('Step 1 - email validation', () => {
    it('should show error for empty email', async () => {
      const wrapper = createWrapper()

      const form = wrapper.find('.login-form')
      await form.trigger('submit')
      await wrapper.vm.$nextTick()

      expect(wrapper.text()).toContain('请输入邮箱地址')
    })

    it('should show error for invalid email format', async () => {
      const wrapper = createWrapper()

      const input = wrapper.find('.login-form input[type="email"]')
      await input.setValue('notanemail')

      const form = wrapper.find('.login-form')
      await form.trigger('submit')
      await wrapper.vm.$nextTick()

      expect(wrapper.text()).toContain('请输入有效的邮箱地址')
    })
  })

  describe('Step 2 - password validation', () => {
    it('should show error for empty code', async () => {
      const wrapper = await createStep2Wrapper()

      const form = wrapper.find('.login-form')
      const inputs = form.findAll('input')
      // inputs[0] is the disabled email, inputs[1] is code, inputs[2] is newPassword, inputs[3] is confirmPassword
      // code (inputs[1]) left empty
      await inputs[2].setValue('Password1')
      await inputs[3].setValue('Password1')

      await form.trigger('submit')
      await wrapper.vm.$nextTick()

      expect(wrapper.text()).toContain('请输入验证码')
    })

    it('should show error for password less than 6 chars', async () => {
      const wrapper = await createStep2Wrapper()

      const form = wrapper.find('.login-form')
      const inputs = form.findAll('input')
      await inputs[1].setValue('123456')  // code
      await inputs[2].setValue('Ab1')     // password < 6
      await inputs[3].setValue('Ab1')

      await form.trigger('submit')
      await wrapper.vm.$nextTick()

      expect(wrapper.text()).toContain('密码至少6位')
    })

    it('should show error for password mismatch', async () => {
      const wrapper = await createStep2Wrapper()

      const form = wrapper.find('.login-form')
      const inputs = form.findAll('input')
      await inputs[1].setValue('123456')
      await inputs[2].setValue('Password1')
      await inputs[3].setValue('Different1')  // mismatch

      await form.trigger('submit')
      await wrapper.vm.$nextTick()

      expect(wrapper.text()).toContain('两次输入的密码不一致')
    })
  })
})
