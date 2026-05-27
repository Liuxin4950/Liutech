<script setup lang="ts">
import { SearchOutlined, ReloadOutlined, FileTextOutlined, ClockCircleOutlined, GlobalOutlined, CheckCircleOutlined, CloseCircleOutlined } from '@ant-design/icons-vue'
import { useTablePage } from '@/composables'
import LogService, { type LogItem, type LogListParams } from '../../services/log'
import { formatDateTime } from '../../utils/utils'

// 表格页面：加载、分页、搜索
const {
  loading, dataSource, searchParams, pagination,
  load, handleSearch, handleReset, handleTableChange
} = useTablePage<LogItem, LogListParams>({
  loadFn: (params) => LogService.getLogList(params),
  defaultSearchParams: { operator: '', action: '', startTime: undefined, endTime: undefined },
  loadErrorMessage: '加载日志列表失败'
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
  { label: '启用/禁用', value: 'disable' },
  { label: '上传', value: 'upload' },
  { label: '购买', value: 'purchase' },
  { label: '签到', value: 'checkin' },
  { label: '审核', value: 'review' },
  { label: '回复', value: 'reply' },
  { label: '导出', value: 'export' },
  { label: '导入', value: 'import' }
]

// 获取操作类型显示名称
const getActionLabel = (action: string) => {
  const option = actionOptions.find(opt => opt.value === action)
  return option ? option.label : action
}

// 获取目标类型显示名称
const getTargetLabel = (target: string) => {
  const targetMap: Record<string, string> = {
    post: '文章', user: '用户', category: '分类', tag: '标签',
    announcement: '公告', message: '留言', image: '图片',
    carousel: '轮播图', resource: '资源', attachment: '附件',
    points: '积分', comment: '评论', tts: '语音', ai_model: 'AI模型'
  }
  return targetMap[target] || target || '-'
}

// 状态颜色
const getStatusColor = (status: string) => {
  return status === '成功' ? 'success' : 'error'
}

// 表格列定义
const columns = [
  { title: '时间', dataIndex: 'createdAt', key: 'createdAt', width: 180 },
  { title: '操作人', dataIndex: 'operator', key: 'operator', width: 120 },
  { title: '操作类型', dataIndex: 'action', key: 'action', width: 100 },
  { title: '目标类型', dataIndex: 'target', key: 'target', width: 80 },
  { title: '操作描述', dataIndex: 'description', key: 'description', ellipsis: true },
  { title: 'IP地址', dataIndex: 'ip', key: 'ip', width: 140 },
  { title: '状态', dataIndex: 'status', key: 'status', width: 80 }
]
</script>

<template>
  <div class="p-24">
    <!-- 搜索区域 -->
    <a-card :bordered="false" class="mb-16">
      <a-form layout="horizontal" :model="searchParams">
        <a-row :gutter="24">
          <a-col :span="5">
            <a-form-item label="操作人" class="mb-0">
              <a-input v-model:value="searchParams.operator" placeholder="输入操作人" allow-clear />
            </a-form-item>
          </a-col>
          <a-col :span="5">
            <a-form-item label="操作类型" class="mb-0">
              <a-select v-model:value="searchParams.action" placeholder="全部" allow-clear>
                <a-select-option v-for="option in actionOptions" :key="option.value" :value="option.value">
                  {{ option.label }}
                </a-select-option>
              </a-select>
            </a-form-item>
          </a-col>
          <a-col :span="5">
            <a-form-item label="开始" class="mb-0">
              <a-date-picker v-model:value="searchParams.startTime" value-format="YYYY-MM-DD" placeholder="开始时间" style="width: 100%" />
            </a-form-item>
          </a-col>
          <a-col :span="5">
            <a-form-item label="结束" class="mb-0">
              <a-date-picker v-model:value="searchParams.endTime" value-format="YYYY-MM-DD" placeholder="结束时间" style="width: 100%" />
            </a-form-item>
          </a-col>
          <a-col :span="4" class="text-right">
            <a-space>
              <a-button type="primary" @click="handleSearch">
                <template #icon><SearchOutlined /></template>
                搜索
              </a-button>
              <a-button @click="handleReset">重置</a-button>
            </a-space>
          </a-col>
        </a-row>
      </a-form>
    </a-card>

    <!-- 表格区域 -->
    <a-card :bordered="false">
      <template #title>
        <span><FileTextOutlined /> 操作日志</span>
      </template>
      <template #extra>
        <a-space>
          <span class="text-secondary text-sm mr-4">共 {{ pagination.total }} 条记录</span>
          <a-button type="primary" @click="load" :loading="loading">
            <template #icon><ReloadOutlined /></template>
            刷新
          </a-button>
        </a-space>
      </template>
      <a-table
        :columns="columns"
        :data-source="dataSource"
        :loading="loading"
        :pagination="pagination"
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

@media (max-width: 768px) {
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

