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
const isProduction = import.meta.env.PROD

// 获取后端服务地址（优先使用环境变量）
const getBackendURL = (): string => {
  const envUrl = import.meta.env.VITE_API_BASE_URL as string | undefined
  if (envUrl && envUrl.trim().length > 0) {
    return envUrl
  }
  if (isDevelopment) {
    return 'http://127.0.0.1:8080'
  }
  if (typeof window !== 'undefined' && window.location.hostname === 'localhost') {
    return '/api'
  }
  return 'https://api.liutech.com'
}

// 服务配置映射
export const SERVICE_CONFIG: Record<ServiceType, ServiceConfig> = {
  [ServiceType.MAIN]: {
    baseURL: getBackendURL(),
    timeout: 30000,
    name: '主服务'
  },
  [ServiceType.AI]: {
      baseURL: (() => {
          const envUrl = import.meta.env.VITE_AI_BASE_URL as string | undefined
          if (envUrl && envUrl.trim().length > 0) return envUrl
          return isDevelopment ? 'http://127.0.0.1:8081/ai' : '/ai'
      })(),
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
