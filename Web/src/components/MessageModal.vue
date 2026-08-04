<template>
  <div v-if="visible" class="modal-overlay" @click="handleOverlayClick">
    <div class="modal-content" @click.stop>
      <div class="modal-header">
        <h3>给我留言</h3>
        <button class="close-btn" @click="close">
          <svg width="24" height="24" viewBox="0 0 24 24" fill="none">
            <path d="M18 6L6 18M6 6l12 12" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
          </svg>
        </button>
      </div>
      <div class="modal-body">
        <form @submit.prevent="handleSubmit">
          <div class="form-group">
            <label for="nickname">昵称 *</label>
            <input
              id="nickname"
              v-model="formData.nickname"
              type="text"
              placeholder="请输入您的昵称"
              maxlength="100"
              required
            />
          </div>
          <div class="form-group">
            <label for="email">邮箱 *</label>
            <input
              id="email"
              v-model="formData.email"
              type="email"
              placeholder="请输入您的邮箱"
              required
            />
          </div>
          <div class="form-group">
            <label for="content">留言内容 *</label>
            <textarea
              id="content"
              v-model="formData.content"
              placeholder="说点什么吧..."
              rows="5"
              maxlength="1000"
              required
            ></textarea>
            <div class="char-count">{{ formData.content.length }}/1000</div>
          </div>
          <div class="form-actions">
            <button type="button" class="btn btn-secondary" @click="close">取消</button>
            <button type="submit" class="btn-primary" :disabled="submitting">
              {{ submitting ? '提交中...' : '提交留言' }}
            </button>
          </div>
        </form>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { MessageService } from '@/services/message'
import { showSuccess, showError } from '@/utils/errorHandler'

interface Props {
  visible: boolean
}

interface Emits {
  (e: 'update:visible', value: boolean): void
  (e: 'success'): void
}

defineProps<Props>()
const emit = defineEmits<Emits>()

const submitting = ref(false)
const formData = reactive({
  nickname: '',
  email: '',
  content: ''
})

const close = () => {
  emit('update:visible', false)
  resetForm()
}

const handleOverlayClick = () => {
  close()
}

const resetForm = () => {
  formData.nickname = ''
  formData.email = ''
  formData.content = ''
}

const handleSubmit = async () => {
  if (submitting.value) return

  submitting.value = true
  try {
    await MessageService.createMessage({
      nickname: formData.nickname,
      email: formData.email,
      content: formData.content
    })
    showSuccess('留言提交成功！等待管理员审核后显示。')
    emit('success')
    close()
  } catch (error: any) {
    showError(error.message || '留言提交失败，请稍后重试')
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: var(--overlay-bg);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}

.modal-content {
  background: var(--bg-soft);
  border-radius: 12px;
  box-shadow: var(--shadow-modal);
  max-width: 500px;
  width: 90%;
  max-height: 90vh;
  overflow: hidden;
}

.modal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20px 24px 16px;
  border-bottom: 1px solid var(--border-soft);
}

.modal-header h3 {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  color: var(--text-main);
}

.close-btn {
  background: none;
  border: none;
  padding: 4px;
  cursor: pointer;
  color: var(--text-subtle);
  border-radius: 4px;
  transition: all 0.2s;
}

.close-btn:hover {
  background: var(--bg-card);
}

.modal-body {
  padding: 24px;
}

.form-group {
  margin-bottom: 20px;
  position: relative;
}

.form-group label {
  display: block;
  margin-bottom: 8px;
  font-size: 14px;
  font-weight: 500;
  color: var(--text-main);
}

.form-group input,
.form-group textarea {
  width: 100%;
  padding: 10px 12px;
  border: 1px solid var(--border-soft);
  border-radius: 6px;
  font-size: 14px;
  background: var(--bg-card);
  color: var(--text-main);
  transition: border-color 0.2s;
  box-sizing: border-box;
}

.form-group input:focus,
.form-group textarea:focus {
  outline: none;
  border-color: var(--color-primary);
}

.form-group textarea {
  resize: vertical;
  min-height: 100px;
  font-family: inherit;
}

.char-count {
  position: absolute;
  bottom: 8px;
  right: 12px;
  font-size: 12px;
  color: var(--text-subtle);
}

.form-actions {
  display: flex;
  gap: 12px;
  justify-content: flex-end;
  margin-top: 24px;
}

.btn {
  padding: 10px 20px;
  border-radius: 6px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
  border: 1px solid transparent;
}

.btn-secondary {
  background: var(--bg-card);
  color: var(--text-subtle);
  border-color: var(--border-soft);
}

.btn-secondary:hover {
  background: var(--bg-hover);
}

.btn-primary {
  background: var(--color-primary);
  color: white;
}

.btn-primary:hover:not(:disabled) {
  opacity: 0.9;
}

.btn-primary:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
</style>
