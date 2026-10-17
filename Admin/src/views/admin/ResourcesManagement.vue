<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { SearchOutlined, ReloadOutlined, PlusOutlined, DeleteOutlined } from '@ant-design/icons-vue'
import LtStatusTag from '@/components/LtStatusTag.vue'
import LtFilterBar from '@/components/LtFilterBar.vue'
import LtTableCard from '@/components/LtTableCard.vue'
import ResourcesService from '../../services/resources'
import type { Resource, ResourcePost } from '../../services/resources'
import { formatDateTime } from '../../utils/utils'
import { useTablePage, useCrudActions, useModalForm } from '@/composables'
import { useTableColumnPrefs } from '@/composables/useTableColumnPrefs'
import TableColumnSettings from '@/components/TableColumnSettings.vue'
import { useTableExport } from '@/composables/useTableExport'
import TableExportButton from '@/components/TableExportButton.vue'

const router = useRouter()
const detailVisible = ref(false)
const detailLoading = ref(false)
const detailError = ref(false)
const detailResource = ref<Resource | null>(null)
let detailRequest = 0
let detailId: number | undefined

const openDetail = async (record: Resource) => {
  if (!record.id) return
  detailId = record.id
  const request = ++detailRequest
  detailVisible.value = true
  detailLoading.value = true
  detailError.value = false
  detailResource.value = null
  try {
    const response = await ResourcesService.getResourceById(record.id)
    if (request === detailRequest && detailVisible.value) detailResource.value = response.data
  } catch (error: any) {
    if (request !== detailRequest || !detailVisible.value) return
    detailError.value = true
    if (!error?.isBusiness) message.error('加载资源详情失败')
  } finally {
    if (request === detailRequest) detailLoading.value = false
  }
}

const closeDetail = () => {
  detailVisible.value = false
  ++detailRequest
  detailLoading.value = false
}

const retryDetail = () => {
  if (detailId) void openDetail({ id: detailId, name: '' })
}

const getPostStatusLabel = (post: ResourcePost) => {
  if (post.deletedAt) return '已删除'
  const labels: Record<string, string> = { draft: '草稿', published: '已发布', archived: '已归档' }
  return labels[post.status] || post.status
}

const openAssociatedPost = (post: ResourcePost) => {
  closeDetail()
  void router.push({
    name: 'PostsManagement',
    query: post.deletedAt ? { includeDeleted: 'true', title: post.title } : { edit: String(post.postId) }
  })
}

// 存储地址只展示为文本；外部链接仅允许无凭据的 HTTP(S) 地址。
const safeExternalLink = computed(() => {
  const value = detailResource.value?.externalLink
  if (!value) return undefined
  try {
    const url = new URL(value)
    return ['http:', 'https:'].includes(url.protocol) && !url.username && !url.password ? url.href : undefined
  } catch {
    return undefined
  }
})

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
  { title: '关联文章', key: 'associatedPosts', width: 260 },
  { title: '状态', key: 'status', width: 80 },
  { title: '创建时间', dataIndex: 'createdAt', key: 'createdAt', width: 170 },
  { title: '操作', key: 'action', width: 200, fixed: 'right' as const }
]

const columnPrefsCtrl = useTableColumnPrefs('resources', columns, { alwaysVisible: ["action"] })
const prefColumns = columnPrefsCtrl.prefColumns

const exportCtrl = useTableExport({
  columns: prefColumns,
  rows: dataSource,
  filename: 'resources',
})

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
    <LtFilterBar>
      <a-form-item label="名称" class="lt-filter-bar__field">
        <a-input v-model:value="searchParams.name" placeholder="请输入资源名称" allow-clear @press-enter="handleSearch" />
      </a-form-item>
      <a-form-item label="资源类型" class="lt-filter-bar__field">
        <a-select v-model:value="searchParams.resourceType" placeholder="全部" allow-clear>
          <a-select-option v-for="opt in resourceTypeOptions" :key="opt.value" :value="opt.value">{{ opt.label }}</a-select-option>
        </a-select>
      </a-form-item>
      <a-form-item label="下载类型" class="lt-filter-bar__field">
        <a-select v-model:value="searchParams.downloadType" placeholder="全部" allow-clear>
          <a-select-option v-for="opt in downloadTypeOptions" :key="opt.value" :value="opt.value">{{ opt.label }}</a-select-option>
        </a-select>
      </a-form-item>
      <template #actions>
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
      </template>
    </LtFilterBar>

    <LtTableCard title="资源列表">
      <template #actions>
        <TableExportButton :ctrl="exportCtrl" />
        <TableColumnSettings :ctrl="columnPrefsCtrl" />
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
      </template>
      <a-table
        :columns="prefColumns"
        :data-source="dataSource"
        :loading="loading"
        :pagination="pagination"
        :row-selection="{ selectedRowKeys, onChange: onSelectChange }"
        row-key="id"
        :scroll="{ x: 1460 }"
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
          <template v-else-if="column.key === 'associatedPosts'">
            <div v-if="record.associatedPosts?.length" class="resource-post-summary">
              <button type="button" class="resource-post-title" @click="openDetail(record)">
                {{ record.associatedPosts[0].title }}
              </button>
              <span class="resource-post-caption">
                {{ getPostStatusLabel(record.associatedPosts[0]) }}
                <template v-if="record.associatedPosts.length > 1"> · 共 {{ record.associatedPosts.length }} 篇</template>
              </span>
            </div>
            <span v-else class="resource-post-caption">未关联文章</span>
          </template>
          <template v-else-if="column.key === 'status'">
            <LtStatusTag :status="record.deletedAt ? 'deleted' : 'normal'" />
          </template>
          <template v-else-if="column.key === 'createdAt'">{{ formatDateTime(record.createdAt) }}</template>
          <template v-else-if="column.key === 'action'">
            <a-space>
              <a-button type="link" size="small" @click="openDetail(record)">详情</a-button>
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
    </LtTableCard>

    <a-drawer v-model:open="detailVisible" title="资源详情" width="min(640px, 100vw)" @close="closeDetail">
      <a-spin :spinning="detailLoading">
        <div class="resource-detail-body">
        <a-alert v-if="detailError" type="error" show-icon message="资源详情加载失败">
          <template #action><a-button size="small" @click="retryDetail">重试</a-button></template>
        </a-alert>
        <template v-else-if="detailResource">
          <h2 class="resource-detail-title">{{ detailResource.name }}</h2>
          <a-descriptions bordered :column="1" size="small">
            <a-descriptions-item label="资源 ID">{{ detailResource.id }}</a-descriptions-item>
            <a-descriptions-item label="资源类型">{{ getResourceTypeLabel(detailResource.resourceType) }}</a-descriptions-item>
            <a-descriptions-item label="状态"><LtStatusTag :status="detailResource.deletedAt ? 'deleted' : 'normal'" /></a-descriptions-item>
            <a-descriptions-item label="上传者">{{ detailResource.uploaderUsername || '-' }}<template v-if="detailResource.uploaderId">（#{{ detailResource.uploaderId }}）</template></a-descriptions-item>
            <a-descriptions-item label="下载方式">{{ getDownloadTypeLabel(detailResource.downloadType) }}<template v-if="detailResource.downloadType === 1"> · {{ detailResource.pointsNeeded || 0 }} 积分</template></a-descriptions-item>
            <a-descriptions-item label="描述"><span class="resource-detail-text">{{ detailResource.description || '暂无描述' }}</span></a-descriptions-item>
            <a-descriptions-item label="文件地址"><span class="resource-detail-text">{{ detailResource.fileUrl || '-' }}</span></a-descriptions-item>
            <a-descriptions-item label="外部链接">
              <a v-if="safeExternalLink" :href="safeExternalLink" target="_blank" rel="noopener noreferrer" class="resource-detail-text">{{ detailResource.externalLink }}</a>
              <span v-else class="resource-detail-text">{{ detailResource.externalLink || '-' }}</span>
            </a-descriptions-item>
            <a-descriptions-item label="购买后说明"><span class="resource-detail-text">{{ detailResource.purchasedNote || '-' }}</span></a-descriptions-item>
            <a-descriptions-item label="创建时间">{{ formatDateTime(detailResource.createdAt) || '-' }}</a-descriptions-item>
            <a-descriptions-item label="更新时间">{{ formatDateTime(detailResource.updatedAt) || '-' }}</a-descriptions-item>
            <a-descriptions-item v-if="detailResource.deletedAt" label="删除时间">{{ formatDateTime(detailResource.deletedAt) }}</a-descriptions-item>
          </a-descriptions>

          <h3 class="resource-detail-section">关联文章（{{ detailResource.associatedPosts?.length || 0 }}）</h3>
          <a-list v-if="detailResource.associatedPosts?.length" :data-source="detailResource.associatedPosts" bordered>
            <template #renderItem="{ item }">
              <a-list-item>
                <a-list-item-meta>
                  <template #title><span class="resource-detail-text">{{ item.title }}</span></template>
                  <template #description>#{{ item.postId }} · {{ getPostStatusLabel(item) }} · {{ item.authorUsername || '未知作者' }}</template>
                </a-list-item-meta>
                <template #actions>
                  <a-button type="link" size="small" @click="openAssociatedPost(item)">{{ item.deletedAt ? '查看回收站' : '管理文章' }}</a-button>
                </template>
              </a-list-item>
            </template>
          </a-list>
          <a-empty v-else description="此资源尚未关联文章，可能为独立资源或未绑定的草稿附件" />
        </template>
        </div>
      </a-spin>
    </a-drawer>

    <a-modal v-model:open="modalVisible" :title="modalTitle" :confirm-loading="confirmLoading" @ok="handleOk" @cancel="handleCancel" destroy-on-close :width="640">
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

<style scoped>
.resource-post-summary {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.resource-post-title {
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--ant-color-link, #1677ff);
  text-align: left;
  font: inherit;
  cursor: pointer;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.resource-post-caption {
  color: var(--ant-color-text-secondary, #64748b);
  font-size: 12px;
}

.resource-detail-title {
  margin: 0 0 20px;
  font-size: 20px;
  overflow-wrap: anywhere;
}

.resource-detail-body {
  min-height: 160px;
}

.resource-detail-section {
  margin: 24px 0 12px;
  font-size: 16px;
}

.resource-detail-text {
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
</style>
