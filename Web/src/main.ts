import { createApp } from 'vue'
import { createPinia } from 'pinia'
import './assets/styles/theme.css'
import './assets/styles/styles.css'
import App from './App.vue'
// 引入路由
import router from './router'
// 引入主题切换
import theme from './utils/theme.ts'
// 引入用户状态管理
import { useUserStore } from './stores/user'
// 引入全局错误处理
import { initGlobalErrorHandler, configureVueErrorHandler } from './utils/globalErrorHandler'

//初始化浅色主题
theme.init()

// 初始化全局错误处理
initGlobalErrorHandler()

const app = createApp(App)
const pinia = createPinia()

// 配置Vue错误处理
configureVueErrorHandler(app)

// 使用Pinia状态管理
app.use(pinia)
// 使用路由
app.use(router)

// 挂载应用
app.mount('#app')

// 初始化用户状态
const userStore = useUserStore()
userStore.initUserState()
