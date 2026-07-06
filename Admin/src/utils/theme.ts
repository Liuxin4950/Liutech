import { computed, type ComputedRef } from 'vue'
import { useSettingsStore } from '@/stores/settings'

/**
 * 主题管理 shim
 * --------------------------------------------------------------------------
 * 保留旧 `theme.current.value / toggle() / init()` API，兼容 App.vue /
 * TheHeader.vue / TinyMCEEditor.vue 等历史引用。
 * 内部转发到 pinia settingsStore（真实主题状态在这里）。
 *
 * 迁移完成后可删除本文件，直接使用 useSettingsStore()。
 */

let _store: ReturnType<typeof useSettingsStore> | null = null
let _current: ComputedRef<'light' | 'dark'> | null = null

function getStore() {
  if (!_store) _store = useSettingsStore()
  return _store
}

const theme = {
  /** 当前生效主题（'light' | 'dark'）的响应式引用，兼容 `.value` 读取 */
  get current(): ComputedRef<'light' | 'dark'> {
    if (!_current) {
      _current = computed(() => (getStore().isDark ? 'dark' : 'light'))
    }
    return _current
  },

  /** 浅色/深色之间切换 */
  toggle() {
    getStore().toggleThemeMode()
  },

  /**
   * 初始化：settingsStore 构造时自动读取 localStorage 并应用。
   * 必须在 `app.use(pinia)` 之后调用。
   */
  init() {
    getStore()
  },
}

export default theme
