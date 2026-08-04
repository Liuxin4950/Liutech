<template>
  <section class="banner-header" aria-label="站点横幅">
    <!-- 轮播幻灯片（无数据时自动回退到默认单张） -->
    <div
      v-for="(slide, index) in slides"
      :key="slide.id ?? index"
      class="banner-slide"
      :class="{ 'is-active': index === currentIndex, 'is-prev': isPrev(index), 'is-next': isNext(index) }"
    >
      <!-- 无图模式（内容页 subheader）：渐变底 + 光斑装饰，不渲染图片 -->
      <div v-if="slide.imageUrl" class="banner-image-wrapper">
        <img
          class="banner-image"
          :class="{ 'is-loaded': loadedIndex === index }"
          :src="slide.imageUrl"
          :alt="slide.title"
          loading="eager"
          fetchpriority="high"
          @load="onImageLoad(index)"
          @error="handleBannerImageError"
        >
        <div class="banner-overlay"></div>
      </div>
      <div v-else class="banner-decor"></div>

      <!-- 文字内容层（内容页由 banner store 注入页面标题/英文标签） -->
      <div class="banner-content">
        <div class="banner-content-inner">
          <transition name="slide-text" mode="out-in">
            <div v-if="index === currentIndex" :key="currentIndex" class="banner-text" :class="{ 'is-dark': !slide.imageUrl, 'is-fallback': isFallback }">
              <span v-if="slide.title" class="banner-title-badge">{{ badgeText }}</span>
              <component :is="config.titleAs" class="banner-title">{{ slide.title || '欢迎访问' }}<span v-if="bannerTitleHighlight" class="title-highlight">{{ bannerTitleHighlight }}</span></component>
              <p v-if="slide.description" class="banner-desc">{{ slide.description }}</p>
              <div v-if="slide.linkUrl" class="banner-actions">
                <a :href="slide.linkUrl" target="_blank" rel="noopener noreferrer" class="btn-primary banner-btn">
                  查看详情
                  <svg viewBox="0 0 24 24" width="16" height="16" fill="currentColor" aria-hidden="true">
                    <path d="M12 4l-1.41 1.41L16.17 11H4v2h12.17l-5.58 5.59L12 20l8-8z"/>
                  </svg>
                </a>
              </div>
            </div>
          </transition>
        </div>
      </div>
    </div>

    <!-- 切换按钮与指示器（多张图片时显示） -->
    <template v-if="slides.length > 1">
      <button class="banner-nav prev" @click="prevImage" aria-label="上一张">
        <svg viewBox="0 0 24 24" width="24" height="24" fill="currentColor" aria-hidden="true">
          <path d="M15.41 7.41L14 6l-6 6 6 6 1.41-1.41L10.83 12z"/>
        </svg>
      </button>
      <button class="banner-nav next" @click="nextImage" aria-label="下一张">
        <svg viewBox="0 0 24 24" width="24" height="24" fill="currentColor" aria-hidden="true">
          <path d="M8.59 16.59L10 18l6-6-6-6-1.41 1.41L13.17 12z"/>
        </svg>
      </button>
      <div class="banner-dots" role="tablist" aria-label="轮播指示器">
        <button
          v-for="(_, index) in slides"
          :key="index"
          class="dot"
          :class="{ active: index === currentIndex }"
          @click="goToIndex(index)"
          :aria-label="`切换到第 ${index + 1} 张`"
        ></button>
      </div>
    </template>

    <!-- 波浪动画（装饰性，跟随主题底色衔接下方内容） -->
    <svg class="waves" viewBox="0 24 150 28" preserveAspectRatio="none" aria-hidden="true">
      <defs>
        <path id="gentle-wave" d="M-160 44c30 0 58-18 88-18s 58 18 88 18 58-18 88-18 58 18 88 18 v44h-352z" />
      </defs>
      <g class="parallax">
        <use href="#gentle-wave" x="48" y="0" class="wave-1" />
        <use href="#gentle-wave" x="48" y="3" class="wave-2" />
        <use href="#gentle-wave" x="48" y="5" class="wave-3" />
        <use href="#gentle-wave" x="48" y="7" class="wave-4" />
      </g>
    </svg>
  </section>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted, onUnmounted } from 'vue'
import { storeToRefs } from 'pinia'
import CarouselService, { type Carousel } from '@/services/carousel'
import { useBannerStore } from '@/stores/banner'
import banner0 from '@/assets/image/banner/banner0.png'

const bannerStore = useBannerStore()
// storeToRefs 保持响应性：直接解构 store 属性拿到的是对象快照，setBanner 后不会更新
const { config } = storeToRefs(bannerStore)

/** 无轮播数据、无页面定制时的默认单张横幅（欢迎语） */
const fallbackSlide: Carousel = {
  title: '欢迎访问 ',
  description: '小鑫同学的技术博客',
  imageUrl: banner0,
  sortOrder: 0,
  status: 1
}

/** 默认展示态（页面未定制时的徽标与标签），属组件展示层，store 保持无状态 */
const DEFAULT_BADGE_TEXT = 'Welcome'
const badgeText = computed(() =>
  config.value.slides.length > 0 ? config.value.badgeText : DEFAULT_BADGE_TEXT
)

/** 默认欢迎语的标题高亮后缀（蓝色 LiuTech），页面定制态沿用各自 config.titleHighlight */
const FALLBACK_TITLE_HIGHLIGHT = 'LiuTech'
const bannerTitleHighlight = computed(() => {
  if (config.value.slides.length > 0) return config.value.titleHighlight
  // 仅纯默认态（无定制、无接口轮播）追加 LiuTech；接口轮播图用自身标题，不追加
  return carousels.value.length === 0 ? FALLBACK_TITLE_HIGHLIGHT : ''
})

/** 是否为默认欢迎语（非定制、无轮播数据），用于区分欢迎语的蓝/橙配色 */
const isFallback = computed(() =>
  config.value.slides.length === 0 && carousels.value.length === 0
)

const carousels = ref<Carousel[]>([])
const currentIndex = ref(0)
const loadedIndex = ref<number | null>(null)
let autoPlayTimer: number | null = null

// 数据优先级：页面定制（banner store）> 接口轮播 > 默认单张（仅 hero 模式）
// subheader 定制页数据未到时返回空列表：不显示 LiuTech 默认内容，只留渐变页眉背景
const slides = computed<Carousel[]>(() => {
  if (config.value.slides.length > 0) return config.value.slides
  if (carousels.value.length > 0) return carousels.value
  return config.value.mode === 'hero' ? [fallbackSlide] : []
})

const currentSlide = computed(() => slides.value[currentIndex.value])

const isPrev = (index: number) => {
  if (slides.value.length <= 1) return false
  return index === (currentIndex.value === 0 ? slides.value.length - 1 : currentIndex.value - 1)
}

const isNext = (index: number) => {
  if (slides.value.length <= 1) return false
  return index === (currentIndex.value === slides.value.length - 1 ? 0 : currentIndex.value + 1)
}

// 切换图片时重置加载状态，触发淡入（subheader 占位期 slides 为空，currentSlide 可能为 undefined，需可选链）
watch(() => currentSlide.value?.imageUrl, () => {
  loadedIndex.value = null
})

const onImageLoad = (index: number) => {
  loadedIndex.value = index
  preloadNext()
}

// 图片加载失败回退默认品牌图（与无封面时一致）
const handleBannerImageError = (e: Event) => {
  const img = e.target as HTMLImageElement
  if (img.src !== banner0) img.src = banner0
}

// 预加载下一张，减少切换时的等待
const preloadNext = () => {
  if (slides.value.length <= 1) return
  const nextIndex = (currentIndex.value + 1) % slides.value.length
  const nextUrl = slides.value[nextIndex]?.imageUrl
  if (nextUrl) {
    const img = new Image()
    img.src = nextUrl
  }
}

const prevImage = () => {
  if (slides.value.length <= 1) return
  currentIndex.value = currentIndex.value === 0
    ? slides.value.length - 1
    : currentIndex.value - 1
}

const nextImage = () => {
  if (slides.value.length <= 1) return
  currentIndex.value = currentIndex.value >= slides.value.length - 1
    ? 0
    : currentIndex.value + 1
}

const goToIndex = (index: number) => {
  if (index === currentIndex.value) return
  currentIndex.value = index
}

const startAutoPlay = () => {
  if (slides.value.length <= 1) return
  stopAutoPlay()
  autoPlayTimer = window.setInterval(() => {
    nextImage()
  }, 5000)
}

const stopAutoPlay = () => {
  if (autoPlayTimer !== null) {
    clearInterval(autoPlayTimer)
    autoPlayTimer = null
  }
}

const loadCarousels = async () => {
  try {
    const data = await CarouselService.getActiveCarousels()
    if (data) {
      carousels.value = data
      currentIndex.value = 0
      startAutoPlay()
    }
  } catch {
    // 加载轮播图失败时静默处理
  }
}

onMounted(() => {
  loadCarousels()
})

onUnmounted(() => {
  stopAutoPlay()
})
</script>

<style scoped lang="scss">
@use "@/assets/styles/tokens" as *;

/* ===== 基础结构与层级 ===== */
.banner-header {
  position: relative;
  width: 100%;
  min-height: 100%;
  overflow: hidden;
  background: linear-gradient(135deg, var(--bg-soft) 0%, var(--bg-element) 100%);
  /* 紧凑模式切换（600px ↔ 340px）过渡动画，由外层 min-height 驱动 */
  transition: min-height 0.4s ease;
}

.banner-slide {
  position: absolute;
  inset: 0;
  z-index: 1;
  opacity: 0;
  visibility: hidden;
  transition: opacity 0.8s ease, visibility 0.8s ease;

  &.is-active {
    z-index: 2;
    opacity: 1;
    visibility: visible;
  }
}

.banner-image-wrapper {
  position: absolute;
  inset: 0;
  overflow: hidden;
}

.banner-image {
  width: 100%;
  height: 100%;
  object-fit: cover;
  opacity: 0;
  transform: scale(1.05);
  transition: opacity 0.8s ease;

  &.is-loaded {
    opacity: 1;
    animation: kenBurns 8s ease forwards;
  }
}

.banner-overlay {
  position: absolute;
  inset: 0;
  z-index: 1;
  background: linear-gradient(180deg, rgba(0, 0, 0, 0.2) 0%, rgba(0, 0, 0, 0.45) 60%, rgba(0, 0, 0, 0.6) 100%);
}

/* 无图模式装饰层（subheader）：柔和渐变 + 径向光斑，替代图片承担视觉主体 */
.banner-decor {
  position: absolute;
  inset: 0;
  z-index: 1;
  background:
    radial-gradient(640px 320px at 88% -10%, rgba(var(--color-primary-rgb), 0.14), transparent 70%),
    radial-gradient(560px 300px at 8% 120%, rgba(224, 122, 95, 0.16), transparent 70%);
}

/* ===== 文字内容层 ===== */
.banner-content {
  position: absolute;
  inset: 0;
  z-index: 3;
  display: flex;
  align-items: center;
  padding: 80px 0;
}

/* 与页面 .content 容器同宽（1200px 居中），水平 padding 跟随全局 .content 响应式，保证与正文对齐 */
.banner-content-inner {
  width: 100%;
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 20px;
  box-sizing: border-box;

  @include respond(md) {
    padding: 0 16px;
  }

  @include respond(sm) {
    padding: 0 12px;
  }
}

.banner-text {
  max-width: 600px;
  color: #fff;

  /* 标题橙色高亮后缀词（两种底色下都保留组合语言） */
  .banner-title .title-highlight {
    color: var(--color-secondary);
  }

  /* 默认欢迎语（未定制且无轮播数据）：LiuTech 高亮蓝色，副标题橙色 */
  &.is-fallback {
    .title-highlight {
      color: var(--color-primary);
    }

    .banner-desc {
      color: var(--color-secondary);
    }
  }

  /* 无图模式：文字深色，与浅色渐变底搭配（标题高亮橙色延续页面标题组合语言） */
  &.is-dark {
    color: var(--text-title);

    .banner-title-badge {
      color: var(--color-primary);
      background: rgba(var(--color-primary-rgb), 0.1);
      border-color: rgba(var(--color-primary-rgb), 0.16);
    }

    .banner-title {
      color: var(--text-title);
      text-shadow: none;
    }

    .banner-desc {
      color: var(--text-subtle);
      text-shadow: none;
    }
  }
}

/* 徽标：参考 Gardyn subheader 的 crumb —— 白字 + 半透明白底药丸 */
.banner-title-badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 16px;
  margin-bottom: 18px;
  font-size: 0.72rem;
  font-weight: 600;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: rgba(255, 255, 255, 0.95);
  background: rgba(255, 255, 255, 0.14);
  border: 1px solid rgba(255, 255, 255, 0.22);
  border-radius: 30px;
  backdrop-filter: blur(8px);
  -webkit-backdrop-filter: blur(8px);
}

.banner-title {
  margin: 0 0 16px;
  font-size: clamp(2rem, 5vw, 3.5rem);
  font-weight: 800;
  line-height: 1.1;
  text-shadow: 0 2px 20px rgba(0, 0, 0, 0.3);
}

.banner-desc {
  margin: 0 0 28px;
  font-size: clamp(1rem, 2vw, 1.25rem);
  line-height: 1.6;
  opacity: 0.9;
  text-shadow: 0 1px 10px rgba(0, 0, 0, 0.3);
}

.banner-btn {
  padding: 12px 28px;
  font-size: 0.95rem;
}

/* ===== 文字进场动画 ===== */
.slide-text-enter-active,
.slide-text-leave-active {
  transition: all 0.5s ease;
}

.slide-text-enter-from {
  opacity: 0;
  transform: translateY(30px);
}

.slide-text-leave-to {
  opacity: 0;
  transform: translateY(-20px);
}

/* ===== 切换按钮与指示器 ===== */
.banner-nav {
  position: absolute;
  top: 50%;
  transform: translateY(-50%);
  z-index: 20;
  width: 48px;
  height: 48px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.15);
  color: white;
  cursor: pointer;
  backdrop-filter: blur(8px);
  transition: all 0.3s ease;

  &:hover {
    background: rgba(255, 255, 255, 0.35);
    transform: translateY(-50%) scale(1.1);
  }

  &.prev { left: 24px; }
  &.next { right: 24px; }
}

.banner-dots {
  position: absolute;
  bottom: 90px;
  left: 50%;
  transform: translateX(-50%);
  z-index: 20;
  display: flex;
  gap: 10px;
}

.dot {
  width: 10px;
  height: 10px;
  padding: 0;
  border: none;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.4);
  cursor: pointer;
  transition: all 0.3s ease;

  &:hover { background: rgba(255, 255, 255, 0.7); }

  &.active {
    background: #fff;
    transform: scale(1.3);
    box-shadow: 0 0 12px rgba(255, 255, 255, 0.5);
  }
}

/* ===== 波浪（装饰，衔接下方主题底色；hero 48px / subheader 40px） ===== */
.waves {
  position: absolute;
  bottom: 0;
  z-index: 10;
  width: 100%;
  height: 48px;
}

.wave-1 { fill: var(--bg-main); opacity: 0.7; }
.wave-2 { fill: var(--bg-main); opacity: 0.5; }
.wave-3 { fill: var(--bg-main); opacity: 0.3; }
.wave-4 { fill: var(--bg-main); opacity: 1; }

.parallax > use {
  animation: move-forever 25s cubic-bezier(0.55, 0.5, 0.45, 0.5) infinite;
}

.parallax > use:nth-child(1) { animation-delay: -2s; animation-duration: 7s; }
.parallax > use:nth-child(2) { animation-delay: -3s; animation-duration: 10s; }
.parallax > use:nth-child(3) { animation-delay: -4s; animation-duration: 13s; }
.parallax > use:nth-child(4) { animation-delay: -5s; animation-duration: 20s; }

/* ===== 内容页页眉（subheader）：矮、垂直居中左对齐，参考 Gardyn subheader =====
   无图时渐变底 + 光斑装饰，标题带橙色高亮后缀；有图时图片 + 白字 */
.banner-header.banner--subheader {
  min-height: 280px;
  background: linear-gradient(135deg, var(--bg-soft) 0%, var(--bg-element) 100%);

  .banner-text { max-width: 720px; }

  .banner-content {
    align-items: center;
    padding: 40px 0;
  }

  .banner-title {
    margin-bottom: 0;
    font-size: clamp(1.6rem, 3vw, 2.2rem);
  }

  .waves { height: 40px; }

  @include respond(md) {
    min-height: 240px;
  }

  @include respond(sm) {
    min-height: 200px;
    .banner-content { padding: 32px 0; }
  }
}

/* ===== 响应式 ===== */
@include respond(md) {
  .banner-content { padding: 60px 0; }

  .banner-nav { width: 40px; height: 40px; }
  .banner-nav.prev { left: 12px; }
  .banner-nav.next { right: 12px; }

  .banner-dots { bottom: 60px; }
}

@include respond(sm) {
  .banner-content {
    align-items: center;
    padding: 32px 0;
  }

  .banner-title { margin-bottom: 10px; }
  .banner-desc { margin-bottom: 20px; }
  .banner-dots { bottom: 48px; }
}

@keyframes kenBurns {
  0% { transform: scale(1.05); }
  100% { transform: scale(1.15); }
}

@keyframes move-forever {
  0% { transform: translate3d(-90px, 0, 0); }
  100% { transform: translate3d(85px, 0, 0); }
}
</style>
