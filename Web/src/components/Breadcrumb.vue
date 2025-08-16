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
  to?: string
  icon?: string
}

const route = useRoute()

// 根据当前路由生成面包屑导航
const breadcrumbItems = computed<BreadcrumbItem[]>(() => {
  const items: BreadcrumbItem[] = []
  
  // 首页
  items.push({
    label: '首页',
    to: '/',
    icon: '🏠'
  })
  
  // 根据路由名称添加对应的面包屑
  switch (route.name) {
    case 'home':
      // 首页显示当前位置
      items.push({
        label: '博客首页'
      })
      break
      
    case 'posts':
      items.push({
        label: '全部文章'
      })
      break
      
    case 'post-detail':
      items.push({
        label: '全部文章',
        to: '/posts'
      })
      // 如果有分类信息，添加分类面包屑
      if (route.meta.category) {
        items.push({
          label: route.meta.category as string,
          to: `/category/${route.meta.categoryId}`
        })
      }
      items.push({
        label: route.meta.title as string || '文章详情'
      })
      break
      
    case 'CategoryPosts':
      items.push({
        label: '全部文章',
        to: '/posts'
      })
      items.push({
        label: route.meta.categoryName as string || '分类文章'
      })
      break
      
    case 'create-post':
      items.push({
        label: '发布文章'
      })
      break
      
    case 'drafts':
      items.push({
        label: '我的文章',
        to: '/my-posts'
      })
      items.push({
        label: '草稿箱'
      })
      break
      
    case 'my-posts':
      items.push({
        label: '我的文章'
      })
      break
      
    case 'profile':
      items.push({
        label: '个人资料'
      })
      break
      
    default:
      // 对于其他页面，使用路由的 meta.title
      if (route.meta.title) {
        items.push({
          label: route.meta.title as string
        })
      }
      break
  }
  
  return items
})
</script>

<style scoped>
.breadcrumb {
  background: var(--bg-color);
  border-bottom: 1px solid var(--border-color);
  padding: 12px 0;
  font-size: 0.9rem;
}

.container {
  margin: 0 auto;
  padding: 0 20px;
}

.breadcrumb-list {
  display: flex;
  align-items: center;
  list-style: none;
  margin: 0;
  padding: 0;
  flex-wrap: wrap;
  gap: 4px;
}

.breadcrumb-item {
  display: flex;
  align-items: center;
}

.breadcrumb-separator {
  color: var(--text-color);
  opacity: 0.5;
  margin: 0 8px;
  font-weight: 500;
}

.breadcrumb-link {
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--text-color);
  opacity: 0.7;
  text-decoration: none;
  padding: 4px 8px;
  border-radius: 6px;
  transition: all 0.2s ease;
}

.breadcrumb-link:hover {
  color: var(--primary-color);
  background: var(--hover-color);
  opacity: 1;
}

.breadcrumb-current {
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--text-color);
  font-weight: 600;
  padding: 4px 8px;
}

.icon {
  font-size: 1rem;
  line-height: 1;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .breadcrumb {
    padding: 8px 0;
    font-size: 0.85rem;
  }
  
  .container {
    padding: 0 15px;
  }
  
  .breadcrumb-separator {
    margin: 0 4px;
  }
  
  .breadcrumb-link,
  .breadcrumb-current {
    padding: 2px 4px;
    gap: 4px;
  }
}
</style>