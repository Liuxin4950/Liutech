<template>
  <div class="profile-page">
    <!-- 个人资料头部横幅 -->
    <div class="profile-banner">
      <div class="banner-bg">
        <div class="banner-overlay"></div>
      </div>
      <div class="profile-header">
        <div class="content">
          <div class="profile-main flex flex-as">
            <div class="avatar-section">
              <div class="avatar-container">
                <img :src="userInfo?.avatarUrl || '/default-avatar.svg'" :alt="userInfo?.username"
                  class="user-avatar" />
                <div class="avatar-edit" @click="showEditForm = true">
                  <i class="edit-icon">✏️</i>
                </div>
                <div class="online-status"></div>
              </div>
            </div>

            <div class="user-info flex-1">
              <div class="user-header flex flex-ac gap-16 mb-16">
                <h1 class="username text-2xl font-bold text-color mb-8">{{ userInfo?.nickname || userInfo?.username ||
                  'Liuxin' }}</h1>
                <div class="user-badges">
                  <span class="badge verified">✓ 已认证</span>
                  <span class="badge level">Lv.{{ calculateLevel(userStats?.postCount || 0) }}</span>
                </div>
              </div>

              <p class="user-bio text-base text-muted">{{ userInfo?.bio || '这个人很懒，什么都没有留下...' }}</p>

              <div class="user-meta flex flex-wrap gap-20 mb-16">
                <div class="meta-item flex flex-ac gap-8">
                  <i class="icon">📧</i>
                  <span>{{ userInfo?.email || '未设置邮箱' }}</span>
                </div>
                <div class="meta-item flex flex-ac gap-8">
                  <i class="icon">📅</i>
                  <span>{{ formatDate(userInfo?.createdAt) }} 加入</span>
                </div>
                <div class="meta-item flex flex-ac gap-8" v-if="userStats?.lastPostAt">
                  <i class="icon">✍️</i>
                  <span>最后发文：{{ formatRelativeTime(userStats.lastPostAt) }}</span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 主要内容区域 -->
    <div class="main-content content bg-main">
      <!-- <div class="container">
       <div class="info-card">
              <div class="card-header">
                <h3>👤 个人信息</h3>
              </div>
              <div class="info-list">
                <div class="info-item">
                  <span class="info-label">用户名</span>
                  <span class="info-value">{{ userInfo?.username || '未设置' }}</span>
                </div>
                <div class="info-item">
                  <span class="info-label">昵称</span>
                  <span class="info-value">{{ userInfo?.nickname || '未设置' }}</span>
                </div>
                <div class="info-item">
                  <span class="info-label">邮箱</span>
                  <span class="info-value">{{ userInfo?.email || '未设置' }}</span>
                </div>
                <div class="info-item">
                  <span class="info-label">注册时间</span>
                  <span class="info-value">{{ formatDate(userInfo?.createdAt) }}</span>
                </div>
                <div class="info-item" v-if="userStats?.lastCommentAt">
                  <span class="info-label">最后评论</span>
                  <span class="info-value">{{ formatRelativeTime(userStats.lastCommentAt) }}</span>
                </div>
                <div class="info-item" v-if="userStats?.lastPostAt">
                  <span class="info-label">最后发文</span>
                  <span class="info-value">{{ formatRelativeTime(userStats.lastPostAt) }}</span>
                </div>
              </div>
            </div>
      </div> -->
    </div>

    <!-- 编辑表单模态框 -->
    <div v-if="showEditForm" class="modal-overlay" @click="closeModal">
      <div class="modal-content" @click.stop>
        <div class="modal-header">
          <h2>编辑个人资料</h2>
          <button class="close-btn" @click="closeModal">×</button>
        </div>

        <form @submit.prevent="handleSubmit" class="edit-form">
          <div class="form-group">
            <label for="email">邮箱 *</label>
            <input id="email" type="email" v-model="formData.email" required class="form-input"
              :class="{ 'error': errors.email }" />
            <small v-if="errors.email" class="error-message">{{ errors.email }}</small>
          </div>

          <div class="form-group">
            <label for="nickname">昵称</label>
            <input id="nickname" type="text" v-model="formData.nickname" class="form-input" placeholder="请输入昵称"
              maxlength="50" />
          </div>

          <div class="form-group">
            <label for="bio">个人简介</label>
            <textarea id="bio" v-model="formData.bio" class="form-textarea" placeholder="介绍一下自己吧..." rows="4"
              maxlength="500"></textarea>
            <small class="form-hint">{{ (formData.bio || '').length }}/500</small>
          </div>

          <div class="form-group">
            <label for="avatarUrl">头像链接</label>
            <input id="avatarUrl" type="url" v-model="formData.avatarUrl" class="form-input" placeholder="请输入头像图片链接" />
          </div>

          <div class="form-actions">
            <button type="button" @click="resetForm" class="btn btn-secondary" :disabled="isLoading">
              重置
            </button>
            <button type="submit" class="btn btn-primary" :disabled="isLoading || !isFormValid">
              <span v-if="isLoading" class="loading-spinner"></span>
              {{ isLoading ? '保存中...' : '保存更改' }}
            </button>
          </div>
        </form>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useUserStore } from '../stores/user'
import { UserService, type UpdateProfileRequest, type UserStats } from '../services/user'
import { showSuccess, showError } from '../utils/errorHandler'
import { formatDate, formatRelativeTime } from '../utils/uitls'

const userStore = useUserStore()
const isLoading = ref(false)
const showEditForm = ref(false)
const showAvatarEdit = ref(false)
const userStats = ref<UserStats | null>(null)
const statsLoading = ref(false)

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

const closeModal = () => {
  showEditForm.value = false
  showAvatarEdit.value = false
  resetForm()
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
    closeModal()
  } catch (error) {
    console.error('更新个人资料失败:', error)
    showError('更新个人资料失败，请稍后重试')
  } finally {
    isLoading.value = false
  }
}

// 计算用户等级
const calculateLevel = (postCount: number) => {
  if (postCount >= 100) return 10
  if (postCount >= 50) return 9
  if (postCount >= 30) return 8
  if (postCount >= 20) return 7
  if (postCount >= 15) return 6
  if (postCount >= 10) return 5
  if (postCount >= 7) return 4
  if (postCount >= 5) return 3
  if (postCount >= 3) return 2
  if (postCount >= 1) return 1
  return 0
}


// 获取用户统计信息
const loadUserStats = async () => {
  if (!userStore.isLoggedIn) return

  statsLoading.value = true
  try {
    userStats.value = await UserService.getUserStats()
  } catch (error) {
    console.error('获取用户统计信息失败:', error)
    showError('获取统计信息失败')
  } finally {
    statsLoading.value = false
  }
}

// 生命周期
onMounted(async () => {
  initForm()
  await loadUserStats()
})
</script>

<style scoped>
.profile-page {
  background: var(--bg-main);
}

/* 个人资料横幅 */
.profile-banner {
  width: 100%;
  height: 220px;
  overflow: hidden;
  z-index: 1;
  position: absolute;
  top: 0;
  transform: translateY(70px);

}

.profile-header {
  height: 100%;
  position: relative;
  z-index: 2;
}

.profile-main {
  display: flex;
  align-items: center;
  gap: 40px;
}

.avatar-section {
  flex-shrink: 0;
}

.avatar-container {
  position: relative;
  display: inline-block;
}

/* 保持头像特色效果 */
.user-avatar {
  width: 140px;
  height: 140px;
  border-radius: 50%;
  border: 5px solid rgba(255, 255, 255, 0.3);
  object-fit: cover;
  transition: all 0.3s ease;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.2);
}

.user-avatar:hover {
  transform: scale(1.05);
  border-color: rgba(255, 255, 255, 0.5);
}

.avatar-edit {
  position: absolute;
  bottom: 5px;
  right: 5px;
  width: 40px;
  height: 40px;
  background: #fff;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
  transition: all 0.3s ease;
}

.avatar-edit:hover {
  background: #f8f9fa;
  transform: scale(1.1);
}

.online-status {
  position: absolute;
  top: 10px;
  right: 10px;
  width: 20px;
  height: 20px;
  background: #10b981;
  border: 3px solid #fff;
  border-radius: 50%;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

.user-header {
  display: flex;
  align-items: center;
  gap: 15px;
  margin-bottom: 15px;
}

.username {
  font-size: 2.4rem;
  font-weight: 700;
  margin: 0;
  text-shadow: 0 2px 4px rgba(0, 0, 0, 0.3);
}


.badge.verified {
  background: rgba(16, 185, 129, 0.2);
  color: #10b981;
  border: 1px solid rgba(16, 185, 129, 0.3);
}

.badge.level {
  border: 1px solid rgba(251, 191, 36, 0.3);
}

.user-bio {
  font-size: 1.2rem;
  opacity: 0.95;
  margin: 0 0 20px 0;
  line-height: 1.6;
  max-width: 600px;
}




/* 主要内容区域 */
.main-content {
  padding: 30px 0;
}



/* 模态框 */
/* 模态框基础样式 */
.modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background-color: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
  padding: 20px;
}

.modal-content {
  background-color: var(--bg-card);
  border-radius: 10px;
  box-shadow: var(--shadow-lg);
  max-width: 500px;
  width: 100%;
  animation: fadeIn 0.3s ease-out;
}

@keyframes fadeIn {
  from { opacity: 0; transform: translateY(-10px); }
  to { opacity: 1; transform: translateY(0); }
}

.modal-header {
  padding: 20px 24px;
  border-bottom: 1px solid var(--border-soft);
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.modal-header h2 {
  font-size: 1.25rem;
  color: var(--text-title);
  margin: 0;
}

.close-btn {
  background: none;
  border: none;
  color: var(--text-subtle);
  font-size: 1.5rem;
  cursor: pointer;
  transition: color 0.2s;
}

.close-btn:hover {
  color: var(--color-error);
}

.edit-form {
  padding: 24px;
}

.form-group {
  margin-bottom: 20px;
}

.form-group label {
  display: block;
  margin-bottom: 6px;
  font-size: 0.95rem;
  color: var(--text-subtle);
}

.form-input,
.form-textarea {
  width: 100%;
  padding: 10px 12px;
  border: 1px solid var(--border-soft);
  border-radius: 6px;
  font-size: 1rem;
  transition: border-color 0.2s;
}

.form-input:focus,
.form-textarea:focus {
  border-color: var(--color-primary);
  outline: none;
  box-shadow: 0 0 0 2px rgba(107, 166, 197, 0.2);
}

.form-textarea {
  resize: vertical;
  min-height: 100px;
}

.form-hint {
  display: block;
  margin-top: 4px;
  font-size: 0.75rem;
  color: var(--text-subtle);
}

.error-message {
  display: block;
  margin-top: 4px;
  font-size: 0.75rem;
  color: var(--color-error);
}

.form-group input.error,
.form-group textarea.error {
  border-color: var(--color-error);
}

.form-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  margin-top: 24px;
  padding-top: 16px;
  border-top: 1px solid var(--border-soft);
}

.btn {
  padding: 8px 20px;
  border-radius: 6px;
  font-weight: 500;
  font-size: 0.95rem;
  border: none;
  cursor: pointer;
  transition: all 0.2s;
}

.btn-primary {
  background-color: var(--color-primary);
  color: var(--bg-main);
}

.btn-primary:hover {
  background-color: var(--color-primary-dark);
  transform: translateY(-1px);
  box-shadow: var(--shadow-sm);
}

.btn-primary:active {
  transform: translateY(0);
  box-shadow: none;
}

.btn-secondary {
  background-color: var(--color-primary-light);
  color: var(--text-title);
}

.btn-secondary:hover {
  background-color: var(--bg-hover);
}

.btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.loading-spinner {
  border: 2px solid var(--color-primary);
  border-top: 2px solid transparent;
  border-radius: 50%;
  width: 16px;
  height: 16px;
  animation: spin 1s linear infinite;
  margin-right: 8px;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

/* 响应式优化 */
@media (max-width: 500px) {
  .modal-content {
    border-radius: 0;
    margin: 0;
    height: 100%;
  }

  .form-actions {
    flex-direction: column;
    gap: 8px;
  }

  .btn {
    width: 100%;
  }
}

</style>