<script setup lang="ts">
import { ref } from 'vue'
import { SearchOutlined, ReloadOutlined, DeleteOutlined } from '@ant-design/icons-vue'
import LtStatusTag from '@/components/LtStatusTag.vue'
import LtFilterBar from '@/components/LtFilterBar.vue'
import LtTableCard from '@/components/LtTableCard.vue'
import CommentThreadDrawer from '@/components/CommentThreadDrawer.vue'
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

const detailOpen = ref(false)
const detailCommentId = ref<number>()
const openDetail = (id: number) => {
  detailCommentId.value = id
  detailOpen.value = true
}

const columns = [
  { title: 'ID', dataIndex: 'id', key: 'id', width: 80 },
  { title: '文章标题', dataIndex: 'postTitle', key: 'postTitle', ellipsis: true },
  { title: '评论者', key: 'username', width: 120 },
  { title: '评论内容', dataIndex: 'content', key: 'content', ellipsis: true },
  { title: '父评论', dataIndex: 'parentId', key: 'parentId', width: 220 },
  { title: '状态', key: 'status', width: 80 },
  { title: '创建时间', dataIndex: 'createdAt', key: 'createdAt', width: 180 },
  { title: '操作', key: 'action', width: 220, fixed: 'right' as const }
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
    <LtFilterBar>
      <a-form-item label="文章" class="lt-filter-bar__field">
        <a-select
          v-model:value="searchParams.postId"
          placeholder="输入文章标题搜索"
          show-search
          :filter-option="false"
          :options="postOptions"
          :loading="postSearchLoading"
          @search="handlePostSearch"
          allow-clear
        />
      </a-form-item>
      <a-form-item label="用户" class="lt-filter-bar__field">
        <a-select
          v-model:value="searchParams.userId"
          placeholder="输入用户名搜索"
          show-search
          :filter-option="false"
          :options="userOptions"
          :loading="userSearchLoading"
          @search="handleUserSearch"
          allow-clear
        />
      </a-form-item>
      <a-form-item label="状态" class="lt-filter-bar__field">
        <a-select v-model:value="searchParams.status" placeholder="全部" allow-clear>
          <a-select-option value="active">正常</a-select-option>
          <a-select-option value="deleted">已删除</a-select-option>
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

    <LtTableCard title="评论列表">
      <template #actions>
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
      </template>
      <a-table
        :columns="prefColumns"
        :data-source="dataSource"
        :loading="loading"
        :pagination="pagination"
        :row-selection="{ selectedRowKeys, onChange: onSelectChange }"
        row-key="id"
        @change="handleTableChange"
        :scroll="{ x: 1350 }"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'username'">
            {{ record.authorType === 'BOT' ? record.bot?.name || 'AI 角色' : record.user?.username || '-' }}
            <a-tag v-if="record.authorType === 'BOT'" color="purple">AI</a-tag>
          </template>
          <template v-else-if="column.key === 'content'">
            <a-tooltip :title="record.content">
              {{ truncateContent(record.content) }}
            </a-tooltip>
          </template>
          <template v-else-if="column.key === 'parentId'">
            <div v-if="record.parentId" class="parent-preview">
              <a-button type="link" size="small" class="parent-link" @click="openDetail(record.id)">
                回复 {{ record.parent?.authorName || '#' + record.parentId }}
              </a-button>
              <a-tag v-if="record.parent?.deletedAt">已删除</a-tag>
              <a-tooltip v-if="record.parent" :title="record.parent.content">
                <p>{{ record.parent.content }}</p>
              </a-tooltip>
              <span v-else class="text-placeholder">父评论不可用</span>
              <span v-if="record.parent && record.parent.postId !== record.postId" class="parent-warning">父评论文章不一致</span>
            </div>
            <span v-else class="text-placeholder">根评论</span>
          </template>
          <template v-else-if="column.key === 'status'">
            <LtStatusTag :status="record.deletedAt ? 'deleted' : 'normal'" />
          </template>
          <template v-else-if="column.key === 'createdAt'">{{ formatDateTime(record.createdAt) }}</template>
          <template v-else-if="column.key === 'action'">
            <a-space>
              <a-button type="link" size="small" @click="openDetail(record.id)">详情</a-button>
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
    </LtTableCard>
    <CommentThreadDrawer v-model:open="detailOpen" :comment-id="detailCommentId" />
  </div>
</template>

<style scoped>
.parent-preview { min-width: 0; }
.parent-link { padding: 0; height: auto; max-width: 100%; white-space: normal; text-align: left; }
.parent-preview p { margin: 4px 0 0; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; overflow-wrap: anywhere; color: var(--lt-color-text-secondary); font-size: 12px; }
.parent-warning { display: block; margin-top: 4px; color: var(--lt-color-warning-text, var(--lt-color-text-secondary)); font-size: 12px; }
</style>


