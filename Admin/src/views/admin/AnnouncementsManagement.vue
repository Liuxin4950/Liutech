<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import { PlusOutlined, DeleteOutlined, NotificationOutlined, DownOutlined } from '@ant-design/icons-vue'
import AnnouncementsService from '../../services/announcements'
import type { AnnouncementListParams, Announcement, AnnouncementListItem } from '../../services/announcements'
import { formatDateTime } from '../../utils/uitls'

// 响应式数据
const loading = ref(false)
const dataSource = ref<AnnouncementListItem[]>([])
const total = ref(0)
const current = ref(1)
const pageSize = ref(10)
const selectedRowKeys = ref<number[]>([])

// 搜索参数
const searchParams = ref<AnnouncementListParams>({
  status: undefined,
  type: undefined,
  includeDeleted: false
})

// 表格列定义
const columns = [
  { title: '标题', dataIndex: 'title', key: 'title', width: 200 },
  { title: '类型', dataIndex: 'typeName', key: 'typeName', width: 80 },
  { title: '优先级', dataIndex: 'priorityName', key: 'priorityName', width: 80 },
  { title: '状态', dataIndex: 'statusName', key: 'statusName', width: 80 },
  { title: '置顶', key: 'isTop', width: 60 },
  { title: '有效性', key: 'isValid', width: 80 },
  { title: '删除状态', key: 'deleteStatus', width: 80 },
  { title: '浏览量', dataIndex: 'viewCount', key: 'viewCount', width: 80 },
  { title: '创建时间', dataIndex: 'createdAt', key: 'createdAt', width: 150 },
  { title: '操作', key: 'action', width: 200, fixed: 'right' }
]

// 下拉选项
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

// ============== 新建/编辑 弹窗 ==============
const modalVisible = ref(false)
const modalTitle = ref('新建公告')
const isEdit = ref(false)
const editingId = ref<number | null>(null)
const confirmLoading = ref(false)

const formRef = ref()
const formModel = ref<Partial<Announcement>>({
  title: '',
  content: '',
  type: 1,
  priority: 2,
  status: 0,
  isTop: 0,
  startTime: undefined,
  endTime: undefined
})

const rules = {
  title: [{ required: true, message: '请输入公告标题' }],
  content: [{ required: true, message: '请输入公告内容' }],
  type: [{ required: true, message: '请选择公告类型' }],
  priority: [{ required: true, message: '请选择优先级' }],
  status: [{ required: true, message: '请选择状态' }]
}

// ============== 弹窗操作 ==============
const openCreate = () => {
  modalTitle.value = '新建公告'
  isEdit.value = false
  editingId.value = null
  formModel.value = {
    title: '',
    content: '',
    type: 1,
    priority: 2,
    status: 0,
    isTop: 0,
    startTime: undefined,
    endTime: undefined
  }
  modalVisible.value = true
}

const openEdit = async (record: AnnouncementListItem) => {
  modalTitle.value = '编辑公告'
  isEdit.value = true
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
        startTime: res.data.startTime,
        endTime: res.data.endTime
      }
      modalVisible.value = true
    } else {
      message.error(res.message || '获取公告详情失败')
    }
  } catch (e) {
    console.error('获取公告详情异常:', e)
    message.warning('获取公告详情失败，请稍后重试')
  }
}

const handleSubmit = async () => {
  try {
    confirmLoading.value = true
    await formRef.value?.validate?.()
    
    if (isEdit.value) {
      const res = await AnnouncementsService.updateAnnouncement(editingId.value as number, formModel.value)
      if (res.code === 200) {
        message.success('更新成功')
        modalVisible.value = false
        loadAnnouncements()
      } else {
        message.error(res.message || '更新失败')
      }
    } else {
      const res = await AnnouncementsService.createAnnouncement(formModel.value as any)
      if (res.code === 200) {
        message.success('创建成功')
        modalVisible.value = false
        current.value = 1
        loadAnnouncements()
      } else {
        message.error(res.message || '创建失败')
      }
    }
  } catch (e) {
    console.error('提交表单异常:', e)
    // 表单校验失败或请求错误，不显示错误信息避免重复提示
  } finally {
    confirmLoading.value = false
  }
}

const handleCancel = () => {
  modalVisible.value = false
}

// ============== 列表查询 ==============
const loadAnnouncements = async () => {
  try {
    loading.value = true
    const params = {
      page: current.value,
      size: pageSize.value,
      ...searchParams.value
    }
    const res = await AnnouncementsService.getAnnouncementList(params)
    console.log(res);

    if (res && res.code === 200) {
      // 确保数据结构正确，避免null错误
      dataSource.value = res.data?.records || []
      total.value = res.data?.total || 0
    } else {
      // 请求失败时，清空数据并显示空状态
      dataSource.value = []
      total.value = 0
      if (res?.message) {
        message.warning(res.message)
      }
    }
  } catch (e) {
    console.error('加载公告列表异常:', e)
    // 异常时清空数据，显示空状态，不抛出全局错误
    dataSource.value = []
    total.value = 0
    // 只在开发环境显示详细错误信息
    if (import.meta.env.DEV) {
      message.warning('加载公告列表失败，请检查网络连接')
    }
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  current.value = 1
  loadAnnouncements()
}

const handleReset = () => {
  searchParams.value = { status: undefined, type: undefined, includeDeleted: false }
  current.value = 1
  loadAnnouncements()
}

// ============== 表格操作 ==============
const handleTableChange = (pagination: any) => {
  current.value = pagination.current
  pageSize.value = pagination.pageSize
  loadAnnouncements()
}

const onSelectChange = (keys: number[]) => {
  selectedRowKeys.value = keys
}

const handleDelete = async (id: number) => {
  try {
    const res = await AnnouncementsService.deleteAnnouncement(id)
    if (res && res.code === 200) {
      message.success('删除成功')
      loadAnnouncements()
    } else {
      message.warning(res?.message || '删除失败')
    }
  } catch (e) {
    console.error('删除公告异常:', e)
    message.warning('删除失败，请稍后重试')
  }
}

const handleRestore = async (id: number) => {
  try {
    const res = await AnnouncementsService.restoreAnnouncement(id)
    if (res && res.code === 200) {
      message.success('恢复成功')
      loadAnnouncements()
    } else {
      message.warning(res?.message || '恢复失败')
    }
  } catch (e) {
    console.error('恢复公告异常:', e)
    message.warning('恢复失败，请稍后重试')
  }
}

const handleBatchDelete = async () => {
  if (!selectedRowKeys.value.length) {
    message.warning('请选择要删除的公告')
    return
  }
  try {
    const res = await AnnouncementsService.batchDeleteAnnouncements(selectedRowKeys.value)
    if (res && res.code === 200) {
      message.success('批量删除成功')
      selectedRowKeys.value = []
      loadAnnouncements()
    } else {
      message.warning(res?.message || '批量删除失败')
    }
  } catch (e) {
    console.error('批量删除公告异常:', e)
    message.warning('批量删除失败，请稍后重试')
  }
}

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
    console.error('更新公告状态异常:', e)
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
    console.error('切换置顶状态异常:', e)
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
    console.error('批量更新状态异常:', e)
    message.warning('批量状态更新失败，请稍后重试')
  }
}

onMounted(async () => {
  await loadAnnouncements()
})
</script>


<template>
  <div class="announcements-management">
    <div class="page-header">
      <h2><NotificationOutlined /> 公告管理</h2>
    </div>

    <!-- 搜索区域 -->
    <a-card class="search-card" :bordered="false">
      <a-form layout="inline" :model="searchParams">
        <a-form-item label="类型">
          <a-select v-model:value="searchParams.type" placeholder="请选择类型" allow-clear style="width: 120px">
            <a-select-option v-for="opt in typeOptions" :key="opt.value" :value="opt.value">{{ opt.label }}</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="状态">
          <a-select v-model:value="searchParams.status" placeholder="请选择状态" allow-clear style="width: 120px">
            <a-select-option v-for="opt in statusOptions" :key="opt.value" :value="opt.value">{{ opt.label }}</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="显示已删除">
          <a-switch v-model:checked="searchParams.includeDeleted" @change="handleSearch" />
        </a-form-item>
        <a-form-item>
          <a-space>
            <a-button type="primary" @click="handleSearch">搜索</a-button>
            <a-button @click="handleReset">重置</a-button>
          </a-space>
        </a-form-item>
      </a-form>
    </a-card>

    <!-- 操作区域 -->
    <a-card class="action-card" :bordered="false">
      <a-space>
        <a-button type="primary" @click="openCreate">
          <PlusOutlined /> 新建公告
        </a-button>
        <a-button danger :disabled="selectedRowKeys.length === 0" @click="handleBatchDelete">
          <DeleteOutlined /> 批量删除
        </a-button>
        <a-dropdown>
          <template #overlay>
            <a-menu>
              <a-menu-item key="publish" @click="handleBatchStatusUpdate(1)">批量发布</a-menu-item>
              <a-menu-item key="draft" @click="handleBatchStatusUpdate(0)">批量设为草稿</a-menu-item>
              <a-menu-item key="offline" @click="handleBatchStatusUpdate(2)">批量下线</a-menu-item>
            </a-menu>
          </template>
          <a-button :disabled="selectedRowKeys.length === 0">
            批量操作 <DownOutlined />
          </a-button>
        </a-dropdown>
      </a-space>
    </a-card>

    <!-- 表格区域 -->
    <a-card :bordered="false">
      <a-table
        :columns="columns"
        :data-source="dataSource"
        :loading="loading"
        :pagination="{
          current,
          pageSize,
          total,
          showSizeChanger: true,
          showQuickJumper: true,
          showTotal: (total: number) => `共 ${total} 条记录`
        }"
        :row-selection="{
          selectedRowKeys,
          onChange: onSelectChange
        }"
        :scroll="{ x: 1200 }"
        @change="handleTableChange"
      >
        <!-- 标题列 -->
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'title'">
            <div class="title-cell">
              <a-tooltip :title="record.content">
                <span class="title-text">{{ record.title }}</span>
              </a-tooltip>
            </div>
          </template>

          <!-- 置顶列 -->
          <template v-else-if="column.key === 'isTop'">
            <a-tag :color="(record.isTop !== null && record.isTop !== undefined && record.isTop) ? 'red' : 'default'">
              {{ (record.isTop !== null && record.isTop !== undefined && record.isTop) ? '置顶' : '普通' }}
            </a-tag>
          </template>

          <!-- 有效性列 -->
          <template v-else-if="column.key === 'isValid'">
            <a-tag :color="(record.isValid !== null && record.isValid !== undefined && record.isValid) ? 'green' : 'orange'">
              {{ (record.isValid !== null && record.isValid !== undefined && record.isValid) ? '有效' : '无效' }}
            </a-tag>
          </template>

          <!-- 删除状态列 -->
          <template v-else-if="column.key === 'deleteStatus'">
            <a-tag :color="(record.deletedAt !== null && record.deletedAt !== undefined) ? 'red' : 'green'">
              {{ (record.deletedAt !== null && record.deletedAt !== undefined) ? '已删除' : '正常' }}
            </a-tag>
          </template>

          <!-- 创建时间列 -->
          <template v-else-if="column.key === 'createdAt'">
            {{ record.createdAt ? formatDateTime(record.createdAt) : '-' }}
          </template>

          <!-- 操作列 -->
          <template v-else-if="column.key === 'action'">
            <a-space>
              <a-button type="link" size="small" @click="openEdit(record)">
                编辑
              </a-button>
              
              <!-- 状态操作 -->
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

              <!-- 置顶操作 -->
              <a-button 
                type="link" 
                size="small" 
                @click="handleToggleTop(record.id, (record.isTop !== null && record.isTop !== undefined && record.isTop) ? 0 : 1)"
              >
                {{ (record.isTop !== null && record.isTop !== undefined && record.isTop) ? '取消置顶' : '置顶' }}
              </a-button>

              <!-- 删除/恢复操作 -->
              <template v-if="record.deletedAt !== null && record.deletedAt !== undefined">
                <a-button type="link" size="small" @click="handleRestore(record.id)">
                  恢复
                </a-button>
              </template>
              <template v-else>
                <a-popconfirm
                  title="确定要删除这个公告吗？"
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
      :width="800"
      @ok="handleSubmit"
      @cancel="handleCancel"
    >
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
              <a-textarea 
                v-model:value="formModel.content" 
                placeholder="请输入公告内容" 
                :rows="6"
              />
            </a-form-item>
          </a-col>
        </a-row>
      </a-form>
    </a-modal>
  </div>
</template>


<style scoped>
.announcements-management {
  padding: 24px;
}

.page-header {
  margin-bottom: 24px;
}

.page-header h2 {
  margin: 0;
  font-size: 24px;
  font-weight: 600;
  color: #262626;
}

.search-card,
.action-card {
  margin-bottom: 16px;
}

.title-cell {
  max-width: 200px;
}

.title-text {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  cursor: pointer;
}

.title-text:hover {
  color: #1890ff;
}
</style>