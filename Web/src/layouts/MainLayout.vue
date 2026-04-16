<script setup lang="ts">
import { onMounted, onUnmounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import TheHeader from '../components/TheHeader.vue'
import TheFooter from '../components/TheFooter.vue'
import Banner from '@/components/Banner.vue'
import Breadcrumb from '@/components/Breadcrumb.vue'
import BottomNavigation from '@/components/BottomNavigation.vue'
import Live2d from '@/components/Live2d.vue'
// 全局页面加载（作者：刘鑫，修改时间：2025-09-24 20:11:17 +08:00）
import GlobalPageLoader from '../components/GlobalPageLoader.vue'
import AiChat from "@/components/AiChat.vue"
import LoginModal from '@/components/LoginModal.vue'
import { requireAuth } from '@/utils/auth'
import { useChatStore } from '@/stores/chat'

const showLoader = ref(false)
const router = useRouter()

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

const aiChatActive = ref(false)

const chatStore = useChatStore()
const live2dRef = ref<InstanceType<typeof Live2d> | null>(null)
let currentTtsAudio: HTMLAudioElement | null = null
let isTtsPlaying = false
let playbackToken = 0
let audioUnlocked = false

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

const resolveTtsPlayUrl = (audioUrl: string) => {
  if (!audioUrl) return ''
  return audioUrl
}

const stopTtsPlayback = () => {
  playbackToken++
  try {
    currentTtsAudio?.pause()
  } catch {
  }
  currentTtsAudio = null
  isTtsPlaying = false
}

const waitOnce = (audio: HTMLAudioElement, event: string, timeoutMs: number) => {
  return new Promise<boolean>((resolve) => {
    let done = false
    const timer = window.setTimeout(() => {
      if (done) return
      done = true
      resolve(false)
    }, timeoutMs)

    const handler = () => {
      if (done) return
      done = true
      window.clearTimeout(timer)
      resolve(true)
    }

    audio.addEventListener(event, handler, { once: true })
  })
}

const delay = (ms: number) => new Promise<void>((r) => window.setTimeout(r, ms))

const playNextTts = async () => {
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

      const playUrl = resolveTtsPlayUrl(next.audioUrl)
      if (!playUrl) continue

      console.log(
        `[TTS][play] seq=${next.seq} conv=${next.conversationId ?? '-'} ` +
        `pickedAt=${new Date().toISOString()} enqueuedAt=${next.enqueuedAt ? new Date(next.enqueuedAt).toISOString() : '-'} ` +
        `playUrl=${playUrl}`
      )

      const speakAt = performance.now()
      let audio = await live2dRef.value.speakAudioUrl(playUrl)
      if (!audio) continue

      console.log(`[TTS][play] seq=${next.seq} speakAudioUrlMs=${Math.round(performance.now() - speakAt)}`)
      currentTtsAudio = audio

      const attachPlayingLog = (el: HTMLAudioElement) => {
        el.addEventListener('playing', () => {
          console.log(`[TTS][playing] seq=${next.seq} at=${new Date().toISOString()}`)
        }, { once: true })
      }
      attachPlayingLog(audio)

      let started = false
      for (let attempt = 0; attempt < 6 && token === playbackToken; attempt++) {
        try {
          await audio.play()
          started = true
          break
        } catch (e: any) {
          const name = e?.name || ''
          if (name === 'NotAllowedError') {
            console.warn(`[TTS][play] seq=${next.seq} blockedByAutoplayAt=${new Date().toISOString()}`)
            break
          }
          console.warn(`[TTS][play] seq=${next.seq} playRejected attempt=${attempt + 1} name=${name}`)
          try {
            audio.pause()
          } catch {
          }
          currentTtsAudio = null
          await delay(250 + attempt * 200)
          if (!live2dRef.value) break
          const retryAudio = await live2dRef.value.speakAudioUrl(playUrl)
          if (!retryAudio) break
          audio = retryAudio
          currentTtsAudio = audio
          attachPlayingLog(audio)
        }
      }

      if (!started) {
        console.warn(`[TTS][play] seq=${next.seq} startFailedAt=${new Date().toISOString()}`)
        try {
          currentTtsAudio?.pause()
        } catch {
        }
        currentTtsAudio = null
        continue
      }

      const finished = await Promise.race([
        waitOnce(audio, 'ended', 60000),
        waitOnce(audio, 'error', 60000),
        waitOnce(audio, 'pause', 60000)
      ])

      if (!finished) {
        console.warn(`[TTS][play] seq=${next.seq} waitTimeoutAt=${new Date().toISOString()}`)
        try {
          audio.pause()
        } catch {
        }
      } else {
        console.log(`[TTS][play] seq=${next.seq} finishedAt=${new Date().toISOString()}`)
      }

      currentTtsAudio = null
    }
  } finally {
    if (token === playbackToken) {
      isTtsPlaying = false
    }
  }
}

// 滚动监听函数
const handleScroll = () => {
  scrollY.value = window.scrollY
}

onMounted(() => {
  // 页面加载时立即显示加载动画
  showLoader.value = true

  const unlockAudio = async () => {
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
      try {
        await ctx.resume()
      } catch {
      }
      osc.start()
      osc.stop(ctx.currentTime + 0.01)
      window.setTimeout(() => {
        ctx.close().catch(() => {
        })
      }, 50)
    } catch {
    }
  }

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
})

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

watch(
  () => showModel.value,
  (visible) => {
    if (!visible) {
      stopTtsPlayback()
      return
    }
    setTimeout(() => {
      playNextTts()
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
    <TheHeader class="header" :scroll-y="scrollY" />
    <main class="main-content">
      <Banner class="banner" />
      <Breadcrumb />
      <router-view />
      <div v-if="showModel || showChat" class="ai-content" :class="{ 'expanded': isExpanded }">
        <div class="ai-box">
          <Live2d
            v-show="showModel"
            ref="live2dRef"
            @click="toggleChat"
            class="live2d"
            :class="{ 'centered': isExpanded, passive: isExpanded }"
            :interactive="!isExpanded"
          ></Live2d>
          <AiChat
            v-show="showChat"
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
    ),
    radial-gradient(circle at 15% 20%, rgba(45, 144, 205, 0.08) 0%, transparent 40%) no-repeat,
    radial-gradient(circle at 85% 80%, rgba(240, 184, 192, 0.1) 0%, transparent 40%) no-repeat,
    radial-gradient(circle at 50% 50%, rgba(45, 144, 205, 0.05) 0%, transparent 50%) no-repeat,
    radial-gradient(circle at 80% 20%, rgba(45, 144, 205, 0.06) 0%, transparent 30%) no-repeat,
    radial-gradient(circle at 20% 70%, rgba(240, 184, 192, 0.08) 0%, transparent 35%) no-repeat;
  background-size: 100% 100%;

  @include respond(md) {
    background:
      linear-gradient(to bottom,
        var(--bg-main) 0%,
        var(--bg-main) 300px,
        var(--bg-main-fade) 380px,
        var(--bg-main-fade) 450px,
        transparent 520px
      ),
      radial-gradient(circle at 15% 20%, rgba(45, 144, 205, 0.08) 0%, transparent 40%) no-repeat,
      radial-gradient(circle at 85% 80%, rgba(240, 184, 192, 0.1) 0%, transparent 40%) no-repeat,
      radial-gradient(circle at 50% 50%, rgba(45, 144, 205, 0.05) 0%, transparent 50%) no-repeat,
      radial-gradient(circle at 80% 20%, rgba(45, 144, 205, 0.06) 0%, transparent 30%) no-repeat,
      radial-gradient(circle at 20% 70%, rgba(240, 184, 192, 0.08) 0%, transparent 35%) no-repeat;
  }

  @include respond(sm) {
    background:
      linear-gradient(to bottom,
        var(--bg-main) 0%,
        var(--bg-main) 220px,
        var(--bg-main-fade) 280px,
        var(--bg-main-fade) 330px,
        transparent 380px
      ),
      radial-gradient(circle at 15% 20%, rgba(45, 144, 205, 0.08) 0%, transparent 40%) no-repeat,
      radial-gradient(circle at 85% 80%, rgba(240, 184, 192, 0.1) 0%, transparent 40%) no-repeat,
      radial-gradient(circle at 50% 50%, rgba(45, 144, 205, 0.05) 0%, transparent 50%) no-repeat,
      radial-gradient(circle at 80% 20%, rgba(45, 144, 205, 0.06) 0%, transparent 30%) no-repeat,
      radial-gradient(circle at 20% 70%, rgba(240, 184, 192, 0.08) 0%, transparent 35%) no-repeat;
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
  bottom: 0;
  right: 0;
  z-index: 10;
  transition: all 0.4s cubic-bezier(0.4, 0, 0.2, 1);
  @include respond(md) {
    width: 100%;
    padding: 0 20px;
  }
}

.ai-content.expanded {
  width: 80vw;
  height: 90vh;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -46%);
  bottom: auto;
  right: auto;
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
  transition: all 0.4s cubic-bezier(0.4, 0, 0.2, 1);
}

.ai-chat {
  width: 400px;
  height: 500px;
  position: absolute;
  top: 0;
  left: 0;
  transform: translateY(-100px) translateX(-400px);
  @include respond(md) {
    width: 100%;
    transform: translateY(-100px) translateX(0);

  }
}

/* 当聊天框展开时的样式 */
.ai-content.expanded .ai-chat {
  width: 100%;
  height: 100%;
  transform: none;
}

/* Live2d居中样式 */
.live2d.centered {
  position: absolute;
  bottom: 0%;
  left: 50%;
  transform: translate(-50%, -20%);
  width: min(400px, 40vw);
  height: min(400px, 40vw);
  z-index: 30;
}

.live2d.passive {
  pointer-events: none;
}



</style>
