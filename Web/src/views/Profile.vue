<template>
  <div class="profile-page">
    <!-- 个人资料头部横幅 -->
    <div class="profile-banner">
      <div class="banner-bg">
        <div class="banner-overlay"></div>
      </div>
      <div class="profile-header">
        <div class="container">
          <div class="profile-main">
            <div class="avatar-section">
              <div class="avatar-container">
                <img 
                  :src="userInfo?.avatarUrl || '/default-avatar.svg'" 
                  :alt="userInfo?.username"
                  class="user-avatar"
                />
                <div class="avatar-edit" @click="showEditForm = true">
                  <i class="edit-icon">✏️</i>
                </div>
                <div class="online-status"></div>
              </div>
            </div>
            
            <div class="user-info">
              <div class="user-header">
                <h1 class="username">{{ userInfo?.nickname || userInfo?.username || 'Liuxin' }}</h1>
                <div class="user-badges">
                  <span class="badge verified">✓ 已认证</span>
                  <span class="badge level">Lv.{{ calculateLevel(userStats?.postCount || 0) }}</span>
                </div>
              </div>
              
              <p class="user-bio">{{ userInfo?.bio || '这个人很懒，什么都没有留下...' }}</p>
              
              <div class="user-meta">
                <div class="meta-item">
                  <i class="icon">📧</i>
                  <span>{{ userInfo?.email || '未设置邮箱' }}</span>
                </div>
                <div class="meta-item">
                  <i class="icon">📅</i>
                  <span>{{ formatDate(userInfo?.createdAt) }} 加入</span>
                </div>
                <div class="meta-item" v-if="userStats?.lastPostAt">
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
    <div class="main-content">
      <div class="container">
        <div class="content-grid">
          <!-- 左侧：统计信息和详细信息 -->
          <div class="left-sidebar">
            <!-- 统计卡片 -->
            <div class="stats-card">
              <div class="card-header">
                <h3>📊 数据统计</h3>
              </div>
              <div class="stats-grid">
                <div class="stat-item primary">
                  <div class="stat-icon">📝</div>
                  <div class="stat-content">
                    <span class="stat-value">{{ userStats?.postCount || 0 }}</span>
                    <span class="stat-label">发布文章</span>
                  </div>
                </div>
                <div class="stat-item success">
                  <div class="stat-icon">💬</div>
                  <div class="stat-content">
                    <span class="stat-value">{{ userStats?.commentCount || 0 }}</span>
                    <span class="stat-label">评论数量</span>
                  </div>
                </div>
                <div class="stat-item warning">
                  <div class="stat-icon">📄</div>
                  <div class="stat-content">
                    <span class="stat-value">{{ userStats?.draftCount || 0 }}</span>
                    <span class="stat-label">草稿箱</span>
                  </div>
                </div>
                <div class="stat-item info">
                  <div class="stat-icon">👀</div>
                  <div class="stat-content">
                    <span class="stat-value">{{ formatNumber(userStats?.viewCount || 0) }}</span>
                    <span class="stat-label">总浏览量</span>
                  </div>
                </div>
                <div class="stat-item purple">
                  <div class="stat-icon">⭐</div>
                  <div class="stat-content">
                    <span class="stat-value">{{ userStats?.points || userInfo?.points || 0 }}</span>
                    <span class="stat-label">积分</span>
                  </div>
                </div>
                <div class="stat-item pink">
                  <div class="stat-icon">🏆</div>
                  <div class="stat-content">
                    <span class="stat-value">{{ calculateRank(userStats?.postCount || 0) }}</span>
                    <span class="stat-label">排名</span>
                  </div>
                </div>
              </div>
            </div>
            
            <!-- 个人详细信息 -->
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
          </div>
          
          <!-- 右侧：活动时间线 -->
          <div class="right-content">
            <div class="activity-card">
              <div class="card-header">
                <h3>📈 活动时间线</h3>
              </div>
              <div class="activity-timeline">
                <div class="timeline-item" v-if="userStats?.lastPostAt">
                  <div class="timeline-dot post"></div>
                  <div class="timeline-content">
                    <div class="timeline-title">发布了新文章</div>
                    <div class="timeline-time">{{ formatRelativeTime(userStats.lastPostAt) }}</div>
                  </div>
                </div>
                <div class="timeline-item" v-if="userStats?.lastCommentAt">
                  <div class="timeline-dot comment"></div>
                  <div class="timeline-content">
                    <div class="timeline-title">发表了评论</div>
                    <div class="timeline-time">{{ formatRelativeTime(userStats.lastCommentAt) }}</div>
                  </div>
                </div>
                <div class="timeline-item">
                  <div class="timeline-dot join"></div>
                  <div class="timeline-content">
                    <div class="timeline-title">加入了社区</div>
                    <div class="timeline-time">{{ formatRelativeTime(userInfo?.createdAt) }}</div>
                  </div>
                </div>
              </div>
            </div>
            
            <!-- 成就徽章 -->
            <div class="achievements-card">
              <div class="card-header">
                <h3>🏅 成就徽章</h3>
              </div>
              <div class="achievements-grid">
                <div class="achievement-item" :class="{ earned: (userStats?.postCount || 0) >= 1 }">
                  <div class="achievement-icon">✍️</div>
                  <div class="achievement-name">初出茅庐</div>
                  <div class="achievement-desc">发布第一篇文章</div>
                </div>
                <div class="achievement-item" :class="{ earned: (userStats?.postCount || 0) >= 10 }">
                  <div class="achievement-icon">📚</div>
                  <div class="achievement-name">勤奋作者</div>
                  <div class="achievement-desc">发布10篇文章</div>
                </div>
                <div class="achievement-item" :class="{ earned: (userStats?.commentCount || 0) >= 50 }">
                  <div class="achievement-icon">💬</div>
                  <div class="achievement-name">活跃评论家</div>
                  <div class="achievement-desc">发表50条评论</div>
                </div>
                <div class="achievement-item" :class="{ earned: (userStats?.viewCount || 0) >= 1000 }">
                  <div class="achievement-icon">🔥</div>
                  <div class="achievement-name">人气作者</div>
                  <div class="achievement-desc">获得1000次浏览</div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
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
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useUserStore } from '../stores/user'
import { UserService, type UpdateProfileRequest, type UserStats } from '../services/user'
import { showSuccess, showError } from '../utils/errorHandler'

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

const formatDate = (dateString?: string) => {
  if (!dateString) return '暂无'
  return new Date(dateString).toLocaleDateString('zh-CN', {
    year: 'numeric',
    month: 'long',
    day: 'numeric'
  })
}

// 格式化相对时间
const formatRelativeTime = (dateString?: string) => {
  if (!dateString) return '暂无'
  const date = new Date(dateString)
  const now = new Date()
  const diff = now.getTime() - date.getTime()
  
  const minutes = Math.floor(diff / (1000 * 60))
  const hours = Math.floor(diff / (1000 * 60 * 60))
  const days = Math.floor(diff / (1000 * 60 * 60 * 24))
  const months = Math.floor(diff / (1000 * 60 * 60 * 24 * 30))
  const years = Math.floor(diff / (1000 * 60 * 60 * 24 * 365))
  
  if (years > 0) return `${years}年前`
  if (months > 0) return `${months}个月前`
  if (days > 0) return `${days}天前`
  if (hours > 0) return `${hours}小时前`
  if (minutes > 0) return `${minutes}分钟前`
  return '刚刚'
}

// 格式化数字
const formatNumber = (num: number) => {
  if (num >= 10000) {
    return (num / 10000).toFixed(1) + 'w'
  }
  if (num >= 1000) {
    return (num / 1000).toFixed(1) + 'k'
  }
  return num.toString()
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

// 计算排名
const calculateRank = (postCount: number) => {
  if (postCount >= 100) return 'Top 1%'
  if (postCount >= 50) return 'Top 5%'
  if (postCount >= 20) return 'Top 10%'
  if (postCount >= 10) return 'Top 25%'
  if (postCount >= 5) return 'Top 50%'
  return 'New'
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
  min-height: 100vh;
  background: var(--bg-color);
}

/* 容器 */
.container {
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 20px;
}

/* 个人资料横幅 */
.profile-banner {
  width: 100%;
  position: absolute;
  top: 60px;
  left: 0;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  overflow: hidden;
  z-index: 1;
}

.banner-bg {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}

.banner-overlay {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.1);
}

.profile-header {
  position: relative;
  z-index: 2;
  padding: 45px 0 75px 0;
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

.edit-icon {
  font-size: 18px;
}

.user-info {
  flex: 1;
  color: white;
}

.user-header {
  display: flex;
  align-items: center;
  gap: 15px;
  margin-bottom: 15px;
}

.username {
  font-size: 2.8rem;
  font-weight: 700;
  margin: 0;
  text-shadow: 0 2px 4px rgba(0, 0, 0, 0.3);
}

.user-badges {
  display: flex;
  gap: 10px;
}

.badge {
  padding: 6px 12px;
  border-radius: 20px;
  font-size: 0.85rem;
  font-weight: 600;
  backdrop-filter: blur(10px);
}

.badge.verified {
  background: rgba(16, 185, 129, 0.2);
  color: #10b981;
  border: 1px solid rgba(16, 185, 129, 0.3);
}

.badge.level {
  background: rgba(251, 191, 36, 0.2);
  color: #f59e0b;
  border: 1px solid rgba(251, 191, 36, 0.3);
}

.user-bio {
  font-size: 1.2rem;
  opacity: 0.95;
  margin: 0 0 20px 0;
  line-height: 1.6;
  max-width: 600px;
}

.user-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 20px;
  margin-bottom: 25px;
}

.meta-item {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 0.95rem;
  opacity: 0.9;
}

.meta-item .icon {
  font-size: 1.1rem;
}

.action-buttons {
  display: flex;
  gap: 15px;
}

.btn {
  padding: 12px 24px;
  border: none;
  border-radius: 8px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s ease;
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 0.95rem;
}

.btn-primary {
  background: rgba(255, 255, 255, 0.2);
  color: white;
  border: 1px solid rgba(255, 255, 255, 0.3);
  backdrop-filter: blur(10px);
}

.btn-primary:hover {
  background: rgba(255, 255, 255, 0.3);
  transform: translateY(-2px);
}

.btn-secondary {
  background: transparent;
  color: white;
  border: 1px solid rgba(255, 255, 255, 0.3);
}

.btn-secondary:hover {
  background: rgba(255, 255, 255, 0.1);
  transform: translateY(-2px);
}

/* 主要内容区域 */
.main-content {
  padding: 40px 0;
}

.content-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 30px;
}

.left-sidebar {
  display: flex;
  flex-direction: column;
  gap: 25px;
}

.right-content {
  display: flex;
  flex-direction: column;
  gap: 25px;
}

/* 卡片样式 */
.stats-card,
.info-card,
.activity-card,
.achievements-card {
  background: var(--bg-color);
  border-radius: 16px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.08);
  overflow: hidden;
  transition: all 0.3s ease;
  border: 1px solid var(--border-color);
}

.stats-card:hover,
.info-card:hover,
.activity-card:hover,
.achievements-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 30px rgba(0, 0, 0, 0.12);
}

.card-header {
  padding: 20px 25px;
  border-bottom: 1px solid var(--border-color);
  background: var(--hover-color);
}

.card-header h3 {
  margin: 0;
  font-size: 1.1rem;
  font-weight: 600;
  color: var(--text-color);
}

/* 统计网格 */
.stats-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 15px;
  padding: 25px;
}

.stat-item {
  display: flex;
  align-items: center;
  gap: 15px;
  padding: 20px;
  border-radius: 12px;
  transition: all 0.3s ease;
  position: relative;
  overflow: hidden;
}

.stat-item::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  opacity: 0.1;
  border-radius: 12px;
}

.stat-item.primary::before { background: #3b82f6; }
.stat-item.success::before { background: #10b981; }
.stat-item.warning::before { background: #f59e0b; }
.stat-item.info::before { background: #06b6d4; }
.stat-item.purple::before { background: #8b5cf6; }
.stat-item.pink::before { background: #ec4899; }

.stat-item:hover {
  transform: translateY(-3px);
  box-shadow: 0 8px 25px rgba(0, 0, 0, 0.1);
}

.stat-icon {
  font-size: 2rem;
  flex-shrink: 0;
}

.stat-content {
  flex: 1;
}

.stat-value {
  display: block;
  font-size: 1.8rem;
  font-weight: 700;
  color: var(--text-color);
  margin-bottom: 4px;
}

.stat-label {
  font-size: 0.85rem;
  color: var(--text-color);
  opacity: 0.7;
  font-weight: 500;
}

/* 信息列表 */
.info-list {
  padding: 25px;
}

.info-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 15px 0;
  border-bottom: 1px solid var(--border-color);
}

.info-item:last-child {
  border-bottom: none;
}

.info-label {
  font-weight: 600;
  color: var(--text-color);
}

.info-value {
  color: var(--text-color);
  opacity: 0.7;
  font-weight: 500;
}

/* 活动时间线 */
.activity-timeline {
  padding: 25px;
}

.timeline-item {
  display: flex;
  align-items: flex-start;
  gap: 15px;
  margin-bottom: 20px;
  position: relative;
}

.timeline-item:last-child {
  margin-bottom: 0;
}

.timeline-item:not(:last-child)::after {
  content: '';
  position: absolute;
  left: 10px;
  top: 30px;
  bottom: -20px;
  width: 2px;
  background: var(--border-color);
}

.timeline-dot {
  width: 20px;
  height: 20px;
  border-radius: 50%;
  flex-shrink: 0;
  position: relative;
  z-index: 1;
}

.timeline-dot.post { background: #3b82f6; }
.timeline-dot.comment { background: #10b981; }
.timeline-dot.join { background: #8b5cf6; }

.timeline-content {
  flex: 1;
}

.timeline-title {
  font-weight: 600;
  color: var(--text-color);
  margin-bottom: 4px;
}

.timeline-time {
  font-size: 0.85rem;
  color: var(--text-color);
  opacity: 0.7;
}

/* 成就徽章 */
.achievements-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 15px;
  padding: 25px;
}

.achievement-item {
  text-align: center;
  padding: 20px;
  border-radius: 12px;
  border: 2px solid var(--border-color);
  transition: all 0.3s ease;
  opacity: 0.6;
}

.achievement-item.earned {
  opacity: 1;
  border-color: #10b981;
  background: linear-gradient(135deg, #ecfdf5 0%, #f0fdf4 100%);
}

.achievement-item:hover {
  transform: translateY(-3px);
  box-shadow: 0 8px 25px rgba(0, 0, 0, 0.1);
}

.achievement-icon {
  font-size: 2.5rem;
  margin-bottom: 10px;
}

.achievement-name {
  font-weight: 600;
  color: var(--text-color);
  margin-bottom: 5px;
}

.achievement-desc {
  font-size: 0.8rem;
  color: var(--text-color);
  opacity: 0.7;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .profile-main {
    flex-direction: column;
    text-align: center;
    gap: 25px;
  }
  
  .content-grid {
    grid-template-columns: 1fr;
  }
  
  .stats-grid {
    grid-template-columns: 1fr;
  }
  
  .achievements-grid {
    grid-template-columns: 1fr;
  }
  
  .user-meta {
    justify-content: center;
  }
  
  .action-buttons {
    justify-content: center;
  }
}

/* 模态框样式 */
.modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.6);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
  backdrop-filter: blur(4px);
}

.modal-content {
  background: var(--bg-color);
  border-radius: 16px;
  width: 90%;
  max-width: 500px;
  max-height: 90vh;
  overflow-y: auto;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.3);
  animation: modalSlideIn 0.3s ease-out;
  border: 1px solid var(--border-color);
}

@keyframes modalSlideIn {
  from {
    opacity: 0;
    transform: scale(0.9) translateY(-20px);
  }
  to {
    opacity: 1;
    transform: scale(1) translateY(0);
  }
}

.modal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 24px 24px 16px;
  border-bottom: 1px solid #e2e8f0;
}

.modal-header h2 {
  margin: 0;
  font-size: 1.5rem;
  font-weight: 600;
  color: #1e293b;
}

.close-btn {
  background: none;
  border: none;
  font-size: 24px;
  color: #64748b;
  cursor: pointer;
  padding: 4px;
  border-radius: 4px;
  transition: all 0.2s ease;
}

.close-btn:hover {
  background: #f1f5f9;
  color: #1e293b;
}

.edit-form {
  padding: 24px;
}

.form-group {
  margin-bottom: 20px;
}

.form-group label {
  display: block;
  font-weight: 600;
  color: #374151;
  margin-bottom: 8px;
  font-size: 0.875rem;
}

.form-input,
.form-textarea {
  width: 100%;
  padding: 12px 16px;
  border: 2px solid #e5e7eb;
  border-radius: 8px;
  font-size: 1rem;
  transition: all 0.3s ease;
  background: #fff;
  box-sizing: border-box;
}

.form-input:focus,
.form-textarea:focus {
  outline: none;
  border-color: #3b82f6;
  box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.1);
}

.form-input.error {
  border-color: #ef4444;
}

.form-textarea {
  resize: vertical;
  min-height: 100px;
  font-family: inherit;
}

.form-hint {
  color: #6b7280;
  font-size: 0.75rem;
  margin-top: 4px;
}

.error-message {
  color: #ef4444;
  font-size: 0.75rem;
  margin-top: 4px;
}

.form-actions {
  display: flex;
  gap: 12px;
  justify-content: flex-end;
  margin-top: 24px;
  padding-top: 16px;
  border-top: 1px solid #e5e7eb;
}

.btn {
  padding: 12px 24px;
  border: none;
  border-radius: 8px;
  font-size: 0.875rem;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s ease;
  display: flex;
  align-items: center;
  gap: 8px;
}

.btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.btn-primary {
  background: #3b82f6;
  color: white;
}

.btn-primary:hover:not(:disabled) {
  background: #2563eb;
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(59, 130, 246, 0.4);
}

.btn-secondary {
  background: #6b7280;
  color: white;
}

.btn-secondary:hover:not(:disabled) {
  background: #4b5563;
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

/* 评论区域 */
.comments-section {
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 20px 40px;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.section-header h3 {
  font-size: 1.25rem;
  font-weight: 600;
  color: #1e293b;
  margin: 0;
}

.edit-profile-btn {
  background: #3b82f6;
  color: white;
  border: none;
  padding: 8px 16px;
  border-radius: 6px;
  font-size: 0.875rem;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.3s ease;
}

.edit-profile-btn:hover {
  background: #2563eb;
  transform: translateY(-1px);
}

.comment-item {
  background: white;
  border-radius: 12px;
  padding: 20px;
  margin-bottom: 16px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
  border: 1px solid #e5e7eb;
  display: flex;
  gap: 16px;
  transition: all 0.3s ease;
}

.comment-item:hover {
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.1);
  transform: translateY(-1px);
}

.comment-avatar {
  flex-shrink: 0;
}

.comment-avatar img {
  width: 48px;
  height: 48px;
  border-radius: 50%;
  object-fit: cover;
  border: 2px solid #e5e7eb;
}

.comment-content {
  flex: 1;
}

.comment-header {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  margin-bottom: 8px;
}

.comment-title {
  color: #3b82f6;
  font-weight: 500;
  font-size: 0.875rem;
  line-height: 1.4;
  flex: 1;
}

.comment-emoji {
  font-size: 1.2rem;
  flex-shrink: 0;
}

.comment-text {
  color: #374151;
  font-size: 0.875rem;
  line-height: 1.5;
  margin-bottom: 8px;
}

.comment-meta {
  display: flex;
  gap: 16px;
  font-size: 0.75rem;
  color: #6b7280;
}

.comment-date,
.comment-location {
  display: flex;
  align-items: center;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .profile-banner {
    height: 250px;
  }
  
  .profile-info {
    flex-direction: column;
    text-align: center;
    gap: 12px;
  }
  
  .user-details {
    text-align: center;
  }
  
  .username {
    font-size: 1.5rem;
  }
  
  .user-bio {
    font-size: 1rem;
  }
  
  .stats-card {
    grid-template-columns: repeat(2, 1fr);
    gap: 16px;
    padding: 20px;
  }
  
  .modal-content {
    width: 95%;
    margin: 20px;
  }
  
  .form-actions {
    flex-direction: column;
  }
  
  .btn {
    width: 100%;
    justify-content: center;
  }
  
  .section-header {
    flex-direction: column;
    gap: 12px;
    align-items: stretch;
  }
  
  .comment-item {
    padding: 16px;
  }
  
  .comment-meta {
    flex-direction: column;
    gap: 4px;
  }
}

@media (max-width: 480px) {
  .stats-card {
    grid-template-columns: 1fr;
  }
  
  .user-avatar {
    width: 100px;
    height: 100px;
  }
}
</style>