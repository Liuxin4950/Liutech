<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import TheHeader from '../components/TheHeader.vue'
import TheFooter from '../components/TheFooter.vue'
import Banner from '@/components/Banner.vue'
import Breadcrumb from '@/components/Breadcrumb.vue'
import BottomNavigation from '@/components/BottomNavigation.vue'
import Live2d from '@/components/Live2d.vue'
// 全局页面加载（作者：刘鑫，修改时间：2025-08-26 16:01:05 +08:00）
import GlobalPageLoader from '../components/GlobalPageLoader.vue'

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
    // 如果不是首次加载，则不显示加载动画
    if (!isFirstLoad.value) {
      next()
      return
    }
    isFirstLoad.value = false
    next()
  })
})

</script>

<template>
  <div class="main-layout">
    <TheHeader />
    <main class="main-content">
      <Banner class="banner" />
      <Breadcrumb />
      <Live2d class="live2d"></Live2d>
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
.live2d{
  position: fixed;
  bottom: 0;
  right: 0;
  width: 400px;
  height: 400px;
  z-index: 10;
}
</style>