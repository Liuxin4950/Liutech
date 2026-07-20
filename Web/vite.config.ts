import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'

// https://vite.dev/config/
export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src')
    }
  },
  server: {
    port: 3000,
    open: true,
    proxy: {
      // 图片等静态资源走相对路径 /uploads/...，开发环境代理到后端
      '/uploads': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  }
})
