<template>
  <div class="profile-page">
    <!-- 悬浮卡片 - 透明背景 + 毛玻璃 -->
    <div class="profile-card">
      <div class="profile-inner">
        <!-- 头像区域 -->
        <div class="avatar-section">
          <div class="avatar-container">
            <img :src="userInfo?.avatarUrl || '/default-avatar.svg'" :alt="userInfo?.username" class="user-avatar" />
            <button class="avatar-edit" @click="showEditForm = true" title="编辑资料">
              <svg viewBox="0 0 24 24" width="14" height="14" fill="currentColor">
                <path d="M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25zM20.71 7.04c.39-.39.39-1.02 0-1.41l-2.34-2.34c-.39-.39-1.02-.39-1.41 0l-1.83 1.83 3.75 3.75 1.83-1.83z"/>
              </svg>
            </button>
          </div>
          <div class="level-badge">
            <Icon name="trophy" size="14" class="level-icon" />
            <span>Lv.{{ calculateLevel(userStats?.points || 0) }}</span>
          </div>
        </div>

        <!-- 用户信息 -->
        <div class="user-info">
          <div class="user-header">
            <h1 class="username">{{ userInfo?.nickname || userInfo?.username || 'Liuxin' }}</h1>
            <Icon name="check" size="14" class="verified-badge" />
          </div>
          <p class="user-bio">{{ userInfo?.bio || '这个人很懒，什么都没有留下...' }}</p>
        </div>

        <!-- 统计 -->
        <div class="stats-section">
          <div class="stats-row">
            <div class="stat-item">
              <span class="stat-value">{{ userStats?.favoriteCount || 0 }}</span>
              <span class="stat-label">收藏</span>
            </div>
            <span class="stat-divider">·</span>
            <div class="stat-item">
              <span class="stat-value">{{ userStats?.commentCount || 0 }}</span>
              <span class="stat-label">评论</span>
            </div>
            <span class="stat-divider">·</span>
            <div class="stat-item">
              <span class="stat-value">{{ userStats?.points || 0 }}</span>
              <span class="stat-label">积分</span>
            </div>
            <template v-if="userStore.isAdmin">
              <span class="stat-divider">·</span>
              <div class="stat-item admin">
                <span class="stat-value">{{ userStats?.postCount || 0 }}</span>
                <span class="stat-label">文章</span>
              </div>
            </template>
          </div>
        </div>
      </div>
    </div>

    <!-- 主要内容 -->
    <div class="main-content">
      <div class="content">
        <!-- 签到 -->
        <CheckinCard @checkin-success="handleCheckinSuccess" class="mb-20" />

        <!-- 下方卡片 -->
        <div class="content-grid">
          <!-- 成就 -->
          <div class="section-card">
            <div class="section-header">成就徽章</div>
            <div class="badges-grid">
              <div class="badge-item" :class="{ locked: !userStats?.favoriteCount }">
                <Icon name="book" size="18" class="badge-icon" />
                <span class="badge-name">藏书达人</span>
              </div>
              <div class="badge-item" :class="{ locked: (userStats?.commentCount || 0) < 10 }">
                <Icon name="message" size="18" class="badge-icon" />
                <span class="badge-name">热心观众</span>
              </div>
              <div class="badge-item" :class="{ locked: (userStats?.points || 0) < 100 }">
                <Icon name="star" size="18" class="badge-icon" />
                <span class="badge-name">积分达人</span>
              </div>
              <div class="badge-item" :class="{ locked: userStore.isAdmin && (userStats?.postCount || 0) < 1 }">
                <Icon name="pen" size="18" class="badge-icon" />
                <span class="badge-name">首发文章</span>
              </div>
            </div>
          </div>

          <!-- 动态 -->
          <div class="section-card">
            <div class="section-header">最近动态</div>
            <div class="timeline">
              <div class="timeline-item" v-if="userStats?.lastPostAt && userStore.isAdmin">
                <span class="timeline-dot"></span>
                <span class="timeline-text">发布了新文章</span>
                <span class="timeline-time">{{ formatRelativeTime(userStats.lastPostAt) }}</span>
              </div>
              <div class="timeline-item" v-if="userStats?.lastCommentAt">
                <span class="timeline-dot"></span>
                <span class="timeline-text">发表了评论</span>
                <span class="timeline-time">{{ formatRelativeTime(userStats.lastCommentAt) }}</span>
              </div>
              <div class="timeline-item">
                <span class="timeline-dot"></span>
                <span class="timeline-text">加入了平台</span>
                <span class="timeline-time">{{ formatRelativeTime(userInfo?.createdAt || '') }}</span>
              </div>
              <div v-if="!userStats?.lastPostAt && !userStats?.lastCommentAt" class="empty-tip">
                暂无活动记录
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 编辑模态框 -->
    <div v-if="showEditForm" class="modal-overlay" @click="closeModal">
      <div class="modal-content" @click.stop>
        <div class="modal-header">
          <h2>编辑个人资料</h2>
          <button class="close-btn" @click="closeModal">×</button>
        </div>
        <form @submit.prevent="handleSubmit" class="edit-form">
          <div class="form-group">
            <label>邮箱 *</label>
            <input type="email" v-model="formData.email" required class="form-input" />
          </div>
          <div class="form-group">
            <label>昵称</label>
            <input type="text" v-model="formData.nickname" class="form-input" maxlength="50" />
          </div>
          <div class="form-group">
            <label>个人简介</label>
            <textarea v-model="formData.bio" class="form-textarea" rows="3" maxlength="500"></textarea>
            <small class="form-hint">{{ (formData.bio || '').length }}/500</small>
          </div>
          <div class="form-group">
            <label>头像</label>
            <div class="avatar-preview-section">
              <img :src="formData.avatarUrl || '/default-avatar.svg'" class="avatar-preview" />
              <input type="url" v-model="formData.avatarUrl" class="form-input flex-1" placeholder="头像链接" />
            </div>
          </div>
          <div class="form-actions">
            <button type="button" @click="resetForm" class="btn btn-secondary">重置</button>
            <button type="submit" class="btn btn-primary" :disabled="isLoading">
              {{ isLoading ? '保存中...' : '保存' }}
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
import { UserService, type UpdateProfileRequest, type UserStats, type CheckinResponse } from '../services/user'
import { showSuccess, showError } from '../utils/errorHandler'
import { formatRelativeTime } from '../utils/uitls'
import CheckinCard from '../components/CheckinCard.vue'
import Icon from '../components/Icon.vue'

const userStore = useUserStore()
const isLoading = ref(false)
const showEditForm = ref(false)
const userStats = ref<UserStats | null>(null)

const formData = reactive<UpdateProfileRequest>({
  email: '',
  nickname: '',
  bio: '',
  avatarUrl: ''
})

const userInfo = computed(() => userStore.userInfo)

const calculateLevel = (points: number) => {
  if (points >= 1000) return 10
  if (points >= 700) return 9
  if (points >= 500) return 8
  if (points >= 300) return 7
  if (points >= 200) return 6
  if (points >= 100) return 5
  if (points >= 50) return 4
  if (points >= 30) return 3
  if (points >= 10) return 2
  if (points >= 1) return 1
  return 0
}

const initForm = () => {
  if (userInfo.value) {
    formData.email = userInfo.value.email || ''
    formData.nickname = userInfo.value.nickname || ''
    formData.bio = userInfo.value.bio || ''
    formData.avatarUrl = userInfo.value.avatarUrl || ''
  }
}

const resetForm = () => initForm()
const closeModal = () => { showEditForm.value = false; resetForm() }

const handleSubmit = async () => {
  if (!formData.email) return
  isLoading.value = true
  try {
    const updatedUser = await UserService.updateProfile(formData)
    userStore.updateUserInfo(updatedUser)
    showSuccess('更新成功')
    closeModal()
  } catch (error) {
    showError('更新失败')
  } finally {
    isLoading.value = false
  }
}

const loadUserStats = async () => {
  if (!userStore.isLoggedIn) return
  try {
    userStats.value = await UserService.getUserStats()
  } catch (error) {
    console.error(error)
  }
}

const handleCheckinSuccess = (result: CheckinResponse) => {
  if (userStats.value) userStats.value.points = result.totalPoints
}

onMounted(() => {
  initForm()
  loadUserStats()
})
</script>

<style scoped lang="scss">
@use "@/assets/styles/tokens" as *;

/* 悬浮卡片 - 透明 + 毛玻璃 */
.profile-card {
  margin: 0 auto 0;
  max-width: 1200px;
  padding: 0 20px;
  position: relative;
  z-index: 10;
}

.profile-inner {
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  border-radius: 16px;
  padding: 32px 40px;
  display: flex;
  align-items: center;
  gap: 40px;
  min-height: 200px;
  background: var(--bg-card);
  border: 1px solid var(--border-base);
  box-shadow: var(--shadow-sm);


  @include respond(lg) {
    flex-wrap: wrap;
    justify-content: center;
    min-height: auto;
    padding: 32px;
    gap: 24px;
  }

  @include respond(md) {
    flex-direction: column;
    text-align: center;
    padding: 28px 24px;
  }
}

/* 头像 */
.avatar-section {
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
}

.avatar-container {
  position: relative;
  display: inline-block;
}

.user-avatar {
  width: 120px;
  height: 120px;
  border-radius: 50%;
  border: 3px solid var(--bg-card);
  object-fit: cover;
  box-shadow: var(--shadow-sm);

  @include respond(sm) {
    width: 90px;
    height: 90px;
  }
}

.avatar-edit {
  position: absolute;
  bottom: 4px;
  right: 4px;
  width: 32px;
  height: 32px;
  background: var(--color-primary);
  border: 2px solid var(--bg-card);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  color: #fff;
  transition: all 0.2s;

  &:hover {
    background: var(--color-primary-dark);
    transform: scale(1.05);
  }
}

.level-badge {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 12px;
  background: linear-gradient(135deg, var(--color-warning) 0%, #d97706 100%);
  border-radius: 12px;
  color: #fff;
  font-size: 0.8rem;
  font-weight: 500;
}

/* 用户信息 */
.user-info {
  flex: 1;
  min-width: 180px;

  @include respond(md) {
    width: 100%;
  }
}

.user-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;

  @include respond(md) {
    justify-content: center;
  }
}

.username {
  font-size: 1.5rem;
  font-weight: 600;
  color: var(--text-title);
  margin: 0;
}

.verified-badge {
  color: var(--color-success);
  font-size: 0.9rem;
}

.user-bio {
  color: var(--text-subtle);
  font-size: 0.9rem;
  margin: 0;

  @include respond(md) {
    text-align: center;
  }
}

/* 统计 */
.stats-section {
  flex-shrink: 0;

  @include respond(lg) {
    width: 100%;
  }
}

.stats-row {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding: 16px 24px;
  background: var(--bg-soft);
  border-radius: 12px;

  @include respond(lg) {
    width: fit-content;
    margin: 0 auto;
  }
}

.stat-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 0 12px;

  &.admin .stat-value {
    color: var(--color-primary);
  }
}

.stat-value {
  font-size: 1.25rem;
  font-weight: 600;
  color: var(--text-title);
}

.stat-label {
  font-size: 0.75rem;
  color: var(--text-muted);
  margin-top: 2px;
}

.stat-divider {
  color: var(--border-base);
  font-size: 0.9rem;
}

/* 主要内容 */
.main-content {
  padding: 40px 0 60px;
}

.content-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 20px;

  @include respond(lg) {
    grid-template-columns: 1fr;
  }
}

.section-card {
  background: var(--bg-card);
  border-radius: 12px;
  padding: 20px;
  border: 1px solid var(--border-base);
  box-shadow: var(--shadow-sm);
}

.section-header {
  font-size: 0.95rem;
  font-weight: 600;
  color: var(--text-title);
  margin-bottom: 16px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--border-light);
}

/* 徽章 */
.badges-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;

  @include respond(sm) {
    grid-template-columns: 1fr;
  }
}

.badge-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px;
  background: var(--bg-soft);
  border-radius: 8px;
  transition: all 0.2s;

  &:hover {
    transform: translateY(-1px);
  }

  &.locked {
    opacity: 0.4;
  }
}

.badge-icon {
  font-size: 1.25rem;
}

.badge-name {
  font-size: 0.9rem;
  color: var(--text-main);
}

/* 时间线 */
.timeline {
  position: relative;
}

.timeline-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 0;
  font-size: 0.9rem;

  &:not(:last-child) {
    border-bottom: 1px solid var(--border-light);
  }
}

.timeline-dot {
  width: 6px;
  height: 6px;
  background: var(--color-primary);
  border-radius: 50%;
  flex-shrink: 0;
}

.timeline-text {
  color: var(--text-main);
  flex: 1;
}

.timeline-time {
  color: var(--text-muted);
  font-size: 0.8rem;
}

.empty-tip {
  color: var(--text-muted);
  font-size: 0.9rem;
  text-align: center;
  padding: 20px;
}

/* 模态框 */
.modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.6);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
  padding: 20px;
  backdrop-filter: blur(4px);
}

.modal-content {
  background: var(--bg-card);
  border-radius: 12px;
  max-width: 440px;
  width: 100%;
  box-shadow: var(--shadow-lg);
}

.modal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 20px;
  border-bottom: 1px solid var(--border-light);

  h2 {
    font-size: 1.1rem;
    color: var(--text-title);
    margin: 0;
  }
}

.close-btn {
  background: none;
  border: none;
  font-size: 1.4rem;
  color: var(--text-muted);
  cursor: pointer;

  &:hover {
    color: var(--color-error);
  }
}

.edit-form {
  padding: 20px;
}

.form-group {
  margin-bottom: 16px;

  label {
    display: block;
    font-size: 0.85rem;
    color: var(--text-subtle);
    margin-bottom: 6px;
  }
}

.form-input,
.form-textarea {
  width: 100%;
  padding: 10px 12px;
  border: 1px solid var(--border-base);
  border-radius: 8px;
  font-size: 0.95rem;
  background: var(--bg-element);
  color: var(--text-main);

  &:focus {
    outline: none;
    border-color: var(--color-primary);
  }
}

.form-textarea {
  resize: vertical;
  min-height: 80px;
}

.form-hint {
  font-size: 0.75rem;
  color: var(--text-muted);
  margin-top: 4px;
}

.avatar-preview-section {
  display: flex;
  gap: 12px;
  align-items: center;
}

.avatar-preview {
  width: 60px;
  height: 60px;
  border-radius: 50%;
  object-fit: cover;
}

.form-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 20px;
  padding-top: 16px;
  border-top: 1px solid var(--border-light);
}

.btn {
  padding: 8px 18px;
  border-radius: 8px;
  font-size: 0.9rem;
  border: none;
  cursor: pointer;
  transition: all 0.2s;
}

.btn-primary {
  background: var(--color-primary);
  color: #fff;

  &:hover:not(:disabled) {
    background: var(--color-primary-dark);
  }
}

.btn-secondary {
  background: var(--bg-soft);
  color: var(--text-main);

  &:hover {
    background: var(--bg-hover);
  }
}

.btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

/* 移动端 */
@include respond(sm) {
  .profile-card {
    margin-top: -200px;
    padding: 0 12px;
  }

  .profile-inner {
    padding: 24px 16px;
  }

  .stats-row {
    width: 100%;
    flex-wrap: wrap;
    gap: 16px;
  }

  .stat-item {
    padding: 0 16px;
  }
}
</style>
