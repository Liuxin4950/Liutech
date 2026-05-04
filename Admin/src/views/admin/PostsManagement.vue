<script setup lang="ts">
import { computed, ref, reactive, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import { PlusOutlined, DeleteOutlined, SearchOutlined, ReloadOutlined } from '@ant-design/icons-vue'
import PostsService from '../../services/posts'
import CategoriesService from '../../services/categories'
import TagsService from '../../services/tags'
import type { PostListParams, Post, PostListItem } from '../../services/posts'
import { formatDateTime } from '../../utils/uitls'
import TinyMCEEditor from '../../components/TinyMCEEditor.vue'
import { ImageUploadService } from '../../services/upload'
import AdminAgentSidebar from '../../components/agent/AdminAgentSidebar.vue'
import type { AdminArticleDraftSnapshot, AgentActionResult } from '../../types/agent'

// 响应式数据
const loading = ref(false)
const dataSource = ref<PostListItem[]>([])
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
const searchParams = ref<PostListParams>({
  title: '',
  categoryId: undefined,
  status: undefined,
  includeDeleted: false
})

// 表格列定义
const columns = [
  { title: '标题', dataIndex: 'title', key: 'title', ellipsis: true },
  { title: '分类', dataIndex: 'category', key: 'category' },
  { title: '作者', dataIndex: 'author', key: 'author' },
  { title: '浏览量', dataIndex: 'viewCount', key: 'viewCount' },
  { title: '点赞量', dataIndex: 'likeCount', key: 'likeCount' },
  { title: '评论数', dataIndex: 'commentCount', key: 'commentCount' },
  { title: '状态', dataIndex: 'status', key: 'status' },
  { title: '删除状态', key: 'deleteStatus' },
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
  summary: '',
  coverImage: '',
  thumbnail: '',
  categoryId: undefined,
  tagIds: [],
  status: 'draft'
})

const agentDraftSnapshot = computed(() => ({
  postId: editingId.value,
  title: formModel.value.title,
  content: formModel.value.content,
  summary: formModel.value.summary,
  categoryId: formModel.value.categoryId,
  tagIds: formModel.value.tagIds,
  status: formModel.value.status,
  coverImage: formModel.value.coverImage,
  thumbnail: formModel.value.thumbnail
}))

const rules = {
  title: [{ required: true, message: '请输入标题' }],
  content: [{ required: true, message: '请输入内容' }],
  categoryId: [{ required: true, message: '请选择分类' }]
}

// 图片上传相关
const coverImageInput = ref<HTMLInputElement>()
const thumbnailInput = ref<HTMLInputElement>()
const uploadingCover = ref(false)
const uploadingThumbnail = ref(false)

// 触发封面图片上传
const triggerCoverImageUpload = () => {
  coverImageInput.value?.click()
}

// 触发缩略图上传
const triggerThumbnailUpload = () => {
  thumbnailInput.value?.click()
}

// 处理封面图片上传
const handleCoverImageUpload = async (event: Event) => {
  const target = event.target as HTMLInputElement
  const file = target.files?.[0]
  if (!file) return

  try {
    uploadingCover.value = true
    const result = await ImageUploadService.uploadImage(file)
    formModel.value.coverImage = result.fileUrl
    message.success('封面图片上传成功')
  } catch (error: any) {
    message.error(error.message || '封面图片上传失败')
  } finally {
    uploadingCover.value = false
    // 清空input值，允许重复选择同一文件
    if (target) target.value = ''
  }
}

// 处理缩略图上传
const handleThumbnailUpload = async (event: Event) => {
  const target = event.target as HTMLInputElement
  const file = target.files?.[0]
  if (!file) return

  try {
    uploadingThumbnail.value = true
    const result = await ImageUploadService.uploadImage(file)
    formModel.value.thumbnail = result.fileUrl
    message.success('缩略图上传成功')
  } catch (error: any) {
    message.error(error.message || '缩略图上传失败')
  } finally {
    uploadingThumbnail.value = false
    // 清空input值，允许重复选择同一文件
    if (target) target.value = ''
  }
}

// 删除封面图片
const removeCoverImage = () => {
  formModel.value.coverImage = ''
}

// 删除缩略图
const removeThumbnail = () => {
  formModel.value.thumbnail = ''
}

const openCreate = async () => {
  isEdit.value = false
  modalTitle.value = '新建文章'
  editingId.value = null
  
  // 确保分类和标签数据已加载
  if (categoryOptions.value.length === 0 || tagOptions.value.length === 0) {
    await loadCategoriesAndTags()
  }
  
  formModel.value = {
    title: '',
    content: '',
    summary: '',
    coverImage: '',
    thumbnail: '',
    categoryId: undefined,
    tagIds: [],
    status: 'draft'
  }
  modalVisible.value = true
}

const openEdit = async (record: PostListItem) => {
  try {
    isEdit.value = true
    modalTitle.value = '编辑文章'
    editingId.value = record.id || null
    
    // 确保分类和标签数据已加载
    if (categoryOptions.value.length === 0 || tagOptions.value.length === 0) {
      await loadCategoriesAndTags()
    }
    
    // 获取完整的文章详情（包含content字段）
    const res = await PostsService.getPostById(record.id)
    if (res.code === 200) {
      const postDetail = res.data
      
      formModel.value = {
        title: postDetail.title,
        content: postDetail.content || '',
        summary: postDetail.summary || '',
        coverImage: postDetail.coverImage || '',
        thumbnail: postDetail.thumbnail || '',
        categoryId: postDetail.categoryId,
        tagIds: postDetail.tags?.map(tag => tag.id) || [],
        status: postDetail.status === 'published' ? 'published' : 'draft'
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
        pagination.current = 1
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

const handleAgentApplyDraft = (draft: Partial<AdminArticleDraftSnapshot>) => {
  const nextStatus = draft.status === 'published' || draft.status === 'archived' || draft.status === 'draft'
    ? draft.status
    : (formModel.value.status || 'draft')
  formModel.value = {
    ...formModel.value,
    ...draft,
    status: nextStatus
  }
}

const handleAgentActionDone = (result?: AgentActionResult) => {
  const target = result?.target as { status?: string; postId?: number } | undefined
  if (target?.status === 'published' || target?.status === 'draft' || target?.status === 'archived') {
    formModel.value.status = target.status
  }
  if (!editingId.value && target?.postId) {
    editingId.value = target.postId
    isEdit.value = true
    modalTitle.value = '编辑文章'
  }
  loadPosts()
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
      page: pagination.current,
      size: pagination.pageSize,
      ...searchParams.value
    }
    const res = await PostsService.getPostList(params)
    if (res.code === 200) {
      dataSource.value = res.data.records
      pagination.total = res.data.total
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
  pagination.current = 1
  loadPosts()
}

const handleReset = () => {
  searchParams.value = { title: '', categoryId: undefined, status: undefined, includeDeleted: false }
  pagination.current = 1
  loadPosts()
}

// 恢复删除
const handleRestore = async (id: number) => {
  const res = await PostsService.restorePost(id)
  if (res.code === 200) { message.success('恢复成功'); loadPosts() } else { message.error(res.message || '恢复失败') }
}

// 彻底删除
const handlePermanentDelete = async (id: number) => {
  const res = await PostsService.permanentDeletePost(id)
  if (res.code === 200) {
    message.success('彻底删除成功')
    loadPosts()
  } else {
    message.error(res.message || '彻底删除失败')
  }
}

// 批量彻底删除
const handleBatchPermanentDelete = async () => {
  if (!selectedRowKeys.value.length) {
    message.warning('请选择要彻底删除的文章')
    return
  }
  const res = await PostsService.batchPermanentDeletePosts(selectedRowKeys.value)
  if (res.code === 200) {
    message.success('批量彻底删除成功')
    selectedRowKeys.value = []
    loadPosts()
  } else {
    message.error(res.message || '批量彻底删除失败')
  }
}

// 批量恢复文章
const handleBatchRestore = async () => {
  if (!selectedRowKeys.value.length) {
    message.warning('请选择要恢复的文章')
    return
  }
  const res = await PostsService.batchRestorePosts(selectedRowKeys.value)
  if (res.code === 200) {
    message.success('批量恢复成功')
    selectedRowKeys.value = []
    loadPosts()
  } else {
    message.error(res.message || '批量恢复失败')
  }
}

const handleTableChange = (p: any) => {
  pagination.current = p.current
  pagination.pageSize = p.pageSize
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
  <div class="p-24">
    <!-- 搜索区域 -->
    <a-card :bordered="false" class="mb-16">
      <a-form layout="horizontal" :model="searchParams">
        <a-row :gutter="24">
          <a-col :span="6">
            <a-form-item label="标题" name="title" class="mb-0">
              <a-input v-model:value="searchParams.title" placeholder="请输入标题" allow-clear />
            </a-form-item>
          </a-col>
          <a-col :span="6">
            <a-form-item label="分类" name="categoryId" class="mb-0">
              <a-select v-model:value="searchParams.categoryId" placeholder="请选择分类" allow-clear>
                <a-select-option v-for="opt in categoryOptions" :key="opt.value" :value="opt.value">{{ opt.label }}</a-select-option>
              </a-select>
            </a-form-item>
          </a-col>
          <a-col :span="6">
            <a-form-item label="状态" name="status" class="mb-0">
              <a-select v-model:value="searchParams.status" placeholder="请选择状态" allow-clear>
                <a-select-option v-for="opt in statusOptions" :key="opt.value" :value="opt.value">{{ opt.label }}</a-select-option>
              </a-select>
            </a-form-item>
          </a-col>
          <a-col :span="6" class="text-right">
            <a-space>
               <a-tooltip title="显示已删除的文章">
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

    <!-- 表格区域 -->
    <a-card :bordered="false">
      <template #title>
        <span>文章列表</span>
      </template>
      <template #extra>
         <a-space>
            <a-button type="primary" @click="openCreate">
              <template #icon><PlusOutlined /></template>
              新建文章
            </a-button>
            <template v-if="!searchParams.includeDeleted">
               <a-popconfirm
                  title="确定要批量删除选中的文章吗？"
                  @confirm="handleBatchDelete"
                  :disabled="selectedRowKeys.length === 0"
                >
                  <a-button danger :disabled="selectedRowKeys.length === 0">
                     <template #icon><DeleteOutlined /></template>
                     批量删除
                  </a-button>
               </a-popconfirm>
            </template>
            <template v-else>
              <a-button :disabled="selectedRowKeys.length === 0" @click="handleBatchRestore">批量恢复</a-button>
              <a-popconfirm
                title="确定要彻底删除选中的文章吗？此操作不可恢复！"
                ok-text="确定"
                cancel-text="取消"
                @confirm="handleBatchPermanentDelete"
              >
                <a-button danger :disabled="selectedRowKeys.length === 0">批量彻底删除</a-button>
              </a-popconfirm>
            </template>
         </a-space>
      </template>

      <a-table
        :columns="columns"
        :data-source="dataSource"
        :loading="loading"
        :pagination="pagination"
        :row-selection="{ selectedRowKeys, onChange: onSelectChange }"
        row-key="id"
        @change="handleTableChange"
        :scroll="{ x: 1300 }"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'category'">
            <span>{{ record.category?.name || '-' }}</span>
          </template>
          <template v-else-if="column.key === 'author'">
            <span>{{ record.author?.username || '-' }}</span>
          </template>
          <template v-else-if="column.key === 'viewCount'">
            <span>{{ record.viewCount || 0 }}</span>
          </template>
          <template v-else-if="column.key === 'likeCount'">
            <span>{{ record.likeCount || 0 }}</span>
          </template>
          <template v-else-if="column.key === 'commentCount'">
            <span>{{ record.commentCount || 0 }}</span>
          </template>
          <template v-else-if="column.key === 'status'">
            <a-tag :color="record.status === 'published' ? 'green' : 'orange'">{{ record.status === 'published' ? '已发布' : '草稿' }}</a-tag>
          </template>
          <template v-else-if="column.key === 'deleteStatus'">
            <a-tag v-if="record.deletedAt" color="red">已删除</a-tag>
            <a-tag v-else color="green">正常</a-tag>
          </template>
          <template v-else-if="column.key === 'createdAt'">
            {{ formatDateTime(record.createdAt) }}
          </template>
          <template v-else-if="column.key === 'action'">
            <a-space>
              <template v-if="!record.deletedAt">
                <a-button type="link" size="small" @click="openEdit(record)">编辑</a-button>
                <a-button type="link" size="small" :class="record.status === 'published' ? 'text-orange-500' : 'text-green-500'" @click="handleStatusChange(record.id, record.status === 'published' ? 'draft' : 'published')">
                  {{ record.status === 'published' ? '下线' : '发布' }}
                </a-button>
                <a-popconfirm title="确定删除该文章吗？" @confirm="handleDelete(record.id)">
                  <a-button type="link" size="small" danger>删除</a-button>
                </a-popconfirm>
              </template>
              <template v-else>
                <a-popconfirm title="确定恢复该文章吗？" @confirm="handleRestore(record.id)">
                  <a-button type="link" size="small">恢复</a-button>
                </a-popconfirm>
                <a-popconfirm 
                  title="确定要彻底删除该文章吗？此操作不可恢复！" 
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

    <!-- 新建/编辑 弹窗 -->
    <a-modal v-model:open="modalVisible" :title="modalTitle" :width="1280" :confirm-loading="confirmLoading" @ok="handleOk" @cancel="handleCancel" destroy-on-close>
      <div class="editor-agent-layout">
      <div class="editor-form-pane">
      <a-form :model="formModel" :rules="rules" ref="formRef" layout="vertical">
        <a-form-item name="title" label="标题" required>
          <a-input v-model:value="formModel.title" placeholder="请输入标题" />
        </a-form-item>
        
        <a-form-item label="文章摘要">
          <a-textarea 
            v-model:value="formModel.summary" 
            placeholder="请输入文章摘要，用于SEO和文章预览" 
            :rows="3"
            :maxlength="200"
            show-count
          />
        </a-form-item>

        <a-form-item label="封面图片">
          <div class="image-upload-container">
            <div v-if="formModel.coverImage" class="image-preview">
              <img :src="formModel.coverImage" alt="封面图片" class="preview-image" />
              <div class="image-actions">
                <a-button type="text" danger @click="removeCoverImage">
                  <template #icon><DeleteOutlined /></template>
                  删除
                </a-button>
              </div>
            </div>
            <div v-else class="upload-placeholder" @click="triggerCoverImageUpload">
              <PlusOutlined />
              <div class="upload-text">上传封面图片</div>
            </div>
            <input 
              ref="coverImageInput"
              type="file" 
              accept="image/*" 
              style="display: none" 
              @change="handleCoverImageUpload"
            />
          </div>
        </a-form-item>

        <a-form-item label="缩略图">
          <div class="image-upload-container">
            <div v-if="formModel.thumbnail" class="image-preview">
              <img :src="formModel.thumbnail" alt="缩略图" class="preview-image" />
              <div class="image-actions">
                <a-button type="text" danger @click="removeThumbnail">
                  <template #icon><DeleteOutlined /></template>
                  删除
                </a-button>
              </div>
            </div>
            <div v-else class="upload-placeholder" @click="triggerThumbnailUpload">
              <PlusOutlined />
              <div class="upload-text">上传缩略图</div>
            </div>
            <input 
              ref="thumbnailInput"
              type="file" 
              accept="image/*" 
              style="display: none" 
              @change="handleThumbnailUpload"
            />
          </div>
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
            <a-radio value="draft">草稿</a-radio>
            <a-radio value="published">已发布</a-radio>
          </a-radio-group>
        </a-form-item>
      </a-form>
      </div>
      <AdminAgentSidebar
        :draft="agentDraftSnapshot"
        @apply-draft="handleAgentApplyDraft"
        @action-done="handleAgentActionDone"
      />
      </div>
    </a-modal>
  </div>
</template>

<style scoped>
/* 移除旧的样式，使用 utility classes */
.editor-agent-layout {
  display: flex;
  gap: 16px;
  align-items: flex-start;
}

.editor-form-pane {
  flex: 1;
  min-width: 0;
}

.image-upload-container {
  width: 100%;
}

.image-preview {
  position: relative;
  display: inline-block;
  border: 1px solid var(--border-base);
  border-radius: 6px;
  overflow: hidden;
}

.preview-image {
  width: 200px;
  height: 120px;
  object-fit: cover;
  display: block;
}

.image-actions {
  position: absolute;
  top: 8px;
  right: 8px;
  background: rgba(0, 0, 0, 0.6);
  border-radius: 4px;
  padding: 4px;
}

.image-actions .ant-btn {
  color: white;
  border: none;
  background: transparent;
  padding: 4px 8px;
  height: auto;
}

.image-actions .ant-btn:hover {
  background: rgba(255, 255, 255, 0.2);
}

.upload-placeholder {
  width: 200px;
  height: 120px;
  border: 2px dashed var(--border-base);
  border-radius: 6px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: all 0.3s;
  color: var(--text-tertiary);
}

.upload-placeholder:hover {
  border-color: var(--color-primary);
  color: var(--color-primary);
}

.upload-text {
  margin-top: 8px;
  font-size: 14px;
}
</style>
