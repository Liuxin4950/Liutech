<template>
  <nav class="breadcrumb" v-if="breadcrumbItems.length > 0">
    <div class="content">
      <ol class="breadcrumb-list">
        <li 
          v-for="(item, index) in breadcrumbItems" 
          :key="index"
          class="breadcrumb-item"
          :class="{ current: index === breadcrumbItems.length - 1 }"
        >
          <!-- 分隔符 -->
          <span v-if="index > 0" class="breadcrumb-separator">›</span>
          
          <!-- 链接项 -->
          <router-link 
            v-if="item.to && index !== breadcrumbItems.length - 1" 
            :to="item.to" 
            class="breadcrumb-link"
          >
            <i v-if="item.icon" class="icon">{{ item.icon }}</i>
            <span>{{ item.label }}</span>
          </router-link>
          
          <!-- 当前页面项 -->
          <span v-else class="breadcrumb-current">
            <i v-if="item.icon" class="icon">{{ item.icon }}</i>
            <span>{{ item.label }}</span>
          </span>
        </li>
      </ol>
    </div>
  </nav>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'

interface BreadcrumbItem {
  label: string
  to?: any
  icon?: string
}

const route = useRoute()

// 父级路由映射（用于“返回列表”场景）
const sectionParentMap = {
  home: { name: 'posts', label: '全部文章' },
  categories: { name: 'category-list', label: '分类' },
  tags: { name: 'tags', label: '标签' },
  archive: { name: 'archive', label: '文章归档' },
  about: { name: 'about', label: '关于我' }
} as const

// 被视为“列表页”的路由：仅展示“首页 > 当前页”
const listRoutes = new Set([
  'posts',
  'category-list',
  'tags',
  'archive',
  'about',
  'my-posts',
  'drafts',
  'profile',
  'create-post'
])

// 智能面包屑：
// - 首页：仅显示“首页”
// - 列表页：显示“首页 > 当前列表”
// - 详情页：优先根据 ?from=categories|tags 判断父级；否则回退到 section 映射；文章详情默认回退到“全部文章”
const breadcrumbItems = computed<BreadcrumbItem[]>(() => {
  const items: BreadcrumbItem[] = []

  // 始终添加首页
  const homeItem: BreadcrumbItem = { label: '首页', to: { name: 'home' } }
  items.push(homeItem)

  const currentName = route.name as string | undefined
  const currentLabel = (route.meta?.title as string) || String(currentName || '') || '当前页'

  // 首页：仅显示首页
  if (!currentName || currentName === 'home') {
    items[0].to = undefined
    return items
  }

  // 列表页：直接“首页 > 当前页”
  if (listRoutes.has(currentName)) {
    items.push({ label: currentLabel })
    return items
  }

  // 详情页：根据类型决定父级
  const from = (route.query.from as string) || ''
  const categoryId = (route.query.categoryId as string) || ''
  const tagId = (route.query.tagId as string) || ''
  const categoryName = (route.query.categoryName as string) || ''
  const tagName = (route.query.tagName as string) || ''

  const addParent = (label: string, to: any) => items.push({ label, to })

  if (currentName === 'post-detail') {
    if (from === 'categories') {
      if (categoryId) addParent(categoryName || '分类', { name: 'category-detail', params: { id: categoryId } })
      else addParent('分类', { name: 'category-list' })
    } else if (from === 'tags') {
      if (tagId) addParent(tagName || '标签', { name: 'tag-detail', params: { id: tagId } })
      else addParent('标签', { name: 'tags' })
    } else if (from === 'archive') {
      addParent('文章归档', { name: 'archive' })
    } else if (from === 'posts' || from === 'home') {
      addParent('全部文章', { name: 'posts' })
    } else if (from === 'my-posts') {
      addParent('我的文章', { name: 'my-posts' })
    } else {
      // 默认回退到“全部文章”
      const parent = sectionParentMap.home
      addParent(parent.label, { name: parent.name })
    }
  } else if (currentName === 'category-detail') {
    const parent = sectionParentMap.categories
    addParent(parent.label, { name: parent.name })
  } else if (currentName === 'tag-detail') {
    const parent = sectionParentMap.tags
    addParent(parent.label, { name: parent.name })
  } else {
    // 其它详情页：根据 section 映射回退
    const section = (route.meta?.section as string) || ''
    const parent = sectionParentMap[section as keyof typeof sectionParentMap]
    if (parent) addParent(parent.label, { name: parent.name })
  }

  // 最后添加当前页（不可点击）
  items.push({ label: currentLabel })

  return items
})
</script>

<style scoped lang="scss">
@use "@/assets/styles/tokens" as *;
/* 面包屑：药丸容器风格，与页面 title-badge / tag 设计语言统一 */
.breadcrumb {
  padding-top: 10px;
}

.breadcrumb-list {
  display: inline-flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  margin: 0;
  padding: 5px 14px;
  list-style: none;
  background: var(--bg-soft);
  border: 1px solid var(--border-light);
  border-radius: 30px;
}

.breadcrumb-item {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 0.8rem;
}

.breadcrumb-separator {
  color: var(--text-muted);
  user-select: none;
}

.breadcrumb-link {
  display: flex;
  align-items: center;
  gap: 4px;
  color: var(--color-primary);
  font-weight: 500;
  text-decoration: none;
  transition: color 0.2s ease;

  &:hover {
    color: var(--color-secondary);
  }
}

.breadcrumb-current {
  display: flex;
  align-items: center;
  gap: 4px;
  color: var(--text-subtle);
  font-weight: 500;
  max-width: 260px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.icon {
  font-size: 0.875rem;
  line-height: 1;
}

/* 响应式设计 */
@include respond(md) {
  .breadcrumb {
    padding: 12px 0;
  }

  .breadcrumb-list {
    padding: 4px 12px;
    gap: 4px;
  }

  .breadcrumb-item {
    gap: 4px;
    font-size: 0.75rem;
  }

  .icon {
    font-size: 0.75rem;
  }
}
</style>