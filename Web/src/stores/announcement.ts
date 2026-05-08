/**
 * 公告状态管理
 * 使用 Pinia 管理公告数据，支持持久化存储
 */
import {defineStore} from 'pinia'
import {computed, ref} from 'vue'
import {type Announcement, AnnouncementService} from '../services/announcement'

export const useAnnouncementStore = defineStore('announcement', () => {
  // 状态
  const announcements = ref<Announcement[]>([])
  const topAnnouncements = ref<Announcement[]>([])
  const latestAnnouncements = ref<Announcement[]>([])
  const isLoading = ref(false)
  const isTopLoading = ref(false)
  const isLatestLoading = ref(false)
  const lastFetchTime = ref<number>(0)
  const lastTopFetchTime = ref<number>(0)
  const lastLatestFetchTime = ref<number>(0)

  // 缓存时间（5分钟）
  const CACHE_DURATION = 5 * 60 * 1000

  // 计算属性
  const getAnnouncementById = computed(() => {
    return (id: number) => announcements.value.find(announcement => announcement.id === id)
  })

  const isDataStale = computed(() => {
    return Date.now() - lastFetchTime.value > CACHE_DURATION
  })

  const isTopDataStale = computed(() => {
    return Date.now() - lastTopFetchTime.value > CACHE_DURATION
  })

  const isLatestDataStale = computed(() => {
    return Date.now() - lastLatestFetchTime.value > CACHE_DURATION
  })

  // 动作
  /**
   * 获取有效公告（分页）
   * @param current 当前页
   * @param size 每页大小
   * @param forceRefresh 是否强制刷新
   */
  const fetchValidAnnouncements = async (current: number = 1, size: number = 10, forceRefresh = false) => {
    // 如果数据还在缓存期内且不强制刷新，直接返回
    if (!forceRefresh && announcements.value.length > 0 && !isDataStale.value) {
      return {
        records: announcements.value,
        total: announcements.value.length,
        size,
        current,
        pages: Math.ceil(announcements.value.length / size)
      }
    }

    isLoading.value = true
    try {
      const response = await AnnouncementService.getValidAnnouncements(current, size)
      announcements.value = response.records || []
      lastFetchTime.value = Date.now()

      return response
    } catch (error) {
      console.error('获取公告列表失败:', error)
      return {
        records: [],
        total: 0,
        size,
        current,
        pages: 0
      }
    } finally {
      isLoading.value = false
    }
  }

  /**
   * 获取置顶公告
   * @param limit 限制数量
   * @param forceRefresh 是否强制刷新
   */
  const fetchTopAnnouncements = async (limit = 5, forceRefresh = false) => {
    // 如果数据还在缓存期内且不强制刷新，直接返回
    if (!forceRefresh && topAnnouncements.value.length > 0 && !isTopDataStale.value) {
      return topAnnouncements.value
    }

    isTopLoading.value = true
    try {
      const response = await AnnouncementService.getTopAnnouncements(limit)
      topAnnouncements.value = response || []
      lastTopFetchTime.value = Date.now()

      return topAnnouncements.value
    } catch (error) {
      console.error('获取置顶公告失败:', error)
      return []
    } finally {
      isTopLoading.value = false
    }
  }

  /**
   * 获取最新公告
   * @param limit 限制数量
   * @param forceRefresh 是否强制刷新
   */
  const fetchLatestAnnouncements = async (limit = 10, forceRefresh = false) => {
    // 如果数据还在缓存期内且不强制刷新，直接返回
    if (!forceRefresh && latestAnnouncements.value.length > 0 && !isLatestDataStale.value) {
      return latestAnnouncements.value
    }

    isLatestLoading.value = true
    try {
      const response = await AnnouncementService.getLatestAnnouncements(limit)
      latestAnnouncements.value = response || []
      lastLatestFetchTime.value = Date.now()

      return latestAnnouncements.value
    } catch (error) {
      console.error('获取最新公告失败:', error)
      return []
    } finally {
      isLatestLoading.value = false
    }
  }

  /**
   * 根据ID获取公告详情
   * @param id 公告ID
   */
  const fetchAnnouncementById = async (id: number) => {
    // 先从本地缓存查找
    const localAnnouncement = getAnnouncementById.value(id)
    if (localAnnouncement) {
      return localAnnouncement
    }

    try {
      const response = await AnnouncementService.getAnnouncementById(id)

      // 更新本地缓存
      if (response) {
        const existingIndex = announcements.value.findIndex(announcement => announcement.id === id)
        if (existingIndex >= 0) {
          announcements.value[existingIndex] = response
        } else {
          announcements.value.push(response)
        }
      }

      return response
    } catch (error) {
      console.error('获取公告详情失败:', error)
      return null
    }
  }

  /**
   * 初始化公告数据
   * Pinia persist 插件会自动恢复状态，此处仅检查数据是否过期并按需刷新
   */
  const initAnnouncements = async () => {
    // 如果没有有效的缓存数据，则从服务器获取最新公告
    if (latestAnnouncements.value.length === 0 || isLatestDataStale.value) {
      await fetchLatestAnnouncements(5, true)
    }
  }

  /**
   * 清除缓存
   */
  const clearCache = () => {
    announcements.value = []
    topAnnouncements.value = []
    latestAnnouncements.value = []
    lastFetchTime.value = 0
    lastTopFetchTime.value = 0
    lastLatestFetchTime.value = 0
  }

  /**
   * 刷新公告数据
   */
  const refreshAnnouncements = async () => {
    const promises = [
      fetchLatestAnnouncements(5, true),
      fetchTopAnnouncements(5, true)
    ]
    return await Promise.all(promises)
  }

  /**
   * 刷新最新公告（最常用的刷新方法）
   */
  const refreshLatestAnnouncements = async (limit = 5) => {
    return await fetchLatestAnnouncements(limit, true)
  }

  return {
    // 状态
    announcements,
    topAnnouncements,
    latestAnnouncements,
    isLoading,
    isTopLoading,
    isLatestLoading,
    lastFetchTime,
    lastTopFetchTime,
    lastLatestFetchTime,

    // 计算属性
    getAnnouncementById,
    isDataStale,
    isTopDataStale,
    isLatestDataStale,

    // 动作
    fetchValidAnnouncements,
    fetchTopAnnouncements,
    fetchLatestAnnouncements,
    fetchAnnouncementById,
    initAnnouncements,
    clearCache,
    refreshAnnouncements,
    refreshLatestAnnouncements
  }
}, {
  persist: {
    key: 'blog-announcement-store',
    storage: localStorage
  }
})