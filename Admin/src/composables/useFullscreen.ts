import { onMounted, onUnmounted, ref } from 'vue'

/**
 * 全屏切换：包装 Fullscreen API，跟踪 isFullscreen 状态
 */
export function useFullscreen() {
  const isFullscreen = ref(false)

  const check = () => {
    isFullscreen.value = !!document.fullscreenElement
  }

  async function toggle() {
    try {
      if (document.fullscreenElement) {
        await document.exitFullscreen()
      } else {
        await document.documentElement.requestFullscreen()
      }
    } catch {
      // 用户拒绝或环境不支持
    }
  }

  onMounted(() => {
    document.addEventListener('fullscreenchange', check)
    check()
  })
  onUnmounted(() => {
    document.removeEventListener('fullscreenchange', check)
  })

  return { isFullscreen, toggle }
}
