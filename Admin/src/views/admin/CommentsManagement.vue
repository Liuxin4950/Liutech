<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import { SearchOutlined, ReloadOutlined, DeleteOutlined, CommentOutlined } from '@ant-design/icons-vue'
import CommentsService from '../../services/comments'
import type { Comment, CommentListParams } from '../../services/comments'
import { formatDateTime } from '../../utils/utils'

// 响应式数据
const loading = ref(false)
const dataSource = ref<Comment[]>([])
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
const searchParams = ref<CommentListParams>({
  postId: undefined,
  userId: undefined,
  status: undefined,
  includeDeleted: false
})

const columns = [
  { title: 'ID', dataIndex: 'id', key: 'id', width: 80 },
  { title: '文章标题', dataIndex: 'postTitle', key: 'postTitle', ellipsis: true },
  { title: '评论者', key: 'username', width: 120 },
  { title: '评论内容', dataIndex: 'content', key: 'content', ellipsis: true },
  { title: '父评论ID', dataIndex: 'parentId', key: 'parentId', width: 100 },
  { title: '状态', key: 'status', width: 80 },
  { title: '创建时间', dataIndex: 'createdAt', key: 'createdAt', width: 180 },
  { title: '操作', key: 'action', width: 180, fixed: 'right' as const }
]

// 截取评论内容前100字
const truncateContent = (content: string) => {
  if (!content) return ''
  return content.length > 100 ? content.substring(0, 100) + '...' : content
}

// 列表加载
const loadComments = async () => {
  try {
    loading.value = true
    const params = { page: pagination.current, size: pagination.pageSize, ...searchParams.value }
    const res = await CommentsService.getCommentList(params)
    if (res.code === 200) {
      dataSource.value = res.data.records
      pagination.total = res.data.total
    } else {
      message.error(res.message || '加载评论失败')
    }
  } catch (e) {
    message.error('加载评论失败')
  } finally {
    loading.value = false
  }
}

const handleSearch = () => { pagination.current = 1; loadComments() }
const handleReset = () => {
  searchParams.value = { postId: undefined, userId: undefined, status: undefined, includeDeleted: false }
  pagination.current = 1
  loadComments()
}

// 软删除
const handleDelete = async (id: number) => {
  const res = await CommentsService.deleteComment(id)
  if (res.code === 200) { message.success('删除成功'); loadComments() } else { message.error(res.message || '删除失败') }
}

// 批量软删除
const handleBatchDelete = async () => {
  if (!selectedRowKeys.value.length) { message.warning('请选择要删除的评论'); return }
  const res = await CommentsService.batchDeleteComments(selectedRowKeys.value)
  if (res.code === 200) { message.success('批量删除成功'); selectedRowKeys.value = []; loadComments() } else { message.error(res.message || '批量删除失败') }
}

// 恢复
const handleRestore = async (id: number) => {
  const res = await CommentsService.restoreComment(id)
  if (res.code === 200) { message.success('恢复成功'); loadComments() } else { message.error(res.message || '恢复失败') }
}

// 彻底删除
const handlePermanentDelete = async (id: number) => {
  const res = await CommentsService.permanentDeleteComment(id)
  if (res.code === 200) { message.success('彻底删除成功'); loadComments() } else { message.error(res.message || '彻底删除失败') }
}

// 批量彻底删除
const handleBatchPermanentDelete = async () => {
  if (!selectedRowKeys.value.length) { message.warning('请选择要彻底删除的评论'); return }
  const res = await CommentsService.batchPermanentDeleteComments(selectedRowKeys.value)
  if (res.code === 200) { message.success('批量彻底删除成功'); selectedRowKeys.value = []; loadComments() } else { message.error(res.message || '批量彻底删除失败') }
}

const handleTableChange = (p: any) => {
  pagination.current = p.current
  pagination.pageSize = p.pageSize
  loadComments()
}
const onSelectChange = (keys: number[]) => { selectedRowKeys.value = keys }

onMounted(() => { loadComments() })
</script>

<template>
  <div class="comments-management">
    <a-card :bordered="false" class="mb-16">
      <a-form layout="horizontal" :model="searchParams">
        <a-row :gutter="24">
          <a-col :span="5">
            <a-form-item label="文章ID" class="mb-0">
              <a-input-number v-model:value="searchParams.postId" placeholder="文章ID" allow-clear style="width: 100%" />
            </a-form-item>
          </a-col>
          <a-col :span="5">
            <a-form-item label="用户ID" class="mb-0">
              <a-input-number v-model:value="searchParams.userId" placeholder="用户ID" allow-clear style="width: 100%" />
            </a-form-item>
          </a-col>
          <a-col :span="5">
            <a-form-item label="状态" class="mb-0">
              <a-select v-model:value="searchParams.status" placeholder="全部" allow-clear>
                <a-select-option value="active">正常</a-select-option>
                <a-select-option value="deleted">已删除</a-select-option>
              </a-select>
            </a-form-item>
          </a-col>
          <a-col :span="9" class="text-right">
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
        <span>评论列表</span>
      </template>
      <template #extra>
        <a-space>
          <a-button v-if="!searchParams.includeDeleted" danger :disabled="selectedRowKeys.length === 0" @click="handleBatchDelete">
            <template #icon><DeleteOutlined /></template>批量删除
          </a-button>
          <a-popconfirm
            v-if="searchParams.includeDeleted"
            title="确定要批量彻底删除选中的评论吗？此操作不可恢复！"
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
        row-key="id"
        @change="handleTableChange"
        :scroll="{ x: 1100 }"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'username'">
            {{ record.user?.username || '-' }}
          </template>
          <template v-else-if="column.key === 'content'">
            <a-tooltip :title="record.content">
              {{ truncateContent(record.content) }}
            </a-tooltip>
          </template>
          <template v-else-if="column.key === 'parentId'">
            {{ record.parentId || '-' }}
          </template>
          <template v-else-if="column.key === 'status'">
            <a-tag v-if="record.deletedAt" color="red">已删除</a-tag>
            <a-tag v-else color="green">正常</a-tag>
          </template>
          <template v-else-if="column.key === 'createdAt'">{{ formatDateTime(record.createdAt) }}</template>
          <template v-else-if="column.key === 'action'">
            <a-space>
              <template v-if="!record.deletedAt">
                <a-popconfirm title="确定删除该评论吗？" @confirm="handleDelete(record.id)">
                  <a-button type="link" size="small" danger>删除</a-button>
                </a-popconfirm>
              </template>
              <template v-else>
                <a-popconfirm title="确定恢复该评论吗？" @confirm="handleRestore(record.id)">
                  <a-button type="link" size="small">恢复</a-button>
                </a-popconfirm>
                <a-popconfirm
                  title="确定要彻底删除该评论吗？此操作不可恢复！"
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
  </div>
</template>

<style scoped>
.comments-management {
  padding: 24px;
}

.mb-16 {
  margin-bottom: 16px;
}

.text-right {
  text-align: right;
}
</style>
