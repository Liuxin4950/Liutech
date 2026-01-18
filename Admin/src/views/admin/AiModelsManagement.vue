<script setup lang="ts">
import { ref, onMounted, onUnmounted, nextTick, computed, watch } from 'vue'
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
  StarFilled,
  AppstoreOutlined,
  FireOutlined,
  ThunderboltOutlined,
  CloudServerOutlined,
  SearchOutlined,
  SettingOutlined,
  ExperimentOutlined
} from '@ant-design/icons-vue'
import * as echarts from 'echarts'
import type { ECharts } from 'echarts'

// ============ 数据状态 ============
const modelList = ref<ModelConfig[]>([])
const usageStats = ref<ModelUsageStats[]>([])
const loading = ref(true)
const tableLoading = ref(false)
const searchText = ref('')

// ============ 统计指标 ============
const statistics = computed(() => {
  const total = modelList.value.length
  const active = modelList.value.filter(m => m.isEnabled).length
  const totalUsage = usageStats.value.reduce((sum, item) => sum + item.usageCount, 0)
  
  let topModelName = '暂无数据'
  let topModelCount = 0
  
  if (usageStats.value.length > 0) {
    const sorted = [...usageStats.value].sort((a, b) => b.usageCount - a.usageCount)
    const top = sorted[0]
    const config = modelList.value.find(m => m.modelName === top.model)
    topModelName = config?.displayName || top.model
    topModelCount = top.usageCount
  }

  const providers = new Set(modelList.value.map(m => m.provider)).size

  return {
    total,
    active,
    totalUsage,
    topModelName,
    topModelCount,
    providers
  }
})

// ============ 过滤后的列表 ============
const filteredModelList = computed(() => {
  if (!searchText.value) return modelList.value
  const text = searchText.value.toLowerCase()
  return modelList.value.filter(item => 
    item.displayName.toLowerCase().includes(text) || 
    item.modelName.toLowerCase().includes(text) ||
    item.provider.toLowerCase().includes(text)
  )
})

// ============ 图表相关 ============
const usageChart = ref<ECharts | null>(null)
const usageChartRef = ref<HTMLElement | null>(null)
const providerChart = ref<ECharts | null>(null)
const providerChartRef = ref<HTMLElement | null>(null)

// ============ 弹窗相关 ============
const modalVisible = ref(false)
const modalTitle = computed(() => isEditMode.value ? '编辑模型配置' : '添加新模型')
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
  modelName: [{ required: true, message: '请输入模型ID/名称', trigger: 'blur' }],
  displayName: [{ required: true, message: '请输入显示名称', trigger: 'blur' }],
  provider: [{ required: true, message: '请选择服务提供商', trigger: 'change' }]
}

// ============ 表格列定义 ============
const columns = [
  { title: '模型信息', dataIndex: 'info', key: 'info', width: 250 },
  { title: '提供商', dataIndex: 'provider', key: 'provider', width: 120 },
  { title: '参数配置', dataIndex: 'params', key: 'params', width: 150 },
  { title: '状态', key: 'status', width: 100 },
  { title: '排序', dataIndex: 'sortOrder', key: 'sortOrder', width: 80, align: 'center' },
  { title: '操作', key: 'action', width: 200, fixed: 'right' }
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
    setTimeout(() => {
      initUsageChart()
      initProviderChart()
    }, 100)
  } catch (error: any) {
    message.error(error.message || '加载数据失败')
  } finally {
    loading.value = false
  }
}

// ============ 图表初始化 ============
const initUsageChart = () => {
  if (!usageChartRef.value) return
  if (usageChart.value) usageChart.value.dispose()
  
  usageChart.value = echarts.init(usageChartRef.value)
  
  const models = usageStats.value.map(item => {
    const config = modelList.value.find(m => m.modelName === item.model)
    return config?.displayName || item.model
  })
  const counts = usageStats.value.map(item => item.usageCount)

  const option: echarts.EChartsOption = {
    title: {
      text: '今日调用趋势',
      left: 'left',
      textStyle: { fontSize: 14, fontWeight: 600, color: '#1f2937' }
    },
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      backgroundColor: 'rgba(255, 255, 255, 0.95)',
      borderColor: '#e5e7eb',
      borderWidth: 1,
      padding: [8, 12],
      textStyle: { color: '#374151' }
    },
    grid: { left: '3%', right: '4%', bottom: '3%', top: '60px', containLabel: true },
    xAxis: {
      type: 'category',
      data: models,
      axisLine: { lineStyle: { color: '#f3f4f6' } },
      axisLabel: { color: '#6b7280', fontSize: 12, interval: 0, rotate: 20 }
    },
    yAxis: {
      type: 'value',
      splitLine: { lineStyle: { color: '#f3f4f6', type: 'dashed' } },
      axisLabel: { color: '#9ca3af' }
    },
    series: [{
      name: '调用次数',
      type: 'bar',
      data: counts,
      barWidth: '40%',
      itemStyle: {
        borderRadius: [4, 4, 0, 0],
        color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: '#3b82f6' },
          { offset: 1, color: '#60a5fa' }
        ])
      },
      emphasis: { itemStyle: { color: '#2563eb' } }
    }]
  }
  usageChart.value.setOption(option)
}

const initProviderChart = () => {
  if (!providerChartRef.value) return
  if (providerChart.value) providerChart.value.dispose()

  providerChart.value = echarts.init(providerChartRef.value)
  
  const providerCounts: Record<string, number> = {}
  modelList.value.forEach(m => {
    providerCounts[m.provider] = (providerCounts[m.provider] || 0) + 1
  })
  
  const data = Object.entries(providerCounts).map(([name, value]) => ({ name, value }))

  const option: echarts.EChartsOption = {
    title: {
      text: '供应商分布',
      left: 'left',
      textStyle: { fontSize: 14, fontWeight: 600, color: '#1f2937' }
    },
    tooltip: { trigger: 'item' },
    legend: { bottom: '0%', left: 'center', icon: 'circle' },
    series: [{
      name: '模型数量',
      type: 'pie',
      radius: ['40%', '70%'],
      center: ['50%', '50%'],
      avoidLabelOverlap: false,
      itemStyle: {
        borderRadius: 10,
        borderColor: '#fff',
        borderWidth: 2
      },
      label: { show: false, position: 'center' },
      emphasis: {
        label: { show: true, fontSize: 16, fontWeight: 'bold' }
      },
      data: data
    }]
  }
  providerChart.value.setOption(option)
}

// ============ 操作方法 ============
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
  formData.value = { ...record }
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
    if (!error.errorFields) message.error(error.message || '操作失败')
  } finally {
    tableLoading.value = false
  }
}

const handleSetDefault = async (id: number) => {
  try {
    await aiModelsService.setDefaultModel(id)
    message.success('已设置为默认模型')
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
    okText: '确定删除',
    okType: 'danger',
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

// ============ 生命周期 ============
const handleResize = () => {
  usageChart.value?.resize()
  providerChart.value?.resize()
}

onMounted(() => {
  loadAllData()
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  usageChart.value?.dispose()
  providerChart.value?.dispose()
})
</script>

<template>
  <div class="page-container">
    <!-- 顶部概览卡片 -->
    <a-row :gutter="[16, 16]" class="mb-24">
      <a-col :xs="24" :sm="12" :lg="6">
        <a-card :bordered="false" class="stat-card">
          <div class="stat-content">
            <div class="stat-icon bg-blue-50 text-blue-500">
              <AppstoreOutlined />
            </div>
            <div class="stat-info">
              <div class="stat-label">可用模型 / 总数</div>
              <div class="stat-value">
                <span class="text-primary">{{ statistics.active }}</span>
                <span class="text-gray-400 text-sm"> / {{ statistics.total }}</span>
              </div>
            </div>
          </div>
        </a-card>
      </a-col>
      
      <a-col :xs="24" :sm="12" :lg="6">
        <a-card :bordered="false" class="stat-card">
          <div class="stat-content">
            <div class="stat-icon bg-green-50 text-green-500">
              <ThunderboltOutlined />
            </div>
            <div class="stat-info">
              <div class="stat-label">今日总调用</div>
              <div class="stat-value">{{ statistics.totalUsage }}</div>
            </div>
          </div>
        </a-card>
      </a-col>
      
      <a-col :xs="24" :sm="12" :lg="6">
        <a-card :bordered="false" class="stat-card">
          <div class="stat-content">
            <div class="stat-icon bg-purple-50 text-purple-500">
              <FireOutlined />
            </div>
            <div class="stat-info">
              <div class="stat-label">最热门模型</div>
              <div class="stat-value text-sm truncate" :title="statistics.topModelName">
                {{ statistics.topModelName }}
              </div>
            </div>
          </div>
        </a-card>
      </a-col>
      
      <a-col :xs="24" :sm="12" :lg="6">
        <a-card :bordered="false" class="stat-card">
          <div class="stat-content">
            <div class="stat-icon bg-orange-50 text-orange-500">
              <CloudServerOutlined />
            </div>
            <div class="stat-info">
              <div class="stat-label">接入供应商</div>
              <div class="stat-value">{{ statistics.providers }}</div>
            </div>
          </div>
        </a-card>
      </a-col>
    </a-row>

    <!-- 图表区域 -->
    <a-row :gutter="[16, 16]" class="mb-24">
      <a-col :xs="24" :lg="16">
        <a-card :bordered="false" class="chart-card">
          <div ref="usageChartRef" class="h-80 w-full"></div>
        </a-card>
      </a-col>
      <a-col :xs="24" :lg="8">
        <a-card :bordered="false" class="chart-card">
          <div ref="providerChartRef" class="h-80 w-full"></div>
        </a-card>
      </a-col>
    </a-row>

    <!-- 列表区域 -->
    <a-card :bordered="false" class="table-card">
      <!-- 工具栏 -->
      <div class="flex justify-between items-center mb-16">
        <div class="flex items-center gap-4">
          <h2 class="text-lg font-semibold m-0 flex items-center gap-2">
            <RobotOutlined /> 模型列表
          </h2>
          <a-input-search
            v-model:value="searchText"
            placeholder="搜索模型名称/提供商"
            style="width: 250px"
            allow-clear
          />
        </div>
        <div class="flex gap-2">
          <a-button @click="loadAllData" :loading="loading">
            <template #icon><ReloadOutlined /></template>
            刷新
          </a-button>
          <a-button type="primary" @click="showAddModal">
            <template #icon><PlusOutlined /></template>
            新增模型
          </a-button>
        </div>
      </div>

      <a-table
        :columns="columns"
        :data-source="filteredModelList"
        :loading="tableLoading"
        :pagination="{ pageSize: 10, showSizeChanger: true, showTotal: (t:any) => `共 ${t} 条` }"
        row-key="id"
      >
        <!-- 模型信息列 -->
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'info'">
            <div class="flex flex-col">
              <div class="flex items-center gap-2">
                <span class="font-medium text-gray-900">{{ record.displayName }}</span>
                <a-tag v-if="record.isDefault" color="gold" class="text-xs scale-90 origin-left m-0">
                  <StarFilled /> 默认
                </a-tag>
              </div>
              <span class="text-xs text-gray-500 font-mono mt-1">{{ record.modelName }}</span>
              <span v-if="record.description" class="text-xs text-gray-400 mt-1 truncate">
                {{ record.description }}
              </span>
            </div>
          </template>

          <!-- 提供商列 -->
          <template v-else-if="column.key === 'provider'">
            <a-tag :color="record.provider === 'openai' ? 'green' : record.provider === 'siliconflow' ? 'blue' : 'default'">
              {{ record.provider }}
            </a-tag>
          </template>

          <!-- 参数配置列 -->
          <template v-else-if="column.key === 'params'">
            <div class="text-xs text-gray-500">
              <div>Tokens: {{ record.maxTokens || '默认' }}</div>
              <div>Temp: {{ record.temperature ?? '默认' }}</div>
            </div>
          </template>

          <!-- 状态列 -->
          <template v-else-if="column.key === 'status'">
            <a-badge :status="record.isEnabled ? 'success' : 'error'" :text="record.isEnabled ? '已启用' : '已禁用'" />
          </template>

          <!-- 操作列 -->
          <template v-else-if="column.key === 'action'">
            <a-space>
              <a-tooltip title="设为默认" v-if="!record.isDefault">
                <a-button type="text" size="small" @click="handleSetDefault(record.id)">
                  <StarOutlined class="text-yellow-500" />
                </a-button>
              </a-tooltip>
              
              <a-tooltip :title="record.isEnabled ? '禁用模型' : '启用模型'">
                <a-button type="text" size="small" @click="handleToggleEnabled(record)">
                  <component :is="record.isEnabled ? StopOutlined : CheckCircleOutlined" 
                    :class="record.isEnabled ? 'text-red-500' : 'text-green-500'" />
                </a-button>
              </a-tooltip>

              <a-tooltip title="编辑配置">
                <a-button type="text" size="small" @click="showEditModal(record)">
                  <EditOutlined class="text-blue-500" />
                </a-button>
              </a-tooltip>

              <a-popconfirm
                v-if="!record.isDefault"
                title="确定要删除这个模型吗？"
                ok-text="删除"
                ok-type="danger"
                @confirm="handleDelete(record)"
              >
                <a-tooltip title="删除模型">
                  <a-button type="text" size="small" danger>
                    <DeleteOutlined />
                  </a-button>
                </a-tooltip>
              </a-popconfirm>
            </a-space>
          </template>
        </template>
      </a-table>
    </a-card>

    <!-- 编辑弹窗 -->
    <a-modal
      v-model:open="modalVisible"
      :title="modalTitle"
      @ok="handleModalOk"
      :confirm-loading="tableLoading"
      :width="600"
      class="rounded-lg"
    >
      <a-form ref="formRef" :model="formData" :rules="formRules" layout="vertical" class="mt-4">
        
        <div class="bg-gray-50 p-4 rounded-md mb-4 border border-gray-100">
          <div class="text-sm font-bold text-gray-700 mb-3 flex items-center gap-2">
            <SettingOutlined /> 基础信息
          </div>
          <a-row :gutter="16">
            <a-col :span="12">
              <a-form-item label="显示名称" name="displayName">
                <a-input v-model:value="formData.displayName" placeholder="如: GPT-4 Turbo" />
              </a-form-item>
            </a-col>
            <a-col :span="12">
              <a-form-item label="提供商" name="provider">
                <a-select v-model:value="formData.provider">
                  <a-select-option value="siliconflow">硅基流动</a-select-option>
                  <a-select-option value="openai">OpenAI</a-select-option>
                  <a-select-option value="ollama">Ollama</a-select-option>
                  <a-select-option value="other">其他</a-select-option>
                </a-select>
              </a-form-item>
            </a-col>
            <a-col :span="24">
              <a-form-item label="模型ID (API Model Name)" name="modelName">
                <a-input 
                  v-model:value="formData.modelName" 
                  placeholder="如: zai-org/GLM-4.6" 
                  :disabled="isEditMode"
                  class="font-mono text-sm"
                />
              </a-form-item>
            </a-col>
          </a-row>
        </div>

        <div class="bg-gray-50 p-4 rounded-md border border-gray-100">
          <div class="text-sm font-bold text-gray-700 mb-3 flex items-center gap-2">
            <ExperimentOutlined /> 高级参数
          </div>
          <a-row :gutter="16">
            <a-col :span="8">
              <a-form-item label="排序权重" name="sortOrder">
                <a-input-number v-model:value="formData.sortOrder" :min="0" class="w-full" />
              </a-form-item>
            </a-col>
            <a-col :span="8">
              <a-form-item label="最大Token" name="maxTokens">
                <a-input-number v-model:value="formData.maxTokens" :min="0" :step="1024" class="w-full" placeholder="默认" />
              </a-form-item>
            </a-col>
            <a-col :span="8">
              <a-form-item label="温度 (Temperature)" name="temperature">
                <a-input-number v-model:value="formData.temperature" :min="0" :max="2" :step="0.1" class="w-full" placeholder="默认" />
              </a-form-item>
            </a-col>
            <a-col :span="24">
              <a-form-item label="功能描述" name="description">
                <a-textarea v-model:value="formData.description" :rows="2" placeholder="简要描述该模型的特点或用途" />
              </a-form-item>
            </a-col>
            <a-col :span="24">
              <a-form-item name="isEnabled" class="mb-0">
                <a-checkbox v-model:checked="formData.isEnabled">立即启用此模型</a-checkbox>
              </a-form-item>
            </a-col>
          </a-row>
        </div>

      </a-form>
    </a-modal>
  </div>
</template>

<style scoped>
.page-container {
  padding: 24px;
  background-color: var(--bg-main);
  min-height: 100vh;
}

.stat-card {
  height: 100%;
  border-radius: 12px;
  box-shadow: 0 1px 2px 0 rgba(0, 0, 0, 0.03);
  transition: all 0.3s;
}

.stat-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
}

.stat-content {
  display: flex;
  align-items: center;
  gap: 16px;
}

.stat-icon {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
}

.stat-info {
  flex: 1;
  overflow: hidden;
}

.stat-label {
  color: #6b7280;
  font-size: 13px;
  margin-bottom: 4px;
}

.stat-value {
  font-size: 20px;
  font-weight: 700;
  color: #1f2937;
  line-height: 1.2;
}

.chart-card {
  border-radius: 12px;
  box-shadow: 0 1px 2px 0 rgba(0, 0, 0, 0.03);
}

.table-card {
  border-radius: 12px;
  box-shadow: 0 1px 2px 0 rgba(0, 0, 0, 0.03);
}

/* Utility classes not present in AntD or Tailwind */
.bg-blue-50 { background-color: #eff6ff; }
.text-blue-500 { color: #3b82f6; }
.bg-green-50 { background-color: #f0fdf4; }
.text-green-500 { color: #22c55e; }
.bg-purple-50 { background-color: #faf5ff; }
.text-purple-500 { color: #a855f7; }
.bg-orange-50 { background-color: #fff7ed; }
.text-orange-500 { color: #f97316; }

.text-gray-400 { color: #9ca3af; }
.text-gray-500 { color: #6b7280; }
.text-gray-900 { color: #111827; }
.text-primary { color: var(--color-primary); }
.text-sm { font-size: 0.875rem; }
.text-xs { font-size: 0.75rem; }
.font-medium { font-weight: 500; }
.font-bold { font-weight: 700; }
.font-mono { font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace; }

.flex { display: flex; }
.flex-col { flex-direction: column; }
.items-center { align-items: center; }
.justify-between { justify-content: space-between; }
.gap-2 { gap: 0.5rem; }
.gap-4 { gap: 1rem; }
.mb-24 { margin-bottom: 24px; }
.mb-16 { margin-bottom: 16px; }
.mt-1 { margin-top: 0.25rem; }
.mt-4 { margin-top: 1rem; }
.m-0 { margin: 0; }
.p-4 { padding: 1rem; }
.p-24 { padding: 24px; }
.w-full { width: 100%; }
.h-80 { height: 20rem; }
.rounded-lg { border-radius: 0.5rem; }
.rounded-md { border-radius: 0.375rem; }
.truncate { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.border { border-width: 1px; }
.border-gray-100 { border-color: #f3f4f6; }

/* Table hover optimization */
:deep(.ant-table-row) {
  cursor: pointer;
}
</style>
