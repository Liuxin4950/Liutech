<template>
  <div class="profile-page">
    <!-- 悬浮卡片 - 透明背景 + 毛玻璃 -->
    <div class="profile-card">
      <div class="profile-inner">
        <!-- 头像区域 -->
        <div class="avatar-section">
          <div class="avatar-container">
            <img :src="userInfo?.avatarUrl || errImg" :alt="userInfo?.username" class="user-avatar" @error="handleImageError" />
            <button class="avatar-edit" @click="openEditForm" title="编辑资料">
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
              <span class="stat-value">{{ userStats?.postCount || 0 }}</span>
              <span class="stat-label">文章</span>
            </div>
            <span class="stat-divider">·</span>
            <div class="stat-item">
              <span class="stat-value">{{ userStats?.commentCount || 0 }}</span>
              <span class="stat-label">评论</span>
            </div>
            <span class="stat-divider">·</span>
            <div class="stat-item">
              <span class="stat-value">{{ userStats?.favoriteCount || 0 }}</span>
              <span class="stat-label">收藏</span>
            </div>
            <span class="stat-divider">·</span>
            <div class="stat-item">
              <span class="stat-value">{{ userStats?.points || 0 }}</span>
              <span class="stat-label">积分</span>
            </div>
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
              <div
                v-for="badge in achievements"
                :key="badge.name"
                class="badge-item"
                :class="{ locked: badge.locked }"
                :title="badge.description"
              >
                <Icon :name="badge.icon" size="18" class="badge-icon" />
                <div class="badge-info">
                  <span class="badge-name">{{ badge.name }}</span>
                  <span v-if="badge.total" class="badge-progress">{{ badge.progress }}/{{ badge.total }}</span>
                </div>
              </div>
            </div>
          </div>

          <!-- 动态 -->
          <div class="section-card">
            <div class="section-header">最近动态</div>
            <div class="timeline">
              <div
                v-for="(item, index) in timelineItems"
                :key="index"
                class="timeline-item"
              >
                <Icon :name="item.icon" size="14" class="timeline-icon" />
                <span class="timeline-text">{{ item.text }}</span>
                <span class="timeline-time">{{ item.time }}</span>
              </div>
              <div v-if="timelineItems.length === 0" class="empty-tip">
                <img src="@/assets/image/扑到.png" alt="" class="fit-err">
                <span>暂无活动记录</span>
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
          <!-- 头像 -->
          <div class="form-group avatar-form-group">
            <label>头像</label>
            <div class="avatar-upload-row">
              <div
                class="avatar-dropzone"
                :class="{ 'is-dragover': avatarDragOver, 'is-uploading': avatarUploading }"
                @click="triggerAvatarUpload"
                @drop="handleAvatarDrop"
                @dragover="handleAvatarDragOver"
                @dragleave="handleAvatarDragLeave"
                :title="avatarUploading ? '上传中...' : '点击或拖拽图片到此处'"
              >
                <img
                  v-if="formData.avatarUrl"
                  :src="formData.avatarUrl"
                  class="avatar-preview"
                  @error="handleImageError"
                  alt="头像"
                />
                <div v-else class="avatar-placeholder">
                  <Icon name="user" size="32" />
                </div>
                <div v-if="avatarUploading" class="avatar-loading">
                  <span class="spinner"></span>
                </div>
                <div v-else class="avatar-overlay-hint">
                  <Icon name="camera" size="16" />
                  <span>更换</span>
                </div>
              </div>
              <div class="avatar-actions">
                <button
                  type="button"
                  class="btn btn-secondary btn-sm"
                  :disabled="avatarUploading"
                  @click="triggerAvatarUpload"
                >
                  {{ avatarUploading ? '上传中...' : '上传图片' }}
                </button>
                <button
                  type="button"
                  class="avatar-mode-toggle"
                  @click="avatarMode = avatarMode === 'upload' ? 'url' : 'upload'"
                >
                  {{ avatarMode === 'upload' ? '使用外链' : '使用上传' }}
                </button>
                <small class="avatar-hint">支持 PNG / JPG / GIF / WEBP，不超过 5MB</small>
              </div>
            </div>
            <input
              ref="avatarInput"
              type="file"
              accept="image/png,image/jpeg,image/gif,image/webp"
              class="hidden-input"
              @change="handleAvatarChange"
            />
            <div v-if="avatarMode === 'url'" class="avatar-url-input">
              <input
                type="url"
                v-model="formData.avatarUrl"
                class="form-input"
                placeholder="粘贴图片链接 https://..."
              />
            </div>
          </div>

          <div class="form-group">
            <label>用户名</label>
            <input type="text" :value="userInfo?.username || '暂无数据'" class="form-input" disabled />
          </div>

          <div class="form-group">
            <label>邮箱 *</label>
            <input type="email" v-model="formData.email" required class="form-input" :placeholder="formData.email ? '' : '暂无数据'" />
          </div>
          <div class="form-group">
            <label>昵称</label>
            <input type="text" v-model="formData.nickname" class="form-input" maxlength="50" :placeholder="formData.nickname ? '' : '暂无数据'" />
          </div>
          <div class="form-group">
            <label>个人简介</label>
            <textarea v-model="formData.bio" class="form-textarea" rows="3" maxlength="500" :placeholder="formData.bio ? '' : '暂无数据'"></textarea>
            <small class="form-hint">{{ (formData.bio || '').length }}/500</small>
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
import { useRouter } from 'vue-router'
import { useUserStore } from '../stores/user'
import { UserService, type UpdateProfileRequest, type UserStats, type CheckinResponse, type CheckinStatus } from '../services/user'
import { ImageUploadService } from '../services/utils'
import { showSuccess, showError } from '../utils/errorHandler'
import { formatRelativeTime } from '../utils/utils'
import { handleImageError, errImg } from '@/composables/useImageFallback'
import CheckinCard from '../components/CheckinCard.vue'
import Icon from '../components/Icon.vue'

const userStore = useUserStore()
const router = useRouter()
const isLoading = ref(false)
const showEditForm = ref(false)
const userStats = ref<UserStats | null>(null)
const checkinStatus = ref<CheckinStatus | null>(null)

const formData = reactive<UpdateProfileRequest>({
  email: '',
  nickname: '',
  bio: '',
  avatarUrl: ''
})

// 头像上传相关状态
const avatarInput = ref<HTMLInputElement | null>(null)
const avatarUploading = ref(false)
const avatarDragOver = ref(false)
const avatarMode = ref<'upload' | 'url'>('upload')

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

// 动态成就列表 - 基于真实数据计算
const achievements = computed(() => {
  const stats = userStats.value
  const consecutive = checkinStatus.value?.consecutiveDays || 0
  const totalCheckins = checkinStatus.value?.totalCheckins || 0
  const points = stats?.points || 0
  const commentCount = stats?.commentCount || 0
  const favoriteCount = stats?.favoriteCount || 0

  return [
    {
      name: '初来乍到',
      icon: 'user',
      locked: false, // 注册即解锁
      description: '完成注册，加入平台'
    },
    {
      name: '藏书达人',
      icon: 'book',
      locked: favoriteCount < 1,
      description: '收藏第一篇文章'
    },
    {
      name: '热心观众',
      icon: 'message',
      locked: commentCount < 10,
      description: '累计发表 10 条评论',
      progress: Math.min(commentCount, 10),
      total: 10
    },
    {
      name: '积分达人',
      icon: 'star',
      locked: points < 100,
      description: '累计获得 100 积分',
      progress: Math.min(points, 100),
      total: 100
    },
    {
      name: '签到达人',
      icon: 'calendar',
      locked: totalCheckins < 30,
      description: '累计签到 30 天',
      progress: Math.min(totalCheckins, 30),
      total: 30
    },
    {
      name: '坚持不懈',
      icon: 'fire',
      locked: consecutive < 7,
      description: '连续签到 7 天',
      progress: Math.min(consecutive, 7),
      total: 7
    },
    {
      name: '收藏家',
      icon: 'heart',
      locked: favoriteCount < 10,
      description: '收藏 10 篇文章',
      progress: Math.min(favoriteCount, 10),
      total: 10
    },
    {
      name: '积分大师',
      icon: 'trophy',
      locked: points < 500,
      description: '累计获得 500 积分',
      progress: Math.min(points, 500),
      total: 500
    }
  ]
})

// 动态时间线 - 基于真实数据生成
const timelineItems = computed(() => {
  const stats = userStats.value
  const items: { text: string; time: string; icon: string }[] = []

  if (stats?.lastPostAt) {
    items.push({ text: '发布了新文章', time: formatRelativeTime(stats.lastPostAt), icon: 'pen' })
  }
  if (stats?.lastCommentAt) {
    items.push({ text: '发表了评论', time: formatRelativeTime(stats.lastCommentAt), icon: 'message' })
  }
  if (checkinStatus.value?.lastCheckinDate) {
    items.push({ text: '完成签到', time: formatRelativeTime(checkinStatus.value.lastCheckinDate), icon: 'calendar' })
  }
  if (userInfo.value?.createdAt) {
    items.push({ text: '加入了平台', time: formatRelativeTime(userInfo.value.createdAt), icon: 'user' })
  }

  return items
})

const initForm = () => {
  if (userInfo.value) {
    formData.email = userInfo.value.email || ''
    formData.nickname = userInfo.value.nickname || ''
    formData.bio = userInfo.value.bio || ''
    formData.avatarUrl = userInfo.value.avatarUrl || ''
  }
}

const openEditForm = async () => {
  // 每次打开都强制拉取最新用户信息，避免 persist 缓存导致表单数据过期
  try {
    await userStore.fetchUserInfo(true)
  } catch {
    // 拉取失败时用现有 userInfo 兜底，不阻塞打开
  }
  initForm()
  showEditForm.value = true
}

const resetForm = () => {
  initForm()
  avatarMode.value = 'upload'
  avatarDragOver.value = false
}

const closeModal = () => { showEditForm.value = false; resetForm() }

// 头像上传：校验 + 提交
const validateAvatarFile = (file: File): string | null => {
  if (!file.type.startsWith('image/')) return '请选择图片文件'
  if (!/^image\/(png|jpe?g|gif|webp)$/i.test(file.type)) return '仅支持 PNG / JPG / GIF / WEBP 格式'
  if (file.size > 5 * 1024 * 1024) return '图片大小不能超过 5MB'
  return null
}

const uploadAvatar = async (file: File) => {
  const err = validateAvatarFile(file)
  if (err) {
    showError(err)
    return
  }
  avatarUploading.value = true
  try {
    const result = await ImageUploadService.uploadAvatar(file)
    formData.avatarUrl = result.fileUrl
    showSuccess('头像上传成功')
  } catch (error: any) {
    showError(error?.message || '头像上传失败')
  } finally {
    avatarUploading.value = false
  }
}

const triggerAvatarUpload = () => {
  if (avatarUploading.value) return
  avatarInput.value?.click()
}

const handleAvatarChange = async (event: Event) => {
  const target = event.target as HTMLInputElement
  const file = target.files?.[0]
  if (file) await uploadAvatar(file)
  target.value = ''
}

const handleAvatarDrop = async (event: DragEvent) => {
  avatarDragOver.value = false
  const file = event.dataTransfer?.files?.[0]
  if (file) await uploadAvatar(file)
}

const handleAvatarDragOver = (event: DragEvent) => {
  event.preventDefault()
  if (avatarUploading.value) return
  avatarDragOver.value = true
}

const handleAvatarDragLeave = () => {
  avatarDragOver.value = false
}

const handleSubmit = async () => {
  if (!formData.email) return
  isLoading.value = true
  try {
    const updatedUser = await UserService.updateProfile(formData)
    userStore.updateUserInfo(updatedUser)
    showSuccess('更新成功')
    closeModal()
  } catch (error: any) {
    // 业务错误（如邮箱被占用）已在拦截器 Toast 提示具体原因，这里不重复弹模态框
    if (!error?.isBusiness) {
      showError('更新失败')
    }
  } finally {
    isLoading.value = false
  }
}

const loadUserStats = async () => {
  if (!userStore.isLoggedIn) return
  try {
    const [stats, checkin] = await Promise.all([
      UserService.getUserStats(),
      UserService.getCheckinStatus()
    ])
    userStats.value = stats
    checkinStatus.value = checkin
  } catch {
    // 加载失败时静默处理
  }
}

const handleCheckinSuccess = (result: CheckinResponse) => {
  if (userStats.value) userStats.value.points = result.totalPoints
  if (checkinStatus.value) {
    checkinStatus.value.consecutiveDays = result.consecutiveDays
    checkinStatus.value.hasCheckedInToday = true
    checkinStatus.value.totalCheckins++
  }
}

onMounted(() => {
  if (!userStore.isLoggedIn) {
    router.push('/login')
    return
  }
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
  color: var(--text-on-primary, #fff);
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
  color: var(--text-on-primary, #fff);
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
  // padding: 40px 0 60px;
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
  cursor: default;

  &:hover {
    transform: translateY(-1px);
  }

  &.locked {
    opacity: 0.4;
  }
}

.badge-icon {
  font-size: 1.25rem;
  flex-shrink: 0;
}

.badge-info {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.badge-name {
  font-size: 0.9rem;
  color: var(--text-main);
}

.badge-progress {
  font-size: 0.7rem;
  color: var(--text-muted);
  margin-top: 2px;
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

.timeline-icon {
  color: var(--color-primary);
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
  display: flex;
  flex-direction: column;
  align-items: center;
  color: var(--text-muted);
  font-size: 0.9rem;
  text-align: center;
  padding: 20px;
}

/* 模态框 */
.modal-overlay {
  position: fixed;
  inset: 0;
  background: var(--overlay-bg-strong);
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

.avatar-form-group {
  margin-bottom: 20px;
}

.avatar-upload-row {
  display: flex;
  align-items: center;
  gap: 16px;
}

.avatar-dropzone {
  position: relative;
  width: 96px;
  height: 96px;
  border-radius: 50%;
  flex-shrink: 0;
  cursor: pointer;
  overflow: hidden;
  border: 2px dashed var(--border-base);
  background: var(--bg-soft);
  display: flex;
  align-items: center;
  justify-content: center;
  transition: border-color 0.2s, background 0.2s, transform 0.2s;

  &:hover {
    border-color: var(--color-primary);
    transform: scale(1.02);
  }

  &.is-dragover {
    border-color: var(--color-primary);
    background: var(--bg-hover);
    transform: scale(1.04);
  }

  &.is-uploading {
    cursor: wait;
    border-style: solid;
    border-color: var(--color-primary);
  }
}

.avatar-preview {
  width: 100%;
  height: 100%;
  object-fit: cover;
  border-radius: 50%;
}

.avatar-placeholder {
  color: var(--text-muted);
  display: flex;
  align-items: center;
  justify-content: center;
}

.avatar-loading {
  position: absolute;
  inset: 0;
  background: rgba(0, 0, 0, 0.45);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
}

.spinner {
  width: 22px;
  height: 22px;
  border: 2px solid rgba(255, 255, 255, 0.35);
  border-top-color: #fff;
  border-radius: 50%;
  animation: avatar-spin 0.7s linear infinite;
}

@keyframes avatar-spin {
  to { transform: rotate(360deg); }
}

.avatar-overlay-hint {
  position: absolute;
  inset: 0;
  background: rgba(0, 0, 0, 0.5);
  color: #fff;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 2px;
  font-size: 0.7rem;
  opacity: 0;
  transition: opacity 0.2s;
  border-radius: 50%;

  .avatar-dropzone:hover & {
    opacity: 1;
  }
}

.avatar-actions {
  display: flex;
  flex-direction: column;
  gap: 6px;
  flex: 1;
}

.avatar-mode-toggle {
  background: none;
  border: none;
  color: var(--color-primary);
  font-size: 0.8rem;
  cursor: pointer;
  padding: 0;
  text-align: left;
  width: fit-content;

  &:hover {
    text-decoration: underline;
  }
}

.avatar-hint {
  font-size: 0.72rem;
  color: var(--text-muted);
}

.hidden-input {
  display: none;
}

.avatar-url-input {
  margin-top: 10px;
}

.btn-sm {
  padding: 6px 14px;
  font-size: 0.82rem;
  width: fit-content;
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
  color: var(--text-on-primary, #fff);

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
