<script setup lang="ts">
import { ref, watch, onScopeDispose } from 'vue'
import { useUserStore } from '@/stores/user'
import { getActivities, type UserActivity } from '@/services/userActivity'
import { formatRelativeTime } from '@/utils/utils'

const user = useUserStore()
const items = ref<UserActivity[]>([])
const page = ref(1)
const pages = ref(0)
const loading = ref(false)
const error = ref('')
const labels: Record<UserActivity['type'], string> = { comment: '评论了', favorite: '收藏了', view: '最近浏览', checkin: '', achievement: '', register: '', post: '发布了' }
let generation = 0
const refresh = async (next = 1) => {
  const token = ++generation
  items.value = []
  if (!user.isLoggedIn) return
  loading.value = true
  error.value = ''
  try {
    const result = await getActivities(next)
    if (token !== generation) return
    items.value = result.records
    page.value = result.current
    pages.value = result.pages
  } catch { if (token === generation) error.value = '动态加载失败，请重试' }
  finally { if (token === generation) loading.value = false }
}
watch(() => [user.isLoggedIn, user.userInfo?.id], () => refresh(), { immediate: true })
onScopeDispose(() => { generation++ })
defineExpose({ refresh })
</script>

<template>
  <div aria-label="最近动态">
    <p v-if="loading" role="status">正在加载动态...</p>
    <p v-else-if="error" role="alert">{{ error }} <button @click="refresh(page)">重试</button></p>
    <p v-else-if="!items.length" class="empty-tip">暂无活动记录</p>
    <ol v-else class="activities">
      <li v-for="item in items" :key="item.id">
        <div>
          <span>{{ labels[item.type] }} </span>
          <router-link v-if="item.targetType === 'post' && item.targetId" :to="`/post/${item.targetId}`">{{ item.title }}</router-link>
          <span v-else>{{ item.title }}</span>
        </div>
        <time :datetime="item.occurredAt" :title="new Date(item.occurredAt).toLocaleString()">{{ formatRelativeTime(item.occurredAt) }}</time>
      </li>
    </ol>
    <nav v-if="pages > 1" class="activity-pagination" aria-label="动态分页">
      <button :disabled="loading || page <= 1" @click="refresh(page - 1)">上一页</button>
      <span>{{ page }} / {{ pages }}</span>
      <button :disabled="loading || page >= pages" @click="refresh(page + 1)">下一页</button>
    </nav>
  </div>
</template>

<style scoped>
.activities { list-style: none; margin: 0; padding: 0; }
li { display: flex; flex-direction: column; gap: 5px; padding: 12px 0; border-bottom: 1px solid var(--border-light); font-size: 13px; color: var(--text-main); overflow-wrap: anywhere; }
li a { color: var(--color-primary); text-decoration: none; }
time { color: var(--text-muted); font-size: 11px; }
.activity-pagination { display: flex; align-items: center; justify-content: space-between; gap: 8px; margin-top: 16px; font-size: 12px; }
button { color: var(--text-main); background: var(--bg-card); border: 1px solid var(--border-base); border-radius: 8px; padding: 7px 10px; cursor: pointer; }
button:disabled { opacity: .5; cursor: default; }
</style>
