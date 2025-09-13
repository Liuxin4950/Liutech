/**
 * 多服务配置文件
 * 支持主服务和AI服务的端口配置
 * 
 * 作者：刘鑫
 * 时间：2025-01-27
 */

// 服务类型（值对象 + 类型别名，避免 enum 引发 TS1294）
export const ServiceType = {
  MAIN: 'main',     // 主服务
  AI: 'ai'          // AI服务
} as const
export type ServiceType = typeof ServiceType[keyof typeof ServiceType]

// 服务配置接口
export interface ServiceConfig {
  baseURL: string
  timeout: number
  name: string
}

// 环境配置
const isDevelopment = import.meta.env.DEV

// 服务配置映射
export const SERVICE_CONFIG: Record<ServiceType, ServiceConfig> = {
  [ServiceType.MAIN]: {
    baseURL: isDevelopment ? 'http://127.0.0.1:8080' : 'https://api.liutech.com',
    timeout: 30000,
    name: '主服务'
  },
  [ServiceType.AI]: {
    baseURL: isDevelopment ? 'http://127.0.0.1:8081' : 'https://ai.liutech.com',
    timeout: 60000, // AI服务可能需要更长的超时时间
    name: 'AI服务'
  }
}

// 获取服务配置
export const getServiceConfig = (serviceType: ServiceType): ServiceConfig => {
  return SERVICE_CONFIG[serviceType]
}

// 获取服务基础URL
export const getServiceBaseURL = (serviceType: ServiceType): string => {
  return SERVICE_CONFIG[serviceType].baseURL
}

// 默认服务类型
export const DEFAULT_SERVICE = ServiceType.MAIN