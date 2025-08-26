<template>
  <nav class="breadcrumb" v-if="breadcrumbItems.length > 0">
    <div class="container content">
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

<style scoped>
.breadcrumb {
  background: var(--bg-color);
  border-bottom: 1px solid var(--border-color);
  padding: 0.75rem 0;
  font-size: 0.875rem;
}

.breadcrumb-list {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 0.25rem;
  margin: 0;
  padding: 0;
  list-style: none;
}

.breadcrumb-item {
  display: flex;
  align-items: center;
  gap: 0.25rem;
}

.breadcrumb-separator {
  color: var(--text-muted);
  font-weight: normal;
  user-select: none;
}

.breadcrumb-link {
  display: flex;
  align-items: center;
  gap: 0.25rem;
  color: var(--primary-color);
  text-decoration: none;
  transition: color 0.2s ease;
}

.breadcrumb-link:hover {
  color: var(--primary-hover);
  text-decoration: underline;
}

.breadcrumb-current {
  display: flex;
  align-items: center;
  gap: 0.25rem;
  color: var(--text-color);
  font-weight: 500;
}

.icon {
  font-size: 0.875rem;
  line-height: 1;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .breadcrumb {
    padding: 0.5rem 0;
    font-size: 0.8rem;
  }
  
  .breadcrumb-list {
    gap: 0.125rem;
  }
  
  .breadcrumb-item {
    gap: 0.125rem;
  }
  
  .icon {
    font-size: 0.75rem;
  }
}
</style>