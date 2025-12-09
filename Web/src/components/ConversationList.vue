<script setup lang="ts">
import { onMounted, ref } from 'vue'
import ConversationService, { type Conversation } from '@/services/conversation'

const props = defineProps<{ modelValue: number | null }>()
const emit = defineEmits<{ (e:'update:modelValue', id:number):void }>()

const type = ref<string>('general')
const list = ref<Conversation[]>([])
const loading = ref(false)

const load = async () => {
  loading.value = true
  try { 
    const data = await ConversationService.list(type.value, 1, 50)
    // 反转数组，让最早的会话显示在上面，最新的显示在下面
    list.value = data.reverse()
  } finally { loading.value = false }
}

const select = (id:number) => emit('update:modelValue', id)
const create = async () => { const id = await ConversationService.create(type.value, '新会话'); await load(); emit('update:modelValue', id) }

const rename = async (id:number) => {
  const title = window.prompt('输入新的会话标题', '新会话')
  if (title && title.trim()) { await ConversationService.rename(id, title.trim()); await load() }
}

const remove = async (id:number) => {
  const ok = window.confirm('确定删除该会话及其所有消息吗？')
  if (!ok) return
  await ConversationService.remove(id)
  if (props.modelValue === id) emit('update:modelValue', null as any)
  await load()
}

onMounted(load)

const formatTime = (val?: string) => {
  if (!val) return ''
  try { return new Date(val).toLocaleString('zh-CN', { hour12: false }) } catch { return val }
}
</script>

<template>
  <div class="conv-list">
    <div class="toolbar">
      <select v-model="type" @change="load">
        <option value="general">通用</option>
        <option value="post">文章</option>
        <option value="tag">标签</option>
        <option value="category">分类</option>
        <option value="user">用户</option>
      </select>
      <button class="new-btn" @click="create">新会话</button>
    </div>
    <div class="items" v-if="!loading">
      <div v-for="c in list" :key="c.id" :class="['item', { active: props.modelValue===c.id }]" @click="select(c.id)">
        <div class="cell">
          <div class="title">{{ c.title || '未命名会话' }}</div>
          <div class="meta">{{ c.messageCount }} 条 · {{ formatTime(c.lastMessageAt) }}</div>
        </div>
        <div class="actions">
          <button class="act" @click.stop="rename(c.id)">重命名</button>
          <button class="act danger" @click.stop="remove(c.id)">删除</button>
        </div>
      </div>
      <div v-if="list.length===0" class="empty">暂无会话</div>
    </div>
    <div class="items" v-else><div class="empty">加载中...</div></div>
  </div>
</template>

<style scoped>
.conv-list{ width: 280px; background: var(--bg-card); border-right: 1px solid var(--border-soft); display:flex; flex-direction:column; border-radius:16px 0 0 16px }
.toolbar{ display:flex; gap:8px; align-items:center; padding:12px; background: var(--bg-soft); border-bottom:1px solid var(--border-soft) }
.toolbar select{ flex:1; height:34px; background: var(--bg-main); color: var(--text-main); border:1px solid var(--border-soft); border-radius:8px; padding:0 8px }
.new-btn{ height:34px; padding:0 12px; background: var(--color-primary); color:#fff; border:none; border-radius:8px; cursor:pointer; transition:.2s }
.new-btn:hover{ transform: translateY(-1px) }
.items{ flex:1; overflow:auto }
.item{ padding:12px; border-bottom:1px solid var(--border-soft); display:flex; align-items:center; justify-content:space-between; gap:8px; cursor:pointer }
.item:hover{ background: var(--bg-hover) }
.item.active{ background: var(--bg-hover) }
.cell{ display:flex; flex-direction:column; gap:4px }
.title{ color: var(--text-title); font-weight:600; max-width:180px; white-space:nowrap; overflow:hidden; text-overflow:ellipsis }
.meta{ color: var(--text-subtle); font-size:12px }
.empty{ color: var(--text-subtle); padding:12px }
.actions{ display:flex; gap:8px }
.act{ padding:6px 10px; background: var(--bg-hover); color: var(--text-main); border:1px solid var(--border-soft); border-radius:8px; cursor:pointer; transition:.2s }
.act:hover{ background: var(--bg-main) }
.act.danger{ background: var(--color-error); color:#fff; border:none }
@media (max-width: 768px){ .conv-list{ width:100%; border-right:none; border-radius:16px 16px 0 0 } }
</style>
