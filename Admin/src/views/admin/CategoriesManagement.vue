<script setup lang="ts">
import { SearchOutlined, ReloadOutlined, PlusOutlined, DeleteOutlined } from '@ant-design/icons-vue'
import { useTablePage, useCrudActions, useModalForm } from '@/composables'
import CategoriesService from '../../services/categories'
import type { Category, CategoryListParams } from '../../services/categories'
import { formatDateTime } from '../../utils/utils'

// 表格页面：加载、分页、搜索、选择
const {
  loading, dataSource, selectedRowKeys, searchParams, pagination,
  load, handleSearch, handleReset, handleTableChange, onSelectChange, clearSelection
} = useTablePage<Category, CategoryListParams>({
  loadFn: (params) => CategoriesService.getCategoryList(params),
  defaultSearchParams: { name: '', includeDeleted: false },
  loadErrorMessage: '加载分类失败'
})

// CRUD 操作：删除、恢复、彻底删除、批量操作
const {
  handleDelete, handleBatchDelete, handleRestore,
  handlePermanentDelete, handleBatchPermanentDelete
} = useCrudActions({
  deleteFn: (id) => CategoriesService.deleteCategory(id),
  batchDeleteFn: (ids) => CategoriesService.batchDeleteCategories(id),
  restoreFn: (id) => CategoriesService.restoreCategory(id),
  permanentDeleteFn: (id) => CategoriesService.permanentDeleteCategory(id),
  batchPermanentDeleteFn: (ids) => CategoriesService.batchPermanentDeleteCategories(id),
  onRefresh: load,
  clearSelection,
  entityName: '分类'
})

// 弹窗表单：新建/编辑
const {
  modalVisible, modalTitle, confirmLoading,
  formRef, formModel, openCreate, openEdit, handleOk, handleCancel
} = useModalForm<Category>({
  createFn: (data) => CategoriesService.createCategory(data) as any,
  updateFn: (id, data) => CategoriesService.updateCategory(id, data) as any,
  defaultForm: () => ({ name: '', description: '' }),
  onCreateSuccess: () => { pagination.current = 1; load() },
  onUpdateSuccess: load,
  entityName: '分类'
})

// 表单校验规则
const rules = {
  name: [{ required: true, message: '请输入分类名称' }]
}

// 表格列定义
const columns = [
  { title: '名称', dataIndex: 'name', key: 'name' },
  { title: '描述', dataIndex: 'description', key: 'description' },
  { title: '状态', key: 'status' },
  { title: '创建者', dataIndex: 'creatorUsername', key: 'creatorUsername' },
  { title: '创建时间', dataIndex: 'createdAt', key: 'createdAt' },
  { title: '操作', key: 'action', width: 180, fixed: 'right' as const }
]
</script>

<template>
  <div class="p-24">
    <!-- 搜索卡片 -->
    <a-card :bordered="false" class="mb-16">
      <a-form layout="horizontal" :model="searchParams">
        <a-row :gutter="24">
          <a-col :span="6">
            <a-form-item label="名称" class="mb-0">
              <a-input v-model:value="searchParams.name" placeholder="请输入分类名称" allow-clear @press-enter="handleSearch" />
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

    <!-- 表格卡片 -->
    <a-card :bordered="false">
      <template #title><span>分类列表</span></template>
      <template #extra>
        <a-space>
          <a-button type="primary" @click="openCreate">
            <template #icon><PlusOutlined /></template>新建分类
          </a-button>
          <a-button v-if="!searchParams.includeDeleted" danger :disabled="selectedRowKeys.length === 0" @click="handleBatchDelete(selectedRowKeys)">
            <template #icon><DeleteOutlined /></template>批量删除
          </a-button>
          <a-popconfirm
            v-if="searchParams.includeDeleted"
            title="确定要批量彻底删除选中的分类吗？此操作不可恢复！"
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

    <!-- 新建/编辑弹窗 -->
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
