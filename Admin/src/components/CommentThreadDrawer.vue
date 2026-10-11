<script setup lang="ts">
import { computed, onBeforeUnmount, ref, shallowRef, watch } from 'vue'
import { DownOutlined, RightOutlined, ReloadOutlined } from '@ant-design/icons-vue'
import LtStatusTag from './LtStatusTag.vue'
import CommentsService, { type Comment } from '@/services/comments'
import { commentAuthorName, flattenCommentRelations, indexCommentRelations, loadCompleteCommentThread } from '@/utils/commentThread'
import { formatDateTime } from '@/utils/utils'

const props = defineProps<{ open: boolean; commentId?: number }>()
const emit = defineEmits<{ 'update:open': [value: boolean] }>()
const loading = ref(false)
const error = ref('')
const comments = shallowRef<Comment[]>([])
const postTitle = ref('')
const postId = ref<number>()
const loadedCount = ref(0)
const selectedId = ref<number>()
const expanded = shallowRef(new Set<number>())
const page = ref(1)
const pageSize = 30
let requestVersion = 0
let request: AbortController | undefined

const relations = computed(() => indexCommentRelations(comments.value))
const rows = computed(() => flattenCommentRelations(relations.value, expanded.value))
const pageRows = computed(() => rows.value.slice((page.value - 1) * pageSize, page.value * pageSize))
const selectedComment = computed(() => selectedId.value ? relations.value.byId.get(selectedId.value) : undefined)
const parentComment = computed(() => selectedComment.value?.parentId ? relations.value.byId.get(selectedComment.value.parentId) : undefined)
const deletedCount = computed(() => comments.value.filter(comment => comment.deletedAt).length)
const path = computed(() => {
  const result: Comment[] = []
  let id = selectedId.value
  while (id !== undefined) {
    const comment = relations.value.byId.get(id)
    if (!comment) break
    result.push(comment)
    id = relations.value.parents.get(id)
  }
  return result.reverse()
})
const displayPath = computed(() => path.value.length > 10
  ? [path.value[0]!, ...path.value.slice(-8)] : path.value)

function locateComment(id: number) {
  selectedId.value = id
  const next = new Set(expanded.value)
  let parent = relations.value.parents.get(id)
  while (parent !== undefined) {
    next.add(parent)
    parent = relations.value.parents.get(parent)
  }
  expanded.value = next
  const index = rows.value.findIndex(row => row.comment.id === id)
  page.value = Math.max(1, Math.floor(index / pageSize) + 1)
}

function toggleComment(id: number) {
  const next = new Set(expanded.value)
  if (next.has(id)) next.delete(id)
  else next.add(id)
  expanded.value = next
  page.value = Math.min(page.value, Math.max(1, Math.ceil(rows.value.length / pageSize)))
}

async function loadThread() {
  request?.abort()
  const version = ++requestVersion
  if (!props.open || !props.commentId) return
  const id = props.commentId
  const controller = new AbortController()
  request = controller
  loading.value = true
  error.value = ''
  comments.value = []
  postTitle.value = ''
  postId.value = undefined
  loadedCount.value = 0
  try {
    const thread = await loadCompleteCommentThread(async (page, snapshotMaxId) => {
      const response = await CommentsService.getCommentThreadPage(id, page, controller.signal, snapshotMaxId)
      if (response.code !== 200) throw new Error(response.message || '加载评论关系失败')
      if (version !== requestVersion) throw new Error('请求已取消')
      loadedCount.value += response.data.comments.records.length
      return response.data
    })
    if (version !== requestVersion) return
    comments.value = thread.comments
    postTitle.value = thread.postTitle || '文章已不可用'
    postId.value = thread.postId
    expanded.value = new Set(relations.value.byId.keys())
    locateComment(id)
  } catch (cause) {
    if (version !== requestVersion || controller.signal.aborted) return
    error.value = cause instanceof Error ? cause.message : '加载评论关系失败'
  } finally {
    if (version === requestVersion) loading.value = false
  }
}

watch(() => [props.open, props.commentId], loadThread, { immediate: true })
onBeforeUnmount(() => { requestVersion++; request?.abort() })
</script>

<template>
  <a-drawer :open="open" title="评论详情与关系" width="min(1120px, 100vw)" @close="emit('update:open', false)">
    <a-spin :spinning="loading">
      <div v-if="loading" class="thread-loading">正在加载完整评论关系，已读取 {{ loadedCount }} 条…</div>
      <a-alert v-if="error" type="error" :message="error" show-icon>
        <template #description><a-button size="small" @click="loadThread">重新加载</a-button></template>
      </a-alert>
      <template v-else-if="comments.length">
        <div class="thread-heading">
          <div>
            <h3>{{ postTitle }}</h3>
            <p>文章 #{{ postId }} · 全部 {{ comments.length }} 条评论 · {{ deletedCount }} 条已删除</p>
          </div>
          <a-button size="small" @click="loadThread"><template #icon><ReloadOutlined /></template>刷新</a-button>
        </div>
        <p class="thread-hint">包含同文章的全部根评论、回复和软删除节点。点击节点查看完整内容。</p>
        <a-alert v-if="relations.issues.size" type="warning" show-icon class="thread-warning"
          :message="`${relations.issues.size} 条评论存在异常关系，已保留并标注`" />
        <div class="thread-layout">
          <section class="thread-outline" aria-label="评论关系树">
            <div class="thread-tools">
              <span>评论关系</span>
              <a-space size="small">
                <a-button type="link" size="small" @click="expanded = new Set(relations.byId.keys()); page = 1">展开全部</a-button>
                <a-button type="link" size="small" @click="expanded = new Set(); page = 1">收起全部</a-button>
                <a-button type="link" size="small" @click="commentId && locateComment(commentId)">定位当前</a-button>
              </a-space>
            </div>
            <ol class="thread-rows">
              <li v-for="row in pageRows" :key="row.comment.id" class="thread-row"
                :class="{ 'is-selected': selectedId === row.comment.id, 'is-origin': commentId === row.comment.id }"
                :style="{ paddingInlineStart: `${10 + Math.min(row.depth, 6) * 14}px` }">
                <button v-if="row.childCount" class="thread-toggle" :aria-label="`${expanded.has(row.comment.id!) ? '收起' : '展开'}评论 #${row.comment.id} 的回复`"
                  :aria-expanded="expanded.has(row.comment.id!)" @click="toggleComment(row.comment.id!)">
                  <DownOutlined v-if="expanded.has(row.comment.id!)" /><RightOutlined v-else />
                </button>
                <span v-else class="thread-toggle-placeholder" />
                <button class="thread-node" :aria-pressed="selectedId === row.comment.id" @click="locateComment(row.comment.id!)">
                  <span class="thread-node-meta">
                    <strong>{{ commentAuthorName(row.comment) }}</strong>
                    <span>#{{ row.comment.id }}</span>
                    <a-tag v-if="row.comment.authorType === 'BOT' || row.comment.botId">AI</a-tag>
                    <a-tag v-if="row.comment.deletedAt">已删除</a-tag>
                    <a-tag v-if="commentId === row.comment.id" color="blue">当前评论</a-tag>
                  </span>
                  <span class="thread-preview">{{ row.comment.content || '（空评论）' }}</span>
                  <span class="thread-node-note">
                    {{ row.depth ? `第 ${row.depth + 1} 层 · ` : '根评论 · ' }}{{ formatDateTime(row.comment.createdAt) }}
                    <template v-if="row.childCount"> · {{ row.childCount }} 条直接回复</template>
                  </span>
                  <span v-if="row.issue" class="thread-issue">{{ row.issue }}</span>
                </button>
              </li>
            </ol>
            <a-pagination v-if="rows.length > pageSize" v-model:current="page" size="small" :page-size="pageSize"
              :total="rows.length" :show-size-changer="false" :show-total="(total: number) => `展开 ${total} 个节点`" class="thread-pagination" />
          </section>
          <section v-if="selectedComment" class="thread-detail" aria-label="评论完整内容">
            <div class="thread-detail-heading">
              <h3>评论 #{{ selectedComment.id }}</h3>
              <LtStatusTag :status="selectedComment.deletedAt ? 'deleted' : 'normal'" />
            </div>
            <p class="thread-detail-author">{{ commentAuthorName(selectedComment) }}<a-tag v-if="selectedComment.authorType === 'BOT' || selectedComment.botId">AI 角色</a-tag></p>
            <p class="thread-detail-date">{{ formatDateTime(selectedComment.createdAt) }}</p>
            <p v-if="selectedComment.deletedAt" class="thread-detail-date">删除时间：{{ formatDateTime(selectedComment.deletedAt) }}</p>
            <div class="thread-content">{{ selectedComment.content || '（空评论）' }}</div>
            <div class="thread-parent">
              <h4>回复关系</h4>
              <template v-if="parentComment">
                <a-button type="link" class="thread-parent-link" @click="locateComment(parentComment.id!)">
                  回复 {{ commentAuthorName(parentComment) }} 的评论 #{{ parentComment.id }}
                </a-button>
                <a-tag v-if="parentComment.deletedAt">父评论已删除</a-tag>
                <p class="thread-parent-content">{{ parentComment.content }}</p>
              </template>
              <p v-else-if="selectedComment.parentId">父评论 #{{ selectedComment.parentId }} 不在本文章关系中。</p>
              <p v-else>这是根评论。</p>
              <p v-if="selectedComment.id && relations.issues.get(selectedComment.id)" class="thread-issue">{{ relations.issues.get(selectedComment.id) }}</p>
            </div>
            <div v-if="path.length > 1" class="thread-path">
              <span>关系路径：</span>
              <template v-for="(ancestor, index) in displayPath" :key="ancestor.id">
                <span v-if="path.length > 10 && index === 1"> / …（{{ path.length - displayPath.length }} 层）</span>
                <span v-if="index"> / </span>
                <a-button type="link" size="small" @click="locateComment(ancestor.id!)">#{{ ancestor.id }}</a-button>
              </template>
            </div>
          </section>
        </div>
      </template>
    </a-spin>
  </a-drawer>
</template>

<style scoped>
.thread-heading, .thread-detail-heading, .thread-tools { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.thread-heading h3, .thread-detail-heading h3 { margin: 0; color: var(--lt-color-text); }
.thread-heading p, .thread-hint, .thread-detail-date { margin: 6px 0 0; color: var(--lt-color-text-secondary); font-size: 12px; }
.thread-hint { margin-bottom: 16px; }
.thread-warning { margin-bottom: 16px; }
.thread-loading { padding: 36px 0; text-align: center; color: var(--lt-color-text-secondary); }
.thread-layout { display: grid; grid-template-columns: minmax(0, 1fr) minmax(280px, 0.85fr); gap: 20px; align-items: start; }
.thread-outline { min-width: 0; border: 1px solid var(--lt-color-border); border-radius: var(--lt-radius-md, 8px); overflow: hidden; }
.thread-tools { flex-wrap: wrap; padding: 10px 12px; border-bottom: 1px solid var(--lt-color-border); font-weight: 600; }
.thread-rows { list-style: none; padding: 0; margin: 0; max-height: 64vh; overflow: auto; }
.thread-row { display: flex; gap: 4px; padding-block: 10px; padding-inline-end: 12px; border-bottom: 1px solid var(--lt-color-border); border-inline-start: 3px solid transparent; }
.thread-row:last-child { border-bottom: 0; }
.thread-row.is-origin { border-inline-start-color: var(--lt-color-primary); }
.thread-row.is-selected { background: var(--lt-color-selected-bg); }
.thread-node { display: flex; flex-direction: column; gap: 5px; flex: 1; min-width: 0; padding: 0; border: 0; background: transparent; text-align: start; cursor: pointer; color: var(--lt-color-text); font: inherit; }
.thread-node-meta { display: flex; flex-wrap: wrap; align-items: center; gap: 6px; font-size: 12px; }
.thread-node-meta :deep(.ant-tag) { margin: 0; font-size: 11px; }
.thread-node-meta > span, .thread-node-note { color: var(--lt-color-text-secondary); }
.thread-preview { display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; overflow-wrap: anywhere; font-size: 13px; }
.thread-node-note, .thread-issue { font-size: 11px; }
.thread-issue { color: var(--lt-color-warning-text, var(--lt-color-text-secondary)); }
.thread-toggle, .thread-toggle-placeholder { width: 20px; flex-shrink: 0; }
.thread-toggle { height: 24px; padding: 0; border: 0; background: transparent; color: var(--lt-color-text-secondary); cursor: pointer; }
.thread-node:focus-visible, .thread-toggle:focus-visible { outline: 2px solid var(--lt-color-primary); outline-offset: 2px; }
.thread-pagination { padding: 12px; border-top: 1px solid var(--lt-color-border); }
.thread-detail { min-width: 0; padding: 16px; border: 1px solid var(--lt-color-border); border-radius: var(--lt-radius-md, 8px); }
.thread-detail-author { display: flex; flex-wrap: wrap; gap: 8px; margin: 14px 0 0; font-weight: 600; }
.thread-content { margin-top: 20px; white-space: pre-wrap; overflow-wrap: anywhere; line-height: 1.8; max-height: 44vh; overflow: auto; }
.thread-parent { margin-top: 20px; padding-top: 16px; border-top: 1px solid var(--lt-color-border); }
.thread-parent h4 { margin: 0 0 8px; }
.thread-parent-link { padding: 0; height: auto; white-space: normal; text-align: start; }
.thread-parent-content { margin: 8px 0 0; white-space: pre-wrap; overflow-wrap: anywhere; line-height: 1.6; max-height: 18vh; overflow: auto; color: var(--lt-color-text-secondary); }
.thread-path { margin-top: 16px; overflow-wrap: anywhere; color: var(--lt-color-text-secondary); font-size: 12px; max-height: 80px; overflow: auto; }
@media (max-width: 760px) { .thread-layout { grid-template-columns: 1fr; } .thread-rows { max-height: 42vh; } }
</style>
