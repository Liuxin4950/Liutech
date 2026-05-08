/**
 * 分类状态管理
 * 使用 Pinia 管理分类数据，支持持久化存储
 */
import {defineStore} from 'pinia'
import {computed, ref} from 'vue'
import {type Category, CategoryService} from '../services/category'

export const useCategoryStore = defineStore('category', () => {
  // 状态
  const categories = ref<Category[]>([])
  const isLoading = ref(false)
  const lastFetchTime = ref<number>(0)

  // 缓存时间（5分钟）
  const CACHE_DURATION = 5 * 60 * 1000

  // 计算属性
  const categoriesWithCount = computed(() => {
    return categories.value.filter(category => category.postCount && category.postCount > 0)
  })

  const getCategoryById = computed(() => {
    return (id: number) => categories.value.find(category => category.id === id)
  })

  const isDataStale = computed(() => {
    return Date.now() - lastFetchTime.value > CACHE_DURATION
  })

  // 动作
  /**
   * 获取所有分类
   * @param forceRefresh 是否强制刷新
   */
  const fetchCategories = async (forceRefresh = false) => {
    // 如果数据还在缓存期内且不强制刷新，直接返回
    if (!forceRefresh && categories.value.length > 0 && !isDataStale.value) {
      return categories.value
    }

    isLoading.value = true
    try {
      const response = await CategoryService.getCategories()
      categories.value = response || []
      lastFetchTime.value = Date.now()

      return categories.value
    } catch (error) {
      console.error('获取分类列表失败:', error)
      return []
    } finally {
      isLoading.value = false
    }
  }

  /**
   * 根据ID获取分类详情
   * @param id 分类ID
   */
  const fetchCategoryById = async (id: number) => {
    // 先从本地缓存查找
    const localCategory = getCategoryById.value(id)
    if (localCategory) {
      return localCategory
    }

    try {
      const response = await CategoryService.getCategoryById(id)

      // 更新本地缓存
      if (response) {
        const existingIndex = categories.value.findIndex(cat => cat.id === id)
        if (existingIndex >= 0) {
          categories.value[existingIndex] = response
        } else {
          categories.value.push(response)
        }
      }

      return response
    } catch (error) {
      console.error('获取分类详情失败:', error)
      return null
    }
  }

  /**
   * 初始化分类数据
   * Pinia persist 插件会自动恢复状态，此处仅检查数据是否过期并按需刷新
   */
  const initCategories = async () => {
    if (categories.value.length === 0 || isDataStale.value) {
      await fetchCategories(true)
    }
  }

  /**
   * 清除缓存
   */
  const clearCache = () => {
    categories.value = []
    lastFetchTime.value = 0
  }

  /**
   * 刷新分类数据
   */
  const refreshCategories = async () => {
    return await fetchCategories(true)
  }

  return {
    // 状态
    categories,
    isLoading,
    lastFetchTime,

    // 计算属性
    categoriesWithCount,
    getCategoryById,
    isDataStale,

    // 动作
    fetchCategories,
    fetchCategoryById,
    initCategories,
    clearCache,
    refreshCategories
  }
}, {
  persist: {
    key: 'blog-category-store',
    storage: localStorage
  }
})
