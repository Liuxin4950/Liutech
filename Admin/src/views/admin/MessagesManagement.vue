<script setup lang="ts">
import { ref } from 'vue'
import { message } from 'ant-design-vue'
import { SearchOutlined, ReloadOutlined, DeleteOutlined, CheckOutlined, CloseOutlined, MessageOutlined } from '@ant-design/icons-vue'
import MessagesService from '../../services/message'
import type { Message } from '../../services/message'
import { formatDateTime } from '../../utils/utils'
import { useTablePage, useCrudActions, useModalForm } from '@/composables'
import { useTableColumnPrefs } from '@/composables/useTableColumnPrefs'
import TableColumnSettings from '@/components/TableColumnSettings.vue'
import { useTableExport } from '@/composables/useTableExport'
import TableExportButton from '@/components/TableExportButton.vue'

interface ReplyFormData {
  reply: string
}

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
} = useTablePage<Message, {
  nickname: string
  status: number
  includeDeleted: boolean
}>({
  loadFn: async (params) => MessagesService.getMessageList(params),
  defaultSearchParams: {
    nickname: '',
    status: -1,
    includeDeleted: false
  },
  transformSearchParams: (params) => {
    const payload: Record<string, any> = {
      nickname: params.nickname || undefined,
      includeDeleted: params.includeDeleted
    }

    if (params.status !== undefined && params.status !== -1) {
      payload.status = params.status
    }

    return payload
  },
  loadErrorMessage: '加载留言失败'
})

const {
  handleDelete,
  handleBatchDelete,
  handleRestore,
  handlePermanentDelete,
  handleBatchPermanentDelete
} = useCrudActions({
  deleteFn: (id) => MessagesService.deleteMessage(id),
  batchDeleteFn: (ids) => MessagesService.batchDeleteMessages(ids),
  restoreFn: (id) => MessagesService.restoreMessage(id),
  permanentDeleteFn: (id) => MessagesService.permanentDeleteMessage(id),
  batchPermanentDeleteFn: (ids) => MessagesService.batchPermanentDeleteMessages(ids),
  onRefresh: load,
  clearSelection,
  entityName: '留言'
})

const {
  modalVisible: replyModalVisible,
  modalTitle: replyModalTitle,
  confirmLoading: replyConfirmLoading,
  editingId: editingReplyId,
  formRef: replyFormRef,
  formModel: replyFormModel,
  openCreate: openReplyCreate,
  openEdit: openReplyEdit,
  handleOk: handleReplySubmit,
  handleCancel: handleReplyCancel
} = useModalForm<ReplyFormData>({
  updateFn: (id, data) => MessagesService.replyMessage(id, data.reply || ''),
  onUpdateSuccess: load,
  defaultForm: () => ({ reply: '' }),
  entityName: '留言回复'
})

const detailModalVisible = ref(false)
const detailRecord = ref<Message | null>(null)

const columns = [
  { title: '昵称', dataIndex: 'nickname', key: 'nickname', width: 120 },
  { title: '邮箱', dataIndex: 'email', key: 'email', width: 200 },
  { title: '留言内容', dataIndex: 'content', key: 'content', ellipsis: true },
  { title: '状态', key: 'status', width: 100 },
  { title: '回复', dataIndex: 'reply', key: 'reply', ellipsis: true },
  { title: '留言时间', dataIndex: 'createdAt', key: 'createdAt', width: 170 },
  { title: '操作', key: 'action', width: 280, fixed: 'right' as const }
]

const columnPrefsCtrl = useTableColumnPrefs('messages', columns, { alwaysVisible: ["action"] })
const prefColumns = columnPrefsCtrl.prefColumns

const exportCtrl = useTableExport({
  columns: prefColumns,
  rows: dataSource,
  filename: 'messages',
})

const statusOptions = [
  { label: '全部', value: -1 },
  { label: '待审核', value: 0 },
  { label: '已通过', value: 1 },
  { label: '已拒绝', value: 2 }
]

const handleReview = async (id: number, status: number) => {
  try {
    const res = await MessagesService.reviewMessage(id, status)
    if (res.code === 200) {
      message.success(status === 1 ? '已通过' : '已拒绝')
      load()
    } else {
      message.error(res.message || '操作失败')
    }
  } catch {
    message.error('操作失败，请检查网络')
  }
}

const openReplyModal = (record: Message) => {
  openReplyEdit({ id: record.id, reply: record.reply || '' } as any)
}

const handleViewDetail = (record: Message) => {
  detailRecord.value = record
  detailModalVisible.value = true
}

const handleBatchApprove = async () => {
  if (!selectedRowKeys.value.length) {
    message.warning('请选择要通过的留言')
    return
  }
  let failed = 0
  for (const id of selectedRowKeys.value) {
    try {
      await MessagesService.reviewMessage(id, 1)
    } catch {
      failed++
    }
  }
  if (failed > 0) {
    message.warning(`${failed} 条留言通过失败`)
  } else {
    message.success('批量通过成功')
  }
  clearSelection()
  load()
}

const handleBatchReject = async () => {
  if (!selectedRowKeys.value.length) {
    message.warning('请选择要拒绝的留言')
    return
  }
  let failed = 0
  for (const id of selectedRowKeys.value) {
    try {
      await MessagesService.reviewMessage(id, 2)
    } catch {
      failed++
    }
  }
  if (failed > 0) {
    message.warning(`${failed} 条留言拒绝失败`)
  } else {
    message.success('批量拒绝成功')
  }
  clearSelection()
  load()
}
</script>

<template>
  <div class="p-24">
    <a-card :bordered="false" class="mb-16">
      <a-form layout="horizontal" :model="searchParams">
        <a-row :gutter="[16, 12]" align="bottom">
          <a-col :xs="24" :sm="12" :lg="8" :xl="6">
            <a-form-item label="昵称" class="mb-0">
              <a-input v-model:value="searchParams.nickname" placeholder="请输入昵称" allow-clear @press-enter="handleSearch" />
            </a-form-item>
          </a-col>
          <a-col :xs="24" :sm="12" :lg="8" :xl="6">
            <a-form-item label="状态" class="mb-0">
              <a-select v-model:value="searchParams.status" placeholder="请选择状态" allow-clear>
                <a-select-option v-for="option in statusOptions" :key="option.label" :value="option.value">{{ option.label }}</a-select-option>
              </a-select>
            </a-form-item>
          </a-col>
          <a-col :xs="24" :sm="12" :lg="8" :xl="6" class="search-actions">
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
      <template #title><span>留言列表</span></template>
      <template #extra>
        <a-space>
          <TableExportButton :ctrl="exportCtrl" />
          <TableColumnSettings :ctrl="columnPrefsCtrl" />
          <a-button v-if="!searchParams.includeDeleted" type="primary" :disabled="selectedRowKeys.length === 0" @click="handleBatchApprove">
            <template #icon><CheckOutlined /></template>批量通过
          </a-button>
          <a-button v-if="!searchParams.includeDeleted" danger :disabled="selectedRowKeys.length === 0" @click="handleBatchReject">
            <template #icon><CloseOutlined /></template>批量拒绝
          </a-button>
          <a-button v-if="!searchParams.includeDeleted" danger :disabled="selectedRowKeys.length === 0" @click="handleBatchDelete(selectedRowKeys)">
            <template #icon><DeleteOutlined /></template>批量删除
          </a-button>
          <a-popconfirm
            v-if="searchParams.includeDeleted"
            title="确定要批量彻底删除选中的留言吗？此操作不可恢复！"
            ok-text="确定"
            cancel-text="取消"
            @confirm="handleBatchPermanentDelete(selectedRowKeys)"
          >
            <a-button danger :disabled="selectedRowKeys.length === 0">批量彻底删除</a-button>
          </a-popconfirm>
        </a-space>
      </template>
      <a-table
        :columns="prefColumns"
        :data-source="dataSource"
        :loading="loading"
        :pagination="pagination"
        :row-selection="{ selectedRowKeys, onChange: onSelectChange }"
        row-key="id"
        :scroll="{ x: 1200 }"
        @change="handleTableChange"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'content'">
            <div style="max-width: 300px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;">{{ record.content }}</div>
          </template>
          <template v-else-if="column.key === 'status'">
            <a-tag v-if="record.deletedAt" color="red">已删除</a-tag>
            <a-tag v-else-if="record.status === 0" color="orange">待审核</a-tag>
            <a-tag v-else-if="record.status === 1" color="green">已通过</a-tag>
            <a-tag v-else-if="record.status === 2" color="red">已拒绝</a-tag>
          </template>
          <template v-else-if="column.key === 'reply'">
            <span v-if="record.reply" style="color: var(--color-success); font-size: var(--lt-font-size-xs);">{{ record.reply }}</span>
            <span v-else style="color: var(--text-disabled);">-</span>
          </template>
          <template v-else-if="column.key === 'createdAt'">
            {{ formatDateTime(record.createdAt) }}
          </template>
          <template v-else-if="column.key === 'action'">
            <a-space>
              <template v-if="!record.deletedAt">
                <a-button type="link" size="small" @click="handleViewDetail(record)">查看</a-button>
                <a-button v-if="record.status === 0" type="link" size="small" @click="handleReview(record.id, 1)">通过</a-button>
                <a-button v-if="record.status === 0" type="link" size="small" danger @click="handleReview(record.id, 2)">拒绝</a-button>
                <a-button type="link" size="small" @click="openReplyModal(record)">
                  <template #icon><MessageOutlined /></template>回复
                </a-button>
                <a-popconfirm title="确定删除该留言吗？" @confirm="handleDelete(record.id)">
                  <a-button type="link" size="small" danger>删除</a-button>
                </a-popconfirm>
              </template>
              <template v-else>
                <a-popconfirm title="确定恢复该留言吗？" @confirm="handleRestore(record.id)">
                  <a-button type="link" size="small">恢复</a-button>
                </a-popconfirm>
                <a-popconfirm
                  title="确定要彻底删除该留言吗？此操作不可恢复！"
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

    <a-modal
      v-model:open="replyModalVisible"
      title="回复留言"
      :confirm-loading="replyConfirmLoading"
      destroy-on-close
      @ok="handleReplySubmit"
      @cancel="handleReplyCancel"
    >
      <a-form :model="replyFormModel" ref="replyFormRef" layout="vertical">
        <a-form-item label="回复内容">
          <a-textarea v-model:value="replyFormModel.reply" :rows="5" placeholder="请输入回复内容" />
        </a-form-item>
      </a-form>
    </a-modal>

    <a-modal v-model:open="detailModalVisible" title="留言详情" :footer="null" destroy-on-close>
      <a-descriptions v-if="detailRecord" bordered :column="1">
        <a-descriptions-item label="昵称">{{ detailRecord.nickname }}</a-descriptions-item>
        <a-descriptions-item label="邮箱">{{ detailRecord.email }}</a-descriptions-item>
        <a-descriptions-item label="留言内容">
          <div style="white-space: pre-wrap; word-break: break-word; line-height: var(--lt-line-height-relaxed);">{{ detailRecord.content }}</div>
        </a-descriptions-item>
        <a-descriptions-item label="状态">
          <a-tag v-if="detailRecord.status === 0" color="orange">待审核</a-tag>
          <a-tag v-else-if="detailRecord.status === 1" color="green">已通过</a-tag>
          <a-tag v-else-if="detailRecord.status === 2" color="red">已拒绝</a-tag>
        </a-descriptions-item>
        <a-descriptions-item v-if="detailRecord.reply" label="博主回复">
          <div style="white-space: pre-wrap; word-break: break-word; line-height: var(--lt-line-height-relaxed); background: var(--lt-color-success-bg); padding: var(--lt-space-sm) var(--lt-space-md); border-radius: var(--lt-radius-sm); border-left: 3px solid var(--lt-color-success);">{{ detailRecord.reply }}</div>
        </a-descriptions-item>
        <a-descriptions-item label="留言时间">{{ formatDateTime(detailRecord.createdAt) }}</a-descriptions-item>
      </a-descriptions>
    </a-modal>
  </div>
</template>

