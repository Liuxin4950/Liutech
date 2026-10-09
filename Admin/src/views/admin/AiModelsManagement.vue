<script setup lang="ts">
import { computed, ref } from 'vue'
import { useTablePage, useModalForm } from '@/composables'
import aiModelsService from '@/services/aiModels'
import { getAiRuntime } from '@/services/aiRuntime'
import type { ModelConfig, ModelConfigRequest } from '@/services/aiModels'
import { message, Modal } from 'ant-design-vue'
import {
  ReloadOutlined,
  PlusOutlined,
  StarFilled,
  RobotOutlined
} from '@ant-design/icons-vue'

// ============== 表格页面 ==============
const loadError = ref('')
const modelsLoaded = ref(false)
const runtimeDefault = ref<string | null>(null)
const mutationBusy = ref<string | null>(null)
const confirmationOpen = ref(false)
const {
  loading, dataSource,
  load
} = useTablePage<ModelConfig, { keyword: string }>({
  loadFn: async () => {
    try {
      const [models, runtime] = await Promise.allSettled([aiModelsService.getModelList(), getAiRuntime()])
      runtimeDefault.value = runtime.status === 'fulfilled' ? runtime.value.defaultModel : null
      if (models.status === 'rejected') throw models.reason
      if (!Array.isArray(models.value)) throw new Error('模型列表响应格式不正确')
      loadError.value = ''
      modelsLoaded.value = true
      return { code: 200, message: 'ok', data: { records: models.value, total: models.value.length } }
    } catch (error) {
      loadError.value = '模型列表读取失败，已保留上次结果。重新读取成功后再操作。'
      throw error
    }
  },
  defaultSearchParams: { keyword: '' },
  autoLoad: true,
  loadErrorMessage: '加载模型失败'
})

// 客户端搜索过滤
const searchText = ref('')
const defaultModel = computed(() => dataSource.value.find(item => item.isDefault))
const defaultModelText = computed(() => defaultModel.value?.displayName || (runtimeDefault.value ? `服务端回退：${runtimeDefault.value}` : modelsLoaded.value ? '尚未配置默认模型' : '正在读取'))
const enabledModelCount = computed(() => dataSource.value.filter(item => item.isEnabled).length)
const actionsDisabled = computed(() => loading.value || !!loadError.value || !!mutationBusy.value || confirmLoading.value)
const filteredModels = computed(() => {
  const keyword = searchText.value.trim().toLowerCase()
  if (!keyword) return dataSource.value
  return dataSource.value.filter(item =>
    item.displayName.toLowerCase().includes(keyword) ||
    item.modelName.toLowerCase().includes(keyword) ||
    (item.description || '').toLowerCase().includes(keyword)
  )
})

// ============== 预设模型 ==============
/** 预设只帮助填写，提交前仍需按供应商当前规格核对。 */
interface ModelPreset {
  /** 下拉选项的标识，用模型名保证唯一 */
  key: string
  modelName: string
  displayName: string
  /** 上下文窗口（输入 + 输出总 token 上限） */
  contextWindow: number
  /** 单次输出上限 */
  maxTokens: number
  temperature: number
  description: string
}

const MODEL_PRESETS: ModelPreset[] = [
  {
    key: 'zai-org/GLM-4.6',
    modelName: 'zai-org/GLM-4.6',
    displayName: 'GLM-4.6',
    contextWindow: 205000,
    maxTokens: 16384,
    temperature: 0.9,
    description: '智谱 GLM-4.6，长上下文通用对话模型，日常写作助手首选。'
  },
  {
    key: 'deepseek-ai/DeepSeek-V3.2',
    modelName: 'deepseek-ai/DeepSeek-V3.2',
    displayName: 'DeepSeek-V3.2',
    contextWindow: 164000,
    maxTokens: 16384,
    temperature: 0.9,
    description: 'DeepSeek-V3.2，通用对话与写作，长文总结表现稳定。'
  },
  {
    key: 'deepseek-ai/DeepSeek-R1',
    modelName: 'deepseek-ai/DeepSeek-R1',
    displayName: 'DeepSeek-R1',
    contextWindow: 164000,
    maxTokens: 16384,
    temperature: 0.6,
    description: 'DeepSeek-R1 推理模型，输出含思考过程，温度调低更稳。'
  },
  {
    key: 'Qwen/Qwen2.5-7B-Instruct',
    modelName: 'Qwen/Qwen2.5-7B-Instruct',
    displayName: 'Qwen2.5-7B',
    contextWindow: 32768,
    maxTokens: 8192,
    temperature: 0.9,
    description: '通义千问 2.5-7B，免费/低价档位，适合做兜底或测试模型。'
  },
  {
    key: 'deepseek-ai/DeepSeek-V2.5',
    modelName: 'deepseek-ai/DeepSeek-V2.5',
    displayName: 'DeepSeek-V2.5',
    contextWindow: 128000,
    maxTokens: 8192,
    temperature: 0.7,
    description: 'DeepSeek-V2.5，上一代通用模型，成本较低。'
  }
]

/** 当前选中的预设 key（仅新增态使用） */
const selectedPresetKey = ref<string | undefined>(undefined)

// ============== 弹窗表单 ==============
const defaultModelForm = (): ModelConfigRequest => ({
  modelName: '',
  displayName: '',
  provider: 'siliconflow',
  isEnabled: true,
  sortOrder: 0,
  maxTokens: undefined,
  contextWindow: undefined,
  temperature: undefined,
  description: ''
})

const {
  modalVisible, modalTitle, isEdit, editingId, confirmLoading,
  formRef, formModel, openCreate: baseOpenCreate, handleOk, handleCancel
} = useModalForm<ModelConfigRequest>({
  createFn: async (data) => {
    const result = await aiModelsService.addModel(data as ModelConfigRequest)
    return { code: 200, data: result }
  },
  updateFn: async (id, data) => {
    const result = await aiModelsService.updateModel(id, data as ModelConfigRequest)
    return { code: 200, data: result }
  },
  defaultForm: defaultModelForm,
  onCreateSuccess: load,
  onUpdateSuccess: load,
  entityName: '模型'
})

// 覆盖 openCreate：自定义标题和默认排序
const openCreate = () => {
  if (actionsDisabled.value) return
  baseOpenCreate()
  modalTitle.value = '新增模型'
  formModel.value.sortOrder = dataSource.value.length
  // 每次新增都从"未选预设"开始，避免沿用上一次的选择
  selectedPresetKey.value = undefined
}

// 覆盖 openEdit：精确映射字段
const openEdit = (record: ModelConfig) => {
  if (actionsDisabled.value) return
  isEdit.value = true
  modalTitle.value = '编辑模型'
  editingId.value = record.id
  formModel.value = {
    modelName: record.modelName,
    displayName: record.displayName,
    provider: record.provider || 'siliconflow',
    isEnabled: record.isEnabled,
    sortOrder: record.sortOrder,
    maxTokens: record.maxTokens,
    contextWindow: record.contextWindow,
    temperature: record.temperature,
    description: record.description
  }
  // 编辑态不展示预设下拉（后端不允许改 modelName），这里顺手清掉选中态
  selectedPresetKey.value = undefined
  modalVisible.value = true
}

/**
 * 用预设一次性填充表单。
 * 只覆盖与预设有关的字段，保留管理员已经填好的排序、启用状态。
 */
const applyPreset = (value: unknown) => {
  // 清空下拉（allow-clear）时 value 为 undefined，不做任何处理
  if (typeof value !== 'string') return
  const preset = MODEL_PRESETS.find(item => item.key === value)
  if (!preset) return

  formModel.value = {
    ...formModel.value,
    modelName: preset.modelName,
    displayName: preset.displayName,
    contextWindow: preset.contextWindow,
    maxTokens: preset.maxTokens,
    temperature: preset.temperature,
    description: preset.description
  }

  // 旧的红字校验结果已经不成立，清掉避免误导
  formRef.value?.clearValidate?.()
  message.success(`已填充「${preset.displayName}」预设，提交前仍可手动调整`)
}

/**
 * 上下文窗口校验：挡住自相矛盾的配置。
 * - 填了就必须 ≥ 4096，再小几乎放不下任何输入
 * - 必须大于最大 Token（输出上限），否则输入预算是 0 或负数
 * - 不填则交给服务端默认值，不做校验
 */
const validateContextWindow = async (_rule: any, value: any) => {
  if (value === undefined || value === null || value === '') return
  const contextWindow = Number(value)
  if (!Number.isFinite(contextWindow) || contextWindow < 1024) {
    throw new Error('上下文窗口至少1024')
  }
  const maxTokens = Number(formModel.value.maxTokens ?? 4096)
  if (Number.isFinite(maxTokens) && maxTokens > 0 && contextWindow <= maxTokens + 512) {
    throw new Error('上下文窗口必须大于输出上限加安全余量512，否则没有输入预算')
  }
}

/** 最大 Token 改动后重新校验上下文窗口（两个字段互相制约） */
const revalidateContextWindow = () => {
  const contextWindow = formModel.value.contextWindow
  if (contextWindow === undefined || contextWindow === null) return
  const result = formRef.value?.validateFields?.(['contextWindow'])
  // 校验失败时错误文案由表单项自己显示，这里只吞掉 Promise 的 reject
  if (result && typeof result.catch === 'function') result.catch(() => { /* 见上 */ })
}

// 表单校验规则
const formRules = {
  modelName: [{ required: true, whitespace: true, message: '请输入模型名称', trigger: 'blur' }],
  displayName: [{ required: true, whitespace: true, message: '请输入显示名称', trigger: 'blur' }],
  contextWindow: [{ validator: validateContextWindow, trigger: 'change' }]
}

// ============== 表格展示辅助 ==============
/**
 * 参数列取值：优先显示管理员配置的值；没配就显示"默认"，
 * 同时把服务端生效值带出来，避免只看到"默认"什么都判断不了。
 */
const formatTokenParam = (configured?: number | null, effective?: number | null, fallback = '默认'): string => {
  const hasConfigured = configured !== undefined && configured !== null
  const hasEffective = effective !== undefined && effective !== null
  if (hasConfigured) return String(configured)
  if (hasEffective) return `${fallback}（生效 ${effective}）`
  return fallback
}

/**
 * 配置是否被服务端限制过。
 * 只在"管理员确实配过这个字段，且生效值和配置值不一致"时才算，
 * 否则没配过的字段会被误报成"被全局上限夹了"。
 */
const isClamped = (record: ModelConfig): boolean => {
  const maxClamped = record.maxTokens !== undefined && record.maxTokens !== null
    && record.effectiveMaxTokens !== undefined && record.effectiveMaxTokens !== null
    && record.effectiveMaxTokens !== record.maxTokens
  const contextClamped = record.contextWindow !== undefined && record.contextWindow !== null
    && record.effectiveContextWindow !== undefined && record.effectiveContextWindow !== null
    && record.effectiveContextWindow !== record.contextWindow
  return maxClamped || contextClamped
}

/** 生效值提示文案：过去是静默夹取，这里显式告诉管理员配置被限制了 */
const effectiveTip = (record: ModelConfig): string => {
  const output = record.effectiveMaxTokens ?? record.maxTokens ?? '-'
  const context = record.effectiveContextWindow ?? record.contextWindow ?? '-'
  return `实际生效：输出 ${output} / 上下文 ${context}（受全局安全上限约束）`
}

/**
 * 输入预算是否被全局成本护栏限制。
 *
 * 场景：模型上下文 205000，但 spring.ai.security.model-policy-max-input-tokens=96000，
 * 于是单次请求只能用 96K 输入。不解释的话，管理员看到"上下文 205000 / 输入预算 96000"
 * 会以为算错了 —— 这正是过去"配了不知道为什么"的老毛病。
 */
const isInputCapped = (record: ModelConfig): boolean => record.inputCappedByPolicy === true

/** 护栏提示文案 */
const inputCapTip = (record: ModelConfig): string => {
  const context = record.effectiveContextWindow ?? record.contextWindow ?? '-'
  return `输入预算 ${record.inputBudgetTokens ?? '-'} 受全局成本护栏限制（模型上下文 ${context}），如需放开请调整 spring.ai.security.model-policy-max-input-tokens`
}

// ============== 特殊操作 ==============
const runMutation = async (key: string, action: () => Promise<void>, success: string) => {
  if (actionsDisabled.value) return
  mutationBusy.value = key
  try {
    await action()
    message.success(success)
    await load()
  } catch (error: any) {
    if (!error?.isBusiness) message.error('操作失败，请检查网络后重试')
    throw error
  } finally {
    mutationBusy.value = null
  }
}
const setDefaultModel = (record: ModelConfig) => {
  if (actionsDisabled.value || confirmationOpen.value || record.isDefault || !record.isEnabled) return
  confirmationOpen.value = true
  Modal.confirm({
    title: `将「${record.displayName}」设为全局默认？`,
    content: '聊天、写作及跟随默认模型的评论角色会使用此模型，已有角色的单独指定模型不变。',
    okText: '设为默认', cancelText: '取消',
    afterClose: () => { confirmationOpen.value = false },
    onOk: () => runMutation(`default:${record.id}`, () => aiModelsService.setDefaultModel(record.id), '全局默认模型已更新')
  })
}
const toggleEnabled = async (record: ModelConfig) => {
  if (record.isDefault || actionsDisabled.value) return
  try { await runMutation(`toggle:${record.id}`, () => aiModelsService.toggleEnabled(record.id, !record.isEnabled), record.isEnabled ? '模型已禁用' : '模型已启用') } catch { /* 失败由操作入口提示，保留当前列表 */ }
}
const removeModel = (record: ModelConfig) => {
  if (record.isDefault || actionsDisabled.value || confirmationOpen.value) return
  confirmationOpen.value = true
  Modal.confirm({
    title: '确认删除模型',
    content: `确定删除 "${record.displayName}" 吗？`,
    okText: '删除',
    okType: 'danger',
    cancelText: '取消',
    afterClose: () => { confirmationOpen.value = false },
    onOk: () => runMutation(`delete:${record.id}`, () => aiModelsService.deleteModel(record.id), '模型已删除')
  })
}
const submitModel = async () => {
  if (confirmLoading.value || mutationBusy.value) return
  await handleOk()
}
const refreshModels = () => { if (!loading.value && !mutationBusy.value && !confirmLoading.value) void load() }
</script>

<template>
  <div class="p-24">
    <div class="page-heading">
      <div><h2>模型配置</h2><p>全局文本模型统一用于聊天、写作与评论；语音引擎在 <router-link to="/ai-settings">语音服务</router-link> 配置。</p></div>
      <a-space wrap>
        <a-button @click="refreshModels" :loading="loading" :disabled="!!mutationBusy || confirmLoading"><ReloadOutlined /> 重新读取</a-button>
        <a-button type="primary" @click="openCreate" :disabled="actionsDisabled"><PlusOutlined /> 新增模型</a-button>
      </a-space>
    </div>
    <a-alert v-if="loadError" class="mb-16" type="error" show-icon :message="loadError" />
    <div class="model-summary mb-16">
      <span>全局默认 <strong>{{ defaultModelText }}</strong></span>
      <a-tag v-if="loadError" color="orange">上次读取结果</a-tag>
      <span v-if="modelsLoaded">已启用 {{ enabledModelCount }} / {{ dataSource.length }}</span>
      <span class="summary-hint">角色可单独指定模型；未指定时跟随全局默认。</span>
    </div>
    <a-card :bordered="false" class="models-card">
      <template #title>
        <span><RobotOutlined /> 可用模型</span>
      </template>
      <template #extra>
        <a-space>
          <a-input-search
            v-model:value="searchText"
            placeholder="搜索模型"
            style="width: 220px"
            allow-clear
          />
        </a-space>
      </template>

      <a-table :data-source="filteredModels" :loading="loading" :pagination="false" :scroll="{ x: 950 }" row-key="id" size="small">
        <a-table-column title="模型" key="model" :ellipsis="true">
          <template #default="{ record }">
            <div class="model-main">
              <div class="model-title">
                <span>{{ record.displayName }}</span>
                <a-tag v-if="record.isDefault" color="gold">
                  <StarFilled /> 默认
                </a-tag>
              </div>
              <div class="model-name">{{ record.modelName }}</div>
              <div v-if="record.description" class="model-desc" :title="record.description">{{ record.description }}</div>
            </div>
          </template>
        </a-table-column>

        <a-table-column title="参数" key="params" width="280">
          <template #default="{ record }">
            <div class="param-text">上下文窗口：{{ formatTokenParam(record.contextWindow, record.effectiveContextWindow) }}</div>
            <div class="param-text">输出上限：{{ formatTokenParam(record.maxTokens, record.effectiveMaxTokens) }}</div>
            <div class="param-text">输入预算：{{ formatTokenParam(record.inputBudgetTokens, null, '未知') }}</div>
            <a-tooltip v-if="isClamped(record) || isInputCapped(record)" :title="[isClamped(record) ? effectiveTip(record) : '', isInputCapped(record) ? inputCapTip(record) : ''].filter(Boolean).join('；')">
              <a-tag color="orange">受安全上限约束</a-tag>
            </a-tooltip>
          </template>
        </a-table-column>

        <a-table-column title="状态" key="status" width="120">
          <template #default="{ record }">
            <a-badge :status="record.isEnabled ? 'success' : 'default'" :text="record.isEnabled ? '已启用' : '已禁用'" />
          </template>
        </a-table-column>

        <a-table-column title="操作" key="action" width="300" fixed="right">
          <template #default="{ record }">
            <a-space>
              <a-button v-if="!record.isDefault" type="link" size="small" :disabled="actionsDisabled || !record.isEnabled" :loading="mutationBusy === `default:${record.id}`" @click="setDefaultModel(record)">设为默认</a-button>
              <a-tooltip :title="record.isDefault ? '先将其他启用模型设为默认，再禁用此模型' : ''">
                <a-button type="link" size="small" :disabled="actionsDisabled || record.isDefault" :loading="mutationBusy === `toggle:${record.id}`" @click="toggleEnabled(record)">{{ record.isEnabled ? '禁用' : '启用' }}</a-button>
              </a-tooltip>
              <a-button type="link" size="small" :disabled="actionsDisabled" @click="openEdit(record)">编辑</a-button>
              <a-button v-if="!record.isDefault" type="link" size="small" danger :disabled="actionsDisabled" :loading="mutationBusy === `delete:${record.id}`" @click="removeModel(record)">删除</a-button>
            </a-space>
          </template>
        </a-table-column>
      </a-table>
    </a-card>

    <a-modal
      v-model:open="modalVisible"
      :title="modalTitle"
      @ok="submitModel"
      @cancel="() => { if (!confirmLoading) handleCancel() }"
      :confirm-loading="confirmLoading"
      :cancel-button-props="{ disabled: confirmLoading }"
      :closable="!confirmLoading"
      :mask-closable="!confirmLoading"
      :keyboard="!confirmLoading"
    >
      <a-form ref="formRef" :model="formModel" :rules="formRules" layout="vertical" :disabled="confirmLoading">
        <!-- 预设只在新增态出现：编辑态后端不允许改 modelName，套预设没有意义 -->
        <a-form-item v-if="!isEdit" label="从预设填充">
          <a-select
            v-model:value="selectedPresetKey"
            class="full-width"
            placeholder="选择常见模型，自动填好上下文窗口 / 最大 Token / 温度"
            allow-clear
            @change="applyPreset"
          >
            <a-select-option v-for="preset in MODEL_PRESETS" :key="preset.key" :value="preset.key">
              {{ preset.displayName }}（上下文 {{ preset.contextWindow }} / 输出 {{ preset.maxTokens }}）
            </a-select-option>
          </a-select>
          <div class="param-text mt-4">预设只帮助填写，提交前请按供应商当前规格核对。</div>
        </a-form-item>
        <a-form-item label="显示名称" name="displayName">
          <a-input v-model:value="formModel.displayName" placeholder="例如：DeepSeek-V3.2" />
        </a-form-item>
        <a-form-item label="模型名称" name="modelName">
          <a-input v-model:value="formModel.modelName" :disabled="isEdit" placeholder="例如：deepseek-ai/DeepSeek-V3.2" />
        </a-form-item>
        <a-form-item label="最大 Token">
          <a-input-number
            v-model:value="formModel.maxTokens"
            :min="1"
            :step="1024"
            class="full-width"
            placeholder="例如：16384"
            @change="revalidateContextWindow"
          />
          <div class="param-text mt-4">本轮输出上限，工具循环也累计在内；达到上限的写作结果不会应用。</div>
        </a-form-item>
        <a-form-item label="上下文窗口" name="contextWindow">
          <a-input-number
            v-model:value="formModel.contextWindow"
            :min="1024"
            :step="1024"
            class="full-width"
            placeholder="例如：205000"
          />
          <div class="param-text mt-4">
            输入预算 = 上下文窗口 − 输出上限 − 512；含历史、完整草稿、工具定义与返回值，按保守预算检查。
          </div>
        </a-form-item>
        <a-form-item label="Temperature">
          <a-input-number v-model:value="formModel.temperature" :min="0" :max="1" :step="0.1" class="full-width" />
        </a-form-item>
        <a-form-item label="排序">
          <a-input-number v-model:value="formModel.sortOrder" :min="0" class="full-width" />
        </a-form-item>
        <a-form-item label="描述">
          <a-textarea v-model:value="formModel.description" :rows="3" placeholder="写给你自己看的维护备注即可" />
        </a-form-item>
        <a-form-item>
          <a-checkbox v-model:checked="formModel.isEnabled" :disabled="isEdit && editingId === defaultModel?.id">启用模型</a-checkbox>
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<style scoped>
.page-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; margin-bottom: 16px; }
.page-heading h2 { margin: 0 0 4px; font-size: 20px; }
.page-heading p { margin: 0; color: var(--lt-color-text-secondary); }
.model-summary { display: flex; flex-wrap: wrap; align-items: center; gap: 12px; padding: 12px 16px; background: var(--lt-color-bg-container); border-radius: var(--lt-radius-lg); }
.summary-hint { margin-left: auto; color: var(--lt-color-text-secondary); font-size: 12px; }
.model-desc { display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; }
@media (max-width: 900px) { .page-heading { flex-direction: column; } .summary-hint { margin-left: 0; width: 100%; } }
.models-card {
  border-radius: var(--lt-radius-xl);
  box-shadow: var(--lt-shadow-xs);
}

.model-main {
  display: flex;
  flex-direction: column;
  gap: var(--lt-space-xs);
}

.model-title {
  display: flex;
  align-items: center;
  gap: var(--lt-space-sm);
  font-weight: var(--lt-font-weight-semibold);
  color: var(--text-main);
}

.model-name {
  font-size: var(--lt-font-size-xs);
  color: var(--text-secondary);
  font-family: var(--lt-font-family-mono);
}

.model-desc,
.param-text {
  font-size: var(--lt-font-size-xs);
  color: var(--text-secondary);
}

.full-width {
  width: 100%;
}

</style>
