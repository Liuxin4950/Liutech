<script setup lang="ts">
import { ref } from 'vue'
import { message } from 'ant-design-vue'
import { PlusOutlined, DeleteOutlined, PictureOutlined, UploadOutlined, SortAscendingOutlined, SearchOutlined, ReloadOutlined } from '@ant-design/icons-vue'
import type { Carousel, CarouselListParams, CarouselFormData } from '../../services/carousel'
import CarouselService from '../../services/carousel'
import { formatDateTime, formatRelativeTime } from '../../utils/utils'
import { ImageUploadService } from '../../services/upload'
import { useTablePage, useCrudActions, useModalForm } from '@/composables'
import { useTableColumnPrefs } from '@/composables/useTableColumnPrefs'
import TableColumnSettings from '@/components/TableColumnSettings.vue'
import { useTableExport } from '@/composables/useTableExport'
import TableExportButton from '@/components/TableExportButton.vue'

// ============== 表格页面 ==============
const {
  loading, dataSource, selectedRowKeys, searchParams, pagination,
  load: loadCarousels, handleSearch, handleReset, handleTableChange, onSelectChange, clearSelection
} = useTablePage<Carousel, CarouselListParams>({
  loadFn: (params) => {
    const { page, ...rest } = params
    return CarouselService.getCarouselList({ current: page, ...rest })
  },
  defaultSearchParams: {
    status: undefined,
    includeDeleted: false
  },
  loadErrorMessage: '加载轮播图列表失败'
})

// ============== CRUD 操作 ==============
const {
  handleDelete, handleBatchDelete, handleRestore,
  handlePermanentDelete, handleBatchPermanentDelete
} = useCrudActions({
  deleteFn: (id) => CarouselService.deleteCarousel(id),
  batchDeleteFn: (ids) => CarouselService.batchDeleteCarousels(ids),
  restoreFn: (id) => CarouselService.restoreCarousel(id),
  permanentDeleteFn: (id) => CarouselService.permanentDeleteCarousel(id),
  batchPermanentDeleteFn: (ids) => CarouselService.batchPermanentDeleteCarousels(ids),
  onRefresh: loadCarousels,
  clearSelection,
  entityName: '轮播图'
})

// ============== 弹窗表单 ==============
const {
  modalVisible, modalTitle, isEdit, editingId, confirmLoading,
  formRef, formModel, handleCancel
} = useModalForm<CarouselFormData>({
  createFn: (data) => CarouselService.createCarousel(data) as any,
  updateFn: (id, data) => CarouselService.updateCarousel(id, data) as any,
  defaultForm: () => ({
    title: '',
    imageUrl: '',
    linkUrl: '',
    sortOrder: 0,
    status: 1
  }),
  onCreateSuccess: () => { pagination.current = 1; loadCarousels() },
  onUpdateSuccess: loadCarousels,
  entityName: '轮播图'
})

// 图片预览和上传状态
const imagePreview = ref<string>('')
const imageUploading = ref(false)

// 自定义 openCreate（重置图片预览）
const openCreate = () => {
  isEdit.value = false
  modalTitle.value = '新建轮播图'
  editingId.value = null
  formModel.value = {
    title: '',
    imageUrl: '',
    linkUrl: '',
    sortOrder: 0,
    status: 1
  }
  imagePreview.value = ''
  modalVisible.value = true
}

// 自定义 openEdit（通过 ID 获取详情）
const openEdit = async (record: Carousel) => {
  if (!record.id) return
  isEdit.value = true
  modalTitle.value = '编辑轮播图'
  editingId.value = record.id

  try {
    const res = await CarouselService.getCarouselById(record.id)
    if (res.code === 200) {
      formModel.value = {
        title: res.data.title ?? '',
        imageUrl: res.data.imageUrl ?? '',
        linkUrl: res.data.linkUrl ?? '',
        sortOrder: res.data.sortOrder ?? 0,
        status: res.data.status ?? 1
      }
      imagePreview.value = res.data.imageUrl
      modalVisible.value = true
    } else {
      message.error(res.message || '获取轮播图详情失败')
    }
  } catch {
    message.warning('获取轮播图详情失败，请稍后重试')
  }
}

// 自定义提交（含图片上传校验）
const handleSubmit = async () => {
  try {
    confirmLoading.value = true

    if (imageUploading.value) {
      message.warning('图片正在上传中，请稍候')
      confirmLoading.value = false
      return
    }

    if (!formModel.value.imageUrl) {
      message.error('请上传轮播图图片')
      confirmLoading.value = false
      return
    }

    await formRef.value?.validateFields?.(['title'])

    if (isEdit.value) {
      const res = await CarouselService.updateCarousel(editingId.value as number, formModel.value)
      if (res.code === 200) {
        message.success('更新成功')
        modalVisible.value = false
        loadCarousels()
      } else {
        message.error(res.message || '更新失败')
      }
    } else {
      const res = await CarouselService.createCarousel(formModel.value as CarouselFormData)
      if (res.code === 200) {
        message.success('创建成功')
        modalVisible.value = false
        pagination.current = 1
        loadCarousels()
      } else {
        message.error(res.message || '创建失败')
      }
    }
  } catch (e) {
  } finally {
    confirmLoading.value = false
  }
}

// ============== 图片上传 ==============
const handleImageChange = async (info: any) => {
  const file = info.fileList?.[0]?.originFileObj || info.file?.originFileObj
  if (!file) return

  imagePreview.value = URL.createObjectURL(file)

  try {
    imageUploading.value = true
    const result = await ImageUploadService.uploadImage(file)
    formModel.value.imageUrl = result.fileUrl
    message.success('图片上传成功')
  } catch (e: any) {
    message.error(e.message || '图片上传失败')
    imagePreview.value = formModel.value.imageUrl || ''
  } finally {
    imageUploading.value = false
  }
}

// ============== 状态与排序 ==============
const handleStatusChange = async (id: number, status: number) => {
  try {
    const res = await CarouselService.updateCarouselStatus(id, status)
    if (res && res.code === 200) {
      message.success('状态更新成功')
      loadCarousels()
    } else {
      message.warning(res?.message || '状态更新失败')
    }
  } catch (e) {
    message.warning('状态更新失败，请稍后重试')
  }
}

const handleSortChange = async (id: number, direction: 'up' | 'down') => {
  const index = dataSource.value.findIndex((item) => item.id === id)
  if (index === -1) return

  const newIndex = direction === 'up' ? index - 1 : index + 1
  if (newIndex < 0 || newIndex >= dataSource.value.length) return

  const current = dataSource.value[index]
  const neighbor = dataSource.value[newIndex]
  const tempSort = current.sortOrder
  current.sortOrder = neighbor.sortOrder
  neighbor.sortOrder = tempSort

  try {
    const [res1, res2] = await Promise.all([
      CarouselService.updateCarouselSort(current.id!, current.sortOrder),
      CarouselService.updateCarouselSort(neighbor.id!, neighbor.sortOrder)
    ])
    if (res1?.code === 200 && res2?.code === 200) {
      message.success('排序更新成功')
    } else {
      message.warning('排序更新失败')
    }
    loadCarousels()
  } catch (e) {
    message.warning('排序更新失败，请稍后重试')
    loadCarousels()
  }
}

// ============== 配置数据 ==============
const columns = [
  { title: '标题', dataIndex: 'title', key: 'title', width: 180 },
  { title: '图片', key: 'image', width: 100 },
  { title: '跳转链接', dataIndex: 'linkUrl', key: 'linkUrl', width: 180, ellipsis: true },
  { title: '排序', dataIndex: 'sortOrder', key: 'sortOrder', width: 70 },
  { title: '状态', dataIndex: 'statusName', key: 'statusName', width: 70 },
  { title: '删除状态', key: 'deleteStatus', width: 70 },
  { title: '创建时间', dataIndex: 'createdAt', key: 'createdAt', width: 160 },
  { title: '操作', key: 'action', width: 260, fixed: 'right' }
]

const columnPrefsCtrl = useTableColumnPrefs('carousels', columns, { alwaysVisible: ["action"] })
const prefColumns = columnPrefsCtrl.prefColumns

const exportCtrl = useTableExport({
  columns: prefColumns,
  rows: dataSource,
  filename: 'carousels',
})

const statusOptions = [
  { label: '禁用', value: 0 },
  { label: '启用', value: 1 }
]
</script>

<template>
  <div class="p-24">
    <!-- 搜索区域 -->
    <a-card :bordered="false" class="mb-16">
      <a-form layout="horizontal" :model="searchParams">
        <a-row :gutter="[16, 12]" align="bottom">
          <a-col :xs="24" :sm="12" :lg="8" :xl="6">
            <a-form-item label="状态" class="mb-0">
              <a-select v-model:value="searchParams.status" placeholder="请选择状态" allow-clear>
                <a-select-option v-for="opt in statusOptions" :key="opt.value" :value="opt.value">
                  {{ opt.label }}
                </a-select-option>
              </a-select>
            </a-form-item>
          </a-col>
          <a-col :xs="24" :sm="12" :lg="8" :xl="6" class="search-actions">
            <a-space>
              <a-tooltip title="显示已删除">
                <a-switch v-model:checked="searchParams.includeDeleted" @change="handleSearch" checked-children="删" un-checked-children="正常" />
              </a-tooltip>
              <a-button type="primary" @click="handleSearch"><template #icon><SearchOutlined /></template>搜索</a-button>
              <a-button @click="handleReset"><template #icon><ReloadOutlined /></template>重置</a-button>
            </a-space>
          </a-col>
        </a-row>
      </a-form>
    </a-card>

    <!-- 表格区域 -->
    <a-card :bordered="false">
      <template #title>
        <span><PictureOutlined /> 轮播图列表</span>
      </template>
      <template #extra>
        <a-space>
          <TableExportButton :ctrl="exportCtrl" />
          <TableColumnSettings :ctrl="columnPrefsCtrl" />
          <template v-if="searchParams.includeDeleted">
            <a-button type="primary" danger :disabled="selectedRowKeys.length === 0" @click="handleBatchPermanentDelete(selectedRowKeys)">
              <DeleteOutlined /> 批量彻底删除
            </a-button>
          </template>
          <template v-else>
            <a-button type="primary" @click="openCreate">
              <PlusOutlined /> 新建轮播图
            </a-button>
            <a-button danger :disabled="selectedRowKeys.length === 0" @click="handleBatchDelete(selectedRowKeys)">
              <DeleteOutlined /> 批量删除
            </a-button>
          </template>
        </a-space>
      </template>
      <a-table
        :columns="prefColumns"
        :data-source="dataSource"
        :loading="loading"
        :pagination="pagination"
        row-key="id"
        :row-selection="{
          selectedRowKeys,
          onChange: onSelectChange
        }"
        :scroll="{ x: 1100 }"
        @change="handleTableChange"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'title'">
            <a-tooltip :title="record.title">
              <span class="title-text">{{ record.title }}</span>
            </a-tooltip>
          </template>

          <template v-else-if="column.key === 'image'">
            <a-image
              v-if="record.imageUrl"
              :src="record.imageUrl"
              :width="80"
              :height="50"
              style="object-fit: cover; border-radius: var(--lt-radius-sm);"
            />
            <span v-else class="text-muted">-</span>
          </template>

          <template v-else-if="column.key === 'linkUrl'">
            <a-tooltip :title="record.linkUrl">
              <a :href="record.linkUrl" target="_blank" v-if="record.linkUrl" class="link-text">
                {{ record.linkUrl }}
              </a>
              <span v-else class="text-muted">-</span>
            </a-tooltip>
          </template>

          <template v-else-if="column.key === 'deleteStatus'">
            <a-tag v-if="record.deletedAt" color="red">已删除</a-tag>
            <a-tag v-else color="green">正常</a-tag>
          </template>

          <template v-else-if="column.key === 'createdAt'">
            <div v-if="record.createdAt" class="time-cell">
              <div class="relative-time">{{ formatRelativeTime(record.createdAt) }}</div>
              <div class="absolute-time">{{ formatDateTime(record.createdAt) }}</div>
            </div>
            <span v-else>-</span>
          </template>

          <template v-else-if="column.key === 'action'">
            <a-space>
              <template v-if="!record.deletedAt">
                <a-button type="link" size="small" @click="openEdit(record)">
                  编辑
                </a-button>

                <a-button
                  type="link"
                  size="small"
                  :disabled="dataSource.indexOf(record) === 0"
                  @click="handleSortChange(record.id, 'up')"
                >
                  <SortAscendingOutlined style="transform: rotate(180deg)" />
                </a-button>
                <a-button
                  type="link"
                  size="small"
                  :disabled="dataSource.indexOf(record) === dataSource.length - 1"
                  @click="handleSortChange(record.id, 'down')"
                >
                  <SortAscendingOutlined />
                </a-button>

                <a-switch
                  :checked="record.status === 1"
                  checked-children="启用"
                  un-checked-children="禁用"
                  size="small"
                  @change="(checked: boolean) => handleStatusChange(record.id, checked ? 1 : 0)"
                />

                <a-popconfirm
                  title="确定删除该轮播图吗？"
                  @confirm="handleDelete(record.id)"
                >
                  <a-button type="link" size="small" danger>
                    删除
                  </a-button>
                </a-popconfirm>
              </template>
              <template v-else>
                <a-popconfirm title="确定恢复该轮播图吗？" @confirm="handleRestore(record.id)">
                  <a-button type="link" size="small">恢复</a-button>
                </a-popconfirm>
                <a-popconfirm
                  title="确定要彻底删除该轮播图吗？此操作不可恢复！"
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
    <a-modal
      v-model:open="modalVisible"
      :title="modalTitle"
      :confirm-loading="confirmLoading"
      :width="600"
      destroy-on-close
      @ok="handleSubmit"
      @cancel="handleCancel"
    >
      <a-form
        ref="formRef"
        :model="formModel"
        :rules="{ title: [{ required: true, message: '请输入轮播图标题' }] }"
        layout="vertical"
      >
        <a-row :gutter="16">
          <a-col :span="24">
            <a-form-item label="轮播图标题" name="title">
              <a-input v-model:value="formModel.title" placeholder="请输入轮播图标题" />
            </a-form-item>
          </a-col>
        </a-row>

        <a-row :gutter="16">
          <a-col :span="24">
            <a-form-item label="轮播图图片" extra="请上传轮播图图片（必填）">
              <a-upload
                name="file"
                :show-upload-list="false"
                accept="image/*"
                :before-upload="() => false"
                @change="handleImageChange"
              >
                <div v-if="imagePreview" class="image-preview">
                  <img :src="imagePreview" alt="图片预览" />
                </div>
                <a-button v-else :loading="imageUploading">
                  <UploadOutlined />
                  选择图片
                </a-button>
              </a-upload>
              <div class="upload-tips">
                支持 jpg、png、gif 格式，大小不超过 5MB
              </div>
            </a-form-item>
          </a-col>
        </a-row>

        <a-row :gutter="16">
          <a-col :span="24">
            <a-form-item label="跳转链接">
              <a-input v-model:value="formModel.linkUrl" placeholder="点击轮播图跳转的链接（可选）" />
            </a-form-item>
          </a-col>
        </a-row>

        <a-row :gutter="16">
          <a-col :span="12">
            <a-form-item label="排序">
              <a-input-number v-model:value="formModel.sortOrder" :min="0" style="width: 100%" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="状态" name="status">
              <a-select v-model:value="formModel.status">
                <a-select-option v-for="opt in statusOptions" :key="opt.value" :value="opt.value">
                  {{ opt.label }}
                </a-select-option>
              </a-select>
            </a-form-item>
          </a-col>
        </a-row>
      </a-form>
    </a-modal>
  </div>
</template>

<style scoped>
.title-text {
  font-weight: var(--lt-font-weight-medium);
  color: var(--text-main);
  cursor: pointer;
}

.title-text:hover {
  color: var(--color-primary);
}

.link-text {
  color: var(--color-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 200px;
  display: block;
}

.text-muted {
  color: var(--text-tertiary);
}

.time-cell {
  display: flex;
  flex-direction: column;
}

.relative-time {
  font-size: var(--lt-font-size-sm);
  color: var(--text-main);
  font-weight: var(--lt-font-weight-medium);
}

.absolute-time {
  font-size: var(--lt-font-size-xs);
  color: var(--text-tertiary);
  margin-top: 2px;
}

.upload-tips {
  margin-top: var(--lt-space-sm);
  font-size: var(--lt-font-size-xs);
  color: var(--text-tertiary);
}

.image-preview {
  width: 200px;
  height: 120px;
  border-radius: var(--lt-radius-lg);
  overflow: hidden;
  border: 1px dashed var(--lt-color-border);
}

.image-preview img {
  width: 100%;
  height: 100%;
  object-fit: cover;
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




