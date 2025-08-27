<template>
  <div class="card">
    <div class="card-title">🔍 文章搜索</div>
    <div class="search-container">
      <input 
        v-model="searchKeyword" 
        type="text" 
        placeholder="搜索文章标题、内容或摘要..."
        class="search-input"
        @keyup.enter="handleSearch"
        @input="handleInput"
      >
      <button 
        @click="handleSearch" 
        class="search-btn"
        :disabled="!searchKeyword.trim() || isSearching"
      >
        <span v-if="isSearching" class="loading-spinner"></span>
        <span v-else>🔍</span>
      </button>
    </div>
    
    <!-- 搜索结果 -->
    <div v-if="showResults" class="search-results">
      <div v-if="isSearching" class="loading-text">搜索中...</div>
      
      <div v-else-if="searchError" class="error-text">
        <p>{{ searchError }}</p>
        <button @click="handleSearch" class="retry-btn">重试</button>
      </div>
      
      <div v-else-if="searchResults.length === 0" class="empty-text">
        没有找到相关文章
      </div>
      
      <div v-else class="results-list">
        <div class="results-header">
          <span class="results-count">找到 {{ totalResults }} 篇相关文章</span>
          <button @click="clearSearch" class="clear-btn">清除</button>
        </div>
        
        <div class="list gap-8">
          <article
            v-for="post in searchResults"
            :key="post.id"
            class="result-item"
            @click="goToPost(post.id)"
          >
            <h4 class="result-title">{{ post.title }}</h4>
            <p class="result-summary">{{ post.summary || '暂无摘要' }}</p>
            <div class="result-meta">
              <span class="result-category">{{ post.category.name }}</span>
              <span class="result-date">{{ formatDate(post.createdAt) }}</span>
            </div>
          </article>
        </div>
        
        <!-- 分页 -->
        <div v-if="totalPages > 1" class="pagination">
          <button 
            v-for="page in totalPages" 
            :key="page"
            @click="goToPage(page)"
            :class="['page-btn', { active: page === currentPage }]"
          >
            {{ page }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { PostService } from '@/services/post'
import type { PostListItem, PageResponse } from '@/services/post'
import { formatDate } from '@/utils/uitls'
import { useErrorHandler } from '@/composables/useErrorHandler'

const router = useRouter()
const { showError } = useErrorHandler()

// 搜索状态
const searchKeyword = ref('')
const isSearching = ref(false)
const showResults = ref(false)
const searchError = ref('')

// 搜索结果
const searchResults = ref<PostListItem[]>([])
const totalResults = ref(0)
const currentPage = ref(1)
const totalPages = ref(0)
const pageSize = 5 // 搜索结果每页显示数量

// 防抖处理
let searchTimeout: number | null = null

// 处理输入
const handleInput = () => {
  if (searchTimeout) {
    clearTimeout(searchTimeout)
  }
  
  // 如果输入为空，隐藏结果
  if (!searchKeyword.value.trim()) {
    showResults.value = false
    return
  }
  
  // 防抖搜索
  searchTimeout = setTimeout(() => {
    handleSearch()
  }, 500)
}

// 执行搜索
const handleSearch = async () => {
  const keyword = searchKeyword.value.trim()
  if (!keyword) {
    showResults.value = false
    return
  }
  
  try {
    isSearching.value = true
    searchError.value = ''
    showResults.value = true
    
    const response = await PostService.getPostList({
      keyword,
      page: currentPage.value,
      size: pageSize
    })
    
    searchResults.value = response.records
    totalResults.value = response.total
    totalPages.value = response.pages
    
  } catch (error) {
    console.error('搜索失败:', error)
    searchError.value = '搜索失败，请稍后重试'
    showError('搜索文章失败')
  } finally {
    isSearching.value = false
  }
}

// 跳转到文章详情
const goToPost = (postId: number) => {
  router.push(`/posts/${postId}`)
  clearSearch()
}

// 分页
const goToPage = (page: number) => {
  currentPage.value = page
  handleSearch()
}

// 清除搜索
const clearSearch = () => {
  searchKeyword.value = ''
  showResults.value = false
  searchResults.value = []
  totalResults.value = 0
  currentPage.value = 1
  totalPages.value = 0
  searchError.value = ''
  
  if (searchTimeout) {
    clearTimeout(searchTimeout)
    searchTimeout = null
  }
}
</script>

<style scoped>
.search-container {
  display: flex;
  gap: 8px;
  margin-bottom: 16px;
}

.search-input {
  flex: 1;
  padding: 8px 12px;
  border: 1px solid var(--border-soft);
  border-radius: 6px;
  font-size: 0.875rem;
  background: var(--bg-soft);
  color: var(--text-main);
  transition: all 0.2s;
}

.search-input:focus {
  outline: none;
  border-color: var(--color-primary);
  box-shadow: 0 0 0 2px rgba(107, 166, 197, 0.1);
}

.search-btn {
  padding: 8px 12px;
  background: var(--color-primary);
  color: white;
  border: none;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.2s;
  display: flex;
  align-items: center;
  justify-content: center;
  min-width: 40px;
}

.search-btn:hover:not(:disabled) {
  background: var(--color-primary-dark);
}

.search-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.loading-spinner {
  width: 14px;
  height: 14px;
  border: 2px solid transparent;
  border-top: 2px solid currentColor;
  border-radius: 50%;
  animation: spin 1s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.search-results {
  border-top: 1px solid var(--border-soft);
  padding-top: 16px;
}

.loading-text, .error-text, .empty-text {
  text-align: center;
  padding: 20px;
  color: var(--text-subtle);
  font-size: 0.875rem;
}

.error-text {
  color: var(--color-error);
}

.retry-btn {
  margin-top: 8px;
  padding: 4px 12px;
  background: var(--color-primary);
  color: white;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  font-size: 0.75rem;
}

.results-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
  padding-bottom: 8px;
  border-bottom: 1px solid var(--border-soft);
}

.results-count {
  font-size: 0.75rem;
  color: var(--text-subtle);
}

.clear-btn {
  padding: 2px 8px;
  background: none;
  color: var(--color-primary);
  border: 1px solid var(--color-primary);
  border-radius: 4px;
  cursor: pointer;
  font-size: 0.75rem;
  transition: all 0.2s;
}

.clear-btn:hover {
  background: var(--color-primary);
  color: white;
}

.result-item {
  padding: 12px;
  border: 1px solid var(--border-soft);
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.2s;
  background: var(--bg-soft);
}

.result-item:hover {
  border-color: var(--color-primary);
  background: var(--bg-hover);
}

.result-title {
  font-size: 0.875rem;
  font-weight: 600;
  color: var(--text-main);
  margin: 0 0 4px 0;
  line-height: 1.4;
}

.result-summary {
  font-size: 0.75rem;
  color: var(--text-subtle);
  margin: 0 0 8px 0;
  line-height: 1.4;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.result-meta {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 0.7rem;
  color: var(--text-muted);
}

.result-category {
  background: var(--bg-tag);
  color: var(--text-main);
  padding: 2px 6px;
  border-radius: 4px;
}

.pagination {
  display: flex;
  justify-content: center;
  gap: 4px;
  margin-top: 16px;
  padding-top: 12px;
  border-top: 1px solid var(--border-soft);
}

.page-btn {
  padding: 4px 8px;
  border: 1px solid var(--border-soft);
  background: var(--bg-soft);
  color: var(--text-main);
  border-radius: 4px;
  cursor: pointer;
  font-size: 0.75rem;
  transition: all 0.2s;
}

.page-btn:hover {
  border-color: var(--color-primary);
  background: var(--bg-hover);
}

.page-btn.active {
  background: var(--color-primary);
  color: white;
  border-color: var(--color-primary);
}
</style>
