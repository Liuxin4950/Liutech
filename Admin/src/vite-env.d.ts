/// <reference types="vite/client" />

declare module '*.vue' {
  import type { DefineComponent } from 'vue'
  const component: DefineComponent<{}, {}, any>
  export default component
}

// vite.config.ts define 注入的构建时常量
declare const __APP_VERSION__: string
declare const __BUILD_TIME__: string
declare const __VUE_VERSION__: string
declare const __ANTDV_VERSION__: string
declare const __ECHARTS_VERSION__: string
declare const __VITE_VERSION__: string
declare const __TYPESCRIPT_VERSION__: string