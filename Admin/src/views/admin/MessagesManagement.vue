<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import { message } from 'ant-design-vue'
import { SearchOutlined, ReloadOutlined, DeleteOutlined, CheckOutlined, CloseOutlined, MessageOutlined } from '@ant-design/icons-vue'
import MessagesService from '../../services/message'
import type { Message, MessageListParams } from '../../services/message'
import { formatDateTime } from '../../utils/utils'

// 响应式数据
const loading = ref(false)
const dataSource = ref<Message[]>([])
const selectedRowKeys = ref<number[]>([])

// 分页配置
const pagination = reactive({
  current: 1,
  pageSize: 10,
  total: 0,
  showSizeChanger: true,
  showQuickJumper: true,
  showTotal: (total: number) => `共 ${total} 条记录`
})

// 搜索参数
const searchParams = ref<MessageListParams>({
  nickname: '',
  status: -1,
  includeDeleted: false
})

const columns = [
  { title: '昵称', dataIndex: 'nickname', key: 'nickname', width: 120 },
  { title: '邮箱', dataIndex: 'email', key: 'email', width: 200 },
  { title: '留言内容', dataIndex: 'content', key: 'content', ellipsis: true },
  { title: '状态', key: 'status', width: 100 },
  { title: '回复', dataIndex: 'reply', key: 'reply', ellipsis: true },
  { title: '留言时间', dataIndex: 'createdAt', key: 'createdAt', width: 170 },
  { title: '操作', key: 'action', width: 280, fixed: 'right' as const }
]

// 回复弹窗相关
const replyModalVisible = ref(false)
const replyConfirmLoading = ref(false)
const editingReplyId = ref<number | null>(null)
const replyContent = ref('')

// 详情弹窗
const detailModalVisible = ref(false)
const detailRecord = ref<Message | null>(null)

// 状态筛选选项
const statusOptions = [
  { label: '全部', value: -1 },
  { label: '待审核', value: 0 },
  { label: '已通过', value: 1 },
  { label: '已拒绝', value: 2 }
]

// 列表加载
const loadMessages = async () => {
  try {
    loading.value = true
    // 过滤掉 status 为 -1 或空值的情况
    const params: any = {
      page: pagination.current,
      size: pagination.pageSize,
      nickname: searchParams.value.nickname || undefined,
      includeDeleted: searchParams.value.includeDeleted
    }
    // 只有当 status 不为 -1 时才添加到参数中
    if (searchParams.value.status !== undefined && searchParams.value.status !== -1) {
      params.status = searchParams.value.status
    }
    const res = await MessagesService.getMessageList(params)
    if (res.code === 200) {
      dataSource.value = res.data.records
      pagination.total = res.data.total
    } else {
      message.error(res.message || '加载留言失败')
    }
  } catch (e) {
    message.error('加载留言失败')
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  pagination.current = 1
  loadMessages()
}

const handleReset = () => {
  searchParams.value = {
    nickname: '',
    status: -1,
    includeDeleted: false
  }
  pagination.current = 1
  loadMessages()
}

// 审核留言
const handleReview = async (id: number, status: number) => {
  const res = await MessagesService.reviewMessage(id, status)
  if (res.code === 200) {
    message.success(status === 1 ? '已通过' : '已拒绝')
    loadMessages()
  } else {
    message.error(res.message || '操作失败')
  }
}

// 打开回复弹窗
const openReplyModal = (record: Message) => {
  editingReplyId.value = record.id || null
  replyContent.value = record.reply || ''
  replyModalVisible.value = true
}

// 提交回复
const handleReplySubmit = async () => {
  if (!replyContent.value.trim()) {
    message.warning('请输入回复内容')
    return
  }
  try {
    replyConfirmLoading.value = true
    const res = await MessagesService.replyMessage(editingReplyId.value as number, replyContent.value)
    if (res.code === 200) {
      message.success('回复成功')
      replyModalVisible.value = false
      loadMessages()
    } else {
      message.error(res.message || '回复失败')
    }
  } catch (e) {
    message.error('回复失败')
  } finally {
    replyConfirmLoading.value = false
  }
}

// 查看详情
const handleViewDetail = (record: Message) => {
  detailRecord.value = record
  detailModalVisible.value = true
}

// 删除留言
const handleDelete = async (id: number) => {
  const res = await MessagesService.deleteMessage(id)
  if (res.code === 200) {
    message.success('删除成功')
    loadMessages()
  } else {
    message.error(res.message || '删除失败')
  }
}

// 批量删除
const handleBatchDelete = async () => {
  if (!selectedRowKeys.value.length) {
    message.warning('请选择要删除的留言')
    return
  }
  const res = await MessagesService.batchDeleteMessages(selectedRowKeys.value)
  if (res.code === 200) {
    message.success('批量删除成功')
    selectedRowKeys.value = []
    loadMessages()
  } else {
    message.error(res.message || '批量删除失败')
  }
}

// 恢复删除
const handleRestore = async (id: number) => {
  const res = await MessagesService.restoreMessage(id)
  if (res.code === 200) {
    message.success('恢复成功')
    loadMessages()
  } else {
    message.error(res.message || '恢复失败')
  }
}

// 彻底删除
const handlePermanentDelete = async (id: number) => {
  const res = await MessagesService.permanentDeleteMessage(id)
  if (res.code === 200) {
    message.success('彻底删除成功')
    loadMessages()
  } else {
    message.error(res.message || '彻底删除失败')
  }
}

// 批量彻底删除
const handleBatchPermanentDelete = async () => {
  if (!selectedRowKeys.value.length) {
    message.warning('请选择要彻底删除的留言')
    return
  }
  const res = await MessagesService.batchPermanentDeleteMessages(selectedRowKeys.value)
  if (res.code === 200) {
    message.success('批量彻底删除成功')
    selectedRowKeys.value = []
    loadMessages()
  } else {
    message.error(res.message || '批量彻底删除失败')
  }
}

const handleTableChange = (p: any) => {
  pagination.current = p.current
  pagination.pageSize = p.pageSize
  loadMessages()
}

const onSelectChange = (keys: number[]) => {
  selectedRowKeys.value = keys
}

// 批量通过
const handleBatchApprove = async () => {
  if (!selectedRowKeys.value.length) {
    message.warning('请选择要通过的留言')
    return
  }
  for (const id of selectedRowKeys.value) {
    await MessagesService.reviewMessage(id, 1)
  }
  message.success('批量通过成功')
  selectedRowKeys.value = []
  loadMessages()
}

// 批量拒绝
const handleBatchReject = async () => {
  if (!selectedRowKeys.value.length) {
    message.warning('请选择要拒绝的留言')
    return
  }
  for (const id of selectedRowKeys.value) {
    await MessagesService.reviewMessage(id, 2)
  }
  message.success('批量拒绝成功')
  selectedRowKeys.value = []
  loadMessages()
}

onMounted(() => {
  loadMessages()
})
</script>

<template>
  <div class="messages-management">
    <a-card :bordered="false" class="mb-16">
      <a-form layout="horizontal" :model="searchParams">
        <a-row :gutter="24">
          <a-col :span="5">
            <a-form-item label="昵称" class="mb-0">
              <a-input v-model:value="searchParams.nickname" placeholder="请输入昵称" allow-clear @press-enter="handleSearch" />
            </a-form-item>
          </a-col>
          <a-col :span="5">
            <a-form-item label="状态" class="mb-0">
              <a-select v-model:value="searchParams.status" placeholder="请选择状态" allow-clear>
                <a-select-option v-for="option in statusOptions" :key="option.label" :value="option.value">
                  {{ option.label }}
                </a-select-option>
              </a-select>
            </a-form-item>
          </a-col>
          <a-col :span="14" class="text-right">
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
      <template #title>
        <span>留言列表</span>
      </template>
      <template #extra>
        <a-space>
          <a-button v-if="!searchParams.includeDeleted" type="primary" :disabled="selectedRowKeys.length === 0" @click="handleBatchApprove">
            <template #icon><CheckOutlined /></template>批量通过
          </a-button>
          <a-button v-if="!searchParams.includeDeleted" danger :disabled="selectedRowKeys.length === 0" @click="handleBatchReject">
            <template #icon><CloseOutlined /></template>批量拒绝
          </a-button>
          <a-button v-if="!searchParams.includeDeleted" danger :disabled="selectedRowKeys.length === 0" @click="handleBatchDelete">
            <template #icon><DeleteOutlined /></template>批量删除
          </a-button>
          <a-popconfirm
            v-if="searchParams.includeDeleted"
            title="确定要批量彻底删除选中的留言吗？此操作不可恢复！"
            ok-text="确定"
            cancel-text="取消"
            @confirm="handleBatchPermanentDelete"
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
        :scroll="{ x: 1200 }"
        row-key="id"
        @change="handleTableChange"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'content'">
            <div class="message-content">{{ record.content }}</div>
          </template>
          <template v-else-if="column.key === 'status'">
            <a-tag v-if="record.deletedAt" color="red">已删除</a-tag>
            <a-tag v-else-if="record.status === 0" color="orange">待审核</a-tag>
            <a-tag v-else-if="record.status === 1" color="green">已通过</a-tag>
            <a-tag v-else-if="record.status === 2" color="red">已拒绝</a-tag>
          </template>
          <template v-else-if="column.key === 'reply'">
            <span v-if="record.reply" class="reply-content">{{ record.reply }}</span>
            <span v-else class="text-placeholder">-</span>
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

    <!-- 回复弹窗 -->
    <a-modal
      v-model:open="replyModalVisible"
      title="回复留言"
      :confirm-loading="replyConfirmLoading"
      @ok="handleReplySubmit"
      destroy-on-close
    >
      <a-form layout="vertical">
        <a-form-item label="回复内容">
          <a-textarea v-model:value="replyContent" :rows="5" placeholder="请输入回复内容" />
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- 详情弹窗 -->
    <a-modal v-model:open="detailModalVisible" title="留言详情" :footer="null" destroy-on-close>
      <a-descriptions v-if="detailRecord" bordered :column="1">
        <a-descriptions-item label="昵称">{{ detailRecord.nickname }}</a-descriptions-item>
        <a-descriptions-item label="邮箱">{{ detailRecord.email }}</a-descriptions-item>
        <a-descriptions-item label="留言内容">
          <div class="detail-content">{{ detailRecord.content }}</div>
        </a-descriptions-item>
        <a-descriptions-item label="状态">
          <a-tag v-if="detailRecord.status === 0" color="orange">待审核</a-tag>
          <a-tag v-else-if="detailRecord.status === 1" color="green">已通过</a-tag>
          <a-tag v-else-if="detailRecord.status === 2" color="red">已拒绝</a-tag>
        </a-descriptions-item>
        <a-descriptions-item v-if="detailRecord.reply" label="博主回复">
          <div class="detail-reply">{{ detailRecord.reply }}</div>
        </a-descriptions-item>
        <a-descriptions-item label="留言时间">{{ formatDateTime(detailRecord.createdAt) }}</a-descriptions-item>
      </a-descriptions>
    </a-modal>
  </div>
</template>

<style scoped>
.messages-management {
  padding: 24px;
}

.message-content {
  max-width: 300px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.reply-content {
  color: #52c41a;
  font-size: 12px;
}

.text-placeholder {
  color: #bfbfbf;
}

.detail-content,
.detail-reply {
  white-space: pre-wrap;
  word-break: break-word;
  line-height: 1.6;
}

.detail-reply {
  background: #f6ffed;
  padding: 8px 12px;
  border-radius: 4px;
  border-left: 3px solid #52c41a;
}
</style>
