<script setup lang="ts">
import { ref } from 'vue'
import { message } from 'ant-design-vue'
import { SearchOutlined, ReloadOutlined, PlusOutlined, DeleteOutlined, HolderOutlined } from '@ant-design/icons-vue'
import { useTablePage, useCrudActions, useModalForm } from '@/composables'
import PostSeriesService, { type PostSeries, type PostSeriesListParams, type SeriesPostOrder } from '../../services/series'
import PostsService, { type PostListItem } from '../../services/posts'
import { ImageUploadService } from '../../services/upload'
import { formatDateTime } from '../../utils/utils'

// 表格页面：加载、分页、搜索、选择
const {
  loading, dataSource, selectedRowKeys, searchParams, pagination,
  load, handleSearch, handleReset, handleTableChange, onSelectChange, clearSelection
} = useTablePage<PostSeries, PostSeriesListParams>({
  loadFn: (params) => PostSeriesService.getSeriesList(params),
  defaultSearchParams: { name: '', includeDeleted: false },
  loadErrorMessage: '加载系列失败'
})

// CRUD 操作
const {
  handleDelete, handleBatchDelete, handleRestore,
  handlePermanentDelete, handleBatchPermanentDelete
} = useCrudActions({
  deleteFn: (id) => PostSeriesService.deleteSeries(id),
  batchDeleteFn: (ids) => PostSeriesService.batchDeleteSeries(ids),
  restoreFn: (id) => PostSeriesService.restoreSeries(id),
  permanentDeleteFn: (id) => PostSeriesService.permanentDeleteSeries(id),
  batchPermanentDeleteFn: (ids) => PostSeriesService.batchPermanentDeleteSeries(ids),
  onRefresh: load,
  clearSelection,
  entityName: '系列'
})

// 弹窗表单
const {
  modalVisible, modalTitle, confirmLoading,
  formRef, formModel, openCreate, openEdit, handleOk, handleCancel
} = useModalForm<PostSeries>({
  createFn: (data) => PostSeriesService.createSeries(data) as any,
  updateFn: (id, data) => PostSeriesService.updateSeries(id, data) as any,
  defaultForm: () => ({ name: '', description: '', coverImage: '' }),
  onCreateSuccess: () => { pagination.current = 1; load() },
  onUpdateSuccess: load,
  entityName: '系列'
})

const rules = {
  name: [{ required: true, message: '请输入系列名称' }]
}

const columns = [
  { title: '名称', dataIndex: 'name', key: 'name' },
  { title: '封面', key: 'cover', width: 90 },
  { title: '描述', dataIndex: 'description', key: 'description', ellipsis: true },
  { title: '文章数', dataIndex: 'postCount', key: 'postCount', width: 80 },
  { title: '状态', key: 'status', width: 80 },
  { title: '创建者', dataIndex: 'creatorUsername', key: 'creatorUsername', width: 100 },
  { title: '创建时间', dataIndex: 'createdAt', key: 'createdAt', width: 160 },
  { title: '操作', key: 'action', width: 230, fixed: 'right' as const }
]

// ============== 封面图上传 ==============
const coverInput = ref<HTMLInputElement>()
const uploadingCover = ref(false)
const triggerCoverUpload = () => coverInput.value?.click()
const handleCoverUpload = async (e: Event) => {
  const target = e.target as HTMLInputElement
  const file = target.files?.[0]
  if (!file) return
  try {
    uploadingCover.value = true
    const result = await ImageUploadService.uploadImage(file)
    formModel.value.coverImage = result.fileUrl
    message.success('封面上传成功')
  } catch (err: any) {
    message.error(err.message || '封面上传失败')
  } finally {
    uploadingCover.value = false
    target.value = ''
  }
}
const removeCover = () => { formModel.value.coverImage = '' }

// ============== 系列内文章管理（拖拽排序） ==============
const postsDrawerVisible = ref(false)
const postsDrawerSeries = ref<PostSeries | null>(null)
const seriesPosts = ref<PostListItem[]>([])
const loadingPosts = ref(false)
const savingOrder = ref(false)
const dragIndex = ref<number>(-1)

const openPostsDrawer = async (record: PostSeries) => {
  postsDrawerSeries.value = record
  postsDrawerVisible.value = true
  await loadSeriesPosts(record.id!)
}

const loadSeriesPosts = async (seriesId: number) => {
  loadingPosts.value = true
  try {
    const res = await PostsService.getPostList({ seriesId, size: 1000 })
    seriesPosts.value = res.data?.records || []
  } catch {
    message.error('加载系列文章失败')
  } finally {
    loadingPosts.value = false
  }
}

const onDragStart = (index: number) => { dragIndex.value = index }
const onDragOver = (e: DragEvent, index: number) => {
  e.preventDefault()
  if (dragIndex.value === index || dragIndex.value < 0) return
  const list = [...seriesPosts.value]
  const [moved] = list.splice(dragIndex.value, 1)
  list.splice(index, 0, moved)
  seriesPosts.value = list
  dragIndex.value = index
}
const onDragEnd = () => { dragIndex.value = -1 }

const saveOrder = async () => {
  if (!postsDrawerSeries.value?.id) return
  savingOrder.value = true
  try {
    const items: SeriesPostOrder[] = seriesPosts.value.map((p, idx) => ({
      postId: p.id,
      seriesSort: idx
    }))
    await PostSeriesService.updatePostsOrder(postsDrawerSeries.value.id, items)
    message.success('排序已保存')
    load()
  } catch {
    message.error('排序保存失败')
  } finally {
    savingOrder.value = false
  }
}
</script>

<template>
  <div class="p-24">
    <!-- 搜索卡片 -->
    <a-card :bordered="false" class="mb-16">
      <a-form layout="horizontal" :model="searchParams">
        <a-row :gutter="[16, 12]" align="bottom">
          <a-col :xs="24" :sm="12" :lg="8" :xl="6">
            <a-form-item label="名称" class="mb-0">
              <a-input v-model:value="searchParams.name" placeholder="请输入系列名称" allow-clear @press-enter="handleSearch" />
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

    <!-- 表格卡片 -->
    <a-card :bordered="false">
      <template #title><span>系列列表</span></template>
      <template #extra>
        <a-space>
          <a-button type="primary" @click="openCreate">
            <template #icon><PlusOutlined /></template>新建系列
          </a-button>
          <a-button v-if="!searchParams.includeDeleted" danger :disabled="selectedRowKeys.length === 0" @click="handleBatchDelete(selectedRowKeys)">
            <template #icon><DeleteOutlined /></template>批量删除
          </a-button>
          <a-popconfirm
            v-if="searchParams.includeDeleted"
            title="确定批量彻底删除选中系列吗？此操作不可恢复！"
            ok-text="确定"
            cancel-text="取消"
            @confirm="handleBatchPermanentDelete(selectedRowKeys)"
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
        :scroll="{ x: 960 }"
        @change="handleTableChange"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'cover'">
            <a-image v-if="record.coverImage" :src="record.coverImage" :width="70" :height="44" style="object-fit: cover; border-radius: 4px" />
            <span v-else style="color: var(--lt-color-text-quaternary)">无</span>
          </template>
          <template v-else-if="column.key === 'status'">
            <a-tag v-if="record.deletedAt" color="red">已删除</a-tag>
            <a-tag v-else color="green">正常</a-tag>
          </template>
          <template v-else-if="column.key === 'createdAt'">{{ formatDateTime(record.createdAt) }}</template>
          <template v-else-if="column.key === 'action'">
            <a-space>
              <template v-if="!record.deletedAt">
                <a-button type="link" size="small" @click="openPostsDrawer(record)">管理文章</a-button>
                <a-button type="link" size="small" @click="openEdit(record)">编辑</a-button>
                <a-popconfirm title="确定删除该系列吗？系列下文章不会被删除。" @confirm="handleDelete(record.id)">
                  <a-button type="link" size="small" danger>删除</a-button>
                </a-popconfirm>
              </template>
              <template v-else>
                <a-popconfirm title="确定恢复该系列吗？" @confirm="handleRestore(record.id)">
                  <a-button type="link" size="small">恢复</a-button>
                </a-popconfirm>
                <a-popconfirm
                  title="确定彻底删除该系列吗？此操作不可恢复！"
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

    <!-- 新建/编辑弹窗 -->
    <a-modal v-model:open="modalVisible" :title="modalTitle" :confirm-loading="confirmLoading" @ok="handleOk" @cancel="handleCancel" destroy-on-close>
      <a-form :model="formModel" :rules="rules" ref="formRef" layout="vertical">
        <a-form-item name="name" label="系列名称" required>
          <a-input v-model:value="formModel.name" placeholder="请输入系列名称" maxlength="50" />
        </a-form-item>
        <a-form-item name="description" label="系列描述">
          <a-textarea v-model:value="formModel.description" placeholder="请输入系列描述" :rows="3" maxlength="500" />
        </a-form-item>
        <a-form-item name="coverImage" label="系列封面">
          <div class="cover-upload">
            <div v-if="formModel.coverImage" class="cover-preview">
              <img :src="formModel.coverImage" alt="封面" />
              <a-button type="text" danger size="small" @click="removeCover">
                <template #icon><DeleteOutlined /></template>移除
              </a-button>
            </div>
            <div v-else class="cover-placeholder" @click="triggerCoverUpload">
              <PlusOutlined />
              <span>{{ uploadingCover ? '上传中...' : '上传封面' }}</span>
            </div>
            <input ref="coverInput" type="file" accept="image/*" style="display:none" @change="handleCoverUpload" />
          </div>
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- 系列内文章管理抽屉 -->
    <a-drawer
      v-model:open="postsDrawerVisible"
      :title="`管理系列文章 - ${postsDrawerSeries?.name || ''}`"
      width="520"
    >
      <div class="drawer-toolbar">
        <span style="color: var(--lt-color-text-secondary); font-size: 13px">共 {{ seriesPosts.length }} 篇，拖拽行调整顺序后点击保存</span>
        <a-button type="primary" size="small" :loading="savingOrder" :disabled="seriesPosts.length === 0" @click="saveOrder">保存排序</a-button>
      </div>
      <a-list :loading="loadingPosts" :data-source="seriesPosts" :locale="{ emptyText: '该系列暂无文章' }">
        <template #renderItem="{ item, index }">
          <a-list-item
            class="series-post-item"
            draggable="true"
            @dragstart="onDragStart(index)"
            @dragover="onDragOver($event, index)"
            @dragend="onDragEnd"
          >
            <div class="post-row">
              <HolderOutlined class="drag-handle" />
              <span class="post-sort">{{ index + 1 }}</span>
              <span class="post-title" :title="item.title">{{ item.title }}</span>
              <a-tag v-if="item.status !== 'published'" color="orange">{{ item.status }}</a-tag>
            </div>
          </a-list-item>
        </template>
      </a-list>
    </a-drawer>
  </div>
</template>

<style scoped>
.cover-upload {
  width: 100%;
}
.cover-preview {
  display: inline-flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 4px;
}
.cover-preview img {
  width: 200px;
  height: 120px;
  object-fit: cover;
  border-radius: 6px;
  border: 1px solid var(--lt-color-border-secondary);
}
.cover-placeholder {
  width: 200px;
  height: 120px;
  border: 1px dashed var(--lt-color-border);
  border-radius: 6px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  cursor: pointer;
  color: var(--lt-color-text-tertiary);
  transition: border-color 0.2s, color 0.2s;
}
.cover-placeholder:hover {
  border-color: var(--lt-color-primary);
  color: var(--lt-color-primary);
}

.drawer-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.series-post-item {
  cursor: grab;
  border: 1px solid var(--lt-color-border-secondary) !important;
  border-radius: 6px !important;
  margin-bottom: 8px !important;
  padding: 10px 12px !important;
  transition: box-shadow 0.2s, border-color 0.2s;
}
.series-post-item:hover {
  border-color: var(--lt-color-primary) !important;
}
.series-post-item:active {
  cursor: grabbing;
}
.post-row {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
}
.drag-handle {
  color: var(--lt-color-text-quaternary);
  cursor: grab;
}
.post-sort {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 24px;
  height: 24px;
  border-radius: 50%;
  background: var(--lt-color-fill-secondary);
  color: var(--lt-color-text-secondary);
  font-size: 12px;
  flex-shrink: 0;
}
.post-title {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
