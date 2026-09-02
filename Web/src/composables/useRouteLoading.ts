import { computed, readonly, ref } from 'vue'

const pendingPath = ref('')
const pendingTitle = ref('')
const isInitialLoading = ref(true)
let initialNavigationFinished = false

/**
 * 路由状态必须在全局守卫开始时写入，而不是等新页面组件挂载。
 * 弱网下异步路由 chunk 尚未下载完成，旧页面仍会保留，这个状态负责给用户即时反馈。
 */
export function beginRouteLoading(path: string, title = '') {
  pendingPath.value = path
  pendingTitle.value = title
}

export function finishRouteLoading(path?: string) {
  // 较早的导航被新导航取代时，不允许旧导航结束新导航的加载提示。
  if (path && pendingPath.value && pendingPath.value !== path) return

  pendingPath.value = ''
  pendingTitle.value = ''

  if (!initialNavigationFinished) {
    initialNavigationFinished = true
    isInitialLoading.value = false
  }
}

export function useRouteLoading() {
  const isNavigating = computed(() => pendingPath.value !== '')

  return {
    pendingPath: readonly(pendingPath),
    pendingTitle: readonly(pendingTitle),
    isInitialLoading: readonly(isInitialLoading),
    isNavigating,
  }
}
