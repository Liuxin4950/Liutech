<template>
  <div class="tags-page content">
    <!-- 页面头部 -->
    <div class="card bg-soft mb-16">
      <div class="flex flex-col gap-16">
        <div class="flex flex-col gap-12">
          <h1 class="text-2xl font-bold text-primary mb-0 flex flex-ac gap-8">
            <span class="text-3xl">🏷️</span> 标签云
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
        <h2 class="text-lg text-primary flex flex-ac">
          <span class="text-xl">🔥</span> 热门标签
        </h2>
        <div class="flex flex-wrap gap-12">
          <router-link
            v-for="tag in popularTags"
            :key="tag.id"
            :to="`/tags/${tag.id}`"
            class="tag flex flex-ac gap-8  px-12 py-8 rounded-8 transition link"
          >
            <span class="text-sm">{{ getTagIcon(tag.name) }}</span>
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
          <span class="text-xl">📚</span> 所有标签
        </h2>
         <!-- 搜索框 -->
        <div class="search-section">
          <div class="search-box relative">
            <input
              v-model="searchKeyword"
              type="text"
              placeholder="搜索标签..."
              class="bg-card text-main"
            />
           
          </div>
        </div>

        <!-- 加载状态 -->
        <div v-if="isLoading" class="loading-text">加载中...</div>

        <!-- 空状态 -->
        <div v-else-if="filteredTags.length === 0" class="text-center p-20">
          <div class="text-2xl mb-8">🏷️</div>
          <h3 class="text-base font-semibold mb-8">
            {{ searchKeyword ? '未找到相关标签' : '暂无标签' }}
          </h3>
          <p class="text-muted text-sm mb-0">
            {{ searchKeyword ? '尝试使用其他关键词搜索' : '还没有任何标签，快去发布文章吧！' }}
          </p>
        </div>

        <!-- 标签云 -->
        <div v-else class="tags-cloud flex flex-wrap gap-12">
          <router-link
            v-for="tag in filteredTags"
            :key="tag.id"
            :to="`/tags/${tag.id}`"
            class="tag flex flex-ac gap-8 px-12 rounded-8 transition link"
          >
            <span class="">{{ tag.name }}</span>
            <span class="">({{ tag.postCount }})</span>
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
    } catch (err) {
      console.error('搜索标签失败:', err)
    } finally {
      isSearching.value = false
    }
  }, 300)
})

// 获取标签图标（Emoji 映射）
const getTagIcon = (tagName: string): string => {
  const iconMap: Record<string, string> = {
    'Vue': '💚',
    'React': '⚛️',
    'JavaScript': '🟨',
    'TypeScript': '🔷',
    'Python': '🐍',
    'Node.js': '🟢',
    'Java': '☕',
    'CSS': '🎨',
    'HTML': '📜',
    '算法': '🧮',
    'AI': '🤖',
    '数据库': '🗄️',
    '性能优化': '⚡',
    '安全': '🔒',
    '设计模式': '📐',
    '测试': '🧪',
    '部署': '🚀',
    '工具': '🔧',
    '教程': '📚',
    '随笔': '✍️'
  }
  return iconMap[tagName] || '🔖'
}

// 标签大小类（用于字体和视觉权重）
const getTagSizeClass = (postCount: number) => {
  if (postCount >= 20) return 'text-lg font-bold bg-primary-light text-primary px-16 py-8'
  if (postCount >= 15) return 'text-base font-semibold bg-gray-100 text-gray-800 px-14 py-7'
  if (postCount >= 10) return 'text-sm font-medium bg-gray-50 text-gray-700 px-12 py-6'
  if (postCount >= 5) return 'text-xs font-normal bg-gray-25 text-gray-600 px-10 py-5'
  return 'text-xs opacity-60 italic bg-gray-10 text-gray-500 px-8 py-4'
}

// 初始化数据
onMounted(async () => {
  await tagStore.initTags()
})
</script>

<style scoped lang="scss">
/* 搜索框 */
.search-section{
  width: 100%;
  .search-box {
    width: 100%;
    height: 40px;
    input{
      width: 100%;
      height: 40px;
      border-radius: 20px;
      padding-left: 20px;
      outline: none;
      border: 1px solid var(--border-soft);
    }

  }
}



/* 标签云内间距响应式优化 */
@media (max-width: 768px) {


}
</style>