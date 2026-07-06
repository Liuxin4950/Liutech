import { onMounted, onUnmounted, ref } from 'vue'

/**
 * 全局键盘快捷键系统
 * ---------------------------------------------------------------------
 * 支持三种绑定形态：
 * 1. 单键：'?' / 'Escape' / '/'
 * 2. 修饰组合：'ctrl+k' / 'meta+shift+p' / 'alt+enter'
 * 3. 序列键（vim 风格）：'g h' / 'g p' / 'g u'（两次按键，间隔 <1200ms）
 *
 * 特性：
 * - 在 input / textarea / [contenteditable] 里默认禁用（除非 shortcut.allowInInput=true）
 * - 修饰键前缀顺序无关：'ctrl+shift+k' === 'shift+ctrl+k'
 * - 序列键用空格分隔，只支持两键序列（覆盖 99% 场景）
 * - 组件卸载时自动清理监听
 */

export interface ShortcutBinding {
  key: string
  handler: (e: KeyboardEvent) => void
  description?: string
  /** 在输入框内也生效（默认 false） */
  allowInInput?: boolean
  /** 阻止默认行为（默认 true） */
  preventDefault?: boolean
}

/** 已注册的快捷键，供 ShortcutsHelp 面板列出 */
const registry = ref<ShortcutBinding[]>([])

/** 正常化 key：小写 + 修饰键排序 */
function normalizeKey(key: string): string {
  return key
    .toLowerCase()
    .split('+')
    .map((s) => s.trim())
    .sort((a, b) => {
      const order = ['ctrl', 'meta', 'alt', 'shift']
      const ai = order.indexOf(a)
      const bi = order.indexOf(b)
      if (ai !== -1 && bi !== -1) return ai - bi
      if (ai !== -1) return -1
      if (bi !== -1) return 1
      return a.localeCompare(b)
    })
    .join('+')
}

/** 从 KeyboardEvent 生成规范化字符串 */
function eventToKey(e: KeyboardEvent): string {
  const parts: string[] = []
  if (e.ctrlKey) parts.push('ctrl')
  if (e.metaKey) parts.push('meta')
  if (e.altKey) parts.push('alt')
  if (e.shiftKey) parts.push('shift')
  const k = e.key.length === 1 ? e.key.toLowerCase() : e.key.toLowerCase()
  // 只在非修饰键时添加
  if (!['control', 'meta', 'alt', 'shift'].includes(k)) parts.push(k)
  return normalizeKey(parts.join('+'))
}

function isTypingContext(target: EventTarget | null): boolean {
  if (!target || !(target instanceof HTMLElement)) return false
  const tag = target.tagName
  if (tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT') return true
  if (target.isContentEditable) return true
  return false
}

/** 序列键状态：记录第一键与时间戳 */
let sequenceFirst = ''
let sequenceExpiry = 0
const SEQUENCE_WINDOW_MS = 1200

/** 全局单例监听器 */
let listenerInstalled = false
const bindings: ShortcutBinding[] = []

function handleKeydown(e: KeyboardEvent) {
  const inputCtx = isTypingContext(e.target)
  const now = Date.now()

  // 只用主键（不含修饰）判断序列键的第二键
  const rawKey = e.key.length === 1 ? e.key.toLowerCase() : e.key.toLowerCase()
  const withMods = eventToKey(e)

  // 尝试序列匹配（仅当前有 pending first 键、且未过期、无修饰键）
  if (sequenceFirst && now < sequenceExpiry && !e.ctrlKey && !e.metaKey && !e.altKey) {
    const seq = `${sequenceFirst} ${rawKey}`
    const match = bindings.find((b) => normalizeKey(b.key.replace(/ /g, '__space__')).replace(/__space__/g, ' ') === seq)
    sequenceFirst = ''
    if (match) {
      if (!inputCtx || match.allowInInput) {
        if (match.preventDefault !== false) e.preventDefault()
        match.handler(e)
      }
      return
    }
  }

  // 单键 / 修饰组合匹配
  const match = bindings.find((b) => !b.key.includes(' ') && normalizeKey(b.key) === withMods)
  if (match) {
    if (inputCtx && !match.allowInInput) return
    if (match.preventDefault !== false) e.preventDefault()
    match.handler(e)
    return
  }

  // 未匹配：如果是单字符无修饰键，作为序列首键候选
  if (rawKey.length === 1 && !e.ctrlKey && !e.metaKey && !e.altKey && !inputCtx) {
    const anyStartsWith = bindings.some((b) => b.key.startsWith(rawKey + ' '))
    if (anyStartsWith) {
      sequenceFirst = rawKey
      sequenceExpiry = now + SEQUENCE_WINDOW_MS
    } else {
      sequenceFirst = ''
    }
  }
}

function installOnce() {
  if (listenerInstalled) return
  window.addEventListener('keydown', handleKeydown)
  listenerInstalled = true
}

/**
 * 注册一批快捷键，组件卸载时自动移除
 */
export function useShortcuts(shortcuts: ShortcutBinding[]) {
  onMounted(() => {
    installOnce()
    for (const s of shortcuts) {
      bindings.push(s)
      registry.value.push(s)
    }
  })
  onUnmounted(() => {
    for (const s of shortcuts) {
      const idx = bindings.indexOf(s)
      if (idx !== -1) bindings.splice(idx, 1)
      const ridx = registry.value.indexOf(s)
      if (ridx !== -1) registry.value.splice(ridx, 1)
    }
  })
}

/** 获取所有已注册的快捷键（供帮助面板消费） */
export function useShortcutRegistry() {
  return registry
}
