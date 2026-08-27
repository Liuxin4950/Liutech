<template>
  <div class="content my-posts-page">
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
      <button class="btn-primary create-btn" @click="createNew">
        <Icon name="edit" size="16" />
        {{ activeTab === 'posts' ? '新建文章' : '新建草稿' }}
      </button>
    </div>

    <!-- 列表：文章/草稿均复用通用 ArticleList，仅操作按钮与 meta 行定制 -->
    <ArticleList
      v-if="activeTab === 'posts'"
      class="mt-8"
      :posts="filteredPosts"
      :loading="activeState.loading"
      :error="activeState.error"
      :pagination="paginationInfo"
      @post-click="viewPost"
      @page-change="changePage"
      @retry="loadActive"
    >
      <template #empty>
        <h3>暂无文章</h3>
        <p>开始创建您的第一篇文章吧！</p>
        <button class="btn-primary" @click="createNew">
          <Icon name="edit" size="16" />
          新建文章
        </button>
      </template>
      <template #actions="{ post }">
        <button type="button" class="action-btn view-btn" aria-label="查看文章" @click.stop="viewPost(post.id)" title="查看">
          <Icon name="eye" size="16" />
          <span class="action-label">查看</span>
        </button>
        <button type="button" class="action-btn edit-btn" aria-label="编辑文章" @click.stop="editPost(post.id)" title="编辑">
          <Icon name="edit" size="16" />
          <span class="action-label">编辑</span>
        </button>
        <button
          v-if="post.status === 'published'"
          type="button"
          class="action-btn unpublish-btn"
          aria-label="取消发布"
          @click.stop="unpublishPost(post.id)"
          title="取消发布"
        >
          <Icon name="upload" size="16" />
          <span class="action-label">下架</span>
        </button>
        <button type="button" class="action-btn delete-btn" aria-label="删除文章" @click.stop="deletePost(post.id)" title="删除">
          <Icon name="trash" size="16" />
          <span class="action-label">删除</span>
        </button>
      </template>
    </ArticleList>

    <ArticleList
      v-else
      :posts="filteredDrafts"
      :loading="activeState.loading"
      :error="activeState.error"
      :pagination="paginationInfo"
      @post-click="editDraft"
      @page-change="changePage"
      @retry="loadActive"
    >
      <template #empty>
        <h3>暂无草稿</h3>
        <p>开始创建您的第一篇草稿吧！</p>
        <button class="btn-primary" @click="createNew">
          <Icon name="edit" size="16" />
          新建草稿
        </button>
      </template>
      <template #meta="{ post }">
        <div class="flex flex-ac gap-16 text-sm text-subtle">
          <span>
            <Icon name="calendar" size="14" class="meta-icon" />
            更新于 {{ formatRelativeTime(post.updatedAt || post.createdAt) }}
          </span>
          <span v-if="post.category">
            <Icon name="tag" size="14" class="meta-icon" />
            {{ post.category.name }}
          </span>
        </div>
      </template>
      <template #actions="{ post }">
        <button type="button" class="action-btn edit-btn" aria-label="编辑草稿" @click.stop="editDraft(post.id)" title="编辑">
          <Icon name="edit" size="16" />
          <span class="action-label">编辑</span>
        </button>
        <button type="button" class="action-btn publish-btn" aria-label="发布草稿" @click.stop="publishDraft(post.id)" title="发布">
          <Icon name="rocket" size="16" />
          <span class="action-label">发布</span>
        </button>
        <button type="button" class="action-btn delete-btn" aria-label="删除草稿" @click.stop="deleteDraft(post.id)" title="删除">
          <Icon name="trash" size="16" />
          <span class="action-label">删除</span>
        </button>
      </template>
    </ArticleList>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, reactive, watch, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { PostService, type PostListItem, type PageResponse } from '../services/post'
import { useErrorHandler } from '@/composables/useErrorHandler'
import { formatRelativeTime } from '@/utils/utils'
import { useBannerStore } from '@/stores/banner'
import bannerFallback from '@/assets/image/banner/banner0.png'
import ArticleList from '@/components/ArticleList.vue'
import Icon from '@/components/Icon.vue'

const router = useRouter()
const route = useRoute()
const { handleAsync, showToastSuccess, showToastError, confirm } = useErrorHandler()
const bannerStore = useBannerStore()

type TabKey = 'posts' | 'drafts'

// 页面标题由 banner 承载（hero 大横幅），tab 切换时标题联动
const updateBanner = () => {
  const isPosts = activeTab.value === 'posts'
  bannerStore.setBanner({
    slides: [{
      title: isPosts ? '我的' : '草稿',
      description: isPosts ? '管理您已发布的文章，编辑或删除' : '管理您的草稿文章，继续编辑或发布',
      imageUrl: bannerFallback,
      sortOrder: 0,
      status: 1
    }],
    badgeText: isPosts ? 'My Posts' : 'Drafts',
    titleAs: 'h1',
    titleHighlight: isPosts ? '文章' : '箱',
    mode: 'hero'
  })
}

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

// tab 切换时 banner 标题联动（我的文章 ↔ 草稿箱）
watch(activeTab, updateBanner)

// 计算属性：按关键字过滤（文章/草稿各自独立），草稿标题空时兜底
const filterByKeyword = (list: PostListItem[]) => {
  const keyword = activeState.value.searchKeyword
  if (!keyword) return list
  return list.filter(item =>
    (item.title || '').toLowerCase().includes(keyword.toLowerCase()) ||
    (item.summary && item.summary.toLowerCase().includes(keyword.toLowerCase()))
  )
}

const filteredPosts = computed(() => filterByKeyword(postsState.list))
const filteredDrafts = computed(() =>
  filterByKeyword(draftsState.list).map(draft => ({ ...draft, title: draft.title || '无标题草稿' }))
)

// 通用列表分页参数（ArticleList 内部渲染分页器）
const paginationInfo = computed(() => {
  const state = activeState.value
  return {
    current: state.currentPage,
    size: state.pageSize,
    total: state.totalCount,
    pages: Math.ceil(state.totalCount / state.pageSize)
  }
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
  // URL 同步（刷新/分享保留当前 tab；posts 为默认 tab，不留在 query 里）
  // 用 history.replaceState 而非 router.replace：后者触发 vue-router 导航会滚动回顶部
  const query = { ...route.query }
  if (tab === 'drafts') query.tab = 'drafts'
  else delete query.tab
  const params = new URLSearchParams()
  Object.entries(query).forEach(([k, v]) => {
    if (Array.isArray(v)) v.forEach(item => params.append(k, item ?? ''))
    else if (v != null) params.append(k, String(v))
  })
  const queryStr = params.toString()
  window.history.replaceState(window.history.state, '', route.path + (queryStr ? `?${queryStr}` : ''))
  // 懒加载：首次进入该 tab 才请求
  if (activeState.value.list.length === 0 && !activeState.value.loading) {
    loadActive()
  }
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
  updateBanner()
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

/* tab 切换条（标题已由 banner 承载，顶部留出呼吸间距） */
.tab-bar {
  display: flex;
  gap: 8px;
  margin: 0 0px 10px;
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

/* 尺寸/配色统一走全局 .btn-primary，这里仅保留防换行，避免与全局重复定义产生不一致 */
.create-btn {
  white-space: nowrap;
}

/* 卡片结构/状态/分页样式由通用 ArticleList 接管，这里只保留页面骨架与操作按钮配色 */

.meta-icon {
  margin-right: 5px;
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

.action-label {
  display: none;
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
    /* 左对齐而非 stretch：避免"新建文章"按钮被拉成 100% 宽度 */
    align-items: flex-start;
    gap: 12px;
  }

  .search-box {
    width: 100%;
    max-width: 100%;
  }

  .action-btn {
    flex: 1;
    width: auto;
    min-width: 0;
    gap: 6px;
  }

  .action-label {
    display: inline;
    white-space: nowrap;
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
}
</style>
