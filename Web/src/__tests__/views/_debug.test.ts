import { describe, it, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import ForgotPassword from '@/views/ForgotPassword.vue'

vi.mock('vue-router', () => ({
  useRouter: vi.fn(() => ({ push: vi.fn() }))
}))
vi.mock('@/composables/useErrorHandler', () => ({
  useErrorHandler: () => ({
    handleFormSubmit: vi.fn(async (fn: Function) => { try { return await fn() } catch { return null } }),
    showSuccessToast: vi.fn()
  })
}))
vi.mock('@/services/user', () => ({
  sendForgotPasswordCode: vi.fn(),
  resetPassword: vi.fn()
}))
vi.mock('@/components/Icon.vue', () => ({
  default: { template: '<span></span>', props: ['name', 'size'] }
}))

describe('debug', () => {
  beforeEach(() => { setActivePinia(createPinia()); vi.clearAllMocks() })

  it('check setData vs vm access', async () => {
    const wrapper = mount(ForgotPassword, {
      global: { stubs: { 'router-link': { template: '<a><slot /></a>' }, 'router-view': true } }
    })
    console.log('Initial step via vm:', wrapper.vm.step)

    await wrapper.setData({ step: 2 })
    await wrapper.vm.$nextTick()
    console.log('After setData step:', wrapper.vm.step)

    // Check form visibility
    const forms = wrapper.findAll('.login-form')
    console.log('Number of forms:', forms.length)
    forms.forEach((f, i) => console.log(`Form ${i} html (first 300):`, f.html().substring(0, 300)))

    // Check if step 1 form still exists
    const step1Form = wrapper.find('.login-form')
    if (step1Form) {
      console.log('First form action:', step1Form.attributes('class'))
      const inputs = step1Form.findAll('input')
      console.log('First form input count:', inputs.length)
    }
  })
})
