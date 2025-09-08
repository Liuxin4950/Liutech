import axios from 'axios'
import type { AxiosInstance, AxiosRequestConfig, AxiosResponse } from 'axios'
import router from '../router'
import { handleApiError, showErrorToast } from '../utils/errorHandler'

// API 响应接口
export interface ApiResponse<T = any> {
  code: number
  message: string
  data: T
}

// 请求配置接口
export interface RequestConfig extends AxiosRequestConfig {
  skipErrorHandler?: boolean // 是否跳过统一错误处理
}

// 创建 axios 实例
const instance: AxiosInstance = axios.create({
  baseURL: 'http://127.0.0.1:8080', // 后端接口的根地址
  timeout: 30000, // 请求超时的时间，单位是毫秒
  headers: {
    'Content-Type': 'application/json'
  }
})

// 请求拦截器
instance.interceptors.request.use(
  (config) => {
    // 从本地存储获取 token
    const token = localStorage.getItem('token')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    
    console.log('API 请求:', config.method?.toUpperCase(), config.url)
    return config
  },
  (error) => {
    console.error('请求失败', error)
    return Promise.reject(error)
  }
)

// 响应拦截器
instance.interceptors.response.use(
  (response: AxiosResponse<ApiResponse>) => {
    const { data } = response
    
    // 检查业务状态码
    if (data.code !== 200) {
      console.error('API 业务错误:', data.message)
      // 使用统一的错误Toast提示
      showErrorToast(data.message || '请求失败')
      const err: any = new Error(data.message || '请求失败')
      err.isBusiness = true // 标记为业务错误，避免后续重复弹窗
      throw err
    }
    
    return response
  },
  (error) => {
    console.error('API 请求失败', error)
    
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

// 封装 GET 请求
export const get = async <T = any>(
  url: string, 
  params: Record<string, any> = {},
  config: RequestConfig = {}
): Promise<ApiResponse<T>> => {
  try {
    const response = await instance.get<ApiResponse<T>>(url, { 
      params, 
      ...config 
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
    const response = await instance.post<ApiResponse<T>>(url, data, config)
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
    const response = await instance.put<ApiResponse<T>>(url, data, config)
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
    const response = await instance.delete<ApiResponse<T>>(url, config)
    return response.data
  } catch (error) {
    console.error(`DELETE 请求失败: ${url}`, error)
    throw error
  }
}

// 导出 axios 实例，供特殊需求使用
export { instance as axiosInstance }
export default instance