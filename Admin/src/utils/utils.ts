/**
 * 通用工具函数
 * @author 刘鑫
 * @date 2025-01-16
 *
 * 日期格式化统一走 dayjs（项目既有的日期库），不再用原生 Date.toLocaleString。
 * 输出格式与迁移前逐字一致：`2026/09/23 21:31:59`，所以是零视觉变化的替换。
 */
import dayjs from 'dayjs'

/**
 * 格式化日期
 * @param dateString 日期字符串
 * @returns 格式化后的日期字符串 (YYYY/MM/DD)
 */
export const formatDate = (dateString: string | undefined): string => {
  if (!dateString) return ''
  return dayjs(dateString).format('YYYY/MM/DD')
}

/**
 * 格式化日期时间
 * @param dateString 日期字符串
 * @returns 格式化后的日期时间字符串 (YYYY/MM/DD HH:mm:ss)
 */
export const formatDateTime = (dateString: string | undefined): string => {
  if (!dateString) return ''
  return dayjs(dateString).format('YYYY/MM/DD HH:mm:ss')
}

/**
 * 格式化相对时间
 * @param dateString 日期字符串
 * @returns 相对时间字符串 (如: 2小时前, 3天前)
 */
export const formatRelativeTime = (dateString: string): string => {
  if (!dateString) return ''
  const date = new Date(dateString)
  const now = new Date()
  const diff = now.getTime() - date.getTime()
  
  const minute = 60 * 1000
  const hour = minute * 60
  const day = hour * 24
  const month = day * 30
  const year = day * 365
  
  if (diff < minute) {
    return '刚刚'
  } else if (diff < hour) {
    return Math.floor(diff / minute) + '分钟前'
  } else if (diff < day) {
    return Math.floor(diff / hour) + '小时前'
  } else if (diff < month) {
    return Math.floor(diff / day) + '天前'
  } else if (diff < year) {
    return Math.floor(diff / month) + '个月前'
  } else {
    return Math.floor(diff / year) + '年前'
  }
}