<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { SearchOutlined, ReloadOutlined, FileTextOutlined, ClockCircleOutlined, GlobalOutlined, CheckCircleOutlined, CloseCircleOutlined, EyeOutlined } from '@ant-design/icons-vue'
import { useTablePage } from '@/composables'
import { useTableColumnPrefs } from '@/composables/useTableColumnPrefs'
import TableColumnSettings from '@/components/TableColumnSettings.vue'
import { useTableExport } from '@/composables/useTableExport'
import TableExportButton from '@/components/TableExportButton.vue'
import LogService, { type LogItem, type LogListParams } from '../../services/log'
import { formatDateTime } from '../../utils/utils'

// 表格页面：加载、分页、搜索
const {
  loading, dataSource, searchParams, pagination,
  load, handleSearch, handleReset, handleTableChange
} = useTablePage<LogItem, LogListParams>({
  loadFn: (params) => LogService.getLogList(params),
  defaultSearchParams: { operator: '', action: '', targetType: undefined, status: undefined, startTime: undefined, endTime: undefined },
  loadErrorMessage: '加载日志列表失败'
})

// 操作类型选项（兜底静态列表，正常由后端 /actions 动态加载）
const actionMap: Record<string, string> = {
  login: '登录', create: '创建', update: '更新', delete: '删除', restore: '恢复',
  publish: '发布', offline: '下线', enable: '启用', disable: '禁用',
  upload: '上传', download: '下载', purchase: '购买', checkin: '签到',
  review: '审核', reply: '回复', export: '导出', import: '导入', test: '测试'
}
const actionOptions = ref<Array<{ label: string, value: string }>>([{ label: '全部', value: '' }])

// 目标类型选项（后端 /target-types 动态加载）
const targetOptions = ref<Array<{ label: string, value: string }>>([{ label: '全部', value: '' }])

// 状态选项
const statusOptions = [
  { label: '全部', value: undefined },
  { label: '成功', value: 1 },
  { label: '失败', value: 0 }
]

// 获取操作类型显示名称
const getActionLabel = (action: string) => actionMap[action] || action

// 获取目标类型显示名称
const targetMap: Record<string, string> = {
  post: '文章', user: '用户', category: '分类', tag: '标签',
  announcement: '公告', message: '留言', image: '图片',
  carousel: '轮播图', resource: '资源', attachment: '附件',
  points: '积分', comment: '评论', tts: '语音', ai_model: 'AI模型',
  music: '音乐', document: '文档', system_setting: '系统设置'
}
const getTargetLabel = (target: string) => targetMap[target] || target || '-'

// 详情弹窗
const detailVisible = ref(false)
const detailLog = ref<LogItem | null>(null)

const showDetail = (record: LogItem) => {
  detailLog.value = record
  detailVisible.value = true
}

// 加载筛选选项
const loadFilterOptions = async () => {
  try {
    const [actionRes, targetRes] = await Promise.all([
      LogService.getActionTypes(),
      LogService.getTargetTypes()
    ])
    if (actionRes.code === 200 && actionRes.data?.length) {
      actionOptions.value = [{ label: '全部', value: '' }, ...actionRes.data.map(a => ({ label: actionMap[a] || a, value: a }))]
    }
    if (targetRes.code === 200 && targetRes.data?.length) {
      targetOptions.value = [{ label: '全部', value: '' }, ...targetRes.data.map(t => ({ label: getTargetLabel(t), value: t }))]
    }
  } catch (e) {
    // 加载失败使用兜底静态选项（actionMap 全量），不阻塞页面
    actionOptions.value = [{ label: '全部', value: '' }, ...Object.entries(actionMap).map(([value, label]) => ({ label, value }))]
  }
}

onMounted(loadFilterOptions)

// 状态颜色
const getStatusColor = (status: string) => {
  return status === '成功' ? 'success' : 'error'
}

// 表格列定义
const columns = [
  { title: '时间', dataIndex: 'createdAt', key: 'createdAt', width: 180 },
  { title: '操作人', dataIndex: 'operator', key: 'operator', width: 120 },
  { title: '操作类型', dataIndex: 'action', key: 'action', width: 100 },
  { title: '目标', dataIndex: 'target', key: 'target', width: 110 },
  { title: '操作描述', dataIndex: 'description', key: 'description', ellipsis: true },
  { title: 'IP地址', dataIndex: 'ip', key: 'ip', minWidth: 170, ellipsis: true },
  { title: '状态', dataIndex: 'status', key: 'status', width: 90 },
  { title: '操作', key: 'actionCol', width: 70 }
]

const columnPrefsCtrl = useTableColumnPrefs('logs', columns)
const prefColumns = columnPrefsCtrl.prefColumns

const exportCtrl = useTableExport({
  columns: prefColumns,
  rows: dataSource,
  filename: 'logs',
})
</script>

<template>
  <div class="p-24">
    <!-- 搜索区域 -->
    <a-card :bordered="false" class="mb-16">
      <a-form layout="horizontal" :model="searchParams">
        <a-row :gutter="[16, 12]" align="bottom">
          <a-col :xs="24" :sm="12" :lg="8" :xl="6">
            <a-form-item label="操作人" class="mb-0">
              <a-input v-model:value="searchParams.operator" placeholder="输入操作人" allow-clear />
            </a-form-item>
          </a-col>
          <a-col :xs="24" :sm="12" :lg="8" :xl="6">
            <a-form-item label="操作类型" class="mb-0">
              <a-select v-model:value="searchParams.action" placeholder="全部" allow-clear>
                <a-select-option v-for="option in actionOptions" :key="option.value" :value="option.value">
                  {{ option.label }}
                </a-select-option>
              </a-select>
            </a-form-item>
          </a-col>
          <a-col :xs="24" :sm="12" :lg="8" :xl="6">
            <a-form-item label="目标类型" class="mb-0">
              <a-select v-model:value="searchParams.targetType" placeholder="全部" allow-clear>
                <a-select-option v-for="option in targetOptions" :key="option.value" :value="option.value">
                  {{ option.label }}
                </a-select-option>
              </a-select>
            </a-form-item>
          </a-col>
          <a-col :xs="24" :sm="12" :lg="8" :xl="6">
            <a-form-item label="状态" class="mb-0">
              <a-select v-model:value="searchParams.status" placeholder="全部" allow-clear>
                <a-select-option v-for="option in statusOptions" :key="String(option.value)" :value="option.value">
                  {{ option.label }}
                </a-select-option>
              </a-select>
            </a-form-item>
          </a-col>
          <a-col :xs="24" :sm="12" :lg="8" :xl="6">
            <a-form-item label="开始" class="mb-0">
              <a-date-picker v-model:value="searchParams.startTime" value-format="YYYY-MM-DD" placeholder="开始时间" style="width: 100%" />
            </a-form-item>
          </a-col>
          <a-col :xs="24" :sm="12" :lg="8" :xl="6">
            <a-form-item label="结束" class="mb-0">
              <a-date-picker v-model:value="searchParams.endTime" value-format="YYYY-MM-DD" placeholder="结束时间" style="width: 100%" />
            </a-form-item>
          </a-col>
          <a-col :xs="24" :sm="12" :lg="8" :xl="6" class="search-actions">
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
          <TableExportButton :ctrl="exportCtrl" />
          <TableColumnSettings :ctrl="columnPrefsCtrl" />
          <span class="text-secondary text-sm mr-4">共 {{ pagination.total }} 条记录</span>
          <a-button type="primary" @click="load" :loading="loading">
            <template #icon><ReloadOutlined /></template>
            刷新
          </a-button>
        </a-space>
      </template>
      <a-table
        :columns="prefColumns"
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
            <a-tooltip v-if="record.targetName" :title="`目标名称：${record.targetName}`">
              <span class="target-text">
                {{ getTargetLabel(record.target) }}
                <span v-if="record.targetName" class="target-name">/ {{ record.targetName }}</span>
              </span>
            </a-tooltip>
            <span v-else class="target-text">{{ getTargetLabel(record.target) }}</span>
          </template>
          <template v-else-if="column.key === 'description'">
            <span class="description-text" :title="record.description">
              {{ record.description || '-' }}
            </span>
          </template>
          <template v-else-if="column.key === 'ip'">
            <a-tooltip :title="record.ip || '-'">
              <div class="ip-cell">
                <GlobalOutlined class="ip-icon" />
                <span class="ip-text">{{ record.ip || '-' }}</span>
              </div>
            </a-tooltip>
          </template>
          <template v-else-if="column.key === 'status'">
            <a-tooltip v-if="record.status !== '成功' && record.detail" :title="`失败原因：${record.detail}`">
              <a-tag :color="getStatusColor(record.status)" class="status-tag">
                <template #icon>
                  <CheckCircleOutlined v-if="record.status === '成功'" />
                  <CloseCircleOutlined v-else />
                </template>
                {{ record.status }}
              </a-tag>
            </a-tooltip>
            <a-tag v-else :color="getStatusColor(record.status)" class="status-tag">
              <template #icon>
                <CheckCircleOutlined v-if="record.status === '成功'" />
                <CloseCircleOutlined v-else />
              </template>
              {{ record.status }}
            </a-tag>
          </template>
          <template v-else-if="column.key === 'actionCol'">
            <a-button type="link" size="small" @click="showDetail(record)">
              <template #icon><EyeOutlined /></template>
              详情
            </a-button>
          </template>
        </template>
      </a-table>
    </a-card>

    <!-- 日志详情弹窗 -->
    <a-modal
      v-model:open="detailVisible"
      title="日志详情"
      :footer="null"
      width="640"
    >
      <a-descriptions v-if="detailLog" :column="1" size="middle" bordered>
        <a-descriptions-item label="时间">{{ formatDateTime(detailLog.createdAt) }}</a-descriptions-item>
        <a-descriptions-item label="操作人">{{ detailLog.operator || '-' }}</a-descriptions-item>
        <a-descriptions-item label="操作类型">{{ getActionLabel(detailLog.action) }}</a-descriptions-item>
        <a-descriptions-item label="目标">
          {{ getTargetLabel(detailLog.target) }}<template v-if="detailLog.targetName"> / {{ detailLog.targetName }}</template>
        </a-descriptions-item>
        <a-descriptions-item label="操作描述">{{ detailLog.description || '-' }}</a-descriptions-item>
        <a-descriptions-item label="IP地址">
          <span class="ip-cell">{{ detailLog.ip || '-' }}</span>
        </a-descriptions-item>
        <a-descriptions-item label="User-Agent" :span="1">
          <span class="ua-text">{{ detailLog.userAgent || '-' }}</span>
        </a-descriptions-item>
        <a-descriptions-item v-if="detailLog.status !== '成功'" label="失败原因">
          <span class="error-text">{{ detailLog.detail || '-' }}</span>
        </a-descriptions-item>
      </a-descriptions>
    </a-modal>
  </div>
</template>

<style scoped>
.time-cell {
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--text-secondary);
  font-size: var(--lt-font-size-sm);
}

.time-icon {
  color: var(--text-tertiary);
  font-size: var(--lt-font-size-xs);
}

.target-text {
  color: var(--text-secondary);
  font-weight: var(--lt-font-weight-medium);
}

.target-name {
  color: var(--text-main);
  font-weight: var(--lt-font-weight-regular);
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
  font-family: var(--lt-font-family-mono);
  font-size: var(--lt-font-size-xs);
}

.ip-text {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ua-text {
  color: var(--text-secondary);
  font-size: var(--lt-font-size-xs);
  word-break: break-all;
}

.error-text {
  color: var(--error-color, #ff4d4f);
  word-break: break-all;
}

.ip-icon {
  color: var(--text-tertiary);
  font-size: var(--lt-font-size-xs);
}

.status-tag {
  display: inline-flex;
  align-items: center;
  gap: var(--lt-space-xs);
}

:deep(.ant-table) {
  background: transparent;
}

:deep(.ant-table-thead > tr > th) {
  background: var(--bg-main);
  color: var(--text-secondary);
  font-weight: var(--lt-font-weight-medium);
  border-bottom: 1px solid var(--border-light);
}

:deep(.ant-table-tbody > tr > td) {
  border-bottom: 1px solid var(--border-light);
  padding: 14px var(--lt-space-lg);
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
    margin-bottom: var(--lt-space-md);
  }
}

.search-actions {
  display: flex;
  justify-content: flex-end;
  align-items: center;
}
@media (max-width: 991px) {
  .search-actions {
    justify-content: flex-start;
  }
}
</style>

