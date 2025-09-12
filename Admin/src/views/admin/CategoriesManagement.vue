<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import CategoriesService from '../../services/categories'
import type { Category, CategoryListParams } from '../../services/categories'
import { formatDateTime } from '../../utils/uitls'

// 响应式数据
const loading = ref(false)
const dataSource = ref<Category[]>([])
const total = ref(0)
const current = ref(1)
const pageSize = ref(10)
const selectedRowKeys = ref<number[]>([])

// 搜索参数
const searchParams = ref<CategoryListParams>({
  name: '',
  includeDeleted: false
})

const columns = [
  { title: '名称', dataIndex: 'name', key: 'name' },
  { title: '描述', dataIndex: 'description', key: 'description' },
  { title: '状态', key: 'status' },
  { title: '创建者', dataIndex: 'creatorUsername', key: 'creatorUsername' },
  { title: '创建时间', dataIndex: 'createdAt', key: 'createdAt' },
  { title: '操作', key: 'action' }
]

// 弹窗相关
const modalVisible = ref(false)
const modalTitle = ref('新建分类')
const isEdit = ref(false)
const editingId = ref<number | null>(null)
const confirmLoading = ref(false)

const formRef = ref()
const formModel = ref<Partial<Category>>({ name: '', description: '' })
const rules = {
  name: [{ required: true, message: '请输入分类名称' }]
}

const openCreate = () => {
  isEdit.value = false
  modalTitle.value = '新建分类'
  editingId.value = null
  formModel.value = { name: '', description: '' }
  modalVisible.value = true
}

const openEdit = (record: Category) => {
  isEdit.value = true
  modalTitle.value = '编辑分类'
  editingId.value = record.id || null
  formModel.value = { name: record.name, description: record.description }
  modalVisible.value = true
}

const handleOk = async () => {
  try {
    confirmLoading.value = true
    await formRef.value?.validate?.()
    if (isEdit.value) {
      const res = await CategoriesService.updateCategory(editingId.value as number, formModel.value as any)
      if (res.code === 200) {
        message.success('更新成功')
        modalVisible.value = false
        loadCategories()
      } else {
        message.error(res.message || '更新失败')
      }
    } else {
      const res = await CategoriesService.createCategory(formModel.value as any)
      if (res.code === 200) {
        message.success('创建成功')
        modalVisible.value = false
        current.value = 1
        loadCategories()
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
const loadCategories = async () => {
  try {
    loading.value = true
    const params = { page: current.value, size: pageSize.value, ...searchParams.value }
    const res = await CategoriesService.getCategoryList(params)
    if (res.code === 200) {
      dataSource.value = res.data.records
      total.value = res.data.total
    } else {
      message.error(res.message || '加载分类失败')
    }
  } catch (e) {
    message.error('加载分类失败')
  } finally {
    loading.value = false
  }
}

const handleSearch = () => { current.value = 1; loadCategories() }
const handleReset = () => { searchParams.value = { name: '', includeDeleted: false }; current.value = 1; loadCategories() }

// 恢复删除
const handleRestore = async (id: number) => {
  const res = await CategoriesService.restoreCategory(id)
  if (res.code === 200) { message.success('恢复成功'); loadCategories() } else { message.error(res.message || '恢复失败') }
}

// 彻底删除
const handlePermanentDelete = async (id: number) => {
  const res = await CategoriesService.permanentDeleteCategory(id)
  if (res.code === 200) { message.success('彻底删除成功'); loadCategories() } else { message.error(res.message || '彻底删除失败') }
}

// 批量彻底删除
const handleBatchPermanentDelete = async () => {
  if (!selectedRowKeys.value.length) { message.warning('请选择要彻底删除的分类'); return }
  const res = await CategoriesService.batchPermanentDeleteCategories(selectedRowKeys.value)
  if (res.code === 200) { message.success('批量彻底删除成功'); selectedRowKeys.value = []; loadCategories() } else { message.error(res.message || '批量彻底删除失败') }
}
const handleTableChange = (p: any) => { current.value = p.current; pageSize.value = p.pageSize; loadCategories() }
const onSelectChange = (keys: number[]) => { selectedRowKeys.value = keys }

const handleDelete = async (id: number) => {
  const res = await CategoriesService.deleteCategory(id)
  if (res.code === 200) { message.success('删除成功'); loadCategories() } else { message.error(res.message || '删除失败') }
}

const handleBatchDelete = async () => {
  if (!selectedRowKeys.value.length) { message.warning('请选择要删除的分类'); return }
  const res = await CategoriesService.batchDeleteCategories(selectedRowKeys.value)
  if (res.code === 200) { message.success('批量删除成功'); selectedRowKeys.value = []; loadCategories() } else { message.error(res.message || '批量删除失败') }
}

onMounted(() => { loadCategories() })
</script>

<template>
  <div class="categories-management">
    <div class="page-header"><h2>分类管理</h2></div>

    <a-card class="search-card" :bordered="false">
      <a-form layout="inline" :model="searchParams">
        <a-form-item label="名称">
          <a-input v-model:value="searchParams.name" placeholder="请输入分类名称" allow-clear style="width: 220px" />
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

    <a-card class="action-card" :bordered="false">
      <a-space>
        <a-button type="primary" @click="openCreate">新建分类</a-button>
        <a-button v-if="!searchParams.includeDeleted" danger :disabled="selectedRowKeys.length === 0" @click="handleBatchDelete">批量删除</a-button>
        <a-popconfirm 
          v-if="searchParams.includeDeleted"
          title="确定要批量彻底删除选中的分类吗？此操作不可恢复！" 
          ok-text="确定" 
          cancel-text="取消"
          @confirm="handleBatchPermanentDelete"
        >
          <a-button danger :disabled="selectedRowKeys.length === 0">批量彻底删除</a-button>
        </a-popconfirm>
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
          <template v-if="column.key === 'status'">
            <a-tag v-if="record.deletedAt" color="red">已删除</a-tag>
            <a-tag v-else color="green">正常</a-tag>
          </template>
          <template v-else-if="column.key === 'createdAt'">{{ formatDateTime(record.createdAt) }}</template>
          <template v-else-if="column.key === 'action'">
            <a-space>
              <template v-if="!record.deletedAt">
                <a-button type="link" size="small" @click="openEdit(record)">编辑</a-button>
                <a-popconfirm title="确定删除该分类吗？" @confirm="handleDelete(record.id)">
                  <a-button type="link" size="small" danger>删除</a-button>
                </a-popconfirm>
              </template>
              <template v-else>
                <a-popconfirm title="确定恢复该分类吗？" @confirm="handleRestore(record.id)">
                  <a-button type="link" size="small">恢复</a-button>
                </a-popconfirm>
                <a-popconfirm 
                  title="确定要彻底删除该分类吗？此操作不可恢复！" 
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
        <a-form-item name="name" label="分类名称" required>
          <a-input v-model:value="formModel.name" placeholder="请输入分类名称" />
        </a-form-item>
        <a-form-item name="description" label="描述">
          <a-input v-model:value="formModel.description" placeholder="请输入描述" />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<style scoped>
.categories-management { padding: 24px; }
.page-header { margin-bottom: 24px; }
.page-header h2 { margin: 0; font-size: 24px; font-weight: 600; color: #262626; }
.search-card, .action-card { margin-bottom: 16px; }
</style>