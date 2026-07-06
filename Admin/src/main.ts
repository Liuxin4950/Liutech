import { createApp } from 'vue'
import { createPinia } from 'pinia'
import piniaPluginPersistedstate from 'pinia-plugin-persistedstate'
import './assets/styles/tokens.css'
import './assets/styles/theme.css'
import './assets/styles/styles.css'
import './assets/styles/rich-text.css'
import './assets/styles/tinymce-dark.css'
import App from './App.vue'
// 引入路由
import router from './router'
// 引入主题切换
import theme from './utils/theme'
// 引入Ant Design Vue
import Antd from 'ant-design-vue'
import 'ant-design-vue/dist/reset.css'
// 引入用户store
import { useUserStore } from './stores/user'
// 引入全局错误处理
import { initGlobalErrorHandler, configureVueErrorHandler } from './utils/globalErrorHandler'

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

// 初始化主题（依赖 pinia，必须放在 app.use(pinia) 之后）
theme.init()

// 初始化用户状态
const userStore = useUserStore()
userStore.initUserState().catch(error => {
  console.warn('用户状态初始化失败:', error)
})

// 挂载应用
app.mount('#app')

