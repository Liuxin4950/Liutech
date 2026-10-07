<template>
  <article class="comment-item" :data-comment-id="comment.id">
    <div class="comment-main">
      <img
        :src="commentAuthorAvatar(comment) || errImg"
        :alt="commentAuthorName(comment)"
        class="avatar-img"
        @error="handleImageError"
      />
      <div class="comment-content">
        <div class="comment-header">
          <span class="username">{{ commentAuthorName(comment) }}</span>
          <span v-if="comment.authorType === 'BOT'" class="ai-badge">AI</span>
        </div>
        <p class="comment-text">{{ comment.content }}</p>
        <div class="comment-actions">
          <time class="comment-time" :datetime="comment.createdAt">{{ formatRelativeTime(comment.createdAt) }}</time>
          <button
            type="button"
            class="action-btn reply-btn"
            :class="{ active: isReplyingTo(comment.id) }"
            :aria-pressed="isReplyingTo(comment.id)"
            @click="selectReply(comment)"
          >
            <Icon name="message" size="14" />
            {{ isReplyingTo(comment.id) ? '取消回复' : '回复' }}
          </button>
        </div>

        <section v-if="thread.replies.length" class="replies-panel" aria-label="评论回复">
          <div :id="repliesId" class="children-list">
            <div
              v-for="reply in visibleReplies"
              :key="reply.id"
              class="flat-reply"
              :data-comment-id="reply.id"
            >
              <img
                :src="commentAuthorAvatar(reply) || errImg"
                :alt="commentAuthorName(reply)"
                class="reply-avatar"
                @error="handleImageError"
              />
              <div class="reply-content">
                <p class="reply-text">
                  <span class="reply-author username">{{ commentAuthorName(reply) }}</span>
                  <span v-if="reply.authorType === 'BOT'" class="ai-badge">AI</span>
                  <span v-if="replyToName(reply)" class="reply-target"> 回复 <span class="mention">@{{ replyToName(reply) }}</span>：</span>
                  <span v-else class="author-separator">：</span>
                  <span class="reply-body">{{ reply.content }}</span>
                </p>
                <div class="comment-actions">
                  <time class="comment-time" :datetime="reply.createdAt">{{ formatRelativeTime(reply.createdAt) }}</time>
                  <button
                    type="button"
                    class="action-btn reply-btn"
                    :class="{ active: isReplyingTo(reply.id) }"
                    :aria-pressed="isReplyingTo(reply.id)"
                    @click="selectReply(reply)"
                  >{{ isReplyingTo(reply.id) ? '取消回复' : '回复' }}</button>
                </div>
              </div>
            </div>
          </div>
          <div class="reply-toolbar">
            <template v-if="replyVisibility === 'collapsed'">
              <button type="button" class="toggle-children-btn" :aria-controls="repliesId" :aria-expanded="false" @click="replyVisibility = 'all'">
                展开全部 {{ thread.replies.length }} 条回复
              </button>
            </template>
            <template v-else>
              <span class="reply-summary">共 {{ thread.replies.length }} 条回复</span>
              <button v-if="remainingReplies > 0" type="button" class="toggle-children-btn" :aria-controls="repliesId" :aria-expanded="false" @click="replyVisibility = 'all'">
                展开其余 {{ remainingReplies }} 条
              </button>
              <button type="button" class="toggle-children-btn collapse-replies" :aria-controls="repliesId" :aria-expanded="true" @click="replyVisibility = 'collapsed'">
                收起回复
              </button>
            </template>
          </div>
        </section>

        <!-- 收起回复或取消输入时保留同一个表单，避免轮询和折叠清空草稿。 -->
        <div v-if="replyTarget" v-show="showReplyForm" ref="replyFormContainer" class="reply-form-container">
          <CommentForm
            :post-id="postId"
            :parent-id="replyTarget.id"
            :reply-to-name="commentAuthorName(replyTarget)"
            @comment-created="handleReplyCreated"
            @cancel="showReplyForm = false"
          />
        </div>
      </div>
    </div>
  </article>
</template>

<script setup lang="ts">
import { ref, computed, nextTick, watch } from 'vue'
import { commentAuthorName, commentAuthorAvatar, flattenCommentReplies } from '@/services/commentTree'
import type { Comment } from '@/services/comment'
import { formatRelativeTime } from '@/utils/utils'
import { handleImageError, errImg } from '@/composables/useImageFallback'
import CommentForm from './CommentForm.vue'
import Icon from './Icon.vue'

const props = defineProps<{ comment: Comment; postId: number }>()
const emit = defineEmits<{ replyCreated: [comment: Comment] }>()

const replyVisibility = ref<'preview' | 'all' | 'collapsed'>('preview')
const replyTarget = ref<Comment | null>(null)
const showReplyForm = ref(false)
const replyFormContainer = ref<HTMLElement | null>(null)
const thread = computed(() => flattenCommentReplies(props.comment))
const repliesId = computed(() => `comment-replies-${props.postId}-${props.comment.id}`)
const visibleReplies = computed(() => {
  if (replyVisibility.value === 'collapsed') return []
  return replyVisibility.value === 'preview' ? thread.value.replies.slice(0, 3) : thread.value.replies
})
const remainingReplies = computed(() => thread.value.replies.length - visibleReplies.value.length)

function replyToName(reply: Comment): string | undefined {
  if (!reply.parentId || reply.parentId === props.comment.id) return undefined
  const parent = thread.value.byId.get(reply.parentId)
  return parent ? commentAuthorName(parent) : undefined
}
function isReplyingTo(id: number): boolean {
  return showReplyForm.value && replyTarget.value?.id === id
}
async function selectReply(comment: Comment) {
  if (replyTarget.value?.id === comment.id) {
    showReplyForm.value = !showReplyForm.value
  } else {
    replyTarget.value = comment
    showReplyForm.value = true
  }
  if (showReplyForm.value) {
    await nextTick()
    replyFormContainer.value?.querySelector('textarea')?.focus()
  }
}
function handleReplyCreated(newReply: Comment) {
  if (newReply.postId !== props.postId) return
  replyVisibility.value = 'all'
  showReplyForm.value = false
  replyTarget.value = null
  emit('replyCreated', newReply)
}
watch([() => props.postId, () => props.comment.id], () => {
  replyVisibility.value = 'preview'
  showReplyForm.value = false
  replyTarget.value = null
})
</script>

<style scoped lang="scss">
@use "@/assets/styles/tokens" as *;

.comment-item {
  padding: 24px 0;
  border-bottom: 1px solid var(--border-soft);
}
.comment-item:last-child { border-bottom: 0; }
.comment-main { display: flex; gap: 12px; }
.avatar-img, .reply-avatar { flex-shrink: 0; border-radius: 50%; object-fit: cover; background: var(--bg-soft); }
.avatar-img { width: 40px; height: 40px; }
.comment-content, .reply-content { flex: 1; min-width: 0; }
.comment-header { display: flex; align-items: center; flex-wrap: wrap; gap: 8px; margin-bottom: 8px; }
.username { font-size: 0.9rem; font-weight: 600; color: var(--text-main); overflow-wrap: anywhere; }
.ai-badge { display: inline-block; margin: 0 4px; padding: 1px 5px; border: 1px solid var(--border-soft); border-radius: 4px; color: var(--color-primary); font-size: 0.65rem; line-height: 1.5; vertical-align: middle; }
.comment-text, .reply-text { margin: 0; color: var(--text-main); line-height: 1.75; overflow-wrap: anywhere; }
.comment-text, .reply-body { white-space: pre-wrap; }
.comment-text { margin-bottom: 8px; }
.comment-actions { display: flex; align-items: center; flex-wrap: wrap; gap: 16px; min-height: 28px; }
.comment-time { font-size: 0.75rem; color: var(--text-subtle); }
.action-btn, .toggle-children-btn { display: inline-flex; align-items: center; gap: 4px; padding: 4px 0; border: 0; border-radius: 4px; background: transparent; cursor: pointer; color: var(--text-subtle); font: inherit; font-size: 0.8rem; }
.action-btn:hover, .action-btn.active, .toggle-children-btn:hover { color: var(--color-primary); }
.action-btn:focus-visible, .toggle-children-btn:focus-visible { outline: 2px solid var(--color-primary); outline-offset: 4px; }
.replies-panel { margin-top: 12px; }
.flat-reply { display: flex; gap: 10px; padding: 10px 0; }
.reply-avatar { width: 28px; height: 28px; margin-top: 2px; }
.reply-text { font-size: 0.88rem; }
.reply-author { font-size: inherit; }
.reply-target, .author-separator { color: var(--text-subtle); }
.mention { color: var(--color-primary); }
.reply-content .comment-actions { gap: 14px; margin-top: 3px; }
.reply-toolbar { display: flex; align-items: center; flex-wrap: wrap; gap: 14px; padding-top: 4px; }
.reply-summary { color: var(--text-subtle); font-size: 0.75rem; }
.toggle-children-btn { color: var(--color-primary); }
.collapse-replies { color: var(--text-subtle); }
.reply-form-container { margin-top: 14px; }
.reply-form-container :deep(.comment-form) { margin: 0; padding: 14px 0 0; border: 0; border-top: 1px solid var(--border-soft); border-radius: 0; background: transparent; }
.reply-form-container :deep(.form-header) { margin-bottom: 10px; }
.reply-form-container :deep(.form-title) { font-size: 0.85rem; }
.reply-form-container :deep(.comment-textarea) { min-height: 84px; font-size: 0.88rem; }
.reply-form-container :deep(.cancel-btn) { padding: 4px 8px; background: transparent; color: var(--text-subtle); opacity: 1; }

@include respond(md) {
  .comment-item { padding: 20px 0; }
  .comment-main { gap: 10px; }
  .avatar-img { width: 34px; height: 34px; }
  .flat-reply { gap: 8px; }
  .reply-avatar { width: 24px; height: 24px; }
  .reply-toolbar { gap: 10px; }
}
</style>
