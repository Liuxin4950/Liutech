<script setup lang="ts">
import { ref, watch, onScopeDispose } from 'vue'
import { useUserStore } from '@/stores/user'
import { getAchievements, claimAchievement, type Achievement } from '@/services/userActivity'

const emit = defineEmits<{ claimed: [points: number] }>()
const user = useUserStore()
const items = ref<Achievement[]>([])
const loading = ref(false)
const error = ref('')
const claiming = ref('')
let generation = 0
const refresh = async () => {
  const token = ++generation
  items.value = []
  if (!user.isLoggedIn) return
  loading.value = true
  error.value = ''
  try { const result = await getAchievements(); if (token === generation) items.value = result }
  catch { if (token === generation) error.value = '成就加载失败，请重试' }
  finally { if (token === generation) loading.value = false }
}
const claim = async (item: Achievement) => {
  if (claiming.value || item.status !== 'claimable') return
  const token = generation
  claiming.value = item.code
  error.value = ''
  try {
    const result = await claimAchievement(item.code)
    if (token !== generation) return
    items.value = items.value.map(value => value.code === item.code ? result.achievement : value)
    emit('claimed', result.points)
  } catch { if (token === generation) error.value = '领取未确认，请重试；重复请求不会重复发放积分' }
  finally { claiming.value = '' }
}
watch(() => [user.isLoggedIn, user.userInfo?.id], refresh, { immediate: true })
onScopeDispose(() => { generation++ })
defineExpose({ refresh })
</script>

<template>
  <section class="achievement-tasks" aria-label="读者成就奖励">
    <p class="task-note">评论 10 条或浏览 10 篇不同公开文章，每项可领取一次 2 积分。</p>
    <p v-if="loading" role="status">正在加载成就...</p>
    <p v-if="error" role="alert">{{ error }} <button :disabled="loading || !!claiming" @click="refresh">刷新进度</button></p>
    <div v-for="item in items" :key="item.code" class="task">
      <div class="task-info">
        <strong>{{ item.title }}</strong><span>{{ item.progress }} / {{ item.target }} · +{{ item.rewardPoints }} 积分</span>
        <progress :value="item.progress" :max="item.target" :aria-label="`${item.title}进度`" />
      </div>
      <button :disabled="!!claiming || item.status !== 'claimable'" @click="claim(item)">{{ claiming === item.code ? '领取中...' : item.status === 'claimed' ? '已领取' : item.status === 'claimable' ? '领取奖励' : '进行中' }}</button>
    </div>
    <p class="task-note">浏览按现有历史去重统计；清空历史会影响未领取进度，已领取奖励保留。浏览不代表读完。</p>
  </section>
</template>

<style scoped>
.achievement-tasks { margin-bottom: 20px; }
.task-note { font-size: 12px; color: var(--text-muted); line-height: 1.7; }
.task { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 12px 0; border-bottom: 1px solid var(--border-light); }
.task-info { display: flex; flex-direction: column; gap: 6px; min-width: 0; flex: 1; }
.task-info strong { font-size: 13px; color: var(--text-main); }
.task-info span { font-size: 12px; color: var(--text-subtle); }
progress { width: 100%; height: 6px; accent-color: var(--color-primary); }
button { flex-shrink: 0; padding: 8px 10px; border: 1px solid var(--border-base); background: var(--bg-card); color: var(--color-primary); border-radius: 8px; cursor: pointer; }
button:disabled { color: var(--text-muted); cursor: default; }
</style>
