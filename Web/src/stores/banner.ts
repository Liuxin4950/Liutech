/**
 * 页面级 Banner 定制 store（无状态工具语义）
 * 用途：MainLayout 中 Banner 组件常驻，各页面进入时上传完整配置，组件按配置渲染。
 *       页面不调用时不产生任何影响（显示默认轮播）；store 不做隐式默认与推断。
 * 作者：刘鑫
 */
import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { Carousel } from '@/services/carousel'

// Banner 形态：hero=500px 大横幅（一级页/默认轮播）/ subheader=280px 页眉（详情页）
export type BannerMode = 'hero' | 'subheader'

// 完整定制配置：调用方必须传全字段（漏传编译报错），无隐式默认
export interface BannerConfig {
  slides: Carousel[]
  badgeText: string
  titleAs: 'h1' | 'h2'
  titleHighlight: string
  mode: BannerMode
}

// 初始空状态（customSlides 为空时 Banner 组件显示默认轮播）
const EMPTY_BANNER: BannerConfig = {
  slides: [],
  badgeText: '',
  titleAs: 'h2',
  titleHighlight: '',
  mode: 'hero'
}

export const useBannerStore = defineStore('banner', () => {
  const config = ref<BannerConfig>({ ...EMPTY_BANNER })

  // 页面调用时上传完整参数，store 纯赋值不推断
  const setBanner = (cfg: BannerConfig) => {
    config.value = { ...cfg }
  }

  // 恢复初始空状态（Banner 组件回到默认轮播展示）
  const resetBanner = () => {
    config.value = { ...EMPTY_BANNER }
  }

  return { config, setBanner, resetBanner }
})
