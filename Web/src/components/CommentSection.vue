<template>
  <div class="comment-section">
    <!-- 评论标题 -->
    <div class="comment-header">
      <h3 class="comment-title">
        <Icon name="message" size="18" /> 评论 <span class="comment-count">({{ totalComments }})</span>
      </h3>
    </div>

    <!-- 发表评论表单 -->
    <div class="comment-form-container">
      <CommentForm 
        :key="postId"
        :post-id="postId"
        @comment-created="handleCommentCreated"
      />
    </div>

    <!-- 评论列表 -->
    <div class="comment-list">
      <LoadingState v-if="loading" compact label="正在加载评论…" />
      <div v-else-if="error" class="error text-sm">
        <p>{{ error }}</p>
        <button @click="loadComments" class="tag-retry-btn">重试</button>
      </div>
      <div v-else-if="comments.length === 0" class="empty flex flex-col flex-ac text-sm">
        <p>暂无评论，快来发表第一条评论吧！</p>
        <img src="@/assets/image/扑到.png" alt="" class="fit-err">
      </div>
      <div v-else>
        <CommentItem 
          v-for="comment in comments" 
          :key="comment.id"
          :comment="comment"
          :post-id="postId"
          @reply-created="handleReplyCreated"
        />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount, computed, watch } from 'vue'
import { CommentService, type Comment } from '@/services/comment'
import { mergeCommentTree, addCommentToTree } from '@/services/commentTree'
import CommentForm from './CommentForm.vue'
import CommentItem from './CommentItem.vue'
import Icon from './Icon.vue'
import LoadingState from './LoadingState.vue'

const props = defineProps<{ postId: number }>()
const comments = ref<Comment[]>([])
const loading = ref(false)
const error = ref('')
const countComments = (items: Comment[]): number => items.reduce((total, item) => total + 1 + countComments(item.children || []), 0)
const totalComments = computed(() => countComments(comments.value))
let timer: ReturnType<typeof setInterval> | undefined
let controller: AbortController | undefined
let generation = 0
let localRevision = 0
const localAdds = new Map<number, number>()
let disposed = false

async function loadComments() {
  if (controller || disposed || document.hidden) return
  const articleId = props.postId
  const version = generation
  const revisionAtStart = localRevision
  const request = new AbortController()
  controller = request
  if (!comments.value.length) loading.value = true
  try {
    const data = await CommentService.getTreeComments(articleId, request.signal)
    if (disposed || version !== generation || request.signal.aborted) return
    const protect = new Set([...localAdds].filter(([, revision]) => revision > revisionAtStart).map(([id]) => id))
    mergeCommentTree(comments.value, data, protect)
    for (const [id, revision] of localAdds) if (revision <= revisionAtStart) localAdds.delete(id)
    error.value = ''
  } catch {
    if (!request.signal.aborted && version === generation && !comments.value.length) error.value = '加载评论失败，请稍后重试'
  } finally {
    if (controller === request) controller = undefined
    if (version === generation) loading.value = false
  }
}
function handleCommentCreated(comment: Comment) {
  if (comment.postId !== props.postId) return
  localAdds.set(comment.id, ++localRevision)
  addCommentToTree(comments.value, comment)
}
const handleReplyCreated = handleCommentCreated
function stopPolling() {
  if (timer) clearInterval(timer)
  timer = undefined
  controller?.abort()
  controller = undefined
  loading.value = false
}
function startPolling() {
  if (disposed || document.hidden) return
  void loadComments()
  if (!timer) timer = setInterval(() => { void loadComments() }, 10000)
}
function onVisibilityChange() { if (document.hidden) stopPolling(); else startPolling() }
watch(() => props.postId, () => {
  generation++
  stopPolling()
  comments.value = []
  localAdds.clear()
  error.value = ''
  startPolling()
})
onMounted(() => {
  document.addEventListener('visibilitychange', onVisibilityChange)
  startPolling()
})
onBeforeUnmount(() => {
  disposed = true
  generation++
  stopPolling()
  document.removeEventListener('visibilitychange', onVisibilityChange)
})
</script>

<style scoped>
@use "@/assets/styles/tokens" as *;
.comment-section {
  margin-top: 40px;
  padding: 0;
}

.comment-header {
  margin-bottom: 24px;
  padding-bottom: 12px;
  border-bottom: 2px solid var(--border-base);
}

.comment-title {
  font-size: 1.5rem;
  font-weight: 600;
  color: var(--color-primary);
  margin: 0;
  display: flex;
  align-items: center;
  gap: 8px;
}

.comment-count {
  font-size: 1rem;
  color: var(--text-main);
  opacity: 0.7;
  font-weight: 400;
}


.comment-list {
  min-height: 200px;
}

.loading, .error, .empty {
  text-align: center;
  padding: 40px 20px;
  color: var(--text-main);
}

.loading, .error {
  opacity: 0.7;
}

.loading p, .empty p {
  font-size: 1rem;
  margin: 0;
}

.error p {
  color: var(--color-error);
  margin-bottom: 16px;
}

.tag-retry-btn {
  padding: 8px 16px;
  background: var(--bg-tag);
  color: white;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  transition: background-color 0.3s;
}

.tag-retry-btn:hover {
  background: var(--bg-tag-hover);
}

.comment-form-container {
    margin-bottom: 32px;
}

/* 响应式设计 */
@include respond(md) {
  .comment-section {
    margin-top: 24px;
  }

  .comment-title {
    font-size: 1.3rem;
  }

  .comment-form-container {
    margin-bottom: 24px;
  }
}

</style>
