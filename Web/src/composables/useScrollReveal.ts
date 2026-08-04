import { onMounted, onUnmounted } from 'vue'

/**
 * 轻量级滚动显现
 * 给带有 .reveal 类的元素添加 .is-visible，触发 CSS 过渡
 * 支持异步加载后新增的 .reveal 元素
 * options.once = false 时：元素离开视口恢复隐藏，再次进入重新播放动画（默认 true 只播一次）
 */
export function useScrollReveal(selector = '.reveal', options?: IntersectionObserverInit & { once?: boolean }) {
  const { once = true, ...ioOptions } = options ?? {}
  let observer: IntersectionObserver | null = null
  let mutationObserver: MutationObserver | null = null
  const observed = new WeakSet<Element>()

  const revealAll = () => {
    document.querySelectorAll(selector).forEach(el => {
      if (!observed.has(el)) {
        el.classList.add('is-visible')
      }
    })
  }

  const observeElement = (el: Element) => {
    if (observed.has(el)) return
    observed.add(el)
    observer?.observe(el)
  }

  const observeAll = () => {
    document.querySelectorAll(selector).forEach(observeElement)
  }

  onMounted(() => {
    if (typeof window === 'undefined' || !('IntersectionObserver' in window)) {
      revealAll()
      return
    }

    observer = new IntersectionObserver((entries) => {
      entries.forEach(entry => {
        if (entry.isIntersecting) {
          entry.target.classList.add('is-visible')
          if (once) observer?.unobserve(entry.target)
        } else if (!once) {
          // 可重复模式：离开视口恢复隐藏，再次进入重新播放动画
          entry.target.classList.remove('is-visible')
        }
      })
    }, {
      threshold: 0.12,
      rootMargin: '0px 0px -40px 0px',
      ...ioOptions
    })

    // 初始观察
    observeAll()

    // 监听 DOM 变化，观察新增的 .reveal 元素
    mutationObserver = new MutationObserver(() => {
      observeAll()
    })

    mutationObserver.observe(document.body, {
      childList: true,
      subtree: true
    })
  })

  onUnmounted(() => {
    observer?.disconnect()
    mutationObserver?.disconnect()
  })
}
