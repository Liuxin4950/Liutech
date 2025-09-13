import axios from 'axios'
import type { AxiosInstance, AxiosRequestConfig, AxiosResponse } from 'axios'
import router from '../router'
import { handleApiError, showErrorToast } from '../utils/errorHandler'
import { ServiceType, getServiceConfig, DEFAULT_SERVICE } from '../config/services'

// API 响应接口
export interface ApiResponse<T = any> {
  code: number
  message: string
  data: T
}

// 请求配置接口
export interface RequestConfig extends AxiosRequestConfig {
  skipErrorHandler?: boolean // 是否跳过统一错误处理
  serviceType?: ServiceType  // 服务类型选择
}

// 创建多个 axios 实例
const instances: Record<ServiceType, AxiosInstance> = {} as Record<ServiceType, AxiosInstance>

// 初始化所有服务实例
Object.values(ServiceType).forEach(serviceType => {
  const config = getServiceConfig(serviceType)
  instances[serviceType as keyof typeof instances] = axios.create({
    baseURL: config.baseURL,
    timeout: config.timeout,
    headers: {
      'Content-Type': 'application/json'
    }
  })
})

// 获取指定服务的实例
const getInstance = (serviceType: ServiceType = DEFAULT_SERVICE): AxiosInstance => {
  return instances[serviceType]
}

// 为每个服务实例配置拦截器
Object.entries(instances).forEach(([serviceType, instance]) => {
  // 请求拦截器
  instance.interceptors.request.use(
    (config) => {
      // 从本地存储获取 token
      const token = localStorage.getItem('token')
      if (token) {
        config.headers.Authorization = `Bearer ${token}`
      }
      
      console.log(`${serviceType.toUpperCase()} API 请求:`, config.method?.toUpperCase(), config.url)
      return config
    },
    (error) => {
      console.error(`${serviceType.toUpperCase()} 请求失败`, error)
      return Promise.reject(error)
    }
  )

  // 响应拦截器（兼容主服务与AI服务的不同返回格式）
  instance.interceptors.response.use(
    (response: AxiosResponse<any>) => {
      const { data } = response

      // 标准业务响应：{ code:number, message:string, data:any }
      const isStandard = data && typeof data.code === 'number' && 'message' in data && 'data' in data
      // AI 服务响应：{ success:boolean, ... }
      const isAi = data && typeof data.success === 'boolean'

      if (isStandard) {
        if (data.code !== 200) {
          console.error(`${serviceType.toUpperCase()} API 业务错误:`, data.message)
          showErrorToast(data.message || '请求失败')
          const err: any = new Error(data.message || '请求失败')
          err.isBusiness = true
          throw err
        }
        return response
      }

      if (isAi) {
        if (data.success !== true) {
          console.error(`${serviceType.toUpperCase()} AI 业务错误:`, data.message)
          showErrorToast(data.message || '请求失败')
          const err: any = new Error(data.message || '请求失败')
          err.isBusiness = true
          throw err
        }
        // AI 成功响应直接透传
        return response
      }

      // 未知结构，直接透传，交由调用方处理
      return response
    },
    (error) => {
      console.error(`${serviceType.toUpperCase()} API 请求失败`, error)
      
      // 使用统一错误处理器
      handleApiError(error)
      
      // 特殊处理401错误，需要跳转登录页
      if (error.response?.status === 401) {
        localStorage.removeItem('token')
        if (router.currentRoute.value.path !== '/login') {
          router.push('/login')
        }
      }
      
      // 重新抛出错误，保持原有的错误传播机制
      throw error
    }
  )
})

// 封装 GET 请求
export const get = async <T = any>(
  url: string, 
  params: Record<string, any> = {},
  config: RequestConfig = {}
): Promise<ApiResponse<T>> => {
  try {
    const { serviceType = DEFAULT_SERVICE, ...axiosConfig } = config
    const instance = getInstance(serviceType)
    const response = await instance.get<ApiResponse<T>>(url, { 
      params, 
      ...axiosConfig 
    })
    return response.data
  } catch (error) {
    console.error(`GET 请求失败: ${url}`, error)
    throw error
  }
}

// 封装 POST 请求
export const post = async <T = any>(
  url: string, 
  data: any = {},
  config: RequestConfig = {}
): Promise<ApiResponse<T>> => {
  try {
    const { serviceType = DEFAULT_SERVICE, ...axiosConfig } = config
    const instance = getInstance(serviceType)
    const response = await instance.post<ApiResponse<T>>(url, data, axiosConfig)
    return response.data
  } catch (error) {
    console.error(`POST 请求失败: ${url}`, error)
    throw error
  }
}

// 封装 PUT 请求
export const put = async <T = any>(
  url: string, 
  data: any = {},
  config: RequestConfig = {}
): Promise<ApiResponse<T>> => {
  try {
    const { serviceType = DEFAULT_SERVICE, ...axiosConfig } = config
    const instance = getInstance(serviceType)
    const response = await instance.put<ApiResponse<T>>(url, data, axiosConfig)
    return response.data
  } catch (error) {
    console.error(`PUT 请求失败: ${url}`, error)
    throw error
  }
}

// 封装 DELETE 请求
export const del = async <T = any>(
  url: string,
  config: RequestConfig = {}
): Promise<ApiResponse<T>> => {
  try {
    const { serviceType = DEFAULT_SERVICE, ...axiosConfig } = config
    const instance = getInstance(serviceType)
    const response = await instance.delete<ApiResponse<T>>(url, axiosConfig)
    return response.data
  } catch (error) {
    console.error(`DELETE 请求失败: ${url}`, error)
    throw error
  }
}

// 导出 axios 实例，供特殊需求使用
export const getAxiosInstance = (serviceType: ServiceType = DEFAULT_SERVICE): AxiosInstance => {
  return getInstance(serviceType)
}

// 导出默认实例（主服务）
export const axiosInstance = getInstance(DEFAULT_SERVICE)
export default axiosInstance

// 导出服务类型和配置
export { ServiceType, getServiceConfig }