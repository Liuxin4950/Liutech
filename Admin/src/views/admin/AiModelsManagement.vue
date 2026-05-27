<script setup lang="ts">
import { computed, ref } from 'vue'
import { useTablePage, useModalForm } from '@/composables'
import aiModelsService from '@/services/aiModels'
import type { ModelConfig, ModelConfigRequest } from '@/services/aiModels'
import { message, Modal } from 'ant-design-vue'
import {
  ReloadOutlined,
  PlusOutlined,
  EditOutlined,
  DeleteOutlined,
  CheckCircleOutlined,
  StopOutlined,
  StarOutlined,
  StarFilled,
  RobotOutlined
} from '@ant-design/icons-vue'

// ============== 表格页面 ==============
const {
  loading, dataSource,
  load
} = useTablePage<ModelConfig, { keyword: string }>({
  loadFn: async () => {
    const result = await aiModelsService.getModelList()
    const list = Array.isArray(result) ? result
      : Array.isArray((result as any)?.data) ? (result as any).data
      : []
    return { code: 200, message: 'ok', data: { records: list as ModelConfig[], total: list.length } }
  },
  defaultSearchParams: { keyword: '' },
  autoLoad: true,
  loadErrorMessage: '加载模型失败'
})

// 客户端搜索过滤
const searchText = ref('')
const filteredModels = computed(() => {
  const keyword = searchText.value.trim().toLowerCase()
  if (!keyword) return dataSource.value
  return dataSource.value.filter(item =>
    item.displayName.toLowerCase().includes(keyword) ||
    item.modelName.toLowerCase().includes(keyword) ||
    (item.description || '').toLowerCase().includes(keyword)
  )
})

// ============== 弹窗表单 ==============
const defaultModelForm = (): ModelConfigRequest => ({
  modelName: '',
  displayName: '',
  provider: 'siliconflow',
  isEnabled: true,
  sortOrder: 0,
  maxTokens: undefined,
  temperature: undefined,
  description: ''
})

const {
  modalVisible, modalTitle, isEdit, editingId, confirmLoading,
  formRef, formModel, openCreate: baseOpenCreate, handleOk, handleCancel
} = useModalForm<ModelConfigRequest>({
  createFn: async (data) => {
    try {
      const result = await aiModelsService.addModel(data as ModelConfigRequest)
      return result && typeof result === 'object' && 'code' in result ? result : { code: 200, data: result }
    } catch (e: any) {
      return { code: 500, message: e?.message || '创建失败', data: null }
    }
  },
  updateFn: async (id, data) => {
    try {
      const result = await aiModelsService.updateModel(id, data as ModelConfigRequest)
      return result && typeof result === 'object' && 'code' in result ? result : { code: 200, data: result }
    } catch (e: any) {
      return { code: 500, message: e?.message || '更新失败', data: null }
    }
  },
  defaultForm: defaultModelForm,
  onCreateSuccess: load,
  onUpdateSuccess: load,
  entityName: '模型'
})

// 覆盖 openCreate：自定义标题和默认排序
const openCreate = () => {
  baseOpenCreate()
  modalTitle.value = '新增模型'
  formModel.value.sortOrder = dataSource.value.length
}

// 覆盖 openEdit：精确映射字段
const openEdit = (record: ModelConfig) => {
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
    temperature: record.temperature,
    description: record.description
  }
  modalVisible.value = true
}

// 表单校验规则
const formRules = {
  modelName: [{ required: true, message: '请输入模型名称', trigger: 'blur' }],
  displayName: [{ required: true, message: '请输入显示名称', trigger: 'blur' }]
}

// ============== 特殊操作 ==============
const setDefaultModel = async (record: ModelConfig) => {
  try {
    await aiModelsService.setDefaultModel(record.id)
    message.success('默认模型已更新')
    await load()
  } catch (error: any) {
    message.error(error?.message || '设置默认模型失败')
  }
}

const toggleEnabled = async (record: ModelConfig) => {
  try {
    await aiModelsService.toggleEnabled(record.id, !record.isEnabled)
    message.success(record.isEnabled ? '模型已禁用' : '模型已启用')
    await load()
  } catch (error: any) {
    message.error(error?.message || '切换模型状态失败')
  }
}

const removeModel = (record: ModelConfig) => {
  Modal.confirm({
    title: '确认删除模型',
    content: `确定删除 "${record.displayName}" 吗？`,
    okText: '删除',
    okType: 'danger',
    cancelText: '取消',
    onOk: async () => {
      try {
        await aiModelsService.deleteModel(record.id)
        message.success('模型已删除')
        await load()
      } catch (error: any) {
        message.error(error?.message || '删除失败')
      }
    }
  })
}
</script>

<template>
  <div class="p-24">
    <a-card :bordered="false" class="models-card">
      <template #title>
        <div class="title-row">
          <div class="title-left">
            <RobotOutlined />
            <span>AI 模型</span>
          </div>
          <div class="title-sub">这里只维护博客前台真正会用到的模型和默认值。</div>
        </div>
      </template>
      <template #extra>
        <a-space>
          <a-input-search
            v-model:value="searchText"
            placeholder="搜索模型"
            style="width: 220px"
            allow-clear
          />
          <a-button @click="load" :loading="loading">
            <template #icon><ReloadOutlined /></template>
            刷新
          </a-button>
          <a-button type="primary" @click="openCreate">
            <template #icon><PlusOutlined /></template>
            新增模型
          </a-button>
        </a-space>
      </template>

      <a-table :data-source="filteredModels" :loading="loading" :pagination="false" row-key="id" size="small">
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
              <div v-if="record.description" class="model-desc">{{ record.description }}</div>
            </div>
          </template>
        </a-table-column>

        <a-table-column title="参数" key="params" width="220">
          <template #default="{ record }">
            <div class="param-text">Max Tokens：{{ record.maxTokens || '默认' }}</div>
            <div class="param-text">Temperature：{{ record.temperature ?? '默认' }}</div>
            <div class="param-text">排序：{{ record.sortOrder ?? 0 }}</div>
          </template>
        </a-table-column>

        <a-table-column title="状态" key="status" width="120">
          <template #default="{ record }">
            <a-badge :status="record.isEnabled ? 'success' : 'default'" :text="record.isEnabled ? '已启用' : '已禁用'" />
          </template>
        </a-table-column>

        <a-table-column title="操作" key="action" width="220">
          <template #default="{ record }">
            <a-space>
              <a-tooltip v-if="!record.isDefault" title="设为默认">
                <a-button type="link" size="small" @click="setDefaultModel(record)">
                  <StarOutlined class="text-gold" />
                </a-button>
              </a-tooltip>
              <a-tooltip :title="record.isEnabled ? '禁用' : '启用'">
                <a-button type="link" size="small" @click="toggleEnabled(record)">
                  <component :is="record.isEnabled ? StopOutlined : CheckCircleOutlined" :class="record.isEnabled ? 'text-red' : 'text-green'" />
                </a-button>
              </a-tooltip>
              <a-tooltip title="编辑">
                <a-button type="link" size="small" @click="openEdit(record)">
                  <EditOutlined class="text-blue" />
                </a-button>
              </a-tooltip>
              <a-tooltip v-if="!record.isDefault" title="删除">
                <a-button type="link" size="small" danger @click="removeModel(record)">
                  <DeleteOutlined />
                </a-button>
              </a-tooltip>
            </a-space>
          </template>
        </a-table-column>
      </a-table>
    </a-card>

    <a-modal
      v-model:open="modalVisible"
      :title="modalTitle"
      @ok="handleOk"
      :confirm-loading="confirmLoading"
    >
      <a-form ref="formRef" :model="formModel" :rules="formRules" layout="vertical">
        <a-form-item label="显示名称" name="displayName">
          <a-input v-model:value="formModel.displayName" placeholder="例如：GLM-4.6" />
        </a-form-item>
        <a-form-item label="模型名称" name="modelName">
          <a-input v-model:value="formModel.modelName" :disabled="isEdit" placeholder="例如：zai-org/GLM-4.6" />
        </a-form-item>
        <a-form-item label="最大 Token">
          <a-input-number v-model:value="formModel.maxTokens" :min="0" :step="1024" class="full-width" />
        </a-form-item>
        <a-form-item label="Temperature">
          <a-input-number v-model:value="formModel.temperature" :min="0" :max="2" :step="0.1" class="full-width" />
        </a-form-item>
        <a-form-item label="排序">
          <a-input-number v-model:value="formModel.sortOrder" :min="0" class="full-width" />
        </a-form-item>
        <a-form-item label="描述">
          <a-textarea v-model:value="formModel.description" :rows="3" placeholder="写给你自己看的维护备注即可" />
        </a-form-item>
        <a-form-item>
          <a-checkbox v-model:checked="formModel.isEnabled">立即启用</a-checkbox>
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<style scoped>
.models-card {
  border-radius: 12px;
  box-shadow: 0 1px 2px 0 rgba(0, 0, 0, 0.03);
}

.title-row {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.title-left {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 700;
}

.title-sub {
  font-size: 12px;
  color: var(--text-secondary);
}

.model-main {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.model-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
  color: var(--text-main);
}

.model-name {
  font-size: 12px;
  color: var(--text-secondary);
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
}

.model-desc,
.param-text {
  font-size: 12px;
  color: var(--text-secondary);
}

.full-width {
  width: 100%;
}

.text-gold { color: var(--color-warning); }
.text-red { color: var(--color-error); }
.text-green { color: var(--color-success); }
.text-blue { color: var(--color-primary); }
</style>