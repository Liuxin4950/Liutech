<template>
    <div class="banner-header">
        <!-- 轮播图图片 -->
        <template v-if="carousels.length > 0">
            <div
                v-for="(slide, index) in carousels"
                :key="slide.id || index"
                class="banner-slide"
                :class="{ 'is-active': index === currentIndex, 'is-prev': isPrev(index), 'is-next': isNext(index) }"
            >
                <div class="banner-image-wrapper">
                    <img
                        class="banner-image"
                        :class="{ 'is-loaded': loadedIndex === index }"
                        :src="slide.imageUrl"
                        :alt="slide.title"
                        loading="eager"
                        fetchpriority="high"
                        @load="onImageLoad(index)"
                        @error="handleImageError"
                    >
                    <div class="banner-overlay"></div>
                </div>

                <!-- Hero 内容层 -->
                <div class="banner-content">
                    <div class="banner-content-inner">
                        <transition name="slide-text" mode="out-in">
                            <div v-if="index === currentIndex" :key="currentIndex" class="banner-text">
                                <span v-if="slide.title" class="banner-title-badge">精选</span>
                                <h2 class="banner-title">{{ slide.title || 'LiuTech' }}</h2>
                                <p v-if="slide.description" class="banner-desc">{{ slide.description }}</p>
                                <div v-if="slide.linkUrl" class="banner-actions">
                                    <a :href="slide.linkUrl" target="_blank" rel="noopener noreferrer" class="btn-primary banner-btn">
                                        查看详情
                                        <svg viewBox="0 0 24 24" width="16" height="16" fill="currentColor">
                                            <path d="M12 4l-1.41 1.41L16.17 11H4v2h12.17l-5.58 5.59L12 20l8-8z"/>
                                        </svg>
                                    </a>
                                </div>
                            </div>
                        </transition>
                    </div>
                </div>
            </div>
        </template>

        <!-- 无轮播图时显示默认图片 -->
        <template v-else>
            <div class="banner-slide is-active">
                <div class="banner-image-wrapper">
                    <img
                        class="banner-image is-loaded"
                        src="@/assets/image/banner/banner0.png"
                        alt="Banner"
                        @error="handleImageError"
                    >
                    <div class="banner-overlay"></div>
                </div>
                <div class="banner-content">
                    <div class="banner-content-inner">
                        <div class="banner-text">
                            <h2 class="banner-title">LiuTech</h2>
                            <p class="banner-desc">全栈工程师的技术博客</p>
                        </div>
                    </div>
                </div>
            </div>
        </template>

        <!-- 切换按钮（多张图片时显示） -->
        <template v-if="carousels.length > 1">
            <button class="banner-nav prev" @click="prevImage" aria-label="上一张">
                <svg viewBox="0 0 24 24" width="24" height="24" fill="currentColor">
                    <path d="M15.41 7.41L14 6l-6 6 6 6 1.41-1.41L10.83 12z"/>
                </svg>
            </button>
            <button class="banner-nav next" @click="nextImage" aria-label="下一张">
                <svg viewBox="0 0 24 24" width="24" height="24" fill="currentColor">
                    <path d="M8.59 16.59L10 18l6-6-6-6-1.41 1.41L13.17 12z"/>
                </svg>
            </button>
            <!-- 指示器 -->
            <div class="banner-dots">
                <button
                    v-for="(_, index) in carousels"
                    :key="index"
                    class="dot"
                    :class="{ active: index === currentIndex }"
                    @click="goToIndex(index)"
                    :aria-label="`切换到第 ${index + 1} 张`"
                ></button>
            </div>
        </template>

        <!-- 波浪动画 -->
        <div class="wave-container">
            <svg class="waves" xmlns="http://www.w3.org/2000/svg" viewBox="0 24 150 28" preserveAspectRatio="none">
                <defs>
                     <path id="gentle-wave"
                        d="M-160 44c30 0 58-18 88-18s 58 18 88 18 58-18 88-18 58 18 88 18 v44h-352z" />
                </defs>
                <g class="parallax">
                    <use xlink:href="#gentle-wave" x="48" y="0" class="wave-1" />
                    <use xlink:href="#gentle-wave" x="48" y="3" class="wave-2" />
                    <use xlink:href="#gentle-wave" x="48" y="5" class="wave-3" />
                    <use xlink:href="#gentle-wave" x="48" y="7" class="wave-4" />
                </g>
            </svg>
        </div>
    </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted, onUnmounted } from 'vue'
import CarouselService, { type Carousel } from '@/services/carousel'
import { handleImageError } from '@/composables/useImageFallback'

const carousels = ref<Carousel[]>([])
const currentIndex = ref(0)
const loadedIndex = ref<number | null>(null)
let autoPlayTimer: number | null = null

const currentCarousel = computed(() => {
    if (carousels.value.length > 0) {
        return carousels.value[currentIndex.value]
    }
    return { title: '', imageUrl: '', linkUrl: '', description: '', sortOrder: 0, status: 1 }
})

const isPrev = (index: number) => {
    if (carousels.value.length <= 1) return false
    return index === (currentIndex.value === 0 ? carousels.value.length - 1 : currentIndex.value - 1)
}

const isNext = (index: number) => {
    if (carousels.value.length <= 1) return false
    return index === (currentIndex.value === carousels.value.length - 1 ? 0 : currentIndex.value + 1)
}

// 切换图片时重置加载状态，触发淡入
watch(() => currentCarousel.value.imageUrl, () => {
    loadedIndex.value = null
})

const onImageLoad = (index: number) => {
    loadedIndex.value = index
    preloadNext()
}

// 预加载下一张，减少切换时的等待
const preloadNext = () => {
    if (carousels.value.length <= 1) return
    const nextIndex = (currentIndex.value + 1) % carousels.value.length
    const nextUrl = carousels.value[nextIndex]?.imageUrl
    if (nextUrl) {
        const img = new Image()
        img.src = nextUrl
    }
}

const prevImage = () => {
    if (carousels.value.length <= 1) return
    currentIndex.value = currentIndex.value === 0
        ? carousels.value.length - 1
        : currentIndex.value - 1
}

const nextImage = () => {
    if (carousels.value.length <= 1) return
    currentIndex.value = currentIndex.value >= carousels.value.length - 1
        ? 0
        : currentIndex.value + 1
}

const goToIndex = (index: number) => {
    if (index === currentIndex.value) return
    currentIndex.value = index
}

const startAutoPlay = () => {
    if (carousels.value.length <= 1) return
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
.banner-header {
    width: 100%;
    height: 100%;
    position: relative;
    background: linear-gradient(135deg, var(--bg-soft) 0%, var(--bg-element) 100%);
    overflow: hidden;
}

.banner-slide {
    position: absolute;
    inset: 0;
    opacity: 0;
    visibility: hidden;
    transition: opacity 0.8s ease, visibility 0.8s ease;
    z-index: 1;
}

.banner-slide.is-active {
    opacity: 1;
    visibility: visible;
    z-index: 2;
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
}

.banner-image.is-loaded {
    opacity: 1;
    animation: kenBurns 8s ease forwards;
}

.banner-overlay {
    position: absolute;
    inset: 0;
    background: linear-gradient(180deg, rgba(0, 0, 0, 0.2) 0%, rgba(0, 0, 0, 0.45) 60%, rgba(0, 0, 0, 0.6) 100%);
    z-index: 1;
}

.banner-content {
    position: absolute;
    inset: 0;
    z-index: 3;
    display: flex;
    align-items: center;
    padding: 80px 40px;
}

.banner-content-inner {
    width: 100%;
    max-width: 1200px;
    margin: 0 auto;
}

.banner-text {
    max-width: 600px;
    color: #fff;
}

.banner-title-badge {
    display: inline-flex;
    align-items: center;
    padding: 4px 14px;
    margin-bottom: 16px;
    font-size: 0.75rem;
    font-weight: 700;
    letter-spacing: 0.08em;
    text-transform: uppercase;
    color: #fff;
    background: var(--color-secondary);
    border-radius: 30px;
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

/* 文字进场动画 */
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

/* 导航按钮 */
.banner-nav {
    position: absolute;
    top: 50%;
    transform: translateY(-50%);
    width: 48px;
    height: 48px;
    border: none;
    border-radius: 50%;
    background: rgba(255, 255, 255, 0.15);
    color: white;
    cursor: pointer;
    display: flex;
    align-items: center;
    justify-content: center;
    transition: all 0.3s ease;
    z-index: 20;
    backdrop-filter: blur(8px);
}

.banner-nav:hover {
    background: rgba(255, 255, 255, 0.35);
    transform: translateY(-50%) scale(1.1);
}

.banner-nav.prev {
    left: 24px;
}

.banner-nav.next {
    right: 24px;
}

/* 指示器 */
.banner-dots {
    position: absolute;
    bottom: 90px;
    left: 50%;
    transform: translateX(-50%);
    display: flex;
    gap: 10px;
    z-index: 20;
}

.dot {
    width: 10px;
    height: 10px;
    border-radius: 50%;
    background: rgba(255, 255, 255, 0.4);
    cursor: pointer;
    transition: all 0.3s ease;
    border: none;
    padding: 0;
}

.dot:hover {
    background: rgba(255, 255, 255, 0.7);
}

.dot.active {
    background: #fff;
    transform: scale(1.3);
    box-shadow: 0 0 12px rgba(255, 255, 255, 0.5);
}

.waves {
    width: 100%;
    position: absolute;
    bottom: 0;
    height: 10vh;
    min-height: 100px;
    max-height: 150px;
    z-index: 10;
}

/* 波浪颜色适配主题 */
.wave-1 { fill: var(--bg-main); opacity: 0.7; }
.wave-2 { fill: var(--bg-main); opacity: 0.5; }
.wave-3 { fill: var(--bg-main); opacity: 0.3; }
.wave-4 { fill: var(--bg-main); opacity: 1; }

.parallax>use {
    animation: move-forever 25s cubic-bezier(0.55, 0.5, 0.45, 0.5) infinite;
}

.parallax>use:nth-child(1) { animation-delay: -2s; animation-duration: 7s; }
.parallax>use:nth-child(2) { animation-delay: -3s; animation-duration: 10s; }
.parallax>use:nth-child(3) { animation-delay: -4s; animation-duration: 13s; }
.parallax>use:nth-child(4) { animation-delay: -5s; animation-duration: 20s; }

@keyframes move-forever {
    0% { transform: translate3d(-90px, 0, 0); }
    100% { transform: translate3d(85px, 0, 0); }
}

@keyframes kenBurns {
    0% { transform: scale(1.05); }
    100% { transform: scale(1.15); }
}

@include respond(md) {
    .banner-content {
        padding: 60px 24px;
    }

    .banner-nav {
        width: 40px;
        height: 40px;
    }

    .banner-nav.prev { left: 12px; }
    .banner-nav.next { right: 12px; }

    .banner-dots {
        bottom: 70px;
    }

    .waves {
        height: 40px;
        min-height: 40px;
    }
}

@include respond(sm) {
    .banner-content {
        padding: 40px 16px;
        align-items: flex-end;
        padding-bottom: 100px;
    }

    .banner-title {
        margin-bottom: 10px;
    }

    .banner-desc {
        margin-bottom: 20px;
    }

    .banner-dots {
        bottom: 60px;
    }
}
</style>
