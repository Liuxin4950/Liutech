import axios from 'axios'
import type { AxiosInstance, AxiosRequestConfig, AxiosResponse } from 'axios'
import Swal from 'sweetalert2'
import router from '../router'
import { showErrorToast } from '../utils/errorHandler'
import { ServiceType, getServiceConfig, DEFAULT_SERVICE } from '../config/services'

// API 响应接口
export interface ApiResponse<T = any> {
  code: number
  message: string
  data: T
}

// 请求配置接口
export interface RequestConfig extends AxiosRequestConfig {
  serviceType?: ServiceType  // 服务类型选择（主服务/AI服务）
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
        // 将 AI 响应包装为标准 ApiResponse 格式，保持类型一致性
        response.data = { code: 200, message: data.message || 'ok', data }
        return response
      }

      // 未知结构（含 AI 服务数组响应），包装为标准格式
      response.data = { code: 200, message: 'ok', data }
      return response
    },
    (error) => {
      console.error(`${serviceType.toUpperCase()} API 请求失败`, error)

      const status = error.response?.status
      const bizMessage = error.response?.data?.message

      // 特殊处理401错误：清除所有弹窗、token，跳转登录页
      if (status === 401) {
        Swal.close()
        localStorage.removeItem('token')
        const currentRoute = router.currentRoute.value
        if (currentRoute.name !== 'login') {
          router.push({ name: 'login', query: { redirect: currentRoute.fullPath } }).catch(() => undefined)
        }
      } else if (status === 403) {
        // 特殊处理403错误：清除弹窗
        Swal.close()
      } else if (bizMessage) {
        // 业务错误（4xx）：提示后端返回的具体 message，并标记业务错误避免调用方重复弹窗
        showErrorToast(bizMessage)
        error.isBusiness = true
      }

      // 重新抛出错误，由调用方决定是否弹窗
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
