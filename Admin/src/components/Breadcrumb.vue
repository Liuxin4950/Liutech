<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { HomeOutlined } from '@ant-design/icons-vue'

const route = useRoute()
const router = useRouter()

/**
 * 面包屑项接口
 */
interface BreadcrumbItem {
  title: string      // 显示标题
  path?: string      // 路由路径
  disabled?: boolean // 是否禁用（当前页不可点击）
}

/**
 * 生成面包屑数据
 */
const breadcrumbs = computed((): BreadcrumbItem[] => {
  const items: BreadcrumbItem[] = []

  // 首页
  items.push({
    title: '首页',
    path: '/'
  })

  // 如果是登录页或 403，不显示面包屑
  if (route.name === 'login' || route.name === 'forbidden') {
    return []
  }

  // 获取匹配的路由记录
  const matched = route.matched

  // 遍历匹配的路由，生成面包屑
  matched.forEach((record, index) => {
    // 跳过根路由
    if (record.path === '/' || record.path === '') {
      return
    }

    // 跳过没有 meta.title 的路由
    if (!record.meta?.title) {
      return
    }

    const isLast = index === matched.length - 1

    items.push({
      title: record.meta.title as string,
      path: record.path,
      disabled: isLast // 最后一个不可点击
    })
  })

  return items
})

/**
 * 跳转到指定路径
 */
const navigateTo = (path: string): void => {
  if (path !== route.path) {
    router.push(path)
  }
}
</script>

<template>
  <div class="breadcrumb-container">
    <a-breadcrumb class="breadcrumb">
      <a-breadcrumb-item>
        <a href="#" @click.prevent="navigateTo('/')">
          <HomeOutlined />
        </a>
      </a-breadcrumb-item>
      <a-breadcrumb-item v-for="item in breadcrumbs.slice(1)" :key="item.path">
        <a v-if="!item.disabled && item.path" href="#" @click.prevent="navigateTo(item.path)">
          {{ item.title }}
        </a>
        <span v-else class="current">{{ item.title }}</span>
      </a-breadcrumb-item>
    </a-breadcrumb>
  </div>
</template>

<style scoped>
.breadcrumb-container {
  width: 100%;
  padding: 0 24px;
  height: 36px;
  background: var(--bg-card);
  border-bottom: 1px solid var(--border-light);
  display: flex;
  align-items: center;
}

.breadcrumb {
  font-size: 14px;
  width: 100%;
}

.breadcrumb :deep(.ant-breadcrumb-separator) {
  color: var(--text-tertiary);
}

.breadcrumb :deep(.ant-breadcrumb-link) {
  color: var(--text-secondary);
}

.breadcrumb :deep(.ant-breadcrumb-link a) {
  color: var(--text-secondary);
  transition: color 0.2s ease;
}

.breadcrumb :deep(.ant-breadcrumb-link a:hover) {
  color: var(--color-primary);
}

.breadcrumb :deep(.ant-breadcrumb-link .current),
.breadcrumb :deep(.ant-breadcrumb-link .current:hover) {
  color: var(--text-main);
  font-weight: 500;
}

.breadcrumb :deep(.anticon) {
  margin-right: 4px;
}
</style>
