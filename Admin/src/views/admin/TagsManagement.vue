<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import { SearchOutlined, ReloadOutlined, PlusOutlined, DeleteOutlined } from '@ant-design/icons-vue'
import TagsService from '../../services/tags'
import type { Tag, TagListParams } from '../../services/tags'
import { formatDateTime } from '../../utils/utils'

// 响应式数据
const loading = ref(false)
const dataSource = ref<Tag[]>([])
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
const searchParams = ref<TagListParams>({
  name: '',
  includeDeleted: false
})

const columns = [
  { title: '名称', dataIndex: 'name', key: 'name' },
  { title: '描述', dataIndex: 'description', key: 'description' },
  { title: '状态', key: 'status' },
  { title: '创建者', dataIndex: 'creatorUsername', key: 'creatorUsername' },
  { title: '创建时间', dataIndex: 'createdAt', key: 'createdAt' },
  { title: '操作', key: 'action', width: 180, fixed: 'right' as const }
]

// 弹窗相关
const modalVisible = ref(false)
const modalTitle = ref('新建标签')
const isEdit = ref(false)
const editingId = ref<number | null>(null)
const confirmLoading = ref(false)

const formRef = ref()
const formModel = ref<Partial<Tag>>({ name: '', description: '' })
const rules = {
  name: [{ required: true, message: '请输入标签名称' }]
}

const openCreate = () => {
  isEdit.value = false
  modalTitle.value = '新建标签'
  editingId.value = null
  formModel.value = { name: '', description: '' }
  modalVisible.value = true
}

const openEdit = (record: Tag) => {
  isEdit.value = true
  modalTitle.value = '编辑标签'
  editingId.value = record.id || null
  formModel.value = { name: record.name, description: record.description }
  modalVisible.value = true
}

const handleOk = async () => {
  try {
    confirmLoading.value = true
    await formRef.value?.validate?.()
    if (isEdit.value) {
      const res = await TagsService.updateTag(editingId.value as number, formModel.value as any)
      if (res.code === 200) {
        message.success('更新成功')
        modalVisible.value = false
        loadTags()
      } else {
        message.error(res.message || '更新失败')
      }
    } else {
      const res = await TagsService.createTag(formModel.value as any)
      if (res.code === 200) {
        message.success('创建成功')
        modalVisible.value = false
        pagination.current = 1
        loadTags()
      } else {
        message.error(res.message || '创建失败')
      }
    }
  } catch (e) {
    // ignore
  } finally {
    confirmLoading.value = false
  }
}

const handleCancel = () => { modalVisible.value = false }

// 列表加载
const loadTags = async () => {
  try {
    loading.value = true
    const params = { page: pagination.current, size: pagination.pageSize, ...searchParams.value }
    const res = await TagsService.getTagList(params)
    if (res.code === 200) {
      dataSource.value = res.data.records
      pagination.total = res.data.total
    } else {
      message.error(res.message || '加载标签失败')
    }
  } catch (e) {
    message.error('加载标签失败')
  } finally {
    loading.value = false
  }
}

const handleSearch = () => { pagination.current = 1; loadTags() }
const handleReset = () => { searchParams.value = { name: '', includeDeleted: false }; pagination.current = 1; loadTags() }

// 恢复删除
const handleRestore = async (id: number) => {
  const res = await TagsService.restoreTag(id)
  if (res.code === 200) { message.success('恢复成功'); loadTags() } else { message.error(res.message || '恢复失败') }
}

// 彻底删除
const handlePermanentDelete = async (id: number) => {
  const res = await TagsService.permanentDeleteTag(id)
  if (res.code === 200) { message.success('彻底删除成功'); loadTags() } else { message.error(res.message || '彻底删除失败') }
}

// 批量彻底删除
const handleBatchPermanentDelete = async () => {
  if (!selectedRowKeys.value.length) { message.warning('请选择要彻底删除的标签'); return }
  const res = await TagsService.batchPermanentDeleteTags(selectedRowKeys.value)
  if (res.code === 200) { message.success('批量彻底删除成功'); selectedRowKeys.value = []; loadTags() } else { message.error(res.message || '批量彻底删除失败') }
}
const handleTableChange = (p: any) => {
  pagination.current = p.current
  pagination.pageSize = p.pageSize
  loadTags()
}
const onSelectChange = (keys: number[]) => { selectedRowKeys.value = keys }

const handleDelete = async (id: number) => {
  try {
    const res = await TagsService.deleteTag(id)
    if (res.code === 200) {
      message.success('删除成功')
      loadTags()
    } else {
      // 针对外键约束错误给出更友好的提示
      if (res.message && res.message.includes('foreign key constraint')) {
        message.error('无法删除该标签，因为还有文章正在使用此标签')
      } else {
        message.error(res.message || '删除失败')
      }
    }
  } catch (error) {
    message.error('删除操作失败，请稍后重试')
  }
}

const handleBatchDelete = async () => {
  if (!selectedRowKeys.value.length) { message.warning('请选择要删除的标签'); return }
  try {
    const res = await TagsService.batchDeleteTags(selectedRowKeys.value)
    if (res.code === 200) {
      message.success('批量删除成功')
      selectedRowKeys.value = []
      loadTags()
    } else {
      // 针对外键约束错误给出更友好的提示
      if (res.message && res.message.includes('foreign key constraint')) {
        message.error('无法删除选中的标签，因为还有文章正在使用这些标签')
      } else {
        message.error(res.message || '批量删除失败')
      }
    }
  } catch (error) {
    message.error('批量删除操作失败，请稍后重试')
  }
}

onMounted(() => { loadTags() })
</script>

<template>
  <div class="p-24">
    <a-card :bordered="false" class="mb-16">
      <a-form layout="horizontal" :model="searchParams">
        <a-row :gutter="24">
          <a-col :span="6">
            <a-form-item label="名称" class="mb-0">
              <a-input v-model:value="searchParams.name" placeholder="请输入标签名称" allow-clear @press-enter="handleSearch" />
            </a-form-item>
          </a-col>
          <a-col :span="18" class="text-right">
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
        <span>标签列表</span>
      </template>
      <template #extra>
        <a-space>
          <a-button type="primary" @click="openCreate">
            <template #icon><PlusOutlined /></template>
            新建标签
          </a-button>
          <a-button v-if="!searchParams.includeDeleted" danger :disabled="selectedRowKeys.length === 0" @click="handleBatchDelete">
            <template #icon><DeleteOutlined /></template>
            批量删除
          </a-button>
          <a-popconfirm
            v-if="searchParams.includeDeleted"
            title="确定要批量彻底删除选中的标签吗？此操作不可恢复！"
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
        :scroll="{ x: 800 }"
        @change="handleTableChange"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'status'">
            <a-tag v-if="record.deletedAt" color="red">已删除</a-tag>
            <a-tag v-else color="green">正常</a-tag>
          </template>
          <template v-else-if="column.key === 'createdAt'">{{ formatDateTime(record.createdAt) }}</template>
          <template v-else-if="column.key === 'action'">
            <a-space>
              <template v-if="!record.deletedAt">
                <a-button type="link" size="small" @click="openEdit(record)">编辑</a-button>
                <a-popconfirm title="确定删除该标签吗？" @confirm="handleDelete(record.id)">
                  <a-button type="link" size="small" danger>删除</a-button>
                </a-popconfirm>
              </template>
              <template v-else>
                <a-popconfirm title="确定恢复该标签吗？" @confirm="handleRestore(record.id)">
                  <a-button type="link" size="small">恢复</a-button>
                </a-popconfirm>
                <a-popconfirm 
                  title="确定要彻底删除该标签吗？此操作不可恢复！" 
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

    <a-modal v-model:open="modalVisible" :title="modalTitle" :confirm-loading="confirmLoading" @ok="handleOk" @cancel="handleCancel" destroy-on-close>
      <a-form :model="formModel" :rules="rules" ref="formRef" layout="vertical">
        <a-form-item name="name" label="标签名称" required>
          <a-input v-model:value="formModel.name" placeholder="请输入标签名称" />
        </a-form-item>
        <a-form-item name="description" label="描述">
          <a-input v-model:value="formModel.description" placeholder="请输入描述" />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<style scoped>
</style>