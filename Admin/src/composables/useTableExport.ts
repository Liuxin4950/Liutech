import { computed, type ComputedRef, type Ref } from 'vue'
import dayjs from 'dayjs'

/**
 * 表格数据导出（CSV / JSON）
 * ---------------------------------------------------------------------
 * 用法（一行接入）：
 *   const { exportCsv, exportJson } = useTableExport({
 *     columns: () => prefColumns.value,  // 当前生效的列（跟随用户列偏好）
 *     rows: () => dataSource.value,       // 当前页数据（也可传入全量）
 *     filename: 'posts',                  // 不含扩展名
 *   })
 *
 * 特性：
 * - CSV 遵循 RFC 4180：字段含逗号/引号/换行时用引号包裹，内部引号转义为 ""
 * - 首行加 UTF-8 BOM，Excel 打开中文不乱码
 * - 列顺序 = 传入 columns 的顺序（自动跟随用户列偏好）
 * - 自动跳过 key='action' 等操作列
 * - customRender/formatter 优先，其次 dataIndex 取值
 * - 文件名自动加日期后缀：posts_2026-07-06.csv
 */

export interface ExportColumn {
  title?: string
  key?: string
  dataIndex?: string | string[]
  customRender?: (opts: { text: any; record: any; index: number }) => any
  formatter?: (value: any, record: any) => any
}

export interface UseTableExportOptions {
  /** 列定义（响应式函数或 ref/computed） */
  columns: (() => ExportColumn[]) | Ref<ExportColumn[]> | ComputedRef<ExportColumn[]>
  /** 行数据（响应式函数或 ref/computed） */
  rows: (() => any[]) | Ref<any[]> | ComputedRef<any[]>
  /** 文件名前缀（不含扩展） */
  filename: string
  /** 跳过的列 key，默认 ['action'] */
  skipKeys?: string[]
}

function resolveRef<T>(source: (() => T) | Ref<T> | ComputedRef<T>): T {
  if (typeof source === 'function') return (source as () => T)()
  return (source as Ref<T>).value
}

/** 从 record 里按 dataIndex 取值，支持 'a.b.c' 或 ['a','b','c'] */
function pickValue(record: any, dataIndex?: string | string[]): any {
  if (!dataIndex) return undefined
  const parts = Array.isArray(dataIndex) ? dataIndex : dataIndex.split('.')
  let cur = record
  for (const p of parts) {
    if (cur == null) return undefined
    cur = cur[p]
  }
  return cur
}

/** 把任意值格式化为字符串，用于 CSV 单元格 */
function toCellString(v: any): string {
  if (v == null) return ''
  if (typeof v === 'string') return v
  if (typeof v === 'number' || typeof v === 'boolean') return String(v)
  if (v instanceof Date) return dayjs(v).format('YYYY-MM-DD HH:mm:ss')
  // 数组/对象都序列化，避免 [object Object]
  try { return JSON.stringify(v) } catch { return String(v) }
}

/** RFC 4180 转义：字段含 , " \r \n 时加引号，内部 " 变 "" */
function escapeCsvCell(v: string): string {
  if (/[",\r\n]/.test(v)) {
    return '"' + v.replace(/"/g, '""') + '"'
  }
  return v
}

function triggerDownload(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  setTimeout(() => URL.revokeObjectURL(url), 100)
}

export function useTableExport(options: UseTableExportOptions) {
  const skipSet = new Set(options.skipKeys || ['action'])

  /** 导出用的列（跳过操作列等无意义列） */
  const exportColumns = computed<ExportColumn[]>(() =>
    resolveRef(options.columns).filter((c) => {
      const key = (c.key ?? (Array.isArray(c.dataIndex) ? c.dataIndex.join('.') : c.dataIndex) ?? '') + ''
      return !skipSet.has(key)
    })
  )

  const rowCount = computed(() => resolveRef(options.rows).length)

  function buildRows(): any[][] {
    const cols = exportColumns.value
    const data = resolveRef(options.rows)
    const header = cols.map((c) => c.title || String(c.dataIndex || c.key || ''))
    const body = data.map((record, index) =>
      cols.map((c) => {
        // 优先 formatter，其次 customRender（若返回 string），最后 dataIndex 取值
        const raw = pickValue(record, c.dataIndex)
        if (c.formatter) {
          try { return toCellString(c.formatter(raw, record)) } catch { return toCellString(raw) }
        }
        if (c.customRender) {
          try {
            const rendered = c.customRender({ text: raw, record, index })
            // customRender 常返回 VNode，只在返回 string/number 时才用
            if (typeof rendered === 'string' || typeof rendered === 'number') return toCellString(rendered)
          } catch { /* 忽略渲染函数抛错，回退到原值 */ }
        }
        return toCellString(raw)
      })
    )
    return [header, ...body]
  }

  function exportCsv() {
    const matrix = buildRows()
    const csv = matrix.map((row) => row.map((cell) => escapeCsvCell(cell)).join(',')).join('\r\n')
    // UTF-8 BOM：Excel 打开中文不乱码
    const blob = new Blob(['﻿' + csv], { type: 'text/csv;charset=utf-8' })
    triggerDownload(blob, `${options.filename}_${dayjs().format('YYYY-MM-DD')}.csv`)
  }

  function exportJson() {
    const data = resolveRef(options.rows)
    const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json;charset=utf-8' })
    triggerDownload(blob, `${options.filename}_${dayjs().format('YYYY-MM-DD')}.json`)
  }

  return {
    exportCsv,
    exportJson,
    exportColumns,
    rowCount,
  }
}
