<template>
  <Teleport to="body">
    <Transition name="search-modal">
      <div v-if="visible" class="search-overlay" @click.self="close">
        <div class="search-modal">
          <div class="search-header">
            <div class="search-box modal-search-box">
              <input
                ref="inputRef"
                v-model="keyword"
                type="text"
                placeholder="搜索文章标题、内容或摘要..."
                class="search-input"
                @input="handleInput"
                @keyup.enter="handleSearch"
                @keyup.esc="close"
              />
              <Icon name="search" size="16" class="search-icon" />
            </div>
          </div>

          <div ref="searchBodyRef" class="search-body" data-lenis-prevent>
            <div v-if="isSearching" class="search-status">搜索中...</div>
            <div v-else-if="searchError" class="search-status error">{{ searchError }}</div>
            <div v-else-if="keyword.trim() && !isSearching && results.length === 0" class="search-status">
              没有找到相关文章
            </div>

            <div v-else-if="!keyword.trim()" class="empty-state">
              <img src="@/assets/image/扑到.png" alt="" class="empty-img" />
              <p class="empty-text">输入关键词搜索文章</p>
            </div>

            <div v-else class="results-list">
              <article
                v-for="post in results"
                :key="post.id"
                class="result-item list-item"
                @click="goToPost(post.id)"
              >
                <h4 class="result-title">{{ post.title }}</h4>
                <p class="result-summary">{{ post.summary || '暂无摘要' }}</p>
                <div class="result-meta">
                  <span class="result-category">{{ post.category?.name }}</span>
                  <span class="result-date">{{ formatDate(post.createdAt) }}</span>
                </div>
              </article>
            </div>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup lang="ts">
import { ref, nextTick, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import Icon from './Icon.vue'
import { PostService, type PostListItem } from '@/services/post'
import { formatDate } from '@/utils/utils'
import { useNestedLenis } from '@/composables/useLenis'

const router = useRouter()

const searchBodyRef = ref<HTMLElement | null>(null)
useNestedLenis(searchBodyRef)

const visible = ref(false)
const keyword = ref('')
const isSearching = ref(false)
const searchError = ref('')
const results = ref<PostListItem[]>([])
const inputRef = ref<HTMLInputElement | null>(null)

let searchTimeout: number | null = null

const open = () => {
  visible.value = true
  nextTick(() => inputRef.value?.focus())
}

const close = () => {
  visible.value = false
  keyword.value = ''
  results.value = []
  searchError.value = ''
  if (searchTimeout) {
    clearTimeout(searchTimeout)
    searchTimeout = null
  }
}

const handleInput = () => {
  if (searchTimeout) clearTimeout(searchTimeout)
  if (!keyword.value.trim()) {
    results.value = []
    return
  }
  searchTimeout = setTimeout(handleSearch, 500)
}

const handleSearch = async () => {
  const kw = keyword.value.trim()
  if (!kw) return

  try {
    isSearching.value = true
    searchError.value = ''
    const resp = await PostService.getPostList({ keyword: kw, page: 1, size: 10 })
    results.value = resp.records
  } catch {
    searchError.value = '搜索失败，请稍后重试'
  } finally {
    isSearching.value = false
  }
}

const goToPost = (id: number) => {
  router.push(`/post/${id}`)
  close()
}

onUnmounted(() => {
  if (searchTimeout) {
    clearTimeout(searchTimeout)
    searchTimeout = null
  }
})

defineExpose({ open, close })
</script>

<style scoped>
.search-overlay {
  position: fixed;
  inset: 0;
  z-index: 9999;
  background: rgba(0, 0, 0, 0.5);
  backdrop-filter: blur(4px);
  display: flex;
  justify-content: center;
  padding-top: 15vh;
}

.search-modal {
  width: min(90vw, 560px);
  max-height: 70vh;
  background: var(--bg-card, #fff);
  border-radius: 12px;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.3);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.search-header {
  display: flex;
  align-items: center;
  padding: 12px 16px;
  border-bottom: 1px solid var(--border-soft);
}

/* 复用全局 .search-input（styles.scss 唯一实现），弹窗内放开 max-width 占满宽度，放大一号 */
.modal-search-box {
  flex: 1;
  max-width: none;

  .search-input {
    padding: 10px 40px 10px 14px;
    font-size: 0.9rem;
  }
}

.search-body {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}

.search-status {
  text-align: center;
  padding: 32px 16px;
  color: var(--text-subtle);
  font-size: 0.875rem;
}

.search-status.error {
  color: var(--color-error, #e74c3c);
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 40px 16px;
  gap: 12px;
}

.empty-img {
  width: 120px;
  height: 120px;
  object-fit: contain;
  opacity: 0.7;
  border-radius: 12px;
}

.empty-text {
  font-size: 0.875rem;
  color: var(--text-muted);
  margin: 0;
}

.results-list {
  display: flex;
  flex-direction: column;
  padding: 4px;
}

/* 分隔线条目：样式走全局 .list-item（10px 12px / 8px 圆角 / 底部线 / hover bg-hover），这里只留内容布局 */
.result-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.result-title {
  font-size: 0.9rem;
  font-weight: 600;
  color: var(--text-title);
  margin: 0;
  line-height: 1.5;
}

.result-summary {
  font-size: 0.75rem;
  color: var(--text-subtle);
  margin: 0;
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.result-meta {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 0.75rem;
  color: var(--text-muted);
}

/* 分类角标：主题色药丸（与 ArticleList.article-category / 全局分类角标统一） */
.result-category {
  display: inline-flex;
  align-items: center;
  padding: 2px 12px;
  border-radius: 30px;
  font-weight: 600;
  font-size: 0.7rem;
  color: var(--color-primary);
  background: rgba(var(--color-primary-rgb), 0.1);
  border: 1px solid rgba(var(--color-primary-rgb), 0.16);
}

/* 过渡动画 */
.search-modal-enter-active,
.search-modal-leave-active {
  transition: opacity 0.2s;
}
.search-modal-enter-active .search-modal,
.search-modal-leave-active .search-modal {
  transition: transform 0.2s, opacity 0.2s;
}
.search-modal-enter-from,
.search-modal-leave-to {
  opacity: 0;
}
.search-modal-enter-from .search-modal {
  transform: translateY(-20px) scale(0.96);
}
</style>
