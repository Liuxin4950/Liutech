import api from './api'

/**
 * 统计数据接口
 */
export interface StatisticsData {
  postCount: number
  categoryCount: number
  tagCount: number
  userCount: number
}

/**
 * 获取仪表盘统计数据
 * @returns Promise<StatisticsData>
 */
export const getStatistics = async (): Promise<StatisticsData> => {
  try {
    // 并发请求所有统计数据
    const [postsRes, categoriesRes, tagsRes, usersRes] = await Promise.all([
      api.get('/admin/posts?page=1&size=1'), // 只获取第一页来获取总数
      api.get('/admin/categories?page=1&size=1'),
      api.get('/admin/tags?page=1&size=1'),
      api.get('/admin/users?page=1&size=1')
    ])

    return {
      postCount: postsRes.data.data.total || 0,
      categoryCount: categoriesRes.data.data.total || 0,
      tagCount: tagsRes.data.data.total || 0,
      userCount: usersRes.data.data.total || 0
    }
  } catch (error) {
    console.error('获取统计数据失败:', error)
    // 返回默认值
    return {
      postCount: 0,
      categoryCount: 0,
      tagCount: 0,
      userCount: 0
    }
  }
}

/**
 * 获取最近活动数据（可扩展）
 */
export const getRecentActivities = async () => {
  try {
    // 获取最近的文章
    const recentPosts = await api.get('/admin/posts?page=1&size=5')
    return recentPosts.data.data.items || []
  } catch (error) {
    console.error('获取最近活动失败:', error)
    return []
  }
}