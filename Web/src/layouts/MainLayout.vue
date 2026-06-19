<script setup lang="ts">
import { onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import TheHeader from '../components/TheHeader.vue'
import TheFooter from '../components/TheFooter.vue'
import Banner from '@/components/Banner.vue'
import Breadcrumb from '@/components/Breadcrumb.vue'
import BottomNavigation from '@/components/BottomNavigation.vue'
import Live2d from '@/components/Live2d.vue'
// 全局页面加载（作者：刘鑫，修改时间：2025-09-24 20:11:17 +08:00）
import GlobalPageLoader from '../components/GlobalPageLoader.vue'
import AiChat from '@/components/AiChat.vue'
import LoginModal from '@/components/LoginModal.vue'
import GlobalSearchModal from '@/components/GlobalSearchModal.vue'
import { requireAuth } from '@/utils/auth'
import { useChatStore } from '@/stores/chat'
import { getServiceBaseURL, ServiceType } from '@/config/services'
import { useOnboarding } from '@/composables/useOnboarding'
import OnboardingGuide from '@/components/OnboardingGuide.vue'

const showLoader = ref(false)
const router = useRouter()
const route = useRoute()

// 滚动位置状态
const scrollY = ref(0)

let timer: number | null = null
// 检查是否为首次访问（页面刷新或首次打开）
const isFirstLoad = ref(true)

// 显示模型和聊天
const showModel = ref(false)
const showChat = ref(false)
const isExpanded = ref(false)

// 登录弹窗控制
const showLoginModal = ref(false)
const loginMessage = ref('')

// 防抖处理，避免频繁点击
let modelToggleTimeout: ReturnType<typeof setTimeout> | null = null;

const chatStore = useChatStore()
const live2dRef = ref<InstanceType<typeof Live2d> | null>(null)
const aiChatRef = ref<InstanceType<typeof AiChat> | null>(null)
const searchModalRef = ref<InstanceType<typeof GlobalSearchModal> | null>(null)

// 新用户引导
const { initOnboarding } = useOnboarding()

// 全局搜索快捷键
const handleGlobalKeydown = (e: KeyboardEvent) => {
  if ((e.metaKey || e.ctrlKey) && e.key === 'k') {
    e.preventDefault()
    searchModalRef.value?.open()
  }
}

// TTS 播放器（内联，仅本组件使用）
function useTtsPlayer() {
  let isTtsPlaying = false
  let playbackToken = 0
  let currentTtsAudio: HTMLAudioElement | null = null
  let isApplyingAvatarCue = false
  let audioUnlocked = false

  function resolveTtsPlayUrl(audioUrl?: string): string {
    if (!audioUrl) return ''
    if (audioUrl.startsWith('/')) {
      const base = getServiceBaseURL(ServiceType.MAIN).replace(/\/$/, '')
      if (base.startsWith('/') && audioUrl.startsWith(`${base}/`)) return audioUrl
      return `${base}${audioUrl}`
    }
    return audioUrl
  }

  function stopTtsPlayback() {
    playbackToken++
    try { currentTtsAudio?.pause() } catch {}
    currentTtsAudio = null
    isTtsPlaying = false
    live2dRef.value?.resumeMusicAfterSpeechIfNeeded?.()
  }

  function waitOnce(audio: HTMLAudioElement, event: string, timeoutMs: number): Promise<boolean> {
    return new Promise((resolve) => {
      let done = false
      const timer = window.setTimeout(() => {
        if (done) return
        done = true
        resolve(false)
      }, timeoutMs)
      audio.addEventListener(event, () => {
        if (done) return
        done = true
        window.clearTimeout(timer)
        resolve(true)
      }, { once: true })
    })
  }

  const delay = (ms: number) => new Promise<void>((r) => window.setTimeout(r, ms))

  async function playNextTts() {
    if (isTtsPlaying) return
    if (chatStore.ttsEnabled !== true || chatStore.ttsAvailable !== true) return
    if (!live2dRef.value) return

    const token = playbackToken
    isTtsPlaying = true

    try {
      while (token === playbackToken) {
        if (chatStore.ttsEnabled !== true || chatStore.ttsAvailable !== true) break
        if (!live2dRef.value) break

        const next = chatStore.shiftTtsAudioQueue()
        if (!next) break

        if (next.cue && next.cue.expression !== 'neutral') {
          live2dRef.value.applyAvatarCue?.({ ...next.cue, skipResetTimer: true })
        }

        if (next.status === 'skipped') {
          console.warn(`[TTS][skip] seq=${next.seq} reason=${next.reason ?? 'unknown'}`)
          continue
        }

        const playUrl = resolveTtsPlayUrl(next.audioUrl)
        if (!playUrl) continue

        let audio = next.audioEl
          ? await live2dRef.value.speakAudioElement(next.audioEl)
          : await live2dRef.value.speakAudioUrl(playUrl)
        if (!audio) continue
        currentTtsAudio = audio

        let started = false
        for (let attempt = 0; attempt < 6 && token === playbackToken; attempt++) {
          try {
            await audio.play()
            started = true
            break
          } catch (e: any) {
            if (e?.name === 'NotAllowedError') break
            try { audio.pause() } catch {}
            currentTtsAudio = null
            await delay(250 + attempt * 200)
            if (!live2dRef.value) break
            const retryAudio = await live2dRef.value.speakAudioUrl(playUrl)
            if (!retryAudio) break
            audio = retryAudio
            currentTtsAudio = audio
          }
        }

        if (!started) {
          try { currentTtsAudio?.pause() } catch {}
          currentTtsAudio = null
          continue
        }

        await Promise.race([
          waitOnce(audio, 'ended', 60000),
          waitOnce(audio, 'error', 60000),
          waitOnce(audio, 'pause', 60000)
        ])

        currentTtsAudio = null
      }
    } finally {
      if (token === playbackToken) {
        isTtsPlaying = false
        live2dRef.value?.applyAvatarCue?.({ expression: 'neutral' })
        live2dRef.value?.resumeMusicAfterSpeechIfNeeded?.()
      }
    }
  }

  async function applyNextAvatarCues() {
    if (isApplyingAvatarCue) return
    if (isTtsPlaying || chatStore.ttsAwaitingAudio || chatStore.ttsPendingCount > 0) return
    if (!live2dRef.value || !showModel.value) return
    isApplyingAvatarCue = true
    try {
      while (live2dRef.value && showModel.value) {
        const next = chatStore.shiftAvatarCueQueue()
        if (!next) break
        live2dRef.value.applyAvatarCue?.(next)
        await delay(120)
      }
    } finally {
      isApplyingAvatarCue = false
    }
  }

  async function unlockAudio() {
    if (audioUnlocked) return
    audioUnlocked = true
    try {
      const Ctx = (window.AudioContext || (window as any).webkitAudioContext) as typeof AudioContext | undefined
      if (!Ctx) return
      const ctx = new Ctx()
      const gain = ctx.createGain()
      gain.gain.value = 0
      const osc = ctx.createOscillator()
      osc.connect(gain)
      gain.connect(ctx.destination)
      try { await ctx.resume() } catch {}
      osc.start()
      osc.stop(ctx.currentTime + 0.01)
      window.setTimeout(() => { ctx.close().catch(() => {}) }, 50)
    } catch {}
  }

  return { stopTtsPlayback, playNextTts, applyNextAvatarCues, unlockAudio }
}

const { stopTtsPlayback, playNextTts, applyNextAvatarCues, unlockAudio } = useTtsPlayer()

const handleExternalChatOpen = (event: Event) => {
  showModel.value = true
  showChat.value = true
  isExpanded.value = true

  const detail = (event as CustomEvent<Record<string, any>>).detail
  if (detail?.prompt) {
    window.setTimeout(() => {
      window.dispatchEvent(new CustomEvent('ai-chat-apply-prompt', { detail }))
    }, 0)
  }
}

// 滚动监听函数
const handleScroll = () => {
  scrollY.value = window.scrollY
}

const handleSpotlightClick = () => {
  showModel.value = true
}

onMounted(() => {
  // 初始化新用户引导
  setTimeout(() => initOnboarding(), 2000)

  // 页面加载时立即显示加载动画
  showLoader.value = true

  const onceUnlock = () => {
    unlockAudio()
    window.removeEventListener('pointerdown', onceUnlock)
    window.removeEventListener('keydown', onceUnlock)
    window.removeEventListener('touchstart', onceUnlock)
  }
  window.addEventListener('pointerdown', onceUnlock, { passive: true })
  window.addEventListener('keydown', onceUnlock, { passive: true })
  window.addEventListener('touchstart', onceUnlock, { passive: true })
  
  // 添加滚动监听
  window.addEventListener('scroll', handleScroll, { passive: true })
  window.addEventListener('ai-chat-open', handleExternalChatOpen)
  window.addEventListener('keydown', handleGlobalKeydown)
  if (timer) { window.clearTimeout(timer) }

  // 兜底 3s 自动结束
  timer = window.setTimeout(() => {
    showLoader.value = false
    timer = null
  }, 3000)

  // 正常完成后，保证至少 1.6s 的可见时长
  const MIN = 1600
  const start = performance.now()
  const end = () => {
    const elapsed = performance.now() - start
    const remain = Math.max(0, MIN - elapsed)
    window.setTimeout(() => {
      showLoader.value = false
      if (timer) {
        window.clearTimeout(timer)
        timer = null
      }
    }, remain)
  }

  // 延迟执行结束逻辑
  window.setTimeout(end, 100)

  // 设置路由守卫，后续路由跳转不显示加载动画
  router.beforeEach((to, from, next) => {
    // 如果不是首次加载，则不显示加载动画
    if (!isFirstLoad.value) {
      next()
      return
    }
    isFirstLoad.value = false
    next()
  })
})

onUnmounted(() => {
  // 移除滚动监听
  window.removeEventListener('scroll', handleScroll)
  window.removeEventListener('ai-chat-open', handleExternalChatOpen)
  window.removeEventListener('keydown', handleGlobalKeydown)
})

// TTS 生命周期：这些 watcher 将 chatStore 的响应式状态变化桥接到 useTtsPlayer 的命令式 API

// TTS 开关/可用性/队列变化：禁用时停止播放并清队列，启用且有待播音频时开始播放
watch(
  () => [chatStore.ttsEnabled, chatStore.ttsAvailable, chatStore.ttsPendingCount],
  () => {
    if (chatStore.ttsEnabled !== true || chatStore.ttsAvailable !== true) {
      stopTtsPlayback()
      chatStore.clearTtsAudioQueue()
      return
    }
    playNextTts()
  }
)

// AI 开始思考时，给 Live2D 应用"思考"表情（半眼+手势变化）
watch(
  () => chatStore.aiThinking,
  (thinking) => {
    if (thinking && live2dRef.value && showModel.value) {
      live2dRef.value.applyAvatarCue?.({ expression: 'thinking', durationMs: 8000 })
    }
  }
)

// 新消息发送时（ttsCancelCounter 递增），停止当前 TTS 播放，避免旧音频和新回复重叠
watch(
  () => chatStore.ttsCancelCounter,
  () => {
    stopTtsPlayback()
  }
)

// TTS 关闭时，独立的 avatar-cue 仍可驱动表情变化
watch(
  () => chatStore.avatarCuePendingCount,
  () => {
    applyNextAvatarCues()
  }
)

// Live2D 模型显示/隐藏时，同步控制 TTS 播放状态
// setTimeout(0) 延迟到下一个 tick，确保 showModel 的 DOM 更新完成后再执行
watch(
  () => showModel.value,
  (visible) => {
    if (!visible) {
      stopTtsPlayback()
      return
    }
    setTimeout(() => {
      playNextTts()
      applyNextAvatarCues()
      live2dRef.value?.refresh?.()
    }, 0)
  }
)

const toggleChat = () => {
  showChat.value = !showChat.value
  if (!showChat.value) {
    // 关闭聊天框时，重置展开状态
    isExpanded.value = false
    showModel.value = true
  }
}

const handleModelClick = () => {
  if (isExpanded.value) return
  toggleChat()
}

// 处理聊天框展开
const handleExpandChat = () => {
  if (isExpanded.value) {
    showModel.value = true
  }
  isExpanded.value = !isExpanded.value
}

const handleCloseChat = () => {
  showChat.value = false
  isExpanded.value = false
  showModel.value = true
}

const handleToggleModelVisibility = () => {
  if (!isExpanded.value) return
  showModel.value = !showModel.value
}

const handleModelWheel = (event: WheelEvent) => {
  if (!isExpanded.value) return
  event.preventDefault()
  aiChatRef.value?.scrollBodyBy?.(event.deltaY)
}

const handleModelStatusChange = () => {
  if (modelToggleTimeout) {
    clearTimeout(modelToggleTimeout);
  }

  // 设置防抖延迟
  modelToggleTimeout = setTimeout(() => {
    showModel.value = !showModel.value
    modelToggleTimeout = null;
  }, 300); // 300ms防抖延迟
}

// 显示登录弹窗
const showLoginModalWithMessage = (message?: string) => {
  loginMessage.value = message || '此功能需要登录后才能使用，请先登录您的账户。'
  showLoginModal.value = true
}

// 处理需要登录的操作
const handleAuthRequired = (action: () => void, message?: string) => {
  requireAuth(action, () => showLoginModalWithMessage(message))
}

</script>

<template>
  <div class="main-layout">
    <TheHeader class="header" :scroll-y="scrollY" @open-search="searchModalRef?.open()" />
    <main class="main-content">
      <Banner class="banner" />
      <Breadcrumb />
      <router-view />
      <div v-if="showModel || showChat" class="ai-content" :class="{ 'expanded': isExpanded }">
        <div class="ai-box">
          <Live2d
            ref="live2dRef"
            @click="handleModelClick"
            @wheel="handleModelWheel"
            class="live2d"
            :class="{ 'centered': isExpanded, 'is-hidden': !showModel }"
            :interactive="true"
          ></Live2d>
          <AiChat
            v-show="showChat"
            ref="aiChatRef"
            class="ai-chat"
            :expanded="isExpanded"
            :model-visible="showModel"
            @expand="handleExpandChat"
            @close="handleCloseChat"
            @toggle-model-visibility="handleToggleModelVisibility"
          ></AiChat>
        </div>
      </div>
    </main>
    <TheFooter />
    <BottomNavigation @ai-chat-active="handleModelStatusChange" @auth-required="handleAuthRequired"></BottomNavigation>
    <GlobalPageLoader :show="showLoader" />

    <!-- 登录弹窗 -->
    <LoginModal v-model:visible="showLoginModal" :message="loginMessage" />

    <!-- 全局搜索 -->
    <GlobalSearchModal ref="searchModalRef" />

    <!-- 新用户引导 -->
    <OnboardingGuide
      @spotlight-click="handleSpotlightClick"
      @complete=""
    />
  </div>
</template>

<style scoped lang="scss">
@use "@/assets/styles/tokens" as *;
.header{
  position: fixed;
  top: 0;
  left: 0;
  width: 100%;
  z-index: 100;
}

.main-layout {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  background:
    linear-gradient(to bottom,
      var(--bg-main) 0%,
      var(--bg-main) 500px,
      var(--bg-main-fade) 600px,
      var(--bg-main-fade) 700px,
      transparent 800px
    );

  @include respond(md) {
    background:
      linear-gradient(to bottom,
        var(--bg-main) 0%,
        var(--bg-main) 300px,
        var(--bg-main-fade) 380px,
        var(--bg-main-fade) 450px,
        transparent 520px
      );
  }

  @include respond(sm) {
    background:
      linear-gradient(to bottom,
        var(--bg-main) 0%,
        var(--bg-main) 220px,
        var(--bg-main-fade) 280px,
        var(--bg-main-fade) 330px,
        transparent 380px
      );
  }
}

.main-content {
  width: 100%;
  height: 100%;
  flex: 1;
  position: relative;
}

.banner {
  height: 600px;
  @include respond(md) {
    height: 400px;

  }
  @include respond(sm) {
    height: 300px;

  }
}

.ai-content {
  width: 400px;
  height: 400px;
  position: fixed;
  top: calc(100vh - 400px);
  left: calc(100vw - 400px);
  z-index: 10;
  transition-property: top, left, width, height, transform, padding;
  transition-duration: 0.4s;
  transition-timing-function: cubic-bezier(0.4, 0, 0.2, 1);
  @include respond(md) {
    width: 100%;
    left: 0;
    top: calc(100vh - 400px);
    padding: 0 20px;
  }
}

.ai-content.expanded {
  width: 80vw;
  height: 90vh;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -46%);
  @include respond(md) {
    width: 100vw;
    height: calc(100vh - 56px);
    top: 56px;
    left: 0;
    transform: none;
    padding: 0;
  }
}

.ai-box {
  position: relative;
  width: 100%;
  height: 100%;
}

.live2d {
  position: relative;
  width: 100%;
  height: 100%;
  z-index: 30;
  transition-property: width, height, left, bottom, transform, opacity;
  transition-duration: 0.4s;
  transition-timing-function: cubic-bezier(0.4, 0, 0.2, 1);
  &.is-hidden {
    visibility: hidden;
    pointer-events: none;
    opacity: 0;
  }
}

.ai-chat {
  width: 400px;
  height: 560px;
  position: absolute;
  right: 100%;
  bottom: 0;
  transition-property: width, height, right, bottom, opacity;
  transition-duration: 0.4s;
  transition-timing-function: cubic-bezier(0.4, 0, 0.2, 1);
  @include respond(md) {
    width: 100%;
    right: 0;

  }
}

/* 当聊天框展开时的样式 */
.ai-content.expanded .ai-chat {
  width: 100%;
  height: 100%;
  right: 0;
  bottom: 0;
}

/* Live2d居中样式 */
.live2d.centered {
  position: absolute;
  bottom: 0%;
  left: 50%;
  transform: translate(-50%, -20%);
  width: min(450px, 40vw);
  height: min(450px, 40vw);
  z-index: 30;
}

@include respond(md) {
  .live2d.centered {
    width: min(400px, 85vw);
    height: min(400px, 85vw);
  }
}

</style>
