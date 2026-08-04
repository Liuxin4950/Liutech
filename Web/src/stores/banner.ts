/**
 * 页面级 Banner 定制 store
 * 用途：MainLayout 中 Banner 组件常驻，各页面可通过此 store 替换 Banner 内容（如图片/标题/徽标），
 *       不设置时 Banner 显示接口轮播数据（首页默认）。
 * 作者：刘鑫
 */
import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { Carousel } from '@/services/carousel'

// 默认徽标文字与标题标签（与 Banner 组件默认一致）
const DEFAULT_BADGE_TEXT = '精选'
const DEFAULT_TITLE_AS = 'h2'

export const useBannerStore = defineStore('banner', () => {
  // 自定义 banner 内容（非空时优先于接口轮播数据）
  const customSlides = ref<Carousel[]>([])
  // 徽标文字（如文章分类名）
  const badgeText = ref(DEFAULT_BADGE_TEXT)
  // 标题标签（文章详情页用 h1 保证 SEO）
  const titleAs = ref<'h1' | 'h2'>(DEFAULT_TITLE_AS)
  // 紧凑模式（文章详情页：更矮、文字底部对齐，贴近内容页定位）
  const compact = ref(false)

  // 设置自定义 banner 内容；slides 传空数组恢复默认轮播
  const setBanner = (slides: Carousel[], options?: { badgeText?: string; titleAs?: 'h1' | 'h2'; compact?: boolean }) => {
    customSlides.value = slides
    if (options?.badgeText) badgeText.value = options.badgeText
    if (options?.titleAs) titleAs.value = options.titleAs
    if (typeof options?.compact === 'boolean') compact.value = options.compact
  }

  // 恢复默认（接口轮播 + 精选徽标 + h2 + 非紧凑）
  const resetBanner = () => {
    customSlides.value = []
    badgeText.value = DEFAULT_BADGE_TEXT
    titleAs.value = DEFAULT_TITLE_AS
    compact.value = false
  }

  return { customSlides, badgeText, titleAs, compact, setBanner, resetBanner }
})
