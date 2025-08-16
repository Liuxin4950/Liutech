<template>
  <div class="tags-page content  ">

    <div class="bg-hover card">
      <!-- 页面标题 -->
      <div class="page-header">
        <h1 class="page-title">标签云</h1>
        <p class="page-description">探索不同主题的文章标签</p>
      </div>

      <!-- 搜索框 -->
      <div class="search-section">
        <div class="search-box">
          <input v-model="searchKeyword" type="text" placeholder="搜索标签..." class="search-input" />
          <i class="search-icon" v-if="!isSearching">🔍</i>
          <i class="search-icon loading" v-else>⏳</i>
        </div>
      </div>

      <!-- 热门标签 -->
      <div v-if="hotTags && hotTags.length > 0" class="hot-tags-section">
        <h2 class="section-title">🔥 热门标签</h2>
        <div class="hot-tags-grid">
          <router-link v-for="tag in hotTags" :key="tag.id" :to="`/tags/${tag.id}`" class="hot-tag-card">
            <div class="tag-name">{{ tag.name }}</div>
            <div class="tag-count">{{ tag.postCount }} 篇文章</div>
          </router-link>
        </div>
      </div>

      <!-- 所有标签 -->
      <div class="all-tags-section">
        <h2 class="section-title">📚 所有标签</h2>

        <!-- 加载状态 -->
        <div v-if="isLoading" class="loading-state">
          <div class="loading-spinner"></div>
          <p>加载中...</p>
        </div>

        <!-- 标签云 -->
        <div v-else-if="filteredTags.length > 0" class="tags-cloud">
          <router-link v-for="tag in filteredTags" :key="tag.id" :to="`/tags/${tag.id}`" class="tag-item"
            :class="getTagSizeClass(tag.postCount)">
            <span class="tag-name">{{ tag.name }}</span>
            <span class="tag-count">({{ tag.postCount || 0 }})</span>
          </router-link>
        </div>

        <!-- 空状态 -->
        <div v-else class="empty-state">
          <div class="empty-icon">🏷️</div>
          <h3>{{ searchKeyword ? '未找到相关标签' : '暂无标签' }}</h3>
          <p>{{ searchKeyword ? '尝试使用其他关键词搜索' : '还没有任何标签，快去发布文章吧！' }}</p>
        </div>
      </div>
    </div>

  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useTagStore } from '../stores/tag'
import type { Tag } from '../services/tag'

/**
 * 标签页面
 * 作者: 刘鑫
 * 创建时间: 2025-08-06
 */

const tagStore = useTagStore()
const searchKeyword = ref('')
const searchResults = ref<Tag[]>([])
const isSearching = ref(false)

// 响应式数据
let hotTags = computed(() => tagStore.hotTags)
console.log('热门标签数据:', hotTags.value);

const isLoading = computed(() => tagStore.isLoading)

// 过滤后的标签（搜索时显示搜索结果，否则显示所有标签）
const filteredTags = computed(() => {
  if (searchKeyword.value.trim()) {
    return searchResults.value
  }
  return tagStore.tags
})

// 防抖搜索
let searchTimer: number | null = null

// 监听搜索关键词变化，调用后端API
watch(searchKeyword, async (newKeyword) => {
  // 清除之前的定时器
  if (searchTimer) {
    clearTimeout(searchTimer)
  }

  if (!newKeyword.trim()) {
    searchResults.value = []
    return
  }

  // 设置防抖定时器
  searchTimer = setTimeout(async () => {
    isSearching.value = true
    try {
      searchResults.value = await tagStore.searchTagsByAPI(newKeyword)
    } catch (error) {
      console.error('搜索标签失败:', error)
    } finally {
      isSearching.value = false
    }
  }, 300) // 防抖300ms
})

// 根据文章数量获取标签大小样式类
const getTagSizeClass = (postCount: number = 0) => {
  if (postCount >= 20) return 'tag-xl'
  if (postCount >= 15) return 'tag-lg'
  if (postCount >= 10) return 'tag-md'
  if (postCount >= 5) return 'tag-sm'
  return 'tag-xs'
}



// 生命周期
onMounted(async () => {
  await tagStore.initTags()
})
</script>

<style scoped>
.page-header {
  text-align: center;
  margin-bottom: 40px;
}

.page-title {
  font-size: 2.5rem;
  font-weight: bold;
  color: var(--text-primary);
  margin-bottom: 10px;
}

.page-description {
  font-size: 1.1rem;
  color: var(--text-secondary);
  margin: 0;
}

.search-section {
  margin-bottom: 40px;
  display: flex;
  justify-content: center;
  align-items: center;
}

.search-box {
  position: relative;
  max-width: 400px;
  width: 100%;
}

.search-input {
  width: 100%;
  padding: 12px 45px 12px 15px;
  border: 2px solid var(--border-color);
  border-radius: 25px;
  font-size: 1rem;
  outline: none;
  transition: border-color 0.3s ease;
}

.search-input:focus {
  border-color: var(--primary-color);
}

.search-icon {
  position: absolute;
  right: 15px;
  top: 50%;
  transform: translateY(-50%);
  color: var(--text-secondary);
}



.section-title {
  font-size: 1.5rem;
  font-weight: bold;
  color: var(--text-primary);
  margin-bottom: 20px;
  padding-bottom: 10px;
  border-bottom: 2px solid var(--border-color);
}

.hot-tags-section {
  margin-bottom: 50px;
}

.hot-tags-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 15px;
}

.hot-tag-card {
  background: var(--bg-color);
  border: 1px solid var(--border-color);
  border-radius: 12px;
  padding: 20px;
  cursor: pointer;
  transition: all 0.3s ease;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  position: relative;
  overflow: hidden;
}

.hot-tag-card:hover {
  transform: translateY(-3px);
  box-shadow: 0 8px 20px rgba(0, 0, 0, 0.15);
  border-color: var(--primary-color);
  background: var(--hover-color);
}

.hot-tag-card::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 3px;
  background: linear-gradient(90deg, var(--primary-color), var(--secondary-color));
  opacity: 0;
  transition: opacity 0.3s ease;
}

.hot-tag-card:hover::before {
  opacity: 1;
}

.hot-tag-card .tag-name {
  display: block;
  font-size: 1.1rem;
  font-weight: 600;
  margin-bottom: 8px;
  color: var(--text-color);
  transition: color 0.3s ease;
}

.hot-tag-card:hover .tag-name {
  color: var(--primary-color);
}

.hot-tag-card .tag-count {
  font-size: 0.85rem;
  color: var(--text-color);
  opacity: 0.7;
  display: flex;
  align-items: center;
  gap: 4px;
}

.hot-tag-card .tag-count::before {
  content: '📊';
  font-size: 0.8rem;
}

.hot-tag-card:hover .tag-count {
  opacity: 0.9;
}

.all-tags-section {
  margin-bottom: 40px;
}

.loading-state {
  text-align: center;
  padding: 60px 20px;
}

.loading-spinner {
  width: 40px;
  height: 40px;
  border: 4px solid var(--border-color);
  border-top: 4px solid var(--primary-color);
  border-radius: 50%;
  animation: spin 1s linear infinite;
  margin: 0 auto 20px;
}

@keyframes spin {
  0% {
    transform: rotate(0deg);
  }

  100% {
    transform: rotate(360deg);
  }
}

.tags-cloud {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
  justify-content: flex-start;
}

.tag-item {
  display: inline-flex;
  align-items: center;
  padding: 8px 16px;
  background: var(--bg-secondary);
  border: 1px solid var(--border-color);
  border-radius: 20px;
  text-decoration: none;
  color: var(--text-primary);
  transition: all 0.3s ease;
  white-space: nowrap;
}

.tag-item:hover {
  background: var(--primary-color);
  color: white;
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
}

.tag-name {
  font-weight: 500;
}

.tag-count {
  margin-left: 5px;
  font-size: 0.85em;
  opacity: 0.7;
}

/* 标签大小样式 */
.tag-xs {
  font-size: 0.8rem;
}

.tag-sm {
  font-size: 0.9rem;
}

.tag-md {
  font-size: 1rem;
  font-weight: 600;
}

.tag-lg {
  font-size: 1.1rem;
  font-weight: 700;
}

.tag-xl {
  font-size: 1.3rem;
  font-weight: 800;
}

.empty-state {
  text-align: center;
  padding: 80px 20px;
}

.empty-icon {
  font-size: 4rem;
  margin-bottom: 20px;
}

.empty-state h3 {
  font-size: 1.5rem;
  color: var(--text-primary);
  margin-bottom: 10px;
}

.empty-state p {
  color: var(--text-secondary);
  font-size: 1rem;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .tags-page {
    padding: 15px;
  }

  .page-title {
    font-size: 2rem;
  }



  .hot-tags-grid {
    grid-template-columns: repeat(auto-fill, minmax(150px, 1fr));
    gap: 10px;
  }

  .hot-tag-card {
    padding: 15px;
  }

  .tags-cloud {
    gap: 8px;
  }

  .tag-item {
    padding: 6px 12px;
    font-size: 0.9rem;
  }
}
</style>