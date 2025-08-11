<template>
  <div class="drafts-page">
    <div class="page-header">
      <h1 class="page-title">📄 草稿箱</h1>
      <p class="page-description">管理您的草稿文章，继续编辑或发布</p>
    </div>

    <!-- 操作栏 -->
    <div class="actions-bar">
      <div class="search-box">
        <input 
          v-model="searchKeyword" 
          type="text" 
          placeholder="搜索草稿..." 
          class="search-input"
          @keyup.enter="handleSearch"
        />
        <span class="search-icon">🔍</span>
      </div>
      <button class="create-btn" @click="createNewDraft">
        <span class="btn-icon">✏️</span>
        新建草稿
      </button>
    </div>

    <!-- 草稿列表 -->
    <div class="drafts-container">
      <!-- 加载状态 -->
      <div v-if="loading" class="loading-state">
        <div class="loading-spinner"></div>
        <p>加载中...</p>
      </div>

      <!-- 错误状态 -->
      <div v-else-if="error" class="error-state">
        <span class="error-icon">❌</span>
        <p>{{ error }}</p>
        <button class="retry-btn" @click="loadDrafts">重试</button>
      </div>

      <!-- 空状态 -->
      <div v-else-if="filteredDrafts.length === 0" class="empty-state">
        <span class="empty-icon">📝</span>
        <h3>暂无草稿</h3>
        <p>开始创建您的第一篇草稿吧！</p>
        <button class="create-btn" @click="createNewDraft">
          <span class="btn-icon">✏️</span>
          新建草稿
        </button>
      </div>

      <!-- 草稿列表 -->
      <div v-else class="drafts-list">
        <div 
          v-for="draft in filteredDrafts" 
          :key="draft.id" 
          class="draft-card"
        >
          <div class="draft-content">
            <h3 class="draft-title" @click="editDraft(draft.id)">
              {{ draft.title || '无标题草稿' }}
            </h3>
            <p class="draft-summary" v-if="draft.summary">
              {{ draft.summary }}
            </p>
            <div class="draft-meta">
              <span class="draft-date">
                <span class="meta-icon">📅</span>
                更新于 {{ formatDate(draft.updatedAt || draft.createdAt) }}
              </span>
              <span class="draft-category" v-if="draft.category">
                <span class="meta-icon">🏷️</span>
                {{ draft.category.name }}
              </span>
            </div>
          </div>
          
          <div class="draft-actions">
            <button class="action-btn edit-btn" @click="editDraft(draft.id)" title="编辑">
              <span class="btn-icon">✏️</span>
            </button>
            <button class="action-btn publish-btn" @click="publishDraft(draft.id)" title="发布">
              <span class="btn-icon">🚀</span>
            </button>
            <button class="action-btn delete-btn" @click="deleteDraft(draft.id)" title="删除">
              <span class="btn-icon">🗑️</span>
            </button>
          </div>
        </div>
      </div>
    </div>

    <!-- 分页 -->
    <div v-if="totalPages > 1" class="pagination">
      <button 
        class="page-btn" 
        :disabled="currentPage === 1" 
        @click="changePage(currentPage - 1)"
      >
        上一页
      </button>
      <span class="page-info">{{ currentPage }} / {{ totalPages }}</span>
      <button 
        class="page-btn" 
        :disabled="currentPage === totalPages" 
        @click="changePage(currentPage + 1)"
      >
        下一页
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { PostService, type PostListItem, type PageResponse } from '../services/post'
import { CategoryService, type Category } from '../services/category'
import { useErrorHandler } from '@/composables/useErrorHandler'

const router = useRouter()
const { handleAsync } = useErrorHandler()

// 响应式数据
const drafts = ref<PostListItem[]>([])
const categories = ref<Category[]>([])
const loading = ref(false)
const error = ref('')
const searchKeyword = ref('')
const currentPage = ref(1)
const pageSize = ref(10)
const totalCount = ref(0)

// 计算属性
const filteredDrafts = computed(() => {
  if (!searchKeyword.value) {
    return drafts.value
  }
  return drafts.value.filter(draft => 
    draft.title.toLowerCase().includes(searchKeyword.value.toLowerCase()) ||
    (draft.summary && draft.summary.toLowerCase().includes(searchKeyword.value.toLowerCase()))
  )
})

const totalPages = computed(() => {
  return Math.ceil(totalCount.value / pageSize.value)
})

// 方法
const loadDrafts = async () => {
  await handleAsync(async () => {
    loading.value = true
    error.value = ''
    
    const response: PageResponse<PostListItem> = await PostService.getDraftList({
      page: currentPage.value,
      size: pageSize.value,
      keyword: searchKeyword.value || undefined
    })
    
    drafts.value = response.records
    totalCount.value = response.total
  }, {
    onError: (err) => {
      error.value = '加载草稿失败，请稍后重试'
      console.error('加载草稿失败:', err)
    },
    onFinally: () => {
      loading.value = false
    }
  })
}

const loadCategories = async () => {
  try {
    categories.value = await CategoryService.getCategories()
  } catch (error) {
    console.error('加载分类失败:', error)
  }
}

const createNewDraft = () => {
  router.push('/create?draft=true')
}

const editDraft = (draftId: number) => {
  router.push(`/create?draft=${draftId}`)
}

const publishDraft = async (draftId: number) => {
  if (!confirm('确定要发布这篇草稿吗？')) {
    return
  }
  
  await handleAsync(async () => {
    await PostService.publishPost(draftId)
    
    // 重新加载草稿列表
    await loadDrafts()
    
    alert('草稿发布成功！')
  }, {
    onError: (err) => {
      console.error('发布草稿失败:', err)
      alert('发布失败，请稍后重试')
    }
  })
}

const deleteDraft = async (draftId: number) => {
  if (!confirm('确定要删除这篇草稿吗？此操作不可恢复。')) {
    return
  }
  
  await handleAsync(async () => {
    await PostService.deletePost(draftId)
    
    // 重新加载草稿列表
    await loadDrafts()
    
    alert('草稿已删除')
  }, {
    onError: (err) => {
      console.error('删除草稿失败:', err)
      alert('删除失败，请稍后重试')
    }
  })
}

const changePage = (page: number) => {
  currentPage.value = page
  loadDrafts()
}

const handleSearch = () => {
  currentPage.value = 1
  loadDrafts()
}



const formatDate = (dateString: string) => {
  const date = new Date(dateString)
  const now = new Date()
  const diff = now.getTime() - date.getTime()
  
  // 小于1小时显示分钟
  if (diff < 60 * 60 * 1000) {
    const minutes = Math.floor(diff / (60 * 1000))
    return `${minutes}分钟前`
  }
  
  // 小于24小时显示小时
  if (diff < 24 * 60 * 60 * 1000) {
    const hours = Math.floor(diff / (60 * 60 * 1000))
    return `${hours}小时前`
  }
  
  // 小于7天显示天数
  if (diff < 7 * 24 * 60 * 60 * 1000) {
    const days = Math.floor(diff / (24 * 60 * 60 * 1000))
    return `${days}天前`
  }
  
  // 超过7天显示具体日期
  return date.toLocaleDateString('zh-CN', {
    year: 'numeric',
    month: 'short',
    day: 'numeric'
  })
}

// 组件挂载时加载数据
onMounted(async () => {
  await Promise.all([
    loadDrafts(),
    loadCategories()
  ])
})
</script>

<style scoped>
.drafts-page {
  max-width: 1200px;
  margin: 0 auto;
  padding: 20px;
}

.page-header {
  text-align: center;
  margin-bottom: 40px;
}

.page-title {
  font-size: 2.5rem;
  color: var(--text-color);
  margin: 0 0 12px 0;
  font-weight: 700;
}

.page-description {
  font-size: 1.1rem;
  color: var(--text-color);
  opacity: 0.7;
  margin: 0;
}

.actions-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 30px;
  gap: 20px;
}

.search-box {
  position: relative;
  flex: 1;
  max-width: 400px;
}

.search-input {
  width: 100%;
  padding: 12px 16px 12px 45px;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  font-size: 14px;
  background: var(--bg-color);
  color: var(--text-color);
  transition: border-color 0.3s;
}

.search-input:focus {
  outline: none;
  border-color: var(--primary-color);
}

.search-icon {
  position: absolute;
  left: 15px;
  top: 50%;
  transform: translateY(-50%);
  font-size: 16px;
  opacity: 0.5;
}

.create-btn {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 20px;
  background: var(--primary-color);
  color: white;
  border: none;
  border-radius: 8px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: background-color 0.3s;
}

.create-btn:hover {
  background: var(--secondary-color);
}

.btn-icon {
  font-size: 16px;
}

.drafts-container {
  min-height: 400px;
}

.loading-state,
.error-state,
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 60px 20px;
  text-align: center;
}

.loading-spinner {
  width: 40px;
  height: 40px;
  border: 3px solid var(--border-color);
  border-top: 3px solid var(--primary-color);
  border-radius: 50%;
  animation: spin 1s linear infinite;
  margin-bottom: 20px;
}

@keyframes spin {
  0% { transform: rotate(0deg); }
  100% { transform: rotate(360deg); }
}

.error-icon,
.empty-icon {
  font-size: 48px;
  margin-bottom: 20px;
}

.error-state h3,
.empty-state h3 {
  color: var(--text-color);
  margin: 0 0 12px 0;
}

.error-state p,
.empty-state p {
  color: var(--text-color);
  opacity: 0.7;
  margin: 0 0 20px 0;
}

.retry-btn {
  padding: 8px 16px;
  background: var(--primary-color);
  color: white;
  border: none;
  border-radius: 6px;
  cursor: pointer;
  transition: background-color 0.3s;
}

.retry-btn:hover {
  background: var(--secondary-color);
}

.drafts-list {
  display: grid;
  gap: 20px;
}

.draft-card {
  background: var(--bg-color);
  border: 1px solid var(--border-color);
  border-radius: 12px;
  padding: 24px;
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  transition: all 0.3s ease;
}

.draft-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 25px rgba(0, 0, 0, 0.1);
  border-color: var(--primary-color);
}

.draft-content {
  flex: 1;
  margin-right: 20px;
}

.draft-title {
  font-size: 1.3rem;
  font-weight: 600;
  color: var(--text-color);
  margin: 0 0 12px 0;
  cursor: pointer;
  transition: color 0.3s;
  line-height: 1.4;
}

.draft-title:hover {
  color: var(--primary-color);
}

.draft-summary {
  color: var(--text-color);
  opacity: 0.8;
  margin: 0 0 16px 0;
  line-height: 1.6;
  display: -webkit-box;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.draft-meta {
  display: flex;
  gap: 20px;
  flex-wrap: wrap;
}

.draft-date,
.draft-words,
.draft-category {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 0.85rem;
  color: var(--text-color);
  opacity: 0.7;
}

.meta-icon {
  font-size: 14px;
}

.draft-actions {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}

.action-btn {
  width: 36px;
  height: 36px;
  border: none;
  border-radius: 6px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.3s;
  font-size: 14px;
}

.edit-btn {
  background: var(--hover-color);
  color: var(--text-color);
}

.edit-btn:hover {
  background: var(--primary-color);
  color: white;
}

.publish-btn {
  background: #48bb78;
  color: white;
}

.publish-btn:hover {
  background: #38a169;
}

.delete-btn {
  background: #f56565;
  color: white;
}

.delete-btn:hover {
  background: #e53e3e;
}

.pagination {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 20px;
  margin-top: 40px;
}

.page-btn {
  padding: 8px 16px;
  background: var(--bg-color);
  color: var(--text-color);
  border: 1px solid var(--border-color);
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.3s;
}

.page-btn:hover:not(:disabled) {
  background: var(--primary-color);
  color: white;
  border-color: var(--primary-color);
}

.page-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.page-info {
  color: var(--text-color);
  font-weight: 500;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .drafts-page {
    padding: 15px;
  }
  
  .page-title {
    font-size: 2rem;
  }
  
  .actions-bar {
    flex-direction: column;
    align-items: stretch;
  }
  
  .search-box {
    max-width: none;
  }
  
  .draft-card {
    flex-direction: column;
    gap: 20px;
  }
  
  .draft-content {
    margin-right: 0;
  }
  
  .draft-actions {
    align-self: flex-end;
  }
  
  .draft-meta {
    gap: 15px;
  }
}
</style>