/**
 * axios 实例工厂（Admin 端统一 HTTP 客户端）
 *
 * 主后端实例（api.ts）与 AI 服务实例（aiClient.ts）共用同一套拦截行为：
 * - 请求：从 utils/auth 取 token 注入 `Authorization: Bearer`
 * - 响应：401 清凭证并跳登录、403 跳权限页、业务错误统一提示后端 message
 *
 * 唯一差异是响应体是否归一化：主后端统一返回 `{ code, message, data }`，需要归一化；
 * AI 服务部分接口直接返回原始对象/数组（见 aiModels.ts 的注释），不能包一层。
 * 因此用 `normalizeResponse` 开关区分，而不是复制两份拦截器。
 *
 * 注意：不预设 Content-Type，让 axios 按 data 类型自动设置——
 * 普通对象自动 JSON.stringify + application/json，FormData 自动 multipart/form-data + boundary。
 * 曾因默认 application/json 覆盖 FormData 检测，导致音乐上传（POST /admin/music）后端 500。
 *
 * @author 刘鑫
 */
import axios from 'axios'
import type { AxiosInstance, AxiosResponse } from 'axios'
import { message } from 'ant-design-vue'
import router from '../router'
import { getToken, removeToken } from '../utils/auth'

export interface HttpClientOptions {
  /** 接口根地址（解析策略见 serviceConfig.ts） */
  baseURL: string
  /** 请求超时（毫秒），默认 30000 */
  timeout?: number
  /** 是否把响应体归一化为 `{ code, message, data }`，默认 false */
  normalizeResponse?: boolean
}

/**
 * 创建带统一拦截器的 axios 实例
 */
export function createHttpClient(options: HttpClientOptions): AxiosInstance {
  const instance = axios.create({
    baseURL: options.baseURL,
    timeout: options.timeout ?? 30000
  })

  // 请求拦截器：注入 JWT
  instance.interceptors.request.use(
    (config) => {
      const token = getToken()
      if (token) {
        config.headers.Authorization = `Bearer ${token}`
      }
      return config
    },
    (error) => Promise.reject(error)
  )

  // 响应拦截器：成功分支按需归一化，失败分支统一处理
  instance.interceptors.response.use(
    (response: AxiosResponse<any>) =>
      options.normalizeResponse ? normalizeResponseBody(response) : response,
    (error) => {
      handleResponseError(error)
      if (error === null || error === undefined) {
        throw new Error('网络请求失败')
      }
      throw error
    }
  )

  return instance
}

/**
 * 归一化响应体为标准 `{ code, message, data }`
 *
 * 支持三种形态：
 * 1. 标准业务响应 `{ code, message, data }` —— 校验 code，非 200 抛业务错误
 * 2. AI 服务响应 `{ success, ... }` —— 校验 success，成功则包装为标准格式
 * 3. 其它结构（如 TinyMCE 上传返回 `{ location }`、blob）—— 直接包装放行
 */
function normalizeResponseBody(response: AxiosResponse<any>): AxiosResponse<any> {
  const { data } = response

  const isStandard = data && typeof data.code === 'number' && 'message' in data && 'data' in data
  const isAi = data && typeof data.success === 'boolean'

  if (isStandard) {
    if (data.code !== 200) {
      console.error('API 业务错误:', data.message)
      const err: any = new Error(data.message || '请求失败')
      err.isBusiness = true
      err.response = response
      message.error(data.message || '请求失败')
      throw err
    }
    return response
  }

  if (isAi) {
    if (data.success !== true) {
      console.error('AI 业务错误:', data.message)
      const err: any = new Error(data.message || '请求失败')
      err.isBusiness = true
      err.response = response
      message.error(data.message || '请求失败')
      throw err
    }
    response.data = { code: 200, message: data.message || 'ok', data }
    return response
  }

  response.data = { code: 200, message: 'ok', data }
  return response
}

/**
 * 统一处理响应错误
 *
 * - 401：清凭证并跳登录页（跳转本身已足够，不再重复提示）
 * - 403：跳权限页
 * - 其它带 message 的错误：提示后端原文并标记 `isBusiness`，调用方据此避免二次弹窗
 */
function handleResponseError(error: any): void {
  console.error('API 请求失败:', error?.message, error?.code, error?.config?.url, error?.response?.status)

  const status = error?.response?.status
  // 后端业务异常统一返回 HTTP 400 + { code, message, data }，message 面向用户可读
  const bizMessage = error?.response?.data?.message

  if (status === 401) {
    message.destroy()
    removeToken()
    if (router.currentRoute.value.path !== '/login') {
      router.push('/login')
    }
  } else if (status === 403) {
    message.destroy()
    if (router.currentRoute.value.path !== '/403') {
      router.push('/403')
    }
  } else if (bizMessage) {
    message.error(bizMessage)
    error.isBusiness = true
  }
}
