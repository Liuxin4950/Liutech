import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import type { RouteRecordRaw, RouteLocationMatched } from 'vue-router'

/**
 * 标签页视图接口
 */
export interface TagView {
  name: string           // 路由名称
  path: string           // 路由路径
  title: string          // 标签显示标题
  affix?: boolean        // 是否固定标签（首页固定）
  closable?: boolean     // 是否可关闭
  fullPath?: string      // 完整路径（含查询参数）
}

/**
 * 标签页状态管理
 */
export const useTagsStore = defineStore('tabs', () => {
  const route = useRoute()
  const router = useRouter()

  // 已访问的标签页列表
  const visitedViews = ref<TagView[]>([])

  // 缓存的视图名称列表（用于 KeepAlive include）
  const cachedViews = ref<string[]>([])

  /**
   * 获取固定标签（首页等）
   */
  const affixTags = computed(() => {
    return visitedViews.value.filter(tag => tag.affix)
  })

  /**
   * 检查标签是否已存在
   */
  const isTagExists = (tag: TagView): boolean => {
    return visitedViews.value.some(v => v.path === tag.path || v.fullPath === tag.fullPath)
  }

  /**
   * 添加已访问的标签
   * @param tag 标签信息
   */
  const addVisitedView = (tag: TagView): void => {
    // 如果已存在则跳过
    if (isTagExists(tag)) {
      // 更新标题（可能国际化后标题变了）
      const existing = visitedViews.value.find(v => v.path === tag.path)
      if (existing && existing.title !== tag.title) {
        existing.title = tag.title
      }
      return
    }

    // 限制标签数量，最多 20 个
    if (visitedViews.value.length >= 20) {
      // 移除最旧的可关闭标签
      const firstClosable = visitedViews.value.find(v => v.closable !== false && !v.affix)
      if (firstClosable) {
        delVisitedView(firstClosable)
      }
    }

    visitedViews.value.push({
      name: tag.name,
      path: tag.path,
      title: tag.title || '未命名',
      affix: tag.affix || false,
      closable: tag.closable !== false,
      fullPath: tag.fullPath || tag.path
    })
  }

  /**
   * 添加缓存视图
   * @param name 视图名称（路由名称）
   */
  const addCachedView = (name: string): void => {
    if (name && !cachedViews.value.includes(name)) {
      cachedViews.value.push(name)
    }
  }

  /**
   * 关闭指定的标签
   * @param tag 标签信息
   * @param routerAction 是否执行路由跳转
   */
  const delVisitedView = (tag: TagView, routerAction: boolean = true): void => {
    const index = visitedViews.value.findIndex(v => v.path === tag.path)
    if (index > -1) {
      visitedViews.value.splice(index, 1)
    }

    // 从缓存中移除
    const cacheIndex = cachedViews.value.indexOf(tag.name)
    if (cacheIndex > -1) {
      cachedViews.value.splice(cacheIndex, 1)
    }

    // 如果需要，执行路由跳转
    if (routerAction) {
      // 如果关闭的是当前激活的标签，跳转到最后一个标签
      if (tag.path === route.path) {
        const lastTag = visitedViews.value[visitedViews.value.length - 1]
        if (lastTag) {
          router.push(lastTag.path)
        } else {
          router.push('/')
        }
      }
    }
  }

  /**
   * 关闭其他标签
   * @param tag 要保留的标签，不传则保留当前页
   */
  const delOtherViews = (tag?: TagView): void => {
    const currentTag = tag || {
      name: route.name as string,
      path: route.path,
      title: route.meta?.title as string || '未命名',
      affix: route.meta?.affix || false
    }

    // 过滤保留当前标签和固定标签
    visitedViews.value = visitedViews.value.filter(v =>
      v.path === currentTag.path || v.affix
    )

    // 重新计算缓存
    cachedViews.value = visitedViews.value
      .filter(v => !v.affix)
      .map(v => v.name)

    // 跳转到目标标签
    if (currentTag.path !== route.path) {
      router.push(currentTag.path)
    }
  }

  /**
   * 关闭所有标签
   */
  const delAllViews = (): void => {
    // 只保留固定标签
    visitedViews.value = visitedViews.value.filter(v => v.affix)
    cachedViews.value = []

    // 跳转到最后一个固定标签或首页
    const lastAffix = visitedViews.value[visitedViews.value.length - 1]
    if (lastAffix) {
      router.push(lastAffix.path)
    } else {
      router.push('/')
    }
  }

  /**
   * 更新标签信息
   * @param tag 标签信息（包含路径）
   */
  const updateVisitedView = (tag: TagView): void => {
    const index = visitedViews.value.findIndex(v => v.path === tag.path)
    if (index > -1) {
      visitedViews.value[index] = { ...visitedViews.value[index], ...tag }
    }
  }

  /**
   * 添加固定标签（首页等）
   * @param routes 路由配置
   */
  const addAffixTags = (routes: RouteRecordRaw[]): void => {
    const affixRoutes: TagView[] = []

    /**
     * 递归遍历路由，查找固定标签
     */
    const filterAffixRoutes = (routes: RouteRecordRaw[], basePath: string = '') => {
      routes.forEach(route => {
        if (route.meta?.affix) {
          const path = basePath + (route.path || '')
          affixRoutes.push({
            name: route.name as string,
            path: path,
            title: route.meta?.title as string || '未命名',
            affix: true,
            closable: false,
            fullPath: path
          })
        }

        // 递归处理子路由
        if (route.children && route.children.length > 0) {
          filterAffixRoutes(route.children, route.path ? `${basePath}${route.path}/` : basePath)
        }
      })
    }

    // 在主路由下查找（排除登录页等）
    const mainLayout = routes.find(r => r.path === '/')
    if (mainLayout && mainLayout.children) {
      filterAffixRoutes(mainLayout.children, '/')
    }

    // 添加固定标签
    affixRoutes.forEach(tag => {
      if (!isTagExists(tag)) {
        visitedViews.value.push(tag)
        addCachedView(tag.name)
      }
    })
  }

  /**
   * 获取当前激活的标签
   */
  const activeTag = computed(() => {
    return visitedViews.value.find(v => v.path === route.path)
  })

  /**
   * 清空所有状态（用于退出登录等）
   */
  const clearAll = (): void => {
    visitedViews.value = []
    cachedViews.value = []
  }

  return {
    visitedViews,
    cachedViews,
    affixTags,
    activeTag,
    addVisitedView,
    addCachedView,
    delVisitedView,
    delOtherViews,
    delAllViews,
    updateVisitedView,
    addAffixTags,
    clearAll,
    isTagExists
  }
})
