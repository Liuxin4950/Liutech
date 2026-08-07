import { get } from './api'
import type { ApiResponse } from './api'
import type { PageResult } from './types'

/**
 * 操作日志相关接口类型定义
 */
export interface LogItem {
  id: number
  operator: string
  action: string
  target: string
  targetName?: string
  description: string
  ip: string
  userAgent?: string
  status: string
  createdAt: string
  detail?: string
}

export interface LogListParams {
  page?: number
  size?: number
  operator?: string
  action?: string
  targetType?: string
  status?: number
  startTime?: string
  endTime?: string
}

const ADMIN_LOGS_URL = '/admin/logs'

/**
 * 分页查询操作日志列表
 */
export async function getLogList(params: LogListParams = {}): Promise<ApiResponse<PageResult<LogItem>>> {
  return get<PageResult<LogItem>>(ADMIN_LOGS_URL, params)
}

/**
 * 获取操作类型列表
 */
export async function getActionTypes(): Promise<ApiResponse<string[]>> {
  return get<string[]>(`${ADMIN_LOGS_URL}/actions`)
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
  getActionTypes,
  getTargetTypes
}
