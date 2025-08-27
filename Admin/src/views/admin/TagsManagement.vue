<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import TagsService from '../../services/tags'
import type { Tag, TagListParams } from '../../services/tags'
import { formatDateTime } from '../../utils/uitls'

// 响应式数据
const loading = ref(false)
const dataSource = ref<Tag[]>([])
const total = ref(0)
const current = ref(1)
const pageSize = ref(10)
const selectedRowKeys = ref<number[]>([])

// 搜索参数
const searchParams = ref<TagListParams>({
  name: ''
})

const columns = [
  { title: '名称', dataIndex: 'name', key: 'name' },
  { title: '描述', dataIndex: 'description', key: 'description' },
  { title: '创建时间', dataIndex: 'createdAt', key: 'createdAt' },
  { title: '操作', key: 'action' }
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
        current.value = 1
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
    const params = { page: current.value, size: pageSize.value, ...searchParams.value }
    const res = await TagsService.getTagList(params)
    if (res.code === 200) {
      dataSource.value = res.data.records
      total.value = res.data.total
    } else {
      message.error(res.message || '加载标签失败')
    }
  } catch (e) {
    message.error('加载标签失败')
  } finally {
    loading.value = false
  }
}

const handleSearch = () => { current.value = 1; loadTags() }
const handleReset = () => { searchParams.value = { name: '' }; current.value = 1; loadTags() }
const handleTableChange = (p: any) => { current.value = p.current; pageSize.value = p.pageSize; loadTags() }
const onSelectChange = (keys: number[]) => { selectedRowKeys.value = keys }

const handleDelete = async (id: number) => {
  const res = await TagsService.deleteTag(id)
  if (res.code === 200) { message.success('删除成功'); loadTags() } else { message.error(res.message || '删除失败') }
}

const handleBatchDelete = async () => {
  if (!selectedRowKeys.value.length) { message.warning('请选择要删除的标签'); return }
  const res = await TagsService.batchDeleteTags(selectedRowKeys.value)
  if (res.code === 200) { message.success('批量删除成功'); selectedRowKeys.value = []; loadTags() } else { message.error(res.message || '批量删除失败') }
}

onMounted(() => { loadTags() })
</script>

<template>
  <div class="tags-management">
    <div class="page-header"><h2>标签管理</h2></div>

    <a-card class="search-card" :bordered="false">
      <a-form layout="inline" :model="searchParams">
        <a-form-item label="名称">
          <a-input v-model:value="searchParams.name" placeholder="请输入标签名称" allow-clear style="width: 220px" />
        </a-form-item>
        <a-form-item>
          <a-space>
            <a-button type="primary" @click="handleSearch">搜索</a-button>
            <a-button @click="handleReset">重置</a-button>
          </a-space>
        </a-form-item>
      </a-form>
    </a-card>

    <a-card class="action-card" :bordered="false">
      <a-space>
        <a-button type="primary" @click="openCreate">新建标签</a-button>
        <a-button danger :disabled="selectedRowKeys.length === 0" @click="handleBatchDelete">批量删除</a-button>
      </a-space>
    </a-card>

    <a-card :bordered="false">
      <a-table
        :columns="columns"
        :data-source="dataSource"
        :loading="loading"
        :pagination="{ current, pageSize, total, showSizeChanger: true, showQuickJumper: true, showTotal: (t:number)=>`共 ${t} 条记录` }"
        :row-selection="{ selectedRowKeys, onChange: onSelectChange }"
        row-key="id"
        @change="handleTableChange"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'createdAt'">{{ formatDateTime(record.createdAt) }}</template>
          <template v-else-if="column.key === 'action'">
            <a-space>
              <a-button type="link" size="small" @click="openEdit(record)">编辑</a-button>
              <a-popconfirm title="确定删除该标签吗？" @confirm="handleDelete(record.id)">
                <a-button type="link" size="small" danger>删除</a-button>
              </a-popconfirm>
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
.tags-management { padding: 24px; }
.page-header { margin-bottom: 24px; }
.page-header h2 { margin: 0; font-size: 24px; font-weight: 600; color: #262626; }
.search-card, .action-card { margin-bottom: 16px; }
</style>