<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import { PlusOutlined, DeleteOutlined, PictureOutlined, UploadOutlined } from '@ant-design/icons-vue'
import type { Carousel } from '../../services/carousel'
import CarouselService from '../../services/carousel'
import { formatDateTime, formatRelativeTime } from '../../utils/uitls'
import { ImageUploadService } from '../../services/upload'

// 响应式数据
const loading = ref(false)
const dataSource = ref<Carousel[]>([])
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
const searchParams = ref({
  status: undefined as number | undefined,
  includeDeleted: false
})

// 表格列定义
const columns = [
  { title: '标题', dataIndex: 'title', key: 'title', width: 180 },
  { title: '图片', key: 'image', width: 100 },
  { title: '跳转链接', dataIndex: 'linkUrl', key: 'linkUrl', width: 180, ellipsis: true },
  { title: '排序', dataIndex: 'sortOrder', key: 'sortOrder', width: 70 },
  { title: '状态', dataIndex: 'statusName', key: 'statusName', width: 70 },
  { title: '删除状态', key: 'deleteStatus', width: 70 },
  { title: '创建时间', dataIndex: 'createdAt', key: 'createdAt', width: 160 },
  { title: '操作', key: 'action', width: 200, fixed: 'right' }
]

const statusOptions = [
  { label: '禁用', value: 0 },
  { label: '启用', value: 1 }
]

// ============== 新建/编辑 弹窗 ==============
const modalVisible = ref(false)
const modalTitle = ref('新建轮播图')
const isEdit = ref(false)
const editingId = ref<number | null>(null)
const confirmLoading = ref(false)

// 图片预览和上传状态
const imagePreview = ref<string>('')
const imageUploading = ref(false)

const formRef = ref()
const formModel = ref({
  title: '',
  imageUrl: '',
  linkUrl: '',
  sortOrder: 0,
  status: 1
})

// ============== 弹窗操作 ==============
const openCreate = () => {
  modalTitle.value = '新建轮播图'
  isEdit.value = false
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

const openEdit = async (record: Carousel) => {
  if (!record.id) return
  modalTitle.value = '编辑轮播图'
  isEdit.value = true
  editingId.value = record.id

  try {
    const res = await CarouselService.getCarouselById(record.id)
    if (res.code === 200) {
      formModel.value = {
        title: res.data.title,
        imageUrl: res.data.imageUrl,
        linkUrl: res.data.linkUrl || '',
        sortOrder: res.data.sortOrder,
        status: res.data.status
      }
      imagePreview.value = res.data.imageUrl
      modalVisible.value = true
    } else {
      message.error(res.message || '获取轮播图详情失败')
    }
  } catch (e) {
    console.error('获取轮播图详情异常:', e)
    message.warning('获取轮播图详情失败，请稍后重试')
  }
}

const handleSubmit = async () => {
  try {
    confirmLoading.value = true

    // 如果正在上传中，等待上传完成
    if (imageUploading.value) {
      message.warning('图片正在上传中，请稍候')
      confirmLoading.value = false
      return
    }

    // 检查图片是否已上传
    if (!formModel.value.imageUrl) {
      message.error('请上传轮播图图片')
      confirmLoading.value = false
      return
    }

    // 验证标题
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
      const res = await CarouselService.createCarousel(formModel.value)
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
    console.error('提交表单异常:', e)
  } finally {
    confirmLoading.value = false
  }
}

const handleCancel = () => {
  modalVisible.value = false
  imagePreview.value = ''
}

// ============== 图片上传 ==============
const handleImageChange = async (info: any) => {
  const file = info.fileList?.[0]?.originFileObj || info.file?.originFileObj
  if (!file) {
    console.log('图片文件为空', info)
    return
  }

  console.log('准备上传图片', file.name, file.size, file.type)

  // 先显示本地预览
  imagePreview.value = URL.createObjectURL(file)

  // 上传到服务器
  try {
    imageUploading.value = true
    const result = await ImageUploadService.uploadImage(file)
    console.log('上传结果:', result)
    formModel.value.imageUrl = result.fileUrl
    console.log('设置的imageUrl:', formModel.value.imageUrl)
    message.success('图片上传成功')
  } catch (e: any) {
    message.error(e.message || '图片上传失败')
    imagePreview.value = formModel.value.imageUrl || ''
  } finally {
    imageUploading.value = false
  }
}

// ============== 列表查询 ==============
const loadCarousels = async () => {
  try {
    loading.value = true
    const params = {
      page: pagination.current,
      size: pagination.pageSize,
      ...searchParams.value
    }
    console.log('请求参数:', params)
    console.log('includeDeleted 值:', params.includeDeleted, '类型:', typeof params.includeDeleted)
    const res = await CarouselService.getCarouselList(params)

    if (res && res.code === 200) {
      console.log('返回数据:', res.data)
      dataSource.value = res.data?.records || []
      pagination.total = res.data?.total || 0
    } else {
      dataSource.value = []
      pagination.total = 0
      if (res?.message) {
        message.warning(res.message)
      }
    }
  } catch (e) {
    console.error('加载轮播图列表异常:', e)
    dataSource.value = []
    pagination.total = 0
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  pagination.current = 1
  loadCarousels()
}

const handleReset = () => {
  searchParams.value = { status: undefined, includeDeleted: false }
  pagination.current = 1
  loadCarousels()
}

// ============== 表格操作 ==============
const handleTableChange = (p: any) => {
  pagination.current = p.current
  pagination.pageSize = p.pageSize
  loadCarousels()
}

const onSelectChange = (keys: number[]) => {
  selectedRowKeys.value = keys
}

const handleDelete = async (id: number) => {
  try {
    const res = await CarouselService.deleteCarousel(id)
    if (res && res.code === 200) {
      message.success('删除成功')
      loadCarousels()
    } else {
      message.warning(res?.message || '删除失败')
    }
  } catch (e) {
    console.error('删除轮播图异常:', e)
    message.warning('删除失败，请稍后重试')
  }
}

const handleRestore = async (id: number) => {
  try {
    const res = await CarouselService.restoreCarousel(id)
    if (res && res.code === 200) {
      message.success('恢复成功')
      loadCarousels()
    } else {
      message.warning(res?.message || '恢复失败')
    }
  } catch (e) {
    console.error('恢复轮播图异常:', e)
    message.warning('恢复失败，请稍后重试')
  }
}

// 彻底删除轮播图
const handlePermanentDelete = async (id: number) => {
  try {
    const res = await CarouselService.permanentDeleteCarousel(id)
    if (res && res.code === 200) {
      message.success('彻底删除成功')
      loadCarousels()
    } else {
      message.warning(res?.message || '彻底删除失败')
    }
  } catch (e) {
    console.error('彻底删除轮播图异常:', e)
    message.warning('彻底删除失败，请稍后重试')
  }
}

// 批量彻底删除轮播图
const handleBatchPermanentDelete = async () => {
  if (!selectedRowKeys.value.length) {
    message.warning('请选择要彻底删除的轮播图')
    return
  }
  try {
    const res = await CarouselService.batchPermanentDeleteCarousels(selectedRowKeys.value)
    if (res && res.code === 200) {
      message.success('批量彻底删除成功')
      selectedRowKeys.value = []
      loadCarousels()
    } else {
      message.warning(res?.message || '批量彻底删除失败')
    }
  } catch (e) {
    console.error('批量彻底删除轮播图异常:', e)
    message.warning('批量彻底删除失败，请稍后重试')
  }
}

const handleBatchDelete = async () => {
  if (!selectedRowKeys.value.length) {
    message.warning('请选择要删除的轮播图')
    return
  }
  try {
    const res = await CarouselService.batchDeleteCarousels(selectedRowKeys.value)
    if (res && res.code === 200) {
      message.success('批量删除成功')
      selectedRowKeys.value = []
      loadCarousels()
    } else {
      message.warning(res?.message || '批量删除失败')
    }
  } catch (e) {
    console.error('批量删除轮播图异常:', e)
    message.warning('批量删除失败，请稍后重试')
  }
}

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
    console.error('更新轮播图状态异常:', e)
    message.warning('状态更新失败，请稍后重试')
  }
}

const handleSortChange = async (id: number, sortOrder: number) => {
  try {
    const res = await CarouselService.updateCarouselSort(id, sortOrder)
    if (res && res.code === 200) {
      message.success('排序更新成功')
      loadCarousels()
    } else {
      message.warning(res?.message || '排序更新失败')
    }
  } catch (e) {
    console.error('更新排序异常:', e)
    message.warning('排序更新失败，请稍后重试')
  }
}

onMounted(async () => {
  await loadCarousels()
})
</script>


<template>
  <div class="p-24">
    <!-- 搜索区域 -->
    <a-card :bordered="false" class="mb-16">
      <a-form layout="horizontal" :model="searchParams">
        <a-row :gutter="24">
          <a-col :span="6">
            <a-form-item label="状态" class="mb-0">
              <a-select v-model:value="searchParams.status" placeholder="请选择状态" allow-clear>
                <a-select-option v-for="opt in statusOptions" :key="opt.value" :value="opt.value">
                  {{ opt.label }}
                </a-select-option>
              </a-select>
            </a-form-item>
          </a-col>
          <a-col :span="6" class="text-right">
            <a-space>
              <a-tooltip :title="searchParams.includeDeleted ? '显示未删除的轮播图' : '显示已删除的轮播图'">
                <a-switch v-model:checked="searchParams.includeDeleted" @change="handleSearch" checked-children="已删" un-checked-children="正常" />
              </a-tooltip>
              <a-button type="primary" @click="handleSearch">搜索</a-button>
              <a-button @click="handleReset">重置</a-button>
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
          <template v-if="searchParams.includeDeleted">
            <a-button type="primary" danger :disabled="selectedRowKeys.length === 0" @click="handleBatchPermanentDelete">
              <DeleteOutlined /> 批量彻底删除
            </a-button>
          </template>
          <template v-else>
            <a-button type="primary" @click="openCreate">
              <PlusOutlined /> 新建轮播图
            </a-button>
            <a-button danger :disabled="selectedRowKeys.length === 0" @click="handleBatchDelete">
              <DeleteOutlined /> 批量删除
            </a-button>
          </template>
        </a-space>
      </template>
      <a-table
        :columns="columns"
        :data-source="dataSource"
        :loading="loading"
        :pagination="pagination"
        :row-selection="{
          selectedRowKeys,
          onChange: onSelectChange
        }"
        :scroll="{ x: 1100 }"
        @change="handleTableChange"
      >
        <!-- 标题列 -->
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'title'">
            <a-tooltip :title="record.title">
              <span class="title-text">{{ record.title }}</span>
            </a-tooltip>
          </template>

          <!-- 图片列 -->
          <template v-else-if="column.key === 'image'">
            <a-image
              v-if="record.imageUrl"
              :src="record.imageUrl"
              :width="80"
              :height="50"
              style="object-fit: cover; border-radius: 4px;"
            />
            <span v-else class="text-muted">-</span>
          </template>

          <!-- 跳转链接列 -->
          <template v-else-if="column.key === 'linkUrl'">
            <a-tooltip :title="record.linkUrl">
              <a :href="record.linkUrl" target="_blank" v-if="record.linkUrl" class="link-text">
                {{ record.linkUrl }}
              </a>
              <span v-else class="text-muted">-</span>
            </a-tooltip>
          </template>

          <!-- 删除状态列 -->
          <template v-else-if="column.key === 'deleteStatus'">
            <a-tag :color="(record.deletedAt !== null && record.deletedAt !== undefined) ? 'red' : 'green'">
              {{ (record.deletedAt !== null && record.deletedAt !== undefined) ? '已删除' : '正常' }}
            </a-tag>
          </template>

          <!-- 创建时间列 -->
          <template v-else-if="column.key === 'createdAt'">
            <div v-if="record.createdAt" class="time-cell">
              <div class="relative-time">{{ formatRelativeTime(record.createdAt) }}</div>
              <div class="absolute-time">{{ formatDateTime(record.createdAt) }}</div>
            </div>
            <span v-else>-</span>
          </template>

          <!-- 操作列 -->
          <template v-else-if="column.key === 'action'">
            <a-space>
              <!-- 已删除的记录：显示恢复和彻底删除 -->
              <template v-if="record.deletedAt !== null && record.deletedAt !== undefined">
                <a-button type="link" size="small" @click="handleRestore(record.id)">
                  恢复
                </a-button>
                <a-popconfirm
                  title="确定要彻底删除这个轮播图吗？此操作不可恢复！"
                  @confirm="handlePermanentDelete(record.id)"
                >
                  <a-button type="link" size="small" danger>
                    彻底删除
                  </a-button>
                </a-popconfirm>
              </template>
              <!-- 正常记录：显示编辑、状态切换、删除 -->
              <template v-else>
                <a-button type="link" size="small" @click="openEdit(record)">
                  编辑
                </a-button>

                <!-- 状态切换 -->
                <a-switch
                  :checked="record.status === 1"
                  checked-children="启用"
                  un-checked-children="禁用"
                  size="small"
                  @change="(checked: boolean) => handleStatusChange(record.id, checked ? 1 : 0)"
                />

                <a-popconfirm
                  title="确定要删除这个轮播图吗？"
                  @confirm="handleDelete(record.id)"
                >
                  <a-button type="link" size="small" danger>
                    删除
                  </a-button>
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
  font-weight: 500;
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
  font-size: 13px;
  color: var(--text-main);
  font-weight: 500;
}

.absolute-time {
  font-size: 12px;
  color: var(--text-tertiary);
  margin-top: 2px;
}

.upload-tips {
  margin-top: 8px;
  font-size: 12px;
  color: var(--text-tertiary);
}

.image-preview {
  width: 200px;
  height: 120px;
  border-radius: 8px;
  overflow: hidden;
  border: 1px dashed #d9d9d9;
}

.image-preview img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
</style>
