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
  name: ''
})

// 表格列定义
const columns = [
  {
    title: '分类名称',
    dataIndex: 'name',
    key: 'name'
  },
  {
    title: '描述',
    dataIndex: 'description',
    key: 'description',
    ellipsis: true
  },
  {
    title: '创建时间',
    dataIndex: 'createdAt',
    key: 'createdAt'
  },
  {
    title: '更新时间',
    dataIndex: 'updatedAt',
    key: 'updatedAt'
  },
  {
    title: '操作',
    key: 'action'
  }
]

// 加载分类列表
const loadCategories = async () => {
  try {
    loading.value = true
    const params = {
      page: current.value,
      size: pageSize.value,
      ...searchParams.value
    }
    const response = await CategoriesService.getCategoryList(params)
    if (response.code === 200) {
      dataSource.value = response.data.records
      total.value = response.data.total
    } else {
      message.error(response.message || '加载分类列表失败')
    }
  } catch (error) {
    message.error('加载分类列表失败')
    console.error('加载分类列表失败:', error)
  } finally {
    loading.value = false
  }
}

// 搜索
const handleSearch = () => {
  current.value = 1
  loadCategories()
}

// 重置搜索
const handleReset = () => {
  searchParams.value = {
    name: ''
  }
  current.value = 1
  loadCategories()
}

// 分页变化
const handleTableChange = (pagination: any) => {
  current.value = pagination.current
  pageSize.value = pagination.pageSize
  loadCategories()
}

// 选择变化
const onSelectChange = (newSelectedRowKeys: number[]) => {
  selectedRowKeys.value = newSelectedRowKeys
}

// 删除分类
const handleDelete = async (id: number) => {
  try {
    const response = await CategoriesService.deleteCategory(id)
    if (response.code === 200) {
      message.success('删除成功')
      loadCategories()
    } else {
      message.error(response.message || '删除失败')
    }
  } catch (error) {
    message.error('删除失败')
    console.error('删除失败:', error)
  }
}

// 批量删除
const handleBatchDelete = async () => {
  if (selectedRowKeys.value.length === 0) {
    message.warning('请选择要删除的分类')
    return
  }
  
  try {
    const response = await CategoriesService.batchDeleteCategories(selectedRowKeys.value)
    if (response.code === 200) {
      message.success('批量删除成功')
      selectedRowKeys.value = []
      loadCategories()
    } else {
      message.error(response.message || '批量删除失败')
    }
  } catch (error) {
    message.error('批量删除失败')
    console.error('批量删除失败:', error)
  }
}

// 组件挂载时加载数据
onMounted(() => {
  loadCategories()
})
</script>

<template>
  <div class="categories-management">
    <!-- 页面标题 -->
    <div class="page-header">
      <h2>分类管理</h2>
    </div>

    <!-- 搜索区域 -->
    <a-card class="search-card" :bordered="false">
      <a-form layout="inline" :model="searchParams">
        <a-form-item label="分类名称">
          <a-input 
            v-model:value="searchParams.name" 
            placeholder="请输入分类名称" 
            allow-clear
            style="width: 200px"
          />
        </a-form-item>
        <a-form-item>
          <a-space>
            <a-button type="primary" @click="handleSearch">
              搜索
            </a-button>
            <a-button @click="handleReset">
              重置
            </a-button>
          </a-space>
        </a-form-item>
      </a-form>
    </a-card>

    <!-- 操作区域 -->
    <a-card class="action-card" :bordered="false">
      <a-space>
        <a-button type="primary">
          新建分类
        </a-button>
        <a-button 
          danger 
          :disabled="selectedRowKeys.length === 0"
          @click="handleBatchDelete"
        >
          批量删除
        </a-button>
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
        @change="handleTableChange"
        row-key="id"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'createdAt'">
            {{ formatDateTime(record.createdAt) }}
          </template>
          <template v-else-if="column.key === 'updatedAt'">
            {{ formatDateTime(record.updatedAt) }}
          </template>
          <template v-else-if="column.key === 'action'">
            <a-space>
              <a-button type="link" size="small">
                编辑
              </a-button>
              <a-popconfirm
                title="确定要删除这个分类吗？"
                @confirm="handleDelete(record.id)"
              >
                <a-button type="link" size="small" danger>
                  删除
                </a-button>
              </a-popconfirm>
            </a-space>
          </template>
        </template>
      </a-table>
    </a-card>
  </div>
</template>

<style scoped>
.categories-management {
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
</style>