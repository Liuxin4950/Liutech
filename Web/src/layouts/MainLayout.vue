<script setup lang="ts">
import { onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import TheHeader from '../components/TheHeader.vue'
import TheFooter from '../components/TheFooter.vue'
import Banner from '@/components/Banner.vue'
import Breadcrumb from '@/components/Breadcrumb.vue'
import BottomNavigation from '@/components/BottomNavigation.vue'
import Live2d from '@/components/Live2d.vue'
import AiChat from '@/components/AiChat.vue'
import LoginModal from '@/components/LoginModal.vue'
import GlobalSearchModal from '@/components/GlobalSearchModal.vue'
import { requireAuth } from '@/utils/auth'
import { useChatStore } from '@/stores/chat'
import { useBannerStore } from '@/stores/banner'
import { useAuthModalStore } from '@/stores/authModal'
import { useOnboarding } from '@/composables/useOnboarding'
import { useTtsPlayer } from '@/composables/useTtsPlayer'
import OnboardingGuide from '@/components/OnboardingGuide.vue'
import { initLenis, destroyLenis } from '@/composables/useLenis'

const route = useRoute()

const chatStore = useChatStore()
const bannerStore = useBannerStore()
const authModalStore = useAuthModalStore()

// Banner 定制集中管理：定制页进入时自行 setBanner 覆盖，这里只在进入非定制页时恢复默认轮播。
// 不用"页面卸载时 resetBanner"——路由切换时旧页面的 reset 可能晚于新页面的 set 执行，产生竞态。
const CUSTOM_BANNER_PATHS = ['/post/', '/category-detail/', '/tags', '/series', '/categories', '/archive', '/about', '/my-posts', '/view-history', '/favorites']
watch(() => route.path, () => {
  if (!CUSTOM_BANNER_PATHS.some(p => route.path.startsWith(p))) {
    bannerStore.resetBanner()
  }
}, { immediate: true })

const live2dRef = ref<InstanceType<typeof Live2d> | null>(null)
const aiChatRef = ref<InstanceType<typeof AiChat> | null>(null)
const bottomNavRef = ref<InstanceType<typeof BottomNavigation> | null>(null)
const searchModalRef = ref<InstanceType<typeof GlobalSearchModal> | null>(null)

// 新用户引导
const { initOnboarding, step: onboardingStep, nextStep: nextOnboardingStep } = useOnboarding()
let onboardingTimer: number | null = null
const live2dStatus = ref<'idle' | 'loading' | 'ready' | 'error'>('idle')

// 全局搜索快捷键
const handleGlobalKeydown = (e: KeyboardEvent) => {
  if ((e.metaKey || e.ctrlKey) && e.key === 'k') {
    e.preventDefault()
    searchModalRef.value?.open()
  }
}

// TTS 播放器 + Avatar Cue 调度 + 音乐桥接（集中在 useTtsPlayer composable）
const { unlockAudio, handleMusicPlay, handleMusicPause, handleSpeakStart } = useTtsPlayer({ chatStore, live2dRef, bottomNavRef })

const handleExternalChatOpen = (event: Event) => {
  const detail = (event as CustomEvent<Record<string, any>>).detail
  chatStore.openChatExternal(detail)
}

const handleSpotlightClick = () => {
  // Live2D 可能已被其它入口提前挂载并加载完成，不要覆盖只会发射一次的 ready 状态。
  if (live2dStatus.value !== 'ready') {
    live2dStatus.value = 'loading'
  }
  chatStore.showModel = true
}

const handleLive2dLoadStart = () => {
  live2dStatus.value = 'loading'
}

const handleLive2dReady = () => {
  live2dStatus.value = 'ready'
}

const handleLive2dError = () => {
  live2dStatus.value = 'error'
}

onMounted(() => {
  initLenis()

  // 初始化新用户引导
  onboardingTimer = window.setTimeout(() => initOnboarding(), 700)

  const onceUnlock = () => {
    unlockAudio()
    window.removeEventListener('pointerdown', onceUnlock)
    window.removeEventListener('keydown', onceUnlock)
    window.removeEventListener('touchstart', onceUnlock)
  }
  window.addEventListener('pointerdown', onceUnlock, { passive: true })
  window.addEventListener('keydown', onceUnlock, { passive: true })
  window.addEventListener('touchstart', onceUnlock, { passive: true })
  window.addEventListener('ai-chat-open', handleExternalChatOpen)
  window.addEventListener('keydown', handleGlobalKeydown)
})

onUnmounted(() => {
  destroyLenis()
  if (onboardingTimer !== null) {
    window.clearTimeout(onboardingTimer)
    onboardingTimer = null
  }
  window.removeEventListener('ai-chat-open', handleExternalChatOpen)
  window.removeEventListener('keydown', handleGlobalKeydown)
})

// TTS 生命周期：这些 watcher 将 chatStore 的响应式状态变化桥接到 useTtsPlayer 的命令式 API

// AI 开始思考时，给 Live2D 应用"思考"表情（半眼+手势变化）
watch(
  () => chatStore.aiThinking,
  (thinking) => {
    if (thinking && live2dRef.value && chatStore.showModel) {
      live2dRef.value.applyAvatarCue?.({ expression: 'thinking', durationMs: 8000 })
    }
  }
)

const handleModelClick = () => {
  if (live2dStatus.value !== 'ready') return
  chatStore.toggleChat()
  if (onboardingStep.value === 'model-tip' && chatStore.showChat) {
    nextOnboardingStep()
  }
}

const handleModelWheel = (event: WheelEvent) => {
  if (!chatStore.isExpanded) return
  event.preventDefault()
  aiChatRef.value?.scrollBodyBy?.(event.deltaY)
}

// 处理需要登录的操作：已登录直接执行，未登录弹出全局登录提示
const handleAuthRequired = (action: () => void, message?: string) => {
  requireAuth(action, () => authModalStore.show(message))
}

</script>

<template>
  <div class="main-layout">
    <TheHeader class="header" @open-search="searchModalRef?.open()" />
    <main class="main-content">
      <Banner class="banner" :class="{ 'banner--subheader': bannerStore.config.mode === 'subheader' }" />
      <Breadcrumb />
      <router-view />
      <div v-if="chatStore.showModel || chatStore.showChat" class="ai-content" :class="{ 'expanded': chatStore.isExpanded }">
        <div class="ai-box">
          <Live2d
            ref="live2dRef"
            @click="handleModelClick"
            @wheel="handleModelWheel"
            @speak-start="handleSpeakStart"
            @load-start="handleLive2dLoadStart"
            @ready="handleLive2dReady"
            @error="handleLive2dError"
            class="live2d"
            :class="{ 'centered': chatStore.isExpanded, 'is-hidden': !chatStore.showModel }"
            :interactive="true"
            :visible="chatStore.showModel"
          ></Live2d>
          <AiChat
            v-show="chatStore.showChat"
            ref="aiChatRef"
            class="ai-chat"
          ></AiChat>
        </div>
      </div>
    </main>
    <TheFooter />
    <BottomNavigation
      ref="bottomNavRef"
      @ai-chat-active="() => chatStore.toggleModel()"
      @auth-required="handleAuthRequired"
      @music-play="handleMusicPlay"
      @music-pause="handleMusicPause"
    ></BottomNavigation>
    <!-- 登录弹窗（状态来自全局 authModal store，路由守卫与页面操作共用） -->
    <LoginModal v-model:visible="authModalStore.visible" :message="authModalStore.message" />

    <!-- 全局搜索 -->
    <GlobalSearchModal ref="searchModalRef" />

    <!-- 新用户引导 -->
    <OnboardingGuide
      :live2d-status="live2dStatus"
      :chat-open="chatStore.showChat"
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
  min-height: 500px;
  @include respond(md) {
    min-height: 400px;
  }
  @include respond(sm) {
    min-height: 320px;
  }
}

/* 内容页页眉（subheader）：矮页眉，min-height 与 Banner 内部 transition 配合产生高度过渡动画 */
.banner--subheader {
  min-height: 280px;
  @include respond(md) {
    min-height: 240px;
  }
  @include respond(sm) {
    min-height: 200px;
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
  /* compact 模式不设 z-index：避免 .ai-chat 形成层叠上下文把 header/body/input 三层
     一起抬到 Live2d(30) 之上。不设 z-index 时三层各自 z-index（header/input 40、body 20）
     与 Live2d(30) 在 .ai-content 内同级比较：header/input 在模型之上可点击，
     body 在模型之下让看板娘浮在内容前。 */
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
  /* 展开时让 Live2d(z-index:30)显示在聊天窗之上（看板娘可见），
     Live2d 居中在底部，不会遮挡顶部 header 按钮 */
  z-index: 20;
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
