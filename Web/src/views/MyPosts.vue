<template>
  <div class="content">
    <div class="page-header">
      <h1 class="page-title"><Icon name="book" size="24" /> 我的文章</h1>
      <p class="page-description">管理您已发布的文章，编辑或删除</p>
    </div>

    <!-- 操作栏 -->
    <div class="actions-bar">
      <div class="search-box">
        <input v-model="searchKeyword" type="text" placeholder="搜索文章..." class="search-input"
          @keyup.enter="handleSearch" />
        <Icon name="search" size="16" class="search-icon" />
      </div>
      <div class="flex gap-20">
        <button class="create-btn" @click="createNewPost">
          <Icon name="edit" size="16" />
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
      <div v-if="loading" class="loading-state text-sm">
        <div class="loading-spinner"></div>
        <p>加载中...</p>
      </div>

      <!-- 错误状态 -->
      <div v-else-if="error" class="error-state text-sm">
        <Icon name="close" size="40" class="error-icon" />
        <p>{{ error }}</p>
        <button class="retry-btn" @click="loadPosts">重试</button>
      </div>

      <!-- 空状态 -->
      <div v-else-if="filteredPosts.length === 0" class="empty-state flex flex-col flex-ac text-sm">
        <!-- <span class="empty-icon">📝</span> -->
        <h3>暂无文章</h3>
        <p>开始创建您的第一篇文章吧！</p>
        <img src="@/assets/image/扑到.png" alt="" class="fit-err">
        <button class="create-btn" @click="createNewPost">
          <Icon name="edit" size="16" />
          新建文章
        </button>
      </div>

      <!-- 文章列表 -->
      <div v-else class="posts-list">
        <div v-for="post in filteredPosts" :key="post.id" class="post-card bg-card">
          <img v-if="post.thumbnail" class="fit" :src="post.coverImage" alt="" loading="lazy">
          <img v-else-if="post.coverImage" class="fit" :src="post.coverImage" alt="" loading="lazy">
          <img v-else class="fit" src="@/assets/image/images.jpg" alt="" loading="lazy">
          
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
                <Icon name="calendar" size="14" class="meta-icon" />
                发布于 {{ formatDate(post.createdAt) }}
              </span>
              <span class="post-category" v-if="post.category">
                <Icon name="tag" size="14" class="meta-icon" />
                {{ post.category.name }}
              </span>
              <span class="post-views">
                <Icon name="eye" size="14" class="meta-icon" />
                {{ post.viewCount || 0 }} 浏览
              </span>
              <span class="post-likes">
                <Icon name="heart" size="14" class="meta-icon" />
                {{ post.likeCount || 0 }} 点赞
              </span>
              <span class="post-comments">
                <Icon name="message" size="14" class="meta-icon" />
                {{ post.commentCount }} 评论
              </span>
            </div>
          </div>

          <div class="post-actions">
            <button class="action-btn view-btn" @click="viewPost(post.id)" title="查看">
              <Icon name="eye" size="16" />
            </button>
            <button class="action-btn edit-btn" @click="editPost(post.id)" title="编辑">
              <Icon name="edit" size="16" />
            </button>
            <button
              v-if="post.status === 'published'"
              class="action-btn unpublish-btn"
              @click="unpublishPost(post.id)"
              title="取消发布"
            >
              <Icon name="upload" size="16" />
            </button>
            <button class="action-btn delete-btn" @click="deletePost(post.id)" title="删除">
              <Icon name="trash" size="16" />
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
import { formatDate } from '@/utils/utils'
import Icon from '@/components/Icon.vue'

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

    totalCount.value = response.total
  }, {
    onError: () => {
      error.value = '加载文章失败，请稍后重试'
    },
    onFinally: () => {
      loading.value = false
    }
  })
}

const loadCategories = async () => {
  try {
    categories.value = await CategoryService.getCategories()
  } catch {
    // 加载分类失败时静默处理
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
    onError: () => {
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
    onError: () => {
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
  background-color: var(--bg-info, #e3f2fd);
  color: var(--color-info, #1976d2);
}

.view-btn:hover {
  background-color: var(--bg-info, #bbdefb);
}

.edit-btn {
  background-color: var(--bg-warning, #fff3e0);
  color: var(--color-warning, #f57c00);
}

.edit-btn:hover {
  background-color: var(--bg-warning, #ffe0b2);
}

.unpublish-btn {
  background-color: rgba(var(--color-primary-rgb, 118, 75, 162), 0.1);
  color: var(--color-primary);
}

.unpublish-btn:hover {
  background-color: rgba(var(--color-primary-rgb, 118, 75, 162), 0.2);
}

.delete-btn {
  background-color: var(--bg-error, #ffebee);
  color: var(--color-error, #d32f2f);
}

.delete-btn:hover {
  background-color: var(--bg-error, #ffcdd2);
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