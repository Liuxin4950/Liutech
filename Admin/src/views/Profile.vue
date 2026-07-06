<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import { message } from 'ant-design-vue'
import { UserOutlined, UploadOutlined, LockOutlined } from '@ant-design/icons-vue'
import { useUserStore } from '../stores/user'
import UserService from '../services/user'
import { ImageUploadService } from '../services/upload'

const userStore = useUserStore()

// 个人资料表单
const profileForm = reactive({
  email: '',
  nickname: '',
  bio: '',
  avatarUrl: ''
})
const profileLoading = ref(false)
const avatarUploading = ref(false)
const avatarPreview = ref('')

// 密码表单
const passwordFormRef = ref()
const passwordForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})
const passwordLoading = ref(false)

const passwordRules = {
  oldPassword: [{ required: true, message: '请输入原密码' }],
  newPassword: [
    { required: true, message: '请输入新密码' },
    { min: 6, max: 20, message: '密码长度必须在6-20位之间' }
  ],
  confirmPassword: [
    { required: true, message: '请确认新密码' },
    {
      validator: (_rule: any, value: string) => {
        if (value && value !== passwordForm.newPassword) {
          return Promise.reject('两次输入的密码不一致')
        }
        return Promise.resolve()
      }
    }
  ]
}

/**
 * 密码强度评估（0-4）：位数 + 字符类型多样性
 * 0: 空 / 太短
 * 1: 弱（只有单一类型或过短）
 * 2: 一般（两种类型）
 * 3: 强（三种类型）
 * 4: 很强（四种类型 + 足够长度）
 */
const passwordStrength = computed(() => {
  const pwd = passwordForm.newPassword
  if (!pwd || pwd.length < 6) return 0
  let score = 0
  if (/[a-z]/.test(pwd)) score++
  if (/[A-Z]/.test(pwd)) score++
  if (/\d/.test(pwd)) score++
  if (/[^a-zA-Z0-9]/.test(pwd)) score++
  if (pwd.length >= 12) score = Math.min(score + 1, 4)
  return Math.min(score, 4)
})

const strengthMeta = computed(() => {
  const map = [
    { label: '', color: 'transparent', percent: 0 },
    { label: '弱', color: 'var(--lt-color-error)', percent: 25 },
    { label: '一般', color: 'var(--lt-color-warning)', percent: 50 },
    { label: '强', color: 'var(--lt-color-success)', percent: 75 },
    { label: '很强', color: 'var(--lt-color-success)', percent: 100 },
  ]
  return map[passwordStrength.value]
})

const loadUserInfo = () => {
  if (userStore.userInfo) {
    profileForm.email = userStore.userInfo.email || ''
    profileForm.nickname = userStore.userInfo.nickname || ''
    profileForm.bio = (userStore.userInfo as any).bio || ''
    profileForm.avatarUrl = userStore.userInfo.avatar || ''
    avatarPreview.value = userStore.userInfo.avatar || ''
  }
}

/** 头像上传前的前端校验：类型 + 大小 */
const beforeAvatarUpload = (file: File): boolean => {
  const isImage = /^image\/(png|jpe?g|gif|webp)$/i.test(file.type)
  if (!isImage) {
    message.error('仅支持 PNG / JPG / GIF / WEBP 格式')
    return false
  }
  const isLt2M = file.size / 1024 / 1024 < 2
  if (!isLt2M) {
    message.error('图片大小不能超过 2MB')
    return false
  }
  return true
}

const handleAvatarChange = async (info: any) => {
  const file = info.fileList?.[0]?.originFileObj || info.file?.originFileObj
  if (!file) return
  if (!beforeAvatarUpload(file)) return

  avatarPreview.value = URL.createObjectURL(file)

  try {
    avatarUploading.value = true
    const result = await ImageUploadService.uploadImage(file)
    profileForm.avatarUrl = result.fileUrl
    message.success('头像上传成功')
  } catch (e: any) {
    message.error(e.message || '头像上传失败')
    avatarPreview.value = profileForm.avatarUrl || ''
  } finally {
    avatarUploading.value = false
  }
}

const handleSaveProfile = async () => {
  try {
    profileLoading.value = true
    const res = await UserService.updateProfile({
      email: profileForm.email,
      nickname: profileForm.nickname,
      bio: profileForm.bio,
      avatar: profileForm.avatarUrl
    } as any)
    if (res.code === 200) {
      message.success('个人资料更新成功')
      await userStore.fetchUserInfo()
    } else {
      message.error(res.message || '更新失败')
    }
  } catch (e: any) {
    message.error(e.message || '更新失败')
  } finally {
    profileLoading.value = false
  }
}

const handleChangePassword = async () => {
  try {
    await passwordFormRef.value?.validate?.()
    passwordLoading.value = true
    const res = await UserService.changePassword({
      oldPassword: passwordForm.oldPassword,
      newPassword: passwordForm.newPassword,
      confirmPassword: passwordForm.confirmPassword
    })
    if (res.code === 200) {
      message.success('密码修改成功')
      passwordForm.oldPassword = ''
      passwordForm.newPassword = ''
      passwordForm.confirmPassword = ''
    } else {
      message.error(res.message || '密码修改失败')
    }
  } catch (e: any) {
    if (e?.errorFields) return
    message.error(e.message || '密码修改失败')
  } finally {
    passwordLoading.value = false
  }
}

onMounted(() => {
  loadUserInfo()
})
</script>

<template>
  <div class="p-24">
    <a-row :gutter="[16, 16]">
      <!-- 左侧：个人资料 -->
      <a-col :xs="24" :md="24" :lg="16">
        <a-card title="个人资料" :bordered="false">
          <a-form layout="vertical">
            <a-form-item label="头像">
              <div class="avatar-section">
                <a-avatar :size="80" :src="avatarPreview">
                  <template #icon><UserOutlined /></template>
                </a-avatar>
                <div class="avatar-actions">
                  <a-upload
                    name="file"
                    :show-upload-list="false"
                    accept="image/png,image/jpeg,image/gif,image/webp"
                    :before-upload="() => false"
                    @change="handleAvatarChange"
                  >
                    <a-button :loading="avatarUploading">
                      <UploadOutlined />
                      更换头像
                    </a-button>
                  </a-upload>
                  <div class="avatar-hint">支持 PNG / JPG / GIF / WEBP，大小不超过 2MB</div>
                </div>
              </div>
            </a-form-item>

            <a-form-item label="用户名">
              <a-input :value="userStore.userInfo?.username" disabled />
            </a-form-item>

            <a-form-item label="邮箱">
              <a-input v-model:value="profileForm.email" placeholder="请输入邮箱" />
            </a-form-item>

            <a-form-item label="昵称">
              <a-input v-model:value="profileForm.nickname" placeholder="请输入昵称" />
            </a-form-item>

            <a-form-item label="个人简介">
              <a-textarea
                v-model:value="profileForm.bio"
                placeholder="介绍一下自己..."
                :rows="4"
                :maxlength="500"
                show-count
              />
            </a-form-item>

            <a-form-item>
              <a-button type="primary" :loading="profileLoading" @click="handleSaveProfile">
                保存修改
              </a-button>
            </a-form-item>
          </a-form>
        </a-card>
      </a-col>

      <!-- 右侧：修改密码 -->
      <a-col :xs="24" :md="24" :lg="8">
        <a-card title="修改密码" :bordered="false">
          <a-form
            ref="passwordFormRef"
            :model="passwordForm"
            :rules="passwordRules"
            layout="vertical"
          >
            <a-form-item label="原密码" name="oldPassword">
              <a-input-password
                v-model:value="passwordForm.oldPassword"
                placeholder="请输入原密码"
              >
                <template #prefix><LockOutlined /></template>
              </a-input-password>
            </a-form-item>

            <a-form-item label="新密码" name="newPassword">
              <a-input-password
                v-model:value="passwordForm.newPassword"
                placeholder="请输入新密码（6-20位）"
              >
                <template #prefix><LockOutlined /></template>
              </a-input-password>
              <!-- 密码强度指示 -->
              <div v-if="passwordForm.newPassword" class="pwd-strength">
                <div class="pwd-strength__bar">
                  <div
                    class="pwd-strength__fill"
                    :style="{ width: strengthMeta.percent + '%', background: strengthMeta.color }"
                  />
                </div>
                <span class="pwd-strength__label" :style="{ color: strengthMeta.color }">
                  {{ strengthMeta.label }}
                </span>
              </div>
            </a-form-item>

            <a-form-item label="确认新密码" name="confirmPassword">
              <a-input-password
                v-model:value="passwordForm.confirmPassword"
                placeholder="请再次输入新密码"
              >
                <template #prefix><LockOutlined /></template>
              </a-input-password>
            </a-form-item>

            <a-form-item>
              <a-button
                type="primary"
                :loading="passwordLoading"
                @click="handleChangePassword"
              >
                修改密码
              </a-button>
            </a-form-item>
          </a-form>
        </a-card>
      </a-col>
    </a-row>
  </div>
</template>

<style scoped>
.avatar-section {
  display: flex;
  align-items: center;
  gap: var(--lt-space-lg);
}

.avatar-actions {
  display: flex;
  flex-direction: column;
  gap: var(--lt-space-xs);
}

.avatar-hint {
  font-size: var(--lt-font-size-xs);
  color: var(--lt-color-text-tertiary);
}

.pwd-strength {
  margin-top: var(--lt-space-xs);
  display: flex;
  align-items: center;
  gap: var(--lt-space-sm);
}

.pwd-strength__bar {
  flex: 1;
  height: 4px;
  background: var(--lt-color-bg-spotlight);
  border-radius: var(--lt-radius-pill);
  overflow: hidden;
}

.pwd-strength__fill {
  height: 100%;
  transition: width var(--lt-duration-base) var(--lt-ease-in-out),
              background var(--lt-duration-base) var(--lt-ease-in-out);
}

.pwd-strength__label {
  font-size: var(--lt-font-size-xs);
  font-weight: var(--lt-font-weight-medium);
  min-width: 32px;
  text-align: right;
  font-variant-numeric: tabular-nums;
}
</style>
