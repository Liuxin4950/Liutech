<template>
  <div class="content">
    <!-- 页面标题 -->
    <div class="page-header">
      <div class="page-title">
        <Icon :name="isEditMode ? 'edit-2' : 'plus'" size="20" />
        <span>{{ isEditMode ? '编辑文章' : '新建文章' }}</span>
      </div>
    </div>

    <!-- 编辑器主体 -->
    <div class="editor-container">
      <!-- 上侧编辑区 -->
      <div class="editor-main">
        <!-- 文章标题 -->
        <div class="title-section">
          <input v-model="form.title" type="text" class="title-input" placeholder="请输入文章标题..." maxlength="100">
          <div class="char-count text-sm text-muted">{{ form.title.length }}/100</div>
        </div>

        <!-- 文章内容编辑器 -->
        <div class="content-section">
          <TinyMCEEditor v-model="form.content" :height="1000" placeholder="开始编写你的文章内容..." class="content-editor" />
        </div>
      </div>

      <!-- 下侧设置面板 -->
      <div class="editor-sidebar ">
        <!-- 发布设置 -->
        <div class="sidebar-section">
          <!-- 附件上传区域 -->
          <div class="sidebar-item">
            <div class="sidebar-title flex flex-ac flex-sb mb-12">
              <span>附件资源</span>
              <span class="text-xs text-muted" v-if="attachments.length > 0">{{ attachments.length }} 个</span>
            </div>

            <!-- 操作按钮：上传文件和添加外链 -->
            <div class="attach-actions">
              <button class="attach-action-btn" @click="triggerAttachmentUpload">
                <Icon name="upload" size="14" />
                <span>上传文件</span>
              </button>
              <button class="attach-action-btn" @click="showExternalLinkForm = true">
                <Icon name="link" size="14" />
                <span>添加外链</span>
              </button>
            </div>
            <input ref="attachmentInput" type="file" multiple @change="handleAttachmentUpload" style="display: none;">

            <!-- 附件列表 -->
            <div v-if="attachments.length > 0" class="attachment-list">
              <div v-for="attachment in attachments" :key="attachment.id" class="attachment-item">
                <div class="att-info">
                  <span class="att-name" :title="attachment.name">{{ attachment.name }}</span>
                  <div class="att-meta">
                    <span class="att-type">{{ attachment.resourceType === 'link' ? '外链' : (attachment.size > 0 ? formatFileSize(attachment.size) : '文件') }}</span>
                    <!-- 积分切换 -->
                    <div class="att-pricing" @click.stop>
                      <label class="pricing-toggle" :class="{ active: attachment.downloadType === 0 }">
                        <input type="radio" :name="'dl-type-' + attachment.id" :value="0"
                          v-model="attachment.downloadType"
                          @change="onDownloadTypeChange(attachment)">
                        <span>免费</span>
                      </label>
                      <label class="pricing-toggle" :class="{ active: attachment.downloadType === 1 }">
                        <input type="radio" :name="'dl-type-' + attachment.id" :value="1"
                          v-model="attachment.downloadType"
                          @change="onDownloadTypeChange(attachment)">
                        <span>积分</span>
                      </label>
                      <input v-if="attachment.downloadType === 1" type="number"
                        :value="attachment.pointsNeeded"
                        @change="handlePointsInput(attachment, $event)"
                        min="1" max="999" class="points-mini-input"
                        @click.stop>
                    </div>
                  </div>
                </div>
                <button @click.stop="removeAttachment(attachment.id)" class="att-remove" title="删除">
                  ×
                </button>
              </div>
            </div>
          </div>

          <!-- 外部链接弹窗 -->
          <div v-if="showExternalLinkForm" class="modal-overlay" @click.self="showExternalLinkForm = false">
            <div class="modal-box">
              <div class="modal-header">
                <span>添加外部链接</span>
                <button @click="showExternalLinkForm = false" class="modal-close">
                  <Icon name="times" size="18" />
                </button>
              </div>
              <div class="modal-body">
                <div class="field-row">
                  <input v-model="externalLinkForm.name" type="text" placeholder="资源名称 *" class="modal-input">
                </div>
                <div class="field-row">
                  <input v-model="externalLinkForm.externalLink" type="url" placeholder="https://链接地址 *" class="modal-input">
                </div>
                <div class="field-row">
                  <textarea v-model="externalLinkForm.purchasedNote" placeholder="购买后说明（提取码等）" class="modal-input" rows="2"></textarea>
                </div>
                <div class="pricing-row">
                  <label class="pricing-option" :class="{ active: externalLinkForm.downloadType === 0 }">
                    <input type="radio" v-model="externalLinkForm.downloadType" :value="0">
                    <span>免费</span>
                  </label>
                  <label class="pricing-option" :class="{ active: externalLinkForm.downloadType === 1 }">
                    <input type="radio" v-model="externalLinkForm.downloadType" :value="1">
                    <span>积分</span>
                  </label>
                  <input v-if="externalLinkForm.downloadType === 1" type="number" v-model.number="externalLinkForm.pointsNeeded" min="1" placeholder="积分" class="points-input">
                </div>
              </div>
              <div class="modal-footer">
                <button @click="showExternalLinkForm = false" class="btn-cancel">取消</button>
                <button @click="createExternalLinkResource" class="btn-confirm">
                  <Icon name="plus" size="14" /> 添加
                </button>
              </div>
            </div>
          </div>

          <!-- 标签设置 -->
          <div class="sidebar-item flex flex-ac gap-20">
            <div class="sidebar-title">文章标签</div>
            <div class="sidebar-content">
              <div v-if="selectedTags.length > 0" class="selected-tags tags-cloud mb-12">
                <span v-for="tag in selectedTags" :key="tag.id" class="tag">
                  {{ tag.name }}
                  <button type="button" @click="removeTag(tag.id)" class="tag-remove">
                    ×
                  </button>
                </span>
              </div>
              <div class="flex gap-8">
                <select v-model="selectedTagId" @change="addTag" class="field-select" style="flex: 1;">
                  <option value="">选择标签</option>
                  <option v-for="tag in availableTags" :key="tag.id" :value="tag.id">
                    {{ tag.name }}
                  </option>
                </select>
                <button 
                  type="button" 
                  @click="showCreateTagDialog" 
                  class="btn-secondary" 
                  title="创建新标签"
                  style="padding: 8px 12px; min-width: auto;"
                >
                  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <line x1="12" y1="5" x2="12" y2="19"></line>
                    <line x1="5" y1="12" x2="19" y2="12"></line>
                  </svg>
                </button>
              </div>
            </div>
          </div>
          <!-- 图片 -->
          <div class="sidebar-item flex flex-ac gap-20">
            <div class="sidebar-title">添加封面</div>
            <div class="sidebar-content flex gap-20">
              <!-- 封面图片上传 -->
              <div class="image-upload-container">
                <div class="image-preview-box" @click="triggerCoverImageUpload"
                  :class="{ 'has-image': form.coverImage }">
                  <img v-if="form.coverImage" :src="form.coverImage" alt="封面图片预览" class="preview-image">
                  <div class="upload-overlay">
                    <div class="upload-text">
                      <span class="overlay-icon"><Icon name="camera" /></span>
                      <span>{{ form.coverImage ? '点击更换图片' : '点击上传封面图片' }}</span>
                    </div>
                  </div>
                </div>
                <input ref="coverImageInput" type="file" accept="image/*" @change="handleCoverImageUpload"
                  style="display: none;">
              </div>

              <!-- 缩略图上传 -->
              <div class="image-upload-container">
                <div class="image-preview-box thumbnail-box" @click="triggerThumbnailUpload"
                  :class="{ 'has-image': form.thumbnail }">
                  <img v-if="form.thumbnail" :src="form.thumbnail" alt="缩略图预览" class="preview-image">
                  <div class="upload-overlay">
                    <div class="upload-text">
                      <span class="overlay-icon"><Icon name="image" /></span>
                      <span>{{ form.thumbnail ? '点击更换图片' : '点击上传缩略图' }}</span>
                    </div>
                  </div>
                </div>
                <input ref="thumbnailInput" type="file" accept="image/*" @change="handleThumbnailUpload"
                  style="display: none;">
              </div>
            </div>
          </div>

          <div class="sidebar-item flex flex-ac gap-20 relative">
            <div class="sidebar-title">文章摘要</div>
            <div class="sidebar-content">
              <textarea v-model="form.summary" class="field-textarea" placeholder="请输入文章摘要（可选）" rows="4"
                maxlength="200"></textarea>
              <div class="char-count text-sm text-muted">{{ (form.summary || '').length }}/200</div>
            </div>
          </div>

          <div class="sidebar-item flex flex-ac gap-20">
            <div class="sidebar-title">文章分类</div>
            <div class="sidebar-content">
              <div class="flex gap-8">
                <select v-model="form.categoryId" class="field-select" required style="flex: 1;">
                  <option value="">请选择分类</option>
                  <option
                    v-for="category in categories"
                    :key="category.id"
                    :value="category.id"
                  >
                    {{ category.name }}
                  </option>
                </select>
                <button 
                  type="button" 
                  @click="showCreateCategoryDialog" 
                  class="btn-secondary" 
                  title="创建新分类"
                  style="padding: 8px 12px; min-width: auto;"
                >
                  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <line x1="12" y1="5" x2="12" y2="19"></line>
                    <line x1="5" y1="12" x2="19" y2="12"></line>
                  </svg>
                </button>
              </div>
            </div>
          </div>


          <div class="sidebar-item flex flex-ac gap-20">
            <div class="sidebar-title">发布状态</div>
            <div class="sidebar-content">
              <select v-model="form.status" class="field-select">
                <option value="draft"><Icon name="edit" /> 草稿</option>
                <option value="published"><Icon name="rocket" /> 发布</option>
              </select>
            </div>
          </div>

          <div class="sidebar-item flex flex-ac gap-20">
            <div class="sidebar-title">文章预览</div>
            <div class="sidebar-content">
              <button @click="previewPost" class="btn-secondary w-full" :disabled="!form.title || !form.content">
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z" />
                  <circle cx="12" cy="12" r="3" />
                </svg>
                预览文章
              </button>
            </div>
          </div>


        </div>

      </div>
    </div>
    <!-- 底部工具栏 -->
    <div class="tool bg-soft">
      <div class="toot-content flex flex-ac flex-sb content">
        <div>
          <span @click="goBack" class="link back">退出{{ isEditMode ? '更新' : '发布' }}</span>
        </div>
        <div class="flex gap-12">
          <button @click="saveDraft" class="btn-secondary" :disabled="saving">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M19 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11l5 5v11a2 2 0 0 1-2 2z" />
              <polyline points="17,21 17,13 7,13 7,21" />
              <polyline points="7,3 7,8 15,8" />
            </svg>
            {{ isEditMode ? '更新草稿' : '保存草稿' }}
          </button>
          <button @click="handleSubmit" class="btn-primary"
            :disabled="saving || !form.title || !form.content || !form.categoryId">
            <svg v-if="saving" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor"
              stroke-width="2">
              <path d="M21 12a9 9 0 11-6.219-8.56" />
            </svg>
            <svg v-else width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M12 19l7-7 3 3-7 7-3-3z" />
              <path d="M18 13l-1.5-7.5L2 2l3.5 14.5L13 18l5-5z" />
            </svg>
            {{ saving
              ? (isEditMode ? '更新中...' : '发布中...')
              : (isEditMode ? '更新文章' : '发布文章')
            }}
          </button>
        </div>
      </div>
    </div>

    <!-- 预览模态框 -->
    <div v-if="showPreview" class="preview-modal" @click="closePreview">
      <div class="preview-content" @click.stop>
        <div class="preview-header">
          <h3>文章预览</h3>
          <button @click="closePreview" class="close-btn">×</button>
        </div>
        <div class="preview-body">
          <!-- 文章头部信息 -->
          <header class="preview-post-header">
            <h1 class="preview-title">{{ form.title }}</h1>

            <!-- 封面图片 -->
            <div v-if="form.coverImage" class="preview-cover rounded-lg mb-16">
              <img :src="form.coverImage" :alt="form.title" class="preview-cover-image">
            </div>

            <div class="flex flex-sb flex-ac mb-16 flex-fw gap-12">
              <div class="flex flex-ac gap-8">
                <span class="text-muted font-medium">预览作者</span>
              </div>
              <div class="flex gap-16 flex-ac text-sm text-muted">
                <span v-if="form.categoryId" class="badge">{{ getCategoryName(form.categoryId) }}</span>
                <span>{{ formatDate(new Date().toISOString()) }}</span>
                <span><Icon name="eye" size="14" /> {{ form.viewCount || 0 }}</span>
                <span><Icon name="heart" size="14" /> {{ form.likeCount || 0 }}</span>
                <span><Icon name="message" size="14" /> 0</span>
              </div>
            </div>

            <!-- 标签云 -->
            <div v-if="selectedTags.length > 0" class="tags-cloud">
              <span v-for="tag in selectedTags" :key="tag.id" class="tag">
                {{ tag.name }}
              </span>
            </div>
          </header>

          <!-- 文章摘要 -->
          <div v-if="form.summary" class="preview-summary bg-hover border-l-3 p-20">
            <p class="text-muted">{{ form.summary }}</p>
          </div>

          <!-- 文章内容 -->
          <article class="">
            <div class="markdown-content" v-html="renderedPreviewContent"></div>
          </article>
        </div>
      </div>
    </div>
  </div>

  <!-- 创建分类对话框 -->
  <div v-if="showCreateCategoryDialogVisible" class="modal-overlay" @click="showCreateCategoryDialogVisible = false">
    <div class="modal-content" @click.stop>
      <div class="modal-header">
        <h3>创建新分类</h3>
        <button @click="showCreateCategoryDialogVisible = false" class="close-btn">×</button>
      </div>
      <div class="modal-body">
        <div class="form-group">
          <label>分类名称 *</label>
          <input 
            v-model="newCategoryName" 
            type="text" 
            placeholder="请输入分类名称"
            maxlength="50"
            @keyup.enter="createCategory"
          >
        </div>
        <div class="form-group">
          <label>分类描述</label>
          <textarea 
            v-model="newCategoryDescription" 
            placeholder="请输入分类描述（可选）"
            maxlength="200"
            rows="3"
          ></textarea>
        </div>
      </div>
      <div class="modal-footer">
        <button @click="showCreateCategoryDialogVisible = false" class="btn btn-secondary">取消</button>
        <button @click="createCategory" :disabled="creatingCategory" class="btn btn-primary">
          {{ creatingCategory ? '创建中...' : '创建' }}
        </button>
      </div>
    </div>
  </div>

  <!-- 创建标签对话框 -->
  <div v-if="showCreateTagDialogVisible" class="modal-overlay" @click="showCreateTagDialogVisible = false">
    <div class="modal-content" @click.stop>
      <div class="modal-header">
        <h3>创建新标签</h3>
        <button @click="showCreateTagDialogVisible = false" class="close-btn">×</button>
      </div>
      <div class="modal-body">
        <div class="form-group">
          <label>标签名称 *</label>
          <input 
            v-model="newTagName" 
            type="text" 
            placeholder="请输入标签名称"
            maxlength="30"
            @keyup.enter="createTag"
          >
        </div>
      </div>
      <div class="modal-footer">
        <button @click="showCreateTagDialogVisible = false" class="btn btn-secondary">取消</button>
        <button @click="createTag" :disabled="creatingTag" class="btn btn-primary">
          {{ creatingTag ? '创建中...' : '创建' }}
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import DOMPurify from 'dompurify'
import TinyMCEEditor from '@/components/TinyMCEEditor.vue'
import { PostService, type PostDetail } from '@/services/post'
import { type Tag } from '@/services/tag'
import { CategoryService, type CreateCategoryRequest } from '@/services/category'
import { TagService, type CreateTagRequest } from '@/services/tag'
import { ImageUploadService } from '@/services/utils'
import { useCategoryStore } from '@/stores/category'
import { useTagStore } from '@/stores/tag'
import { useErrorHandler } from '@/composables/useErrorHandler'
import { formatDate } from '@/utils/uitls'
import Swal from 'sweetalert2'

const router = useRouter()
const route = useRoute()
const { handleAsync } = useErrorHandler()

// 表单数据
const form = ref({
  title: '',
  content: '',
  summary: '',
  categoryId: '',
  status: 'published' as 'draft' | 'published',
  coverImage: '',
  thumbnail: '',
  viewCount: 0,
  likeCount: 0
})

// 草稿键，用于关联附件
const draftKey = ref('')

// 生成UUID作为draftKey
const generateDraftKey = () => {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function(c) {
    const r = Math.random() * 16 | 0
    const v = c === 'x' ? r : (r & 0x3 | 0x8)
    return v.toString(16)
  })
}

// Pinia stores
const categoryStore = useCategoryStore()
const tagStore = useTagStore()

// 分类和标签数据
const categories = computed(() => categoryStore.categories)
const tags = computed(() => tagStore.tags)
const selectedTags = ref<Tag[]>([])
const selectedTagId = ref('')

// 状态
const saving = ref(false)
const showPreview = ref(false)
const isEditMode = ref(false)

// 渲染预览内容（处理 Markdown 语法）
const renderedPreviewContent = computed(() => {
  if (!form.value.content) return ''
  const content = form.value.content
  const hasHtmlTags = /<[^>]*>/g.test(content)

  if (hasHtmlTags) {
    // HTML 内容中的 Markdown 语法转换
    const html = content
      .replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
      .replace(/\*(.*?)\*/g, '<em>$1</em>')
      .replace(/`(.*?)`/g, '<code>$1</code>')
    return DOMPurify.sanitize(html)
  } else {
    const html = content
      .replace(/\n/g, '<br>')
      .replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
      .replace(/\*(.*?)\*/g, '<em>$1</em>')
      .replace(/`(.*?)`/g, '<code>$1</code>')
    return DOMPurify.sanitize(html)
  }
})
const editingPostId = ref<number | null>(null)
const loading = ref(false)

// 附件相关状态
const attachments = ref<Array<{
  id: string
  name: string
  size: number
  type: string
  url: string
  resourceId?: number
  attachmentId?: number
  resourceType?: 'file' | 'link'  // 资源类型：file=上传文件，link=外部链接
  externalLink?: string        // 外部链接地址
  purchasedNote?: string       // 购买后显示的说明
  downloadType: number // 0-免费，1-积分
  pointsNeeded: number // 所需积分
  _prevPointsNeeded?: number // 上次积分值（用于失败回滚）
  _updateTimer?: any // 用于防抖更新的定时器句柄
}>>([])
const uploadingAttachment = ref(false)

// 附件类型选择：file=文件上传，link=外部链接
const attachmentType = ref<'file' | 'link'>('file')

// 外部链接表单
const externalLinkForm = ref({
  name: '',
  description: '',
  externalLink: '',
  purchasedNote: '',
  downloadType: 0,
  pointsNeeded: 0
})

// 是否显示外部链接表单
const showExternalLinkForm = ref(false)

// 创建分类和标签相关状态
const showCreateCategoryDialogVisible = ref(false)
const showCreateTagDialogVisible = ref(false)
const creatingCategory = ref(false)
const creatingTag = ref(false)
const newCategoryName = ref('')
const newCategoryDescription = ref('')
const newTagName = ref('')

// 可选标签（排除已选择的）
const availableTags = computed(() => {
  return tags.value.filter(tag =>
    !selectedTags.value.some(selected => selected.id === tag.id)
  )
})

// 获取分类名称
const getCategoryName = (categoryId: string | number) => {
  const category = categories.value.find(c => c.id === Number(categoryId))
  return category?.name || '未分类'
}

// 加载分类列表
const loadCategories = async () => {
  await handleAsync(async () => {
    await categoryStore.fetchCategories()
  }, {
    onError: (err) => {
      console.error('加载分类失败:', err)
      Swal.fire('错误', '加载分类失败，请刷新页面重试', 'error')
    }
  })
}

// 加载标签列表
const loadTags = async () => {
  await handleAsync(async () => {
    await tagStore.fetchTags()
  }, {
    onError: (err) => {
      console.error('加载标签失败:', err)
    }
  })
}

// 添加标签
const addTag = () => {
  if (!selectedTagId.value) return

  const tag = tags.value.find(t => t.id === Number(selectedTagId.value))
  if (tag && !selectedTags.value.some(t => t.id === tag.id)) {
    selectedTags.value.push(tag)
  }
  selectedTagId.value = ''
}

// 移除标签
const removeTag = (tagId: number) => {
  selectedTags.value = selectedTags.value.filter(tag => tag.id !== tagId)
}

// 保存草稿
const saveDraft = async () => {
  if (!form.value.title.trim()) {
    Swal.fire('提示', '请输入文章标题', 'warning')
    return
  }

  const originalStatus = form.value.status
  form.value.status = 'draft'

  await submitPost()

  form.value.status = originalStatus
}

// 预览文章
const previewPost = () => {
  if (!form.value.title || !form.value.content) {
    Swal.fire('提示', '请填写标题和内容后再预览', 'warning')
    return
  }
  showPreview.value = true
}

// 关闭预览
const closePreview = () => {
  showPreview.value = false
}

// 处理表单提交
const handleSubmit = async () => {
  await submitPost()
}

// 提交文章
const submitPost = async () => {
  if (!form.value.title.trim()) {
    Swal.fire('错误', '请输入文章标题', 'error')
    return
  }

  if (!form.value.content.trim()) {
    Swal.fire('错误', '请输入文章内容', 'error')
    return
  }

  if (!form.value.categoryId) {
    Swal.fire('错误', '请选择文章分类', 'error')
    return
  }

  await handleAsync(async () => {
    saving.value = true
    // 提交前统一刷新附件收费设置，确保后端已持久化
    await flushAttachmentMetaUpdates()

    let postId = editingPostId.value
    if (isEditMode.value && editingPostId.value) {
      // 编辑模式：更新文章
      const updateData = {
        id: editingPostId.value,
        title: form.value.title.trim(),
        content: form.value.content,
        summary: form.value.summary?.trim() || '',
        categoryId: Number(form.value.categoryId),
        status: form.value.status,
        tagIds: selectedTags.value.map(tag => tag.id),
        coverImage: form.value.coverImage || '',
        thumbnail: form.value.thumbnail || '',
        draftKey: draftKey.value
      }
      await PostService.updatePost(editingPostId.value, updateData)
    } else {
      // 创建模式：新建文章
      const postData = {
        title: form.value.title.trim(),
        content: form.value.content,
        summary: form.value.summary?.trim() || '',
        categoryId: Number(form.value.categoryId),
        status: form.value.status,
        tagIds: selectedTags.value.map(tag => tag.id),
        coverImage: form.value.coverImage || '',
        thumbnail: form.value.thumbnail || '',
        draftKey: draftKey.value
      }
      const result = await PostService.createPost(postData)
      postId = result.id
    }

    const actionText = isEditMode.value
      ? (form.value.status === 'draft' ? '更新草稿' : '更新文章')
      : (form.value.status === 'draft' ? '保存草稿' : '发布文章')
    await Swal.fire('成功', `${actionText}成功！`, 'success')

    // 跳转到文章详情页
    router.push(`/post/${postId}?from=home`)
  }, {
    onError: (err) => {
      console.error('提交文章失败:', err)
      const actionText = isEditMode.value
        ? (form.value.status === 'draft' ? '更新草稿' : '更新文章')
        : (form.value.status === 'draft' ? '保存草稿' : '发布文章')
      Swal.fire('错误', `${actionText}失败，请重试`, 'error')
    },
    onFinally: () => {
      saving.value = false
    }
  })
}

// 刷新所有附件的收费设置到后端，避免输入未触发change导致未保存
const flushAttachmentMetaUpdates = async () => {
  const list = attachments.value
  for (const att of list) {
    // 取消未执行的定时器，改为立即同步
    if (att._updateTimer) {
      clearTimeout(att._updateTimer)
      att._updateTimer = null
    }
    if (!att.resourceId) continue
    try {
      await PostService.updateAttachmentMeta(
        att.resourceId,
        att.downloadType === 1 ? 1 : 0,
        att.downloadType === 1 ? Math.max(1, Math.floor(Number(att.pointsNeeded || 1))) : 0
      )
    } catch (err) {
      console.error('同步附件收费设置失败:', err)
      // 不阻塞整体提交，仅记录错误；如需更严格可在此抛出
    }
  }
}

// 返回上一页
const goBack = () => {
  if (form.value.title || form.value.content) {
    Swal.fire({
      title: '确认离开？',
      text: '当前编辑的内容将会丢失',
      icon: 'warning',
      showCancelButton: true,
      confirmButtonText: '确认离开',
      cancelButtonText: '继续编辑'
    }).then((result) => {
      if (result.isConfirmed) {
        router.back()
      }
    })
  } else {
    router.back()
  }
}

// 输入时防抖更新积分，避免未触发change导致未保存
const META_UPDATE_DELAY = 500
const onPointsNeededInput = async (attachment: {
  id: string
  resourceId?: number
  downloadType: number
  pointsNeeded: number
  _prevPointsNeeded?: number
  _updateTimer?: any
}) => {
  if (attachment.downloadType !== 1) return

  // 规范化为 >=1 的整数，但不在此处回滚，仅修正展示值
  let val = Number(attachment.pointsNeeded)
  if (!Number.isFinite(val) || val < 1) val = 1
  attachment.pointsNeeded = Math.floor(val)

  if (!attachment.resourceId) return

  // 防抖：取消上次计划
  if (attachment._updateTimer) {
    clearTimeout(attachment._updateTimer)
    attachment._updateTimer = null
  }

  // 计划一次更新
  attachment._updateTimer = setTimeout(async () => {
    try {
      await PostService.updateAttachmentMeta(
        attachment.resourceId!,
        1,
        attachment.pointsNeeded
      )
    } catch (err) {
      console.error('防抖更新附件积分失败:', err)
      // 不在输入过程中弹窗打断，留给提交前统一校验
    } finally {
      attachment._updateTimer = null
    }
  }, META_UPDATE_DELAY)
}

// 加载文章数据（编辑模式）
const loadPostData = async (postId: number) => {
  await handleAsync(async () => {
    loading.value = true
    const postData: PostDetail = await PostService.getPostDetail(postId)

    // 填充表单数据
    form.value = {
      title: postData.title,
      content: postData.content,
      summary: postData.summary || '',
      categoryId: postData.category.id.toString(),
      status: 'published', // 编辑已发布文章时默认保持发布状态
      coverImage: postData.coverImage || '',
      thumbnail: postData.thumbnail || '',
      viewCount: postData.viewCount || 0,
      likeCount: postData.likeCount || 0
    }

    // 设置标签
    if (postData.tags) {
      // 将 TagInfo[] 转换为 Tag[] 类型，添加默认的 postCount
      selectedTags.value = postData.tags.map(tag => ({
        id: tag.id,
        name: tag.name,
        postCount: 0  // 为编辑模式的标签添加默认的文章数量
      }))
    }

    // 加载该文章已有关联的附件（当前用户上传的，可编辑）
    try {
      const existing = await PostService.getPostAttachments(postId)
      // 后端返回字段：attachmentId, resourceId, fileName, fileUrl, resourceType, downloadType, pointsNeeded, createdTime
      attachments.value = (existing as any[]).map(item => ({
        id: (item.resourceId ?? item.attachmentId ?? Date.now()).toString(),
        name: item.fileName || '未命名文件',
        size: item.fileSize ?? 0,
        type: item.resourceType === 'link' ? '外部链接' : (item.type === 'link' ? '外部链接' : '文件'),
        resourceType: item.resourceType ?? (item.type === 'link' ? 'link' : 'file'),
        url: item.fileUrl,
        resourceId: item.resourceId,
        attachmentId: item.attachmentId,
        externalLink: item.externalLink,
        purchasedNote: item.purchasedNote,
        downloadType: Number(item.downloadType ?? 0),
        pointsNeeded: Number(item.pointsNeeded ?? 0)
      }))
    } catch (e) {
      console.error('加载文章附件失败:', e)
      // 不影响编辑表单，附件区域留空
    }
  }, {
    onError: (err) => {
      console.error('加载文章数据失败:', err)
      Swal.fire('错误', '加载文章数据失败，请重试', 'error')
      router.back()
    },
    onFinally: () => {
      loading.value = false
    }
  })
}

// 检查URL参数并设置编辑模式
const checkEditMode = () => {
  const draftParam = route.query.draft
  const editParam = route.query.edit

  if (draftParam && draftParam !== 'true') {
    // 编辑草稿
    isEditMode.value = true
    editingPostId.value = Number(draftParam)
    form.value.status = 'draft'
  } else if (editParam) {
    // 编辑已发布文章
    isEditMode.value = true
    editingPostId.value = Number(editParam)
    form.value.status = 'published'
  } else if (draftParam === 'true') {
    // 新建草稿
    form.value.status = 'draft'
  }
}

// 图片上传相关方法
const coverImageInput = ref<HTMLInputElement>()
const thumbnailInput = ref<HTMLInputElement>()
const attachmentInput = ref<HTMLInputElement>()

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

  await uploadImage(file, 'cover')
}

// 处理缩略图上传
const handleThumbnailUpload = async (event: Event) => {
  const target = event.target as HTMLInputElement
  const file = target.files?.[0]
  if (!file) return

  await uploadImage(file, 'thumbnail')
}

// 上传图片的通用方法
const uploadImage = async (file: File, type: 'cover' | 'thumbnail') => {
  await handleAsync(async () => {
    // 显示上传进度

    try {
      const result = await ImageUploadService.uploadImage(file)

      // 上传成功，更新对应的图片URL
      const imageUrl = result.fileUrl
      if (type === 'cover') {
        form.value.coverImage = imageUrl
      } else {
        form.value.thumbnail = imageUrl
      }

      Swal.close()
      Swal.fire('成功', '图片上传成功！', 'success')
    } catch (error) {
      Swal.close()
      throw error
    }
  }, {
    onError: (err) => {
      console.error('图片上传失败:', err)
      Swal.fire('错误', err.message || '图片上传失败，请重试', 'error')
    }
  })
}

// 附件相关方法
// 触发附件上传
const triggerAttachmentUpload = () => {
  attachmentInput.value?.click()
}

// 处理附件上传
const handleAttachmentUpload = async (event: Event) => {
  const target = event.target as HTMLInputElement
  const files = target.files
  if (!files || files.length === 0) return

  uploadingAttachment.value = true

  try {
    for (let i = 0; i < files.length; i++) {
      const file = files[i]
      await uploadAttachment(file)
    }

    // 清空文件输入
    target.value = ''
  } catch (error) {
    console.error('附件上传失败:', error)
  } finally {
    uploadingAttachment.value = false
  }
}

// 上传单个附件
const uploadAttachment = async (file: File) => {
  await handleAsync(async () => {
    // 默认为免费附件
    const downloadType = 0
    const pointsNeeded = 0
    
    const result = await PostService.uploadAttachment(file, draftKey.value, 'attachment', downloadType, pointsNeeded)

    // 添加到附件列表
    const attachment = {
      id: result.resourceId?.toString() || Date.now().toString(),
      name: file.name,
      size: file.size,
      type: file.type || '未知类型',
      url: result.fileUrl,
      resourceId: result.resourceId,
      attachmentId: result.attachmentId,
      downloadType: downloadType,
      pointsNeeded: pointsNeeded
    }

    attachments.value.push(attachment)

    Swal.fire('成功', `附件 "${file.name}" 上传成功！`, 'success')
  }, {
    onError: (err) => {
      console.error('附件上传失败:', err)
      Swal.fire('错误', `附件 "${file.name}" 上传失败，请重试`, 'error')
    }
  })
}

// 切换附件类型
const switchAttachmentType = (type: 'file' | 'link') => {
  attachmentType.value = type
  showExternalLinkForm.value = (type === 'link')
}

// 创建外部链接资源
const createExternalLinkResource = async () => {
  await handleAsync(async () => {
    // 验证外部链接表单
    if (!externalLinkForm.value.name.trim()) {
      Swal.fire('错误', '请输入资源名称', 'error')
      return
    }

    if (!externalLinkForm.value.externalLink.trim()) {
      Swal.fire('错误', '请输入外部链接地址', 'error')
      return
    }

    const result = await PostService.createExternalLinkResource(
      externalLinkForm.value.name,
      externalLinkForm.value.description,
      externalLinkForm.value.externalLink,
      externalLinkForm.value.purchasedNote,
      draftKey.value,
      'resource',
      externalLinkForm.value.downloadType,
      externalLinkForm.value.pointsNeeded
    )

    // 添加到附件列表
    const attachment = {
      id: result.resourceId?.toString() || Date.now().toString(),
      name: externalLinkForm.value.name,
      size: 0,
      type: '外部链接',
      resourceType: 'link' as const,
      url: externalLinkForm.value.externalLink,
      resourceId: result.resourceId,
      attachmentId: result.attachmentId,
      externalLink: externalLinkForm.value.externalLink,
      purchasedNote: externalLinkForm.value.purchasedNote,
      downloadType: externalLinkForm.value.downloadType,
      pointsNeeded: externalLinkForm.value.pointsNeeded
    }

    attachments.value.push(attachment)

    // 重置表单
    showExternalLinkForm.value = false
    externalLinkForm.value = {
      name: '',
      description: '',
      externalLink: '',
      purchasedNote: '',
      downloadType: 0,
      pointsNeeded: 0
    }

    Swal.fire('成功', '外部链接资源添加成功！', 'success')

  }, {
    onError: (err) => {
      console.error('创建外部链接资源失败:', err)
      Swal.fire('错误', '创建外部链接资源失败，请重试', 'error')
    }
  })
}

// 删除附件
const removeAttachment = async (attachmentId: string) => {
  const result = await Swal.fire({
    title: '确认删除？',
    text: '删除后无法恢复',
    icon: 'warning',
    showCancelButton: true,
    confirmButtonText: '确认删除',
    cancelButtonText: '取消'
  })

  if (result.isConfirmed) {
    await handleAsync(async () => {
      const attachment = attachments.value.find(att => att.id === attachmentId)
      if (attachment && attachment.resourceId) {
        await PostService.deleteAttachment(attachment.resourceId)
      }

      attachments.value = attachments.value.filter(att => att.id !== attachmentId)
      Swal.fire('已删除', '附件已删除', 'success')
    }, {
      onError: (err) => {
        console.error('删除附件失败:', err)
        Swal.fire('错误', '删除附件失败，请重试', 'error')
      }
    })
  }
}

// 当附件下载类型变更时，立即更新后端，失败回滚
const onDownloadTypeChange = async (attachment: {
  id: string
  resourceId?: number
  downloadType: number
  pointsNeeded: number
  _prevPointsNeeded?: number
}) => {
  const newType = attachment.downloadType
  const prevType = newType === 0 ? 1 : 0

  if (!attachment.resourceId) {
    Swal.fire('错误', '资源标识缺失，无法更新附件设置', 'error')
    // 回滚
    attachment.downloadType = prevType
    return
  }

  // 如果改为积分下载且积分未设置，默认设为 1 分
  if (newType === 1 && (!attachment.pointsNeeded || attachment.pointsNeeded < 1)) {
    attachment.pointsNeeded = 1
  }

  try {
    await PostService.updateAttachmentMeta(
      attachment.resourceId,
      newType,
      newType === 1 ? attachment.pointsNeeded : 0
    )
  } catch (err) {
    console.error('更新附件下载类型失败:', err)
    Swal.fire('错误', '更新附件下载类型失败，已为你恢复原值', 'error')
    // 回滚
    attachment.downloadType = prevType
  }
}

// 当积分变更时，立即更新后端，失败回滚
const onPointsNeededChange = async (attachment: {
  id: string
  resourceId?: number
  downloadType: number
  pointsNeeded: number
  _prevPointsNeeded?: number
  _updateTimer?: any
}) => {
  if (attachment.downloadType !== 1) return

  const prevPoints = attachment._prevPointsNeeded ?? attachment.pointsNeeded

  // 规范化数值：>=1 的整数
  let newPoints = Number(attachment.pointsNeeded)
  if (!Number.isFinite(newPoints) || newPoints < 1) {
    newPoints = 1
  }
  newPoints = Math.floor(newPoints)
  attachment.pointsNeeded = newPoints

  if (!attachment.resourceId) {
    Swal.fire('错误', '资源标识缺失，无法更新附件设置', 'error')
    // 回滚
    attachment.pointsNeeded = prevPoints
    return
  }

  try {
    await PostService.updateAttachmentMeta(
      attachment.resourceId,
      1,
      newPoints
    )
  } catch (err) {
    console.error('更新附件积分失败:', err)
    Swal.fire('错误', '更新附件积分失败，已为你恢复原值', 'error')
    // 回滚
    attachment.pointsNeeded = prevPoints
  }
}

// 处理积分输入变化（包装函数，提取事件中的值）
const handlePointsInput = async (attachment: any, event: Event) => {
  const input = event.target as HTMLInputElement
  attachment.pointsNeeded = Number(input.value) || 1
  await onPointsNeededChange(attachment)
}

// 格式化文件大小
const formatFileSize = (bytes: number): string => {
  if (bytes === 0) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i]
}

// 显示创建分类对话框
const showCreateCategoryDialog = () => {
  showCreateCategoryDialogVisible.value = true
  newCategoryName.value = ''
  newCategoryDescription.value = ''
}

// 显示创建标签对话框
const showCreateTagDialog = () => {
  showCreateTagDialogVisible.value = true
  newTagName.value = ''
}

// 创建新分类
const createCategory = async () => {
  if (!newCategoryName.value.trim()) {
    Swal.fire('提示', '请输入分类名称', 'warning')
    return
  }

  await handleAsync(async () => {
    creatingCategory.value = true
    
    const categoryData: CreateCategoryRequest = {
      name: newCategoryName.value.trim(),
      description: newCategoryDescription.value.trim() || undefined
    }
    
    const newCategory = await CategoryService.createCategory(categoryData)
    
    // 刷新分类列表
    await categoryStore.fetchCategories(true)
    
    // 自动选择新创建的分类（添加空值检查）
    if (newCategory && newCategory.id) {
      form.value.categoryId = newCategory.id.toString()
    }
    
    Swal.fire('成功', '分类创建成功！', 'success')
  }, {
    onError: (err) => {
      console.error('创建分类失败:', err)
      Swal.fire('错误', `创建分类失败: ${err.message || '请重试'}`, 'error')
    },
    onFinally: () => {
      // 无论成功还是失败，都要执行清理操作
      creatingCategory.value = false
      // 清空输入框
      newCategoryName.value = ''
      newCategoryDescription.value = ''
      // 关闭对话框
      showCreateCategoryDialogVisible.value = false
    }
  })
}

// 创建新标签
const createTag = async () => {
  if (!newTagName.value.trim()) {
    Swal.fire('提示', '请输入标签名称', 'warning')
    return
  }

  await handleAsync(async () => {
    creatingTag.value = true
    
    const tagData: CreateTagRequest = {
      name: newTagName.value.trim()
    }
    
    const newTag = await TagService.createTag(tagData)
    
    // 刷新标签列表
    await tagStore.fetchTags(true)
    
    // 自动添加新创建的标签到已选择列表（添加空值检查）
    if (newTag && newTag.id) {
      selectedTags.value.push(newTag)
    }
    
    Swal.fire('成功', '标签创建成功！', 'success')
  }, {
    onError: (err) => {
      console.error('创建标签失败:', err)
      Swal.fire('错误', `创建标签失败: ${err.message || '请重试'}`, 'error')
    },
    onFinally: () => {
      // 无论成功还是失败，都要执行清理操作
      creatingTag.value = false
      // 清空输入框
      newTagName.value = ''
      // 关闭对话框
      showCreateTagDialogVisible.value = false
    }
  })
}
 
// 组件挂载时加载数据
onMounted(async () => {
  checkEditMode()

  // 无论是否编辑模式，都生成一个草稿键用于附件关联（编辑模式下用于绑定新上传的附件）
  draftKey.value = generateDraftKey()

  await Promise.all([
    loadCategories(),
    loadTags()
  ])

  // 如果是编辑模式，加载文章数据
  if (isEditMode.value && editingPostId.value) {
    await loadPostData(editingPostId.value)
  }
})
</script>

<style scoped lang="scss">
@use "@/assets/styles/tokens" as *;
/* 编辑器工具栏样式 */
.editor-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 0;
  background: var(--bg-soft);
}

.btn-secondary,
.btn-primary {
  display: flex;
  align-items: center;
  padding: 10px 16px;
  border-radius: 6px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
  border: 1px solid;
  color: var(--text-main);
}

.btn-secondary:hover,
.btn-primary:hover {
  background-color: var(--color-primary);
  color: white;
}


/* 图片上传组件样式 */
.image-upload-container {
  width: 200px;
  height: 150px;
}

.image-preview-box {
  position: relative;
  width: 100%;
  height: 100%;
  border: 2px dashed var(--border-soft);
  border-radius: 8px;
  overflow: hidden;
  cursor: pointer;
  transition: all 0.3s ease;
  background: var(--bg-main);
}

/* 附件上传样式 */
.attachment-upload-area {
  width: 100%;
}

.attachment-list {
  max-height: 300px;
  overflow-y: auto;
  border: 1px solid var(--border-base);
  border-radius: 6px;
  background: var(--bg-main);
}

.attachment-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px;
  border-bottom: 1px solid var(--border-soft);
  transition: background-color 0.2s;
}


.attachment-item:hover {
  background: var(--bg-hover);
}

.attachment-info {
  display: flex;
  align-items: center;
  gap: 12px;
  flex: 1;
  min-width: 0;
}

.attachment-icon {
  font-size: 16px;
  flex-shrink: 0;
}

.attachment-details {
  flex: 1;
  min-width: 0;
}

.attachment-name {
  font-weight: 500;
  color: var(--text-main);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.attachment-meta {
  margin-top: 2px;
}

.attachment-remove {
  background: none;
  border: none;
  color: var(--text-muted);
  cursor: pointer;
  font-size: 18px;
  font-weight: bold;
  padding: 4px 8px;
  border-radius: 4px;
  transition: all 0.2s;
  flex-shrink: 0;
}

.attachment-remove:hover {
  background: var(--color-danger);
  color: white;
}

.image-preview-box:hover {
  color: white;
  background-color: var(--color-primary);
}

.image-preview-box.has-image {
  border-style: solid;
  border-color: var(--color-primary);
}

.preview-image {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform 0.3s ease;
}

.image-preview-box:hover .preview-image {
  transform: scale(1.05);
}

.upload-overlay {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.6);
  backdrop-filter: blur(4px);
  display: flex;
  align-items: center;
  justify-content: center;
  opacity: 0;
  transition: opacity 0.3s ease;
}

.image-preview-box:hover .upload-overlay {
  opacity: 1;
}

.image-preview-box:not(.has-image) .upload-overlay {
  opacity: 1;
  background: rgba(0, 0, 0, 0.1);
  backdrop-filter: none;
}

.upload-text {
  text-align: center;
  color: white;
  font-weight: 500;
}

.image-preview-box:not(.has-image) .upload-text {
  color: var(--text-muted);
}

.overlay-icon {
  display: flex;
  font-size: 24px;
  margin-bottom: 8px;
}

.upload-text span {
  font-size: 14px;
  line-height: 1.4;
}

.btn-primary:disabled,
.btn-secondary:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

/* 编辑器容器 */
.editor-container {
  display: flex;
  flex-direction: column;

}

/* 左侧编辑区 */
.editor-main {
  margin-bottom: 20px;
}

.title-section {
  border-bottom: 1px solid var(--border-soft);
  margin-bottom: 20px;
}

.title-input {
  width: 100%;
  padding: 12px 0;
  border: none;
  outline: none;
  font-size: 24px;
  font-weight: 400;
  color: var(--text-main);
  background: transparent;
}

.title-input::placeholder {
  color: var(--tag-text-color);
}

.content-section {
  flex: 1;
}

/* 右侧设置面板 */
.editor-sidebar {
  width: 100%;
}

.sidebar-tool {
  height: 40px;
}

.sidebar-section {
  padding: 40px;
  border-radius: 8px;
  box-shadow: 0 0px 2px rgba(0, 0, 0, 0.3);
  background-color: var(--bg-card);
}

.sidebar-item {
  margin-bottom: 24px;
}

.sidebar-item>.sidebar-title {
  width: 110px;
}

.sidebar-title {
  font-size: 18px;
  color: var(--text-main);
}

.sidebar-content {
  width: 100%;
}



.field-input,
.field-textarea,
.field-select {
  width: 100%;
  padding: 8px 12px;
  border: 1px solid var(--border-base);
  font-size: 14px;
  background: var(--bg-soft);
  color: var(--text-main);
  transition: all 0.2s;
  outline: none;
  border-radius: 6px;
  cursor: pointer;
  font-family: inherit;
}

.field-input:focus,
.field-textarea:focus,
.field-select:focus {
  outline: none;
  border-color: var(--color-primary);
  box-shadow: 0 0 0 2px rgba(45, 144, 205, 0.15);
}

.field-select:hover {
  border-color: var(--color-primary);
}

.char-count {
  position: absolute;
  right: 0;
  bottom: 0;
  transform: translate(-10px, -10px);
}

// 悬浮工具栏
.tool {
  width: 100%;
  height: 60px;
  position: fixed;
  left: 0;
  bottom: 0;
  z-index: 10;
  box-shadow: var(--shadow-sm);
}

.toot-content {
  height: 100%;
  // background-color: black;
}

// 退出样式
.back:hover {
  color: var(--color-primary);
}




/* 预览模态框 */
.preview-modal {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}

.preview-content {
  background: var(--bg-main);
  border-radius: 12px;
  max-width: 900px;
  max-height: 90vh;
  width: 90%;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.preview-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 20px;
  border-bottom: 1px solid var(--border-soft);
}

.preview-header h3 {
  margin: 0;
  color: var(--text-main);
}

.close-btn {
  background: none;
  border: none;
  font-size: 24px;
  cursor: pointer;
  color: var(--text-main);
}

.preview-body {
  padding: 20px;
  overflow-y: auto;
}

/* 预览文章头部 */
.preview-post-header {
  position: relative;
  padding: 20px;
  border-bottom: 1px solid var(--border-soft);
}

.preview-title {
  font-size: 24px;
  font-weight: 700;
  color: var(--text-main);
  margin: 0 0 16px 0;
  line-height: 1.3;
}

/* 预览封面图片 */
.preview-cover {
  width: 100%;
  height: 200px;
  overflow: hidden;
  border-radius: 8px;
  margin-bottom: 16px;
}

.preview-cover-image {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform 0.3s ease;
}


/* 预览摘要样式 */
.preview-summary {
  margin-bottom: 0;
}

.preview-summary p {
  margin: 0;
  font-style: italic;
}

/* 预览内容样式 - 与详情页同步 */
.markdown-content {
  line-height: 1.7;
  padding: 32px;
  color: var(--text-main);
  font-size: 16px;
  word-wrap: break-word;
  background: var(--bg-main);
  border-radius: 12px;
  margin: 24px 0;

  /* 首段首字母放大 */
  & > p:first-of-type::first-letter {
    font-size: 3em;
    font-weight: 700;
    float: left;
    line-height: 1;
    margin-right: 8px;
    margin-top: 4px;
    color: var(--color-primary);
  }
}

.markdown-content :deep(h1),
.markdown-content :deep(h2),
.markdown-content :deep(h3),
.markdown-content :deep(h4),
.markdown-content :deep(h5),
.markdown-content :deep(h6) {
  color: var(--text-title);
  font-weight: 600;
  margin: 24px 0 16px 0;
  line-height: 1.4;
}

.markdown-content :deep(h1) { font-size: 2em; }
.markdown-content :deep(h2) { font-size: 1.7em; }
.markdown-content :deep(h3) { font-size: 1.4em; }
.markdown-content :deep(h4) { font-size: 1.2em; }
.markdown-content :deep(h5) { font-size: 1.1em; }
.markdown-content :deep(h6) { font-size: 1em; }

.markdown-content :deep(p) {
  margin: 16px 0;
  color: var(--text-main);
}

.markdown-content :deep(a) {
  color: var(--text-link);
  text-decoration: none;
  transition: color 0.2s ease;
}

.markdown-content :deep(a:hover) {
  color: var(--color-primary-dark);
  text-decoration: underline;
}

.markdown-content :deep(ul),
.markdown-content :deep(ol) {
  margin: 16px 0;
  padding-left: 24px;
  color: var(--text-main);
}

.markdown-content :deep(li) {
  margin: 8px 0;
  line-height: 1.6;
}

.markdown-content :deep(blockquote) {
  margin: 24px 0;
  padding: 20px 24px;
  border-left: 4px solid var(--color-primary);
  background: linear-gradient(135deg, var(--bg-soft), var(--bg-hover));
  color: var(--text-subtle);
  font-style: italic;
  border-radius: 0 12px 12px 0;
}

.markdown-content :deep(blockquote p) {
  margin: 0;
}

.markdown-content :deep(code) {
  background: var(--bg-soft);
  padding: 2px 6px;
  border-radius: 4px;
  font-family: 'Fira Code', 'Consolas', monospace;
  font-size: 0.9em;
}

.markdown-content :deep(pre) {
  background: var(--bg-soft);
  padding: 16px;
  border-radius: 8px;
  overflow-x: auto;
  margin: 16px 0;
}

.markdown-content :deep(pre code) {
  background: none;
  padding: 0;
}

.markdown-content :deep(img) {
  max-width: 100%;
  height: auto;
  border-radius: 8px;
  box-shadow: var(--shadow-md);
  margin: 16px 0;
  display: block;
  margin-left: auto;
  margin-right: auto;
}

/* 响应式设计 */
@include respond(md) {
  .create-post {
    padding: 16px;
  }

  .page-header {
    flex-direction: column;
    gap: 16px;
    align-items: flex-start;
  }

  .form-row {
    grid-template-columns: 1fr;
  }

  .form-actions {
    flex-direction: column;
  }

  .preview-content {
    width: 95%;
    max-height: 90vh;
  }
}

/* 弹窗样式 */
.modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 2000;
}

.modal-content {
  background: var(--bg-main);
  border-radius: 12px;
  max-width: 500px;
  width: 90%;
  max-height: 80vh;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  box-shadow: var(--shadow-lg);
  border: 1px solid var(--border-soft);
}

.modal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 20px;
  border-bottom: 1px solid var(--border-soft);
  background: var(--bg-soft);
}

.modal-header h3 {
  margin: 0;
  color: var(--text-title);
  font-size: 16px;
  font-weight: 600;
}

.close-btn {
  background: none;
  border: none;
  font-size: 24px;
  cursor: pointer;
  color: var(--text-subtle);
  padding: 4px;
  border-radius: 4px;
  transition: all 0.2s ease;
}

.close-btn:hover {
  background: var(--bg-hover);
  color: var(--text-main);
}

.modal-body {
  padding: 20px;
  overflow-y: auto;
  background: var(--bg-main);
}

.modal-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  padding: 20px;
  border-top: 1px solid var(--border-soft);
  background: var(--bg-soft);
}

.modal-footer .btn {
  padding: 10px 20px;
  border-radius: 6px;
  border: none;
  cursor: pointer;
  font-size: 14px;
  font-weight: 500;
  transition: all 0.2s ease;
  min-width: 80px;
}

.modal-footer .btn-secondary {
  background: var(--bg-card);
  color: var(--text-main);
  border: 1px solid var(--border-base);
}

.modal-footer .btn-secondary:hover {
  background: var(--bg-hover);
  border-color: var(--border-strong);
}

.modal-footer .btn-primary {
  background: var(--color-primary);
  color: white;
  border: 1px solid var(--color-primary);
}

.modal-footer .btn-primary:hover {
  background: var(--color-primary-dark);
  border-color: var(--color-primary-dark);
}

.modal-footer .btn-primary:disabled {
  background: var(--text-muted);
  border-color: var(--text-muted);
  cursor: not-allowed;
  opacity: 0.6;
}

.form-group {
  margin-bottom: 20px;
}

.form-group:last-child {
  margin-bottom: 0;
}

.form-group label {
  display: block;
  margin-bottom: 8px;
  color: var(--text-main);
  font-weight: 500;
  font-size: 14px;
}

.form-group input,
.form-group textarea {
  width: 100%;
  padding: 12px 16px;
  border: 1px solid var(--border-soft);
  border-radius: 6px;
  background: var(--bg-main);
  color: var(--text-main);
  font-size: 14px;
  transition: all 0.2s ease;
  box-sizing: border-box;
}

.form-group input:focus,
.form-group textarea:focus {
  outline: none;
  border-color: var(--color-primary);
  box-shadow: 0 0 0 2px rgba(95, 145, 173, 0.1);
}

.form-group input:hover,
.form-group textarea:hover {
  border-color: var(--border-base);
}

.form-group textarea {
  resize: vertical;
  min-height: 80px;
  font-family: inherit;
}

/* 附件上传区域辅助样式 */
.border-soft { border-color: var(--border-light); }
.border-dashed { border-style: dashed; border-width: 2px; }
.hover-border-primary:hover { border-color: var(--color-primary); }
.hover-bg-soft:hover { background-color: var(--bg-soft); }
.hover-text-error:hover { color: var(--color-error); }
.bg-primary { background-color: var(--color-primary); }
.bg-soft { background-color: var(--bg-soft); }
.bg-white { background-color: var(--bg-card); }
.bg-main { background-color: var(--bg-main); }
.text-white { color: white; }
.text-subtle { color: var(--text-subtle); }
.text-muted { color: var(--text-muted); }
.text-primary { color: var(--color-primary); }
.text-main { color: var(--text-main); }
.w-full { width: 100%; }
.w-24 { width: 24px; }
.h-24 { height: 24px; }
.w-32 { width: 32px; }
.h-32 { height: 32px; }
.w-40 { width: 40px; }
.w-60 { width: 60px; }
.py-4 { padding-top: 4px; padding-bottom: 4px; }
.py-8 { padding-top: 8px; padding-bottom: 8px; }
.px-8 { padding-left: 8px; padding-right: 8px; }
.px-12 { padding-left: 12px; padding-right: 12px; }
.overflow-hidden { overflow: hidden; }
.cursor-pointer { cursor: pointer; }
.cursor-not-allowed { cursor: not-allowed; }
.opacity-50 { opacity: 0.5; }
.truncate { white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.select-none { user-select: none; }
.scale-75 { transform: scale(0.75); }
.scale-90 { transform: scale(0.9); }
.mr-4 { margin-right: 4px; }
.mx-auto { margin-left: auto; margin-right: auto; }
.border-none { border: none; }
.focus-ring-0:focus { box-shadow: none; outline: none; }
.accent-primary { accent-color: var(--color-primary); }
.shadow-sm { box-shadow: var(--shadow-sm); }
.hover-shadow-sm:hover { box-shadow: var(--shadow-sm); }
.max-h-200 { max-height: 200px; }
.pr-4 { padding-right: 4px; }
.origin-left { transform-origin: left; }
.hover-text-main:hover { color: var(--text-main); }

/* 页面标题头 */
.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 24px;
  margin-bottom: 20px;
  background: var(--bg-card);
  border-radius: 12px;
  border: 1px solid var(--border-light);
}

.page-title {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 18px;
  font-weight: 600;
  color: var(--text-title);
}

/* 快速添加行 */
.upload-row,
.link-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 14px;
  background: var(--bg-soft);
  border: 1px dashed var(--border-base);
  border-radius: 8px;
  margin-bottom: 8px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.upload-row:hover,
.link-row:hover {
  border-color: var(--color-primary);
  background: var(--bg-hover);
}

.upload-icon,
.link-icon {
  width: 36px;
  height: 36px;
  background: var(--bg-card);
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--color-primary);
}

.upload-row .upload-text,
.link-row .link-text {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 2px;
  text-align: left;
}

.upload-row .upload-main,
.link-row .link-main {
  font-size: 14px;
  font-weight: 500;
  color: var(--text-main);
  text-align: left;
}

.upload-row .upload-sub,
.link-row .link-sub {
  font-size: 12px;
  color: var(--text-muted);
  text-align: left;
}

.upload-add,
.link-add {
  color: var(--text-muted);
}

/* 附件列表 */
.attachment-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-top: 12px;
}

/* 操作按钮区域 */
.attach-actions {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}

.attach-action-btn {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 8px 12px;
  background: var(--bg-main);
  border: 1px dashed var(--border-base);
  border-radius: 6px;
  font-size: 13px;
  color: var(--text-muted);
  cursor: pointer;
  transition: all 0.2s ease;
}

.attach-action-btn:hover {
  border-color: var(--color-primary);
  color: var(--color-primary);
  background: var(--bg-hover);
}

.attachment-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  background: var(--bg-main);
  border: 1px solid var(--border-light);
  border-radius: 8px;
  transition: all 0.2s ease;
}

.attachment-item:hover {
  border-color: var(--color-primary);
}

.att-info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.att-name {
  font-size: 14px;
  font-weight: 500;
  color: var(--text-main);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.att-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
}

/* 附件定价切换 */
.att-pricing {
  display: flex;
  align-items: center;
  gap: 4px;
}

.pricing-toggle {
  display: flex;
  align-items: center;
  gap: 2px;
  padding: 2px 6px;
  border-radius: 4px;
  cursor: pointer;
  font-size: 11px;
  color: var(--text-muted);
  transition: all 0.2s;
}

.pricing-toggle input {
  display: none;
}

.pricing-toggle.active {
  background: var(--color-primary);
  color: #fff;
}

.pricing-toggle:hover:not(.active) {
  background: var(--bg-hover);
}

.points-mini-input {
  width: 44px;
  padding: 2px 4px;
  border: 1px solid var(--border-base);
  border-radius: 4px;
  font-size: 11px;
  text-align: center;
  background: var(--bg-main);
}

.points-mini-input:focus {
  outline: none;
  border-color: var(--color-primary);
}

.att-type {
  color: var(--text-muted);
}

.att-points {
  color: var(--color-warning);
  font-weight: 500;
}

.att-free {
  color: var(--text-muted);
}

.att-remove {
  width: 24px;
  height: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: none;
  border: 1px solid var(--border-light);
  color: var(--text-muted);
  cursor: pointer;
  border-radius: 4px;
  font-size: 14px;
  line-height: 1;
  transition: all 0.2s ease;
  flex-shrink: 0;
}

.att-remove:hover {
  background: var(--color-error);
  border-color: var(--color-error);
  color: #fff;
}

/* 外链弹窗 */
.modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 2000;
}

.modal-box {
  background: var(--bg-card);
  border-radius: 12px;
  width: 400px;
  max-width: 90vw;
  box-shadow: var(--shadow-lg);
  overflow: hidden;
}

.modal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  border-bottom: 1px solid var(--border-light);
  font-size: 16px;
  font-weight: 600;
  color: var(--text-title);
}

.modal-close {
  padding: 4px;
  background: none;
  border: none;
  color: var(--text-muted);
  cursor: pointer;
  border-radius: 4px;
  transition: all 0.2s ease;
}

.modal-close:hover {
  background: var(--bg-hover);
  color: var(--text-main);
}

.modal-body {
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.modal-input {
  width: 100%;
  padding: 10px 12px;
  border: 1px solid var(--border-base);
  border-radius: 8px;
  font-size: 14px;
  background: var(--bg-main);
  color: var(--text-main);
  transition: border-color 0.2s ease;
  box-sizing: border-box;
}

.modal-input:focus {
  outline: none;
  border-color: var(--color-primary);
}

.modal-input::placeholder {
  color: var(--text-muted);
}

.pricing-row {
  display: flex;
  align-items: center;
  gap: 12px;
}

.pricing-option {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  background: var(--bg-soft);
  border: 1px solid var(--border-light);
  border-radius: 6px;
  cursor: pointer;
  font-size: 13px;
  color: var(--text-main);
  transition: all 0.2s ease;
}

.pricing-option input {
  display: none;
}

.pricing-option.active {
  background: var(--color-primary);
  border-color: var(--color-primary);
  color: white;
}

.points-input {
  width: 70px;
  padding: 6px 10px;
  border: 1px solid var(--border-base);
  border-radius: 6px;
  font-size: 13px;
  text-align: center;
  background: var(--bg-main);
  color: var(--text-main);
}

.points-input:focus {
  outline: none;
  border-color: var(--color-primary);
}

.modal-footer {
  display: flex;
  gap: 10px;
  padding: 16px 20px;
  border-top: 1px solid var(--border-light);
  justify-content: flex-end;
}

.btn-cancel {
  padding: 8px 16px;
  background: var(--bg-soft);
  border: 1px solid var(--border-base);
  border-radius: 6px;
  font-size: 13px;
  color: var(--text-main);
  cursor: pointer;
  transition: all 0.2s ease;
}

.btn-cancel:hover {
  background: var(--bg-hover);
}

.btn-confirm {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  background: var(--color-primary);
  border: none;
  border-radius: 6px;
  font-size: 13px;
  color: white;
  cursor: pointer;
  transition: all 0.2s ease;
}

.btn-confirm:hover {
  background: var(--color-primary-dark);
}

</style>
