import { get, post, put, del } from './api'

// 文章分类信息接口
interface CategoryInfo {
  id: number
  name: string
}

// 文章作者信息接口
interface AuthorInfo {
  id: number
  username: string
  avatarUrl?: string
}

// 文章标签信息接口
interface TagInfo {
  id: number
  name: string
}

// 文章系列信息接口
interface SeriesInfo {
  id: number
  name: string
  description?: string
  coverImage?: string
  sort?: number
  totalCount?: number
}

// 系列目录项（文章详情页系列导航用）
interface SeriesCatalogItem {
  id: number
  title: string
  sort: number
  current: boolean
}

// 文章列表项接口
export interface PostListItem {
  id: number
  title: string
  summary?: string
  category: CategoryInfo
  author: AuthorInfo
  tags?: TagInfo[]
  series?: SeriesInfo | null
  commentCount: number
  coverImage?: string
  thumbnail?: string
  viewCount: number
  likeCount: number
  favoriteCount: number
  likeStatus: number  // 0-未点赞, 1-已点赞
  favoriteStatus: number  // 0-未收藏, 1-已收藏
  status: 'draft' | 'published' | 'archived'
  createdAt: string
  updatedAt?: string
  viewedAt?: string  // 浏览历史接口返回的最近浏览时间
}

// 文章详情接口
export interface PostDetail extends PostListItem {
  content: string
  // 系列目录（同系列文章导航）
  seriesCatalog?: SeriesCatalogItem[]
  // 文章附件列表（公开查询，不限上传者）
  attachments?: PostAttachment[]
}

// 分页响应接口
export interface PageResponse<T> {
  records: T[]
  total: number
  size: number
  current: number
  pages: number
  hasNext?: boolean
  hasPrevious?: boolean
}

// 文章查询参数接口
export interface PostQueryParams {
  page?: number
  size?: number
  categoryId?: number
  tagId?: number
  seriesId?: number
  keyword?: string
  sortBy?: 'latest' | 'popular'
  status?: 'draft' | 'published'
  authorId?: number
}

// 创建文章请求接口
interface CreatePostRequest {
  title: string
  content: string
  summary?: string
  categoryId: number
  status: 'draft' | 'published'
  tagIds?: number[]
  seriesId?: number | null
  seriesSort?: number
  coverImage?: string
  thumbnail?: string
  viewCount?: number
  likeCount?: number
  draftKey?: string
}

// 创建文章响应接口
interface CreatePostResponse {
  id: number
  title: string
  status: string
  createdAt: string
}

// 更新文章请求接口
interface UpdatePostRequest {
  id?: number
  title?: string
  content?: string
  summary?: string
  categoryId?: number
  status?: 'draft' | 'published'
  tagIds?: number[]
  seriesId?: number | null
  seriesSort?: number
  coverImage?: string
  thumbnail?: string
  viewCount?: number
  likeCount?: number
}

// 附件相关接口
interface AttachmentInfo {
  id: number
  name: string
  size: number
  type: string
  url: string
  resourceId: number
  attachmentId?: number
  createdAt: string
}

interface AttachmentUploadResponse {
  resourceId: number
  attachmentId?: number
  fileUrl: string
  fileName: string
  fileSize: number
}

// 文章详情返回中的附件信息（与后端 PostDetailResp.AttachmentInfo 对应）
interface PostAttachment {
  attachmentId: number
  resourceId: number
  fileName: string
  fileUrl: string
  externalLink?: string        // 外部链接地址
  resourceType?: string        // 资源类型：file/link/both
  purchasedNote?: string       // 购买后显示的说明
  pointsNeeded?: number
  createdTime: string
  // 是否已购买（后端计算字段：免费、本人上传或已购买都为 true）
  purchased?: boolean
}

/**
 * 文章服务类
 */
export class PostService {
  /**
   * 获取文章列表
   * @param params 查询参数
   * @returns 分页文章列表
   */
  static async getPostList(params: PostQueryParams = {}): Promise<PageResponse<PostListItem>> {
    try {
      const { sortBy, ...rest } = params
      const response = await get('/posts', {
        ...rest,
        ...(sortBy ? { sort: sortBy === 'popular' ? 'hot' : sortBy } : {})
      })
      return response.data
    } catch (error) {
      console.error('获取文章列表失败:', error)
      throw error
    }
  }

  /**
   * 获取文章详情
   * @param id 文章ID
   * @returns 文章详情
   */
  static async getPostDetail(id: number): Promise<PostDetail> {
    try {
      const response = await get(`/posts/${id}`)
      return response.data
    } catch (error) {
      console.error('获取文章详情失败:', error)
      throw error
    }
  }

  /**
   * 获取文章详情（管理员，可查草稿）
   * 仅用于 CreatePost.vue 编辑模式加载草稿/未发布文章，需要 ADMIN 角色。
   * 普通用户应使用 getPostDetail()。
   */
  static async getPostDetailForAdmin(id: number): Promise<PostDetail> {
    try {
      const response = await get(`/admin/posts/${id}`)
      return response.data
    } catch (error) {
      console.error('获取文章详情(管理员)失败:', error)
      throw error
    }
  }

  /**
   * 获取最新文章列表
   * @param limit 返回数量，默认10
   * @returns 最新文章列表
   */
  static async getLatestPosts(limit: number = 10): Promise<PostListItem[]> {
    try {
      const response = await get('/posts/latest', { limit })
      return response.data
    } catch (error) {
      console.error('获取最新文章失败:', error)
      throw error
    }
  }

  /**
   * 获取热门文章（按点赞、评论、访问量综合热度排序）
   * @param limit 返回数量，默认10
   * @returns 热门文章列表
   */
  static async getHotPosts(limit: number = 10): Promise<PostListItem[]> {
    try {
      const response = await get('/posts/hot', { limit })
      return response.data
    } catch (error) {
      console.error('获取热门文章失败:', error)
      throw error
    }
  }

  /**
   * 获取个性化推荐文章（需登录）
   * 基于浏览历史推荐同分类/同标签的未读文章，不足时后端用热门补足。
   * 未登录调用会返回 401，调用方应回退到 getHotPosts。
   * @param limit 返回数量，默认5
   * @returns 推荐文章列表
   */
  static async getRecommendations(limit: number = 5): Promise<PostListItem[]> {
    try {
      const response = await get('/posts/recommendations', { limit })
      return response.data
    } catch (error) {
      console.error('获取个性化推荐失败:', error)
      throw error
    }
  }

  /**
   * 创建文章
   * @param postData 文章数据
   * @returns 创建结果
   */
  static async createPost(postData: CreatePostRequest): Promise<CreatePostResponse> {
    try {
      const response = await post('/posts', postData)
      return response.data
    } catch (error) {
      console.error('创建文章失败:', error)
      throw error
    }
  }

  /**
   * 获取草稿箱列表
   * @param params 查询参数
   * @returns 分页草稿列表
   */
  static async getDraftList(params: PostQueryParams = {}): Promise<PageResponse<PostListItem>> {
    try {
      const response = await get('/posts/drafts', params)
      return response.data
    } catch (error) {
      console.error('获取草稿列表失败:', error)
      throw error
    }
  }

  /**
   * 更新文章
   * @param id 文章ID
   * @param postData 更新数据
   * @returns 更新结果
   */
  static async updatePost(id: number, postData: UpdatePostRequest): Promise<boolean> {
    try {
      const response = await put(`/posts/${id}`, postData)
      return response.data
    } catch (error) {
      console.error('更新文章失败:', error)
      throw error
    }
  }

  /**
   * 删除文章
   * @param id 文章ID
   * @returns 删除结果
   */
  static async deletePost(id: number): Promise<void> {
    try {
      await del(`/posts/${id}`)
    } catch (error) {
      console.error('删除文章失败:', error)
      throw error
    }
  }

  /**
   * 发布文章
   * @param id 文章ID
   * @returns 发布结果
   */
  static async publishPost(id: number): Promise<boolean> {
    try {
      const response = await put(`/posts/${id}/publish`)
      return response.data
    } catch (error) {
      console.error('发布文章失败:', error)
      throw error
    }
  }

  /**
   * 取消发布文章
   * @param id 文章ID
   * @returns 取消发布结果
   */
  static async unpublishPost(id: number): Promise<void> {
    try {
      await put(`/posts/${id}/unpublish`)
    } catch (error) {
      console.error('取消发布文章失败:', error)
      throw error
    }
  }

  /**
   * 获取用户文章列表（包括草稿和已发布）
   * @param params 查询参数
   * @returns 分页文章列表
   */
  static async getMyPosts(params: PostQueryParams = {}): Promise<PageResponse<PostListItem>> {
    try {
      // 不设置status过滤，获取用户所有文章
      const response = await get('/posts/my', params)
      return response.data
    } catch (error) {
      console.error('获取我的文章失败:', error)
      throw error
    }
  }

  /**
   * 点赞文章
   * @param id 文章ID
   * @returns 操作结果
   */
  static async likePost(id: number): Promise<void> {
    try {
      await post(`/posts/${id}/like`)
    } catch (error) {
      console.error('点赞文章失败:', error)
      throw error
    }
  }

  /**
   * 收藏文章
   * @param id 文章ID
   * @returns 操作结果
   */
  static async favoritePost(id: number): Promise<void> {
    try {
      await post(`/posts/${id}/favorite`)
    } catch (error) {
      console.error('收藏文章失败:', error)
      throw error
    }
  }

  /**
   * 上传附件
   * 大文件（>5MB）走分片上传：单片 5MB，整个请求在 CDN 空闲超时阈值内完成，
   * 避免大请求回源耗时过长被边缘节点掐断（表现为 ERR_HTTP2_PROTOCOL_ERROR / 响应丢失）。
   * @param file 文件
   * @param draftKey 草稿键
   * @param type 附件类型（默认 attachment）
   * @param downloadType 下载类型（0-免费，1-积分）
   * @param pointsNeeded 所需积分
   */
  static async uploadAttachment(file: File, draftKey: string, type: string = 'attachment', downloadType: number = 0, pointsNeeded: number = 0): Promise<AttachmentUploadResponse> {
    try {
      const CHUNK_SIZE = 5 * 1024 * 1024

      // 大文件分片上传
      if (file.size > CHUNK_SIZE) {
        const uploadId = `${Date.now()}-${Math.random().toString(36).slice(2, 10)}`
        const totalChunks = Math.ceil(file.size / CHUNK_SIZE)

        for (let i = 0; i < totalChunks; i++) {
          const chunk = file.slice(i * CHUNK_SIZE, Math.min((i + 1) * CHUNK_SIZE, file.size))
          const chunkForm = new FormData()
          chunkForm.append('file', chunk, file.name)
          chunkForm.append('uploadId', uploadId)
          chunkForm.append('chunkIndex', String(i))
          chunkForm.append('totalChunks', String(totalChunks))
          chunkForm.append('fileName', file.name)
          await post('/upload/resource/chunk', chunkForm as any, {
            headers: { 'Content-Type': 'multipart/form-data' }
          } as any)
        }

        const mergeForm = new FormData()
        mergeForm.append('uploadId', uploadId)
        mergeForm.append('totalChunks', String(totalChunks))
        mergeForm.append('fileName', file.name)
        mergeForm.append('draftKey', draftKey)
        mergeForm.append('type', type)
        mergeForm.append('downloadType', downloadType.toString())
        mergeForm.append('pointsNeeded', pointsNeeded.toString())
        const mergeResponse = await post('/upload/resource/merge', mergeForm as any, {
          headers: { 'Content-Type': 'multipart/form-data' }
        } as any)
        return mergeResponse.data
      }

      // 小文件直接上传
      const formData = new FormData()
      formData.append('file', file)
      formData.append('type', type)
      formData.append('draftKey', draftKey)
      formData.append('downloadType', downloadType.toString())
      formData.append('pointsNeeded', pointsNeeded.toString())

      const response = await post('/upload/resource', formData as any, {
        headers: { 'Content-Type': 'multipart/form-data' }
      } as any)
      return response.data
    } catch (error) {
      console.error('上传附件失败:', error)
      throw error
    }
  }

  /** 获取文章附件 */
  static async getPostAttachments(postId: number): Promise<AttachmentInfo[]> {
    try {
      const response = await get(`/upload/attachments/post/${postId}`)
      return response.data as any
    } catch (error) {
      console.error('获取文章附件失败:', error)
      throw error
    }
  }

  /** 删除附件 */
  static async deleteAttachment(resourceId: number): Promise<void> {
    try {
      await del(`/upload/attachments/${resourceId}`)
    } catch (error) {
      console.error('删除附件失败:', error)
      throw error
    }
  }

  /** 创建外部链接资源 */
  static async createExternalLinkResource(
    name: string,
    description: string,
    externalLink: string,
    purchasedNote: string,
    draftKey: string,
    type: string,
    downloadType: number,
    pointsNeeded: number
  ): Promise<AttachmentUploadResponse> {
    try {
      const formData = new FormData()
      formData.append('name', name)
      if (description) formData.append('description', description)
      formData.append('externalLink', externalLink)
      if (purchasedNote) formData.append('purchasedNote', purchasedNote)
      formData.append('draftKey', draftKey)
      formData.append('type', type)
      formData.append('downloadType', downloadType.toString())
      formData.append('pointsNeeded', pointsNeeded.toString())

      const response = await post('/upload/resource/external', formData, {
        headers: { 'Content-Type': 'multipart/form-data' }
      } as any)
      return response.data
    } catch (error) {
      console.error('创建外部链接资源失败:', error)
      throw error
    }
  }

  /** 购买资源（扣积分） */
  static async purchaseResource(resourceId: number): Promise<void> {
    try {
      await post(`/resource/purchase/${resourceId}`)
    } catch (error) {
      console.error('购买资源失败:', error)
      throw error
    }
  }

  /** 下载资源文件（通过后端验证） */
  static async downloadResource(resourceId: number, fileName: string, onProgress?: (percent: number) => void): Promise<void> {
    try {
      // 导入axios实例用于文件下载
      const { getAxiosInstance } = await import('./api')
      const axiosInstance = getAxiosInstance()

      const response = await axiosInstance.get(`/resource/download/${resourceId}`, {
        responseType: 'blob',
        onDownloadProgress: (e) => {
          // 后端已设 Content-Length（本地磁盘与 COS 统一经存储抽象获取），total 有值才能算百分比
          if (onProgress && e.total && e.total > 0) {
            onProgress(Math.min(99, Math.round((e.loaded / e.total) * 100)))
          }
        }
      })

      // 拦截器会把非标准响应包成 { code, message, data }，blob 在 data 里
      const rawData: any = (response.data as any)?.data ?? response.data

      // 创建下载链接
      const blob = new Blob([rawData])
      const url = window.URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = fileName || 'download'
      document.body.appendChild(a)
      a.click()

      // 清理
      window.URL.revokeObjectURL(url)
      document.body.removeChild(a)
    } catch (error) {
      console.error('下载资源失败:', error)
      throw error
    }
  }
  /** 更新附件收费设置（下载类型与积分） */
  static async updateAttachmentMeta(resourceId: number, downloadType: number = 0, pointsNeeded: number = 0): Promise<void> {
    try {
      await put(`/upload/attachments/${resourceId}/meta`, null as any, { params: { downloadType, pointsNeeded } } as any)
    } catch (error) {
      console.error('更新附件收费设置失败:', error)
      throw error
    }
  }

  /**
   * 获取用户收藏的文章列表
   * @param params 查询参数
   * @returns 分页收藏文章列表
   * @author 刘鑫
   * @date 2025-09-26T00:20:02+08:00
   */
  static async getFavoritePosts(params: PostQueryParams = {}): Promise<PageResponse<PostListItem>> {
    try {
      const response = await get('/posts/favorites', params)
      return response.data
    } catch (error) {
      console.error('获取收藏文章列表失败:', error)
      throw error
    }
  }

  /**
   * 记录文章浏览（需登录）：同一文章重复浏览只刷新时间，列表置顶
   * @param postId 文章ID
   * @author 刘鑫
   * @date 2026-08-07
   */
  static async recordView(postId: number): Promise<boolean> {
    try {
      const response = await post(`/posts/${postId}/view`)
      return response.data
    } catch (error) {
      console.error('记录浏览失败:', error)
      throw error
    }
  }

  /**
   * 获取当前用户的浏览历史（需登录，按最近浏览时间倒序）
   * @param params 查询参数
   * @returns 分页浏览历史文章列表（含 viewedAt 最近浏览时间）
   * @author 刘鑫
   * @date 2026-08-07
   */
  static async getViewHistory(params: PostQueryParams = {}): Promise<PageResponse<PostListItem>> {
    try {
      const response = await get('/posts/view-history', params)
      return response.data
    } catch (error) {
      console.error('获取浏览历史失败:', error)
      throw error
    }
  }

  /**
   * 清空当前用户的浏览历史（需登录）
   * @author 刘鑫
   * @date 2026-08-07
   */
  static async clearViewHistory(): Promise<boolean> {
    try {
      const response = await del('/posts/view-history')
      return response.data
    } catch (error) {
      console.error('清空浏览历史失败:', error)
      throw error
    }
  }
}

export default PostService
