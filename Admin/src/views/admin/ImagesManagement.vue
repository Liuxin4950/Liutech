<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { message, Modal } from 'ant-design-vue'
import {
  SearchOutlined,
  ReloadOutlined,
  DeleteOutlined,
  EyeOutlined,
  ClearOutlined,
  ExclamationCircleOutlined
} from '@ant-design/icons-vue'
import ImagesService from '../../services/images'
import type { Image, ImageListParams } from '../../services/images'
import { formatDateTime } from '../../utils/utils'
import { useTablePage, useCrudActions } from '@/composables'

// ============== 表格页面 ==============
const {
  loading, dataSource, selectedRowKeys, searchParams, pagination,
  load: loadImages, handleSearch, handleReset, handleTableChange, onSelectChange, clearSelection
} = useTablePage<Image, ImageListParams>({
  loadFn: (params) => ImagesService.getImageList(params),
  defaultSearchParams: {
    fileName: '',
    mimeType: '',
    status: undefined,
    includeDeleted: false
  },
  loadErrorMessage: '加载图片列表失败'
})

// ============== CRUD 操作 ==============
const {
  handleRestore,
  handlePermanentDelete,
  handleBatchPermanentDelete
} = useCrudActions({
  deleteFn: (id) => ImagesService.deleteImage(id),
  batchDeleteFn: (ids) => ImagesService.batchDeleteImages(ids),
  restoreFn: (id) => ImagesService.restoreImage(id),
  permanentDeleteFn: (id) => ImagesService.permanentDeleteImage(id),
  batchPermanentDeleteFn: (ids) => ImagesService.batchPermanentDeleteImages(ids),
  onRefresh: loadImages,
  clearSelection,
  entityName: '图片'
})

// 自定义删除（支持额外警告信息）
const handleDelete = async (id: number) => {
  const res = await ImagesService.deleteImage(id)
  if (res.code === 200) {
    if (res.data?.warning) {
      message.warning(res.data.warning)
    } else {
      message.success('删除成功')
    }
    loadImages()
  } else {
    message.error(res.message || '删除失败')
  }
}

const handleBatchDelete = async () => {
  if (!selectedRowKeys.value.length) {
    message.warning('请选择要删除的图片')
    return
  }
  const res = await ImagesService.batchDeleteImages(selectedRowKeys.value)
  if (res.code === 200) {
    if (res.data?.warning) {
      message.warning(res.data.warning)
    } else {
      message.success('批量删除成功')
    }
    selectedRowKeys.value = []
    loadImages()
  } else {
    message.error(res.message || '批量删除失败')
  }
}

// ============== 格式化函数 ==============
const formatFileSize = (bytes: number | undefined | null): string => {
  if (bytes === undefined || bytes === null || bytes === 0) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB']
  const k = 1024
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + units[i]
}

const formatDimensions = (width: number | null, height: number | null): string => {
  if (!width || !height) return '-'
  return `${width} x ${height}`
}

// ============== 图片预览 ==============
const previewVisible = ref(false)
const previewImage = ref('')
const previewFileName = ref('')

const handlePreview = (record: Image) => {
  previewImage.value = record.fileUrl
  previewFileName.value = record.fileName
  previewVisible.value = true
}

// ============== 孤立图片清理 ==============
const orphanModalVisible = ref(false)
const orphanImages = ref<Image[]>([])
const orphanLoading = ref(false)
const cleanupLoading = ref(false)

const handleShowOrphans = async () => {
  orphanModalVisible.value = true
  orphanLoading.value = true
  try {
    const res = await ImagesService.getOrphanImages()
    if (res.code === 200) {
      orphanImages.value = res.data
    } else {
      message.error(res.message || '查询孤立图片失败')
    }
  } catch (e) {
    message.error('查询孤立图片失败')
  } finally {
    orphanLoading.value = false
  }
}

const handleCleanupOrphans = () => {
  Modal.confirm({
    title: '确认清理',
    icon: h(ExclamationCircleOutlined),
    content: `确定要清理所有 ${orphanImages.value.length} 张孤立图片吗？此操作将同时删除文件系统中的文件，不可恢复！`,
    okText: '确定清理',
    okType: 'danger',
    cancelText: '取消',
    async onOk() {
      cleanupLoading.value = true
      try {
        const res = await ImagesService.cleanupOrphanImages()
        if (res.code === 200) {
          message.success(`成功清理 ${res.data} 张孤立图片`)
          orphanModalVisible.value = false
          loadImages()
        } else {
          message.error(res.message || '清理孤立图片失败')
        }
      } catch (e) {
        message.error('清理孤立图片失败')
      } finally {
        cleanupLoading.value = false
      }
    }
  })
}

// ============== 表格列定义 ==============
const columns = [
  { title: 'ID', dataIndex: 'id', key: 'id', width: 60 },
  { title: '文件名', key: 'fileName', width: 280 },
  { title: '大小', key: 'fileSize', width: 100 },
  { title: '尺寸', key: 'dimensions', width: 110 },
  { title: 'MIME类型', dataIndex: 'mimeType', key: 'mimeType', width: 140 },
  { title: '上传者', dataIndex: 'uploaderUsername', key: 'uploaderUsername', width: 100 },
  { title: '引用次数', dataIndex: 'usageCount', key: 'usageCount', width: 90 },
  { title: '创建时间', dataIndex: 'createdAt', key: 'createdAt', width: 170 },
  { title: '操作', key: 'action', width: 150, fixed: 'right' as const }
]
</script>

<template>
  <div class="p-24">
    <!-- 搜索区域 -->
    <a-card :bordered="false" class="mb-16">
      <a-form layout="horizontal" :model="searchParams">
        <a-row :gutter="24">
          <a-col :span="6">
            <a-form-item label="文件名" class="mb-0">
              <a-input v-model:value="searchParams.fileName" placeholder="请输入文件名" allow-clear @press-enter="handleSearch" />
            </a-form-item>
          </a-col>
          <a-col :span="5">
            <a-form-item label="MIME类型" class="mb-0">
              <a-input v-model:value="searchParams.mimeType" placeholder="如 image/png" allow-clear @press-enter="handleSearch" />
            </a-form-item>
          </a-col>
          <a-col :span="13" class="text-right">
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
              <a-button @click="handleShowOrphans">
                <template #icon><ClearOutlined /></template>
                孤立图片清理
              </a-button>
            </a-space>
          </a-col>
        </a-row>
      </a-form>
    </a-card>

    <!-- 列表区域 -->
    <a-card :bordered="false">
      <template #title>
        <span>图片列表</span>
      </template>
      <template #extra>
        <a-space>
          <a-button v-if="!searchParams.includeDeleted" danger :disabled="selectedRowKeys.length === 0" @click="handleBatchDelete">
            <template #icon><DeleteOutlined /></template>批量删除
          </a-button>
          <a-popconfirm
            v-if="searchParams.includeDeleted"
            title="确定要批量彻底删除选中的图片吗？此操作不可恢复！"
            ok-text="确定"
            cancel-text="取消"
            @confirm="handleBatchPermanentDelete(selectedRowKeys)"
          >
            <a-button danger :disabled="selectedRowKeys.length === 0">
              <template #icon><DeleteOutlined /></template>批量彻底删除
            </a-button>
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
          <!-- 文件名列：带缩略图 -->
          <template v-if="column.key === 'fileName'">
            <div class="file-name-cell">
              <a-image
                :width="40"
                :height="40"
                :src="record.fileUrl"
                :preview="{ src: record.fileUrl }"
                :fallback="'data:image/svg+xml;base64,PHN2ZyB3aWR0aD0iNDAiIGhlaWdodD0iNDAiIHhtbG5zPSJodHRwOi8vd3d3LnczLm9yZy8yMDAwL3N2ZyI+PHJlY3Qgd2lkdGg9IjQwIiBoZWlnaHQ9IjQwIiBmaWxsPSIjZjVmNWY1Ii8+PHRleHQgeD0iMjAiIHk9IjI0IiB0ZXh0LWFuY2hvcj0ibWlkZGxlIiBmaWxsPSIjY2NjIiBmb250LXNpemU9IjEyIj7lm77niYc8L3RleHQ+PC9zdmc+'"
                class="image-thumbnail"
              />
              <div class="file-name-text">
                <a-tooltip :title="record.fileName">
                  <span class="file-name">{{ record.fileName }}</span>
                </a-tooltip>
                <a-tag v-if="record.deletedAt" color="red">已删除</a-tag>
              </div>
            </div>
          </template>

          <!-- 文件大小 -->
          <template v-else-if="column.key === 'fileSize'">
            {{ formatFileSize(record.fileSize) }}
          </template>

          <!-- 尺寸 -->
          <template v-else-if="column.key === 'dimensions'">
            {{ formatDimensions(record.width, record.height) }}
          </template>

          <!-- 引用次数 -->
          <template v-else-if="column.key === 'usageCount'">
            <a-tag :color="record.usageCount > 0 ? 'blue' : 'default'">
              {{ record.usageCount }}
            </a-tag>
          </template>

          <!-- 创建时间 -->
          <template v-else-if="column.key === 'createdAt'">
            {{ formatDateTime(record.createdAt) }}
          </template>

          <!-- 操作列 -->
          <template v-else-if="column.key === 'action'">
            <a-space>
              <a-button type="link" size="small" @click="handlePreview(record)">
                <template #icon><EyeOutlined /></template>
              </a-button>
              <template v-if="!record.deletedAt">
                <a-popconfirm title="确定删除该图片吗？" @confirm="handleDelete(record.id)">
                  <a-button type="link" size="small" danger>删除</a-button>
                </a-popconfirm>
              </template>
              <template v-else>
                <a-popconfirm title="确定恢复该图片吗？" @confirm="handleRestore(record.id)">
                  <a-button type="link" size="small">恢复</a-button>
                </a-popconfirm>
                <a-popconfirm
                  title="确定要彻底删除该图片吗？此操作将同时删除文件系统中的文件，不可恢复！"
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

    <!-- 图片预览弹窗 -->
    <a-modal
      v-model:open="previewVisible"
      :title="previewFileName"
      :footer="null"
      destroy-on-close
      centered
      width="auto"
      class="image-preview-modal"
      @cancel="previewVisible = false"
    >
      <img :src="previewImage" :alt="previewFileName" class="preview-image" />
    </a-modal>

    <!-- 孤立图片清理弹窗 -->
    <a-modal
      v-model:open="orphanModalVisible"
      title="孤立图片清理"
      width="800"
      destroy-on-close
      :footer="null"
      @cancel="orphanModalVisible = false"
    >
      <div class="orphan-modal-content">
        <a-alert
          v-if="orphanImages.length > 0"
          :message="`发现 ${orphanImages.length} 张孤立图片（未被任何文章引用）`"
          description="清理操作将同时删除文件系统中的文件，此操作不可恢复。"
          type="warning"
          show-icon
          class="mb-16"
        />
        <a-empty v-if="!orphanLoading && orphanImages.length === 0" description="没有孤立图片" />

        <a-spin :spinning="orphanLoading">
          <a-table
            v-if="orphanImages.length > 0"
            :columns="[
              { title: 'ID', dataIndex: 'id', width: 60 },
              { title: '文件名', dataIndex: 'fileName' },
              { title: '大小', key: 'fileSize', width: 100 },
              { title: 'MIME类型', dataIndex: 'mimeType', width: 140 },
              { title: '上传者', dataIndex: 'uploaderUsername', width: 100 },
              { title: '创建时间', key: 'createdAt', width: 170 }
            ]"
            :data-source="orphanImages"
            :pagination="{ pageSize: 5 }"
            row-key="id"
            size="small"
          >
            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'fileSize'">
                {{ formatFileSize(record.fileSize) }}
              </template>
              <template v-else-if="column.key === 'createdAt'">
                {{ formatDateTime(record.createdAt) }}
              </template>
            </template>
          </a-table>
        </a-spin>

        <div v-if="orphanImages.length > 0" class="orphan-actions">
          <a-button
            type="primary"
            danger
            :loading="cleanupLoading"
            @click="handleCleanupOrphans"
          >
            <template #icon><ClearOutlined /></template>
            清理全部 {{ orphanImages.length }} 张孤立图片
          </a-button>
        </div>
      </div>
    </a-modal>
  </div>
</template>

<script lang="ts">
import { h } from 'vue'
export default { name: 'ImagesManagement' }
</script>

<style scoped>
.mb-16 {
  margin-bottom: 16px;
}

.file-name-cell {
  display: flex;
  align-items: center;
  gap: 10px;
}

.file-name-text {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.file-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 180px;
  font-size: 13px;
}


.image-thumbnail {
  border-radius: 4px;
  object-fit: cover;
  flex-shrink: 0;
}

.image-preview-modal :deep(.ant-modal-body) {
  padding: 0;
  text-align: center;
}

.preview-image {
  max-width: 90vw;
  max-height: 80vh;
  object-fit: contain;
}

.orphan-modal-content {
  min-height: 100px;
}

.orphan-actions {
  margin-top: 16px;
  text-align: right;
}
</style>

