<script setup lang="ts">
import { ref, onMounted, onUnmounted, nextTick, computed } from 'vue'
import aiModelsService from '@/services/aiModels'
import type { ModelConfig, ModelConfigRequest, ModelUsageStats } from '@/services/aiModels'
import { message, Modal } from 'ant-design-vue'
import {
  RobotOutlined,
  BarChartOutlined,
  ReloadOutlined,
  PlusOutlined,
  EditOutlined,
  DeleteOutlined,
  CheckCircleOutlined,
  StopOutlined,
  StarOutlined,
  StarFilled
} from '@ant-design/icons-vue'
import * as echarts from 'echarts'
import type { ECharts } from 'echarts'

// ============ 数据状态 ============
const modelList = ref<ModelConfig[]>([])
const usageStats = ref<ModelUsageStats[]>([])
const loading = ref(true)
const tableLoading = ref(false)

// ============ 图表相关 ============
const usageChart = ref<ECharts | null>(null)
const usageChartRef = ref<HTMLElement | null>(null)

// ============ 弹窗相关 ============
const modalVisible = ref(false)
const modalTitle = computed(() => isEditMode.value ? '编辑模型' : '添加模型')
const isEditMode = ref(false)
const editingId = ref<number | null>(null)

const formRef = ref()
const formData = ref<ModelConfigRequest>({
  modelName: '',
  displayName: '',
  provider: 'siliconflow',
  isEnabled: true,
  sortOrder: 0,
  maxTokens: undefined,
  temperature: undefined,
  description: ''
})

const formRules = {
  modelName: [{ required: true, message: '请输入模型名称', trigger: 'blur' }],
  displayName: [{ required: true, message: '请输入显示名称', trigger: 'blur' }],
  provider: [{ required: true, message: '请输入提供商', trigger: 'blur' }]
}

// ============ 表格列定义 ============
const columns = [
  { title: '显示名称', dataIndex: 'displayName', key: 'displayName' },
  { title: '模型名称', dataIndex: 'modelName', key: 'modelName' },
  { title: '提供商', dataIndex: 'provider', key: 'provider' },
  {
    title: '状态',
    key: 'status',
    width: 120
  },
  {
    title: '排序',
    dataIndex: 'sortOrder',
    key: 'sortOrder',
    width: 80
  },
  { title: '描述', dataIndex: 'description', key: 'description', ellipsis: true },
  {
    title: '操作',
    key: 'action',
    width: 280
  }
]

// ============ 数据加载 ============
const loadAllData = async () => {
  try {
    loading.value = true
    const [models, stats] = await Promise.all([
      aiModelsService.getModelList(),
      aiModelsService.getTodayModelUsage()
    ])
    modelList.value = models
    usageStats.value = stats
    await nextTick()
    setTimeout(() => initUsageChart(), 100)
  } catch (error: any) {
    message.error(error.message || '加载数据失败')
  } finally {
    loading.value = false
  }
}

// ============ 图表初始化 ============
const initUsageChart = () => {
  if (!usageChartRef.value || usageStats.value.length === 0) return

  if (usageChart.value) {
    usageChart.value.dispose()
  }

  usageChart.value = echarts.init(usageChartRef.value)

  const models = usageStats.value.map(item => {
    const config = modelList.value.find(m => m.modelName === item.model)
    return config?.displayName || item.model
  })
  const counts = usageStats.value.map(item => item.usageCount)

  const option: echarts.EChartsOption = {
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      backgroundColor: 'rgba(255, 255, 255, 0.95)',
      borderColor: '#e5e7eb',
      borderWidth: 1,
      textStyle: { color: '#374151' }
    },
    grid: {
      left: '3%',
      right: '4%',
      bottom: '3%',
      top: '10%',
      containLabel: true
    },
    xAxis: {
      type: 'category',
      data: models,
      axisLine: { lineStyle: { color: '#e5e7eb' } },
      axisLabel: {
        color: '#6b7280',
        fontSize: 12,
        interval: 0,
        rotate: 30
      }
    },
    yAxis: {
      type: 'value',
      name: '使用次数',
      axisLine: { show: false },
      axisLabel: { color: '#6b7280', fontSize: 12 },
      splitLine: { lineStyle: { color: '#f3f4f6', type: 'dashed' } }
    },
    series: [{
      name: '使用次数',
      type: 'bar',
      data: counts,
      barWidth: '50%',
      itemStyle: {
        color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: '#3b82f6' },
          { offset: 1, color: '#1d4ed8' }
        ]),
        borderRadius: [6, 6, 0, 0]
      },
      emphasis: {
        itemStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: '#60a5fa' },
            { offset: 1, color: '#2563eb' }
          ])
        }
      },
      label: {
        show: true,
        position: 'top',
        color: '#6b7280',
        fontSize: 12
      }
    }]
  }

  usageChart.value.setOption(option)
}

// ============ 弹窗操作 ============
const showAddModal = () => {
  isEditMode.value = false
  editingId.value = null
  formData.value = {
    modelName: '',
    displayName: '',
    provider: 'siliconflow',
    isEnabled: true,
    sortOrder: modelList.value.length,
    maxTokens: undefined,
    temperature: undefined,
    description: ''
  }
  modalVisible.value = true
}

const showEditModal = (record: ModelConfig) => {
  isEditMode.value = true
  editingId.value = record.id
  formData.value = {
    modelName: record.modelName,
    displayName: record.displayName,
    provider: record.provider,
    isEnabled: record.isEnabled,
    sortOrder: record.sortOrder,
    maxTokens: record.maxTokens,
    temperature: record.temperature,
    description: record.description
  }
  modalVisible.value = true
}

const handleModalOk = async () => {
  try {
    await formRef.value.validate()
    tableLoading.value = true

    if (isEditMode.value && editingId.value) {
      await aiModelsService.updateModel(editingId.value, formData.value)
      message.success('模型更新成功')
    } else {
      await aiModelsService.addModel(formData.value)
      message.success('模型添加成功')
    }

    modalVisible.value = false
    await loadAllData()
  } catch (error: any) {
    if (error.errorFields) {
      // 表单验证错误
      return
    }
    message.error(error.message || '操作失败')
  } finally {
    tableLoading.value = false
  }
}

const handleModalCancel = () => {
  modalVisible.value = false
  formRef.value?.resetFields()
}

// ============ 表格操作 ============
const handleSetDefault = async (id: number) => {
  try {
    await aiModelsService.setDefaultModel(id)
    message.success('默认模型设置成功')
    await loadAllData()
  } catch (error: any) {
    message.error(error.message || '设置失败')
  }
}

const handleToggleEnabled = async (record: ModelConfig) => {
  try {
    await aiModelsService.toggleEnabled(record.id, !record.isEnabled)
    message.success(record.isEnabled ? '模型已禁用' : '模型已启用')
    await loadAllData()
  } catch (error: any) {
    message.error(error.message || '操作失败')
  }
}

const handleDelete = (record: ModelConfig) => {
  Modal.confirm({
    title: '确认删除',
    content: `确定要删除模型 "${record.displayName}" 吗？此操作不可恢复。`,
    okText: '确定',
    cancelText: '取消',
    onOk: async () => {
      try {
        await aiModelsService.deleteModel(record.id)
        message.success('模型删除成功')
        await loadAllData()
      } catch (error: any) {
        message.error(error.message || '删除失败')
      }
    }
  })
}

const handleRefresh = () => {
  loadAllData()
}

// ============ 生命周期 ============
const handleResize = () => {
  usageChart.value?.resize()
}

onMounted(() => {
  loadAllData()
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  usageChart.value?.dispose()
})
</script>

<template>
  <div class="ai-models-container">
    <!-- 顶部标题栏 -->
    <div class="header-row">
      <div>
        <h1 class="page-title">AI模型管理</h1>
        <p class="page-description">管理可用的AI模型，设置默认模型，查看今日使用统计</p>
      </div>
      <a-button type="text" :loading="loading" @click="handleRefresh">
        <template #icon><ReloadOutlined /></template>
        刷新
      </a-button>
    </div>

    <a-row :gutter="24">
      <!-- 左侧: 模型列表 -->
      <a-col :xs="24" :lg="14">
        <a-card :bordered="false" class="content-card">
          <template #title>
            <div class="card-title">
              <RobotOutlined class="mr-8" />
              <span>模型配置</span>
            </div>
          </template>
          <template #extra>
            <a-button type="primary" size="small" @click="showAddModal">
              <PlusOutlined /> 添加模型
            </a-button>
          </template>

          <a-table
            :columns="columns"
            :data-source="modelList"
            :loading="tableLoading"
            :pagination="{ pageSize: 10 }"
            row-key="id"
            size="small"
          >
            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'status'">
                <a-space>
                  <a-tag v-if="record.isDefault" color="gold" class="default-tag">
                    <StarFilled /> 默认
                  </a-tag>
                  <a-tag :color="record.isEnabled ? 'success' : 'default'">
                    <CheckCircleOutlined v-if="record.isEnabled" />
                    <StopOutlined v-else />
                    {{ record.isEnabled ? '已启用' : '已禁用' }}
                  </a-tag>
                </a-space>
              </template>
              <template v-else-if="column.key === 'action'">
                <a-space size="small">
                  <a-button
                    v-if="!record.isDefault"
                    type="link"
                    size="small"
                    @click="handleSetDefault(record.id)"
                  >
                    <StarOutlined /> 设为默认
                  </a-button>
                  <a-button type="link" size="small" @click="handleToggleEnabled(record)">
                    {{ record.isEnabled ? '禁用' : '启用' }}
                  </a-button>
                  <a-button type="link" size="small" @click="showEditModal(record)">
                    <EditOutlined /> 编辑
                  </a-button>
                  <a-popconfirm
                    v-if="!record.isDefault"
                    title="确定要删除这个模型吗？"
                    ok-text="确定"
                    cancel-text="取消"
                    @confirm="handleDelete(record)"
                  >
                    <a-button type="link" size="small" danger>
                      <DeleteOutlined /> 删除
                    </a-button>
                  </a-popconfirm>
                </a-space>
              </template>
            </template>
          </a-table>
        </a-card>
      </a-col>

      <!-- 右侧: 今日使用统计 -->
      <a-col :xs="24" :lg="10">
        <a-card :bordered="false" class="content-card">
          <template #title>
            <div class="card-title">
              <BarChartOutlined class="mr-8" />
              <span>今日模型使用统计</span>
            </div>
          </template>

          <div v-if="usageStats.length === 0 && !loading" class="empty-state">
            <a-empty description="今日暂无使用记录" />
          </div>

          <div v-else ref="usageChartRef" class="echarts-box"></div>
        </a-card>
      </a-col>
    </a-row>

    <!-- 添加/编辑弹窗 -->
    <a-modal
      v-model:open="modalVisible"
      :title="modalTitle"
      @ok="handleModalOk"
      @cancel="handleModalCancel"
      :confirm-loading="tableLoading"
    >
      <a-form
        ref="formRef"
        :model="formData"
        :rules="formRules"
        layout="vertical"
      >
        <a-form-item label="模型名称" name="modelName">
          <a-input
            v-model:value="formData.modelName"
            placeholder="如: zai-org/GLM-4.6"
            :disabled="isEditMode"
          />
        </a-form-item>
        <a-form-item label="显示名称" name="displayName">
          <a-input
            v-model:value="formData.displayName"
            placeholder="如: GLM-4.6"
          />
        </a-form-item>
        <a-form-item label="提供商" name="provider">
          <a-select v-model:value="formData.provider">
            <a-select-option value="siliconflow">硅基流动</a-select-option>
            <a-select-option value="openai">OpenAI</a-select-option>
            <a-select-option value="ollama">Ollama</a-select-option>
            <a-select-option value="other">其他</a-select-option>
          </a-select>
        </a-form-item>
        <a-row :gutter="16">
          <a-col :span="12">
            <a-form-item label="启用状态" name="isEnabled">
              <a-switch v-model:checked="formData.isEnabled" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="排序顺序" name="sortOrder">
              <a-input-number
                v-model:value="formData.sortOrder"
                :min="0"
                style="width: 100%"
              />
            </a-form-item>
          </a-col>
        </a-row>
        <a-row :gutter="16">
          <a-col :span="12">
            <a-form-item label="最大Token数" name="maxTokens">
              <a-input-number
                v-model:value="formData.maxTokens"
                :min="0"
                :step="1024"
                style="width: 100%"
                placeholder="默认: 8192"
              />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="温度参数" name="temperature">
              <a-input-number
                v-model:value="formData.temperature"
                :min="0"
                :max="2"
                :step="0.1"
                style="width: 100%"
                placeholder="默认: 0.9"
              />
            </a-form-item>
          </a-col>
        </a-row>
        <a-form-item label="描述" name="description">
          <a-textarea
            v-model:value="formData.description"
            :rows="3"
            placeholder="模型功能描述"
          />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<style scoped>
.ai-models-container {
  padding: 24px;
}

.header-row {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 24px;
}

.page-title {
  font-size: 24px;
  font-weight: 700;
  color: #1f2937;
  margin: 0 0 4px 0;
}

.page-description {
  color: #6b7280;
  margin: 0;
  font-size: 14px;
}

.mr-8 {
  margin-right: 8px;
}

.content-card {
  border-radius: 12px;
  margin-bottom: 24px;
}

.card-title {
  display: flex;
  align-items: center;
  font-size: 16px;
  font-weight: 600;
  color: #262626;
}

.default-tag {
  font-weight: 600;
}

.echarts-box {
  width: 100%;
  height: 350px;
  min-height: 350px;
}

.empty-state {
  padding: 60px 20px;
  text-align: center;
}

:deep(.ant-table) {
  font-size: 13px;
}

:deep(.ant-table-thead > tr > th) {
  background: #f9fafb;
  font-weight: 600;
  color: #374151;
}

:deep(.ant-table-tbody > tr:hover) {
  background: #f9fafb;
}
</style>
