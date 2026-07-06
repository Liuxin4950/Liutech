import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'
import { readFileSync } from 'fs'

// 构建时读取 package.json 版本号 + 生成构建时间
const pkg = JSON.parse(readFileSync(resolve(__dirname, 'package.json'), 'utf-8'))

// https://vite.dev/config/
export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src')
    }
  },
  define: {
    __APP_VERSION__: JSON.stringify(pkg.version || '0.0.0'),
    __BUILD_TIME__: JSON.stringify(new Date().toISOString()),
    __VUE_VERSION__: JSON.stringify(pkg.dependencies?.vue?.replace(/^\^/, '') || ''),
    __ANTDV_VERSION__: JSON.stringify(pkg.dependencies?.['ant-design-vue']?.replace(/^\^/, '') || ''),
    __ECHARTS_VERSION__: JSON.stringify(pkg.dependencies?.echarts?.replace(/^\^/, '') || ''),
    __VITE_VERSION__: JSON.stringify(pkg.devDependencies?.vite?.replace(/^\^/, '') || ''),
    __TYPESCRIPT_VERSION__: JSON.stringify(pkg.devDependencies?.typescript?.replace(/^~/, '').replace(/^\^/, '') || ''),
  },
  server: {
    port: 3001,
    open: true
  }
})
