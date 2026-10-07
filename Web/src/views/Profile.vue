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
        </div>

        <!-- 用户信息 -->
        <div class="user-info">
          <div class="user-header">
            <h1 class="username">{{ userInfo?.nickname || userInfo?.username || 'Liuxin' }}</h1>
          </div>
          <p class="user-bio">{{ userInfo?.bio || '这个人很懒，什么都没有留下...' }}</p>
        </div>

        <p v-if="statsLoading" role="status">正在加载统计...</p>
        <p v-if="statsError" role="alert">{{ statsError }} <button @click="loadUserStats">重试</button></p>
        <!-- 统计 -->
        <div class="stats-section">
          <div class="stats-row">
            <div class="stat-item">
              <span class="stat-value">{{ userStats?.viewCount ?? '—' }}</span>
              <span class="stat-label">已浏览</span>
            </div>
            <span class="stat-divider">·</span>
            <div class="stat-item">
              <span class="stat-value">{{ userStats?.commentCount ?? '—' }}</span>
              <span class="stat-label">评论</span>
            </div>
            <span class="stat-divider">·</span>
            <div class="stat-item">
              <span class="stat-value">{{ userStats?.favoriteCount ?? '—' }}</span>
              <span class="stat-label">收藏</span>
            </div>
            <span class="stat-divider">·</span>
            <div class="stat-item">
              <span class="stat-value">{{ userStats?.points ?? '—' }}</span>
              <span class="stat-label">积分</span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 主要内容 -->
    <div class="main-content">
      <div class="content">
        <!-- 下方卡片 -->
        <div class="content-grid">
          <div class="section-card">
            <div class="section-header">我的内容</div>
            <ProfileLibrary />
          </div>

          <!-- 动态 -->
          <div class="section-card">
            <div class="section-header activity-header">
              <span>最近动态</span>
              <button class="history-clear" :disabled="clearingHistory" @click="clearHistory">{{ clearingHistory ? '清空中…' : '清空浏览记录' }}</button>
            </div>
            <p class="activity-hint">浏览、收藏、评论与签到的最近记录。清空浏览不会删除收藏或评论。</p>
            <UserActivities ref="activitiesRef" />
          </div>
        </div>
        <details class="profile-checkin" @toggle="showCheckin = ($event.target as HTMLDetailsElement).open">
          <summary>签到与积分 <small>每日签到 · 查看签到记录</small></summary>
          <CheckinCard v-if="showCheckin" @checkin-success="handleCheckinSuccess" />
        </details>
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
            <input type="text" v-model="formData.username" class="form-input" maxlength="20" placeholder="请输入用户名" />
            <small class="form-hint">用户名是登录账号，3-20 位，修改后需重新登录</small>
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
import { ref, reactive, computed, onMounted, onScopeDispose, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '../stores/user'
import { UserService, type UpdateProfileRequest, type UserStats, type CheckinResponse } from '../services/user'
import { ImageUploadService } from '../services/utils'
import { showSuccess, showError } from '../utils/errorHandler'
import ProfileLibrary from '@/components/ProfileLibrary.vue'
import PostService from '@/services/post'
import { usePostInteractionStore } from '@/stores/postInteraction'
import { useErrorHandler } from '@/composables/useErrorHandler'
import UserActivities from '@/components/UserActivities.vue'
import { handleImageError, errImg } from '@/composables/useImageFallback'
import CheckinCard from '../components/CheckinCard.vue'
import Icon from '../components/Icon.vue'

const userStore = useUserStore()
const router = useRouter()
const interaction = usePostInteractionStore()
const { confirm } = useErrorHandler()
const clearingHistory = ref(false)
const showCheckin = ref(false)
const isLoading = ref(false)
const showEditForm = ref(false)
const userStats = ref<UserStats | null>(null)
const statsLoading = ref(false)
const statsError = ref('')
const activitiesRef = ref<InstanceType<typeof UserActivities> | null>(null)
let statsGeneration = 0

const formData = reactive<UpdateProfileRequest>({
  username: '',
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

const initForm = () => {
  if (userInfo.value) {
    formData.username = userInfo.value.username || ''
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
  // 用户名规则与注册一致：3-20 位
  const username = (formData.username || '').trim()
  if (username.length < 3 || username.length > 20) {
    showError('用户名长度必须在3-20之间')
    return
  }
  const usernameChanged = username !== userStore.userInfo?.username
  isLoading.value = true
  try {
    const updatedUser = await UserService.updateProfile({ ...formData, username })
    userStore.updateUserInfo(updatedUser)
    if (usernameChanged) {
      // 用户名是登录账号，JWT 中已绑定旧用户名，修改后旧 token 失效，需重新登录
      userStore.logout()
      showSuccess('用户名修改成功，请重新登录')
      router.push('/login')
      return
    }
    showSuccess('更新成功')
    closeModal()
  } catch (error: any) {
    // 业务错误（如用户名/邮箱被占用）已在拦截器 Toast 提示具体原因，这里不重复弹模态框
    if (!error?.isBusiness) {
      showError('更新失败')
    }
  } finally {
    isLoading.value = false
  }
}

const loadUserStats = async () => {
  if (!userStore.isLoggedIn) return
  const token = ++statsGeneration
  statsLoading.value = true
  statsError.value = ''
  try {
    const stats = await UserService.getUserStats()
    if (token !== statsGeneration) return
    userStats.value = stats
  } catch {
    if (token !== statsGeneration) return
    userStats.value = null
    statsError.value = '统计加载失败，请重试'
  } finally { if (token === statsGeneration) statsLoading.value = false }
}
const clearHistory = async () => {
  if (clearingHistory.value) return
  if (!await confirm('确定清空全部浏览记录吗？收藏、评论和已购资源会保留。')) return
  clearingHistory.value = true
  try {
    await PostService.clearViewHistory()
    showSuccess('浏览记录已清空')
  } catch (error: any) {
    if (!error?.isBusiness) showError('清空失败，请稍后重试')
  } finally {
    clearingHistory.value = false
  }
}

watch(() => [userStore.isLoggedIn, userInfo.value?.id, interaction.historyRevision], () => {
  statsGeneration++
  userStats.value = null
  statsLoading.value = false
  statsError.value = ''
  void loadUserStats()
}, { immediate: true })

const handleCheckinSuccess = (result: CheckinResponse) => {
  void activitiesRef.value?.refresh()
  if (userStats.value) userStats.value.points = result.totalPoints
}

onMounted(() => {
  if (!userStore.isLoggedIn) {
    router.push('/login')
    return
  }
  initForm()
})
onScopeDispose(() => { statsGeneration++ })
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
  grid-template-columns: minmax(0, 1.35fr) minmax(0, 1fr);
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

.activity-header { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.history-clear { border: none; background: none; color: var(--color-primary); cursor: pointer; font-size: 12px; }
.history-clear:disabled { opacity: .5; cursor: default; }
.activity-hint { margin: 0 0 8px; color: var(--text-muted); font-size: 12px; line-height: 1.7; }

.profile-checkin { margin-top: 20px; }
.profile-checkin summary { cursor: pointer; padding: 16px 20px; border: 1px solid var(--border-base); border-radius: 12px; background: var(--bg-card); color: var(--text-title); font-size: 14px; }
.profile-checkin small { color: var(--text-muted); font-size: 12px; margin-left: 8px; }
.profile-checkin[open] summary { margin-bottom: 12px; }

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
    padding: 0 12px;
  }

  .profile-inner {
    padding: 24px 16px;
  }

  .stats-row {
    width: 100%;
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 16px;
  }

  .stat-divider { display: none; }
  .stat-item { padding: 0; }
}
</style>
