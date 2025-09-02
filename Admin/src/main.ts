import { createApp } from 'vue'
import { createPinia } from 'pinia'
import piniaPluginPersistedstate from 'pinia-plugin-persistedstate'
import './assets/styles/theme.css'
import './assets/styles/styles.css'
import App from './App.vue'
// 引入路由
import router from './router'
// 引入主题切换
import theme from './utils/theme.ts'
// 引入Ant Design Vue
import Antd from 'ant-design-vue'
import 'ant-design-vue/dist/reset.css'
// 引入用户store
import { useUserStore } from './stores/user'

// 引入全局错误处理
import { initGlobalErrorHandler, configureVueErrorHandler } from './utils/globalErrorHandler'

//初始化浅色主题
theme.init()

// 初始化全局错误处理
initGlobalErrorHandler()

const app = createApp(App)
const pinia = createPinia()

// 配置Pinia持久化插件
pinia.use(piniaPluginPersistedstate)

// 配置Vue错误处理
configureVueErrorHandler(app)

// 使用Pinia状态管理
app.use(pinia)
// 使用路由
app.use(router)
// 使用Ant Design Vue
app.use(Antd)

// 初始化用户状态
const userStore = useUserStore()
// 使用 Promise.catch 处理可能的初始化错误，避免未处理的Promise拒绝
userStore.initUserState().catch(error => {
  console.warn('用户状态初始化失败:', error)
  // 初始化失败不影响应用启动，只是用户需要重新登录
})

// 挂载应用
app.mount('#app')

