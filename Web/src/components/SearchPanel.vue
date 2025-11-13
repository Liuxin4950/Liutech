<script setup lang="ts">
import { ref, watch, onMounted } from 'vue'
import PostService, { type PostListItem, type PageResponse } from '@/services/post'

const props = defineProps<{ visible: boolean, query?: string }>()
const emit = defineEmits<{ (e: 'close'): void }>()

const inputQuery = ref('')
const results = ref<PostListItem[]>([])
const loading = ref(false)
const page = ref(1)
const size = ref(10)
const total = ref(0)

const fetch = async () => {
  if (!props.visible) return
  loading.value = true
  try {
    const resp: PageResponse<PostListItem> = await PostService.getPostList({ page: page.value, size: size.value, keyword: inputQuery.value })
    results.value = resp.records || []
    total.value = resp.total || 0
  } finally {
    loading.value = false
  }
}

const doSearch = async () => { page.value = 1; await fetch() }
const close = () => emit('close')

watch(() => props.visible, v => { if (v) { inputQuery.value = props.query || ''; fetch() } })
watch(() => props.query, q => { if (props.visible) { inputQuery.value = q || ''; fetch() } })
onMounted(() => { if (props.visible) { inputQuery.value = props.query || '' ; fetch() } })
</script>

<template>
  <div v-if="visible" class="search-overlay">
    <div class="search-panel">
      <div class="search-header">
        <input class="search-input" v-model="inputQuery" placeholder="输入关键词搜索文章" />
        <button class="search-btn" :disabled="loading" @click="doSearch">搜索</button>
        <button class="close-btn" @click="close">关闭</button>
      </div>
      <div class="search-body">
        <div v-if="loading" class="loading">正在搜索...</div>
        <div v-else>
          <div v-if="results.length === 0" class="empty">暂无搜索结果</div>
          <ul v-else class="result-list">
            <li v-for="item in results" :key="item.id" class="result-item">
              <div class="title">{{ item.title }}</div>
              <div class="summary">{{ item.summary || '' }}</div>
            </li>
          </ul>
        </div>
      </div>
      <div class="search-footer">
        <span class="count">共 {{ total }} 条</span>
      </div>
    </div>
  </div>
  </template>

<style scoped>
.search-overlay { position: fixed; inset: 0; background: rgba(0,0,0,0.3); display: flex; align-items: center; justify-content: center; z-index: 1000 }
.search-panel { width: 720px; max-width: 96vw; background: var(--bg-card); border: 1px solid var(--border-soft); box-shadow: var(--shadow-lg); border-radius: 12px; display: flex; flex-direction: column }
.search-header { display: flex; gap: 8px; align-items: center; padding: 12px; background: var(--bg-soft); border-bottom: 1px solid var(--border-soft) }
.search-input { flex: 1; padding: 8px 12px; background: var(--bg-main); color: var(--text-main); border: 1px solid var(--border-soft); border-radius: 6px }
.search-btn { padding: 8px 12px; background: var(--color-primary); color: #fff; border: none; border-radius: 6px; cursor: pointer }
.close-btn { padding: 8px 12px; background: var(--bg-hover); color: var(--text-main); border: 1px solid var(--border-soft); border-radius: 6px; cursor: pointer }
.search-body { padding: 12px; background: var(--bg-main); color: var(--text-main); max-height: 60vh; overflow: auto }
.loading { color: var(--text-subtle) }
.empty { color: var(--text-subtle); background: var(--bg-card); border: 1px dashed var(--border-soft); padding: 12px; border-radius: 8px }
.result-list { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 12px }
.result-item { padding: 12px; border: 1px solid var(--border-soft); border-radius: 8px; background: var(--bg-card) }
.title { font-weight: 600; color: var(--text-title) }
.summary { font-size: 14px; color: var(--text-subtle); margin-top: 6px }
.search-footer { padding: 10px 12px; border-top: 1px solid var(--border-soft); background: var(--bg-soft); color: var(--text-subtle) }
@media (max-width: 480px) { .search-panel { width: 96vw } }
</style>
