<template>
  <div class="comment-item" :class="{ 'is-reply': isReply }">
    <!-- 评论主体 -->
    <div class="comment-main">
      <!-- 用户头像 -->
      <div class="comment-avatar">
        <img 
          :src="comment.user?.avatarUrl || '/default-avatar.svg'"
          :alt="comment.user?.username || '匿名用户'"
          class="avatar-img"
          @error="handleAvatarError"
        />
      </div>
      
      <!-- 评论内容区 -->
      <div class="comment-content">
        <!-- 用户信息和时间 -->
        <div class="comment-header">
          <span class="username">{{ comment.user?.username || '匿名用户' }}</span>
          <span class="comment-time">{{ formatRelativeTime(comment.createdAt) }}</span>
        </div>
        
        <!-- 评论文本 -->
        <div class="comment-text">
          {{ comment.content }}
        </div>
        
        <!-- 操作按钮 -->
        <div class="comment-actions">
          <button 
            @click="toggleReplyForm"
            class="action-btn reply-btn"
            :class="{ 'active': showReplyForm }"
          >
            <span class="icon">💬</span>
            {{ showReplyForm ? '取消回复' : '回复' }}
          </button>
        </div>
        
        <!-- 回复表单 -->
        <div v-if="showReplyForm" class="reply-form-container">
          <CommentForm 
            :post-id="postId"
            :parent-id="comment.id"
            @comment-created="handleReplyCreated"
            @cancel="showReplyForm = false"
          />
        </div>
      </div>
    </div>
    
    <!-- 子评论 -->
    <div v-if="hasChildren" class="comment-children">
      <!-- 折叠/展开按钮 -->
      <button 
        @click="toggleChildren"
        class="toggle-children-btn"
        :class="{ 'expanded': showChildren }"
      >
        <span class="toggle-icon">{{ showChildren ? '▼' : '▶' }}</span>
        <span class="toggle-text">
          {{ showChildren ? '收起' : '展开' }} {{ comment.children?.length || 0 }} 条回复
        </span>
      </button>
      
      <!-- 子评论列表 -->
      <div v-if="showChildren" class="children-list">
        <CommentItem
          v-for="child in comment.children"
          :key="child.id"
          :comment="child"
          :post-id="postId"
          :is-reply="true"
          :depth="depth + 1"
          @reply-created="$emit('replyCreated', $event)"
        />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import type { Comment } from '@/services/comment'
import { formatRelativeTime } from '@/utils/uitls'
import CommentForm from './CommentForm.vue'

// Props
interface Props {
  comment: Comment
  postId: number
  isReply?: boolean
  depth?: number  // 层级深度：0=顶级评论，1=第一层回复，2=第二层...
}

const props = withDefaults(defineProps<Props>(), {
  isReply: false,
  depth: 0
})

// Emits
interface Emits {
  replyCreated: [comment: Comment]
}

const emit = defineEmits<Emits>()

// 响应式数据
const showReplyForm = ref(false)
const showChildren = ref(props.depth < 1) // depth=0(顶级评论)时默认展开子评论，depth>=1时默认隐藏
const isLiked = ref(false)
const likeCount = ref(0)

// 计算属性
const hasChildren = computed(() => {
  return props.comment.children && props.comment.children.length > 0
})

// 方法
const toggleReplyForm = () => {
  showReplyForm.value = !showReplyForm.value
}

const toggleChildren = () => {
  showChildren.value = !showChildren.value
}

const handleReplyCreated = (newReply: Comment) => {
  // 关闭回复表单
  showReplyForm.value = false
  
  // 确保子评论展开
  showChildren.value = true
  
  // 向上传递事件
  emit('replyCreated', newReply)
}

const handleAvatarError = (event: Event) => {
  const img = event.target as HTMLImageElement
  img.src = '/default-avatar.svg'
}


</script>

<style scoped>
@use "@/assets/styles/tokens" as *;
.comment-item {
  margin-bottom: 16px;
}

.comment-item.is-reply {
  margin-left: 20px;
  padding-left: 20px;
  border-left: 2px solid var(--border-base);
}

.comment-main {
  display: flex;
  gap: 12px;
}

.comment-avatar {
  flex-shrink: 0;
}

.avatar-img {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  object-fit: cover;
  border: 2px solid var(--border-base);
}

.comment-item.is-reply .avatar-img {
  width: 32px;
  height: 32px;
}

.comment-content {
  flex: 1;
  min-width: 0;
}

.comment-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 8px;
}

.username {
  font-weight: 600;
  color: var(--text-main);
  font-size: 0.95rem;
}

.comment-time {
  font-size: 0.8rem;
  color: var(--text-subtle);
}

.comment-text {
  color: var(--text-main);
  line-height: 1.6;
  margin-bottom: 12px;
  word-wrap: break-word;
  white-space: pre-wrap;
}

.comment-actions {
  display: flex;
  gap: 16px;
  margin-bottom: 12px;
}

.action-btn {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 6px 12px;
  background: none;
  border: 1px solid var(--border-base);
  border-radius: 16px;
  cursor: pointer;
  font-size: 0.85rem;
  color: var(--text-subtle);
  transition: all 0.3s;
}

.action-btn:hover {
  background: var(--bg-hover);
  border-color: var(--border-base);
  color: var(--text-main);
}

.action-btn.active {
  background: var(--color-primary);
  color: white;
  border-color: var(--color-primary);
}

.action-btn.liked {
  color: var(--color-error);
  border-color: var(--color-error);
}

.action-btn.liked:hover {
  background: var(--bg-error);
}

.icon {
  font-size: 0.9rem;
}

.count {
  font-size: 0.8rem;
  min-width: 16px;
  text-align: center;
}

.reply-form-container {
  margin-top: 12px;
}

.comment-children {
  margin-top: 16px;
}

.toggle-children-btn {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  background: var(--bg-hover);
  border: 1px solid var(--border-base);
  border-radius: 6px;
  cursor: pointer;
  font-size: 0.85rem;
  color: var(--text-subtle);
  transition: all 0.3s;
  margin-bottom: 12px;
  margin-left: 52px;
}

.comment-item.is-reply .toggle-children-btn {
  margin-left: 44px;
}

.toggle-children-btn:hover {
  background: var(--bg-hover);
  border-color: var(--color-primary);
  color: var(--text-main);
}

.toggle-icon {
  font-size: 0.7rem;
  transition: transform 0.3s;
}

.toggle-children-btn.expanded .toggle-icon {
  transform: rotate(0deg);
}

.children-list {
  margin-left: 52px;
}

.comment-item.is-reply .children-list {
  margin-left: 44px;
}

/* 响应式设计 */
@include respond(md) {
  .comment-item.is-reply {
    margin-left: 12px;
    padding-left: 12px;
  }

  .comment-main {
    gap: 8px;
  }

  .avatar-img {
    width: 36px;
    height: 36px;
  }

  .comment-item.is-reply .avatar-img {
    width: 28px;
    height: 28px;
  }

  .comment-header {
    flex-direction: column;
    align-items: flex-start;
    gap: 4px;
  }

  .comment-actions {
    gap: 12px;
  }

  .action-btn {
    padding: 4px 8px;
    font-size: 0.8rem;
  }

  .toggle-children-btn {
    margin-left: 44px;
    padding: 6px 10px;
  }

  .comment-item.is-reply .toggle-children-btn {
    margin-left: 40px;
  }

  .children-list {
    margin-left: 44px;
  }

  .comment-item.is-reply .children-list {
    margin-left: 40px;
  }
}
</style>