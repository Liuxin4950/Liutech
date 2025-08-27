<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import { DownOutlined } from '@ant-design/icons-vue'
import PostsService from '../../services/posts'
import type { Post, PostListItem, PostListParams } from '../../services/posts'
import { formatDateTime } from '../../utils/uitls'

// 响应式数据
const loading = ref(false)
const dataSource = ref<PostListItem[]>([])
const total = ref(0)
const current = ref(1)
const pageSize = ref(10)
const selectedRowKeys = ref<number[]>([])

// 搜索参数
const searchParams = ref<PostListParams>({
  title: '',
  status: '',
  categoryId: undefined
})

// 表格列定义
const columns = [
  {
    title: '标题',
    dataIndex: 'title',
    key: 'title',
    ellipsis: true
  },
  {
    title: '分类',
    dataIndex: 'categoryName',
    key: 'categoryName'
  },
  {
    title: '作者',
    dataIndex: 'authorName',
    key: 'authorName'
  },
  {
    title: '状态',
    dataIndex: 'status',
    key: 'status'
  },
  {
    title: '创建时间',
    dataIndex: 'createdAt',
    key: 'createdAt'
  },
  {
    title: '操作',
    key: 'action'
  }
]

// 加载文章列表
const loadPosts = async () => {
  try {
    loading.value = true
    const params = {
      page: current.value,
      size: pageSize.value,
      ...searchParams.value
    }
    const response = await PostsService.getPostList(params)
    if (response.code === 200) {
      // 后端返回的数据中包含嵌套的 category 与 author 对象
      // 这里在前端做一次扁平化映射，补充 categoryName 与 authorName 以适配表格列定义
      const records = (response.data.records as any[]).map((r: any) => ({
        ...r,
        categoryName: r?.category?.name ?? r?.categoryName ?? '',
        authorName: r?.author?.username ?? r?.authorName ?? ''
      }))
      dataSource.value = records as PostListItem[]
      total.value = response.data.total
    } else {
      message.error(response.message || '加载文章列表失败')
    }
  } catch (error) {
    message.error('加载文章列表失败')
    console.error('加载文章列表失败:', error)
  } finally {
    loading.value = false
  }
}

// 搜索
const handleSearch = () => {
  current.value = 1
  loadPosts()
}

// 重置搜索
const handleReset = () => {
  searchParams.value = {
    title: '',
    status: '',
    categoryId: undefined
  }
  current.value = 1
  loadPosts()
}

// 分页变化
const handleTableChange = (pagination: any) => {
  current.value = pagination.current
  pageSize.value = pagination.pageSize
  loadPosts()
}

// 选择变化
const onSelectChange = (newSelectedRowKeys: number[]) => {
  selectedRowKeys.value = newSelectedRowKeys
}

// 删除文章
const handleDelete = async (id: number) => {
  try {
    const response = await PostsService.deletePost(id)
    if (response.code === 200) {
      message.success('删除成功')
      loadPosts()
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
    message.warning('请选择要删除的文章')
    return
  }
  
  try {
    const response = await PostsService.batchDeletePosts(selectedRowKeys.value)
    if (response.code === 200) {
      message.success('批量删除成功')
      selectedRowKeys.value = []
      loadPosts()
    } else {
      message.error(response.message || '批量删除失败')
    }
  } catch (error) {
    message.error('批量删除失败')
    console.error('批量删除失败:', error)
  }
}

// 更新状态
const handleStatusChange = async (id: number, status: string) => {
  try {
    const response = await PostsService.updatePostStatus(id, status)
    if (response.code === 200) {
      message.success('状态更新成功')
      loadPosts()
    } else {
      message.error(response.message || '状态更新失败')
    }
  } catch (error) {
    message.error('状态更新失败')
    console.error('状态更新失败:', error)
  }
}

// 组件挂载时加载数据
onMounted(() => {
  loadPosts()
})
</script>

<template>
  <div class="posts-management">
    <!-- 页面标题 -->
    <div class="page-header">
      <h2>文章管理</h2>
    </div>

    <!-- 搜索区域 -->
    <a-card class="search-card" :bordered="false">
      <a-form layout="inline" :model="searchParams">
        <a-form-item label="标题">
          <a-input 
            v-model:value="searchParams.title" 
            placeholder="请输入文章标题" 
            allow-clear
            style="width: 200px"
          />
        </a-form-item>
        <a-form-item label="状态">
          <a-select 
            v-model:value="searchParams.status" 
            placeholder="请选择状态" 
            allow-clear
            style="width: 120px"
          >
            <a-select-option value="published">已发布</a-select-option>
            <a-select-option value="draft">草稿</a-select-option>
            <a-select-option value="archived">已归档</a-select-option>
          </a-select>
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
          新建文章
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
          <template v-if="column.key === 'status'">
            <a-tag :color="record.status === 'published' ? 'green' : record.status === 'draft' ? 'orange' : 'red'">
              {{ record.status === 'published' ? '已发布' : record.status === 'draft' ? '草稿' : '已归档' }}
            </a-tag>
          </template>
          <template v-else-if="column.key === 'createdAt'">
            {{ formatDateTime(record.createdAt) }}
          </template>
          <template v-else-if="column.key === 'action'">
            <a-space>
              <a-button type="link" size="small">
                编辑
              </a-button>
              <a-dropdown>
                <template #overlay>
                  <a-menu>
                    <a-menu-item key="publish" @click="handleStatusChange(record.id, 'published')">
                      发布
                    </a-menu-item>
                    <a-menu-item key="draft" @click="handleStatusChange(record.id, 'draft')">
                      转为草稿
                    </a-menu-item>
                    <a-menu-item key="archive" @click="handleStatusChange(record.id, 'archived')">
                      归档
                    </a-menu-item>
                  </a-menu>
                </template>
                <a-button type="link" size="small">
                  更多 <DownOutlined />
                </a-button>
              </a-dropdown>
              <a-popconfirm
                title="确定要删除这篇文章吗？"
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
.posts-management {
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