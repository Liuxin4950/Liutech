import { computed } from 'vue'
import zhCN from './locales/zh-CN'
import enUS from './locales/en-US'
import antdZhCN from 'ant-design-vue/es/locale/zh_CN'
import antdEnUS from 'ant-design-vue/es/locale/en_US'
import { useSettingsStore, type Locale } from '@/stores/settings'

/**
 * 极简 i18n：不引 vue-i18n，够用即可。
 * 只支持 `t('a.b.c')` 与占位符 `t('a.b.c', { n: 3 })`。
 */
type Messages = Record<string, any>

const dicts: Record<Locale, Messages> = {
  'zh-CN': zhCN,
  'en-US': enUS,
}

const antdLocales: Record<Locale, any> = {
  'zh-CN': antdZhCN,
  'en-US': antdEnUS,
}

function pickPath(dict: Messages, path: string): string | undefined {
  const parts = path.split('.')
  let cur: any = dict
  for (const p of parts) {
    if (cur == null) return undefined
    cur = cur[p]
  }
  return typeof cur === 'string' ? cur : undefined
}

function interpolate(tpl: string, params?: Record<string, string | number>): string {
  if (!params) return tpl
  return tpl.replace(/\{(\w+)\}/g, (_, k) => String(params[k] ?? `{${k}}`))
}

export function useI18n() {
  const settings = useSettingsStore()

  const locale = computed<Locale>(() => settings.locale)
  const antdLocale = computed(() => antdLocales[locale.value])

  function t(key: string, params?: Record<string, string | number>): string {
    const dict = dicts[locale.value] || dicts['zh-CN']
    const found = pickPath(dict, key)
    if (found) return interpolate(found, params)
    // 回退：先试中文，再返回 key
    const fallback = pickPath(dicts['zh-CN'], key)
    return fallback ? interpolate(fallback, params) : key
  }

  function setLocale(v: Locale) {
    settings.setLocale(v)
  }

  return { locale, antdLocale, t, setLocale }
}
