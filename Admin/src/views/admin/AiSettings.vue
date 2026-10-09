<script setup lang="ts">
import { computed, onBeforeUnmount, onDeactivated, onMounted, ref, watch } from 'vue'
import { message } from 'ant-design-vue'
import { ReloadOutlined, SaveOutlined, CloudUploadOutlined, PlayCircleOutlined } from '@ant-design/icons-vue'
import { getAiRuntime, type AiRuntimeDTO } from '@/services/aiRuntime'
import { copyTtsConfig, sameTtsConfig, ttsStatusLabel } from '@/utils/ttsSettings'
import {
  getSiliconFlowVoices, getTtsConfig, getTtsStatus, getTtsVoices, resolveAiAudioUrl,
  testTtsSpeech, updateTtsConfig, uploadSiliconFlowVoice,
  type SiliconFlowVoiceDTO, type TtsConfigDTO, type TtsStatusDTO
} from '@/services/tts'

const loading = ref(false)
const saving = ref(false)
const detecting = ref(false)
const configReadOk = ref(false)
const configError = ref('')
const statusError = ref('')
const runtimeError = ref('')
const runtime = ref<AiRuntimeDTO | null>(null)
const ttsStatus = ref<TtsStatusDTO | null>(null)
const savedConfig = ref<TtsConfigDTO | null>(null)
const form = ref<TtsConfigDTO>({ enabled: false, baseUrl: '', voiceModel: '', provider: 'GPT_SOVITS' })
const dirty = computed(() => !!savedConfig.value && !sameTtsConfig(form.value, savedConfig.value))
const configBusy = computed(() => saving.value || loading.value || !configReadOk.value)
const voiceOptions = ref<string[]>([])
const siliconFlowVoices = ref<SiliconFlowVoiceDTO[]>([])
const loadingVoices = ref(false)
const loadingCloudVoices = ref(false)
const voicesError = ref('')
const cloudVoicesError = ref('')
const uploadDialogOpen = ref(false)
const uploadingVoice = ref(false)
const selectedVoiceFile = ref<File | null>(null)
const voiceUpload = ref({ model: '', customName: '', text: '' })
const testingSpeech = ref(false)
const isTestPlaying = ref(false)
const previewReady = ref(false)
const testError = ref('')
const testText = ref('你好，这是当前已保存语音配置的试听。')
let testAudio: HTMLAudioElement | null = null
let testController: AbortController | null = null
let testGeneration = 0
let disposed = false

const siliconFlowModelOptions = [
  { value: 'FunAudioLLM/CosyVoice2-0.5B', label: 'CosyVoice2-0.5B' },
  { value: 'IndexTeam/IndexTTS-2', label: 'IndexTTS-2' },
  { value: 'fnlp/MOSS-TTSD-v0.5', label: 'MOSS-TTSD-v0.5' }
]
const voiceStatusText = computed(() => ttsStatusLabel(ttsStatus.value, !!statusError.value))
const voiceStatusColor = computed(() => statusError.value ? 'error' : !ttsStatus.value ? 'default' : ttsStatus.value.online ? 'success' : ttsStatus.value.enabled ? 'warning' : 'default')
const currentStatusText = computed(() => statusError.value || ttsStatus.value?.message || '尚未检测语音服务')
const currentModelText = computed(() => runtimeError.value ? '文本模型读取失败' : runtime.value?.defaultModel || '尚未读取')
const checkedAtText = computed(() => ttsStatus.value?.checkedAt ? new Date(ttsStatus.value.checkedAt).toLocaleString('zh-CN') : '尚未检测')
const savedProviderText = computed(() => !savedConfig.value ? '尚未读取' : savedConfig.value.provider === 'SILICONFLOW' ? 'SiliconFlow 云端' : 'GPT-SoVITS')
const savedVoiceText = computed(() => {
  const saved = savedConfig.value
  if (!saved) return '尚未读取'
  if (saved.provider === 'SILICONFLOW') return siliconFlowVoices.value.find(v => v.uri === saved.siliconFlowVoiceUri)?.customName || saved.siliconFlowVoiceUri || '未设置'
  return saved.voiceModel || '使用服务默认模型'
})
const cloudVoiceOptions = computed(() => {
  const options = siliconFlowVoices.value.filter(v => v.uri).map(v => ({ value: v.uri!, label: `${v.customName || '未命名'} · ${v.model || '未知模型'}` }))
  const uri = form.value.siliconFlowVoiceUri
  if (uri && !options.some(v => v.value === uri)) options.unshift({ value: uri, label: `当前音色：${uri}` })
  return options
})
const cloudVoiceMismatch = computed(() => {
  const voice = siliconFlowVoices.value.find(v => v.uri === form.value.siliconFlowVoiceUri)
  return !!voice?.model && voice.model !== form.value.siliconFlowModel
})
const localVoiceOptions = computed(() => {
  const options = voiceOptions.value.map(v => ({ value: v, label: v }))
  if (form.value.voiceModel && !voiceOptions.value.includes(form.value.voiceModel)) options.unshift({ value: form.value.voiceModel, label: `${form.value.voiceModel}（当前配置）` })
  return options
})

const applyConfig = (config: TtsConfigDTO) => {
  const value = copyTtsConfig(config)
  if (savedConfig.value && !sameTtsConfig(value, savedConfig.value)) stopTestSpeech()
  savedConfig.value = value
  form.value = { ...value }
  configReadOk.value = true
  configError.value = ''
}
const detectStatus = async () => {
  if (detecting.value || disposed) return
  detecting.value = true
  try {
    const [runtimeResult, statusResult] = await Promise.allSettled([getAiRuntime(), getTtsStatus()])
    if (disposed) return
    if (runtimeResult.status === 'fulfilled') { runtime.value = runtimeResult.value; runtimeError.value = '' }
    else { runtimeError.value = '文本模型状态读取失败，可重新检测'; runtime.value = null }
    if (statusResult.status === 'fulfilled') { ttsStatus.value = statusResult.value; statusError.value = '' }
    else statusError.value = '语音状态检测失败，可重新检测；不代表服务已确认离线。'
  } finally { detecting.value = false }
}
const refreshVoices = async () => {
  if (loadingVoices.value || configBusy.value) return
  const baseUrl = form.value.baseUrl?.trim() || ''
  if (!baseUrl) { voicesError.value = '请先填写服务地址'; return }
  loadingVoices.value = true
  try {
    const list = await getTtsVoices(baseUrl)
    if (disposed || form.value.baseUrl?.trim() !== baseUrl) return
    voiceOptions.value = list
    voicesError.value = ''
  } catch { voicesError.value = '语音模型读取失败，已保留当前选择，可重试。' }
  finally { loadingVoices.value = false }
}
const refreshSiliconFlowVoices = async () => {
  if (loadingCloudVoices.value || configBusy.value) return
  loadingCloudVoices.value = true
  try { const list = await getSiliconFlowVoices(); if (!disposed) { siliconFlowVoices.value = list; cloudVoicesError.value = '' } }
  catch { cloudVoicesError.value = '音色目录读取失败，已保留当前选择，可重试或填写 URI。' }
  finally { loadingCloudVoices.value = false }
}
const loadConfiguration = async () => {
  if (loading.value || saving.value || uploadingVoice.value) return
  if (dirty.value) { message.warning('请先保存或放弃当前改动，再重新读取配置'); return }
  loading.value = true
  configReadOk.value = false
  try { const config = await getTtsConfig(); if (!disposed) applyConfig(config) }
  catch { configError.value = '语音配置读取失败。当前内容不可提交，请重新读取配置。' }
  finally { loading.value = false }
  if (configReadOk.value) {
    if (form.value.provider === 'SILICONFLOW') void refreshSiliconFlowVoices()
    else if (form.value.baseUrl) void refreshVoices()
  }
}
const discardChanges = () => { if (savedConfig.value && !saving.value && !uploadingVoice.value) form.value = { ...savedConfig.value } }
const save = async () => {
  if (configBusy.value || uploadingVoice.value || !dirty.value) return
  if (form.value.enabled && form.value.provider === 'GPT_SOVITS' && !form.value.baseUrl?.trim()) { message.warning('请输入 GPT-SoVITS 服务地址'); return }
  if (form.value.enabled && form.value.provider === 'SILICONFLOW' && !form.value.siliconFlowVoiceUri?.trim()) { message.warning('请选择或填写云端音色 URI'); return }
  if (form.value.provider === 'SILICONFLOW' && cloudVoiceMismatch.value) { message.warning('当前音色与云端模型不匹配，请重新选择音色或对应模型'); return }
  saving.value = true
  stopTestSpeech()
  try {
    const updated = await updateTtsConfig(copyTtsConfig(form.value))
    if (disposed) return
    applyConfig(updated)
    message.success('语音配置已保存并应用')
    void detectStatus()
  } catch (error: any) { if (!error?.isBusiness) message.error('保存失败，已保留当前改动，可重试') }
  finally { saving.value = false }
}
const selectCloudVoice = (uri?: string) => {
  form.value.siliconFlowVoiceUri = uri || ''
  const voice = siliconFlowVoices.value.find(v => v.uri === uri)
  if (voice?.model) form.value.siliconFlowModel = voice.model
}
const openVoiceUploadDialog = () => {
  if (configBusy.value || uploadingVoice.value) return
  selectedVoiceFile.value = null
  voiceUpload.value = { model: form.value.siliconFlowModel || 'FunAudioLLM/CosyVoice2-0.5B', customName: '', text: '' }
  uploadDialogOpen.value = true
}
const beforeVoiceUpload = (file: File) => { if (!uploadingVoice.value) selectedVoiceFile.value = file; return false }
const submitVoiceUpload = async () => {
  if (uploadingVoice.value || configBusy.value) return
  if (!selectedVoiceFile.value) { message.warning('请选择参考音频'); return }
  if (!voiceUpload.value.customName.trim() || !voiceUpload.value.text.trim()) { message.warning('请填写音色名称和音频对应文本'); return }
  uploadingVoice.value = true
  try {
    const voice = await uploadSiliconFlowVoice(selectedVoiceFile.value, voiceUpload.value.model, voiceUpload.value.customName.trim(), voiceUpload.value.text.trim())
    if (!voice.uri) throw new Error('服务未返回音色 URI')
    if (disposed) return
    form.value.siliconFlowModel = voice.model || voiceUpload.value.model
    form.value.siliconFlowVoiceUri = voice.uri
    siliconFlowVoices.value = [{ ...voice, model: form.value.siliconFlowModel }, ...siliconFlowVoices.value.filter(v => v.uri !== voice.uri)]
    message.success('音色已上传并选入草稿，保存配置后生效')
    uploadDialogOpen.value = false
  } catch (error: any) { if (!error?.isBusiness) message.error('上传失败，已保留音色名称、文本和文件，可重试') }
  finally { uploadingVoice.value = false }
}
const stopTestSpeech = () => {
  testGeneration++
  testController?.abort()
  testController = null
  if (testAudio) { testAudio.onended = null; testAudio.onerror = null; testAudio.pause(); testAudio.removeAttribute('src'); testAudio.load() }
  testAudio = null
  testingSpeech.value = isTestPlaying.value = previewReady.value = false
}
const playTestSpeech = async () => {
  if (isTestPlaying.value) { stopTestSpeech(); return }
  if (testingSpeech.value || !savedConfig.value?.enabled || configBusy.value) return
  if (!testText.value.trim()) { message.warning('请输入试听文本'); return }
  const token = ++testGeneration
  testError.value = ''
  try {
    if (!previewReady.value || !testAudio) {
      testingSpeech.value = true
      testController = new AbortController()
      const result = await testTtsSpeech(testText.value.trim(), testController.signal)
      if (token !== testGeneration || disposed) return
      if (!result.audioUrl) throw new Error('服务未返回音频')
      testAudio = new Audio(resolveAiAudioUrl(result.audioUrl))
      previewReady.value = true
      testAudio.onended = stopTestSpeech
      testAudio.onerror = () => { testError.value = '音频播放失败，可重新生成试听'; stopTestSpeech() }
    }
    const audio = testAudio
    await audio.play()
    if (token !== testGeneration || disposed) { audio.pause(); return }
    isTestPlaying.value = true
    void detectStatus()
  } catch (error: any) {
    if (token !== testGeneration || disposed) return
    testError.value = previewReady.value ? '浏览器未开始播放，点击“播放试听”重试，无需重新合成。' : '试听生成失败，可检查状态后重试。'
    if (!error?.isBusiness) message.error(testError.value)
  } finally { if (token === testGeneration) { testingSpeech.value = false; testController = null } }
}
const stopHiddenPreview = () => { if (document.hidden) stopTestSpeech() }
onMounted(() => { void loadConfiguration(); void detectStatus(); document.addEventListener('visibilitychange', stopHiddenPreview) })
onDeactivated(stopTestSpeech)
watch(testText, () => { if (previewReady.value && !isTestPlaying.value) stopTestSpeech() })
onBeforeUnmount(() => { disposed = true; stopTestSpeech(); document.removeEventListener('visibilitychange', stopHiddenPreview) })
</script>

<template>
  <div class="p-24">
    <div class="page-heading">
      <div><h2>语音服务</h2><p>选择引擎与音色，保存后应用到全站朗读。<router-link to="/ai-models">文本模型配置 →</router-link></p></div>
      <a-space wrap>
        <a-button @click="detectStatus" :loading="detecting">检测状态</a-button>
        <a-button @click="loadConfiguration" :loading="loading" :disabled="saving || uploadingVoice || dirty"><ReloadOutlined /> 重新读取配置</a-button>
        <a-button type="primary" @click="save" :loading="saving" :disabled="configBusy || uploadingVoice || !dirty"><SaveOutlined /> 保存并应用</a-button>
        <a-button class="header-test" @click="playTestSpeech" :loading="testingSpeech" :disabled="!savedConfig?.enabled || configBusy"><PlayCircleOutlined /> {{ isTestPlaying ? '停止试听' : previewReady ? '播放试听' : '试听已保存配置' }}</a-button>
        <a-button v-if="testingSpeech" class="header-test" @click="stopTestSpeech">取消试听</a-button>
      </a-space>
    </div>
    <a-alert v-if="configError" class="mb-16" type="error" show-icon :message="configError" />
    <div class="status-strip mb-16">
      <a-tag :color="voiceStatusColor">语音 {{ voiceStatusText }}</a-tag>
      <span>{{ currentStatusText }}</span>
      <a-tag v-if="dirty" color="orange">有未保存改动</a-tag>
      <span class="text-secondary">检测时间 {{ checkedAtText }}</span>
    </div>
    <a-row :gutter="[16, 16]">
      <a-col :xs="24" :lg="15">
        <a-card :bordered="false" title="引擎与音色" :loading="loading">
          <a-form layout="vertical" :disabled="configBusy || uploadingVoice">
            <a-form-item label="全站朗读"><a-switch v-model:checked="form.enabled" /><span class="switch-label">{{ form.enabled ? '开启语音生成' : '关闭语音生成' }}</span></a-form-item>
            <a-form-item label="语音引擎">
              <a-radio-group v-model:value="form.provider" button-style="solid">
                <a-radio-button value="GPT_SOVITS">GPT-SoVITS</a-radio-button><a-radio-button value="SILICONFLOW">SiliconFlow 云端</a-radio-button>
              </a-radio-group>
            </a-form-item>
            <template v-if="form.provider === 'GPT_SOVITS'">
              <a-form-item label="服务地址"><a-input v-model:value="form.baseUrl" placeholder="http://服务地址:8000" allow-clear /></a-form-item>
              <a-form-item label="语音模型">
                <div class="input-with-actions"><a-select v-model:value="form.voiceModel" :options="localVoiceOptions" placeholder="留空使用服务默认模型" allow-clear /><a-button @click="refreshVoices" :loading="loadingVoices">读取模型</a-button></div>
              </a-form-item>
              <p v-if="voicesError" class="field-error">{{ voicesError }}</p>
            </template>
            <template v-else>
              <a-form-item label="云端模型"><a-select v-model:value="form.siliconFlowModel" :options="siliconFlowModelOptions" /></a-form-item>
              <a-form-item label="云端音色">
                <a-select :value="form.siliconFlowVoiceUri" :options="cloudVoiceOptions" placeholder="选择已上传音色，或在高级配置填写 URI" allow-clear @change="selectCloudVoice" />
              </a-form-item>
              <p v-if="cloudVoiceMismatch" class="field-error">当前音色与云端模型不匹配，请重新选择后保存。</p>
              <a-space wrap class="mb-16"><a-button @click="refreshSiliconFlowVoices" :loading="loadingCloudVoices"><ReloadOutlined /> 读取音色</a-button><a-button @click="openVoiceUploadDialog"><CloudUploadOutlined /> 上传新音色</a-button></a-space>
              <p v-if="cloudVoicesError" class="field-error">{{ cloudVoicesError }}</p>
              <p v-if="ttsStatus && !ttsStatus.siliconFlowApiKeyConfigured" class="field-error">云端凭据尚未配置，请联系服务维护者。</p>
            </template>
            <a-collapse ghost>
              <a-collapse-panel key="advanced" header="高级配置 · 音频参数与音色 URI">
                <a-form-item v-if="form.provider === 'SILICONFLOW'" label="音色 URI"><a-input v-model:value="form.siliconFlowVoiceUri" placeholder="speech:…" allow-clear /></a-form-item>
                <a-row :gutter="12">
                  <a-col :xs="24" :sm="8"><a-form-item label="输出格式"><a-select v-model:value="form.responseFormat" :options="['mp3','wav','opus'].map(v => ({ value: v, label: v }))" /></a-form-item></a-col>
                  <a-col :xs="24" :sm="8"><a-form-item label="采样率"><a-select v-model:value="form.sampleRate" :options="[8000,16000,24000,32000,44100,48000].map(v => ({ value: v, label: `${v} Hz` }))" /></a-form-item></a-col>
                  <a-col :xs="24" :sm="8"><a-form-item label="语速"><a-input-number v-model:value="form.speed" :min="0.25" :max="4" :step="0.05" style="width:100%" /></a-form-item></a-col>
                </a-row>
                <p class="text-secondary">音色选择会同步其对应的云端模型；读取目录不会替换当前选择。</p>
              </a-collapse-panel>
            </a-collapse>
            <div v-if="dirty" class="draft-actions"><span>改动尚未应用。</span><a-button type="link" :disabled="saving || uploadingVoice" @click="discardChanges">放弃改动</a-button></div>
          </a-form>
        </a-card>
      </a-col>
      <a-col :xs="24" :lg="9">
        <a-card :bordered="false" title="已保存配置与试听">
          <dl class="saved-config"><dt>朗读</dt><dd>{{ !savedConfig ? '尚未读取' : savedConfig.enabled ? '已开启' : '已关闭' }}</dd><dt>引擎</dt><dd>{{ savedProviderText }}</dd><dt>音色</dt><dd :title="savedVoiceText">{{ savedVoiceText }}</dd><dt>文本模型</dt><dd :title="currentModelText">{{ currentModelText }} <router-link to="/ai-models">管理</router-link></dd></dl>
          <a-divider />
          <a-textarea v-model:value="testText" :rows="3" :maxlength="300" placeholder="输入试听文本" :disabled="testingSpeech || isTestPlaying" aria-label="试听文本" />
          <p class="text-secondary">{{ dirty ? '试听当前已保存配置；左侧改动保存后才生效。' : '试听当前已保存配置，生成期间请勿重复提交。' }}</p>
          <a-space wrap class="panel-test"><a-button @click="playTestSpeech" :loading="testingSpeech" :disabled="!savedConfig?.enabled || configBusy"><PlayCircleOutlined /> {{ isTestPlaying ? '停止试听' : previewReady ? '播放试听' : '试听已保存配置' }}</a-button><a-button v-if="testingSpeech" @click="stopTestSpeech">取消试听</a-button></a-space>
          <p v-if="testError" class="field-error">{{ testError }}</p>
          <p v-if="savedConfig && !savedConfig.enabled" class="text-secondary">开启朗读并保存后可试听。</p>
        </a-card>
        <a-collapse ghost class="diagnostics">
          <a-collapse-panel key="diagnostics" header="状态说明与诊断">
            <p>{{ currentStatusText }}</p><p>{{ runtimeError || runtime?.aiMessage || 'AI 服务状态尚未读取' }}</p>
            <p>“待确认”表示配置齐全但尚未完成真实合成；保存成功不代表供应商已验证在线。</p>
            <p v-if="ttsStatus?.siliconFlowApiKeySource">云端凭据来源：{{ ttsStatus.siliconFlowApiKeySource }}</p>
          </a-collapse-panel>
        </a-collapse>
      </a-col>
    </a-row>
    <a-modal v-model:open="uploadDialogOpen" title="上传新音色" ok-text="上传并选入草稿" :confirm-loading="uploadingVoice" :cancel-button-props="{ disabled: uploadingVoice }" :closable="!uploadingVoice" :mask-closable="!uploadingVoice" :keyboard="!uploadingVoice" @ok="submitVoiceUpload">
      <a-form layout="vertical" :disabled="uploadingVoice">
        <a-form-item label="云端模型"><a-select v-model:value="voiceUpload.model" :options="siliconFlowModelOptions" /></a-form-item>
        <a-form-item label="音色名称" required><a-input v-model:value="voiceUpload.customName" placeholder="给音色起个便于识别的名称" /></a-form-item>
        <a-form-item label="音频对应文本" required><a-textarea v-model:value="voiceUpload.text" :rows="3" placeholder="准确填写参考音频中说出的文字" /></a-form-item>
        <a-form-item label="参考音频" required><a-upload-dragger :show-upload-list="false" :before-upload="beforeVoiceUpload" :max-count="1" :disabled="uploadingVoice" accept=".mp3,.wav,.pcm,.opus,audio/*"><p>{{ selectedVoiceFile?.name || '拖入参考音频，或点击选择' }}</p></a-upload-dragger></a-form-item>
        <p class="text-secondary">上传只创建音色并选入草稿，保存配置后才替换全站语音。</p>
      </a-form>
    </a-modal>
  </div>
</template>

<style scoped>
.page-heading { display:flex; align-items:flex-start; justify-content:space-between; gap:16px; margin-bottom:16px; }
.page-heading h2 { margin:0 0 4px; font-size:20px; }
.page-heading p { margin:0; color:var(--lt-color-text-secondary); }
.status-strip { display:flex; flex-wrap:wrap; align-items:center; gap:8px 12px; padding:12px 16px; background:var(--lt-color-bg-container); border-radius:var(--lt-radius-lg); }
.text-secondary { color:var(--lt-color-text-secondary); font-size:12px; line-height:1.7; margin-top:10px; }
.switch-label { margin-left:10px; color:var(--lt-color-text-secondary); }
.input-with-actions { display:flex; gap:8px; }
.input-with-actions :deep(.ant-select) { flex:1; min-width:0; }
.field-error { color:var(--lt-color-error); font-size:12px; line-height:1.6; }
.saved-config { display:grid; grid-template-columns:72px minmax(0,1fr); gap:12px 8px; margin:0; }
.saved-config dt { color:var(--lt-color-text-secondary); }
.saved-config dd { margin:0; overflow-wrap:anywhere; }
.draft-actions { display:flex; align-items:center; justify-content:space-between; margin-top:8px; color:var(--lt-color-warning); font-size:12px; }
.diagnostics { margin-top:12px; }
.header-test { display:none; }
@media(max-width:1000px) { .page-heading { flex-direction:column; } }
@media(max-width:991px) { .header-test { display:inline-flex; } .panel-test { display:none; } }
</style>
