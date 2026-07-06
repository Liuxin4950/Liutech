import { computed, ref, watch } from 'vue'

/**
 * 表格列偏好：用户可自定义显示/隐藏列、调整顺序，写入 localStorage。
 * ---------------------------------------------------------------------
 * 用法：
 *   const { prefColumns, columnPrefs, resetPrefs } = useTableColumnPrefs(
 *     'posts',                     // 唯一 key（一般用页面名）
 *     columns,                     // 原始 columns（响应式或普通数组均可）
 *     { alwaysVisible: ['action'] } // 强制常驻列（如操作列）
 *   )
 *   // 模板里把 :columns="columns" 改成 :columns="prefColumns"
 *
 *   // 弹出配置面板：给 <TableColumnSettings /> 传 v-model:prefs="columnPrefs"
 *
 * 存储结构：{ hidden: string[], order: string[] }
 * - hidden：被隐藏的列 dataIndex / key
 * - order：显式排序（不在此数组的列按原顺序排在末尾）
 */

export interface ColumnPref {
  hidden: string[]
  order: string[]
}

export interface TableColumnPrefOptions {
  /** 这些列的 key 永远不会被隐藏（例如操作列） */
  alwaysVisible?: string[]
}

type ColumnLike = Record<string, any>

function columnKey(col: ColumnLike): string {
  return (col.key ?? col.dataIndex ?? col.title ?? '') + ''
}

export function useTableColumnPrefs(
  tableKey: string,
  columns: ColumnLike[] | { value: ColumnLike[] },
  options: TableColumnPrefOptions = {},
) {
  const STORAGE_KEY = `lt-table-prefs:${tableKey}`
  const alwaysVisible = new Set(options.alwaysVisible || [])

  function readStored(): ColumnPref {
    try {
      const raw = localStorage.getItem(STORAGE_KEY)
      if (!raw) return { hidden: [], order: [] }
      const parsed = JSON.parse(raw)
      return {
        hidden: Array.isArray(parsed.hidden) ? parsed.hidden : [],
        order: Array.isArray(parsed.order) ? parsed.order : [],
      }
    } catch {
      return { hidden: [], order: [] }
    }
  }

  const columnPrefs = ref<ColumnPref>(readStored())

  watch(columnPrefs, (v) => {
    try { localStorage.setItem(STORAGE_KEY, JSON.stringify(v)) } catch { /* ignore */ }
  }, { deep: true })

  /** 原始列（Ref 或裸数组统一取值） */
  function rawColumns(): ColumnLike[] {
    return Array.isArray(columns) ? columns : columns.value || []
  }

  const prefColumns = computed<ColumnLike[]>(() => {
    const raw = rawColumns()
    const rawByKey = new Map(raw.map((c) => [columnKey(c), c]))

    // 1. 排序：先按 order 里的顺序拉出，再追加不在 order 里的（保持原顺序）
    const orderedKeys: string[] = []
    for (const k of columnPrefs.value.order) {
      if (rawByKey.has(k) && !orderedKeys.includes(k)) orderedKeys.push(k)
    }
    for (const c of raw) {
      const k = columnKey(c)
      if (!orderedKeys.includes(k)) orderedKeys.push(k)
    }

    // 2. 过滤：隐藏但不动 alwaysVisible
    const hidden = new Set(columnPrefs.value.hidden)
    return orderedKeys
      .map((k) => rawByKey.get(k)!)
      .filter((c) => alwaysVisible.has(columnKey(c)) || !hidden.has(columnKey(c)))
  })

  /** 所有可选列（含隐藏项），供设置面板渲染 */
  const allColumns = computed<Array<ColumnLike & { _visible: boolean; _locked: boolean }>>(() => {
    const raw = rawColumns()
    const rawByKey = new Map(raw.map((c) => [columnKey(c), c]))
    const orderedKeys: string[] = []
    for (const k of columnPrefs.value.order) {
      if (rawByKey.has(k) && !orderedKeys.includes(k)) orderedKeys.push(k)
    }
    for (const c of raw) {
      const k = columnKey(c)
      if (!orderedKeys.includes(k)) orderedKeys.push(k)
    }
    const hidden = new Set(columnPrefs.value.hidden)
    return orderedKeys.map((k) => {
      const c = rawByKey.get(k)!
      return {
        ...c,
        _visible: !hidden.has(k),
        _locked: alwaysVisible.has(k),
      }
    })
  })

  function toggleVisible(key: string) {
    if (alwaysVisible.has(key)) return
    const set = new Set(columnPrefs.value.hidden)
    if (set.has(key)) set.delete(key)
    else set.add(key)
    columnPrefs.value = { ...columnPrefs.value, hidden: [...set] }
  }

  function setOrder(orderedKeys: string[]) {
    columnPrefs.value = { ...columnPrefs.value, order: orderedKeys }
  }

  function resetPrefs() {
    columnPrefs.value = { hidden: [], order: [] }
  }

  return {
    prefColumns,
    columnPrefs,
    allColumns,
    toggleVisible,
    setOrder,
    resetPrefs,
  }
}
