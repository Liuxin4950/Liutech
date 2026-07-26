import Lenis from "lenis"
import type { LenisOptions } from "lenis"

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