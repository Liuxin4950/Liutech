import { get } from './api'

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
