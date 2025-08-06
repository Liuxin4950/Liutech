/**
 * 标签状态管理
 * 使用 Pinia 管理标签数据，支持持久化存储
 */
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { TagService, type Tag } from '../services/tag'
import { handleApiError } from '../utils/errorHandler'

export const useTagStore = defineStore('tag', () => {
  // 状态
  const tags = ref<Tag[]>([])
  const hotTags = ref<Tag[]>([])
  const isLoading = ref(false)
  const isHotTagsLoading = ref(false)
  const lastFetchTime = ref<number>(0)
  const lastHotTagsFetchTime = ref<number>(0)
  
  // 缓存时间（5分钟）
  const CACHE_DURATION = 5 * 60 * 1000
  
  // 计算属性
  const tagsWithCount = computed(() => {
    return tags.value.filter(tag => tag.postCount && tag.postCount > 0)
  })
  
  const getTagById = computed(() => {
    return (id: number) => tags.value.find(tag => tag.id === id)
  })
  
  const isDataStale = computed(() => {
    return Date.now() - lastFetchTime.value > CACHE_DURATION
  })
  
  const isHotTagsDataStale = computed(() => {
    return Date.now() - lastHotTagsFetchTime.value > CACHE_DURATION
  })
  
  // 动作
  /**
   * 获取所有标签
   * @param forceRefresh 是否强制刷新
   */
  const fetchTags = async (forceRefresh = false) => {
    // 如果数据还在缓存期内且不强制刷新，直接返回
    if (!forceRefresh && tags.value.length > 0 && !isDataStale.value) {
      return tags.value
    }
    
    isLoading.value = true
    try {
      const response = await TagService.getTags()
      tags.value = response || []
      lastFetchTime.value = Date.now()
      
      // 数据已通过Pinia persist自动持久化
      
      return tags.value
    } catch (error) {
      console.error('获取标签列表失败:', error)
      handleApiError(error)
      return []
    } finally {
      isLoading.value = false
    }
  }
  
  /**
   * 获取热门标签
   * @param limit 限制数量
   * @param forceRefresh 是否强制刷新
   */
  const fetchHotTags = async (limit = 10, forceRefresh = false) => {
    // 如果数据还在缓存期内且不强制刷新，直接返回
    if (!forceRefresh && hotTags.value.length > 0 && !isHotTagsDataStale.value) {
      return hotTags.value
    }
    
    isHotTagsLoading.value = true
    try {
      const response = await TagService.getHotTags(limit)
      hotTags.value = response || []
      lastHotTagsFetchTime.value = Date.now()
      
      // 数据已通过Pinia persist自动持久化
      
      return hotTags.value
    } catch (error) {
      console.error('获取热门标签失败:', error)
      handleApiError(error)
      return []
    } finally {
      isHotTagsLoading.value = false
    }
  }
  
  /**
   * 根据ID获取标签详情
   * @param id 标签ID
   */
  const fetchTagById = async (id: number) => {
    // 先从本地缓存查找
    const localTag = getTagById.value(id)
    if (localTag) {
      return localTag
    }
    
    try {
      const response = await TagService.getTagById(id)
      
      // 更新本地缓存
      if (response) {
        const existingIndex = tags.value.findIndex(tag => tag.id === id)
        if (existingIndex >= 0) {
          tags.value[existingIndex] = response
        } else {
          tags.value.push(response)
        }
        
        // 更新持久化存储
        localStorage.setItem('blog_tags', JSON.stringify({
          data: tags.value,
          timestamp: lastFetchTime.value
        }))
      }
      
      return response
    } catch (error) {
      console.error('获取标签详情失败:', error)
      handleApiError(error)
      return null
    }
  }
  
  /**
   * 根据文章ID获取标签
   * @param postId 文章ID
   */
  const fetchTagsByPostId = async (postId: number) => {
    try {
      const response = await TagService.getTagsByPostId(postId)
      return response || []
    } catch (error) {
      console.error('获取文章标签失败:', error)
      handleApiError(error)
      return []
    }
  }
  
  /**
   * 初始化标签数据
   * 检查缓存数据是否过期，如果过期则重新获取
   */
  const initTags = async () => {
    // 检查数据是否需要刷新（Pinia persist会自动恢复数据）
    const promises = []
    if (tags.value.length === 0 || isDataStale.value) {
      promises.push(fetchTags(true))
    }
    if (hotTags.value.length === 0 || isHotTagsDataStale.value) {
      promises.push(fetchHotTags(10, true))
    }
    
    await Promise.all(promises)
  }
  
  /**
   * 清除缓存
   */
  const clearCache = () => {
    tags.value = []
    hotTags.value = []
    lastFetchTime.value = 0
    lastHotTagsFetchTime.value = 0
    // Pinia persist会自动同步清理
  }
  
  /**
   * 刷新标签数据
   */
  const refreshTags = async () => {
    const promises = [
      fetchTags(true),
      fetchHotTags(10, true)
    ]
    return await Promise.all(promises)
  }
  
  /**
   * 搜索标签（本地过滤）
   * @param keyword 关键词
   */
  const searchTags = computed(() => {
    return (keyword: string) => {
      if (!keyword.trim()) return tags.value
      
      const lowerKeyword = keyword.toLowerCase()
      return tags.value.filter(tag => 
        tag.name.toLowerCase().includes(lowerKeyword)
      )
    }
  })

  /**
   * 搜索标签（调用后端API）
   * @param keyword 关键词
   */
  const searchTagsByAPI = async (keyword: string): Promise<Tag[]> => {
    if (!keyword.trim()) {
      return tags.value
    }
    
    try {
      const result = await TagService.searchTagsByName(keyword.trim())
      return result
    } catch (error) {
      console.error('搜索标签失败:', error)
      handleApiError(error)
      // 如果API调用失败，回退到本地搜索
      return searchTags.value(keyword)
    }
  }
  
  return {
    // 状态
    tags,
    hotTags,
    isLoading,
    isHotTagsLoading,
    lastFetchTime,
    lastHotTagsFetchTime,
    
    // 计算属性
    tagsWithCount,
    getTagById,
    isDataStale,
    isHotTagsDataStale,
    searchTags,
    
    // 动作
    fetchTags,
    fetchHotTags,
    fetchTagById,
    fetchTagsByPostId,
    initTags,
    clearCache,
    refreshTags,
    searchTagsByAPI
  }
}, {
  persist: {
    key: 'blog-tag-store',
    storage: localStorage
  }
})