<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
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

// 加载用户信息
const loadUserInfo = () => {
  if (userStore.userInfo) {
    profileForm.email = userStore.userInfo.email || ''
    profileForm.nickname = userStore.userInfo.nickname || ''
    profileForm.bio = (userStore.userInfo as any).bio || ''
    profileForm.avatarUrl = userStore.userInfo.avatar || ''
    avatarPreview.value = userStore.userInfo.avatar || ''
  }
}

// 头像上传
const handleAvatarChange = async (info: any) => {
  const file = info.fileList?.[0]?.originFileObj || info.file?.originFileObj
  if (!file) return

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

// 保存个人资料
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
      // 刷新 store 中的用户信息
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

// 修改密码
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
    if (e?.errorFields) {
      // 表单校验失败，不显示额外错误
      return
    }
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
    <a-row :gutter="24">
      <!-- 左侧：个人资料 -->
      <a-col :span="16">
        <a-card title="个人资料" :bordered="false">
          <a-form layout="vertical">
            <!-- 头像 -->
            <a-form-item label="头像">
              <div class="avatar-section">
                <a-avatar :size="80" :src="avatarPreview">
                  <template #icon>
                    <UserOutlined />
                  </template>
                </a-avatar>
                <a-upload
                  name="file"
                  :show-upload-list="false"
                  accept="image/*"
                  :before-upload="() => false"
                  @change="handleAvatarChange"
                >
                  <a-button :loading="avatarUploading" style="margin-left: 16px">
                    <UploadOutlined />
                    更换头像
                  </a-button>
                </a-upload>
              </div>
            </a-form-item>

            <!-- 用户名（只读） -->
            <a-form-item label="用户名">
              <a-input :value="userStore.userInfo?.username" disabled />
            </a-form-item>

            <!-- 邮箱 -->
            <a-form-item label="邮箱">
              <a-input v-model:value="profileForm.email" placeholder="请输入邮箱" />
            </a-form-item>

            <!-- 昵称 -->
            <a-form-item label="昵称">
              <a-input v-model:value="profileForm.nickname" placeholder="请输入昵称" />
            </a-form-item>

            <!-- 个人简介 -->
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
      <a-col :span="8">
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
                <template #prefix>
                  <LockOutlined />
                </template>
              </a-input-password>
            </a-form-item>

            <a-form-item label="新密码" name="newPassword">
              <a-input-password
                v-model:value="passwordForm.newPassword"
                placeholder="请输入新密码（6-20位）"
              >
                <template #prefix>
                  <LockOutlined />
                </template>
              </a-input-password>
            </a-form-item>

            <a-form-item label="确认新密码" name="confirmPassword">
              <a-input-password
                v-model:value="passwordForm.confirmPassword"
                placeholder="请再次输入新密码"
              >
                <template #prefix>
                  <LockOutlined />
                </template>
              </a-input-password>
            </a-form-item>

            <a-form-item>
              <a-button
                type="primary"
                danger
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
}
</style>
