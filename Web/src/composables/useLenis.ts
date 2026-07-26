import Lenis from "lenis"
import type { LenisOptions } from "lenis"
import { onUnmounted, watch, type Ref } from "vue"

// 全局 Lenis 平滑滚动实例（模块级单例，供 MainLayout 初始化、router scrollBehavior 复用）
let lenisInstance: Lenis | null = null
let rafId: number | null = null

/**
 * 初始化全局 Lenis 平滑滚动。
 * - lerp 0.1：平滑系数，越小越丝滑但略有延迟，0.1 兼顾丝滑与响应
 * - 不传 wrapper/content：使用 window 滚动，position:sticky/fixed 不受影响
 * - Lenis 会自动跳过 defaultPrevented 的 wheel 事件，故 Live2D 模型区域
 *   (handleModelWheel 已 preventDefault) 的滚动转发不会被 Lenis 接管
 */
export function initLenis(options?: LenisOptions): Lenis {
  if (lenisInstance) return lenisInstance

  lenisInstance = new Lenis({
    lerp: 0.1,
    smoothWheel: true,
    wheelMultiplier: 1,
    touchMultiplier: 1.5,
    ...options
  })

  const raf = (time: number) => {
    lenisInstance?.raf(time)
    rafId = requestAnimationFrame(raf)
  }
  rafId = requestAnimationFrame(raf)

  return lenisInstance
}

/** 销毁 Lenis 实例并取消 rAF 循环 */
export function destroyLenis(): void {
  if (rafId !== null) {
    cancelAnimationFrame(rafId)
    rafId = null
  }
  lenisInstance?.destroy()
  lenisInstance = null
}

/** 获取当前 Lenis 实例（未初始化或已销毁时为 null） */
export function getLenis(): Lenis | null {
  return lenisInstance
}

/**
 * 在指定元素上创建嵌套 Lenis 实例，让内部可滚动容器也拥有平滑惯性。
 *
 * 配合外层（window）Lenis 的 data-lenis-prevent：外层跳过该容器，内层接管，
 * 两者各管各的滚动，不冲突。
 *
 * 用 watch(targetRef) 而非 onMounted：支持 v-if 动态挂载的容器（侧栏/弹窗），
 * 元素出现即创建、消失即销毁。
 *
 * Lenis 用原生 scrollTop（wrapper.scrollTo({top})），不用 transform，且监听
 * scrollend 同步 targetScroll=actualScroll，故外部直接设 scrollTop（如
 * scrollToBottom 的 scrollTop=scrollHeight、scrollBodyBy 的 scrollTop+=deltaY）
 * 不会被嵌套实例的惯性覆盖。
 */
export function useNestedLenis(targetRef: Ref<HTMLElement | null>, options?: LenisOptions) {
  let innerLenis: Lenis | null = null
  let innerRafId: number | null = null

  const create = (el: HTMLElement) => {
    if (innerLenis) return
    innerLenis = new Lenis({
      wrapper: el,
      content: el,
      lerp: 0.1,
      smoothWheel: true,
      wheelMultiplier: 1,
      ...options
    })
    const raf = (time: number) => {
      innerLenis?.raf(time)
      innerRafId = requestAnimationFrame(raf)
    }
    innerRafId = requestAnimationFrame(raf)
  }

  const destroyInner = () => {
    if (innerRafId !== null) {
      cancelAnimationFrame(innerRafId)
      innerRafId = null
    }
    innerLenis?.destroy()
    innerLenis = null
  }

  watch(
    targetRef,
    (el) => {
      destroyInner()
      if (el) create(el)
    },
    { immediate: true }
  )

  onUnmounted(destroyInner)
}