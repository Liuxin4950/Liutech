<template>
  <div class="content">
    <!-- 编辑器主体 -->
    <div class="editor-container bg-card p-20">
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
      <div class="editor-sidebar">
        <!-- 发布设置 -->
        <div class="sidebar-section">
          <!-- 附件上传区域 -->
          <div class="sidebar-item flex flex-ac gap-20">
            <div class="sidebar-title">文章附件</div>
            <div class="sidebar-content">
              <!-- 附件上传按钮 -->
              <div class="attachment-upload-area">
                <button @click="triggerAttachmentUpload" class="btn-secondary w-full mb-12" :disabled="uploadingAttachment">
                  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M21.44 11.05l-9.19 9.19a6 6 0 0 1-8.49-8.49l9.19-9.19a4 4 0 0 1 5.66 5.66L9.64 16.2a2 2 0 0 1-2.83-2.83l8.49-8.48"/>
                  </svg>
                  {{ uploadingAttachment ? '上传中...' : '上传附件' }}
                </button>
                <input ref="attachmentInput" type="file" @change="handleAttachmentUpload" style="display: none;" multiple>
              </div>

              <!-- 附件列表 -->
              <div v-if="attachments.length > 0" class="attachment-list">
                <div v-for="attachment in attachments" :key="attachment.id" class="attachment-item">
                  <div class="attachment-info">
                    <div class="attachment-icon">📎</div>
                    <div class="attachment-details">
                      <div class="attachment-name">{{ attachment.name }}</div>
                      <div class="attachment-meta text-sm text-muted">
                        {{ formatFileSize(attachment.size) }} • {{ attachment.type }}
                      </div>
                      <!-- 收费设置 -->
                      <div class="attachment-pricing mt-8">
                        <div class="flex flex-ac gap-12">
                          <label class="flex flex-ac gap-4">
                            <input 
                              type="radio" 
                              :name="`downloadType_${attachment.id}`" 
                              :value="0" 
                              v-model="attachment.downloadType"
                              @change="onDownloadTypeChange(attachment)"
                            >
                            <span class="text-sm">免费</span>
                          </label>
                          <label class="flex flex-ac gap-4">
                            <input 
                              type="radio" 
                              :name="`downloadType_${attachment.id}`" 
                              :value="1" 
                              v-model="attachment.downloadType"
                              @change="onDownloadTypeChange(attachment)"
                            >
                            <span class="text-sm">积分</span>
                          </label>
                        </div>
                        <div v-if="attachment.downloadType === 1" class="mt-8">
                          <input 
                            type="number" 
                            v-model.number="attachment.pointsNeeded" 
                            placeholder="所需积分" 
                            min="1" 
                            class="field-input text-sm" 
                            style="width: 100px;"
                            @focus="attachment._prevPointsNeeded = attachment.pointsNeeded"
                            @change="onPointsNeededChange(attachment)"
                          >
                          <span class="text-sm text-muted ml-4">积分</span>
                        </div>
                      </div>
                    </div>
                  </div>
                  <button @click="removeAttachment(attachment.id)" class="attachment-remove" title="删除附件">
                    ×
                  </button>
                </div>
              </div>

              <div v-if="attachments.length === 0" class="text-sm text-muted">
                暂无附件
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
              <select v-model="selectedTagId" @change="addTag" class="field-select">
                <option value="">选择标签</option>
                <option v-for="tag in availableTags" :key="tag.id" :value="tag.id">
                  {{ tag.name }}
                </option>
              </select>
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
                      <i class="upload-icon">📷</i>
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
                      <i class="upload-icon">🖼️</i>
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
              <select v-model="form.categoryId" class="field-select" required>
              <option value="">请选择分类</option>
              <option
                v-for="category in categories"
                :key="category.id"
                :value="category.id"
              >
                {{ category.name }}
              </option>
            </select>
            </div>
          </div>


          <div class="sidebar-item flex flex-ac gap-20">
            <div class="sidebar-title">发布状态</div>
            <div class="sidebar-content">
              <select v-model="form.status" class="field-select">
                <option value="draft">📝 草稿</option>
                <option value="published">🚀 发布</option>
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
                <span>👁️ {{ form.viewCount || 0 }}</span>
                <span>❤️ {{ form.likeCount || 0 }}</span>
                <span>💬 0</span>
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
          <article class="p-20">
            <div class="markdown-content" v-html="form.content"></div>
          </article>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import TinyMCEEditor from '@/components/TinyMCEEditor.vue'
import { PostService, type PostDetail } from '@/services/post'
import { type Tag } from '@/services/tag'
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
  downloadType: number // 0-免费，1-积分
  pointsNeeded: number // 所需积分
  _prevPointsNeeded?: number // 上次积分值（用于失败回滚）
}>>([])
const uploadingAttachment = ref(false)

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

    let result
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
        thumbnail: form.value.thumbnail || ''
      }
      result = await PostService.updatePost(editingPostId.value, updateData)
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
      result = await PostService.createPost(postData)
    }

    const actionText = isEditMode.value
      ? (form.value.status === 'draft' ? '更新草稿' : '更新文章')
      : (form.value.status === 'draft' ? '保存草稿' : '发布文章')
    await Swal.fire('成功', `${actionText}成功！`, 'success')

    // 跳转到文章详情页
    const postId = isEditMode.value ? editingPostId.value : result.id
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

// 格式化文件大小
const formatFileSize = (bytes: number): string => {
  if (bytes === 0) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i]
}
 
// 组件挂载时加载数据
onMounted(async () => {
  checkEditMode()
 
  // 如果不是编辑模式，生成新的 draftKey
  if (!isEditMode.value) {
    draftKey.value = generateDraftKey()
    console.log('生成草稿键:', draftKey.value)
  }
 
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
  max-height: 200px;
  overflow-y: auto;
  border: 1px solid var(--border-soft);
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

.attachment-item:last-child {
  border-bottom: none;
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

.upload-icon {
  display: block;
  font-size: 2rem;
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
  background: var(--bg-main);
  border-radius: 8px;
  border: 1px solid var(--border-soft);
}

.sidebar-item {
  margin-bottom: 24px;
}

.sidebar-item>.sidebar-title {
  width: 80px;
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
  border: 1px solid var(--border-soft);
  font-size: 14px;
  background: var(--bg-soft);
  color: var(--text-main);
  transition: border-color 0.2s;
  outline: none;
}

.field-input:focus,
.field-textarea:focus,
.field-select:focus {
  outline: none;
  border-color: var(--primary-color);
  box-shadow: 0 0 0 2px rgba(64, 158, 255, 0.1);
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
  z-index: 99;
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
  font-size: 1.8rem;
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

/* 预览内容样式 */
.markdown-content {
  color: var(--text-main);
}

/* 响应式设计 */
@media (max-width: 768px) {
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
</style>
