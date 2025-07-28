import { get, post } from './api'

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
  createdAt: string
  updatedAt?: string
}

// 文章详情接口
export interface PostDetail extends PostListItem {
  content: string
}

// 分页响应接口
export interface PageResponse<T> {
  records: T[]
  total: number
  size: number
  current: number
  pages: number
}

// 文章查询参数接口
export interface PostQueryParams {
  page?: number
  size?: number
  categoryId?: number
  tagId?: number
  keyword?: string
  sortBy?: 'latest' | 'popular'
}

// 创建文章请求接口
export interface CreatePostRequest {
  title: string
  content: string
  summary?: string
  categoryId: number
  status: 'draft' | 'published'
  tagIds?: number[]
}

// 创建文章响应接口
export interface CreatePostResponse {
  id: number
  title: string
  status: string
  createdAt: string
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
}

export default PostService