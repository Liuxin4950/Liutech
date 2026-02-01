<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { message } from 'ant-design-vue'
import { ReloadOutlined, SaveOutlined } from '@ant-design/icons-vue'
import { getTtsConfig, getTtsStatus, updateTtsConfig, type TtsConfigDTO, type TtsStatusDTO } from '@/services/tts'

const loading = ref(false)
const saving = ref(false)

const config = ref<TtsConfigDTO>({
  enabled: true,
  baseUrl: ''
})

const status = ref<TtsStatusDTO | null>(null)

const statusText = computed(() => {
  if (!status.value) return '未检测'
  const s = status.value
  if (!s.enabled) return s.message || '已关闭'
  return s.online ? (s.message || '在线') : (s.message || '离线')
})

const statusType = computed(() => {
  if (!status.value) return 'info'
  if (!status.value.enabled) return 'warning'
  return status.value.online ? 'success' : 'error'
})

const refresh = async () => {
  if (loading.value) return
  loading.value = true
  try {
    const [cfg, st] = await Promise.all([getTtsConfig(), getTtsStatus()])
    config.value.enabled = cfg.enabled
    config.value.baseUrl = cfg.baseUrl || ''
    status.value = st
  } catch (e: any) {
    message.error('加载语音配置失败')
  } finally {
    loading.value = false
  }
}

const save = async () => {
  if (saving.value) return
  saving.value = true
  try {
    await updateTtsConfig({
      enabled: config.value.enabled,
      baseUrl: config.value.baseUrl?.trim() || ''
    })
    message.success('保存成功')
    await refresh()
  } catch (e: any) {
    message.error('保存失败')
  } finally {
    saving.value = false
  }
}

onMounted(() => {
  refresh()
})
</script>

<template>
  <div style="padding: 16px 24px;">
    <a-card title="语音推理配置（TTS）" :loading="loading">
      <a-alert
        style="margin-bottom: 16px;"
        :type="statusType as any"
        show-icon
        :message="`当前状态：${statusText}`"
        :description="status?.baseUrl ? `服务地址：${status.baseUrl}` : '服务地址：未配置'"
      />

      <a-form layout="vertical">
        <a-form-item label="全局开关（关闭后前台无法开启语音）">
          <a-switch v-model:checked="config.enabled" />
        </a-form-item>

        <a-form-item label="语音推理服务基础地址（例如：http://127.0.0.1:8000）">
          <a-input v-model:value="config.baseUrl" placeholder="http://127.0.0.1:8000" allow-clear />
        </a-form-item>

        <a-space>
          <a-button :loading="saving" type="primary" @click="save">
            <template #icon><SaveOutlined /></template>
            保存
          </a-button>
          <a-button :loading="loading" @click="refresh">
            <template #icon><ReloadOutlined /></template>
            刷新状态
          </a-button>
        </a-space>
      </a-form>
    </a-card>
  </div>
</template>

