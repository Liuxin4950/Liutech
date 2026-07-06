import { defineStore } from 'pinia'
import { computed, ref, watch } from 'vue'

/**
 * 全局 UI 偏好：主题、密度、侧栏折叠首选项。
 * 主题切换通过 <html data-lt-theme> 属性驱动 CSS 变量重写，
 * 同时同步 <html class="dark"> 以兼容旧代码里的 .dark 选择器。
 */
export type ThemeMode = 'light' | 'dark' | 'auto'
export type TableSize = 'small' | 'middle' | 'large'
export type Locale = 'zh-CN' | 'en-US'

const THEME_KEY = 'lt-theme-mode'
const TABLE_SIZE_KEY = 'lt-table-size'
const SIDEBAR_KEY = 'lt-sidebar-collapsed'
const LOCALE_KEY = 'lt-locale'

function readThemeMode(): ThemeMode {
  try {
    const saved = localStorage.getItem(THEME_KEY) as ThemeMode | null
    if (saved === 'light' || saved === 'dark' || saved === 'auto') return saved
    const legacy = localStorage.getItem('theme')
    if (legacy === 'light' || legacy === 'dark') return legacy
  } catch { /* ignore */ }
  return 'light'
}

function readTableSize(): TableSize {
  try {
    const v = localStorage.getItem(TABLE_SIZE_KEY) as TableSize | null
    if (v === 'small' || v === 'middle' || v === 'large') return v
  } catch { /* ignore */ }
  return 'middle'
}

function readSidebarCollapsed(): boolean {
  try {
    return localStorage.getItem(SIDEBAR_KEY) === '1'
  } catch { return false }
}

function readLocale(): Locale {
  try {
    const v = localStorage.getItem(LOCALE_KEY) as Locale | null
    if (v === 'zh-CN' || v === 'en-US') return v
  } catch { /* ignore */ }
  // 默认根据浏览器语言
  if (typeof navigator !== 'undefined' && /^en/i.test(navigator.language)) return 'en-US'
  return 'zh-CN'
}

function systemPrefersDark(): boolean {
  if (typeof window === 'undefined' || !window.matchMedia) return false
  return window.matchMedia('(prefers-color-scheme: dark)').matches
}

function applyTheme(effective: 'light' | 'dark') {
  const el = document.documentElement
  el.setAttribute('data-lt-theme', effective)
  if (effective === 'dark') el.classList.add('dark')
  else el.classList.remove('dark')
  el.style.colorScheme = effective
}

export const useSettingsStore = defineStore('settings', () => {
  const themeMode = ref<ThemeMode>(readThemeMode())
  const systemDark = ref(systemPrefersDark())
  const tableSize = ref<TableSize>(readTableSize())
  const sidebarCollapsed = ref<boolean>(readSidebarCollapsed())
  const locale = ref<Locale>(readLocale())

  const isDark = computed(() =>
    themeMode.value === 'dark' || (themeMode.value === 'auto' && systemDark.value)
  )

  function setThemeMode(mode: ThemeMode) { themeMode.value = mode }
  function cycleThemeMode() {
    themeMode.value =
      themeMode.value === 'light' ? 'dark' :
      themeMode.value === 'dark' ? 'auto' : 'light'
  }
  function toggleThemeMode() {
    themeMode.value = isDark.value ? 'light' : 'dark'
  }

  function setTableSize(v: TableSize) { tableSize.value = v }
  function setSidebarCollapsed(v: boolean) { sidebarCollapsed.value = v }
  function setLocale(v: Locale) { locale.value = v }

  // 主题：写 DOM + localStorage
  watch([themeMode, systemDark], ([mode]) => {
    applyTheme(isDark.value ? 'dark' : 'light')
    try { localStorage.setItem(THEME_KEY, mode) } catch { /* ignore */ }
  }, { immediate: true })

  watch(tableSize, (v) => {
    try { localStorage.setItem(TABLE_SIZE_KEY, v) } catch { /* ignore */ }
  })

  watch(sidebarCollapsed, (v) => {
    try { localStorage.setItem(SIDEBAR_KEY, v ? '1' : '0') } catch { /* ignore */ }
  })

  watch(locale, (v) => {
    try { localStorage.setItem(LOCALE_KEY, v) } catch { /* ignore */ }
    document.documentElement.lang = v
  }, { immediate: true })

  if (typeof window !== 'undefined' && window.matchMedia) {
    const mql = window.matchMedia('(prefers-color-scheme: dark)')
    const listener = (e: MediaQueryListEvent) => { systemDark.value = e.matches }
    mql.addEventListener?.('change', listener)
  }

  return {
    themeMode,
    isDark,
    tableSize,
    sidebarCollapsed,
    locale,
    setThemeMode,
    cycleThemeMode,
    toggleThemeMode,
    setTableSize,
    setSidebarCollapsed,
    setLocale,
  }
})
