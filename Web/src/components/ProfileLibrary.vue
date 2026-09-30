<script setup lang="ts">
import { ref, watch, onScopeDispose } from 'vue'
import { useUserStore } from '@/stores/user'
import { usePostInteractionStore } from '@/stores/postInteraction'
import PostService, { type PostListItem } from '@/services/post'
import { getPurchasedResources, type PurchasedResource } from '@/services/resource'
import { formatRelativeTime } from '@/utils/utils'
import LoadingState from './LoadingState.vue'

const user = useUserStore()
const interaction = usePostInteractionStore()
const tab = ref<'favorites' | 'purchases'>('favorites')
const favorites = ref<PostListItem[]>([])
const purchases = ref<PurchasedResource[]>([])
const loading = ref(false)
const error = ref('')
const page = ref(1)
const pages = ref(0)
const total = ref(0)
const downloading = ref<number | null>(null)
const downloadError = ref('')
let generation = 0

async function load(next = 1) {
  const token = ++generation
  favorites.value = []
  purchases.value = []
  error.value = ''
  downloadError.value = ''
  downloading.value = null
  page.value = 1
  pages.value = 0
  total.value = 0
  loading.value = false
  if (!user.isLoggedIn) return
  loading.value = true
  try {
    const result = tab.value === 'favorites'
      ? await PostService.getFavoritePosts({ page: next, size: 5 })
      : await getPurchasedResources(next)
    if (token !== generation) return
    if (tab.value === 'favorites') favorites.value = result.records as PostListItem[]
    else purchases.value = result.records as PurchasedResource[]
    page.value = result.current
    pages.value = result.pages
    total.value = result.total
  } catch {
    if (token === generation) error.value = '内容加载失败，请重试'
  } finally {
    if (token === generation) loading.value = false
  }
}

async function download(item: PurchasedResource) {
  const token = generation
  downloading.value = item.id
  downloadError.value = ''
  try {
    await PostService.downloadResource(item.resourceId, item.resourceName)
  } catch {
    if (token === generation) downloadError.value = '下载失败，请稍后重试'
  } finally {
    if (token === generation) downloading.value = null
  }
}

watch(() => [tab.value, user.isLoggedIn, user.userInfo?.id, interaction.lastFavoriteEvent], () => load(), { immediate: true })
onScopeDispose(() => { generation++ })
</script>

<template>
  <section aria-label="我的内容">
    <div class="library-tabs" role="tablist" aria-label="内容类型">
      <button id="favorites-tab" role="tab" :aria-selected="tab === 'favorites'" aria-controls="library-panel" :class="{ active: tab === 'favorites' }" @click="tab = 'favorites'">收藏文章</button>
      <button id="purchases-tab" role="tab" :aria-selected="tab === 'purchases'" aria-controls="library-panel" :class="{ active: tab === 'purchases' }" @click="tab = 'purchases'">已购资源</button>
    </div>
    <div id="library-panel" role="tabpanel" :aria-labelledby="`${tab}-tab`">
      <LoadingState v-if="loading" compact label="正在加载内容…" />
      <p v-else-if="error" role="alert">{{ error }} <button @click="load(page)">重试</button></p>
      <template v-else>
        <template v-if="tab === 'favorites'">
          <ul v-if="favorites.length" class="library-list">
            <li v-for="item in favorites" :key="item.id">
              <router-link :to="`/post/${item.id}?from=favorites`">{{ item.title }}</router-link>
              <p v-if="item.summary" class="summary">{{ item.summary }}</p>
              <small>{{ item.category?.name || '文章' }} · {{ item.author?.username }}</small>
            </li>
          </ul>
          <p v-else class="empty-tip">把喜欢的文章收藏在这里，下次接着读。</p>
          <router-link class="library-link" to="/favorites">查看全部收藏 →</router-link>
        </template>
        <template v-else>
          <ul v-if="purchases.length" class="library-list">
            <li v-for="item in purchases" :key="item.id">
              <strong>{{ item.resourceName }}</strong>
              <small>{{ item.pointsUsed }} 积分 · {{ formatRelativeTime(item.purchasedAt) }}</small>
              <div class="resource-actions">
                <span v-if="!item.available" class="unavailable">资源已下架</span>
                <template v-else>
                  <router-link v-if="item.postId" :to="`/post/${item.postId}`">查看来源文章</router-link>
                  <button v-if="item.resourceType === 'file' || item.resourceType === 'both'" :disabled="downloading !== null" @click="download(item)">{{ downloading === item.id ? '下载中…' : '下载文件' }}</button>
                  <span v-else-if="!item.postId" class="unavailable">来源文章暂不可用</span>
                </template>
              </div>
            </li>
          </ul>
          <p v-else class="empty-tip">还没有购买资源。购买后可在这里重新查找和下载。</p>
          <p v-if="downloadError" role="alert">{{ downloadError }}</p>
        </template>
        <nav v-if="pages > 1" class="library-pagination" aria-label="内容分页">
          <button :disabled="page <= 1" @click="load(page - 1)">上一页</button>
          <span>{{ page }} / {{ pages }} · 共 {{ total }} 项</span>
          <button :disabled="page >= pages" @click="load(page + 1)">下一页</button>
        </nav>
      </template>
    </div>
  </section>
</template>

<style scoped>
.library-tabs { display: flex; gap: 8px; margin-bottom: 12px; }
button { border: 1px solid var(--border-base); border-radius: 8px; padding: 8px 12px; background: var(--bg-card); color: var(--text-main); cursor: pointer; }
button.active { color: var(--color-primary); background: var(--bg-soft); border-color: var(--color-primary); }
button:disabled { opacity: .5; cursor: default; }
.library-list { list-style: none; padding: 0; margin: 0; }
.library-list li { display: flex; flex-direction: column; gap: 8px; padding: 16px 0; border-bottom: 1px solid var(--border-light); overflow-wrap: anywhere; }
a { color: var(--color-primary); text-decoration: none; }
a:hover { text-decoration: underline; }
.summary { margin: 0; color: var(--text-subtle); font-size: 13px; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; }
small, .unavailable, .empty-tip { color: var(--text-muted); font-size: 12px; }
.empty-tip { padding: 24px 0; line-height: 1.8; }
.library-link { display: inline-block; margin-top: 16px; font-size: 13px; }
.resource-actions { display: flex; align-items: center; gap: 12px; font-size: 13px; }
.library-pagination { display: flex; align-items: center; justify-content: space-between; gap: 8px; margin-top: 18px; font-size: 12px; }
</style>
