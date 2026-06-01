<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { PostService, type PostListItem, type PageResponse } from '../services/post'
import { CategoryService, type Category } from '../services/category'
import { useErrorHandler } from '@/composables/useErrorHandler'
import { formatRelativeTime } from '@/utils/utils'
import { handleImageError } from '@/composables/useImageFallback'
import Pagination from '@/components/Pagination.vue'
import Icon from '@/components/Icon.vue'

const router = useRouter()
const { handleAsync,showToastSuccess,showToastError,confirm } = useErrorHandler()

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
    onError: () => {
      error.value = '加载草稿失败，请稍后重试'
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
    // 加载分类失败时静默处理，不影响主流程
  }
}
// 跳转到标签页面
const goToTag = (tagId: number) => {
  router.push(`/tags/${tagId}`)
}

const createNewDraft = () => {
  router.push('/create?draft=true')
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

    // 重新加载草稿列表
    await loadDrafts()

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

    // 重新加载草稿列表
    await loadDrafts()

    showToastSuccess('草稿已删除')
  }, {
    onError: () => {
      showToastError('删除失败，请稍后重试')
    }
  })
}

const handleSearch = () => {
  currentPage.value = 1
  loadDrafts()
}

const changePage = (page: number) => {
  currentPage.value = page
  loadDrafts()
}

// 生命周期
onMounted(async () => {
  await Promise.all([
    loadDrafts(),
    loadCategories()
  ])
})
</script>

<template>
  <div class="drafts-page">
    <div class="page-header">
      <h1 class="page-title"><Icon name="file" size="24" /> 草稿箱</h1>
      <p class="page-description">管理您的草稿文章，继续编辑或发布</p>
    </div>

    <!-- 操作栏 -->
    <div class="actions-bar">
      <div class="search-box">
                <Icon name="search" size="16" class="search-icon" />
        <input v-model="searchKeyword" type="text" placeholder="搜索草稿..." class="search-input"
          @keyup.enter="handleSearch" />
      </div>
      <button class="create-btn" @click="createNewDraft">
        <Icon name="edit" size="16" />
        新建草稿
      </button>
    </div>


    <!-- 草稿列表 -->
    <div class="drafts-container">
      <!-- 加载状态 -->
    <div v-if="loading" class="loading-state text-sm">
      <div class="loading-spinner"></div>
      <p>加载中...</p>
    </div>

    <!-- 错误状态 -->
    <div v-else-if="error" class="error-state text-sm">
      <Icon name="close" size="40" class="error-icon" />
      <p>{{ error }}</p>
      <button class="retry-btn" @click="loadDrafts">重试</button>
    </div>

    <!-- 空状态 -->
    <div v-else-if="filteredDrafts.length === 0" class="empty-state flex flex-col flex-ac text-sm">
        <h3>暂无草稿</h3>
        <p>开始创建您的第一篇草稿吧！</p>
        <img src="@/assets/image/扑到.png" alt="" class="fit-err">
        <button class="create-btn" @click="createNewDraft">
          <Icon name="edit" size="16" />
          新建草稿
        </button>
      </div>

      <!-- 草稿列表 -->
      <div v-else class="drafts-list">
        <div v-for="draft in filteredDrafts" :key="draft.id" class="draft-card bg-card gap-12">
          <img v-if="draft.thumbnail" class="fit" :src="draft.coverImage" alt="" loading="lazy" @error="handleImageError">
          <img v-else-if="draft.coverImage" class="fit" :src="draft.coverImage" alt="" loading="lazy" @error="handleImageError">
          <img v-else class="fit" src="@/assets/image/err.png" alt="" loading="lazy">
          <div class="draft-content flex flex-col gap-12">
            <h3 class="draft-title text-primary" @click="editDraft(draft.id)">
              {{ draft.title || '无标题草稿' }}
            </h3>
            <p class="draft-summary" v-if="draft.summary">
              {{ draft.summary }}
            </p>
            <div class="tags-cloud" v-if="draft.tags && draft.tags.length > 0">
              <span @click.stop="goToTag(tag.id)" v-for="tag in draft.tags" :key="tag.id" class="tag">
                {{ tag.name }}
              </span>
            </div>
            <div class="draft-meta">
              <span class="draft-date">
                <Icon name="calendar" size="14" class="meta-icon" />
                更新于 {{ formatRelativeTime(draft.updatedAt || draft.createdAt) }}
              </span>
              <span class="draft-category" v-if="draft.category">
                <Icon name="tag" size="14" class="meta-icon" />
                {{ draft.category.name }}
              </span>
            </div>
          </div>

          <div class="draft-actions">
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

    <!-- 分页器 -->
    <Pagination class="mt-24"
      v-if="!loading && drafts.length > 0"
      :current-page="currentPage"
      :total-pages="totalPages"
      @page-change="changePage"
    />
  </div>
</template>

<style scoped lang="scss">
@use "@/assets/styles/tokens" as *;

/***** 修改人：刘鑫；修改时间：2025-08-26；统一草稿页按钮颜色到“我的文章”风格 *****/
.drafts-page { max-width: 1200px; margin: 0 auto; padding: 20px; }

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

.actions-bar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 30px; gap: 20px; }

.search-box {
    position: relative;
    display: flex;
    align-items: center;
    flex: 1;
    max-width: 400px;
}





.search-icon {
    position: absolute;
    right: 12px;
    top: 50%;
    transform: translateY(-50%);
    color: var(--text-muted);
    pointer-events: none;
}

.search-input {
    flex: 1;
    padding: 8px 36px 8px 12px;
    min-width: 0;
}

.create-btn { display: flex; align-items: center; gap: 8px; padding: 12px 24px; background-color: var(--color-primary); color: white; border: none; border-radius: 25px; font-size: 14px; font-weight: 500; cursor: pointer; transition: all 0.3s; white-space: nowrap; }
.create-btn:hover { background-color: var(--color-primary-dark); transform: translateY(-2px); }

.drafts-list { display: grid; gap: 20px; }
.draft-card {  border: 1px solid var(--border-soft); border-radius: 12px; padding: 24px; display: flex; justify-content: space-between; align-items: flex-start; transition: all 0.3s ease; gap: 20px; }
.draft-card>img { width: 200px; height: 150px; }
.draft-card:hover { transform: translateY(-2px); box-shadow: 0 8px 25px rgba(0,0,0,0.1); }

.draft-content { flex: 1; margin-right: 0; }
.draft-title { font-size: 1.3rem; font-weight: 600; cursor: pointer; transition: color 0.3s; line-height: 1.4; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.draft-title:hover { color: var(--color-primary); }
.draft-summary { color: var(--text-main); opacity: 0.8; line-height: 1.6; display: -webkit-box; -webkit-box-orient: vertical; -webkit-line-clamp: 2; overflow: hidden; }

.draft-meta { display: flex; gap: 20px; flex-wrap: wrap; }
.draft-date, .draft-words, .draft-category { display: flex; align-items: center; gap: 4px; font-size: 0.85rem; color: var(--text-main); opacity: 0.7; }
.tags-cloud { display: flex; flex-wrap: wrap; gap: 8px; margin: 8px 0; }
.tag { padding: 4px 10px; background-color: var(--bg-soft); color: var(--text-subtle); border-radius: 4px; font-size: 0.8rem; cursor: pointer; transition: all 0.3s; }
.tag:hover { background-color: var(--color-primary); color: white; }

.draft-actions { display: flex; gap: 8px; flex-shrink: 0; }
.action-btn { width: 36px; height: 36px; border: none; border-radius: 8px; cursor: pointer; display: flex; align-items: center; justify-content: center; transition: all 0.3s; font-size: 14px; }
.edit-btn { background: var(--bg-warning, #fff3e0); color: var(--color-warning, #f57c00); }
.edit-btn:hover { background: var(--bg-warning, #ffe0b2); }
.publish-btn { background: var(--bg-success, #e3f9ea); color: var(--color-success, #2f855a); }
.publish-btn:hover { background: var(--bg-success, #c6f6d5); }
.delete-btn { background: var(--bg-error, #ffebee); color: var(--color-error, #d32f2f); }
.delete-btn:hover { background: var(--bg-error, #ffcdd2); }

.retry-btn { padding: 10px 20px; background-color: var(--color-primary); color: white; border: none; border-radius: 20px; cursor: pointer; margin-top: 15px; }
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

/* 响应式设计 */

@include respond(md) {
  .drafts-page { padding: 15px; }
  .actions-bar { flex-direction: column; align-items: stretch; gap: 12px; }
  
  .draft-card { 
    flex-direction: column; 
    gap: 15px; 
    padding: 16px;
  }
  
  .draft-card>img { 
    width: 100%; 
    height: 180px; /* 移动端给一个固定高度，防止图片过大或比例失调 */
    object-fit: cover;
    border-radius: 8px;
  }
  
  .draft-title { font-size: 1.1rem; }
  
  .draft-actions { 
    align-self: stretch; /* 按钮区域横向拉伸 */
    justify-content: flex-end;
    margin-top: 10px;
    border-top: 1px solid var(--border-soft);
    padding-top: 12px;
  }
  
  .draft-meta { gap: 10px; flex-direction: column; align-items: flex-start; }
}

@include respond(sm) {
  .draft-actions {
    justify-content: space-between; /* 在更小的屏幕上，按钮平分底部空间 */
  }
  .action-btn {
    flex: 1; /* 让按钮宽度均分 */
  }
}
</style>
