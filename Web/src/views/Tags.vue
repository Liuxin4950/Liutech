<template>
  <div class="tags-page content">
    <!-- 页面头部 -->
    <div class="card bg-card mb-16">
      <div class="flex flex-col gap-16">
        <div class="flex flex-col gap-12">
          <h1 class="text-2xl font-bold text-primary mb-0 flex flex-ac gap-8">
            <Icon name="tag" size="20" /> 标签云
          </h1>
          <p class="text-muted text-base mb-0">
            探索不同主题的文章标签
          </p>
          <div class="flex flex-ac gap-8">
            <span class="badge">共 {{ tags.length }} 个标签</span>
            <span class="badge">{{ totalPosts }} 篇文章</span>
          </div>
        </div>
      </div>
    </div>

    <!-- 热门标签 -->
    <div v-if="popularTags.length > 0" class="card bg-soft mb-16">
      <div class="flex flex-col gap-16">
        <h2 class="text-lg text-primary flex flex-ac gap-8">
          <Icon name="fire" size="20" />
          <div class="">热门标签</div>
          
        </h2>
        <div class="flex flex-wrap flex-fw gap-12" >
          <router-link
            v-for="tag in popularTags"
            :key="tag.id"
            :to="`/tags/${tag.id}`"
            class="tag flex flex-ac gap-8  px-12 py-8 rounded-8 transition link"
          >
            <Icon :name="getTagIcon(tag.name)" size="14" />
            <span class="text-sm font-medium">{{ tag.name }}</span>
            <span class="text-xs text-muted">({{ tag.postCount }})</span>
          </router-link>
        </div>
      </div>
    </div>

    <!-- 所有标签 -->
    <div class="card bg-soft  mb-16">
      <div class="flex flex-col gap-16">
        <h2 class="text-lg font-semibold text-primary mb-0 flex flex-ac gap-8">
          <Icon name="book" size="20" />所有标签
        </h2>
         <!-- 搜索框 -->
        <div class="search-section">
          <div class="search-box relative">
            <input
              v-model="searchKeyword"
              type="text"
              placeholder="搜索标签..."
              class="search-input"
            />
           
          </div>
        </div>

        <!-- 加载状态 -->
        <div v-if="isLoading" class="loading-text text-sm">加载中...</div>

        <!-- 空状态 -->
        <div v-else-if="filteredTags.length === 0" class="text-center p-20 flex flex-col flex-ac text-sm">
          <h3 class="text-base font-semibold mb-8">
            {{ searchKeyword ? '未找到相关标签' : '暂无标签' }}
          </h3>
          <p class="text-muted text-sm mb-0">
            {{ searchKeyword ? '尝试使用其他关键词搜索' : '还没有任何标签，快去发布文章吧！' }}
          </p>
          <img src="@/assets/image/扑到.png" alt="" class="fit-err">
        </div>

        <!-- 标签云 -->
        <div v-else class="tags-cloud flex flex-wrap gap-12">
          <router-link
            v-for="tag in filteredTags"
            :key="tag.id"
            :to="`/tags/${tag.id}`"
            class="tag flex flex-ac gap-8 px-12 rounded-8 transition link"
          >
            <Icon :name="getTagIcon(tag.name)" size="14" />
            <span class="">{{ tag.name }}</span>
            <span class="text-muted">({{ tag.postCount }})</span>
          </router-link>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useTagStore } from '@/stores/tag'
import type { Tag } from '@/services/tag'
import Icon from '@/components/Icon.vue'

const tagStore = useTagStore()
const searchKeyword = ref('')
const searchResults = ref<Tag[]>([])
const isSearching = ref(false)

// 响应式数据
const tags = computed(() => tagStore.tags || [])
const isLoading = computed(() => tagStore.isLoading)

// 计算总文章数
const totalPosts = computed(() => {
  return tags.value.reduce((sum, tag) => sum + (tag.postCount || 0), 0)
})

// 热门标签：按文章数排序，取前6个
const popularTags = computed(() => {
  return tags.value
    .filter(tag => tag.postCount > 0)
    .sort((a, b) => b.postCount - a.postCount)
    .slice(0, 6)
})

// 过滤后标签：搜索结果 or 所有标签
const filteredTags = computed(() => {
  if (searchKeyword.value.trim()) {
    return searchResults.value
  }
  return tags.value
})

// 防抖搜索
let searchTimer: number | null = null
watch(searchKeyword, (newVal) => {
  if (searchTimer) clearTimeout(searchTimer)

  if (!newVal.trim()) {
    searchResults.value = []
    return
  }

  searchTimer = window.setTimeout(async () => {
    isSearching.value = true
    try {
      searchResults.value = await tagStore.searchTagsByAPI(newVal.trim())
    } catch {
      // 搜索标签失败时静默处理
    } finally {
      isSearching.value = false
    }
  }, 300)
})

// 获取标签图标（SVG 映射）
const getTagIcon = (tagName: string): string => {
  const iconMap: Record<string, string> = {
    'Vue': 'layers',
    'React': 'atom',
    'JavaScript': 'square',
    'TypeScript': 'square',
    'Python': 'python',
    'Node.js': 'square',
    'Java': 'coffee',
    'CSS': 'layout',
    'HTML': 'book',
    '算法': 'square',
    'AI': 'bot',
    '数据库': 'database',
    '性能优化': 'zap',
    '安全': 'shield',
    '设计模式': 'layout',
    '测试': 'check',
    '部署': 'rocket',
    '工具': 'wrench',
    '教程': 'book',
    '随笔': 'pen'
  }
  return iconMap[tagName] || 'tag'
}

// 初始化数据
onMounted(async () => {
  await tagStore.initTags()
})
</script>

<style scoped lang="scss">
@use "@/assets/styles/tokens" as *;

.flex.flex-wrap.gap-12 {
  @include respond(sm) {
    gap: 8px;
    
    .tag {
      font-size: 12px;
      padding: 6px 10px;
    }
  }
}

.tags-cloud {
  @include respond(sm) {
    gap: 8px;
    
    .tag-item {
      font-size: 12px;
      padding: 6px 10px;
    }
  }
}

.search-box input {
  @include respond(sm) {
    font-size: 14px;
    padding: 10px 12px;
  }
}

</style>