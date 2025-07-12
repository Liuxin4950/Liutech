import { createApp } from 'vue'
import './assets/styles/theme.css'
import App from './App.vue'
// 引入路由
import router from './router'
// 引入主题切换
import theme from './utils/theme.ts';
//初始化浅色主题
theme.init();

const app = createApp(App)

// 使用路由
app.use(router)

// 挂载应用
app.mount('#app')
