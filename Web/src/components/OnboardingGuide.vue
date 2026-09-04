<template>
  <Teleport to="body">
    <!-- 欢迎对话框 -->
    <Transition name="welcome-fade" appear>
      <div v-if="isWelcomeActive" class="welcome">
        <div class="welcome__backdrop" />
        <div class="welcome__card">
          <div class="welcome__avatar">
            <img src="/洛天依.png" alt="小鑫同学" />
          </div>
          <h2 class="welcome__title">欢迎来到我的博客</h2>
          <p class="welcome__subtitle">我是小鑫同学，用一分钟认识一下这里的 AI 助手吗？</p>
          <div class="welcome__actions">
            <button class="welcome__btn welcome__btn--ghost" @click="handleWelcomeDecline">
              不用了，我自己看看
            </button>
            <button class="welcome__btn welcome__btn--primary" @click="handleWelcomeAccept">
              好的，带我逛逛
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><path d="M5 12h14M12 5l7 7-7 7"/></svg>
            </button>
          </div>
        </div>
      </div>
    </Transition>
    
    <!-- 第一阶段：遮罩高亮引导 -->
    <Transition name="onboarding-fade" appear>
      <div
        v-if="isSpotlightActive && spotlightReady"
        class="onboarding"
        role="dialog"
        aria-label="新用户引导"
      >
        <!-- 遮罩 + 高亮切割 (SVG mask 实现圆角镂空) -->
        <svg class="onboarding__mask" width="100%" height="100%">
          <defs>
            <mask id="onboarding-mask">
              <rect width="100%" height="100%" fill="white" />
              <rect
                :x="spotlightRect.x"
                :y="spotlightRect.y"
                :width="spotlightRect.w"
                :height="spotlightRect.h"
                :rx="spotlightRect.r"
                fill="black"
              />
            </mask>
          </defs>
          <rect
            width="100%"
            height="100%"
            fill="rgba(0,0,0,0.55)"
            mask="url(#onboarding-mask)"
            @click="handleDismiss"
            class="onboarding__overlay-rect"
          />
        </svg>

        <!-- 高亮边框 + 呼吸光晕 -->
        <div class="onboarding__ring" :style="ringStyle">
          <span class="onboarding__glow" />
        </div>

        <!-- 高亮区域可点击 -->
        <div
          class="onboarding__hitarea"
          :style="hitareaStyle"
          @click.stop="handleSpotlightClick"
        />

        <!-- 引导气泡 -->
        <Transition name="tooltip-pop" appear>
          <div v-if="tooltipVisible" class="tooltip" :style="tooltipPos">
            <div class="tooltip__card">
              <div class="tooltip__step">1 / 3</div>
              <p class="tooltip__msg">
                右下角是我的 <strong>AI 助手</strong>，<br />点击它可以和我聊天哦
              </p>
              <div class="tooltip__footer">
                <button class="tooltip__btn tooltip__btn--ghost" @click="handleDismiss">
                  跳过
                </button>
                <button class="tooltip__btn tooltip__btn--primary" @click="handleSpotlightClick">
                  知道了
                  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
                    <path d="M5 12h14M12 5l7 7-7 7"/>
                  </svg>
                </button>
              </div>
            </div>
            <!-- 气泡箭头 -->
            <div class="tooltip__arrow" :class="`tooltip__arrow--${arrowDir}`" :style="arrowStyle" />
          </div>
        </Transition>
      </div>
    </Transition>

    <!-- 第二阶段：等待 Live2D 真正可交互后，再引导用户点击模型 -->
    <Transition name="chat-tip-pop" appear>
      <div
        v-if="isModelTipActive && modelTipReady"
        class="chat-tip"
        :style="modelTipPos"
      >
        <div class="chat-tip__card">
          <span class="chat-tip__step">2 / 3</span>
          <span v-if="live2dStatus === 'loading'" class="chat-tip__loading-dot" aria-hidden="true" />
          <p class="chat-tip__msg">{{ modelTipMessage }}</p>
          <button class="chat-tip__close" @click="handleDismiss" aria-label="跳过引导">
            <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round"><path d="M18 6L6 18M6 6l12 12"/></svg>
          </button>
        </div>
        <div class="chat-tip__arrow" />
      </div>
    </Transition>

    <!-- 第三阶段：聊天框已打开，指出真正的输入入口 -->
    <Transition name="tooltip-pop" appear>
      <div
        v-if="isChatTipActive && chatInputReady"
        class="input-tip"
        :style="chatInputTipPos"
      >
        <div class="tooltip__card">
          <div class="tooltip__step">3 / 3</div>
          <p class="tooltip__msg">
            在这里输入问题，也可以选择<strong>快捷问题或语音输入</strong>。
          </p>
          <div class="tooltip__footer">
            <button class="tooltip__btn tooltip__btn--primary" @click="handleChatTipDismiss">
              开始聊天
            </button>
          </div>
        </div>
        <div class="tooltip__arrow tooltip__arrow--top" :style="chatInputArrowStyle" />
      </div>
    </Transition>
  </Teleport>
</template>

<script setup lang="ts">
/**
 * 新用户引导组件
 * 三阶段：高亮 AI 按钮 → 等待并点击 Live2D → 定位聊天输入框
 * 使用 SVG mask 实现圆角镂空高亮
 */
import { ref, computed, watch, onMounted, onUnmounted, nextTick } from 'vue'
import { useOnboarding } from '@/composables/useOnboarding'

const props = withDefaults(defineProps<{
  live2dStatus?: 'idle' | 'loading' | 'ready' | 'error'
  chatOpen?: boolean
}>(), {
  live2dStatus: 'idle',
  chatOpen: false,
})

const emit = defineEmits<{
  'spotlight-click': []
  'complete': []
}>()

const {
  isWelcomeActive,
  isSpotlightActive,
  isModelTipActive,
  isChatTipActive,
  accept,
  nextStep,
  skip,
  finish,
} = useOnboarding()

// ---- 类型 ----
interface Rect { top: number; left: number; width: number; height: number }

// ---- 状态 ----
const targetRect = ref<Rect | null>(null)
const spotlightReady = ref(false)
const tooltipVisible = ref(false)
const modelTipReady = ref(false)
const chatInputReady = ref(false)

const PAD = 12   // 高亮内边距
const RADIUS = 14 // 高亮圆角

// ---- SVG mask 高亮坐标 ----
const spotlightRect = computed(() => {
  const r = targetRect.value
  if (!r) return { x: 0, y: 0, w: 0, h: 0, r: RADIUS }
  return {
    x: r.left - PAD,
    y: r.top - PAD,
    w: r.width + PAD * 2,
    h: r.height + PAD * 2,
    r: RADIUS,
  }
})

// 呼吸光环定位
const ringStyle = computed(() => {
  const s = spotlightRect.value
  if (!s.w) return { display: 'none' }
  return {
    top: `${s.y - 3}px`,
    left: `${s.x - 3}px`,
    width: `${s.w + 6}px`,
    height: `${s.h + 6}px`,
    borderRadius: `${RADIUS + 3}px`,
  }
})

// 可点击区域
const hitareaStyle = computed(() => {
  const s = spotlightRect.value
  if (!s.w) return { display: 'none' }
  return {
    top: `${s.y}px`,
    left: `${s.x}px`,
    width: `${s.w}px`,
    height: `${s.h}px`,
    borderRadius: `${RADIUS}px`,
  }
})

// ---- 气泡定位 ----
const TOOLTIP_W = 310
const GAP = 14
const arrowDir = ref<'top' | 'bottom'>('top')
const tooltipPos = ref<Record<string, string>>({})
const arrowStyle = ref<Record<string, string>>({})

function placeTooltip() {
  const r = targetRect.value
  if (!r) return

  const vw = window.innerWidth
  const vh = window.innerHeight
  const spotTop = r.top - PAD
  const spotBottom = r.top + r.height + PAD
  const spotCenterX = r.left + r.width / 2

  // 水平居中，不超出视口
  let left = spotCenterX - TOOLTIP_W / 2
  left = Math.max(16, Math.min(left, vw - TOOLTIP_W - 16))

  // 箭头水平偏移（指向高亮中心）
  const arrowX = Math.max(24, Math.min(spotCenterX - left, TOOLTIP_W - 24))

  // 气泡优先放在高亮上方（用 bottom 定位避免 transform 冲突）
  if (spotTop > 180) {
    arrowDir.value = 'top'
    tooltipPos.value = {
      position: 'fixed',
      bottom: `${vh - spotTop + GAP}px`,
      left: `${left}px`,
      width: `${TOOLTIP_W}px`,
    }
  } else {
    arrowDir.value = 'bottom'
    tooltipPos.value = {
      position: 'fixed',
      top: `${spotBottom + GAP}px`,
      left: `${left}px`,
      width: `${TOOLTIP_W}px`,
    }
  }

  arrowStyle.value = { left: `${arrowX}px` }
}

// ---- Live2D 聊天提示定位 ----
const modelTipPos = ref<Record<string, string>>({})

const modelTipMessage = computed(() => {
  if (props.live2dStatus === 'error') return '模型加载失败了，可以在占位区域重新加载'
  if (props.live2dStatus === 'ready') return '我准备好了，点我一下就能打开聊天框'
  return '模型资源正在加载，完成后就可以点击我啦'
})

function placeModelTip() {
  const el = document.querySelector('.ai-content .live2d')
    || document.querySelector('[data-onboarding="ai-assistant"]')
  if (!el) {
    modelTipPos.value = { position: 'fixed', bottom: '340px', right: '80px' }
    modelTipReady.value = true
    return
  }
  const rect = el.getBoundingClientRect()
  modelTipPos.value = {
    position: 'fixed',
    top: `${rect.top - 56}px`,
    left: `${rect.left + rect.width / 2}px`,
    transform: 'translateX(-50%)',
  }
  modelTipReady.value = true
}

const chatInputTipPos = ref<Record<string, string>>({})
const chatInputArrowStyle = ref<Record<string, string>>({})

function placeChatInputTip(): boolean {
  const el = document.querySelector('[data-onboarding="chat-input"]')
  if (!el) {
    chatInputReady.value = false
    return false
  }

  const rect = el.getBoundingClientRect()
  if (rect.width <= 0 || rect.height <= 0) {
    chatInputReady.value = false
    return false
  }

  const width = Math.min(320, window.innerWidth - 32)
  const centerX = rect.left + rect.width / 2
  const left = Math.max(16, Math.min(centerX - width / 2, window.innerWidth - width - 16))
  chatInputTipPos.value = {
    position: 'fixed',
    bottom: `${window.innerHeight - rect.top + 14}px`,
    left: `${left}px`,
    width: `${width}px`,
  }
  chatInputArrowStyle.value = {
    left: `${Math.max(24, Math.min(centerX - left, width - 24))}px`,
  }
  chatInputReady.value = true
  return true
}

// 聊天框使用 v-show，打开瞬间目标可能仍未完成布局。短时持续定位既能覆盖慢渲染，
// 也能在后续调整面板过渡时保持气泡跟随；聊天关闭时立即隐藏，避免悬浮在旧位置。
const CHAT_INPUT_RETRY_INTERVAL_MS = 100
const CHAT_INPUT_RETRY_LIMIT = 12
let chatInputRetryTimer: number | null = null

function stopChatInputRetry() {
  if (chatInputRetryTimer !== null) {
    window.clearTimeout(chatInputRetryTimer)
    chatInputRetryTimer = null
  }
}

function scheduleChatInputTip() {
  stopChatInputRetry()
  let attempts = 0

  const attempt = () => {
    chatInputRetryTimer = null
    if (!isChatTipActive.value || !props.chatOpen) {
      chatInputReady.value = false
      return
    }

    placeChatInputTip()
    attempts += 1
    if (attempts < CHAT_INPUT_RETRY_LIMIT) {
      chatInputRetryTimer = window.setTimeout(attempt, CHAT_INPUT_RETRY_INTERVAL_MS)
    }
  }

  nextTick(attempt)
}

// ---- 定位入口 ----
function locateSpotlight() {
  const el = document.querySelector('[data-onboarding="ai-assistant"]')
  if (!el) { spotlightReady.value = false; return }
  const rect = el.getBoundingClientRect()
  targetRect.value = { top: rect.top, left: rect.left, width: rect.width, height: rect.height }
  spotlightReady.value = true
  nextTick(() => {
    placeTooltip()
    // 气泡延迟出现，让高亮先渲染
    setTimeout(() => { tooltipVisible.value = true }, 200)
  })
}

  // ---- 欢迎事件 ----
  function handleWelcomeAccept() {
    accept()
  }

  function handleWelcomeDecline() {
    skip()
    emit('complete')
  }
// ---- 事件 ----
function handleSpotlightClick() {
  tooltipVisible.value = false
  nextStep()
  emit('spotlight-click')
}

function handleDismiss() {
  skip()
  emit('complete')
}

function handleChatTipDismiss() {
  finish()
  emit('complete')
}

// ---- 窗口变化 ----
let raf = 0
function onResize() {
  cancelAnimationFrame(raf)
  raf = requestAnimationFrame(() => {
    if (isSpotlightActive.value) { locateSpotlight(); placeTooltip() }
    if (isModelTipActive.value) placeModelTip()
    if (isChatTipActive.value && props.chatOpen) placeChatInputTip()
  })
}

// ---- 生命周期 ----
watch(isSpotlightActive, (v) => {
  if (v) nextTick(() => setTimeout(locateSpotlight, 350))
})

watch(isModelTipActive, (v) => {
  if (v) nextTick(() => setTimeout(placeModelTip, 250))
})

watch([isChatTipActive, () => props.chatOpen], ([active, chatOpen]) => {
  if (active && chatOpen) {
    scheduleChatInputTip()
  } else {
    stopChatInputRetry()
    chatInputReady.value = false
  }
}, { immediate: true })

onMounted(() => {
  window.addEventListener('resize', onResize, { passive: true })
  window.addEventListener('scroll', onResize, { passive: true })
  if (isSpotlightActive.value) nextTick(() => setTimeout(locateSpotlight, 350))
  if (isModelTipActive.value) nextTick(() => setTimeout(placeModelTip, 250))
})

onUnmounted(() => {
  window.removeEventListener('resize', onResize)
  window.removeEventListener('scroll', onResize)
  cancelAnimationFrame(raf)
  stopChatInputRetry()
})
</script>

<style scoped lang="scss">
@use "@/assets/styles/tokens" as *;
/* ============================================
   欢迎对话框
   ============================================ */
.welcome {
  position: fixed;
  inset: 0;
  z-index: 10001;
  display: flex;
  align-items: center;
  justify-content: center;
  pointer-events: auto;
}

.welcome__backdrop {
  position: absolute;
  inset: 0;
  background: rgba(0, 0, 0, 0.4);
  backdrop-filter: blur(4px);
  -webkit-backdrop-filter: blur(4px);
}

.welcome__card {
  position: relative;
  z-index: 1;
  background: var(--bg-card);
  border-radius: 24px;
  padding: 40px 36px 32px;
  max-width: 380px;
  width: calc(100% - 32px);
  text-align: center;
  box-shadow:
    0 4px 6px rgba(0, 0, 0, 0.04),
    0 20px 60px rgba(0, 0, 0, 0.15);
  border: 1px solid var(--border-light);
  animation: welcome-card-in 0.5s cubic-bezier(0.34, 1.56, 0.64, 1) both;
  animation-delay: 0.15s;
}

@keyframes welcome-card-in {
  from { opacity: 0; transform: translateY(24px) scale(0.92); }
  to { opacity: 1; transform: translateY(0) scale(1); }
}

.welcome__avatar {
  width: 72px;
  height: 72px;
  margin: 0 auto 20px;
  border-radius: 50%;
  overflow: hidden;
  border: 3px solid var(--color-primary);
  box-shadow: 0 0 0 4px rgba(var(--color-primary-rgb), 0.12);
  animation: welcome-avatar-pop 0.6s cubic-bezier(0.34, 1.56, 0.64, 1) both;
  animation-delay: 0.35s;
  img { width: 100%; height: 100%; object-fit: cover; }
}

@keyframes welcome-avatar-pop {
  from { opacity: 0; transform: scale(0.5); }
  to { opacity: 1; transform: scale(1); }
}

.welcome__title {
  margin: 0 0 8px;
  font-size: 20px;
  font-weight: 700;
  color: var(--text-title);
  animation: welcome-text-in 0.4s ease both;
  animation-delay: 0.45s;
}

.welcome__subtitle {
  margin: 0 0 28px;
  font-size: 14.5px;
  line-height: 1.6;
  color: var(--text-subtle);
  animation: welcome-text-in 0.4s ease both;
  animation-delay: 0.55s;
}

@keyframes welcome-text-in {
  from { opacity: 0; transform: translateY(8px); }
  to { opacity: 1; transform: translateY(0); }
}

.welcome__actions {
  display: flex;
  flex-direction: column;
  gap: 10px;
  animation: welcome-text-in 0.4s ease both;
  animation-delay: 0.6s;
}

.welcome__btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  width: 100%;
  padding: 12px 20px;
  border-radius: 12px;
  font-size: 14.5px;
  font-weight: 500;
  cursor: pointer;
  border: none;
  outline: none;
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
  &:focus-visible { box-shadow: var(--focus-ring); }
}

.welcome__btn--ghost {
  background: transparent;
  color: var(--text-subtle);
  border: 1px solid var(--border-base);
  &:hover {
    background: var(--bg-hover);
    color: var(--text-main);
    border-color: var(--border-strong);
  }
}

.welcome__btn--primary {
  background: var(--color-primary);
  color: var(--text-on-primary);
  font-weight: 600;
  svg { transition: transform 0.2s; }
  &:hover {
    filter: brightness(1.06);
    box-shadow: 0 4px 14px rgba(var(--color-primary-rgb), 0.3);
    svg { transform: translateX(3px); }
  }
}

/* 欢迎弹窗淡入 */
.welcome-fade-enter-active { transition: opacity 0.35s ease; }
.welcome-fade-leave-active { transition: opacity 0.25s ease; }
.welcome-fade-enter-from, .welcome-fade-leave-to { opacity: 0; }

/* ============================================
   第一阶段：遮罩高亮
   ============================================ */
.onboarding {
  position: fixed;
  inset: 0;
  z-index: 10001;
  pointer-events: none;
}

/* SVG 遮罩 */
.onboarding__mask {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  pointer-events: auto;
}

.onboarding__overlay-rect {
  cursor: pointer;
  transition: opacity 0.3s;
}

/* 呼吸光环 */
.onboarding__ring {
  position: absolute;
  pointer-events: none;
  z-index: 2;
  border: 2px solid rgba(var(--color-primary-rgb), 0.6);
  transition: all 0.4s cubic-bezier(0.4, 0, 0.2, 1);
}

.onboarding__glow {
  position: absolute;
  inset: -6px;
  border-radius: inherit;
  box-shadow:
    0 0 12px 2px rgba(var(--color-primary-rgb), 0.25),
    0 0 24px 6px rgba(var(--color-primary-rgb), 0.1);
  animation: glow-pulse 2.4s ease-in-out infinite;
  pointer-events: none;
}

@keyframes glow-pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.35; }
}

/* 可点击高亮区域 */
.onboarding__hitarea {
  position: absolute;
  z-index: 3;
  pointer-events: auto;
  cursor: pointer;
}

/* ============================================
   引导气泡
   ============================================ */
.tooltip {
  position: fixed;
  z-index: 4;
  pointer-events: auto;
}

.tooltip__card {
  background: var(--bg-card);
  border-radius: 16px;
  padding: 22px 24px 18px;
  box-shadow:
    0 4px 6px rgba(0, 0, 0, 0.04),
    0 12px 32px rgba(0, 0, 0, 0.1);
  border: 1px solid var(--border-light);
  backdrop-filter: blur(12px);
}

.tooltip__step {
  display: inline-block;
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.5px;
  color: var(--color-primary);
  background: var(--state-primary-bg);
  padding: 2px 10px;
  border-radius: 20px;
  margin-bottom: 12px;
}

.tooltip__msg {
  margin: 0 0 18px;
  font-size: 15px;
  line-height: 1.7;
  color: var(--text-main);

  strong {
    color: var(--color-primary);
    font-weight: 600;
  }
}

.tooltip__footer {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  gap: 10px;
}

.tooltip__btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 18px;
  border-radius: 10px;
  font-size: 13.5px;
  font-weight: 500;
  cursor: pointer;
  border: none;
  outline: none;
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);

  &:focus-visible {
    box-shadow: var(--focus-ring);
  }
}

.tooltip__btn--ghost {
  background: transparent;
  color: var(--text-subtle);

  &:hover {
    background: var(--bg-hover);
    color: var(--text-main);
  }
}

.tooltip__btn--primary {
  background: var(--color-primary);
  color: var(--text-on-primary);
  font-weight: 600;

  svg {
    transition: transform 0.2s;
  }

  &:hover {
    filter: brightness(1.06);
    box-shadow: 0 2px 8px rgba(var(--color-primary-rgb), 0.3);

    svg {
      transform: translateX(2px);
    }
  }
}

/* 箭头 */
.tooltip__arrow {
  position: absolute;
  width: 14px;
  height: 14px;
  background: var(--bg-card);
  border: 1px solid var(--border-light);
  transform: rotate(45deg);
  z-index: -1;
}

.tooltip__arrow--top {
  bottom: -8px;
  border-top: none;
  border-left: none;
  box-shadow: 4px 4px 8px rgba(0, 0, 0, 0.06);
}

.tooltip__arrow--bottom {
  top: -8px;
  border-bottom: none;
  border-right: none;
  box-shadow: -2px -2px 6px rgba(0, 0, 0, 0.04);
}

/* 气泡入场动画 */
.tooltip-pop-enter-active {
  transition: all 0.4s cubic-bezier(0.34, 1.56, 0.64, 1);
  transition-delay: 0.1s;
}
.tooltip-pop-leave-active {
  transition: all 0.2s ease;
}
.tooltip-pop-enter-from {
  opacity: 0;
  transform: translateY(8px) scale(0.96);
}
.tooltip-pop-leave-to {
  opacity: 0;
  transform: translateY(4px) scale(0.98);
}

/* ============================================
   第二阶段：聊天提示
   ============================================ */
.chat-tip {
  z-index: 10002;
  max-width: calc(100vw - 32px);
  pointer-events: auto;
}

.chat-tip__card {
  position: relative;
  display: flex;
  align-items: center;
  gap: 12px;
  background: var(--bg-card);
  border-radius: 14px;
  padding: 14px 20px;
  box-shadow:
    0 2px 4px rgba(0, 0, 0, 0.04),
    0 8px 24px rgba(0, 0, 0, 0.1);
  border: 1px solid var(--border-light);
  box-sizing: border-box;
  max-width: 100%;
}

.chat-tip__step {
  flex-shrink: 0;
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.5px;
  color: var(--color-primary);
  background: var(--state-primary-bg);
  padding: 2px 8px;
  border-radius: 20px;
}

.chat-tip__loading-dot {
  width: 8px;
  height: 8px;
  flex: 0 0 auto;
  border-radius: 50%;
  background: var(--color-primary);
  box-shadow: 0 0 0 0 rgba(var(--color-primary-rgb), 0.35);
  animation: chat-tip-loading 1.4s ease-out infinite;
}

@keyframes chat-tip-loading {
  70%, 100% { box-shadow: 0 0 0 8px rgba(var(--color-primary-rgb), 0); }
}

.chat-tip__msg {
  min-width: 0;
  margin: 0;
  font-size: 14px;
  font-weight: 500;
  color: var(--text-main);
  overflow-wrap: anywhere;
  animation: tip-float 2.5s ease-in-out infinite;
}

@keyframes tip-float {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(-2px); }
}

.chat-tip__close {
  flex-shrink: 0;
  width: 24px;
  height: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: none;
  border: none;
  color: var(--text-muted);
  cursor: pointer;
  border-radius: 50%;
  transition: all 0.15s ease;
  padding: 0;

  &:hover {
    background: var(--bg-hover);
    color: var(--text-main);
  }
}

.chat-tip__arrow {
  position: absolute;
  bottom: -7px;
  left: 50%;
  margin-left: -7px;
  width: 14px;
  height: 14px;
  background: var(--bg-card);
  border: 1px solid var(--border-light);
  border-top: none;
  border-left: none;
  transform: rotate(45deg);
  box-shadow: 4px 4px 8px rgba(0, 0, 0, 0.06);
}

.input-tip {
  z-index: 10002;
  pointer-events: auto;
}

/* 聊天提示入场动画 */
.chat-tip-pop-enter-active {
  transition: all 0.4s cubic-bezier(0.34, 1.56, 0.64, 1);
}
.chat-tip-pop-leave-active {
  transition: all 0.2s ease;
}
.chat-tip-pop-enter-from {
  opacity: 0;
  transform: translateY(10px) scale(0.95);
}
.chat-tip-pop-leave-to {
  opacity: 0;
  transform: translateY(6px) scale(0.97);
}

/* ============================================
   遮罩整体淡入淡出
   ============================================ */
.onboarding-fade-enter-active {
  transition: opacity 0.5s ease;
}
.onboarding-fade-leave-active {
  transition: opacity 0.3s ease;
}
.onboarding-fade-enter-from,
.onboarding-fade-leave-to {
  opacity: 0;
}

/* ============================================
   响应式
   ============================================ */
@include respond(md) {
  .tooltip__card {
    padding: 18px 20px 16px;
  }
  .tooltip__msg {
    font-size: 14px;
  }
}

@include respond(sm) {
  .tooltip__card {
    padding: 16px;
    border-radius: 14px;
  }
  .tooltip__msg {
    font-size: 13.5px;
    margin-bottom: 14px;
  }
  .tooltip__btn {
    padding: 7px 14px;
    font-size: 13px;
  }
  .chat-tip__card {
    padding: 10px 14px;
    border-radius: 12px;
  }
  .chat-tip__msg {
    font-size: 13px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .chat-tip__loading-dot,
  .chat-tip__msg {
    animation: none;
  }
}
</style>
