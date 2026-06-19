import { get } from './api'
import type { ApiResponse } from './api'

/**
 * 操作日志相关接口类型定义
 */
export interface LogItem {
  id: number
  operator: string
  action: string
  target: string
  description: string
  ip: string
  status: string
  createdAt: string
  detail?: string
}

export interface LogListParams {
  page?: number
  size?: number
  operator?: string
  action?: string
  startTime?: string
  endTime?: string
}

export interface PageResult<T> {
  records: T[]
  total: number
  current: number
  size: number
  pages: number
}

const ADMIN_LOGS_URL = '/admin/logs'

/**
 * 分页查询操作日志列表
 */
export async function getLogList(params: LogListParams = {}): Promise<ApiResponse<PageResult<LogItem>>> {
  return get<PageResult<LogItem>>(ADMIN_LOGS_URL, params)
}

/**
 * 根据ID查询日志详情
 */
export async function getLogById(id: number): Promise<ApiResponse<LogItem>> {
  return get<LogItem>(`${ADMIN_LOGS_URL}/${id}`)
}

/**
 * 获取操作类型列表
 */
export async function getActionTypes(): Promise<ApiResponse<string[]>> {
  return get<string[]>(`${ADMIN_LOGS_URL}/actions`)
}

/**
 * 获取操作类型统计（操作类型及数量）
 */
export async function getActionStats(): Promise<ApiResponse<Array<Record<string, any>>>> {
  return get<Array<Record<string, any>>>(`${ADMIN_LOGS_URL}/action-stats`)
}

/**
 * 获取目标类型列表
 */
export async function getTargetTypes(): Promise<ApiResponse<string[]>> {
  return get<string[]>(`${ADMIN_LOGS_URL}/target-types`)
}

// 导出默认对象（兼容导入方式）
export default {
  getLogList,
  getLogById,
  getActionTypes,
  getActionStats,
  getTargetTypes
}
