<script setup lang="ts">
import { message } from 'ant-design-vue'
import { SearchOutlined, ReloadOutlined, PlusOutlined, DeleteOutlined } from '@ant-design/icons-vue'
import ResourcesService from '../../services/resources'
import type { Resource } from '../../services/resources'
import { formatDateTime } from '../../utils/utils'
import { useTablePage, useCrudActions, useModalForm } from '@/composables'

const resourceTypeOptions = [
  { label: '文件', value: 'file' },
  { label: '链接', value: 'link' },
  { label: '两者都有', value: 'both' }
]

const downloadTypeOptions = [
  { label: '免费', value: 0 },
  { label: '积分', value: 1 }
]

const {
  loading,
  dataSource,
  selectedRowKeys,
  searchParams,
  pagination,
  load,
  handleSearch,
  handleReset,
  handleTableChange,
  onSelectChange,
  clearSelection
} = useTablePage<Resource, {
  name: string
  resourceType: string | undefined
  downloadType: number | undefined
  includeDeleted: boolean
}>({
  loadFn: async (params) => ResourcesService.getResourceList(params),
  defaultSearchParams: {
    name: '',
    resourceType: undefined,
    downloadType: undefined,
    includeDeleted: false
  },
  loadErrorMessage: '加载资源失败'
})

const {
  handleDelete,
  handleBatchDelete,
  handleRestore,
  handlePermanentDelete,
  handleBatchPermanentDelete
} = useCrudActions({
  deleteFn: (id) => ResourcesService.deleteResource(id),
  batchDeleteFn: (ids) => ResourcesService.batchDeleteResources(ids),
  restoreFn: (id) => ResourcesService.restoreResource(id),
  permanentDeleteFn: (id) => ResourcesService.permanentDeleteResource(id),
  batchPermanentDeleteFn: (ids) => ResourcesService.batchPermanentDeleteResources(ids),
  onRefresh: load,
  clearSelection,
  entityName: '资源'
})

const {
  modalVisible,
  modalTitle,
  isEdit,
  confirmLoading,
  formRef,
  formModel,
  openCreate,
  openEdit,
  handleOk,
  handleCancel
} = useModalForm<Resource>({
  createFn: (data) => ResourcesService.createResource(data) as any,
  updateFn: (id, data) => ResourcesService.updateResource(id, data) as any,
  onCreateSuccess: () => {
    pagination.current = 1
    load()
  },
  onUpdateSuccess: load,
  defaultForm: () => ({
    name: '',
    description: '',
    fileUrl: '',
    externalLink: '',
    resourceType: 'file',
    purchasedNote: '',
    uploaderId: undefined,
    downloadType: 0,
    pointsNeeded: 0
  }),
  entityName: '资源'
})

const rules = {
  name: [{ required: true, message: '请输入资源名称' }],
  resourceType: [{ required: true, message: '请选择资源类型' }],
  downloadType: [{ required: true, message: '请选择下载类型' }]
}

const columns = [
  { title: 'ID', dataIndex: 'id', key: 'id', width: 60 },
  { title: '名称', dataIndex: 'name', key: 'name', ellipsis: true },
  { title: '资源类型', key: 'resourceType', width: 100 },
  { title: '下载类型', key: 'downloadType', width: 100 },
  { title: '所需积分', dataIndex: 'pointsNeeded', key: 'pointsNeeded', width: 100 },
  { title: '上传者', dataIndex: 'uploaderUsername', key: 'uploaderUsername', width: 100 },
  { title: '状态', key: 'status', width: 80 },
  { title: '创建时间', dataIndex: 'createdAt', key: 'createdAt', width: 170 },
  { title: '操作', key: 'action', width: 200, fixed: 'right' as const }
]

const getResourceTypeColor = (type?: string) => {
  switch (type) {
    case 'file': return 'blue'
    case 'link': return 'green'
    case 'both': return 'purple'
    default: return 'default'
  }
}

const getResourceTypeLabel = (type?: string) => {
  switch (type) {
    case 'file': return '文件'
    case 'link': return '链接'
    case 'both': return '两者'
    default: return type || '-'
  }
}

const getDownloadTypeLabel = (type?: number) => {
  return type === 1 ? '积分' : '免费'
}

const getDownloadTypeColor = (type?: number) => {
  return type === 1 ? 'orange' : 'green'
}

const handleBatchPermanentDeleteAction = () => {
  if (!selectedRowKeys.value.length) {
    message.warning('请选择要彻底删除的资源')
    return
  }
  handleBatchPermanentDelete(selectedRowKeys.value)
}
</script>

<template>
  <div class="p-24">
    <a-card :bordered="false" class="mb-16">
      <a-form layout="horizontal" :model="searchParams">
        <a-row :gutter="24">
          <a-col :span="5">
            <a-form-item label="名称" class="mb-0">
              <a-input v-model:value="searchParams.name" placeholder="请输入资源名称" allow-clear @press-enter="handleSearch" />
            </a-form-item>
          </a-col>
          <a-col :span="4">
            <a-form-item label="资源类型" class="mb-0">
              <a-select v-model:value="searchParams.resourceType" placeholder="全部" allow-clear>
                <a-select-option v-for="opt in resourceTypeOptions" :key="opt.value" :value="opt.value">{{ opt.label }}</a-select-option>
              </a-select>
            </a-form-item>
          </a-col>
          <a-col :span="4">
            <a-form-item label="下载类型" class="mb-0">
              <a-select v-model:value="searchParams.downloadType" placeholder="全部" allow-clear>
                <a-select-option v-for="opt in downloadTypeOptions" :key="opt.value" :value="opt.value">{{ opt.label }}</a-select-option>
              </a-select>
            </a-form-item>
          </a-col>
          <a-col :span="11" class="text-right">
            <a-space>
              <a-tooltip title="显示已删除">
                <a-switch v-model:checked="searchParams.includeDeleted" @change="handleSearch" checked-children="删" un-checked-children="正常" />
              </a-tooltip>
              <a-button type="primary" @click="handleSearch">
                <template #icon><SearchOutlined /></template>
                搜索
              </a-button>
              <a-button @click="handleReset">
                <template #icon><ReloadOutlined /></template>
                重置
              </a-button>
            </a-space>
          </a-col>
        </a-row>
      </a-form>
    </a-card>

    <a-card :bordered="false">
      <template #title><span>资源列表</span></template>
      <template #extra>
        <a-space>
          <a-button type="primary" @click="openCreate">
            <template #icon><PlusOutlined /></template>新建资源
          </a-button>
          <a-button v-if="!searchParams.includeDeleted" danger :disabled="selectedRowKeys.length === 0" @click="handleBatchDelete(selectedRowKeys)">
            <template #icon><DeleteOutlined /></template>批量删除
          </a-button>
          <a-popconfirm
            v-if="searchParams.includeDeleted"
            title="确定要批量彻底删除选中的资源吗？此操作不可恢复！"
            ok-text="确定"
            cancel-text="取消"
            @confirm="handleBatchPermanentDeleteAction"
          >
            <a-button danger :disabled="selectedRowKeys.length === 0">批量彻底删除</a-button>
          </a-popconfirm>
        </a-space>
      </template>
      <a-table
        :columns="columns"
        :data-source="dataSource"
        :loading="loading"
        :pagination="pagination"
        :row-selection="{ selectedRowKeys, onChange: onSelectChange }"
        row-key="id"
        :scroll="{ x: 1200 }"
        @change="handleTableChange"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'resourceType'">
            <a-tag :color="getResourceTypeColor(record.resourceType)">{{ getResourceTypeLabel(record.resourceType) }}</a-tag>
          </template>
          <template v-else-if="column.key === 'downloadType'">
            <a-tag :color="getDownloadTypeColor(record.downloadType)">{{ getDownloadTypeLabel(record.downloadType) }}</a-tag>
          </template>
          <template v-else-if="column.key === 'pointsNeeded'">
            {{ record.downloadType === 1 ? (record.pointsNeeded || 0) : '-' }}
          </template>
          <template v-else-if="column.key === 'status'">
            <a-tag v-if="record.deletedAt" color="red">已删除</a-tag>
            <a-tag v-else color="green">正常</a-tag>
          </template>
          <template v-else-if="column.key === 'createdAt'">{{ formatDateTime(record.createdAt) }}</template>
          <template v-else-if="column.key === 'action'">
            <a-space>
              <template v-if="!record.deletedAt">
                <a-button type="link" size="small" @click="openEdit(record)">编辑</a-button>
                <a-popconfirm title="确定删除该资源吗？" @confirm="handleDelete(record.id)">
                  <a-button type="link" size="small" danger>删除</a-button>
                </a-popconfirm>
              </template>
              <template v-else>
                <a-popconfirm title="确定恢复该资源吗？" @confirm="handleRestore(record.id)">
                  <a-button type="link" size="small">恢复</a-button>
                </a-popconfirm>
                <a-popconfirm
                  title="确定要彻底删除该资源吗？此操作不可恢复！"
                  ok-text="确定"
                  cancel-text="取消"
                  @confirm="handlePermanentDelete(record.id)"
                >
                  <a-button type="link" size="small" danger>彻底删除</a-button>
                </a-popconfirm>
              </template>
            </a-space>
          </template>
        </template>
      </a-table>
    </a-card>

    <a-modal v-model:open="modalVisible" :title="modalTitle" :confirm-loading="confirmLoading" @ok="handleOk" @cancel="handleCancel" destroy-on-close width="640px">
      <a-form :model="formModel" :rules="rules" ref="formRef" layout="vertical">
        <a-form-item name="name" label="资源名称" required>
          <a-input v-model:value="formModel.name" placeholder="请输入资源名称" />
        </a-form-item>
        <a-form-item name="description" label="描述">
          <a-textarea v-model:value="formModel.description" placeholder="请输入资源描述" :rows="3" />
        </a-form-item>
        <a-form-item name="resourceType" label="资源类型" required>
          <a-radio-group v-model:value="formModel.resourceType">
            <a-radio value="file">文件</a-radio>
            <a-radio value="link">链接</a-radio>
            <a-radio value="both">两者都有</a-radio>
          </a-radio-group>
        </a-form-item>
        <a-form-item v-if="formModel.resourceType === 'file' || formModel.resourceType === 'both'" name="fileUrl" label="文件地址">
          <a-input v-model:value="formModel.fileUrl" placeholder="请输入文件URL" />
        </a-form-item>
        <a-form-item v-if="formModel.resourceType === 'link' || formModel.resourceType === 'both'" name="externalLink" label="外部链接">
          <a-input v-model:value="formModel.externalLink" placeholder="请输入外部链接地址" />
        </a-form-item>
        <a-form-item name="purchasedNote" label="购买后说明">
          <a-textarea v-model:value="formModel.purchasedNote" placeholder="购买/下载后显示的说明（提取码、使用说明等）" :rows="2" />
        </a-form-item>
        <a-row :gutter="16">
          <a-col :span="12">
            <a-form-item name="downloadType" label="下载类型" required>
              <a-select v-model:value="formModel.downloadType">
                <a-select-option :value="0">免费</a-select-option>
                <a-select-option :value="1">积分</a-select-option>
              </a-select>
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item v-if="formModel.downloadType === 1" name="pointsNeeded" label="所需积分">
              <a-input-number v-model:value="formModel.pointsNeeded" :min="0" :precision="2" placeholder="请输入积分" style="width: 100%" />
            </a-form-item>
          </a-col>
        </a-row>
      </a-form>
    </a-modal>
  </div>
</template>
