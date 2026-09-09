/**
 * 主后端 API 客户端（Admin 端）
 *
 * 实例创建与请求/响应拦截器统一由 `httpClient.ts` 提供，本文件只负责：
 * - 声明主后端响应类型 `ApiResponse`
 * - 导出 get/post/put/del 四个便捷方法（统一返回 `response.data`）
 * - 导出原始实例，供上传/下载等需要自定义 config 的场景使用
 *
 * @author 刘鑫
 */
import type { AxiosInstance, AxiosRequestConfig } from 'axios'
import { createHttpClient } from './httpClient'
import { getBackendURL } from './serviceConfig'

/** 主后端统一响应结构 */
export interface ApiResponse<T = any> {
  code: number
  message: string
  data: T
}

/** 请求配置（透传 axios 原生配置） */
export interface RequestConfig extends AxiosRequestConfig {}

/**
 * 主后端 axios 实例
 *
 * baseURL 解析见 serviceConfig.ts；normalizeResponse 开启后，
 * 响应体会被归一化为 `{ code, message, data }`，调用方无需关心后端原始结构。
 */
const instance: AxiosInstance = createHttpClient({
  baseURL: getBackendURL(),
  timeout: 30000,
  normalizeResponse: true
})

/** GET 请求 */
export const get = async <T = any>(
  url: string,
  params: Record<string, any> = {},
  config: RequestConfig = {}
): Promise<ApiResponse<T>> => {
  const response = await instance.get<ApiResponse<T>>(url, { params, ...config })
  return response.data
}

/** POST 请求 */
export const post = async <T = any>(
  url: string,
  data: any = {},
  config: RequestConfig = {}
): Promise<ApiResponse<T>> => {
  const response = await instance.post<ApiResponse<T>>(url, data, config)
  return response.data
}

/** PUT 请求 */
export const put = async <T = any>(
  url: string,
  data: any = {},
  config: RequestConfig = {}
): Promise<ApiResponse<T>> => {
  const response = await instance.put<ApiResponse<T>>(url, data, config)
  return response.data
}

/** DELETE 请求 */
export const del = async <T = any>(
  url: string,
  config: RequestConfig = {}
): Promise<ApiResponse<T>> => {
  const response = await instance.delete<ApiResponse<T>>(url, config)
  return response.data
}

// 导出 axios 实例，供特殊需求使用
export { instance as axiosInstance }
export default instance
