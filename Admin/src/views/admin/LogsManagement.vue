<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { message } from 'ant-design-vue'
import LogService, { type LogItem, type LogListParams } from '../../services/log'
import { formatDateTime } from '../../utils/uitls'
import {
  SearchOutlined,
  ReloadOutlined,
  FileTextOutlined,
  ClockCircleOutlined,
  UserOutlined,
  GlobalOutlined,
  CheckCircleOutlined,
  CloseCircleOutlined
} from '@ant-design/icons-vue'

// 响应式数据
const loading = ref(false)
const dataSource = ref<LogItem[]>([])
const total = ref(0)
const current = ref(1)
const pageSize = ref(10)

// 搜索参数
const searchParams = ref<LogListParams>({
  operator: '',
  action: '',
  startTime: undefined,
  endTime: undefined
})

// 操作类型选项
const actionOptions = [
  { label: '全部', value: '' },
  { label: '创建', value: 'create' },
  { label: '更新', value: 'update' },
  { label: '删除', value: 'delete' },
  { label: '恢复', value: 'restore' },
  { label: '发布', value: 'publish' },
  { label: '下线', value: 'offline' },
  { label: '启用/禁用', value: 'disable' }
]

// 表格列定义
const columns = [
  {
    title: '时间',
    dataIndex: 'createdAt',
    key: 'createdAt',
    width: 180
  },
  {
    title: '操作人',
    dataIndex: 'operator',
    key: 'operator',
    width: 120
  },
  {
    title: '操作类型',
    dataIndex: 'action',
    key: 'action',
    width: 100
  },
  {
    title: '目标类型',
    dataIndex: 'target',
    key: 'target',
    width: 80
  },
  {
    title: '操作描述',
    dataIndex: 'description',
    key: 'description',
    ellipsis: true
  },
  {
    title: 'IP地址',
    dataIndex: 'ip',
    key: 'ip',
    width: 140
  },
  {
    title: '状态',
    dataIndex: 'status',
    key: 'status',
    width: 80
  }
]

// 获取操作类型显示名称
const getActionLabel = (action: string) => {
  const option = actionOptions.find(opt => opt.value === action)
  return option ? option.label : action
}

// 获取目标类型显示名称
const getTargetLabel = (target: string) => {
  const targetMap: Record<string, string> = {
    post: '文章',
    user: '用户',
    category: '分类',
    tag: '标签',
    announcement: '公告'
  }
  return targetMap[target] || target || '-'
}

// 状态颜色
const getStatusColor = (status: string) => {
  return status === '成功' ? 'success' : 'error'
}

// =================== 列表与查询 ===================
// 加载日志列表
const loadLogs = async () => {
  try {
    loading.value = true
    const params: LogListParams = {
      page: current.value,
      size: pageSize.value,
      ...searchParams.value
    }
    // 移除空值参数
    Object.keys(params).forEach(key => {
      const value = params[key as keyof LogListParams]
      if (value === '' || value === undefined) {
        delete params[key as keyof LogListParams]
      }
    })

    const response = await LogService.getLogList(params)
    if (response.code === 200 && response.data) {
      dataSource.value = response.data.records
      total.value = response.data.total
    } else {
      message.error(response.message || '加载日志列表失败')
    }
  } catch (error) {
    message.error('加载日志列表失败')
    console.error('加载日志列表失败:', error)
  } finally {
    loading.value = false
  }
}

// 搜索
const handleSearch = () => {
  current.value = 1
  loadLogs()
}

// 重置搜索
const handleReset = () => {
  searchParams.value = {
    operator: '',
    action: '',
    startTime: undefined,
    endTime: undefined
  }
  current.value = 1
  loadLogs()
}

// 刷新
const handleRefresh = () => {
  loadLogs()
}

// 分页变化
const handleTableChange = (pagination: any) => {
  current.value = pageSize.value = pagination.current
  pagination.pageSize
  loadLogs()
}

// 组件挂载时加载数据
onMounted(() => {
  loadLogs()
})
</script>

<template>
  <div class="logs-management">
    <!-- 页面标题 -->
    <div class="page-header">
      <h2>
        <FileTextOutlined class="header-icon" />
        操作日志
      </h2>
    </div>

    <!-- 搜索区域 -->
    <a-card class="search-card" :bordered="false">
      <a-form layout="inline" :model="searchParams">
        <a-form-item label="操作人">
          <a-input
            v-model:value="searchParams.operator"
            placeholder="请输入操作人"
            allow-clear
            style="width: 160px"
          >
            <template #prefix><UserOutlined /></template>
          </a-input>
        </a-form-item>
        <a-form-item label="操作类型">
          <a-select
            v-model:value="searchParams.action"
            placeholder="请选择"
            allow-clear
            style="width: 120px"
          >
            <a-select-option
              v-for="option in actionOptions.slice(1)"
              :key="option.value"
              :value="option.value"
            >
              {{ option.label }}
            </a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="开始时间">
          <a-date-picker
            v-model:value="searchParams.startTime"
            value-format="YYYY-MM-DD"
            placeholder="开始时间"
            style="width: 140px"
          />
        </a-form-item>
        <a-form-item label="结束时间">
          <a-date-picker
            v-model:value="searchParams.endTime"
            value-format="YYYY-MM-DD"
            placeholder="结束时间"
            style="width: 140px"
          />
        </a-form-item>
        <a-form-item>
          <a-space>
            <a-button type="primary" @click="handleSearch">
              <template #icon><SearchOutlined /></template>
              搜索
            </a-button>
            <a-button @click="handleReset">重置</a-button>
          </a-space>
        </a-form-item>
      </a-form>
    </a-card>

    <!-- 操作区域 -->
    <a-card class="action-card" :bordered="false">
      <a-space>
        <a-button type="primary" @click="handleRefresh" :loading="loading">
          <template #icon><ReloadOutlined /></template>
          刷新
        </a-button>
        <span class="total-text">共 {{ total }} 条记录</span>
      </a-space>
    </a-card>

    <!-- 表格区域 -->
    <a-card :bordered="false">
      <a-table
        :columns="columns"
        :data-source="dataSource"
        :loading="loading"
        :pagination="{
          current,
          pageSize,
          total,
          showSizeChanger: true,
          showQuickJumper: true,
          showTotal: (total: number) => `共 ${total} 条记录`
        }"
        @change="handleTableChange"
        row-key="id"
        class="log-table"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'createdAt'">
            <div class="time-cell">
              <ClockCircleOutlined class="time-icon" />
              {{ formatDateTime(record.createdAt) }}
            </div>
          </template>
          <template v-else-if="column.key === 'action'">
            <a-tag :color="record.action === 'create' ? 'blue' :
                          record.action === 'delete' ? 'red' :
                          record.action === 'update' ? 'orange' :
                          record.action === 'restore' ? 'green' : 'default'">
              {{ getActionLabel(record.action) }}
            </a-tag>
          </template>
          <template v-else-if="column.key === 'target'">
            <span class="target-text">{{ getTargetLabel(record.target) }}</span>
          </template>
          <template v-else-if="column.key === 'description'">
            <span class="description-text" :title="record.description">
              {{ record.description || '-' }}
            </span>
          </template>
          <template v-else-if="column.key === 'ip'">
            <div class="ip-cell">
              <GlobalOutlined class="ip-icon" />
              {{ record.ip || '-' }}
            </div>
          </template>
          <template v-else-if="column.key === 'status'">
            <a-tag :color="getStatusColor(record.status)" class="status-tag">
              <template #icon>
                <CheckCircleOutlined v-if="record.status === '成功'" />
                <CloseCircleOutlined v-else />
              </template>
              {{ record.status }}
            </a-tag>
          </template>
        </template>
      </a-table>
    </a-card>
  </div>
</template>

<style scoped>
.logs-management {
  padding: 24px;
}

.page-header {
  margin-bottom: 24px;
  display: flex;
  align-items: center;
  gap: 12px;
}

.page-header h2 {
  margin: 0;
  font-size: 24px;
  font-weight: 600;
  color: var(--text-main);
  display: flex;
  align-items: center;
  gap: 10px;
}

.header-icon {
  color: var(--color-primary);
}

.search-card,
.action-card {
  margin-bottom: 16px;
  border-radius: 8px;
}

.total-text {
  color: var(--text-tertiary);
  font-size: 14px;
  margin-left: 8px;
}

.time-cell {
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--text-secondary);
  font-size: 13px;
}

.time-icon {
  color: var(--text-tertiary);
  font-size: 12px;
}

.target-text {
  color: var(--text-secondary);
  font-weight: 500;
}

.description-text {
  color: var(--text-main);
  max-width: 280px;
  display: -webkit-box;
  -webkit-line-clamp: 1;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.ip-cell {
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--text-secondary);
  font-family: 'SF Mono', 'Consolas', monospace;
  font-size: 12px;
}

.ip-icon {
  color: var(--text-tertiary);
  font-size: 12px;
}

.status-tag {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

/* 表格样式覆盖 */
:deep(.ant-table) {
  background: transparent;
}

:deep(.ant-table-thead > tr > th) {
  background: var(--bg-main);
  color: var(--text-secondary);
  font-weight: 500;
  border-bottom: 1px solid var(--border-light);
}

:deep(.ant-table-tbody > tr > td) {
  border-bottom: 1px solid var(--border-light);
  padding: 14px 16px;
}

:deep(.ant-table-tbody > tr:hover > td) {
  background: var(--bg-hover);
}

/* 响应式 */
@media (max-width: 768px) {
  .logs-management {
    padding: 16px;
  }

  :deep(.ant-form-inline) {
    display: flex;
    flex-direction: column;
  }

  :deep(.ant-form-inline .ant-form-item) {
    margin-right: 0;
    margin-bottom: 12px;
  }
}
</style>
