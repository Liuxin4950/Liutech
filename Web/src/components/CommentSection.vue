<template>
  <div class="comment-section">
    <!-- 评论标题 -->
    <div class="comment-header">
      <h3 class="comment-title">
        💬 评论 <span class="comment-count">({{ totalComments }})</span>
      </h3>
    </div>

    <!-- 发表评论表单 -->
    <div class="comment-form-container">
      <CommentForm 
        :post-id="postId"
        @comment-created="handleCommentCreated"
      />
    </div>

    <!-- 评论列表 -->
    <div class="comment-list">
      <div v-if="loading" class="loading">
        <p>加载评论中...</p>
      </div>
      <div v-else-if="error" class="error">
        <p>{{ error }}</p>
        <button @click="loadComments" class="retry-btn">重试</button>
      </div>
      <div v-else-if="comments.length === 0" class="empty">
        <p>暂无评论，快来发表第一条评论吧！</p>
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
import { ref, onMounted, computed } from 'vue'
import { CommentService, type Comment } from '@/services/comment'
import { useErrorHandler } from '@/composables/useErrorHandler'
import CommentForm from './CommentForm.vue'
import CommentItem from './CommentItem.vue'

// Props
interface Props {
  postId: number
}

const props = defineProps<Props>()

// Composables
const { handleAsync } = useErrorHandler()

// 响应式数据
const comments = ref<Comment[]>([])
const loading = ref(false)
const error = ref('')

// 计算总评论数（包括子评论）
const totalComments = computed(() => {
  const countComments = (commentList: Comment[]): number => {
    return commentList.reduce((total, comment) => {
      return total + 1 + countComments(comment.children || [])
    }, 0)
  }
  return countComments(comments.value)
})

// 加载评论列表
const loadComments = async () => {
  await handleAsync(async () => {
    loading.value = true
    error.value = ''
    
    const data = await CommentService.getTreeComments(props.postId)
    comments.value = data
  }, {
    onError: (err) => {
      error.value = '加载评论失败，请稍后重试'
      console.error('加载评论失败:', err)
    },
    onFinally: () => {
      loading.value = false
    }
  })
}

// 处理新评论创建
const handleCommentCreated = (newComment: Comment) => {
  // 如果是顶级评论，直接添加到列表开头
  if (!newComment.parentId) {
    comments.value.unshift(newComment)
  } else {
    // 如果是回复评论，需要找到父评论并添加到其children中
    addReplyToParent(comments.value, newComment)
  }
}

// 处理回复创建
const handleReplyCreated = (newReply: Comment) => {
  addReplyToParent(comments.value, newReply)
}

// 递归查找父评论并添加回复
const addReplyToParent = (commentList: Comment[], reply: Comment) => {
  for (const comment of commentList) {
    if (comment.id === reply.parentId) {
      if (!comment.children) {
        comment.children = []
      }
      comment.children.push(reply)
      return true
    }
    if (comment.children && addReplyToParent(comment.children, reply)) {
      return true
    }
  }
  return false
}

// 组件挂载时加载评论
onMounted(() => {
  loadComments()
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
  border-bottom: 2px solid var(--text-primary);
}

.comment-title {
  font-size: 1.5rem;
  font-weight: 600;
  color: var(--text-primary);
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
  opacity: 0.7;
}

.loading p, .empty p {
  font-size: 1rem;
  margin: 0;
}

.error p {
  color: #e74c3c;
  margin-bottom: 16px;
}

.retry-btn {
  padding: 8px 16px;
  background: var(--bg-tag);
  color: white;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  transition: background-color 0.3s;
}

.retry-btn:hover {
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
}

.comment-title {
  font-size: 1.3rem;
}

.comment-form-container {
  margin-bottom: 24px;
}

</style>