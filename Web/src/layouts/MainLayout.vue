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
import { useBannerStore } from '@/stores/banner'
import { useOnboarding } from '@/composables/useOnboarding'
import { useTtsPlayer } from '@/composables/useTtsPlayer'
import OnboardingGuide from '@/components/OnboardingGuide.vue'
import { initLenis, destroyLenis } from '@/composables/useLenis'

const showLoader = ref(false)
const router = useRouter()
const route = useRoute()

let timer: number | null = null
// 检查是否为首次访问（页面刷新或首次打开）
const isFirstLoad = ref(true)

// 登录弹窗控制
const showLoginModal = ref(false)
const loginMessage = ref('')

const chatStore = useChatStore()
const bannerStore = useBannerStore()

// Banner 定制集中管理：定制页进入时自行 setBanner 覆盖，这里只在进入非定制页时恢复默认轮播。
// 不用"页面卸载时 resetBanner"——路由切换时旧页面的 reset 可能晚于新页面的 set 执行，产生竞态。
const CUSTOM_BANNER_PATHS = ['/post/', '/category-detail/', '/tags', '/series', '/categories', '/archive', '/about']
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
const { initOnboarding } = useOnboarding()

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
  chatStore.showModel = true
}

onMounted(() => {
  initLenis()

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
  destroyLenis()
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
  chatStore.toggleChat()
}

const handleModelWheel = (event: WheelEvent) => {
  if (!chatStore.isExpanded) return
  event.preventDefault()
  aiChatRef.value?.scrollBodyBy?.(event.deltaY)
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
