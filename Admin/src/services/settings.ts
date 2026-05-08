import { get, put, post } from './api'
import type { ApiResponse } from './api'

/**
 * 系统设置项
 */
export interface SystemSetting {
  id: number
  settingKey: string
  settingValue: string
  description: string
  createdAt: string
  updatedAt: string
}

/**
 * 按分组查询的结果
 */
export type GroupedSettings = Record<string, SystemSetting[]>

/**
 * 批量更新请求体元素
 */
export interface SettingUpdateItem {
  key: string
  value: string
  description?: string
}

/**
 * 系统设置管理服务（管理端）
 */
export class SettingsService {
  private static readonly BASE_URL = '/admin/settings'

  /** 获取所有设置 */
  static async listAll(): Promise<ApiResponse<SystemSetting[]>> {
    return get<SystemSetting[]>(this.BASE_URL)
  }

  /** 根据 key 获取单个设置 */
  static async getByKey(key: string): Promise<ApiResponse<SystemSetting>> {
    return get<SystemSetting>(`${this.BASE_URL}/${key}`)
  }

  /** 更新单个设置 */
  static async updateByKey(key: string, value: string, description?: string): Promise<ApiResponse<boolean>> {
    return put<boolean>(`${this.BASE_URL}/${key}`, { value, description })
  }

  /** 批量更新设置 */
  static async batchUpdate(settings: SettingUpdateItem[]): Promise<ApiResponse<boolean>> {
    return post<boolean>(`${this.BASE_URL}/batch`, settings)
  }

  /** 按分组获取设置 */
  static async getGrouped(): Promise<ApiResponse<GroupedSettings>> {
    return get<GroupedSettings>(`${this.BASE_URL}/grouped`)
  }
}

export default SettingsService
