<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { message } from 'ant-design-vue'
import { ReloadOutlined, SaveOutlined, RobotOutlined, SoundOutlined, ApiOutlined, CheckCircleOutlined } from '@ant-design/icons-vue'
import aiModelsService, { type ModelConfig } from '@/services/aiModels'
import { getAiRuntime, type AiRuntimeDTO } from '@/services/aiRuntime'
import { getTtsConfig, getTtsStatus, getTtsVoices, updateTtsConfig, type TtsConfigDTO, type TtsStatusDTO } from '@/services/tts'

const loading = ref(false)
const saving = ref(false)
const runtime = ref<AiRuntimeDTO | null>(null)
const ttsStatus = ref<TtsStatusDTO | null>(null)
const modelOptions = ref<ModelConfig[]>([])
const voiceOptions = ref<string[]>([])

const form = ref<TtsConfigDTO & { defaultModelId: number | null }>({
  enabled: true,
  baseUrl: '',
  voiceModel: '',
  defaultModelId: null
})

const selectedDefaultModel = computed(() =>
  modelOptions.value.find(item => item.id === form.value.defaultModelId) || null
)

const enabledModelCount = computed(() =>
  modelOptions.value.filter(item => item.isEnabled).length
)

const currentStatusText = computed(() => {
  if (!ttsStatus.value) return '未检测'
  if (!ttsStatus.value.enabled) return ttsStatus.value.message || '已关闭'
  return ttsStatus.value.online ? (ttsStatus.value.message || '在线') : (ttsStatus.value.message || '离线')
})

const refreshVoices = async () => {
  if (!form.value.baseUrl) {
    voiceOptions.value = []
    return
  }
  try {
    voiceOptions.value = await getTtsVoices(form.value.baseUrl || '')
    if (voiceOptions.value.length > 0 && !voiceOptions.value.includes(form.value.voiceModel || '')) {
      form.value.voiceModel = voiceOptions.value[0]
    }
  } catch {
    voiceOptions.value = []
  }
}

const refresh = async () => {
  if (loading.value) return
  loading.value = true
  try {
    const [runtimeData, ttsConfig, ttsRuntimeStatus, models] = await Promise.all([
      getAiRuntime(),
      getTtsConfig(),
      getTtsStatus(),
      aiModelsService.getModelList()
    ])

    runtime.value = runtimeData
    ttsStatus.value = ttsRuntimeStatus
    modelOptions.value = models

    const currentDefaultModel = models.find(item => item.isDefault) || null
    form.value = {
      enabled: ttsConfig.enabled,
      baseUrl: ttsConfig.baseUrl || '',
      voiceModel: ttsConfig.voiceModel || '',
      defaultModelId: currentDefaultModel?.id || null
    }

    await refreshVoices()
  } catch (error: any) {
    message.error(error?.message || '加载 AI 设置失败')
  } finally {
    loading.value = false
  }
}

const save = async () => {
  if (saving.value) return
  saving.value = true
  try {
    await updateTtsConfig({
      enabled: form.value.enabled,
      baseUrl: form.value.baseUrl?.trim() || '',
      voiceModel: form.value.voiceModel?.trim() || ''
    })

    const currentDefault = modelOptions.value.find(item => item.isDefault)
    if (form.value.defaultModelId && currentDefault?.id !== form.value.defaultModelId) {
      await aiModelsService.setDefaultModel(form.value.defaultModelId)
    }

    message.success('AI 设置已保存')
    await refresh()
  } catch (error: any) {
    message.error(error?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

onMounted(() => {
  refresh()
})
</script>

<template>
  <div class="page-container">
    <a-row :gutter="[16, 16]" class="mb-16">
      <a-col :xs="24" :sm="12" :lg="6">
        <a-card :bordered="false" class="stat-card">
          <div class="stat-row">
            <div class="stat-icon bg-blue">
              <ApiOutlined />
            </div>
            <div>
              <div class="stat-label">AI 服务</div>
              <div class="stat-value">{{ runtime?.aiOnline ? '在线' : '离线' }}</div>
              <div class="stat-sub">{{ runtime?.aiMessage || '未检测' }}</div>
            </div>
          </div>
        </a-card>
      </a-col>
      <a-col :xs="24" :sm="12" :lg="6">
        <a-card :bordered="false" class="stat-card">
          <div class="stat-row">
            <div class="stat-icon bg-green">
              <RobotOutlined />
            </div>
            <div>
              <div class="stat-label">默认模型</div>
              <div class="stat-value compact">{{ runtime?.defaultModel || '未设置' }}</div>
              <div class="stat-sub">已启用 {{ enabledModelCount }} 个模型</div>
            </div>
          </div>
        </a-card>
      </a-col>
      <a-col :xs="24" :sm="12" :lg="6">
        <a-card :bordered="false" class="stat-card">
          <div class="stat-row">
            <div class="stat-icon bg-orange">
              <SoundOutlined />
            </div>
            <div>
              <div class="stat-label">TTS 状态</div>
              <div class="stat-value">{{ ttsStatus?.online ? '在线' : '离线' }}</div>
              <div class="stat-sub">{{ currentStatusText }}</div>
            </div>
          </div>
        </a-card>
      </a-col>
      <a-col :xs="24" :sm="12" :lg="6">
        <a-card :bordered="false" class="stat-card">
          <div class="stat-row">
            <div class="stat-icon bg-purple">
              <CheckCircleOutlined />
            </div>
            <div>
              <div class="stat-label">当前语音</div>
              <div class="stat-value compact">{{ form.voiceModel || '未设置' }}</div>
              <div class="stat-sub">{{ ttsStatus?.baseUrl || '未配置服务地址' }}</div>
            </div>
          </div>
        </a-card>
      </a-col>
    </a-row>

    <a-card :bordered="false" class="settings-card" :loading="loading">
      <template #title>AI 设置</template>
      <template #extra>
        <a-space>
          <a-button @click="refresh" :loading="loading">
            <template #icon><ReloadOutlined /></template>
            刷新
          </a-button>
          <a-button type="primary" @click="save" :loading="saving">
            <template #icon><SaveOutlined /></template>
            保存
          </a-button>
        </a-space>
      </template>

      <a-form layout="vertical">
        <a-row :gutter="16">
          <a-col :xs="24" :lg="12">
            <div class="section-title">聊天模型</div>
            <a-form-item label="默认模型">
              <a-select
                v-model:value="form.defaultModelId"
                placeholder="请选择默认模型"
                :options="modelOptions.filter(item => item.isEnabled).map(item => ({ value: item.id, label: `${item.displayName} (${item.modelName})` }))"
              />
            </a-form-item>
            <a-alert
              type="info"
              show-icon
              :message="selectedDefaultModel?.displayName || '未选择默认模型'"
              :description="selectedDefaultModel?.description || '前台聊天默认使用这里设置的模型。模型的详细参数在“AI模型”页面维护。'"
            />
          </a-col>

          <a-col :xs="24" :lg="12">
            <div class="section-title">语音推理</div>
            <a-form-item label="全局开关">
              <a-switch v-model:checked="form.enabled" />
            </a-form-item>
            <a-form-item label="TTS 服务地址">
              <a-input v-model:value="form.baseUrl" placeholder="http://127.0.0.1:8000" allow-clear />
            </a-form-item>
            <a-form-item>
              <a-button @click="refreshVoices" :disabled="!form.baseUrl">
                读取可用语音模型
              </a-button>
            </a-form-item>
            <a-form-item label="语音模型">
              <a-select
                v-model:value="form.voiceModel"
                :options="voiceOptions.map(item => ({ value: item, label: item }))"
                placeholder="请先配置服务地址并加载可用语音模型"
                allow-clear
              />
            </a-form-item>
            <a-alert
              :type="ttsStatus?.online ? 'success' : 'warning'"
              show-icon
              :message="`TTS：${currentStatusText}`"
              :description="ttsStatus?.voiceModel ? `当前生效语音：${ttsStatus.voiceModel}` : '当前未设置语音模型'"
            />
          </a-col>
        </a-row>
      </a-form>
    </a-card>
  </div>
</template>

<style scoped>
.page-container {
  padding: 24px;
  background: var(--bg-main);
  min-height: 100vh;
}

.mb-16 {
  margin-bottom: 16px;
}

.stat-card,
.settings-card {
  border-radius: 12px;
  box-shadow: 0 1px 2px 0 rgba(0, 0, 0, 0.03);
}

.stat-row {
  display: flex;
  align-items: center;
  gap: 14px;
}

.stat-icon {
  width: 44px;
  height: 44px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
}

.bg-blue { background: #eff6ff; color: #2563eb; }
.bg-green { background: #f0fdf4; color: #16a34a; }
.bg-orange { background: #fff7ed; color: #ea580c; }
.bg-purple { background: #faf5ff; color: #9333ea; }

.stat-label {
  color: #6b7280;
  font-size: 13px;
  margin-bottom: 4px;
}

.stat-value {
  font-size: 20px;
  font-weight: 700;
  color: #111827;
  line-height: 1.2;
}

.stat-value.compact {
  font-size: 14px;
}

.stat-sub {
  color: #9ca3af;
  font-size: 12px;
  margin-top: 4px;
}

.section-title {
  font-size: 14px;
  font-weight: 700;
  color: #374151;
  margin-bottom: 12px;
}
</style>
