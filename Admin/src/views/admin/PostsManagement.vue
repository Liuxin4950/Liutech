<script setup lang="ts">
import { computed, ref, reactive, onMounted, nextTick } from 'vue'
import { message } from 'ant-design-vue'
import { PlusOutlined, DeleteOutlined, SearchOutlined, ReloadOutlined } from '@ant-design/icons-vue'
import DOMPurify from 'dompurify'
import { useTablePage, useCrudActions, useModalForm } from '@/composables'
import PostsService from '../../services/posts'
import CategoriesService from '../../services/categories'
import TagsService from '../../services/tags'
import type { PostListParams, Post, PostListItem } from '../../services/posts'
import { formatDateTime } from '../../utils/utils'
import TinyMCEEditor from '../../components/TinyMCEEditor.vue'
import { ImageUploadService } from '../../services/upload'
import AdminAgentSidebar from '../../components/agent/AdminAgentSidebar.vue'
import type { AdminArticleDraftSnapshot, AgentActionResult, FieldUpdatePayload } from '../../types/agent'

// ============== 表格页面 ==============
const {
  loading, dataSource, selectedRowKeys, searchParams, pagination,
  load, handleSearch, handleReset, handleTableChange, onSelectChange, clearSelection
} = useTablePage<PostListItem, PostListParams>({
  loadFn: (params) => PostsService.getPostList(params),
  defaultSearchParams: { title: '', categoryId: undefined, status: undefined, includeDeleted: false },
  loadErrorMessage: '加载文章列表失败',
  autoLoad: false
})

// ============== CRUD 操作 ==============
const {
  handleDelete, handleBatchDelete, handleRestore,
  handlePermanentDelete, handleBatchPermanentDelete
} = useCrudActions({
  deleteFn: (id) => PostsService.deletePost(id),
  batchDeleteFn: (ids) => PostsService.batchDeletePosts(ids),
  restoreFn: (id) => PostsService.restorePost(id),
  permanentDeleteFn: (id) => PostsService.permanentDeletePost(id),
  batchPermanentDeleteFn: (ids) => PostsService.batchPermanentDeletePosts(ids),
  onRefresh: load,
  clearSelection,
  entityName: '文章'
})

// ============== 弹窗表单 ==============
const {
  modalVisible, modalTitle, isEdit, editingId, confirmLoading,
  formRef, formModel, handleOk, handleCancel
} = useModalForm<Post>({
  createFn: (data) => PostsService.createPost(data as Post),
  updateFn: (id, data) => PostsService.updatePost(id, data as Post),
  defaultForm: () => ({
    title: '',
    content: '',
    summary: '',
    coverImage: '',
    thumbnail: '',
    categoryId: undefined,
    tagIds: [],
    status: 'draft'
  }),
  onCreateSuccess: () => { pagination.current = 1; load() },
  onUpdateSuccess: load,
  entityName: '文章'
})

// ============== 表格列与选项 ==============
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
  { title: '操作', key: 'action', width: 200, fixed: 'right' as const }
]

const categoryOptions = ref<{ label: string; value: number }[]>([])
const tagOptions = ref<{ label: string; value: number }[]>([])
const statusOptions = [
  { label: '草稿', value: 'draft' },
  { label: '已发布', value: 'published' }
]

// ============== 分类/标签管理 ==============
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

const createCategoryVisible = ref(false)
const createTagVisible = ref(false)
const creatingCategory = ref(false)
const creatingTag = ref(false)
const newCategoryName = ref('')
const newCategoryDescription = ref('')
const newTagName = ref('')

const openCreateCategory = () => {
  newCategoryName.value = ''
  newCategoryDescription.value = ''
  createCategoryVisible.value = true
}

const openCreateTag = () => {
  newTagName.value = ''
  createTagVisible.value = true
}

const createCategory = async () => {
  const name = newCategoryName.value.trim()
  if (!name) { message.warning('请输入分类名称'); return }
  try {
    creatingCategory.value = true
    const res = await CategoriesService.createCategory({ name, description: newCategoryDescription.value.trim() } as any)
    if (res.code === 200) {
      await loadCategoriesAndTags()
      const created = categoryOptions.value.find(item => item.label === name)
      if (created) formModel.value.categoryId = created.value
      createCategoryVisible.value = false
      message.success('分类已创建并选中')
    } else {
      message.error(res.message || '创建分类失败')
    }
  } finally {
    creatingCategory.value = false
  }
}

const createTag = async () => {
  const name = newTagName.value.trim()
  if (!name) { message.warning('请输入标签名称'); return }
  try {
    creatingTag.value = true
    const res = await TagsService.createTag({ name } as any)
    if (res.code === 200) {
      await loadCategoriesAndTags()
      const created = tagOptions.value.find(item => item.label === name)
      if (created) {
        const current = formModel.value.tagIds || []
        if (!current.includes(created.value)) {
          formModel.value.tagIds = [...current, created.value]
        }
      }
      createTagVisible.value = false
      message.success('标签已创建并选中')
    } else {
      message.error(res.message || '创建标签失败')
    }
  } finally {
    creatingTag.value = false
  }
}

// ============== 覆盖 openCreate/openEdit ==============
const openCreate = async () => {
  if (categoryOptions.value.length === 0 || tagOptions.value.length === 0) {
    await loadCategoriesAndTags()
  }
  isEdit.value = false
  modalTitle.value = '新建文章'
  editingId.value = null
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
    if (categoryOptions.value.length === 0 || tagOptions.value.length === 0) {
      await loadCategoriesAndTags()
    }
    isEdit.value = true
    modalTitle.value = '编辑文章'
    editingId.value = record.id || null
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

// ============== 图片上传 ==============
const coverImageInput = ref<HTMLInputElement>()
const thumbnailInput = ref<HTMLInputElement>()
const uploadingCover = ref(false)
const uploadingThumbnail = ref(false)

const triggerCoverImageUpload = () => { coverImageInput.value?.click() }
const triggerThumbnailUpload = () => { thumbnailInput.value?.click() }

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
    if (target) target.value = ''
  }
}

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
    if (target) target.value = ''
  }
}

const removeCoverImage = () => { formModel.value.coverImage = '' }
const removeThumbnail = () => { formModel.value.thumbnail = '' }

// ============== AI Agent 集成 ==============
const aiSuggestedCategoryName = ref('')
const aiSuggestedTagNames = ref<string[]>([])
const undoStack = ref<Array<{ field: string; oldValue: any }>>([])
const highlightedFields = reactive<Record<string, boolean>>({})

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

const fieldLabelMap: Record<string, string> = {
  title: '标题',
  summary: '摘要',
  content: '正文',
  categoryId: '分类',
  tagIds: '标签',
}

const sameName = (left?: string, right?: string) =>
  (left || '').trim().toLowerCase() === (right || '').trim().toLowerCase()

const normalizeSuggestedNames = (names?: string[]) => {
  const result: string[] = []
  for (const raw of names || []) {
    const name = (raw || '').trim()
    if (!name || result.some(item => sameName(item, name))) continue
    result.push(name)
  }
  return result.slice(0, 8)
}

const rememberAiTaxonomySuggestions = (payload: FieldUpdatePayload) => {
  const suggestedCategory = (payload.suggestedCategoryName || '').trim()
  if (suggestedCategory) {
    const existing = categoryOptions.value.find(item => sameName(item.label, suggestedCategory))
    if (existing) {
      formModel.value.categoryId = existing.value
      aiSuggestedCategoryName.value = ''
    } else {
      aiSuggestedCategoryName.value = suggestedCategory
    }
  }
  if (payload.suggestedTagNames?.length) {
    let currentIds = formModel.value.tagIds || []
    const nextSuggestions: string[] = []
    for (const name of normalizeSuggestedNames(payload.suggestedTagNames)) {
      const existing = tagOptions.value.find(item => sameName(item.label, name))
      if (existing) {
        if (!currentIds.includes(existing.value)) {
          currentIds = [...currentIds, existing.value]
          formModel.value.tagIds = currentIds
        }
      } else if (!nextSuggestions.some(item => sameName(item, name))) {
        nextSuggestions.push(name)
      }
    }
    aiSuggestedTagNames.value = nextSuggestions
  }
}

const handleFieldUpdate = (payload: FieldUpdatePayload) => {
  const entries: Array<[string, any]> = []
  if (payload.title !== undefined && payload.title !== null) entries.push(['title', payload.title])
  if (payload.summary !== undefined && payload.summary !== null) entries.push(['summary', payload.summary])
  if (payload.contentHtml !== undefined && payload.contentHtml !== null) entries.push(['content', DOMPurify.sanitize(payload.contentHtml)])
  if (payload.categoryId !== undefined && payload.categoryId !== null) entries.push(['categoryId', payload.categoryId])
  if (payload.tagIds?.length) entries.push(['tagIds', [...payload.tagIds]])
  if (entries.length > 0) undoStack.value = []
  for (const [field, newValue] of entries) {
    const oldValue = (formModel.value as any)[field]
    undoStack.value.push({ field, oldValue })
    ;(formModel.value as any)[field] = newValue
    highlightedFields[field] = true
    nextTick(() => {
      setTimeout(() => { delete highlightedFields[field] }, 1500)
    })
  }
  if (entries.length > 0) {
    message.success(`AI 已更新 ${entries.map(([f]) => fieldLabelMap[f] || f).join('、')}`)
  }
  rememberAiTaxonomySuggestions(payload)
}

const undoField = (field: string) => {
  let lastIdx = -1
  for (let i = undoStack.value.length - 1; i >= 0; i--) {
    if (undoStack.value[i].field === field) { lastIdx = i; break }
  }
  if (lastIdx < 0) return
  const { oldValue } = undoStack.value[lastIdx]
  undoStack.value.splice(lastIdx, 1)
  ;(formModel.value as any)[field] = oldValue
  message.info(`已撤销「${fieldLabelMap[field] || field}」`)
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
  load()
}

const createAiSuggestedCategory = async (name: string) => {
  const categoryName = name.trim()
  if (!categoryName) return
  newCategoryName.value = categoryName
  newCategoryDescription.value = '由 AI 写作助手建议创建'
  await createCategory()
  aiSuggestedCategoryName.value = ''
}

const createAiSuggestedTag = async (name: string) => {
  const tagName = name.trim()
  if (!tagName) return
  newTagName.value = tagName
  await createTag()
  aiSuggestedTagNames.value = aiSuggestedTagNames.value.filter(item => !sameName(item, tagName))
}

const createAllAiSuggestedTags = async () => {
  const names = [...aiSuggestedTagNames.value]
  for (const name of names) await createAiSuggestedTag(name)
}

const createAllAiSuggestedTaxonomy = async () => {
  const categoryName = aiSuggestedCategoryName.value
  const tagNames = [...aiSuggestedTagNames.value]
  if (categoryName) await createAiSuggestedCategory(categoryName)
  for (const name of tagNames) await createAiSuggestedTag(name)
}

// ============== 自定义操作 ==============
const handleBatchRestore = async () => {
  if (!selectedRowKeys.value.length) {
    message.warning('请选择要恢复的文章')
    return
  }
  const res = await PostsService.batchRestorePosts(selectedRowKeys.value)
  if (res.code === 200) {
    message.success('批量恢复成功')
    clearSelection()
    load()
  } else {
    message.error(res.message || '批量恢复失败')
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
    clearSelection()
    load()
  } else {
    message.error(res.message || '批量状态更新失败')
  }
}

const handleStatusChange = async (id: number, status: string) => {
  const res = await PostsService.updatePostStatus(id, status)
  if (res.code === 200) {
    message.success('状态更新成功')
    load()
  } else {
    message.error(res.message || '状态更新失败')
  }
}

// 表单校验规则
const rules = {
  title: [{ required: true, message: '请输入标题' }],
  content: [{ required: true, message: '请输入内容' }],
  categoryId: [{ required: true, message: '请选择分类' }]
}

// ============== 初始化 ==============
onMounted(async () => {
  await loadCategoriesAndTags()
  await load()
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
                  @confirm="handleBatchDelete(selectedRowKeys)"
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
                @confirm="handleBatchPermanentDelete(selectedRowKeys)"
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
        <div :class="['field-wrapper', { 'field-highlight': highlightedFields.title }]">
        <a-form-item name="title" label="标题" required>
          <a-input v-model:value="formModel.title" placeholder="请输入标题" />
        </a-form-item>
        </div>

        <div :class="['field-wrapper', { 'field-highlight': highlightedFields.summary }]">
        <a-form-item label="文章摘要">
          <a-textarea
            v-model:value="formModel.summary"
            placeholder="请输入文章摘要，用于SEO和文章预览"
            :rows="3"
            :maxlength="200"
            show-count
          />
        </a-form-item>
        </div>

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

        <div :class="['field-wrapper', { 'field-highlight': highlightedFields.content }]">
        <a-form-item name="content" label="内容" required>
          <TinyMCEEditor v-model="formModel.content" placeholder="请输入文章内容" :height="400" />
        </a-form-item>
        </div>
        <div :class="['field-wrapper', { 'field-highlight': highlightedFields.categoryId }]">
        <a-form-item name="categoryId" label="分类" required>
          <a-input-group compact>
            <a-select v-model:value="formModel.categoryId" placeholder="请选择分类" style="width: calc(100% - 40px)">
              <a-select-option v-for="opt in categoryOptions" :key="opt.value" :value="opt.value">{{ opt.label }}</a-select-option>
            </a-select>
            <a-tooltip title="新增分类">
              <a-button @click="openCreateCategory">
                <template #icon><PlusOutlined /></template>
              </a-button>
            </a-tooltip>
          </a-input-group>
        </a-form-item>
        </div>
        <div :class="['field-wrapper', { 'field-highlight': highlightedFields.tagIds }]">
        <a-form-item name="tagIds" label="标签">
          <a-input-group compact>
            <a-select v-model:value="formModel.tagIds" mode="multiple" placeholder="请选择标签" style="width: calc(100% - 40px)">
              <a-select-option v-for="opt in tagOptions" :key="opt.value" :value="opt.value">{{ opt.label }}</a-select-option>
            </a-select>
            <a-tooltip title="新增标签">
              <a-button @click="openCreateTag">
                <template #icon><PlusOutlined /></template>
              </a-button>
            </a-tooltip>
          </a-input-group>
        </a-form-item>
        </div>
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
        @field-update="handleFieldUpdate"
        @action-done="handleAgentActionDone"
      />
      </div>
      <div v-if="aiSuggestedCategoryName || aiSuggestedTagNames.length" class="ai-taxonomy-bar">
        <span class="ai-taxonomy-label">AI 建议新增：</span>
        <a-button
          v-if="aiSuggestedCategoryName"
          size="small"
          :loading="creatingCategory"
          @click="createAiSuggestedCategory(aiSuggestedCategoryName)"
        >
          分类：{{ aiSuggestedCategoryName }}
        </a-button>
        <a-button
          v-for="name in aiSuggestedTagNames"
          :key="name"
          size="small"
          :loading="creatingTag && newTagName === name"
          @click="createAiSuggestedTag(name)"
        >
          标签：{{ name }}
        </a-button>
        <a-button
          v-if="aiSuggestedTagNames.length > 1"
          size="small"
          type="primary"
          ghost
          :loading="creatingTag"
          @click="createAllAiSuggestedTags"
        >
          全部创建并选中
        </a-button>
        <a-button
          v-if="aiSuggestedCategoryName && aiSuggestedTagNames.length"
          size="small"
          type="primary"
          :loading="creatingCategory || creatingTag"
          @click="createAllAiSuggestedTaxonomy"
        >
          分类和标签全部处理
        </a-button>
      </div>
      <div v-if="undoStack.length" class="undo-bar">
        <span class="undo-bar-label">AI 已修改：</span>
        <a-button
          v-for="(entry, idx) in undoStack"
          :key="idx"
          size="small"
          @click="undoField(entry.field)"
        >
          撤销「{{ fieldLabelMap[entry.field] || entry.field }}」
        </a-button>
      </div>
    </a-modal>

    <a-modal v-model:open="createCategoryVisible" title="新增分类" :confirm-loading="creatingCategory" destroy-on-close @ok="createCategory" @cancel="createCategoryVisible = false">
      <a-form layout="vertical">
        <a-form-item label="分类名称" required>
          <a-input v-model:value="newCategoryName" placeholder="请输入分类名称" maxlength="50" @press-enter="createCategory" />
        </a-form-item>
        <a-form-item label="分类描述">
          <a-textarea v-model:value="newCategoryDescription" placeholder="可选" :rows="3" maxlength="200" />
        </a-form-item>
      </a-form>
    </a-modal>

    <a-modal v-model:open="createTagVisible" title="新增标签" :confirm-loading="creatingTag" destroy-on-close @ok="createTag" @cancel="createTagVisible = false">
      <a-form layout="vertical">
        <a-form-item label="标签名称" required>
          <a-input v-model:value="newTagName" placeholder="请输入标签名称" maxlength="30" @press-enter="createTag" />
        </a-form-item>
      </a-form>
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

.field-wrapper {
  transition: background-color 0.3s ease;
  border-radius: 6px;
  padding: 2px 4px;
  margin: -2px -4px;
}

.field-wrapper.field-highlight {
  background-color: #e6f7ff;
  animation: field-flash 1.5s ease-out;
}

@keyframes field-flash {
  0% { background-color: #bae7ff; }
  100% { background-color: transparent; }
}

.undo-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  padding: 10px 16px;
  background: #fffbe6;
  border-top: 1px solid #ffe58f;
  margin: 0 -24px -12px;
  border-radius: 0 0 8px 8px;
}

.undo-bar-label {
  font-size: 13px;
  color: #d46b08;
  font-weight: 500;
  white-space: nowrap;
}

.ai-taxonomy-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  padding: 10px 16px;
  background: #f6ffed;
  border-top: 1px solid #b7eb8f;
  margin: 0 -24px;
}

.ai-taxonomy-label {
  font-size: 13px;
  color: #389e0d;
  font-weight: 500;
  white-space: nowrap;
}
</style>

