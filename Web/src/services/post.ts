import { get, post, put, del } from './api'

// 文章分类信息接口
export interface CategoryInfo {
  id: number
  name: string
}

// 文章作者信息接口
export interface AuthorInfo {
  id: number
  username: string
  avatarUrl?: string
}

// 文章标签信息接口
export interface TagInfo {
  id: number
  name: string
}

// 文章列表项接口
export interface PostListItem {
  id: number
  title: string
  summary?: string
  category: CategoryInfo
  author: AuthorInfo
  tags?: TagInfo[]
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
}

// 文章详情接口
export interface PostDetail extends PostListItem {
  content: string
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
  keyword?: string
  sortBy?: 'latest' | 'popular'
  status?: 'draft' | 'published'
  authorId?: number
}

// 创建文章请求接口
export interface CreatePostRequest {
  title: string
  content: string
  summary?: string
  categoryId: number
  status: 'draft' | 'published'
  tagIds?: number[]
  coverImage?: string
  thumbnail?: string
  viewCount?: number
  likeCount?: number
  draftKey?: string
}

// 创建文章响应接口
export interface CreatePostResponse {
  id: number
  title: string
  status: string
  createdAt: string
}

// 更新文章请求接口
export interface UpdatePostRequest {
  id?: number
  title?: string
  content?: string
  summary?: string
  categoryId?: number
  status?: 'draft' | 'published'
  tagIds?: number[]
  coverImage?: string
  thumbnail?: string
  viewCount?: number
  likeCount?: number
}

// 附件相关接口
export interface AttachmentInfo {
  id: number
  name: string
  size: number
  type: string
  url: string
  resourceId: number
  attachmentId?: number
  createdAt: string
}

export interface AttachmentUploadResponse {
  resourceId: number
  attachmentId?: number
  fileUrl: string
  fileName: string
  fileSize: number
}

// 文章详情返回中的附件信息（与后端 PostDetailResl.AttachmentInfo 对应）
export interface PostAttachment {
  attachmentId: number
  resourceId: number
  fileName: string
  fileUrl: string
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
      const response = await get('/posts', params)
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
   * 获取热门文章列表
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
  static async updatePost(id: number, postData: UpdatePostRequest): Promise<PostDetail> {
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
  static async publishPost(id: number): Promise<PostDetail> {
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
   * 获取文章列表（别名方法）
   * @param params 查询参数
   * @returns 分页文章列表
   */
  static async getPosts(params: PostQueryParams = {}): Promise<PageResponse<PostListItem>> {
    return this.getPostList(params)
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
   * @param file 文件
   * @param draftKey 草稿键
   * @param type 附件类型（默认 attachment）
   * @param downloadType 下载类型（0-免费，1-积分）
   * @param pointsNeeded 所需积分
   */
  static async uploadAttachment(file: File, draftKey: string, type: string = 'attachment', downloadType: number = 0, pointsNeeded: number = 0): Promise<AttachmentUploadResponse> {
    try {
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

  /** 获取草稿附件 */
  static async getDraftAttachments(draftKey: string): Promise<AttachmentInfo[]> {
    try {
      const response = await get(`/upload/attachments/draft/${draftKey}`)
      return response.data as any
    } catch (error) {
      console.error('获取草稿附件失败:', error)
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

  /** 购买资源（扣积分） */
  static async purchaseResource(resourceId: number): Promise<void> {
    try {
      await post(`/api/resource/purchase/${resourceId}`)
    } catch (error) {
      console.error('购买资源失败:', error)
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
}

export default PostService