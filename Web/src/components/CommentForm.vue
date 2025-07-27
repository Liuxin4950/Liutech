<template>
  <div class="comment-form">
    <div class="form-header">
      <h4 v-if="!parentId" class="form-title">发表评论</h4>
      <h4 v-else class="form-title">回复评论</h4>
      <button 
        v-if="parentId" 
        @click="$emit('cancel')"
        class="cancel-btn"
        type="button"
      >
        取消
      </button>
    </div>
    
    <form @submit.prevent="submitComment" class="form-content">
      <div class="textarea-container">
        <textarea
          v-model="content"
          :placeholder="parentId ? '写下你的回复...' : '写下你的评论...'"
          class="comment-textarea"
          rows="4"
          maxlength="1000"
          :disabled="submitting"
        ></textarea>
        <div class="char-count">
          {{ content.length }}/1000
        </div>
      </div>
      
      <div class="form-actions">
        <div class="login-tip" v-if="!isLoggedIn">
          <span>请先 <router-link to="/login" class="login-link">登录</router-link> 后发表评论</span>
        </div>
        <button 
          type="submit" 
          class="submit-btn"
          :disabled="!canSubmit"
          :class="{ 'submitting': submitting }"
        >
          <span v-if="submitting">发布中...</span>
          <span v-else>{{ parentId ? '回复' : '发表评论' }}</span>
        </button>
      </div>
    </form>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { CommentService, type Comment, type CreateCommentRequest } from '@/services/comment'
import { useErrorHandler } from '@/composables/useErrorHandler'

// Props
interface Props {
  postId: number
  parentId?: number
}

const props = defineProps<Props>()

// Emits
interface Emits {
  commentCreated: [comment: Comment]
  cancel: []
}

const emit = defineEmits<Emits>()

// Composables
const { handleAsync } = useErrorHandler()

// 响应式数据
const content = ref('')
const submitting = ref(false)

// 计算属性
const isLoggedIn = computed(() => {
  return !!localStorage.getItem('token')
})

const canSubmit = computed(() => {
  return isLoggedIn.value && 
         content.value.trim().length > 0 && 
         content.value.length <= 1000 && 
         !submitting.value
})

// 提交评论
const submitComment = async () => {
  if (!canSubmit.value) return

  await handleAsync(async () => {
    submitting.value = true
    
    const commentData: CreateCommentRequest = {
      postId: props.postId,
      content: content.value.trim(),
      parentId: props.parentId
    }
    
    const newComment = await CommentService.createComment(commentData)
    
    // 清空表单
    content.value = ''
    
    // 发出事件
    emit('commentCreated', newComment)
    
    // 如果是回复，发出取消事件关闭回复框
    if (props.parentId) {
      emit('cancel')
    }
  }, {
    onError: (err) => {
      console.error('发表评论失败:', err)
      // 错误处理已在 useErrorHandler 中统一处理
    },
    onFinally: () => {
      submitting.value = false
    }
  })
}
</script>

<style scoped>
.comment-form {
  background: var(--hover-color);
  border: 1px solid var(--border-color);
  border-radius: 8px;
  padding: 20px;
  margin-bottom: 16px;
}

.form-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.form-title {
  font-size: 1.1rem;
  font-weight: 600;
  color: var(--text-color);
  margin: 0;
}

.cancel-btn {
  padding: 6px 12px;
  background: var(--text-color);
  opacity: 0.6;
  color: white;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  font-size: 0.875rem;
  transition: all 0.3s;
}

.cancel-btn:hover {
  opacity: 0.8;
}

.form-content {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.textarea-container {
  position: relative;
}

.comment-textarea {
  width: 100%;
  min-height: 100px;
  padding: 12px;
  border: 1px solid var(--border-color);
  border-radius: 6px;
  font-size: 0.95rem;
  line-height: 1.5;
  resize: vertical;
  transition: border-color 0.3s;
  box-sizing: border-box;
  background: var(--bg-color);
  color: var(--text-color);
}

.comment-textarea:focus {
  outline: none;
  border-color: var(--primary-color);
  box-shadow: 0 0 0 2px rgba(52, 152, 219, 0.1);
}

.comment-textarea:disabled {
  background-color: var(--hover-color);
  cursor: not-allowed;
}

.char-count {
  position: absolute;
  bottom: 8px;
  right: 12px;
  font-size: 0.75rem;
  color: var(--text-color);
  opacity: 0.6;
  background: var(--bg-color);
  padding: 2px 4px;
  border-radius: 3px;
}

.form-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.login-tip {
  font-size: 0.875rem;
  color: var(--text-color);
  opacity: 0.7;
}

.login-link {
  color: var(--primary-color);
  text-decoration: none;
  font-weight: 500;
}

.login-link:hover {
  text-decoration: underline;
}

.submit-btn {
  padding: 10px 20px;
  background: var(--primary-color);
  color: white;
  border: none;
  border-radius: 6px;
  cursor: pointer;
  font-size: 0.95rem;
  font-weight: 500;
  transition: all 0.3s;
  min-width: 100px;
}

.submit-btn:hover:not(:disabled) {
  background: var(--secondary-color);
  transform: translateY(-1px);
}

.submit-btn:disabled {
  background: var(--border-color);
  cursor: not-allowed;
  transform: none;
}

.submit-btn.submitting {
  background: var(--text-color);
  opacity: 0.6;
  cursor: wait;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .comment-form {
    padding: 16px;
  }
  
  .form-header {
    flex-direction: column;
    align-items: flex-start;
    gap: 8px;
  }
  
  .cancel-btn {
    align-self: flex-end;
  }
  
  .form-actions {
    flex-direction: column;
    align-items: stretch;
    gap: 12px;
  }
  
  .login-tip {
    text-align: center;
  }
  
  .submit-btn {
    width: 100%;
  }
}
</style>