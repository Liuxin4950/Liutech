<script setup lang="ts">
import { ref } from 'vue'
import { message } from 'ant-design-vue'
import { PlusOutlined, DeleteOutlined, NotificationOutlined, DownOutlined, EyeOutlined, UploadOutlined, DownloadOutlined, SearchOutlined, ReloadOutlined, CloudUploadOutlined } from '@ant-design/icons-vue'
import DOMPurify from 'dompurify'
import dayjs, { Dayjs } from 'dayjs'
import AnnouncementsService from '../../services/announcements'
import type { AnnouncementListParams, Announcement, AnnouncementListItem } from '../../services/announcements'
import { formatDateTime, formatRelativeTime } from '../../utils/utils'
import TinyMCEEditor from '@/components/TinyMCEEditor.vue'
import { useTablePage, useCrudActions, useModalForm } from '@/composables'
import { useTableColumnPrefs } from '@/composables/useTableColumnPrefs'
import TableColumnSettings from '@/components/TableColumnSettings.vue'
import { useTableExport } from '@/composables/useTableExport'
import TableExportButton from '@/components/TableExportButton.vue'

// ============== 表格页面 ==============
const {
  loading, dataSource, selectedRowKeys, searchParams, pagination,
  load: loadAnnouncements, handleSearch, handleReset, handleTableChange, onSelectChange, clearSelection
} = useTablePage<AnnouncementListItem, AnnouncementListParams & { keyword?: string }>({
  loadFn: (params) => {
    const { page, ...rest } = params
    return AnnouncementsService.getAnnouncementList({ current: page, ...rest })
  },
  defaultSearchParams: {
    status: undefined,
    type: undefined,
    includeDeleted: false,
    keyword: ''
  },
  loadErrorMessage: '加载公告列表失败'
})

// ============== CRUD 操作 ==============
const {
  handleDelete, handleBatchDelete, handleRestore,
  handlePermanentDelete, handleBatchPermanentDelete
} = useCrudActions({
  deleteFn: (id) => AnnouncementsService.deleteAnnouncement(id),
  batchDeleteFn: (ids) => AnnouncementsService.batchDeleteAnnouncements(ids),
  restoreFn: (id) => AnnouncementsService.restoreAnnouncement(id),
  permanentDeleteFn: (id) => AnnouncementsService.permanentDeleteAnnouncement(id),
  batchPermanentDeleteFn: (ids) => AnnouncementsService.batchPermanentDeleteAnnouncements(ids),
  onRefresh: loadAnnouncements,
  clearSelection,
  entityName: '公告'
})

// ============== 弹窗表单 ==============
const {
  modalVisible, modalTitle, isEdit, editingId, confirmLoading,
  formRef, formModel, draftSavedAt, openCreate, handleCancel
} = useModalForm<Announcement>({
  createFn: (data) => AnnouncementsService.createAnnouncement(data as any),
  updateFn: (id, data) => AnnouncementsService.updateAnnouncement(id, data as unknown as Partial<Announcement>),
  defaultForm: () => ({
    title: '',
    content: '',
    type: 1,
    priority: 2,
    status: 0,
    isTop: 0,
    startTime: undefined,
    endTime: undefined
  }),
  // 草稿自动保存：TinyMCE 编辑防丢失
  draft: {
    key: 'announcements',
    isDirty: (f) => !!(f.title || f.content),
  },
  onCreateSuccess: () => { pagination.current = 1; loadAnnouncements() },
  onUpdateSuccess: loadAnnouncements,
  entityName: '公告'
})

// 自定义 openEdit（获取详情并转换 dayjs 字段）
const openEdit = async (record: AnnouncementListItem) => {
  isEdit.value = true
  modalTitle.value = '编辑公告'
  editingId.value = record.id

  try {
    const res = await AnnouncementsService.getAnnouncementById(record.id)
    if (res.code === 200) {
      formModel.value = {
        title: res.data.title,
        content: res.data.content,
        type: res.data.type,
        priority: res.data.priority,
        status: res.data.status,
        isTop: res.data.isTop,
        startTime: res.data.startTime ? dayjs(res.data.startTime) : undefined,
        endTime: res.data.endTime ? dayjs(res.data.endTime) : undefined
      }
      modalVisible.value = true
    } else {
      message.error(res.message || '获取公告详情失败')
    }
  } catch (e) {
    message.warning('获取公告详情失败，请稍后重试')
  }
}

// 自定义提交（处理时间格式）
const handleSubmit = async () => {
  try {
    confirmLoading.value = true
    await formRef.value?.validate?.()

    const formatTime = (time: string | Dayjs | undefined): string | undefined => {
      if (!time) return undefined
      return typeof time === 'string' ? time : time.format('YYYY-MM-DD HH:mm:ss')
    }

    const submitData: Partial<Announcement> = {
      ...formModel.value,
      startTime: formatTime(formModel.value.startTime),
      endTime: formatTime(formModel.value.endTime)
    }

    if (isEdit.value) {
      const res = await AnnouncementsService.updateAnnouncement(editingId.value as number, submitData)
      if (res.code === 200) {
        message.success('更新成功')
        modalVisible.value = false
        loadAnnouncements()
      } else {
        message.error(res.message || '更新失败')
      }
    } else {
      const res = await AnnouncementsService.createAnnouncement(submitData as any)
      if (res.code === 200) {
        message.success('创建成功')
        modalVisible.value = false
        pagination.current = 1
        loadAnnouncements()
      } else {
        message.error(res.message || '创建失败')
      }
    }
  } catch (e) {
    // 表单校验失败或请求错误
  } finally {
    confirmLoading.value = false
  }
}

// ============== 表单校验规则 ==============
const rules = {
  title: [{ required: true, message: '请输入公告标题' }],
  content: [{ required: true, message: '请输入公告内容' }],
  type: [{ required: true, message: '请选择公告类型' }],
  priority: [{ required: true, message: '请选择优先级' }],
  status: [{ required: true, message: '请选择状态' }]
}

// ============== 预览功能 ==============
const previewVisible = ref(false)
const previewData = ref<AnnouncementListItem | null>(null)

const sanitizeAnnouncementContent = (content?: string) => {
  return DOMPurify.sanitize(content || '')
}

const openPreview = (record: AnnouncementListItem) => {
  previewData.value = { ...record }
  previewVisible.value = true
}

const closePreview = () => {
  previewVisible.value = false
  previewData.value = null
}

// ============== 导入导出功能 ==============
const uploadVisible = ref(false)
const uploadLoading = ref(false)
const fileList = ref<any[]>([])
const exportImportEnabled = true

const handleExport = async () => {
  try {
    message.loading('正在导出数据...', 0)
    const blob = await AnnouncementsService.exportAnnouncements(searchParams.value)

    const url = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `公告数据_${new Date().toISOString().slice(0, 10)}.xlsx`
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    window.URL.revokeObjectURL(url)

    message.destroy()
    message.success('导出成功')
  } catch (e) {
    message.destroy()
    message.error('导出失败，请稍后重试')
  }
}

const handleUpload = async (file: any) => {
  if (!file) return

  try {
    uploadLoading.value = true
    const res = await AnnouncementsService.importAnnouncements(file.file)

    if (res.code === 200) {
      const { success, failed, errors } = res.data
      if (errors && errors.length > 0) {
        message.warning(`导入完成：成功 ${success} 条，失败 ${failed} 条。错误详情：${errors.join(', ')}`)
      } else {
        message.success(`导入成功：共 ${success} 条数据`)
      }

      uploadVisible.value = false
      fileList.value = []
      loadAnnouncements()
    } else {
      message.error(res.message || '导入失败')
    }
  } catch (e) {
    message.error('导入失败，请稍后重试')
  } finally {
    uploadLoading.value = false
  }
}

const beforeUpload = (file: File) => {
  const isExcel = file.type === 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' ||
                 file.type === 'application/vnd.ms-excel'
  if (!isExcel) {
    message.error('只能上传Excel文件!')
    return false
  }

  const isLt10M = file.size / 1024 / 1024 < 10
  if (!isLt10M) {
    message.error('文件大小不能超过10MB!')
    return false
  }

  return false
}

// ============== 批量状态/置顶操作 ==============
const handleStatusChange = async (id: number, status: number) => {
  try {
    const res = await AnnouncementsService.updateAnnouncementStatus(id, status)
    if (res && res.code === 200) {
      message.success('状态更新成功')
      loadAnnouncements()
    } else {
      message.warning(res?.message || '状态更新失败')
    }
  } catch (e) {
    message.warning('状态更新失败，请稍后重试')
  }
}

const handleToggleTop = async (id: number, isTop: number) => {
  try {
    const res = await AnnouncementsService.toggleAnnouncementTop(id, isTop)
    if (res && res.code === 200) {
      message.success(isTop ? '置顶成功' : '取消置顶成功')
      loadAnnouncements()
    } else {
      message.warning(res?.message || '操作失败')
    }
  } catch (e) {
    message.warning('操作失败，请稍后重试')
  }
}

const handleBatchStatusUpdate = async (status: number) => {
  if (!selectedRowKeys.value.length) {
    message.warning('请选择要更新状态的公告')
    return
  }
  try {
    const res = await AnnouncementsService.batchUpdateAnnouncementStatus(selectedRowKeys.value, status)
    if (res && res.code === 200) {
      message.success('批量状态更新成功')
      selectedRowKeys.value = []
      loadAnnouncements()
    } else {
      message.warning(res?.message || '批量状态更新失败')
    }
  } catch (e) {
    message.warning('批量状态更新失败，请稍后重试')
  }
}

const handleBatchTopUpdate = async (isTop: number) => {
  if (!selectedRowKeys.value.length) {
    message.warning('请选择要操作的公告')
    return
  }
  try {
    const res = await AnnouncementsService.batchToggleAnnouncementTop(selectedRowKeys.value, isTop)
    if (res && res.code === 200) {
      message.success(isTop ? '批量置顶成功' : '批量取消置顶成功')
      selectedRowKeys.value = []
      loadAnnouncements()
    } else {
      message.warning(res?.message || '批量操作失败')
    }
  } catch (e) {
    message.warning('批量操作失败，请稍后重试')
  }
}

// ============== 辅助函数 ==============
const typeOptions = [
  { label: '系统', value: 1 },
  { label: '活动', value: 2 },
  { label: '维护', value: 3 },
  { label: '其他', value: 4 }
]

const priorityOptions = [
  { label: '低', value: 1 },
  { label: '中', value: 2 },
  { label: '高', value: 3 },
  { label: '紧急', value: 4 }
]

const statusOptions = [
  { label: '草稿', value: 0 },
  { label: '发布', value: 1 },
  { label: '下线', value: 2 }
]

const columns = [
  { title: '标题', dataIndex: 'title', key: 'title', width: 250 },
  { title: '类型', dataIndex: 'typeName', key: 'typeName', width: 80 },
  { title: '优先级', dataIndex: 'priorityName', key: 'priorityName', width: 80 },
  { title: '状态', dataIndex: 'statusName', key: 'statusName', width: 80 },
  { title: '置顶', key: 'isTop', width: 60 },
  { title: '有效性', key: 'isValid', width: 80 },
  { title: '删除状态', key: 'deleteStatus', width: 80 },
  { title: '浏览量', dataIndex: 'viewCount', key: 'viewCount', width: 80 },
  { title: '创建时间', dataIndex: 'createdAt', key: 'createdAt', width: 180 },
  { title: '操作', key: 'action', width: 320, fixed: 'right' }
]

const columnPrefsCtrl = useTableColumnPrefs('announcements', columns, { alwaysVisible: ["action"] })
const prefColumns = columnPrefsCtrl.prefColumns

const exportCtrl = useTableExport({
  columns: prefColumns,
  rows: dataSource,
  filename: 'announcements',
})

/** 草稿时间格式化 */
function formatDraftTime(ts: number): string {
  const diff = Math.max(0, Date.now() - ts)
  const s = Math.floor(diff / 1000)
  if (s < 5) return '刚刚'
  if (s < 60) return `${s} 秒前`
  const m = Math.floor(s / 60)
  if (m < 60) return `${m} 分钟前`
  return `${Math.floor(m / 60)} 小时前`
}

const getTypeName = (type: number) => {
  const typeMap: Record<number, string> = { 1: '系统', 2: '活动', 3: '维护', 4: '其他' }
  return typeMap[type] || '未知'
}

const getPriorityName = (priority: number) => {
  const priorityMap: Record<number, string> = { 1: '低', 2: '中', 3: '高', 4: '紧急' }
  return priorityMap[priority] || '未知'
}

const getStatusName = (status: number) => {
  const statusMap: Record<number, string> = { 0: '草稿', 1: '发布', 2: '下线' }
  return statusMap[status] || '未知'
}

const getTypeColor = (type: number) => {
  const colorMap: Record<number, string> = { 1: 'blue', 2: 'green', 3: 'orange', 4: 'default' }
  return colorMap[type] || 'default'
}

const getPriorityColor = (priority: number) => {
  const colorMap: Record<number, string> = { 1: 'default', 2: 'blue', 3: 'orange', 4: 'red' }
  return colorMap[priority] || 'default'
}

const stripHtml = (html: string) => {
  if (!html) return ''
  return html.replace(/<[^>]*>/g, '').substring(0, 100) + (html.length > 100 ? '...' : '')
}
</script>

<template>
  <div class="p-24">
    <!-- 搜索区域 -->
    <a-card :bordered="false" class="mb-16">
      <a-form layout="horizontal" :model="searchParams">
        <a-row :gutter="[16, 12]" align="bottom">
          <a-col :xs="24" :sm="12" :lg="8" :xl="6">
            <a-form-item label="关键词" class="mb-0">
              <a-input
                v-model:value="searchParams.keyword"
                placeholder="搜索标题或内容"
                @press-enter="handleSearch"
                allow-clear
              />
            </a-form-item>
          </a-col>
          <a-col :xs="24" :sm="12" :lg="8" :xl="6">
            <a-form-item label="类型" class="mb-0">
              <a-select v-model:value="searchParams.type" placeholder="请选择类型" allow-clear>
                <a-select-option v-for="opt in typeOptions" :key="opt.value" :value="opt.value">{{ opt.label }}</a-select-option>
              </a-select>
            </a-form-item>
          </a-col>
          <a-col :xs="24" :sm="12" :lg="8" :xl="6">
            <a-form-item label="状态" class="mb-0">
              <a-select v-model:value="searchParams.status" placeholder="请选择状态" allow-clear>
                <a-select-option v-for="opt in statusOptions" :key="opt.value" :value="opt.value">{{ opt.label }}</a-select-option>
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
        <span><NotificationOutlined /> 公告列表</span>
      </template>
      <template #extra>
         <a-space>
            <TableExportButton :ctrl="exportCtrl" />
            <TableColumnSettings :ctrl="columnPrefsCtrl" />
            <a-button type="primary" @click="openCreate">
              <PlusOutlined /> 新建公告
            </a-button>
            <a-tooltip title="导出全部公告（服务端 Excel，含所有分页）">
              <a-button :disabled="!exportImportEnabled" @click="handleExport">
                <DownloadOutlined /> 导出全部
              </a-button>
            </a-tooltip>
            <a-button :disabled="!exportImportEnabled" @click="uploadVisible = true">
              <UploadOutlined /> 导入Excel
            </a-button>
            <a-button danger :disabled="selectedRowKeys.length === 0" @click="handleBatchDelete(selectedRowKeys)">
              <DeleteOutlined /> 批量删除
            </a-button>
            <a-popconfirm
              v-if="searchParams.includeDeleted"
              title="确定要批量彻底删除选中的公告吗？此操作不可恢复！"
              ok-text="确定"
              cancel-text="取消"
              @confirm="handleBatchPermanentDelete(selectedRowKeys)"
            >
              <a-button danger :disabled="selectedRowKeys.length === 0">
                <DeleteOutlined /> 批量彻底删除
              </a-button>
            </a-popconfirm>
            <a-dropdown>
              <template #overlay>
                <a-menu>
                  <a-menu-item key="publish" @click="handleBatchStatusUpdate(1)">批量发布</a-menu-item>
                  <a-menu-item key="draft" @click="handleBatchStatusUpdate(0)">批量设为草稿</a-menu-item>
                  <a-menu-item key="offline" @click="handleBatchStatusUpdate(2)">批量下线</a-menu-item>
                  <a-menu-divider />
                  <a-menu-item key="setTop" @click="handleBatchTopUpdate(1)">批量置顶</a-menu-item>
                  <a-menu-item key="cancelTop" @click="handleBatchTopUpdate(0)">批量取消置顶</a-menu-item>
                </a-menu>
              </template>
              <a-button :disabled="selectedRowKeys.length === 0">
                批量操作 <DownOutlined />
              </a-button>
            </a-dropdown>
         </a-space>
      </template>
      <a-table
        :columns="prefColumns"
        :data-source="dataSource"
        :loading="loading"
        :pagination="pagination"
        :row-selection="{
          selectedRowKeys,
          onChange: onSelectChange
        }"
        :scroll="{ x: 1200 }"
        row-key="id"
        @change="handleTableChange"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'title'">
            <div class="title-cell">
              <div class="title-main">
                <a-tooltip :title="record.title">
                  <span class="title-text">{{ record.title }}</span>
                </a-tooltip>
                <div class="title-badges">
                  <a-tag v-if="record.isTop" size="small" color="red">置顶</a-tag>
                  <a-tag size="small" :color="getTypeColor(record.type)">{{ record.typeName }}</a-tag>
                </div>
              </div>
              <div class="title-summary">
                <a-tooltip :title="stripHtml(record.content)">
                  <span class="summary-text">{{ stripHtml(record.content) }}</span>
                </a-tooltip>
              </div>
            </div>
          </template>

          <template v-else-if="column.key === 'isTop'">
            <a-tag :color="(record.isTop !== null && record.isTop !== undefined && record.isTop) ? 'red' : 'default'">
              {{ (record.isTop !== null && record.isTop !== undefined && record.isTop) ? '置顶' : '普通' }}
            </a-tag>
          </template>

          <template v-else-if="column.key === 'isValid'">
            <a-tag :color="(record.isValid !== null && record.isValid !== undefined && record.isValid) ? 'green' : 'orange'">
              {{ (record.isValid !== null && record.isValid !== undefined && record.isValid) ? '有效' : '无效' }}
            </a-tag>
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
              <a-button type="link" size="small" @click="openPreview(record)">
                <EyeOutlined /> 预览
              </a-button>
              <a-button type="link" size="small" @click="openEdit(record)">
                编辑
              </a-button>

              <a-dropdown>
                <template #overlay>
                  <a-menu>
                    <a-menu-item key="publish" @click="handleStatusChange(record.id, 1)" :disabled="record.status === 1">
                      发布
                    </a-menu-item>
                    <a-menu-item key="draft" @click="handleStatusChange(record.id, 0)" :disabled="record.status === 0">
                      设为草稿
                    </a-menu-item>
                    <a-menu-item key="offline" @click="handleStatusChange(record.id, 2)" :disabled="record.status === 2">
                      下线
                    </a-menu-item>
                  </a-menu>
                </template>
                <a-button type="link" size="small">
                  状态 <DownOutlined />
                </a-button>
              </a-dropdown>

              <a-button
                type="link"
                size="small"
                @click="handleToggleTop(record.id, (record.isTop !== null && record.isTop !== undefined && record.isTop) ? 0 : 1)"
              >
                {{ (record.isTop !== null && record.isTop !== undefined && record.isTop) ? '取消置顶' : '置顶' }}
              </a-button>

              <template v-if="record.deletedAt !== null && record.deletedAt !== undefined">
                <a-popconfirm title="确定恢复该公告吗？" @confirm="handleRestore(record.id)">
                  <a-button type="link" size="small">恢复</a-button>
                </a-popconfirm>
                <a-popconfirm
                  title="确定要彻底删除该公告吗？此操作不可恢复！"
                  ok-text="确定"
                  cancel-text="取消"
                  @confirm="handlePermanentDelete(record.id)"
                >
                  <a-button type="link" size="small" danger>
                    彻底删除
                  </a-button>
                </a-popconfirm>
              </template>
              <template v-else>
                <a-popconfirm
                  title="确定删除该公告吗？"
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
      :confirm-loading="confirmLoading"
      :width="800"
      destroy-on-close
      @ok="handleSubmit"
      @cancel="handleCancel"
    >
      <template #title>
        <div class="modal-title-with-draft">
          <span>{{ modalTitle }}</span>
          <span v-if="draftSavedAt" class="draft-hint">
            <CloudUploadOutlined /> 草稿已保存 {{ formatDraftTime(draftSavedAt) }}
          </span>
        </div>
      </template>
      <a-form
        ref="formRef"
        :model="formModel"
        :rules="rules"
        layout="vertical"
      >
        <a-row :gutter="16">
          <a-col :span="24">
            <a-form-item label="公告标题" name="title">
              <a-input v-model:value="formModel.title" placeholder="请输入公告标题" />
            </a-form-item>
          </a-col>
        </a-row>

        <a-row :gutter="16">
          <a-col :span="8">
            <a-form-item label="公告类型" name="type">
              <a-select v-model:value="formModel.type" placeholder="请选择类型">
                <a-select-option v-for="opt in typeOptions" :key="opt.value" :value="opt.value">
                  {{ opt.label }}
                </a-select-option>
              </a-select>
            </a-form-item>
          </a-col>
          <a-col :span="8">
            <a-form-item label="优先级" name="priority">
              <a-select v-model:value="formModel.priority" placeholder="请选择优先级">
                <a-select-option v-for="opt in priorityOptions" :key="opt.value" :value="opt.value">
                  {{ opt.label }}
                </a-select-option>
              </a-select>
            </a-form-item>
          </a-col>
          <a-col :span="8">
            <a-form-item label="状态" name="status">
              <a-select v-model:value="formModel.status" placeholder="请选择状态">
                <a-select-option v-for="opt in statusOptions" :key="opt.value" :value="opt.value">
                  {{ opt.label }}
                </a-select-option>
              </a-select>
            </a-form-item>
          </a-col>
        </a-row>

        <a-row :gutter="16">
          <a-col :span="12">
            <a-form-item label="开始时间">
              <a-date-picker
                v-model:value="formModel.startTime"
                show-time
                placeholder="请选择开始时间"
                style="width: 100%"
              />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="结束时间">
              <a-date-picker
                v-model:value="formModel.endTime"
                show-time
                placeholder="请选择结束时间"
                style="width: 100%"
              />
            </a-form-item>
          </a-col>
        </a-row>

        <a-row :gutter="16">
          <a-col :span="24">
            <a-form-item label="是否置顶">
              <a-switch v-model:checked="formModel.isTop" :checked-value="1" :un-checked-value="0" />
            </a-form-item>
          </a-col>
        </a-row>

        <a-row :gutter="16">
          <a-col :span="24">
            <a-form-item label="公告内容" name="content">
              <TinyMCEEditor
                v-model="formModel.content"
                :height="300"
                placeholder="请输入公告内容..."
              />
            </a-form-item>
          </a-col>
        </a-row>
      </a-form>
    </a-modal>

    <!-- 预览弹窗 -->
    <a-modal
      v-model:open="previewVisible"
      title="公告预览"
      :width="800"
      :footer="null"
      destroy-on-close
      @cancel="closePreview"
    >
      <div v-if="previewData" class="announcement-preview">
        <div class="preview-header">
          <div class="preview-title">{{ previewData.title }}</div>
          <div class="preview-meta">
            <a-space>
              <a-tag :color="getTypeColor(previewData.type)">{{ getTypeName(previewData.type) }}</a-tag>
              <a-tag :color="getPriorityColor(previewData.priority)">{{ getPriorityName(previewData.priority) }}</a-tag>
              <a-tag v-if="previewData.isTop" color="red">置顶</a-tag>
              <span class="preview-time">{{ formatDateTime(previewData.createdAt) }}</span>
            </a-space>
          </div>
        </div>

        <div class="preview-content" v-html="sanitizeAnnouncementContent(previewData.content)"></div>

        <div class="preview-footer">
          <a-space split="|">
            <span>浏览量: {{ previewData.viewCount || 0 }}</span>
            <span>状态: {{ getStatusName(previewData.status) }}</span>
            <span v-if="previewData.startTime">开始时间: {{ formatDateTime(previewData.startTime) }}</span>
            <span v-if="previewData.endTime">结束时间: {{ formatDateTime(previewData.endTime) }}</span>
          </a-space>
        </div>
      </div>
    </a-modal>

    <!-- 导入弹窗 -->
    <a-modal
      v-model:open="uploadVisible"
      title="导入公告数据"
      :confirm-loading="uploadLoading"
      destroy-on-close
      @ok="() => fileList[0] && handleUpload(fileList[0])"
      @cancel="() => { uploadVisible = false; fileList = [] }"
    >
      <a-upload
        v-model:file-list="fileList"
        :before-upload="beforeUpload"
        :max-count="1"
        accept=".xlsx,.xls"
        :custom-request="() => {}"
      >
        <a-button>
          <UploadOutlined />
          选择Excel文件
        </a-button>
      </a-upload>

      <div class="upload-tips">
        <p>• 支持格式：.xlsx、.xls</p>
        <p>• 文件大小：不超过10MB</p>
        <p>• 导入字段：标题、内容、类型、优先级、状态、置顶、开始时间、结束时间</p>
      </div>
    </a-modal>
  </div>
</template>


<style scoped>
.title-cell {
  max-width: 250px;
}

.title-main {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--lt-space-sm);
}

.title-text {
  flex: 1;
  font-weight: var(--lt-font-weight-medium);
  color: var(--text-main);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  cursor: pointer;
  margin-right: var(--lt-space-sm);
}

.title-text:hover {
  color: var(--color-primary);
}

.title-badges {
  display: flex;
  gap: var(--lt-space-xs);
  flex-shrink: 0;
}

.title-summary {
  margin-top: var(--lt-space-xs);
}

.summary-text {
  font-size: var(--lt-font-size-xs);
  color: var(--text-tertiary);
  line-height: var(--lt-line-height-snug);
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  cursor: pointer;
}

.summary-text:hover {
  color: var(--color-primary);
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
  margin-top: var(--lt-space-2xs);
}

/* 预览弹窗样式 */
.announcement-preview .preview-header {
  border-bottom: 1px solid var(--border-light);
  padding-bottom: var(--lt-space-lg);
  margin-bottom: 20px;
}

.announcement-preview .preview-header .preview-title {
  font-size: var(--lt-font-size-xl);
  font-weight: var(--lt-font-weight-semibold);
  color: var(--text-main);
  margin-bottom: var(--lt-space-md);
  line-height: var(--lt-line-height-snug);
}

.announcement-preview .preview-header .preview-meta .preview-time {
  color: var(--text-tertiary);
  font-size: var(--lt-font-size-base);
}

.announcement-preview .preview-content {
  min-height: 200px;
  line-height: var(--lt-line-height-relaxed);
  color: var(--text-main);
  font-size: var(--lt-font-size-base);
  margin-bottom: 20px;
}

.announcement-preview .preview-content :deep(h1) { font-size: var(--lt-font-size-2xl); font-weight: var(--lt-font-weight-semibold); margin: 20px 0 var(--lt-space-lg) 0; }
.announcement-preview .preview-content :deep(h2) { font-size: var(--lt-font-size-xl); font-weight: var(--lt-font-weight-semibold); margin: var(--lt-space-lg) 0 var(--lt-space-md) 0; }
.announcement-preview .preview-content :deep(h3) { font-size: var(--lt-font-size-lg); font-weight: var(--lt-font-weight-semibold); margin: 14px 0 10px 0; }
.announcement-preview .preview-content :deep(p) { margin: var(--lt-space-sm) 0; line-height: var(--lt-line-height-relaxed); }
.announcement-preview .preview-content :deep(ul), .announcement-preview .preview-content :deep(ol) { margin: var(--lt-space-sm) 0; padding-left: 20px; }
.announcement-preview .preview-content :deep(li) { margin: var(--lt-space-xs) 0; }
.announcement-preview .preview-content :deep(blockquote) {
  border-left: 4px solid var(--color-primary);
  margin: var(--lt-space-lg) 0;
  padding: var(--lt-space-sm) var(--lt-space-lg);
  background: var(--color-primary-bg);
  color: var(--text-main);
}
.announcement-preview .preview-content :deep(code) {
  background: var(--bg-hover);
  padding: var(--lt-space-2xs) 6px;
  border-radius: var(--lt-radius-sm);
  font-family: var(--lt-font-family-mono);
  color: var(--color-error);
}
.announcement-preview .preview-content :deep(pre) {
  background: var(--bg-hover);
  border: 1px solid var(--border-light);
  border-radius: var(--lt-radius-sm);
  padding: var(--lt-space-md);
  overflow-x: auto;
  margin: var(--lt-space-lg) 0;
}
.announcement-preview .preview-content :deep(img) {
  max-width: 100%;
  height: auto;
  border-radius: var(--lt-radius-lg);
  margin: var(--lt-space-sm) 0;
}
.announcement-preview .preview-content :deep(table) {
  width: 100%;
  border-collapse: collapse;
  margin: var(--lt-space-lg) 0;
}
.announcement-preview .preview-content :deep(th), .announcement-preview .preview-content :deep(td) {
  border: 1px solid var(--border-light);
  padding: var(--lt-space-sm) var(--lt-space-md);
  text-align: left;
}
.announcement-preview .preview-content :deep(th) {
  background: var(--bg-hover);
  font-weight: var(--lt-font-weight-semibold);
}

.announcement-preview .preview-footer {
  border-top: 1px solid var(--border-light);
  padding-top: var(--lt-space-lg);
  color: var(--text-tertiary);
  font-size: var(--lt-font-size-base);
}

/* 导入弹窗样式 */
.upload-tips {
  margin-top: var(--lt-space-lg);
  padding: var(--lt-space-md);
  background: var(--color-primary-bg);
  border: 1px solid var(--color-primary-hover);
  border-radius: var(--lt-radius-md);
}

.upload-tips p {
  margin: var(--lt-space-xs) 0;
  font-size: var(--lt-font-size-sm);
  color: var(--text-secondary);
}

.upload-tips p:first-child {
  margin-top: 0;
}

.upload-tips p:last-child {
  margin-bottom: 0;
}

/* 弹窗标题栏内的草稿状态提示 */
.modal-title-with-draft {
  display: flex;
  align-items: center;
  gap: var(--lt-space-md);
}
.draft-hint {
  font-size: var(--lt-font-size-xs);
  color: var(--lt-color-success);
  font-weight: var(--lt-font-weight-regular);
  display: inline-flex;
  align-items: center;
  gap: var(--lt-space-xs);
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


