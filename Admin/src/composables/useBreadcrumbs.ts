import { computed } from 'vue'
import { useRoute } from 'vue-router'

/** 面包屑项 */
export interface BreadcrumbItem {
  title: string
  path?: string
  disabled?: boolean
}

/**
 * 从当前路由 matched 记录生成面包屑数据。
 * 登录页/403 页返回空数组。
 */
export function useBreadcrumbs() {
  const route = useRoute()

  const breadcrumbs = computed<BreadcrumbItem[]>(() => {
    if (route.name === 'login' || route.name === 'forbidden') return []

    const items: BreadcrumbItem[] = [{ title: '首页', path: '/' }]
    const matched = route.matched

    matched.forEach((record, index) => {
      if (record.path === '/' || record.path === '') return
      if (!record.meta?.title) return
      items.push({
        title: record.meta.title as string,
        path: record.path,
        disabled: index === matched.length - 1,
      })
    })

    return items
  })

  return { breadcrumbs }
}
