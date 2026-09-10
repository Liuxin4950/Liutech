import { createApp } from 'vue'
import { createPinia } from 'pinia'
import piniaPluginPersistedstate from 'pinia-plugin-persistedstate'
import { createHead } from '@vueuse/head'
import './assets/styles/theme.css'
import './assets/styles/styles.scss'
import './assets/styles/markdown.css'
import './assets/styles/rich-content.css'
import App from './App.vue'
// 引入路由
import router from './router'
// 引入主题切换
import theme from './utils/theme'
// 引入状态管理
import { useUserStore } from './stores/user'
import { useCategoryStore } from './stores/category'
import { useTagStore } from './stores/tag'
// 引入全局错误处理
import { initGlobalErrorHandler, configureVueErrorHandler } from './utils/globalErrorHandler'

//初始化浅色主题
theme.init()

// 初始化全局错误处理
initGlobalErrorHandler()

/**
 * 部署后"旧页面加载不到旧分包"的自愈处理
 *
 * 背景（2026-09-10 实际踩到）：Vite 每次构建都会给资源加内容哈希，部署新版本后旧文件名即失效。
 * 如果用户此刻仍停留在**旧页面**（SPA 不会自己重新请求 index.html），页面本身还能用，
 * 但任何**懒加载**的分包（路由组件、Live2d 等按需模块）都会 404 —— 表现为"某个功能突然失灵"，
 * 而已经在运行的部分（如音频播放）不受影响，非常难排查。当事人只能靠硬刷新恢复。
 *
 * 这里监听 Vite 的分包加载失败事件：首次失败自动刷新一次拿新版本；
 * 30 秒内不重复刷新，避免"服务器真故障"时陷入刷新循环。
 */
window.addEventListener('vite:preloadError', (event) => {
  event.preventDefault()
  const RELOAD_KEY = 'liutech:stale-chunk-reload-at'
  const lastReloadAt = Number(window.sessionStorage?.getItem(RELOAD_KEY) ?? 0)
  if (Date.now() - lastReloadAt < 30_000) {
    console.warn('[app] 分包加载失败，且刚刚已刷新过，不再重复刷新')
    return
  }
  try {
    window.sessionStorage?.setItem(RELOAD_KEY, String(Date.now()))
  } catch {
    // 隐私模式下 sessionStorage 可能不可用，退化为"本次直接刷新"
  }
  console.warn('[app] 检测到页面版本已过期（分包 404），自动刷新获取新版本')
  window.location.reload()
})

const app = createApp(App)
const pinia = createPinia()

// 配置Pinia持久化插件
const head = createHead()
pinia.use(piniaPluginPersistedstate)

// 配置Vue错误处理
configureVueErrorHandler(app)

// 使用Pinia状态管理
app.use(pinia)
// 使用路由
app.use(router)
app.use(head)

// 挂载应用
app.mount('#app')

// 初始化状态
const userStore = useUserStore()
const categoryStore = useCategoryStore()
const tagStore = useTagStore()

// 初始化用户状态
userStore.initUserState()

// 初始化分类和标签数据
categoryStore.initCategories()
tagStore.initTags()
