<template>
  <div class="content">
    <div class="page-header">
      <h1 class="page-title">📚 我的文章</h1>
      <p class="page-description">管理您已发布的文章，编辑或删除</p>
    </div>

    <!-- 操作栏 -->
    <div class="actions-bar">
      <div class="search-box">
        <input v-model="searchKeyword" type="text" placeholder="搜索文章..." class="search-input"
          @keyup.enter="handleSearch" />
        <span class="search-icon">🔍</span>
      </div>
      <div class="flex gap-20">
        <button class="create-btn" @click="createNewPost">
          <!-- <span class="btn-icon"></span> -->
          新建文章
        </button>
        <button class="create-btn" @click="goDrafts">
          <!-- <span class="btn-icon"></span> -->
          草稿箱 
        </button>
      </div>
   
    </div>

    <!-- 文章列表 -->
    <div class="posts-container">
      <!-- 加载状态 -->
      <div v-if="loading" class="loading-state">
        <div class="loading-spinner"></div>
        <p>加载中...</p>
      </div>

      <!-- 错误状态 -->
      <div v-else-if="error" class="error-state">
        <span class="error-icon">❌</span>
        <p>{{ error }}</p>
        <button class="retry-btn" @click="loadPosts">重试</button>
      </div>

      <!-- 空状态 -->
      <div v-else-if="filteredPosts.length === 0" class="empty-state">
        <span class="empty-icon">📝</span>
        <h3>暂无文章</h3>
        <p>开始创建您的第一篇文章吧！</p>
        <button class="create-btn" @click="createNewPost">
          <span class="btn-icon">✏️</span>
          新建文章
        </button>
      </div>

      <!-- 文章列表 -->
      <div v-else class="posts-list">
        <div v-for="post in filteredPosts" :key="post.id" class="post-card bg-card">
          <img v-if="post.thumbnail" class="fit" :src="post.coverImage" alt="">
          <img v-else-if="post.coverImage" class="fit" :src="post.coverImage" alt="">
          <img v-else="post.coverImage" class="fit" src="@/assets/image/images.jpg" alt="">
          
          <div class="post-content flex flex-col gap-12">
            <h3 class="post-title text-primary" @click="viewPost(post.id)">
              {{ post.title }}
            </h3>
            <p class="post-summary" v-if="post.summary">
              {{ post.summary }}
            </p>
            <div class="tags-cloud" v-if="post.tags && post.tags.length > 0">
              <span @click.stop="goToTag(tag.id)" v-for="tag in post.tags" :key="tag.id" class="tag">
                {{ tag.name }}
              </span>
            </div>

            <div class="post-meta">
              <span class="post-date">
                <span class="meta-icon">📅</span>
                发布于 {{ formatDate(post.createdAt) }}
              </span>
              <span class="post-category" v-if="post.category">
                <span class="meta-icon">🏷️</span>
                {{ post.category.name }}
              </span>
              <span class="post-views">
                <span class="meta-icon">👁️</span>
                {{ post.viewCount || 0 }} 浏览
              </span>
              <span class="post-likes">
                <span class="meta-icon">❤️</span>
                {{ post.likeCount || 0 }} 点赞
              </span>
              <span class="post-comments">
                <span class="meta-icon">💬</span>
                {{ post.commentCount }} 评论
              </span>
            </div>
          </div>

          <div class="post-actions">
            <button class="action-btn view-btn" @click="viewPost(post.id)" title="查看">
              <span class="btn-icon">👁️</span>
            </button>
            <button class="action-btn edit-btn" @click="editPost(post.id)" title="编辑">
              <span class="btn-icon">✏️</span>
            </button>
            <button 
              v-if="post.status === 'published'" 
              class="action-btn unpublish-btn" 
              @click="unpublishPost(post.id)" 
              title="取消发布"
            >
              <span class="btn-icon">📤</span>
            </button>
            <button class="action-btn delete-btn" @click="deletePost(post.id)" title="删除">
              <span class="btn-icon">🗑️</span>
            </button>
          </div>
        </div>
      </div>
    </div>

    <!-- 分页 -->
    <div v-if="totalPages > 1" class="pagination">
      <button class="page-btn" :disabled="currentPage === 1" @click="changePage(currentPage - 1)">
        上一页
      </button>
      <span class="page-info">{{ currentPage }} / {{ totalPages }}</span>
      <button class="page-btn" :disabled="currentPage === totalPages" @click="changePage(currentPage + 1)">
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
import { formatDate } from '@/utils/uitls'

const router = useRouter()
const { handleAsync,showToastSuccess,showToastError,confirm } = useErrorHandler()

// 响应式数据
const posts = ref<PostListItem[]>([])
const categories = ref<Category[]>([])
const loading = ref(false)
const error = ref('')
const searchKeyword = ref('')
const currentPage = ref(1)
const pageSize = ref(10)
const totalCount = ref(0)

// 计算属性
const filteredPosts = computed(() => {
  if (!searchKeyword.value) {
    return posts.value
  }
  return posts.value.filter(post =>
    post.title.toLowerCase().includes(searchKeyword.value.toLowerCase()) ||
    (post.summary && post.summary.toLowerCase().includes(searchKeyword.value.toLowerCase()))
  )
})

const totalPages = computed(() => {
  return Math.ceil(totalCount.value / pageSize.value)
})

// 方法
const loadPosts = async () => {
  await handleAsync(async () => {
    loading.value = true
    error.value = ''

    const response: PageResponse<PostListItem> = await PostService.getMyPosts({
      page: currentPage.value,
      size: pageSize.value,
      keyword: searchKeyword.value || undefined
    })

    posts.value = response.records
    console.log(posts.value);
    
    totalCount.value = response.total
  }, {
    onError: (err) => {
      error.value = '加载文章失败，请稍后重试'
      console.error('加载文章失败:', err)
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
// 跳转到标签页面
const goToTag = (tagId: number) => {
  router.push(`/tags/${tagId}`)
}
// 跳转新建文章
const createNewPost = () => {
  router.push('/create')
}

const goDrafts = () => {
  router.push('/drafts')
}


const viewPost = (postId: number) => {
  router.push(`/post/${postId}?from=my-posts`)
}

const editPost = (postId: number) => {
  router.push(`/create?edit=${postId}`)
}

const deletePost = async (postId: number) => {
  const confirmed = await confirm('确定要删除这篇文章吗？删除后无法恢复！')
  if (!confirmed) {
    return
  }

  await handleAsync(async () => {
    await PostService.deletePost(postId)

    // 重新加载文章列表
    await loadPosts()

    // 显示成功消息
    showToastSuccess('文章删除成功！')
  }, {
    onError: (err) => {
      console.error('删除文章失败:', err)
      showToastError('删除文章失败，请稍后重试')
    }
  })
}

const unpublishPost = async (postId: number) => {
  const confirmed = await confirm('文章将转为草稿状态。')
  if (!confirmed) {
    return
  }

  await handleAsync(async () => {
    await PostService.unpublishPost(postId)

    // 重新加载文章列表
    await loadPosts()

    // 显示成功消息
    showToastSuccess('文章已取消发布，转为草稿状态！')
  }, {
    onError: (err) => {
      console.error('取消发布文章失败:', err)
      showToastError('取消发布文章失败，请稍后重试')
    }
  })
}

const handleSearch = () => {
  currentPage.value = 1
  loadPosts()
}

const changePage = (page: number) => {
  currentPage.value = page
  loadPosts()
}



// 生命周期
onMounted(async () => {
  await Promise.all([
    loadPosts(),
    loadCategories()
  ])
})
</script>

<style scoped>
.my-posts-page {
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
  color: var(--color-primary);
  margin-bottom: 10px;
}

.page-description {
  color: var(--text-main);
  opacity: 0.8;
  font-size: 1.1rem;
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
  padding: 12px 40px 12px 16px;
  border: 2px solid var(--border-soft);
  border-radius: 25px;
  font-size: 14px;
  background-color: var(--bg-soft);
  color: var(--text-main);
  transition: border-color 0.3s;
}

.search-input:focus {
  outline: none;
  border-color: var(--color-primary);
}

.search-icon {
  position: absolute;
  right: 15px;
  top: 50%;
  transform: translateY(-50%);
  color: var(--text-main);
  opacity: 0.6;
}

.create-btn {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 24px;
  background-color: var(--color-primary);
  color: white;
  border: none;
  border-radius: 25px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.3s;
  white-space: nowrap;
}

.create-btn:hover {
  background-color: var(--color-primary-dark);
  transform: translateY(-2px);
}

.posts-container {
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
  border: 4px solid var(--border-soft);
  border-top: 4px solid var(--color-primary);
  border-radius: 50%;
  animation: spin 1s linear infinite;
  margin-bottom: 20px;
}

@keyframes spin {
  0% {
    transform: rotate(0deg);
  }

  100% {
    transform: rotate(360deg);
  }
}

.error-icon,
.empty-icon {
  font-size: 3rem;
  margin-bottom: 20px;
}

.retry-btn {
  padding: 10px 20px;
  background-color: var(--color-primary);
  color: white;
  border: none;
  border-radius: 20px;
  cursor: pointer;
  margin-top: 15px;
}

.posts-list {
  display: grid;
  gap: 20px;
}

.post-card {
  border: 1px solid var(--border-soft);
  border-radius: 12px;
  padding: 24px;
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  transition: all 0.3s;
  gap: 20px;
}

.post-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 25px rgba(0, 0, 0, 0.1);
}

.post-card>img {
  width: 200px;
  height: 150px;
}

.post-content {
  flex: 1;
}

.post-title {
  font-size: 1.3rem;
  font-weight: 600;
  cursor: pointer;
  transition: color 0.3s;
}

.post-title:hover {
  color: var(--color-primary);
}

.post-summary {
  color: var(--text-main);
  display: -webkit-box;
  -webkit-line-clamp: 2; /* 限制显示2行 */
  -webkit-box-orient: vertical;
  overflow: hidden;
  line-height: 1.5;
  max-height: 3em; /* 2行的高度 (1.5 * 2) */
  word-break: break-word;
}

.post-meta {
  display: flex;
  gap: 20px;
  font-size: 0.9rem;
  color: var(--text-main);
  opacity: 0.7;
  flex-wrap: wrap;
}

.meta-icon {
  margin-right: 5px;
}

.post-actions {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}

.action-btn {
  width: 36px;
  height: 36px;
  border: none;
  border-radius: 8px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.3s;
  font-size: 14px;
}

.view-btn {
  background-color: #e3f2fd;
  color: #1976d2;
}

.view-btn:hover {
  background-color: #bbdefb;
}

.edit-btn {
  background-color: #fff3e0;
  color: #f57c00;
}

.edit-btn:hover {
  background-color: #ffe0b2;
}

.unpublish-btn {
  background-color: #f3e5f5;
  color: #7b1fa2;
}

.unpublish-btn:hover {
  background-color: #e1bee7;
}

.delete-btn {
  background-color: #ffebee;
  color: #d32f2f;
}

.delete-btn:hover {
  background-color: #ffcdd2;
}

.pagination {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 20px;
  margin-top: 40px;
}

.page-btn {
  padding: 10px 20px;
  border: 1px solid var(--border-soft);
  background-color: var(--bg-soft);
  color: var(--text-main);
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.3s;
}

.page-btn:hover:not(:disabled) {
  background-color: var(--color-primary);
  color: white;
  border-color: var(--color-primary);
}

.page-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.page-info {
  font-weight: 500;
  color: var(--text-main);
}

/* 响应式设计 */
@include respond(md) {
  .my-posts-page {
    padding: 15px;
  }

  .actions-bar {
    flex-direction: column;
    align-items: stretch;
  }

  .search-box {
    max-width: none;
  }

  .post-card {
    flex-direction: column;
    gap: 15px;
  }

  .post-actions {
    justify-content: center;
  }

  .post-meta {
    gap: 15px;
  }
  
.post-card>img {
  width: 100%;
  height: auto;
}
}
</style>