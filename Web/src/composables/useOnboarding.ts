/**
 * 新用户引导 composable
 * 管理首次访问引导的状态、持久化和生命周期
 * 流程：welcome → spotlight → model-tip → chat-tip → done
 */
import { ref, computed } from 'vue'

const STORAGE_VERSION_KEY = 'liutech_onboarding_version'
const STORAGE_STEP_KEY = 'liutech_onboarding_step'

// 当前引导版本，修改此值可重新对所有用户触发引导
const CURRENT_VERSION = '3'

// 引导阶段定义
export type OnboardingStep = 'idle' | 'welcome' | 'spotlight' | 'model-tip' | 'chat-tip' | 'done'

const step = ref<OnboardingStep>('idle')
const isActive = ref(false)

function getVersion(): string {
  try { return localStorage.getItem(STORAGE_VERSION_KEY) || '' } catch { return '' }
}

function getStep(): string {
  try { return localStorage.getItem(STORAGE_STEP_KEY) || '' } catch { return '' }
}

function persist(s: OnboardingStep) {
  try {
    localStorage.setItem(STORAGE_VERSION_KEY, CURRENT_VERSION)
    localStorage.setItem(STORAGE_STEP_KEY, s)
  } catch { /* localStorage 不可用时静默处理 */ }
}

/**
 * 初始化引导状态
 * 在 MainLayout onMounted 中调用
 */
function initOnboarding() {
  const savedVersion = getVersion()
  const savedStep = getStep()

  // 版本不匹配 → 从 welcome 开始
  if (savedVersion !== CURRENT_VERSION) {
    step.value = 'welcome'
    isActive.value = true
    persist('welcome')
    return
  }

  // 已完成
  if (savedStep === 'done') {
    step.value = 'done'
    isActive.value = false
    return
  }

  // 中间状态刷新 → 视为已完成（上下文丢失）
  if (savedStep === 'spotlight' || savedStep === 'model-tip' || savedStep === 'chat-tip') {
    finish()
    return
  }

  // 默认从 welcome 开始
  step.value = 'welcome'
  isActive.value = true
  persist('welcome')
}

/**
 * 用户同意引导 → 进入 spotlight
 */
function accept() {
  step.value = 'spotlight'
  isActive.value = true
  persist('spotlight')
}

/**
 * 进入下一阶段
 */
function nextStep() {
  if (step.value === 'spotlight') {
    step.value = 'model-tip'
    isActive.value = true
    persist('model-tip')
  } else if (step.value === 'model-tip') {
    step.value = 'chat-tip'
    isActive.value = true
    persist('chat-tip')
  } else {
    finish()
  }
}

/**
 * 跳过整个引导
 */
function skip() {
  finish()
}

/**
 * 完成引导
 */
function finish() {
  step.value = 'done'
  isActive.value = false
  persist('done')
}

/**
 * 重置引导（调试用）
 */
function resetOnboarding() {
  try {
    localStorage.removeItem(STORAGE_VERSION_KEY)
    localStorage.removeItem(STORAGE_STEP_KEY)
  } catch { /* ignore */ }
  step.value = 'idle'
  isActive.value = false
}

export function useOnboarding() {
  const isWelcomeActive = computed(() => isActive.value && step.value === 'welcome')
  const isSpotlightActive = computed(() => isActive.value && step.value === 'spotlight')
  const isModelTipActive = computed(() => isActive.value && step.value === 'model-tip')
  const isChatTipActive = computed(() => isActive.value && step.value === 'chat-tip')

  return {
    step,
    isActive,
    isWelcomeActive,
    isSpotlightActive,
    isModelTipActive,
    isChatTipActive,
    initOnboarding,
    accept,
    nextStep,
    skip,
    finish,
    resetOnboarding,
  }
}
