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
  /** 最近一次请求的错误信息；null 表示无错误。页面据此区分「真的没有数据」与「请求失败」 */
  const error = ref<string | null>(null)

  // 缓存时间（5分钟）
  const CACHE_DURATION = 5 * 60 * 1000

  // 计算属性
  const categoriesWithCount = computed(() => {
    return categories.value.filter(category => category.postCount && category.postCount > 0)
  })

  const getCategoryById = (id: number) => categories.value.find(category => category.id === id)

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
      error.value = null

      return categories.value
    } catch (err) {
      // 不再吞掉异常返回空数组：请求失败必须能被调用方感知
      error.value = err instanceof Error ? err.message : '获取分类列表失败'
      throw err
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
    const localCategory = getCategoryById(id)
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
    } catch (err) {
      // 同上：抛出以便调用方进入错误态，而不是误判为「分类不存在」
      error.value = err instanceof Error ? err.message : '获取分类详情失败'
      throw err
    }
  }

  /**
   * 初始化分类数据
   * Pinia persist 插件会自动恢复状态，此处仅检查数据是否过期并按需刷新
   * 注意：启动阶段由 main.ts 无 await 调用，失败时需自行兜底，避免未处理的 Promise 拒绝
   */
  const initCategories = async () => {
    if (categories.value.length === 0 || isDataStale.value) {
      try {
        await fetchCategories(true)
      } catch (err) {
        // 错误已记入 error 状态，后续页面进入时会再次尝试并展示错误态
        console.error('初始化分类失败:', err)
      }
    }
  }

  /**
   * 清除缓存
   */
  const clearCache = () => {
    categories.value = []
    lastFetchTime.value = 0
    error.value = null
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
    error,

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
