<script setup lang="ts">
import { onMounted, ref } from 'vue'
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

const showLoader = ref(false)
const router = useRouter()

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

onMounted(() => {
  // 页面加载时立即显示加载动画
  showLoader.value = true
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
    console.log(to, from)
    // 如果不是首次加载，则不显示加载动画
    if (!isFirstLoad.value) {
      next()
      return
    }
    isFirstLoad.value = false
    next()
  })
})

const toggleChat = () => {
  showChat.value = !showChat.value
  if (!showChat.value) {
    // 关闭聊天框时，重置展开状态
    isExpanded.value = false
  }
}

// 处理聊天框展开
const handleExpandChat = () => {
  isExpanded.value = true
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
    <TheHeader />
    <main class="main-content">
      <Banner class="banner" />
      <Breadcrumb />

      <div v-if="showModel" class="ai-content" :class="{ 'expanded': isExpanded }">
        <div class="ai-box">
          <Live2d @click="toggleChat" class="live2d" :class="{ 'centered': isExpanded }"></Live2d>
          <AiChat v-show="showChat"  class="ai-chat" :expanded="isExpanded" @expand="handleExpandChat"></AiChat>
        </div>
      </div>
      <router-view />
    </main>
    <TheFooter />
    <BottomNavigation @ai-chat-active="handleModelStatusChange" @auth-required="handleAuthRequired"></BottomNavigation>
    <GlobalPageLoader :show="showLoader" />

    <!-- 登录弹窗 -->
    <LoginModal v-model:visible="showLoginModal" :message="loginMessage" />
  </div>
</template>

<style scoped>
.main-layout {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}

.main-content {
  flex: 1;
}

.banner {
  height: 400px;
}

.ai-content {
  width: 400px;
  height: 400px;
  position: fixed;
  bottom: 0;
  right: 0;
  z-index: 10;
  transition: all 0.4s cubic-bezier(0.4, 0, 0.2, 1);
}

.ai-content.expanded {
  width: 80vw;
  height: 90vh;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -45%);
  bottom: auto;
  right: auto;
}

.ai-box,.live2d {
  position: relative;
  width: 100%;
  height: 100%;
  transition: all 0.4s cubic-bezier(0.4, 0, 0.2, 1);
  z-index: 10;
}

.ai-chat {
  width: 400px;
  height: 400px;
  position: absolute;
  top: 0;
  left: 0;
  transform: translateY(-100px) translateX(-400px);
  z-index: 11;
  transition: all 0.4s cubic-bezier(0.4, 0, 0.2, 1);
}

/* 当聊天框展开时的样式 */
.ai-content.expanded .ai-chat {
  width: 100%;
  height: 100%;
  transform: none;
  z-index: 1001; /* 确保不被Live2d遮挡 */
}

/* Live2d居中样式 */
.live2d.centered {
  position: fixed;
  bottom: 0;
  left: 50%;
  transform: translate(-50%, 0%);
  z-index: 1002; /* 悬浮在聊天框上方 */
  width: 400px;
  height: 400px;
}


/* 响应式布局 */
@media screen and (max-width: 768px) {
  .banner {
    height: 200px;
  }
}
</style>
