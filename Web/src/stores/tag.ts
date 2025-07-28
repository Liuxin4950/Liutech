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
      
      // 持久化到本地存储
      localStorage.setItem('blog_tags', JSON.stringify({
        data: tags.value,
        timestamp: lastFetchTime.value
      }))
      
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
      
      // 持久化到本地存储
      localStorage.setItem('blog_hot_tags', JSON.stringify({
        data: hotTags.value,
        timestamp: lastHotTagsFetchTime.value
      }))
      
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
   * 从本地存储恢复数据，如果数据过期则重新获取
   */
  const initTags = async () => {
    try {
      // 恢复所有标签数据
      const storedTags = localStorage.getItem('blog_tags')
      if (storedTags) {
        const { data, timestamp } = JSON.parse(storedTags)
        
        // 检查数据是否过期
        if (Date.now() - timestamp < CACHE_DURATION) {
          tags.value = data || []
          lastFetchTime.value = timestamp
        }
      }
      
      // 恢复热门标签数据
      const storedHotTags = localStorage.getItem('blog_hot_tags')
      if (storedHotTags) {
        const { data, timestamp } = JSON.parse(storedHotTags)
        
        // 检查数据是否过期
        if (Date.now() - timestamp < CACHE_DURATION) {
          hotTags.value = data || []
          lastHotTagsFetchTime.value = timestamp
        }
      }
    } catch (error) {
      console.warn('恢复标签数据失败:', error)
    }
    
    // 如果没有有效的本地数据，则从服务器获取
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
    localStorage.removeItem('blog_tags')
    localStorage.removeItem('blog_hot_tags')
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
   * 搜索标签
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
    refreshTags
  }
}, {
  persist: {
    key: 'blog-tag-store',
    storage: localStorage
  }
})