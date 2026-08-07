<template>
  <div class="content my-posts-page">
    <div class="page-title">
      <span class="title-badge"><Icon name="book" size="12" /> My Posts</span>
      <h1 class="title-heading">我的<span class="title-highlight">文章</span></h1>
      <p class="title-desc">{{ activeTab === 'posts' ? '管理您已发布的文章，编辑或删除' : '管理您的草稿文章，继续编辑或发布' }}</p>
    </div>

    <!-- 文章/草稿切换 -->
    <div class="tab-bar">
      <button
        :class="['tab-btn', { active: activeTab === 'posts' }]"
        @click="switchTab('posts')"
      >
        <Icon name="book" size="15" /> 我的文章
      </button>
      <button
        :class="['tab-btn', { active: activeTab === 'drafts' }]"
        @click="switchTab('drafts')"
      >
        <Icon name="file" size="15" /> 草稿箱
      </button>
    </div>

    <!-- 操作栏 -->
    <div class="actions-bar">
      <div class="search-box">
        <Icon name="search" size="16" class="search-icon" />
        <input v-model="activeState.searchKeyword" type="text" :placeholder="activeTab === 'posts' ? '搜索文章...' : '搜索草稿...'" class="search-input"
          @keyup.enter="handleSearch" />
      </div>
      <button class="create-btn" @click="createNew">
        <Icon name="edit" size="16" />
        {{ activeTab === 'posts' ? '新建文章' : '新建草稿' }}
      </button>
    </div>

    <!-- 列表 -->
    <div class="posts-container">
      <!-- 加载状态 -->
      <div v-if="activeState.loading" class="loading-state text-sm">
        <div class="loading-spinner"></div>
        <p>加载中...</p>
      </div>

      <!-- 错误状态 -->
      <div v-else-if="activeState.error" class="error-state text-sm">
        <Icon name="close" size="40" class="error-icon" />
        <p>{{ activeState.error }}</p>
        <button class="retry-btn" @click="loadActive">重试</button>
      </div>

      <!-- 空状态 -->
      <div v-else-if="filteredList.length === 0" class="empty-state flex flex-col flex-ac text-sm">
        <h3>{{ activeTab === 'posts' ? '暂无文章' : '暂无草稿' }}</h3>
        <p>{{ activeTab === 'posts' ? '开始创建您的第一篇文章吧！' : '开始创建您的第一篇草稿吧！' }}</p>
        <img src="@/assets/image/扑到.png" alt="" class="fit-err">
        <button class="create-btn" @click="createNew">
          <Icon name="edit" size="16" />
          {{ activeTab === 'posts' ? '新建文章' : '新建草稿' }}
        </button>
      </div>

      <!-- 已发布文章列表 -->
      <div v-else-if="activeTab === 'posts'" class="posts-list">
        <div v-for="post in filteredList" :key="post.id" class="post-card bg-card">
          <img v-if="post.thumbnail" class="fit" :src="post.thumbnail" alt="" loading="lazy" @error="handleImageError">
          <img v-else-if="post.coverImage" class="fit" :src="post.coverImage" alt="" loading="lazy" @error="handleImageError">
          <img v-else class="fit" src="@/assets/image/err.png" alt="" loading="lazy">

          <div class="post-content flex flex-col gap-12">
            <h3 class="post-title text-primary" @click="viewPost(post.id)">
              {{ post.title }}
            </h3>
            <p class="post-summary text-base text-subtle" v-if="post.summary">
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
                {{ post.commentCount || 0 }} 评论
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

      <!-- 草稿列表 -->
      <div v-else class="posts-list">
        <div v-for="draft in filteredList" :key="draft.id" class="post-card bg-card">
          <img v-if="draft.thumbnail" class="fit" :src="draft.thumbnail" alt="" loading="lazy" @error="handleImageError">
          <img v-else-if="draft.coverImage" class="fit" :src="draft.coverImage" alt="" loading="lazy" @error="handleImageError">
          <img v-else class="fit" src="@/assets/image/err.png" alt="" loading="lazy">
          <div class="post-content flex flex-col gap-12">
            <h3 class="post-title text-primary" @click="editDraft(draft.id)">
              {{ draft.title || '无标题草稿' }}
            </h3>
            <p class="post-summary text-base text-subtle" v-if="draft.summary">
              {{ draft.summary }}
            </p>
            <div class="tags-cloud" v-if="draft.tags && draft.tags.length > 0">
              <span @click.stop="goToTag(tag.id)" v-for="tag in draft.tags" :key="tag.id" class="tag">
                {{ tag.name }}
              </span>
            </div>
            <div class="post-meta">
              <span class="post-date">
                <Icon name="calendar" size="14" class="meta-icon" />
                更新于 {{ formatRelativeTime(draft.updatedAt || draft.createdAt) }}
              </span>
              <span class="post-category" v-if="draft.category">
                <Icon name="tag" size="14" class="meta-icon" />
                {{ draft.category.name }}
              </span>
            </div>
          </div>

          <div class="post-actions">
            <button class="action-btn edit-btn" @click="editDraft(draft.id)" title="编辑">
              <Icon name="edit" size="16" />
            </button>
            <button class="action-btn publish-btn" @click="publishDraft(draft.id)" title="发布">
              <Icon name="rocket" size="16" />
            </button>
            <button class="action-btn delete-btn" @click="deleteDraft(draft.id)" title="删除">
              <Icon name="trash" size="16" />
            </button>
          </div>
        </div>
      </div>
    </div>

    <!-- 分页 -->
    <Pagination
      v-if="!activeState.loading && filteredList.length > 0"
      :current-page="activeState.currentPage"
      :total-pages="totalPages"
      @page-change="changePage"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, reactive, watch, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { PostService, type PostListItem, type PageResponse } from '../services/post'
import { useErrorHandler } from '@/composables/useErrorHandler'
import { formatDate, formatRelativeTime } from '@/utils/utils'
import { handleImageError } from '@/composables/useImageFallback'
import Pagination from '@/components/Pagination.vue'
import Icon from '@/components/Icon.vue'

const router = useRouter()
const route = useRoute()
const { handleAsync, showToastSuccess, showToastError, confirm } = useErrorHandler()

type TabKey = 'posts' | 'drafts'

// 文章与草稿各自独立的状态（列表/搜索/分页），切换 tab 互不影响
interface TabState {
  list: PostListItem[]
  loading: boolean
  error: string
  searchKeyword: string
  currentPage: number
  pageSize: number
  totalCount: number
}

const createTabState = (): TabState => ({
  list: [],
  loading: false,
  error: '',
  searchKeyword: '',
  currentPage: 1,
  pageSize: 10,
  totalCount: 0,
})

const postsState = reactive<TabState>(createTabState())
const draftsState = reactive<TabState>(createTabState())

// 初始 tab 从 URL 读取，支持 /my-posts?tab=drafts
const activeTab = ref<TabKey>(route.query.tab === 'drafts' ? 'drafts' : 'posts')
const activeState = computed(() => (activeTab.value === 'posts' ? postsState : draftsState))

// 浏览器前进/后退改变 query 时跟随切换
watch(() => route.query.tab, (tab) => {
  const next: TabKey = tab === 'drafts' ? 'drafts' : 'posts'
  if (next !== activeTab.value) {
    activeTab.value = next
    if (activeState.value.list.length === 0 && !activeState.value.loading) {
      loadActive()
    }
  }
})

// 计算属性
const filteredList = computed(() => {
  const state = activeState.value
  if (!state.searchKeyword) {
    return state.list
  }
  return state.list.filter(item =>
    item.title.toLowerCase().includes(state.searchKeyword.toLowerCase()) ||
    (item.summary && item.summary.toLowerCase().includes(state.searchKeyword.toLowerCase()))
  )
})

const totalPages = computed(() => {
  const state = activeState.value
  return Math.ceil(state.totalCount / state.pageSize)
})

// 方法
const loadActive = async () => {
  const state = activeState.value
  await handleAsync(async () => {
    state.loading = true
    state.error = ''

    const params = {
      page: state.currentPage,
      size: state.pageSize,
      keyword: state.searchKeyword || undefined
    }
    const response: PageResponse<PostListItem> = activeTab.value === 'posts'
      ? await PostService.getMyPosts(params)
      : await PostService.getDraftList(params)

    state.list = response.records
    state.totalCount = response.total
  }, {
    onError: () => {
      state.error = activeTab.value === 'posts' ? '加载文章失败，请稍后重试' : '加载草稿失败，请稍后重试'
    },
    onFinally: () => {
      state.loading = false
    }
  })
}

const switchTab = (tab: TabKey) => {
  if (activeTab.value === tab) return
  activeTab.value = tab
  // URL 同步，刷新/分享保留当前 tab；posts 为默认 tab，不留在 query 里
  const query = { ...route.query }
  if (tab === 'drafts') query.tab = 'drafts'
  else delete query.tab
  router.replace({ query })
  // 懒加载：首次进入该 tab 才请求
  if (activeState.value.list.length === 0 && !activeState.value.loading) {
    loadActive()
  }
}

// 跳转到标签页面
const goToTag = (tagId: number) => {
  router.push(`/tags/${tagId}`)
}

// 新建文章/草稿（同一编辑器，draft=true 默认草稿状态）
const createNew = () => {
  router.push(activeTab.value === 'drafts' ? '/create?draft=true' : '/create')
}

const editDraft = (draftId: number) => {
  router.push(`/create?draft=${draftId}`)
}

const publishDraft = async (draftId: number) => {
  const confirmed = await confirm('确定要发布这篇草稿吗？')
  if (!confirmed) {
    return
  }

  await handleAsync(async () => {
    await PostService.publishPost(draftId)
    await loadActive()
    showToastSuccess('草稿发布成功！')
  }, {
    onError: () => {
      showToastError('发布失败，请稍后重试')
    }
  })
}

const deleteDraft = async (draftId: number) => {
  const confirmed = await confirm('确定要删除这篇草稿吗？此操作不可恢复。')
  if (!confirmed) {
    return
  }

  await handleAsync(async () => {
    await PostService.deletePost(draftId)
    await loadActive()
    showToastSuccess('草稿已删除')
  }, {
    onError: () => {
      showToastError('删除失败，请稍后重试')
    }
  })
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
    await loadActive()
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
    await loadActive()
    showToastSuccess('文章已取消发布，转为草稿状态！')
  }, {
    onError: () => {
      showToastError('取消发布文章失败，请稍后重试')
    }
  })
}

const handleSearch = () => {
  activeState.value.currentPage = 1
  loadActive()
}

const changePage = (page: number) => {
  activeState.value.currentPage = page
  loadActive()
}

// 生命周期
onMounted(() => {
  loadActive()
})
</script>

<style scoped lang="scss">
@use "@/assets/styles/tokens" as *;
.my-posts-page {
  max-width: 1200px;
  margin: 0 auto;
  padding: 20px;
  overflow: hidden;
  box-sizing: border-box;
}

/* tab 切换条 */
.tab-bar {
  display: flex;
  gap: 8px;
  margin-bottom: 24px;
  padding: 4px;
  background: var(--bg-soft, #f5f5f5);
  border-radius: 30px;
  width: fit-content;
}

.tab-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 20px;
  border: none;
  border-radius: 30px;
  background: transparent;
  color: var(--text-main);
  opacity: 0.6;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.25s ease;
}

.tab-btn:hover {
  opacity: 1;
}

.tab-btn.active {
  background: var(--color-primary);
  color: #fff;
  opacity: 1;
  box-shadow: 0 2px 8px rgba(var(--color-primary-rgb), 0.35);
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
  overflow: hidden;
  min-width: 0;
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
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  min-width: 0;
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

.publish-btn {
  background-color: var(--bg-success, #e3f9ea);
  color: var(--color-success, #2f855a);
}

.publish-btn:hover {
  background-color: var(--bg-success, #c6f6d5);
}

.delete-btn {
  background-color: var(--bg-error, #ffebee);
  color: var(--color-error, #d32f2f);
}

.delete-btn:hover {
  background-color: var(--bg-error, #ffcdd2);
}

/* 响应式设计 */
@include respond(md) {
  .my-posts-page {
    padding: 15px;
  }

  .actions-bar {
    flex-direction: column;
    align-items: stretch;
    gap: 12px;
  }

  .search-box {
    width: 100%;
  }

  .post-card {
    flex-direction: column;
    gap: 15px;
    padding: 16px;
  }

  .post-card>img {
    width: 100%;
    height: 180px;
    object-fit: cover;
    border-radius: 8px;
  }

  .post-title {
    font-size: 1.1rem;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  .post-actions {
    align-self: stretch;
    justify-content: flex-end;
    margin-top: 10px;
    border-top: 1px solid var(--border-soft);
    padding-top: 12px;
  }

  .post-meta {
    gap: 10px;
    flex-direction: column;
    align-items: flex-start;
  }
}

@include respond(sm) {
  .actions-bar {
    gap: 10px;
  }

  .tab-bar {
    width: 100%;
  }

  .tab-btn {
    flex: 1;
    justify-content: center;
  }

  .post-actions {
    justify-content: space-between;
  }

  .action-btn {
    flex: 1;
  }
}
</style>
