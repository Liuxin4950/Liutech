<template>
    <div class="banner-header">
        <!-- 轮播图图片 -->
        <template v-if="carousels.length > 0">
            <a
                v-if="currentCarousel.linkUrl"
                :href="currentCarousel.linkUrl"
                target="_blank"
                class="banner-link"
            >
                <img
                    class="banner-image"
                    :src="currentCarousel.imageUrl"
                    :alt="currentCarousel.title"
                >
            </a>
            <img
                v-else
                class="banner-image"
                :src="currentCarousel.imageUrl"
                :alt="currentCarousel.title"
            >
        </template>
        <!-- 无轮播图时显示默认图片 -->
        <template v-else>
            <img class="banner-image" src="@/assets/image/banner/liuyin.jpeg" alt="Banner">
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
                <span
                    v-for="(_, index) in carousels"
                    :key="index"
                    class="dot"
                    :class="{ active: index === currentIndex }"
                    @click="currentIndex = index"
                ></span>
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
import { ref, computed, onMounted, onUnmounted } from 'vue'
import CarouselService, { type Carousel } from '@/services/carousel'

const carousels = ref<Carousel[]>([])
const currentIndex = ref(0)
let autoPlayTimer: number | null = null

const currentCarousel = computed(() => {
    if (carousels.value.length > 0) {
        return carousels.value[currentIndex.value]
    }
    return { title: '', imageUrl: '', linkUrl: '', sortOrder: 0, status: 1 }
})

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
        const res = await CarouselService.getActiveCarousels()
        if (res.code === 200 && res.data) {
            carousels.value = res.data
            currentIndex.value = 0
            startAutoPlay()
        }
    } catch (error) {
        console.error('加载轮播图失败:', error)
    }
}

onMounted(() => {
    loadCarousels()
})

onUnmounted(() => {
    stopAutoPlay()
})
</script>

<style scoped>
@use "@/assets/styles/tokens" as *;
.banner-header {
    width: 100%;
    height: 100%;
    position: relative;
    background: linear-gradient(60deg, var(--color-primary) 0%, var(--color-primary) 100%);
    overflow: hidden;
}
.banner-link {
    display: block;
    width: 100%;
    height: 100%;
}
.banner-image{
    width: 100%;
    height: 100%;
    object-fit: cover;
    transition: opacity 0.5s ease;
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
    background: rgba(255, 255, 255, 0.2);
    color: white;
    cursor: pointer;
    display: flex;
    align-items: center;
    justify-content: center;
    transition: all 0.3s ease;
    z-index: 20;
    backdrop-filter: blur(4px);
}
.banner-nav:hover {
    background: rgba(255, 255, 255, 0.4);
    transform: translateY(-50%) scale(1.1);
}
.banner-nav.prev {
    left: 20px;
}
.banner-nav.next {
    right: 20px;
}

/* 指示器 */
.banner-dots {
    position: absolute;
    bottom: 80px;
    left: 50%;
    transform: translateX(-50%);
    display: flex;
    gap: 8px;
    z-index: 20;
}
.dot {
    width: 10px;
    height: 10px;
    border-radius: 50%;
    background: rgba(255, 255, 255, 0.5);
    cursor: pointer;
    transition: all 0.3s ease;
}
.dot:hover {
    background: rgba(255, 255, 255, 0.8);
}
.dot.active {
    background: white;
    transform: scale(1.2);
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
.wave-1 {
    fill: var(--bg-main);
    opacity: 0.7;
}

.wave-2 {
    fill: var(--bg-main);
    opacity: 0.5;
}

.wave-3 {
    fill: var(--bg-main);
    opacity: 0.3;
}

.wave-4 {
    fill: var(--bg-main);
    opacity: 1;
}

.parallax>use {
    animation: move-forever 25s cubic-bezier(0.55, 0.5, 0.45, 0.5) infinite;
}

.parallax>use:nth-child(1) {
    animation-delay: -2s;
    animation-duration: 7s;
}

.parallax>use:nth-child(2) {
    animation-delay: -3s;
    animation-duration: 10s;
}

.parallax>use:nth-child(3) {
    animation-delay: -4s;
    animation-duration: 13s;
}

.parallax>use:nth-child(4) {
    animation-delay: -5s;
    animation-duration: 20s;
}

@keyframes move-forever {
    0% {
        transform: translate3d(-90px, 0, 0);
    }

    100% {
        transform: translate3d(85px, 0, 0);
    }
}

@include respond(md) {
    .banner-nav {
        width: 40px;
        height: 40px;
    }

    .banner-nav.prev {
        left: 10px;
    }

    .banner-nav.next {
        right: 10px;
    }

    .banner-dots {
        bottom: 60px;
    }

    .waves {
        height: 40px;
        min-height: 40px;
    }
}
</style>
