/**
 * AI 服务 API 客户端（Admin 端）
 *
 * baseURL 解析统一由 `serviceConfig.ts` 提供（环境变量 → 开发 8081 → 生产 /ai）。
 * 请求/响应拦截器与主后端实例共用 `httpClient.ts`，行为一致（注入 JWT、401 跳登录、403 跳权限页）。
 *
 * 唯一区别：**不归一化响应体**。AI 服务的模型接口直接返回原始对象/数组，
 * 若走归一化会给调用方多包一层 `{ code, message, data }`（见 aiModels.ts 的说明）。
 *
 * @author 刘鑫
 */
import type { AxiosInstance } from 'axios'
import { createHttpClient } from './httpClient'
import { getAiBaseUrl } from './serviceConfig'

/** AI 服务 axios 实例 */
export const aiApi: AxiosInstance = createHttpClient({
  baseURL: getAiBaseUrl(),
  timeout: 30000,
  normalizeResponse: false
})
