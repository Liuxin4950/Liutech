import { get, post, put, del, type ApiResponse } from './api'
import { aiApi } from './aiClient'
import type { Comment } from './comments'
import type { PageResult } from './types'

export interface CommunityBotInput {
  name: string
  avatarUrl: string
  personality: string
  background: string
  systemPrompt: string
  interests: string
  participation: number
  enabled: boolean
}
export interface CommunityBot extends CommunityBotInput { id: number; version: number }
export interface CommunityKnowledge { id: number; botId: number; title: string; content: string; version: number }
export interface CommunitySettings {
  enabled: boolean
  minDelaySeconds: number
  maxDelaySeconds: number
  cooldownSeconds: number
  botDailyCommentLimit: number
  siteDailyCommentLimit: number
  postDailyCommentLimit: number
  botDailyTaskLimit: number
  siteDailyTaskLimit: number
  maxChainComments: number
  version?: number
}
export const defaultCommunitySettings = (): CommunitySettings => ({
  enabled: false, minDelaySeconds: 20, maxDelaySeconds: 90, cooldownSeconds: 30,
  botDailyCommentLimit: 20, siteDailyCommentLimit: 100, postDailyCommentLimit: 20,
  botDailyTaskLimit: 40, siteDailyTaskLimit: 200, maxChainComments: 4,
})
export interface CommunityReadTrace { source: string; id: number | string; start?: number; end?: number; total?: number; truncated?: boolean }
export interface CommunityToolTrace { name: string; input?: unknown; status: string }
export interface CommunityRun {
  id?: number | string
  taskId: number | string
  botId: number
  postId: number
  postTitle?: string
  commentId?: number
  commentPreview?: string
  targetCommentPreview?: string
  targetAuthorName?: string
  decision?: 'SKIP' | 'COMMENT' | 'REPLY'
  reason?: string
  content?: string
  contentTruncated?: boolean
  targetCommentId?: number
  publishedCommentId?: number
  publicationStatus?: string
  publicationError?: string
  publishedAt?: string
  model?: string
  inputTokens?: number
  outputTokens?: number
  tokenUsageAvailable?: boolean
  tokenUsageComplete?: boolean
  modelRounds?: number
  readTrace?: CommunityReadTrace[]
  toolTrace?: CommunityToolTrace[]
  preview?: boolean
  status?: string
  createdAt?: string
  error?: string
  roleSnapshot?: CommunityBot
}
export type CommunityComment = Omit<Comment, 'id'> & { id: number; parentPreview?: string }
export interface CommunityCommentThread {
  postId: number
  postTitle?: string
  rootCommentId: number
  total: number
  truncated: boolean
  comments: CommunityComment[]
}
export interface CommunityTask {
  id: number | string; eventId?: number | string; botId: number; postId: number; commentId?: number
  postTitle?: string; commentPreview?: string
  retryKind?: 'regenerate' | 'publish' | 'postprocess' | 'complete'; retryReason?: string; publishedCommentId?: number
  status: string; attempts: number; failures?: number; error?: string; availableAt?: string; leaseUntil?: string; createdAt?: string
}
export interface CommunityMemory {
  id: number | string; botId: number; sourcePostId: number; sourceCommentId?: number
  postTitle?: string; sourceCommentPreview?: string
  sourceCommentIds?: number[]; participants?: number[]; summary: string; createdAt?: string
}
export interface CommunityBackfillResult { queued: number; skipped: number; postCount: number }

async function aiData<T>(request: Promise<{ data: ApiResponse<T> & { success?: boolean } }>): Promise<T> {
  const { data } = await request
  if (data.success === false || (typeof data.code === 'number' && data.code !== 200)) {
    throw new Error(data.message || '请求失败')
  }
  return data.data
}
function settingsInput(data: CommunitySettings): Omit<CommunitySettings, 'version'> {
  const { enabled, minDelaySeconds, maxDelaySeconds, cooldownSeconds, botDailyCommentLimit,
    siteDailyCommentLimit, postDailyCommentLimit, botDailyTaskLimit, siteDailyTaskLimit, maxChainComments } = data
  return { enabled, minDelaySeconds, maxDelaySeconds, cooldownSeconds, botDailyCommentLimit,
    siteDailyCommentLimit, postDailyCommentLimit, botDailyTaskLimit, siteDailyTaskLimit, maxChainComments }
}
const base = '/admin/community'
const aiBase = '/admin/community'
export const communityService = {
  bots: async () => (await get<CommunityBot[]>(`${base}/bots`)).data,
  createBot: async (data: CommunityBotInput) => (await post<CommunityBot>(`${base}/bots`, data)).data,
  updateBot: async (id: number, data: CommunityBotInput) => (await put<CommunityBot>(`${base}/bots/${id}`, data)).data,
  deleteBot: async (id: number) => del(`${base}/bots/${id}`),
  comments: async (botId: number, page = 1, size = 20) => (await get<PageResult<CommunityComment>>(`${base}/bots/${botId}/comments`, { page, size })).data,
  thread: async (commentId: number) => (await get<CommunityCommentThread>(`${base}/comments/${commentId}/thread`)).data,
  knowledge: async (botId: number) => (await get<CommunityKnowledge[]>(`${base}/bots/${botId}/knowledge`)).data,
  saveKnowledge: async (botId: number, data: { title: string; content: string }, id?: number) => id
    ? put<CommunityKnowledge>(`${base}/bots/${botId}/knowledge/${id}`, data)
    : post<CommunityKnowledge>(`${base}/bots/${botId}/knowledge`, data),
  deleteKnowledge: async (botId: number, id: number) => del(`${base}/bots/${botId}/knowledge/${id}`),
  settings: async () => (await get<CommunitySettings>(`${base}/settings`)).data,
  saveSettings: async (data: CommunitySettings) => (await put<CommunitySettings>(`${base}/settings`, settingsInput(data))).data,
  postEnabled: async (postId: number) => (await get<{ enabled: boolean }>(`${base}/posts/${postId}/enabled`)).data,
  setPostEnabled: async (postId: number, enabled: boolean) => put(`${base}/posts/${postId}/enabled`, { enabled }),
  invite: async (postId: number, botIds: number[]) => (await post<{ queued: number }>(`${base}/posts/${postId}/invite`, { botIds })).data,
  backfill: async (limit = 10, botIds?: number[]) => (await post<CommunityBackfillResult>(`${base}/backfill`, { limit, botIds })).data,
  preview: (data: { botId: number; postId: number; commentId?: number }, signal?: AbortSignal) => aiData<CommunityRun>(aiApi.post(aiBase + '/preview', data, { timeout: 180000, signal })),
  runs: (botId?: number) => aiData<CommunityRun[]>(aiApi.get(aiBase + '/runs', { params: { botId, limit: 100 } })),
  tasks: (botId?: number) => aiData<CommunityTask[]>(aiApi.get(aiBase + '/tasks', { params: { botId, limit: 100 } })),
  retryTask: (taskId: number | string) => aiData<{ taskId?: number | string; queued: boolean; reason: string; retryKind?: CommunityTask['retryKind'] }>(aiApi.post(`${aiBase}/tasks/${encodeURIComponent(String(taskId))}/retry`)),
  memory: (botId: number) => aiData<CommunityMemory[]>(aiApi.get(aiBase + '/memory', { params: { botId } })),
  clearMemory: (botId: number) => aiData<void>(aiApi.delete(`${aiBase}/memory/${botId}`)),
}
