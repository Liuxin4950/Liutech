import { describe, it, expect, vi, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useCategoryStore } from '@/stores/category'
import { CategoryService } from '@/services/category'

vi.mock('@/services/category', () => ({
  CategoryService: {
    getCategories: vi.fn(),
    getCategoryById: vi.fn()
  }
}))

const mockCategoryService = vi.mocked(CategoryService)

describe('useCategoryStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  describe('initial state', () => {
    it('should have empty categories and false isLoading', () => {
      const store = useCategoryStore()
      expect(store.categories).toEqual([])
      expect(store.isLoading).toBe(false)
    })
  })

  describe('categoriesWithCount', () => {
    it('should filter categories with postCount > 0', () => {
      const store = useCategoryStore()
      store.categories = [
        { id: 1, name: 'Tech', postCount: 5 },
        { id: 2, name: 'Empty', postCount: 0 },
        { id: 3, name: 'Life', postCount: 3 }
      ]
      expect(store.categoriesWithCount).toEqual([
        { id: 1, name: 'Tech', postCount: 5 },
        { id: 3, name: 'Life', postCount: 3 }
      ])
    })

    it('should exclude categories without postCount', () => {
      const store = useCategoryStore()
      store.categories = [
        { id: 1, name: 'Tech' },
        { id: 2, name: 'Life', postCount: 2 }
      ]
      expect(store.categoriesWithCount).toEqual([
        { id: 2, name: 'Life', postCount: 2 }
      ])
    })
  })

  describe('getCategoryById', () => {
    it('should return category by id', () => {
      const store = useCategoryStore()
      store.categories = [
        { id: 1, name: 'Tech' },
        { id: 2, name: 'Life' }
      ]
      expect(store.getCategoryById(2)).toEqual({ id: 2, name: 'Life' })
    })

    it('should return undefined for non-existent id', () => {
      const store = useCategoryStore()
      store.categories = [{ id: 1, name: 'Tech' }]
      expect(store.getCategoryById(999)).toBeUndefined()
    })
  })

  describe('isDataStale', () => {
    it('should be true when no data fetched', () => {
      const store = useCategoryStore()
      expect(store.isDataStale).toBe(true)
    })

    it('should be false right after fetch', () => {
      const store = useCategoryStore()
      store.lastFetchTime = Date.now()
      expect(store.isDataStale).toBe(false)
    })

    it('should be true after cache duration expires', () => {
      const store = useCategoryStore()
      store.lastFetchTime = Date.now() - 6 * 60 * 1000 // 6 minutes ago
      expect(store.isDataStale).toBe(true)
    })
  })

  describe('fetchCategories', () => {
    it('should fetch categories from service', async () => {
      const categories = [
        { id: 1, name: 'Tech', postCount: 10 },
        { id: 2, name: 'Life', postCount: 5 }
      ]
      mockCategoryService.getCategories.mockResolvedValue(categories)

      const store = useCategoryStore()
      const result = await store.fetchCategories()

      expect(mockCategoryService.getCategories).toHaveBeenCalled()
      expect(result).toEqual(categories)
      expect(store.categories).toEqual(categories)
    })

    it('should use cache when data is fresh', async () => {
      const categories = [{ id: 1, name: 'Tech' }]
      mockCategoryService.getCategories.mockResolvedValue(categories)

      const store = useCategoryStore()
      await store.fetchCategories()
      await store.fetchCategories() // second call should use cache

      expect(mockCategoryService.getCategories).toHaveBeenCalledTimes(1)
    })

    it('should force refresh when forceRefresh is true', async () => {
      mockCategoryService.getCategories.mockResolvedValue([])

      const store = useCategoryStore()
      await store.fetchCategories()
      await store.fetchCategories(true)

      expect(mockCategoryService.getCategories).toHaveBeenCalledTimes(2)
    })

    it('should return empty array on error', async () => {
      mockCategoryService.getCategories.mockRejectedValue(new Error('network'))

      const store = useCategoryStore()
      const result = await store.fetchCategories()

      expect(result).toEqual([])
    })

    it('should set isLoading during fetch', async () => {
      let resolve: (v: any) => void
      mockCategoryService.getCategories.mockImplementation(() => new Promise(r => { resolve = r }))

      const store = useCategoryStore()
      const promise = store.fetchCategories()
      expect(store.isLoading).toBe(true)

      resolve!([])
      await promise
      expect(store.isLoading).toBe(false)
    })
  })

  describe('fetchCategoryById', () => {
    it('should return cached category if exists', async () => {
      const store = useCategoryStore()
      store.categories = [{ id: 1, name: 'Tech' }]

      const result = await store.fetchCategoryById(1)

      expect(result).toEqual({ id: 1, name: 'Tech' })
      expect(mockCategoryService.getCategoryById).not.toHaveBeenCalled()
    })

    it('should fetch from service when not cached', async () => {
      const category = { id: 5, name: 'New Category' }
      mockCategoryService.getCategoryById.mockResolvedValue(category)

      const store = useCategoryStore()
      const result = await store.fetchCategoryById(5)

      expect(mockCategoryService.getCategoryById).toHaveBeenCalledWith(5)
      expect(result).toEqual(category)
      expect(store.categories).toContainEqual(category)
    })

    it('should call service to fetch non-cached category', async () => {
      const store = useCategoryStore()
      store.categories = [{ id: 1, name: 'Old Name' }]

      const updated = { id: 99, name: 'Brand New' }
      mockCategoryService.getCategoryById.mockResolvedValue(updated)

      const result = await store.fetchCategoryById(99)

      expect(mockCategoryService.getCategoryById).toHaveBeenCalledWith(99)
      expect(result).toEqual(updated)
      // New category should be added to the list
      expect(store.categories.length).toBe(2)
    })

    it('should return null on error', async () => {
      mockCategoryService.getCategoryById.mockRejectedValue(new Error('not found'))

      const store = useCategoryStore()
      const result = await store.fetchCategoryById(999)

      expect(result).toBeNull()
    })
  })

  describe('initCategories', () => {
    it('should fetch categories when empty', async () => {
      mockCategoryService.getCategories.mockResolvedValue([])

      const store = useCategoryStore()
      await store.initCategories()

      expect(mockCategoryService.getCategories).toHaveBeenCalled()
    })

    it('should fetch categories when stale', async () => {
      mockCategoryService.getCategories.mockResolvedValue([])

      const store = useCategoryStore()
      store.categories = [{ id: 1, name: 'Old' }]
      store.lastFetchTime = Date.now() - 10 * 60 * 1000 // 10 minutes ago

      await store.initCategories()

      expect(mockCategoryService.getCategories).toHaveBeenCalled()
    })

    it('should not fetch when data is fresh and not empty', async () => {
      const store = useCategoryStore()
      store.categories = [{ id: 1, name: 'Tech' }]
      store.lastFetchTime = Date.now()

      await store.initCategories()

      expect(mockCategoryService.getCategories).not.toHaveBeenCalled()
    })
  })

  describe('clearCache', () => {
    it('should reset categories and lastFetchTime', () => {
      const store = useCategoryStore()
      store.categories = [{ id: 1, name: 'Tech' }]
      store.lastFetchTime = Date.now()

      store.clearCache()

      expect(store.categories).toEqual([])
      expect(store.lastFetchTime).toBe(0)
    })
  })

  describe('refreshCategories', () => {
    it('should force fetch categories', async () => {
      mockCategoryService.getCategories.mockResolvedValue([{ id: 1, name: 'Fresh' }])

      const store = useCategoryStore()
      const result = await store.refreshCategories()

      expect(mockCategoryService.getCategories).toHaveBeenCalled()
      expect(result).toEqual([{ id: 1, name: 'Fresh' }])
    })
  })
})
