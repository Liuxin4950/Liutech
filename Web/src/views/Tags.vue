<template>
  <div class="tags-page content">
    <!-- 热门标签 -->
    <div v-if="popularTags.length > 0" class="card bg-card mb-16">
      <div class="flex flex-col gap-16">
        <h4 class="card-title"><span class="card-badge"><Icon name="fire" size="12" /> Hot</span><span class="card-title-text">热门<span class="card-highlight">标签</span></span></h4>
        <div class="flex flex-wrap flex-fw gap-12" >
          <router-link
            v-for="tag in popularTags"
            :key="tag.id"
            :to="`/tags/${tag.id}`"
            class="tag flex flex-ac gap-8 transition link"
          >
            <Icon :name="getTagIcon(tag.name)" size="14" />
            <span class="text-sm font-medium">{{ tag.name }}</span>
            <span class="text-xs text-muted">({{ tag.postCount || 0 }})</span>
          </router-link>
        </div>
      </div>
    </div>

    <!-- 所有标签 -->
    <div class="card bg-card mb-16">
      <div class="flex flex-col gap-16">
        <h4 class="card-title"><span class="card-badge"><Icon name="book" size="12" /> All</span><span class="card-title-text">所有<span class="card-highlight">标签</span></span></h4>
         <!-- 搜索框 -->
        <div class="search-section">
          <div class="search-box relative">
              <Icon name="search" size="16" class="search-icon" />
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
          <h3 class="text-base font-semibold">
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
            class="tag flex flex-ac gap-8 transition link"
          >
            <Icon :name="getTagIcon(tag.name)" size="14" />
            <span class="">{{ tag.name }}</span>
            <span class="text-muted">({{ tag.postCount || 0 }})</span>
          </router-link>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useTagStore } from '@/stores/tag'
import { useBannerStore } from '@/stores/banner'
import bannerFallback from '@/assets/image/banner/banner0.png'
import type { Tag } from '@/services/tag'
import Icon from '@/components/Icon.vue'

const tagStore = useTagStore()
const bannerStore = useBannerStore()
const searchKeyword = ref('')
const searchResults = ref<Tag[]>([])

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
    try {
      searchResults.value = await tagStore.searchTagsByAPI(newVal.trim())
    } catch {
      // 搜索标签失败时静默处理
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

// Banner 页眉：标签云（一级页面，500px hero 大横幅承担页面标题）
// immediate：store 持久化缓存命中时 tags 初始即有值、无变化，需立即定制
watch([tags, totalPosts], () => {
  if (tags.value.length === 0) return
  bannerStore.setBanner({
    slides: [{
      title: '标签',
      description: `共 ${tags.value.length} 个标签 · ${totalPosts.value} 篇文章`,
      imageUrl: bannerFallback,
      sortOrder: 0,
      status: 1
    }],
    badgeText: 'Tag Cloud',
    titleAs: 'h1',
    titleHighlight: '云',
    mode: 'hero'
  })
}, { immediate: true })
</script>

<style scoped lang="scss">
@use "@/assets/styles/tokens" as *;

/* 标签页有独立 Icon，隐藏全局 .tag 的 # 前缀；其余样式走全局药丸 */
.tag::before {
  content: none;
}

/* 空状态标题（原全局 .mb-8 移入） */
.text-center h3 {
  margin-bottom: 8px;
}

.flex.flex-wrap.gap-12 {
  @include respond(sm) {
    gap: 8px;
  }
}

.tags-cloud {
  @include respond(sm) {
    gap: 8px;
  }
}

.search-box input {
    @include respond(sm) {
        font-size: 14px;
        padding: 10px 36px 10px 12px;
    }
}

</style>