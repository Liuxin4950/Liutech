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
                @keyup.enter="handleEnter"
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
              <div v-if="history.length" class="history-section">
                <div class="history-header">
                  <span class="history-title">搜索历史</span>
                  <button type="button" class="history-clear" @click="clearHistory">清空</button>
                </div>
                <div class="history-chips">
                  <button
                    v-for="word in history"
                    :key="word"
                    type="button"
                    class="history-chip"
                    @click="searchHistory(word)"
                  >
                    {{ word }}
                  </button>
                </div>
              </div>
              <template v-else>
                <!-- <img src="@/assets/image/扑到.png" alt="" class="empty-img" /> -->
                <p class="empty-text">输入关键词搜索文章</p>
              </template>
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

// 搜索历史：localStorage 持久化，最多 8 条，最新在前
const HISTORY_KEY = 'liutech-search-history'
const MAX_HISTORY = 8
const history = ref<string[]>([])

const loadHistory = () => {
  try {
    const raw = localStorage.getItem(HISTORY_KEY)
    history.value = raw ? JSON.parse(raw) : []
  } catch {
    history.value = []
  }
}

const saveHistory = () => {
  try {
    localStorage.setItem(HISTORY_KEY, JSON.stringify(history.value))
  } catch { /* ignore */ }
}

/** 记录搜索词：去重并移到最前 */
const recordKeyword = (word: string) => {
  const kw = word.trim()
  if (!kw) return
  history.value = [kw, ...history.value.filter(w => w !== kw)].slice(0, MAX_HISTORY)
  saveHistory()
}

const clearHistory = () => {
  history.value = []
  saveHistory()
}

/** 点击历史词：填入并立即搜索 */
const searchHistory = (word: string) => {
  keyword.value = word
  recordKeyword(word)
  handleSearch()
}

let searchTimeout: number | null = null

const open = () => {
  visible.value = true
  loadHistory()
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

/** 回车搜索：记录历史；防抖自动搜索（handleInput）不记录，避免存下输入中途的半截词 */
const handleEnter = () => {
  const kw = keyword.value.trim()
  if (!kw) return
  recordKeyword(kw)
  handleSearch()
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
  padding: 0px 8px;
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

/* 搜索历史 */
.history-section {
  width: 100%;
  padding: 4px;
}

.history-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}

.history-title {
  font-size: 0.8rem;
  font-weight: 600;
  color: var(--text-subtle);
}

.history-clear {
  padding: 2px 8px;
  background: transparent;
  border: none;
  color: var(--text-muted);
  font-size: 0.75rem;
  cursor: pointer;
  transition: color 0.2s ease;
}

.history-clear:hover {
  color: var(--color-error);
}

.history-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.history-chip {
  padding: 5px 12px;
  background: var(--bg-soft);
  border: 1px solid var(--border-light);
  border-radius: 999px;
  color: var(--text-main);
  font-size: 0.8rem;
  cursor: pointer;
  transition: all 0.2s ease;
}

.history-chip:hover {
  border-color: var(--color-primary);
  color: var(--color-primary);
  background: var(--bg-hover);
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
