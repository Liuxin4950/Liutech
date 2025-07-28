<template>
  <div class="profile-container">
    <div class="profile-header">
      <h1>个人资料</h1>
      <p class="subtitle">管理您的个人信息</p>
    </div>

    <div class="profile-content">
      <!-- 头像部分 -->
      <div class="avatar-section">
        <div class="avatar-wrapper">
          <img 
            :src="userInfo?.avatarUrl || '/default-avatar.png'" 
            :alt="userInfo?.username"
            class="avatar"
          />
          <div class="avatar-overlay">
            <i class="icon-camera"></i>
            <span>更换头像</span>
          </div>
        </div>
      </div>

      <!-- 表单部分 -->
      <form @submit.prevent="handleSubmit" class="profile-form">
        <div class="form-group">
          <label for="username">用户名</label>
          <input 
            id="username"
            type="text" 
            :value="userInfo?.username" 
            disabled 
            class="form-input disabled"
          />
          <small class="form-hint">用户名不可修改</small>
        </div>

        <div class="form-group">
          <label for="email">邮箱 *</label>
          <input 
            id="email"
            type="email" 
            v-model="formData.email" 
            required 
            class="form-input"
            :class="{ 'error': errors.email }"
          />
          <small v-if="errors.email" class="error-message">{{ errors.email }}</small>
        </div>

        <div class="form-group">
          <label for="nickname">昵称</label>
          <input 
            id="nickname"
            type="text" 
            v-model="formData.nickname" 
            class="form-input"
            placeholder="请输入昵称"
            maxlength="50"
          />
        </div>

        <div class="form-group">
          <label for="bio">个人简介</label>
          <textarea 
            id="bio"
            v-model="formData.bio" 
            class="form-textarea"
            placeholder="介绍一下自己吧..."
            rows="4"
            maxlength="500"
          ></textarea>
          <small class="form-hint">{{ (formData.bio || '').length }}/500</small>
        </div>

        <div class="form-group">
          <label for="avatarUrl">头像链接</label>
          <input 
            id="avatarUrl"
            type="url" 
            v-model="formData.avatarUrl" 
            class="form-input"
            placeholder="请输入头像图片链接"
          />
        </div>

        <div class="form-actions">
          <button 
            type="button" 
            @click="resetForm" 
            class="btn btn-secondary"
            :disabled="isLoading"
          >
            重置
          </button>
          <button 
            type="submit" 
            class="btn btn-primary"
            :disabled="isLoading || !isFormValid"
          >
            <span v-if="isLoading" class="loading-spinner"></span>
            {{ isLoading ? '保存中...' : '保存更改' }}
          </button>
        </div>
      </form>

      <!-- 用户统计信息 -->
      <div class="user-stats">
        <h3>账户信息</h3>
        <div class="stats-grid">
          <div class="stat-item">
            <span class="stat-label">用户积分</span>
            <span class="stat-value">{{ userInfo?.points || 0 }}</span>
          </div>
          <div class="stat-item">
            <span class="stat-label">注册时间</span>
            <span class="stat-value">{{ formatDate(userInfo?.createdAt) }}</span>
          </div>
          <div class="stat-item">
            <span class="stat-label">最近登录</span>
            <span class="stat-value">{{ formatDate(userInfo?.lastLoginAt) }}</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useUserStore } from '../stores/user'
import { UserService, type UpdateProfileRequest } from '../services/user'
import { showSuccess, showError } from '../utils/errorHandler'

const userStore = useUserStore()
const isLoading = ref(false)

// 表单数据
const formData = reactive<UpdateProfileRequest>({
  email: '',
  nickname: '',
  bio: '',
  avatarUrl: ''
})

// 表单验证错误
const errors = reactive({
  email: ''
})

// 计算属性
const userInfo = computed(() => userStore.userInfo)

const isFormValid = computed(() => {
  return formData.email && !errors.email
})

// 方法
const validateEmail = (email: string) => {
  const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
  if (!email) {
    return '邮箱不能为空'
  }
  if (!emailRegex.test(email)) {
    return '请输入有效的邮箱地址'
  }
  return ''
}

const validateForm = () => {
  errors.email = validateEmail(formData.email || '')
  return !errors.email
}

const initForm = () => {
  if (userInfo.value) {
    formData.email = userInfo.value.email || ''
    formData.nickname = userInfo.value.nickname || ''
    formData.bio = userInfo.value.bio || ''
    formData.avatarUrl = userInfo.value.avatarUrl || ''
  }
}

const resetForm = () => {
  initForm()
  errors.email = ''
}

const handleSubmit = async () => {
  if (!validateForm()) {
    return
  }

  isLoading.value = true
  try {
    const updatedUser = await UserService.updateProfile(formData)
    // 更新store中的用户信息
    userStore.updateUserInfo(updatedUser)
    showSuccess('个人资料更新成功')
  } catch (error) {
    console.error('更新个人资料失败:', error)
    showError('更新个人资料失败，请稍后重试')
  } finally {
    isLoading.value = false
  }
}

const formatDate = (dateString?: string) => {
  if (!dateString) return '暂无'
  return new Date(dateString).toLocaleDateString('zh-CN', {
    year: 'numeric',
    month: 'long',
    day: 'numeric'
  })
}

// 生命周期
onMounted(() => {
  initForm()
})
</script>

<style scoped>
.profile-container {
  max-width: 800px;
  margin: 0 auto;
  padding: 2rem;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.1);
}

.profile-header {
  text-align: center;
  margin-bottom: 2rem;
  padding-bottom: 1rem;
  border-bottom: 1px solid #eee;
}

.profile-header h1 {
  color: #2c3e50;
  margin-bottom: 0.5rem;
  font-size: 2rem;
  font-weight: 600;
}

.subtitle {
  color: #7f8c8d;
  font-size: 1rem;
  margin: 0;
}

.profile-content {
  display: grid;
  gap: 2rem;
}

/* 头像部分 */
.avatar-section {
  display: flex;
  justify-content: center;
  margin-bottom: 1rem;
}

.avatar-wrapper {
  position: relative;
  width: 120px;
  height: 120px;
  border-radius: 50%;
  overflow: hidden;
  cursor: pointer;
  transition: transform 0.3s ease;
}

.avatar-wrapper:hover {
  transform: scale(1.05);
}

.avatar {
  width: 100%;
  height: 100%;
  object-fit: cover;
  border: 4px solid #fff;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
}

.avatar-overlay {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.6);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: white;
  opacity: 0;
  transition: opacity 0.3s ease;
  font-size: 0.875rem;
}

.avatar-wrapper:hover .avatar-overlay {
  opacity: 1;
}

.icon-camera::before {
  content: '📷';
  font-size: 1.5rem;
  margin-bottom: 0.25rem;
}

/* 表单样式 */
.profile-form {
  display: grid;
  gap: 1.5rem;
}

.form-group {
  display: flex;
  flex-direction: column;
}

.form-group label {
  font-weight: 600;
  color: #2c3e50;
  margin-bottom: 0.5rem;
  font-size: 0.875rem;
}

.form-input,
.form-textarea {
  padding: 0.75rem;
  border: 2px solid #e1e8ed;
  border-radius: 8px;
  font-size: 1rem;
  transition: border-color 0.3s ease, box-shadow 0.3s ease;
  background: #fff;
}

.form-input:focus,
.form-textarea:focus {
  outline: none;
  border-color: #3498db;
  box-shadow: 0 0 0 3px rgba(52, 152, 219, 0.1);
}

.form-input.disabled {
  background: #f8f9fa;
  color: #6c757d;
  cursor: not-allowed;
}

.form-input.error {
  border-color: #e74c3c;
}

.form-textarea {
  resize: vertical;
  min-height: 100px;
  font-family: inherit;
}

.form-hint {
  color: #7f8c8d;
  font-size: 0.75rem;
  margin-top: 0.25rem;
}

.error-message {
  color: #e74c3c;
  font-size: 0.75rem;
  margin-top: 0.25rem;
}

/* 按钮样式 */
.form-actions {
  display: flex;
  gap: 1rem;
  justify-content: flex-end;
  margin-top: 1rem;
}

.btn {
  padding: 0.75rem 1.5rem;
  border: none;
  border-radius: 8px;
  font-size: 1rem;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s ease;
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.btn-primary {
  background: #3498db;
  color: white;
}

.btn-primary:hover:not(:disabled) {
  background: #2980b9;
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(52, 152, 219, 0.3);
}

.btn-secondary {
  background: #95a5a6;
  color: white;
}

.btn-secondary:hover:not(:disabled) {
  background: #7f8c8d;
}

.loading-spinner {
  width: 16px;
  height: 16px;
  border: 2px solid transparent;
  border-top: 2px solid currentColor;
  border-radius: 50%;
  animation: spin 1s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

/* 用户统计信息 */
.user-stats {
  background: #f8f9fa;
  padding: 1.5rem;
  border-radius: 8px;
  border: 1px solid #e9ecef;
}

.user-stats h3 {
  color: #2c3e50;
  margin-bottom: 1rem;
  font-size: 1.25rem;
  font-weight: 600;
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 1rem;
}

.stat-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0.75rem;
  background: white;
  border-radius: 6px;
  border: 1px solid #e9ecef;
}

.stat-label {
  color: #7f8c8d;
  font-size: 0.875rem;
}

.stat-value {
  color: #2c3e50;
  font-weight: 600;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .profile-container {
    padding: 1rem;
    margin: 1rem;
  }

  .profile-header h1 {
    font-size: 1.5rem;
  }

  .form-actions {
    flex-direction: column;
  }

  .btn {
    width: 100%;
    justify-content: center;
  }

  .stats-grid {
    grid-template-columns: 1fr;
  }
}
</style>