<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import PostsService from '../../services/posts'
import CategoriesService from '../../services/categories'
import TagsService from '../../services/tags'
import type { PostListParams, Post, PostListItem } from '../../services/posts'
import { formatDateTime } from '../../utils/uitls'
import TinyMCEEditor from '../../components/TinyMCEEditor.vue'

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
  categoryId: undefined,
  status: undefined
})

// 表格列定义
const columns = [
  { title: '标题', dataIndex: 'title', key: 'title' },
  { title: '分类', dataIndex: 'category', key: 'category' },
  { title: '作者', dataIndex: 'author', key: 'author' },
  { title: '状态', dataIndex: 'status', key: 'status' },
  { title: '创建时间', dataIndex: 'createdAt', key: 'createdAt' },
  { title: '操作', key: 'action' }
]

// 下拉选项
const categoryOptions = ref<{ label: string; value: number }[]>([])
const tagOptions = ref<{ label: string; value: number }[]>([])
const statusOptions = [
  { label: '草稿', value: 'draft' },
  { label: '已发布', value: 'published' }
]

// ============== 新建/编辑 弹窗 ==============
const modalVisible = ref(false)
const modalTitle = ref('新建文章')
const isEdit = ref(false)
const editingId = ref<number | null>(null)
const confirmLoading = ref(false)

const formRef = ref()
const formModel = ref<Partial<Post>>({
  title: '',
  content: '',
  categoryId: undefined,
  tagIds: [],
  status: 0
})

const rules = {
  title: [{ required: true, message: '请输入标题' }],
  content: [{ required: true, message: '请输入内容' }],
  categoryId: [{ required: true, message: '请选择分类' }]
}

const openCreate = () => {
  isEdit.value = false
  modalTitle.value = '新建文章'
  editingId.value = null
  formModel.value = {
    title: '',
    content: '',
    categoryId: undefined,
    tagIds: [],
    status: 0
  }
  modalVisible.value = true
}

const openEdit = async (record: PostListItem) => {
  try {
    isEdit.value = true
    modalTitle.value = '编辑文章'
    editingId.value = record.id || null
    
    // 获取完整的文章详情（包含content字段）
    const res = await PostsService.getPostById(record.id)
    if (res.code === 200) {
      const postDetail = res.data
      formModel.value = {
        title: postDetail.title,
        content: postDetail.content || '',
        categoryId: record.category?.id,
        tagIds: record.tags?.map(tag => tag.id) || [],
        status: record.status === 'published' ? 1 : 0
      }
      modalVisible.value = true
    } else {
      message.error(res.message || '获取文章详情失败')
    }
  } catch (e) {
    message.error('获取文章详情失败')
  }
}

const handleOk = async () => {
  try {
    confirmLoading.value = true
    await formRef.value?.validate?.()
    if (isEdit.value) {
      const res = await PostsService.updatePost(editingId.value as number, formModel.value as any)
      if (res.code === 200) {
        message.success('更新成功')
        modalVisible.value = false
        loadPosts()
      } else {
        message.error(res.message || '更新失败')
      }
    } else {
      const res = await PostsService.createPost(formModel.value as any)
      if (res.code === 200) {
        message.success('创建成功')
        modalVisible.value = false
        current.value = 1
        loadPosts()
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

const handleCancel = () => {
  modalVisible.value = false
}

// ============== 列表查询 ==============
const loadCategoriesAndTags = async () => {
  const [cats, tags] = await Promise.all([
    CategoriesService.getCategoryList({ page: 1, size: 1000 }),
    TagsService.getTagList({ page: 1, size: 1000 })
  ])
  if (cats.code === 200) {
    categoryOptions.value = cats.data.records.map((c: any) => ({ label: c.name, value: c.id }))
  }
  if (tags.code === 200) {
    tagOptions.value = tags.data.records.map((t: any) => ({ label: t.name, value: t.id }))
  }
}

const loadPosts = async () => {
  try {
    loading.value = true
    const params = {
      page: current.value,
      size: pageSize.value,
      ...searchParams.value
    }
    const res = await PostsService.getPostList(params)
    if (res.code === 200) {
      dataSource.value = res.data.records
      total.value = res.data.total
    } else {
      message.error(res.message || '加载文章列表失败')
    }
  } catch (e) {
    message.error('加载文章列表失败')
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  current.value = 1
  loadPosts()
}

const handleReset = () => {
  searchParams.value = { title: '', categoryId: undefined, status: undefined }
  current.value = 1
  loadPosts()
}

const handleTableChange = (pagination: any) => {
  current.value = pagination.current
  pageSize.value = pagination.pageSize
  loadPosts()
}

const onSelectChange = (keys: number[]) => {
  selectedRowKeys.value = keys
}

const handleDelete = async (id: number) => {
  const res = await PostsService.deletePost(id)
  if (res.code === 200) {
    message.success('删除成功')
    loadPosts()
  } else {
    message.error(res.message || '删除失败')
  }
}

const handleBatchDelete = async () => {
  if (!selectedRowKeys.value.length) {
    message.warning('请选择要删除的文章')
    return
  }
  const res = await PostsService.batchDeletePosts(selectedRowKeys.value)
  if (res.code === 200) {
    message.success('批量删除成功')
    selectedRowKeys.value = []
    loadPosts()
  } else {
    message.error(res.message || '批量删除失败')
  }
}

const handleBatchStatusUpdate = async (status: string) => {
  if (!selectedRowKeys.value.length) {
    message.warning('请选择要更新状态的文章')
    return
  }
  const res = await PostsService.batchUpdatePostStatus(selectedRowKeys.value, status)
  if (res.code === 200) {
    message.success('批量状态更新成功')
    selectedRowKeys.value = []
    loadPosts()
  } else {
    message.error(res.message || '批量状态更新失败')
  }
}

const handleStatusChange = async (id: number, status: string) => {
  const res = await PostsService.updatePostStatus(id, status)
  if (res.code === 200) {
    message.success('状态更新成功')
    loadPosts()
  } else {
    message.error(res.message || '状态更新失败')
  }
}

onMounted(async () => {
  await loadCategoriesAndTags()
  await loadPosts()
})
</script>

<template>
  <div class="posts-management">
    <div class="page-header">
      <h2>文章管理</h2>
    </div>

    <!-- 搜索区域 -->
    <a-card class="search-card" :bordered="false">
      <a-form layout="inline" :model="searchParams">
        <a-form-item label="标题">
          <a-input v-model:value="searchParams.title" placeholder="请输入标题" allow-clear style="width: 200px" />
        </a-form-item>
        <a-form-item label="分类">
          <a-select v-model:value="searchParams.categoryId" placeholder="请选择分类" allow-clear style="width: 160px">
            <a-select-option v-for="opt in categoryOptions" :key="opt.value" :value="opt.value">{{ opt.label }}</a-select-option>
          </a-select>
        </a-form-item>

        <a-form-item label="状态">
          <a-select v-model:value="searchParams.status" placeholder="请选择状态" allow-clear style="width: 120px">
            <a-select-option v-for="opt in statusOptions" :key="opt.value" :value="opt.value">{{ opt.label }}</a-select-option>
          </a-select>
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
        <a-button type="primary" @click="openCreate">新建文章</a-button>
        <a-button danger :disabled="selectedRowKeys.length === 0" @click="handleBatchDelete">批量删除</a-button>
      </a-space>
    </a-card>

    <!-- 表格区域 -->
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
          <template v-if="column.key === 'category'">
            <span>{{ record.category?.name || '-' }}</span>
          </template>
          <template v-else-if="column.key === 'author'">
            <span>{{ record.author?.username || '-' }}</span>
          </template>
          <template v-else-if="column.key === 'status'">
            <a-tag :color="record.status === 'published' ? 'green' : 'orange'">{{ record.status === 'published' ? '已发布' : '草稿' }}</a-tag>
          </template>
          <template v-else-if="column.key === 'createdAt'">
            {{ formatDateTime(record.createdAt) }}
          </template>
          <template v-else-if="column.key === 'action'">
            <a-space>
              <a-button type="link" size="small" @click="openEdit(record)">编辑</a-button>
              <a-button type="link" size="small" :class="record.status === 'published' ? 'text-orange-500' : 'text-green-500'" @click="handleStatusChange(record.id, record.status === 'published' ? 'draft' : 'published')">
                {{ record.status === 'published' ? '下线' : '发布' }}
              </a-button>
              <a-popconfirm title="确定删除该文章吗？" @confirm="handleDelete(record.id)">
                <a-button type="link" size="small" danger>删除</a-button>
              </a-popconfirm>
            </a-space>
          </template>
        </template>
      </a-table>
    </a-card>

    <!-- 新建/编辑 弹窗 -->
    <a-modal v-model:open="modalVisible" :title="modalTitle" :confirm-loading="confirmLoading" @ok="handleOk" @cancel="handleCancel" destroy-on-close>
      <a-form :model="formModel" :rules="rules" ref="formRef" layout="vertical">
        <a-form-item name="title" label="标题" required>
          <a-input v-model:value="formModel.title" placeholder="请输入标题" />
        </a-form-item>
        <a-form-item name="content" label="内容" required>
          <TinyMCEEditor v-model="formModel.content" placeholder="请输入文章内容" :height="400" />
        </a-form-item>
        <a-form-item name="categoryId" label="分类" required>
          <a-select v-model:value="formModel.categoryId" placeholder="请选择分类">
            <a-select-option v-for="opt in categoryOptions" :key="opt.value" :value="opt.value">{{ opt.label }}</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item name="tagIds" label="标签">
          <a-select v-model:value="formModel.tagIds" mode="multiple" placeholder="请选择标签">
            <a-select-option v-for="opt in tagOptions" :key="opt.value" :value="opt.value">{{ opt.label }}</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item name="status" label="状态">
          <a-radio-group v-model:value="formModel.status">
            <a-radio :value="0">草稿</a-radio>
            <a-radio :value="1">已发布</a-radio>
          </a-radio-group>
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<style scoped>
.posts-management { padding: 24px; }
.page-header { margin-bottom: 24px; }
.page-header h2 { margin: 0; font-size: 24px; font-weight: 600; color: #262626; }
.search-card, .action-card { margin-bottom: 16px; }
</style>