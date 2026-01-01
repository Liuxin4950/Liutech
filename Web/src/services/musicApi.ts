import { get, post, put, del } from './api'

/**
 * 音乐项数据结构
 */
export interface MusicItem {
  id: number
  title: string
  artist: string | null
  coverUrl: string | null
  fullAudioUrl: string
  vocalUrl: string
  duration: number | null
  sortOrder: number
  status: number
  createdAt: string
  updatedAt: string
}

/**
 * 获取音乐列表
 * @returns 音乐列表
 */
export const getMusicList = async (): Promise<MusicItem[]> => {
  const response = await get<MusicItem[]>('/music/list')
  return response.data
}

/**
 * 获取音乐详情
 * @param id 音乐ID
 * @returns 音乐详情
 */
export const getMusicDetail = async (id: number): Promise<MusicItem> => {
  const response = await get<MusicItem>(`/music/${id}`)
  return response.data
}

/**
 * 上传音乐（Admin）
 * @param data 上传数据
 * @returns 音乐ID
 */
export const uploadMusic = async (data: {
  title: string
  artist?: string
  cover?: File
  fullAudio: File
  vocalAudio: File
}): Promise<number> => {
  const formData = new FormData()
  formData.append('title', data.title)
  if (data.artist) {
    formData.append('artist', data.artist)
  }
  if (data.cover) {
    formData.append('cover', data.cover)
  }
  formData.append('fullAudio', data.fullAudio)
  formData.append('vocalAudio', data.vocalAudio)

  const response = await post<number>('/admin/music', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
  return response.data
}

/**
 * 更新音乐信息（Admin）
 * @param id 音乐ID
 * @param data 更新数据
 * @returns 是否成功
 */
export const updateMusic = async (
  id: number,
  data: {
    title?: string
    artist?: string
    sortOrder?: number
    status?: number
  }
): Promise<boolean> => {
  const params = new URLSearchParams()
  if (data.title) params.append('title', data.title)
  if (data.artist) params.append('artist', data.artist)
  if (data.sortOrder !== undefined) params.append('sortOrder', String(data.sortOrder))
  if (data.status !== undefined) params.append('status', String(data.status))

  const response = await put<boolean>(`/admin/music/${id}`, params, {
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' }
  })
  return response.data
}

/**
 * 删除音乐（Admin）
 * @param id 音乐ID
 * @returns 是否成功
 */
export const deleteMusic = async (id: number): Promise<boolean> => {
  const response = await del<boolean>(`/admin/music/${id}`)
  return response.data
}

/**
 * 更新排序（Admin）
 * @param ids 排序后的ID列表
 * @returns 是否成功
 */
export const updateMusicSort = async (ids: number[]): Promise<boolean> => {
  const params = new URLSearchParams()
  ids.forEach(id => params.append('ids', String(id)))

  const response = await put<boolean>('/admin/music/sort', params, {
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' }
  })
  return response.data
}
