<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { message } from 'ant-design-vue'
import { ReloadOutlined, SaveOutlined, RobotOutlined, SoundOutlined, ApiOutlined, CheckCircleOutlined, CloudUploadOutlined, PlayCircleOutlined } from '@ant-design/icons-vue'
import aiModelsService, { type ModelConfig } from '@/services/aiModels'
import { getAiRuntime, type AiRuntimeDTO } from '@/services/aiRuntime'
import {
  getSiliconFlowVoices,
  getTtsConfig,
  getTtsStatus,
  getTtsVoices,
  resolveMainAudioUrl,
  testTtsSpeech,
  updateTtsConfig,
  uploadSiliconFlowVoice,
  type SiliconFlowVoiceDTO,
  type TtsConfigDTO,
  type TtsStatusDTO
} from '@/services/tts'

const loading = ref(false)
const saving = ref(false)
const runtime = ref<AiRuntimeDTO | null>(null)
const ttsStatus = ref<TtsStatusDTO | null>(null)
const modelOptions = ref<ModelConfig[]>([])
const voiceOptions = ref<string[]>([])
const siliconFlowVoices = ref<SiliconFlowVoiceDTO[]>([])
const uploadDialogOpen = ref(false)
const uploadingVoice = ref(false)
const testingSpeech = ref(false)
const selectedVoiceFile = ref<File | null>(null)

const form = ref<TtsConfigDTO & { defaultModelId: number | null }>({
  enabled: true,
  baseUrl: '',
  voiceModel: '',
  provider: 'GPT_SOVITS',
  siliconFlowModel: 'FunAudioLLM/CosyVoice2-0.5B',
  siliconFlowVoiceUri: '',
  responseFormat: 'mp3',
  sampleRate: 44100,
  speed: 1,
  defaultModelId: null
})

const voiceUpload = ref({
  customName: 'naxida',
  text: '在一无所知中, 梦里的一天结束了，一个新的轮回便会开始'
})

const siliconFlowModelOptions = [
  { value: 'FunAudioLLM/CosyVoice2-0.5B', label: 'CosyVoice2-0.5B' },
  { value: 'IndexTeam/IndexTTS-2', label: 'IndexTTS-2' },
  { value: 'fnlp/MOSS-TTSD-v0.5', label: 'MOSS-TTSD-v0.5' }
]

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

const currentVoiceText = computed(() => {
  if (form.value.provider === 'SILICONFLOW') {
    return form.value.siliconFlowVoiceUri || '未设置'
  }
  return form.value.voiceModel || '未设置'
})

const currentVoiceSub = computed(() => {
  if (form.value.provider === 'SILICONFLOW') {
    return form.value.siliconFlowModel || '未设置云端模型'
  }
  return ttsStatus.value?.baseUrl || '未配置服务地址'
})

const selectedVoiceFileName = computed(() => selectedVoiceFile.value?.name || '未选择文件')

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

const refreshSiliconFlowVoices = async () => {
  try {
    siliconFlowVoices.value = await getSiliconFlowVoices()
    if (!form.value.siliconFlowVoiceUri && siliconFlowVoices.value.length > 0) {
      const naxida = siliconFlowVoices.value.find(item => item.customName === 'naxida')
      form.value.siliconFlowVoiceUri = (naxida || siliconFlowVoices.value[0]).uri || ''
    }
  } catch {
    siliconFlowVoices.value = []
  }
}

const selectCloudVoice = (uri?: string) => {
  if (!uri) {
    form.value.siliconFlowVoiceUri = ''
    return
  }
  const voice = siliconFlowVoices.value.find(item => item.uri === uri)
  if (voice) {
    useCloudVoice(voice)
    return
  }
  form.value.siliconFlowVoiceUri = uri
}

const refresh = async () => {
  if (loading.value) return
  loading.value = true
  try {
    // 用 allSettled:单个 API 失败(如 AI 服务离线时 getModelList 500)不阻塞其他,
    // 每项失败单独提示,不覆盖 form 里已有的值 —— 避免"整个表单被回滚到初始值,用户以为开关不生效"
    const [runtimeResult, ttsConfigResult, ttsStatusResult, modelsResult] = await Promise.allSettled([
      getAiRuntime(),
      getTtsConfig(),
      getTtsStatus(),
      aiModelsService.getModelList()
    ])

    if (runtimeResult.status === 'fulfilled') {
      runtime.value = runtimeResult.value
    } else {
      console.warn('加载 AI 运行时状态失败', runtimeResult.reason)
    }

    if (ttsStatusResult.status === 'fulfilled') {
      ttsStatus.value = ttsStatusResult.value
    } else {
      console.warn('加载 TTS 状态失败', ttsStatusResult.reason)
    }

    if (modelsResult.status === 'fulfilled') {
      modelOptions.value = modelsResult.value
    } else {
      console.warn('加载模型列表失败(AI 服务可能未启动)', modelsResult.reason)
      message.warning('模型列表加载失败,请确认 AI 服务已启动')
    }

    if (ttsConfigResult.status === 'fulfilled') {
      const ttsConfig = ttsConfigResult.value
      const currentDefaultModel = modelsResult.status === 'fulfilled'
        ? modelsResult.value.find(item => item.isDefault) || null
        : null
      form.value = {
        enabled: ttsConfig.enabled,
        baseUrl: ttsConfig.baseUrl || '',
        voiceModel: ttsConfig.voiceModel || '',
        provider: ttsConfig.provider || 'GPT_SOVITS',
        siliconFlowModel: ttsConfig.siliconFlowModel || 'FunAudioLLM/CosyVoice2-0.5B',
        siliconFlowVoiceUri: ttsConfig.siliconFlowVoiceUri || '',
        responseFormat: ttsConfig.responseFormat || 'mp3',
        sampleRate: ttsConfig.sampleRate || 44100,
        speed: ttsConfig.speed || 1,
        defaultModelId: currentDefaultModel?.id || null
      }
    } else {
      console.error('加载 TTS 配置失败', ttsConfigResult.reason)
      message.error('TTS 配置加载失败,页面表单可能不同步')
    }

    await Promise.all([refreshVoices(), refreshSiliconFlowVoices()])
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
      voiceModel: form.value.voiceModel?.trim() || '',
      provider: form.value.provider || 'GPT_SOVITS',
      siliconFlowModel: form.value.siliconFlowModel?.trim() || 'FunAudioLLM/CosyVoice2-0.5B',
      siliconFlowVoiceUri: form.value.siliconFlowVoiceUri?.trim() || '',
      responseFormat: form.value.responseFormat || 'mp3',
      sampleRate: form.value.sampleRate || 44100,
      speed: form.value.speed || 1
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

const openVoiceUploadDialog = () => {
  selectedVoiceFile.value = null
  uploadDialogOpen.value = true
}

const beforeVoiceUpload = (file: File) => {
  selectedVoiceFile.value = file
  return false
}

const submitVoiceUpload = async () => {
  if (uploadingVoice.value) return false
  if (!selectedVoiceFile.value) {
    message.warning('请先选择参考音频文件')
    return false
  }
  if (!voiceUpload.value.text.trim()) {
    message.warning('请填写参考音频对应文本')
    return false
  }
  uploadingVoice.value = true
  try {
    const result = await uploadSiliconFlowVoice(
      selectedVoiceFile.value,
      form.value.siliconFlowModel || 'FunAudioLLM/CosyVoice2-0.5B',
      voiceUpload.value.customName.trim() || 'naxida',
      voiceUpload.value.text.trim()
    )
    form.value.siliconFlowVoiceUri = result.uri || ''
    message.success('参考音频已上传')
    uploadDialogOpen.value = false
    await refreshSiliconFlowVoices()
  } catch (error: any) {
    message.error(error?.message || '上传参考音频失败')
  } finally {
    uploadingVoice.value = false
  }
  return false
}

const useCloudVoice = (voice: SiliconFlowVoiceDTO) => {
  form.value.siliconFlowModel = voice.model || form.value.siliconFlowModel
  form.value.siliconFlowVoiceUri = voice.uri || ''
}

const playTestSpeech = async () => {
  if (testingSpeech.value) return
  testingSpeech.value = true
  try {
    const result = await testTtsSpeech('慢工出细活，再给我两分钟，你马上就能见识到超梦分析的厉害了。')
    const audio = new Audio(resolveMainAudioUrl(result.audioUrl))
    await audio.play()
  } catch (error: any) {
    message.error(error?.message || '试听失败')
  } finally {
    testingSpeech.value = false
  }
}

onMounted(() => {
  refresh()
})
</script>

<template>
  <div class="p-24">
    <a-row :gutter="[16, 16]" class="mb-16">
      <a-col :xs="24" :sm="12" :lg="6">
        <a-card :bordered="false" class="stat-card">
          <div class="stat-row">
            <div class="stat-icon bg-blue">
              <ApiOutlined />
            </div>
            <div class="stat-text">
              <div class="stat-label">AI 服务</div>
              <div class="stat-value" :title="runtime?.aiOnline ? '在线' : '离线'">{{ runtime?.aiOnline ? '在线' : '离线' }}</div>
              <div class="stat-sub" :title="runtime?.aiMessage || '未检测'">{{ runtime?.aiMessage || '未检测' }}</div>
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
            <div class="stat-text">
              <div class="stat-label">默认模型</div>
              <div class="stat-value compact" :title="runtime?.defaultModel || '未设置'">{{ runtime?.defaultModel || '未设置' }}</div>
              <div class="stat-sub" :title="`已启用 ${enabledModelCount} 个模型`">已启用 {{ enabledModelCount }} 个模型</div>
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
            <div class="stat-text">
              <div class="stat-label">TTS 状态</div>
              <div class="stat-value" :title="ttsStatus?.online ? '在线' : '离线'">{{ ttsStatus?.online ? '在线' : '离线' }}</div>
              <div class="stat-sub" :title="currentStatusText">{{ currentStatusText }}</div>
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
            <div class="stat-text">
              <div class="stat-label">当前语音</div>
              <div class="stat-value compact" :title="currentVoiceText">{{ currentVoiceText }}</div>
              <div class="stat-sub" :title="currentVoiceSub">{{ currentVoiceSub }}</div>
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
                allow-clear
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

            <a-form-item label="推理引擎">
              <a-radio-group v-model:value="form.provider" button-style="solid">
                <a-radio-button value="GPT_SOVITS">自定义 GPT-SoVITS</a-radio-button>
                <a-radio-button value="SILICONFLOW">SiliconFlow 云端</a-radio-button>
              </a-radio-group>
            </a-form-item>

            <template v-if="form.provider === 'GPT_SOVITS'">
              <a-form-item label="TTS 服务地址">
                <a-input v-model:value="form.baseUrl" placeholder="http://127.0.0.1:8000" allow-clear />
              </a-form-item>
              <a-form-item>
                <a-button @click="refreshVoices" :disabled="!form.baseUrl">
                  <template #icon><ReloadOutlined /></template>
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
            </template>

            <template v-else>
              <a-alert
                v-if="!ttsStatus?.siliconFlowApiKeyConfigured"
                class="mb-16"
                type="warning"
                show-icon
                message="SiliconFlow API Key 未配置"
                description="后端按 SILICONFLOW_TTS_API_KEY、SILICONFLOW_API_KEY、SPRING_AI_OPENAI_API_KEY 顺序读取；本地直跑也会读取项目根目录 .env，修改后需要重启后端。"
              />
              <a-alert
                v-else
                class="mb-16"
                type="success"
                show-icon
                :message="`SiliconFlow Key 已配置${ttsStatus?.siliconFlowApiKeySource ? `：${ttsStatus.siliconFlowApiKeySource}` : ''}`"
              />

              <a-form-item label="云端模型">
                <a-select
                  v-model:value="form.siliconFlowModel"
                  :options="siliconFlowModelOptions"
                  placeholder="请选择 SiliconFlow TTS 模型"
                />
              </a-form-item>
              <a-form-item label="云端音色 URI">
                <a-input
                  v-model:value="form.siliconFlowVoiceUri"
                  placeholder="speech:naxida:ss14k9ofjb:otzhoxllirkrcnsligpb"
                  allow-clear
                />
              </a-form-item>
              <a-form-item label="已上传音色">
                <a-space compact class="full-width">
                  <a-select
                    class="flex-1"
                    :value="form.siliconFlowVoiceUri"
                    :options="siliconFlowVoices.map(item => ({ value: item.uri, label: `${item.customName || '未命名'} (${item.model || '未知模型'})` }))"
                    placeholder="选择已上传的云端音色"
                    allow-clear
                    @change="selectCloudVoice"
                  />
                  <a-button @click="refreshSiliconFlowVoices">
                    <template #icon><ReloadOutlined /></template>
                  </a-button>
                </a-space>
              </a-form-item>

              <a-row :gutter="12">
                <a-col :xs="24" :sm="8">
                  <a-form-item label="音频格式">
                    <a-select
                      v-model:value="form.responseFormat"
                      :options="['mp3', 'wav', 'opus', 'pcm'].map(item => ({ value: item, label: item }))"
                    />
                  </a-form-item>
                </a-col>
                <a-col :xs="24" :sm="8">
                  <a-form-item label="采样率">
                    <a-select
                      v-model:value="form.sampleRate"
                      :options="[32000, 44100, 48000, 24000, 16000, 8000].map(item => ({ value: item, label: `${item} Hz` }))"
                    />
                  </a-form-item>
                </a-col>
                <a-col :xs="24" :sm="8">
                  <a-form-item label="语速">
                    <a-input-number v-model:value="form.speed" :min="0.25" :max="4" :step="0.05" class="full-width" />
                  </a-form-item>
                </a-col>
              </a-row>

              <a-form-item label="云端操作">
                <a-space>
                  <a-button @click="openVoiceUploadDialog">
                    <template #icon><CloudUploadOutlined /></template>
                    上传参考音频
                  </a-button>
                  <a-button @click="playTestSpeech" :loading="testingSpeech">
                    <template #icon><PlayCircleOutlined /></template>
                    测试试听
                  </a-button>
                </a-space>
              </a-form-item>
            </template>

            <a-alert
              :type="ttsStatus?.online ? 'success' : 'warning'"
              show-icon
              :message="`TTS：${currentStatusText}`"
              :description="form.provider === 'SILICONFLOW'
                ? `云端音色：${form.siliconFlowVoiceUri || '未设置'}`
                : (ttsStatus?.voiceModel ? `当前生效语音：${ttsStatus.voiceModel}` : '当前未设置语音模型')"
            />
          </a-col>
        </a-row>
      </a-form>
    </a-card>

    <a-modal
      v-model:open="uploadDialogOpen"
      title="上传 SiliconFlow 参考音频"
      :confirm-loading="uploadingVoice"
      ok-text="上传"
      cancel-text="取消"
      @ok="submitVoiceUpload"
    >
      <a-form layout="vertical">
        <a-form-item label="云端模型">
          <a-select
            v-model:value="form.siliconFlowModel"
            :options="siliconFlowModelOptions"
            placeholder="请选择 SiliconFlow TTS 模型"
          />
        </a-form-item>
        <a-form-item label="音色名称">
          <a-input v-model:value="voiceUpload.customName" placeholder="例如 naxida" />
        </a-form-item>
        <a-form-item label="参考音频文本">
          <a-textarea v-model:value="voiceUpload.text" :rows="3" placeholder="参考音频对应文本" />
        </a-form-item>
        <a-form-item label="参考音频文件">
          <a-space direction="vertical" class="full-width">
            <a-upload :show-upload-list="false" :before-upload="beforeVoiceUpload" accept=".mp3,.wav,.pcm,.opus,audio/*">
              <a-button>
                <template #icon><CloudUploadOutlined /></template>
                选择音频文件
              </a-button>
            </a-upload>
            <div class="upload-file-name">{{ selectedVoiceFileName }}</div>
          </a-space>
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<style scoped>
.stat-card,
.settings-card {
  border-radius: var(--lt-radius-lg);
  box-shadow: var(--lt-shadow-xs);
}

.stat-card {
  height: 100%;
}
.stat-card :deep(.ant-card-body) {
  height: 100%;
  display: flex;
  align-items: center;
}

.stat-row {
  display: flex;
  align-items: center;
  gap: var(--lt-space-md);
  min-width: 0;
}

.stat-icon {
  width: var(--lt-size-stat-icon);
  height: var(--lt-size-stat-icon);
  border-radius: var(--lt-radius-lg);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: var(--lt-font-size-xl);
  flex-shrink: 0;
}

.stat-text {
  flex: 1;
  min-width: 0;
}

.bg-blue { background: var(--lt-color-info-bg); color: var(--lt-color-info); }
.bg-green { background: var(--lt-color-success-bg); color: var(--lt-color-success); }
.bg-orange { background: var(--lt-color-warning-bg); color: var(--lt-color-warning); }
.bg-purple { background: var(--lt-color-purple-bg); color: var(--lt-color-purple); }

.stat-label {
  color: var(--lt-color-text-secondary);
  font-size: var(--lt-font-size-sm);
  margin-bottom: var(--lt-space-xs);
}

.stat-value {
  font-size: var(--lt-font-size-xl);
  font-weight: var(--lt-font-weight-bold);
  color: var(--lt-color-text);
  line-height: var(--lt-line-height-tight);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.stat-value.compact {
  font-size: var(--lt-font-size-base);
}

.stat-sub {
  color: var(--lt-color-text-tertiary);
  font-size: var(--lt-font-size-xs);
  margin-top: var(--lt-space-xs);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.section-title {
  font-size: var(--lt-font-size-base);
  font-weight: var(--lt-font-weight-bold);
  color: var(--lt-color-text-secondary);
  margin-bottom: var(--lt-space-md);
}

.full-width {
  width: 100%;
}

.flex-1 {
  flex: 1;
}

.upload-file-name {
  color: var(--lt-color-text-secondary);
  font-size: var(--lt-font-size-sm);
}
</style>

