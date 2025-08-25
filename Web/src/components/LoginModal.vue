<template>
  <div v-if="visible" class="modal-overlay" @click="handleOverlayClick">
    <div class="modal-content" @click.stop>
      <div class="modal-header">
        <h3>需要登录</h3>
        <button class="close-btn" @click="close">
          <svg width="24" height="24" viewBox="0 0 24 24" fill="none">
            <path d="M18 6L6 18M6 6l12 12" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
          </svg>
        </button>
      </div>
      <div class="modal-body">
        <p>{{ message || '此功能需要登录后才能使用，请先登录您的账户。' }}</p>
      </div>
      <div class="modal-footer">
        <button class="btn btn-secondary" @click="close">取消</button>
        <button class="btn btn-primary" @click="goToLogin">去登录</button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'

interface Props {
  visible: boolean
  message?: string
}

interface Emits {
  (e: 'update:visible', value: boolean): void
  (e: 'close'): void
}

defineProps<Props>()
const emit = defineEmits<Emits>()

const router = useRouter()

const close = () => {
  emit('update:visible', false)
  emit('close')
}

const handleOverlayClick = () => {
  close()
}

const goToLogin = () => {
  close()
  router.push({ name: 'login', query: { redirect: router.currentRoute.value.fullPath } })
}
</script>

<style scoped>
.modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}

.modal-content {
  background: white;
  border-radius: 8px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.15);
  max-width: 400px;
  width: 90%;
  max-height: 90vh;
  overflow: hidden;
}

.modal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20px 24px 16px;
  border-bottom: 1px solid #e5e7eb;
}

.modal-header h3 {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  color: #1f2937;
}

.close-btn {
  background: none;
  border: none;
  padding: 4px;
  cursor: pointer;
  color: #6b7280;
  border-radius: 4px;
  transition: all 0.2s;
}

.close-btn:hover {
  background: #f3f4f6;
  color: #374151;
}

.modal-body {
  padding: 20px 24px;
}

.modal-body p {
  margin: 0;
  color: #4b5563;
  line-height: 1.6;
}

.modal-footer {
  display: flex;
  gap: 12px;
  justify-content: flex-end;
  padding: 16px 24px 20px;
  border-top: 1px solid #e5e7eb;
}

.btn {
  padding: 8px 16px;
  border-radius: 6px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
  border: 1px solid transparent;
}

.btn-secondary {
  background: #f9fafb;
  color: #374151;
  border-color: #d1d5db;
}

.btn-secondary:hover {
  background: #f3f4f6;
  border-color: #9ca3af;
}

.btn-primary {
  background: #3b82f6;
  color: white;
}

.btn-primary:hover {
  background: #2563eb;
}
</style>