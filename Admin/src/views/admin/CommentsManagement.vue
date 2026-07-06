<script setup lang="ts">
import { ref } from 'vue'
import { SearchOutlined, ReloadOutlined, DeleteOutlined } from '@ant-design/icons-vue'
import { useTablePage, useCrudActions } from '@/composables'
import { useTableColumnPrefs } from '@/composables/useTableColumnPrefs'
import TableColumnSettings from '@/components/TableColumnSettings.vue'
import { useTableExport } from '@/composables/useTableExport'
import TableExportButton from '@/components/TableExportButton.vue'
import CommentsService from '../../services/comments'
import { PostsService } from '../../services/posts'
import { UserService } from '../../services/user'
import type { Comment, CommentListParams } from '../../services/comments'
import { formatDateTime } from '../../utils/utils'

const {
  loading, dataSource, selectedRowKeys, searchParams, pagination,
  load, handleSearch, handleReset, handleTableChange, onSelectChange, clearSelection
} = useTablePage<Comment, CommentListParams>({
  loadFn: (params) => CommentsService.getCommentList(params),
  defaultSearchParams: { postId: undefined, userId: undefined, status: undefined, includeDeleted: false },
  loadErrorMessage: '加载评论失败'
})

const {
  handleDelete, handleBatchDelete, handleRestore,
  handlePermanentDelete, handleBatchPermanentDelete
} = useCrudActions({
  deleteFn: (id) => CommentsService.deleteComment(id),
  batchDeleteFn: (ids) => CommentsService.batchDeleteComments(ids),
  restoreFn: (id) => CommentsService.restoreComment(id),
  permanentDeleteFn: (id) => CommentsService.permanentDeleteComment(id),
  batchPermanentDeleteFn: (ids) => CommentsService.batchPermanentDeleteComments(ids),
  onRefresh: load,
  clearSelection,
  entityName: '评论'
})

const truncateContent = (content: string) => {
  if (!content) return ''
  return content.length > 100 ? content.substring(0, 100) + '...' : content
}

const columns = [
  { title: 'ID', dataIndex: 'id', key: 'id', width: 80 },
  { title: '文章标题', dataIndex: 'postTitle', key: 'postTitle', ellipsis: true },
  { title: '评论者', key: 'username', width: 120 },
  { title: '评论内容', dataIndex: 'content', key: 'content', ellipsis: true },
  { title: '父评论', dataIndex: 'parentId', key: 'parentId', width: 100 },
  { title: '状态', key: 'status', width: 80 },
  { title: '创建时间', dataIndex: 'createdAt', key: 'createdAt', width: 180 },
  { title: '操作', key: 'action', width: 180, fixed: 'right' as const }
]

const columnPrefsCtrl = useTableColumnPrefs('comments', columns, { alwaysVisible: ["action"] })
const prefColumns = columnPrefsCtrl.prefColumns

const exportCtrl = useTableExport({
  columns: prefColumns,
  rows: dataSource,
  filename: 'comments',
})

const postOptions = ref<{ label: string; value: number }[]>([])
const postSearchLoading = ref(false)
const handlePostSearch = async (value: string) => {
  if (!value || value.length < 1) {
    postOptions.value = []
    return
  }
  try {
    postSearchLoading.value = true
    const res = await PostsService.getPostList({ page: 1, size: 20, title: value })
    if (res.code === 200) {
      postOptions.value = res.data.records.map((p) => ({
        label: p.title + (p.author?.username ? ' - ' + p.author.username : ''),
        value: p.id
      }))
    }
  } catch {
    // ignore
  } finally {
    postSearchLoading.value = false
  }
}

const userOptions = ref<{ label: string; value: number }[]>([])
const userSearchLoading = ref(false)
const handleUserSearch = async (value: string) => {
  if (!value || value.length < 1) {
    userOptions.value = []
    return
  }
  try {
    userSearchLoading.value = true
    const res = await UserService.getUserList({ page: 1, size: 20, username: value })
    if (res.code === 200) {
      userOptions.value = res.data.records.map((u: any) => ({
        label: u.username + (u.nickname ? ' (' + u.nickname + ')' : ''),
        value: u.id
      }))
    }
  } catch {
    // ignore
  } finally {
    userSearchLoading.value = false
  }
}
</script>

<template>
  <div class="p-24">
    <a-card :bordered="false" class="mb-16">
      <a-form layout="horizontal" :model="searchParams">
        <a-row :gutter="[16, 12]" align="bottom">
          <a-col :xs="24" :sm="12" :lg="8" :xl="6">
            <a-form-item label="文章" class="mb-0">
              <a-select
                v-model:value="searchParams.postId"
                placeholder="输入文章标题搜索"
                show-search
                :filter-option="false"
                :options="postOptions"
                :loading="postSearchLoading"
                @search="handlePostSearch"
                style="width: 100%"
                allow-clear
              />
            </a-form-item>
          </a-col>
          <a-col :xs="24" :sm="12" :lg="8" :xl="6">
            <a-form-item label="用户" class="mb-0">
              <a-select
                v-model:value="searchParams.userId"
                placeholder="输入用户名搜索"
                show-search
                :filter-option="false"
                :options="userOptions"
                :loading="userSearchLoading"
                @search="handleUserSearch"
                style="width: 100%"
                allow-clear
              />
            </a-form-item>
          </a-col>
          <a-col :xs="24" :sm="12" :lg="8" :xl="6">
            <a-form-item label="状态" class="mb-0">
              <a-select v-model:value="searchParams.status" placeholder="全部" allow-clear>
                <a-select-option value="active">正常</a-select-option>
                <a-select-option value="deleted">已删除</a-select-option>
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
      <template #title><span>评论列表</span></template>
      <template #extra>
        <a-space>
          <TableExportButton :ctrl="exportCtrl" />
          <TableColumnSettings :ctrl="columnPrefsCtrl" />
          <a-button v-if="!searchParams.includeDeleted" danger :disabled="selectedRowKeys.length === 0" @click="handleBatchDelete(selectedRowKeys)">
            <template #icon><DeleteOutlined /></template>批量删除
          </a-button>
          <a-popconfirm
            v-if="searchParams.includeDeleted"
            title="确定要批量彻底删除选中的评论吗？此操作不可恢复！"
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
            <a-tag v-if="record.parentId" color="blue">回复 #{{ record.parentId }}</a-tag>
            <span v-else class="text-placeholder">-</span>
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


