import { get, post, put, del } from './api'
import type { ApiResponse } from './api'

/**
 * 音乐数据类型
 */
export interface Music {
  id: number
  title: string
  artist: string | null
  coverUrl: string | null
  fullAudioUrl: string
  vocalUrl: string
  duration: number | null
  sortOrder: number
  status: number
  deletedAt: string | null
  createdAt: string
  updatedAt: string
}

/**
 * 上传音乐参数
 */
export interface UploadMusicParams {
  title: string
  artist?: string
  cover?: string  // 封面URL（字符串）
  fullAudio: File
  vocalAudio: File
}

/**
 * 更新音乐参数
 */
export interface UpdateMusicParams {
  title?: string
  artist?: string
  cover?: string
  sortOrder?: number
  status?: number
}

const musicService = {
  /**
   * 获取音乐列表
   */
  getMusicList: (params?: any) => {
    return get<Music[]>('/admin/music/list', params)
  },

  /**
   * 获取音乐详情
   */
  getMusicById: (id: number) => {
    return get<Music>(`/music/${id}`)
  },

  /**
   * 上传音乐
   */
  uploadMusic: (params: UploadMusicParams) => {
    const formData = new FormData()
    formData.append('title', params.title)
    if (params.artist) {
      formData.append('artist', params.artist)
    }
    if (params.cover) {
      // 封面是已上传的图片URL，直接作为字符串传递
      formData.append('coverUrl', params.cover)
    }
    formData.append('fullAudio', params.fullAudio)
    formData.append('vocalAudio', params.vocalAudio)

    // 不设置 Content-Type，让 axios 自动处理 boundary；上传需要更长超时
    return post<number>('/admin/music', formData, { timeout: 60000 })
  },

  /**
   * 更新音乐信息
   */
  updateMusic: (id: number, params: UpdateMusicParams) => {
    const formData = new URLSearchParams()
    if (params.title) formData.append('title', params.title)
    if (params.artist !== undefined) formData.append('artist', params.artist)
    if (params.cover) formData.append('coverUrl', params.cover)
    if (params.sortOrder !== undefined) formData.append('sortOrder', String(params.sortOrder))
    if (params.status !== undefined) formData.append('status', String(params.status))

    return put<boolean>(`/admin/music/${id}`, formData, {
      headers: {
        'Content-Type': 'application/x-www-form-urlencoded'
      }
    })
  },

  /**
   * 删除音乐（硬删除）
   */
  deleteMusic: (id: number) => {
    return del<boolean>(`/admin/music/${id}`)
  },

  /**
   * 更新排序
   */
  updateSortOrder: (ids: number[]) => {
    const params = new URLSearchParams()
    ids.forEach((id) => params.append('ids', String(id)))
    return put<boolean>(`/admin/music/sort?${params.toString()}`)
  },

  /**
   * 批量删除音乐（硬删除 + 清理文件）
   */
  batchDelete: (ids: number[]) => {
    return post<boolean>('/admin/music/batch', ids)
  }
}

export default musicService
