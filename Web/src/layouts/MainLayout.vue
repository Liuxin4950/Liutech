<script setup lang="ts">
import {onMounted, ref} from 'vue'
import {useRouter} from 'vue-router'
import TheHeader from '../components/TheHeader.vue'
import TheFooter from '../components/TheFooter.vue'
import Banner from '@/components/Banner.vue'
import Breadcrumb from '@/components/Breadcrumb.vue'
import BottomNavigation from '@/components/BottomNavigation.vue'
import Live2d from '@/components/Live2d.vue'
// 全局页面加载（作者：刘鑫，修改时间：2025-08-26 16:01:05 +08:00）
import GlobalPageLoader from '../components/GlobalPageLoader.vue'
import AiChat from "@/components/AiChat.vue";

const showLoader = ref(false)
const router = useRouter()

let timer: number | null = null
// 检查是否为首次访问（页面刷新或首次打开）
const isFirstLoad = ref(true)

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

//显示/隐藏
const showChat = ref(false)

const toggleChat = () =>{
  showChat.value = !showChat.value
}


const aiChatActive = ref(false)
// 子组件通过 $emit('status-change', true/false) 来通知父组件
const handleStatusChange = (val: boolean) => {
  aiChatActive.value = val
  // showChat.value = val
}

</script>

<template>
  <div class="main-layout">
    <TheHeader />
    <main class="main-content">
      <Banner class="banner" />
      <Breadcrumb />

      <div class="ai-content">
        <div class="ai-box">
        <Live2d @click="toggleChat" class="live2d"></Live2d>
          <AiChat v-show="showChat" :class="{ 'ai-chat-active': aiChatActive }" class="ai-chat"
                  @status-change="handleStatusChange"></AiChat>
        </div>
      </div>

      <router-view />
    </main>
    <TheFooter />
    <BottomNavigation></BottomNavigation>
    <GlobalPageLoader :show="showLoader" />
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
.banner{
  height: 300px;
}
.ai-content{
  width: 400px ;
  height: 400px ;
  position: fixed;
  bottom: 0;
  right: 0;
  z-index: 10;
}
.ai-box,.live2d{
  position: relative;
  width: 100%;
  height: 100%;
}

.ai-chat{
  position: absolute;
  top: 0;
  left: 0;
  transform: translateY(-50px);
  z-index: 11;
  transition: all 0.3s ease;
}

.ai-chat-active {
  position: absolute;
  top: auto;
  bottom: 0;
  left: 0;
  transform: translate(-350px);

  z-index: 11;
}


</style>
