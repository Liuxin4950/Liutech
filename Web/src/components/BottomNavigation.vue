<!-- 推荐使用 SVG 图标（示例） -->
<template>
  <div class="bottom-nav">
    <div class="flex flex-col gap-16 mb-16">

      <!-- 滚动控制按钮:顶部显示去底部,滚动后显示回顶部,并带进度能量环 -->
      <button
        class="fab fab--progress"
        @click="onScrollControlClick"
        :aria-label="scrollProgress > 0 ? '回到顶部' : '回到底部'"
        :title="scrollProgress > 0 ? '回到顶部' : '回到底部'"
      >
        <!-- 能量环:进度 0 时完全隐藏,到底部时完整一圈 -->
        <svg
          class="fab__progress-ring"
          viewBox="0 0 54 54"
          width="54"
          height="54"
          :style="{ '--progress': scrollProgress }"
          aria-hidden="true"
        >
          <circle
            class="fab__progress-ring__track"
            cx="27"
            cy="27"
            r="25"
          />
          <circle
            class="fab__progress-ring__fill"
            cx="27"
            cy="27"
            r="25"
          />
        </svg>
        <span class="fab__icon">
          <!-- 顶部时向下,滚动后向上 -->
          <svg
            viewBox="0 0 24 24"
            width="20"
            height="20"
            fill="currentColor"
            class="fab__icon-svg"
            :class="{ 'fab__icon-svg--flipped': scrollProgress < 0.001 }"
          >
            <path d="M7.41 15.41L12 10.83l4.59 4.58L18 12l-6-6-6 6z"/>
          </svg>
        </span>
      </button>

      <!-- 发布文章（主操作） -->
      <button v-if="userStore.isAdmin"
        class="fab fab--primary"
        @click="goCreate"
        aria-label="发布文章"
        title="发布文章"
      >
        <!-- 更现代的笔触图标 -->
        <svg viewBox="0 0 24 24" width="24" height="24" fill="currentColor">
          <path d="M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25zM20.71 7.04c.39-.39.39-1.02 0-1.41l-2.34-2.34c-.39-.39-1.02-.39-1.41 0l-1.83 1.83 3.75 3.75 1.83-1.83z"/>
        </svg>
      </button>

      <!-- 我的文章 -->
      <button v-if="userStore.isAdmin"
        class="fab"
        @click="goMyPosts"
        aria-label="我的文章"
        title="我的文章"
      >
        <!-- 更直观的文档图标 -->
        <svg viewBox="0 0 24 24" width="20" height="20" fill="currentColor">
          <path d="M14 2H6c-1.1 0-1.99.9-1.99 2L4 20c0 1.1.89 2 1.99 2H18c1.1 0 2-.9 2-2V8l-6-6zm2 16H8v-2h8v2zm0-4H8v-2h8v2zm-3-7V3.5L18.5 9H13z"/>
        </svg>
      </button>

      <!-- 音乐播放器：折叠成圆形融入按钮列，点击展开并播放（驱动 Live2D 口型） -->
      <MusicCapsule
        ref="musicCapsuleRef"
        @play="(audio) => emit('music-play', audio)"
        @pause="() => emit('music-pause')"
      />

      <!-- 纳西妲看板娘入口（需要登录）第一期 -->
      <!-- 改为普通用户也可以使用，但是没有记忆功能  v-if="userStore.isLoggedIn" -->
      <button
        data-onboarding="ai-assistant"
        class="fab"
        @click="goAiChat"
        aria-label="纳西妲"
        title="纳西妲"
      >
        <!-- 纳西妲头像 -->

        <img class="fit" src="@/assets/aifile/纳西妲.webp" alt="">
      </button>

    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import MusicCapsule from '@/components/MusicCapsule.vue'

const router = useRouter()
const userStore = useUserStore()

// 局部节流:防止按钮频繁点击
const throttle = <T extends (...args: unknown[]) => void>(
  fn: T,
  wait = 300
): ((...args: Parameters<T>) => void) => {
  let last = 0
  return (...args) => {
    const now = Date.now()
    if (now - last >= wait) {
      last = now
      fn(...args)
    }
  }
}

// 页面滚动进度(0~1),用于回到顶部按钮的能量环
const scrollProgress = ref(0)
let rafId: number | null = null

const updateScrollProgress = () => {
  const docEl = document.documentElement
  const scrollTop = window.scrollY || docEl.scrollTop
  const maxScroll = docEl.scrollHeight - window.innerHeight
  if (maxScroll <= 0) {
    scrollProgress.value = 0
    return
  }
  // 到底部留 2px 容差,保证视觉上环完整闭合
  const progress = Math.min(Math.max(scrollTop / (maxScroll - 2), 0), 1)
  scrollProgress.value = progress
}

const onScroll = () => {
  if (rafId != null) return
  rafId = requestAnimationFrame(() => {
    updateScrollProgress()
    rafId = null
  })
}

onMounted(() => {
  updateScrollProgress()
  window.addEventListener('scroll', onScroll, { passive: true })
})

onUnmounted(() => {
  window.removeEventListener('scroll', onScroll)
  if (rafId != null) {
    cancelAnimationFrame(rafId)
  }
})
const emit = defineEmits<{
  (e: 'ai-chat-active'): void
  (e: 'auth-required', action: () => void, message?: string): void
  (e: 'music-play', audio: HTMLAudioElement): void
  (e: 'music-pause'): void
}>()

const musicCapsuleRef = ref<InstanceType<typeof MusicCapsule> | null>(null)

// 供 MainLayout 在 TTS 播放前挂起音乐、结束后恢复
defineExpose({
  isMusicPlaying: () => !!musicCapsuleRef.value?.isPlaying?.(),
  pauseMusic: () => musicCapsuleRef.value?.pauseMusic?.(),
  resumeMusic: () => musicCapsuleRef.value?.resumeMusic?.()
})

// 平滑滚动到顶部
const scrollToTop = () => {
  const duration = 500 // 动画持续时间（毫秒）
  const startPosition = window.pageYOffset || document.documentElement.scrollTop
  const startTime = performance.now()

  const animation = (currentTime: number) => {
    const elapsed = currentTime - startTime
    const progress = Math.min(elapsed / duration, 1)

    // 使用 ease-out 缓动函数
    const easeOut = 1 - Math.pow(1 - progress, 3)
    const newPosition = startPosition * (1 - easeOut)

    window.scrollTo(0, newPosition)

    if (progress < 1) {
      requestAnimationFrame(animation)
    }
  }

  requestAnimationFrame(animation)
}

// 平滑滚动到底部
const scrollToBottom = () => {
  const duration = 500 // 动画持续时间（毫秒）
  const startPosition = window.pageYOffset || document.documentElement.scrollTop
  const endPosition = document.documentElement.scrollHeight - window.innerHeight
  const distance = endPosition - startPosition
  const startTime = performance.now()

  const animation = (currentTime: number) => {
    const elapsed = currentTime - startTime
    const progress = Math.min(elapsed / duration, 1)

    // 使用 ease-in-out 缓动函数
    const easeInOut = progress < 0.5
      ? 4 * progress * progress * progress
      : 1 - Math.pow(-2 * progress + 2, 3) / 2

    const newPosition = startPosition + distance * easeInOut
    window.scrollTo(0, newPosition)

    if (progress < 1) {
      requestAnimationFrame(animation)
    }
  }

  requestAnimationFrame(animation)
}

const onScrollControlClick = throttle(() => {
  if (scrollProgress.value > 0) {
    scrollToTop()
  } else {
    scrollToBottom()
  }
})

const goCreate = throttle(() => {
  // 需要登录验证的操作
  emit('auth-required', () => router.push('/create'), '发布文章需要登录，请先登录您的账户。')
})

const goMyPosts = throttle(() => {
  // 需要登录验证的操作
  emit('auth-required', () => router.push('/my-posts'), '查看我的文章需要登录，请先登录您的账户。')
})

const goAiChat = throttle(() => {
  // 更改父组件的状态
  emit('ai-chat-active')
})

</script>



<style scoped lang="scss">
.bottom-nav {
  position: fixed;
  bottom: 0;
  right: 0;
  padding: 12px;
  z-index: 1000;
  // 让子元素的展开面板/播放列表能溢出到左侧和上方显示
  overflow: visible;
}

.fab {
  width: 50px;
  height: 50px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  cursor: pointer;
  position: relative;

  color: var(--text-main);
  background: var(--bg-card);
  border: 1px solid var(--border-soft);
  box-shadow: var(--shadow-sm);
  transition: all 0.2s ease-in-out;

  &:hover {
    background: var(--bg-hover);
    transform: translateY(-2px);
    box-shadow: var(--shadow-lg);
  }

  &--progress {
    // 能量环贴在按钮边缘,不额外占据布局空间
    overflow: visible;
  }

  &__icon {
    position: relative;
    z-index: 2;
    display: flex;
    align-items: center;
    justify-content: center;
  }

  &__progress-ring {
    position: absolute;
    top: 50%;
    left: 50%;
    width: 54px;
    height: 54px;
    transform: translate(-50%, -50%) rotate(-90deg);
    pointer-events: none;
    z-index: 1;

    // 进度 0 时完全不可见,滚动后才出现
    opacity: calc(1 * var(--progress, 0));
    transition: opacity 0.12s ease-out;

    &__track,
    &__fill {
      fill: none;
      stroke-width: 2.5;
      stroke-linecap: round;
    }

    &__track {
      stroke: transparent;
    }

    &__fill {
      stroke: var(--color-primary);
      // 周长 2 * PI * 25 ≈ 157.08
      stroke-dasharray: 157.08;
      stroke-dashoffset: calc(157.08 * (1 - var(--progress, 0)));
      transition: stroke-dashoffset 0.08s linear;
      filter: drop-shadow(0 0 2px rgba(var(--color-primary-rgb), 0.2));
    }
  }

  &__icon-svg {
    transition: transform 0.2s ease-in-out;

    &--flipped {
      transform: rotate(180deg);
    }
  }
}
</style>
