<template>
  <div class="content">
    <!-- 顶部工具栏 -->
    <div class="editor-toolbar">
      <div class="toolbar-left">
        <button @click="goBack" class="btn-icon" title="返回">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M19 12H5M12 19l-7-7 7-7"/>
          </svg>
        </button>
        <div class="editor-title">
          <span class="title-icon">{{ isEditMode ? '✏️' : '📝' }}</span>
          <span>{{ isEditMode ? '编辑文章' : '发布文章' }}</span>
        </div>
      </div>
      <div class="toolbar-right flex gap-8">
        <button @click="saveDraft" class="btn-secondary" :disabled="saving">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M19 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11l5 5v11a2 2 0 0 1-2 2z"/>
            <polyline points="17,21 17,13 7,13 7,21"/>
            <polyline points="7,3 7,8 15,8"/>
          </svg>
          {{ isEditMode ? '更新草稿' : '保存草稿' }}
        </button>
        <button 
          @click="handleSubmit" 
          class="btn-primary" 
          :disabled="saving || !form.title || !form.content || !form.categoryId"
        >
          <svg v-if="saving" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M21 12a9 9 0 11-6.219-8.56"/>
          </svg>
          <svg v-else width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M12 19l7-7 3 3-7 7-3-3z"/>
            <path d="M18 13l-1.5-7.5L2 2l3.5 14.5L13 18l5-5z"/>
          </svg>
          {{ saving 
            ? (isEditMode ? '更新中...' : '发布中...') 
            : (isEditMode ? '更新文章' : '发布文章') 
          }}
        </button>
      </div>
    </div>

    <!-- 编辑器主体 -->
    <div class="editor-container">
      <!-- 左侧编辑区 -->
      <div class="editor-main">
        <!-- 文章标题 -->
        <div class="title-section">
          <input
            v-model="form.title"
            type="text"
            class="title-input"
            placeholder="请输入文章标题..."
            maxlength="100"
          >
          <div class="char-count text-sm text-muted">{{ form.title.length }}/100</div>
        </div>

        <!-- 文章内容编辑器 -->
        <div class="content-section">
          <TinyMCEEditor 
            v-model="form.content"
            :height="600"
            placeholder="开始编写你的文章内容..."
            class="content-editor"
          />
        </div>
      </div>

      <!-- 右侧设置面板 -->
      <div class="editor-sidebar">
        <!-- 发布设置 -->
        <div class="sidebar-section">
          <h3 class="sidebar-title">发布设置</h3>
          
          <div class="form-field">
            <label class="field-label">发布状态</label>
            <select v-model="form.status" class="field-select">
              <option value="draft">📝 草稿</option>
              <option value="published">🚀 发布</option>
            </select>
          </div>

          <div class="form-field">
            <label class="field-label">文章分类 *</label>
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

        <!-- 文章摘要 -->
        <div class="sidebar-section">
          <h3 class="sidebar-title">文章摘要</h3>
          <textarea
            v-model="form.summary"
            class="field-textarea"
            placeholder="请输入文章摘要（可选）"
            rows="4"
            maxlength="200"
          ></textarea>
          <div class="char-count text-sm text-muted">{{ (form.summary || '').length }}/200</div>
        </div>

        <!-- 标签设置 -->
        <div class="sidebar-section">
          <h3 class="sidebar-title">文章标签</h3>
          <div class="tags-section">
            <div v-if="selectedTags.length > 0" class="selected-tags tags-cloud mb-12">
              <span
                v-for="tag in selectedTags"
                :key="tag.id"
                class="tag"
              >
                {{ tag.name }}
                <button
                  type="button"
                  @click="removeTag(tag.id)"
                  class="tag-remove"
                >
                  ×
                </button>
              </span>
            </div>
            <select
              v-model="selectedTagId"
              @change="addTag"
              class="field-select"
            >
              <option value="">选择标签</option>
              <option
                v-for="tag in availableTags"
                :key="tag.id"
                :value="tag.id"
              >
                {{ tag.name }}
              </option>
            </select>
          </div>
        </div>

        <!-- 图片设置 -->
        <div class="sidebar-section">
          <h3 class="sidebar-title">图片设置</h3>
          
          <!-- 封面图片上传 -->
          <div class="form-field">
            <label class="field-label">封面图片</label>
            <div class="image-upload-container">
              <div 
                class="image-preview-box"
                @click="triggerCoverImageUpload"
                :class="{ 'has-image': form.coverImage }"
              >
                <img 
                  v-if="form.coverImage" 
                  :src="form.coverImage" 
                  alt="封面图片预览"
                  class="preview-image"
                >
                <div class="upload-overlay">
                  <div class="upload-text">
                    <i class="upload-icon">📷</i>
                    <span>{{ form.coverImage ? '点击更换图片' : '点击上传封面图片' }}</span>
                  </div>
                </div>
              </div>
              <input 
                ref="coverImageInput"
                type="file" 
                accept="image/*" 
                @change="handleCoverImageUpload"
                style="display: none;"
              >
            </div>
          </div>
          
          <!-- 缩略图上传 -->
          <div class="form-field">
            <label class="field-label">缩略图</label>
            <div class="image-upload-container">
              <div 
                class="image-preview-box thumbnail-box"
                @click="triggerThumbnailUpload"
                :class="{ 'has-image': form.thumbnail }"
              >
                <img 
                  v-if="form.thumbnail" 
                  :src="form.thumbnail" 
                  alt="缩略图预览"
                  class="preview-image"
                >
                <div class="upload-overlay">
                  <div class="upload-text">
                    <i class="upload-icon">🖼️</i>
                    <span>{{ form.thumbnail ? '点击更换图片' : '点击上传缩略图' }}</span>
                  </div>
                </div>
              </div>
              <input 
                ref="thumbnailInput"
                type="file" 
                accept="image/*" 
                @change="handleThumbnailUpload"
                style="display: none;"
              >
            </div>
          </div>
        </div>

        <!-- 预览按钮 -->
        <div class="sidebar-section">
          <button
            @click="previewPost"
            class="btn-outline w-full"
            :disabled="!form.title || !form.content"
          >
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/>
              <circle cx="12" cy="12" r="3"/>
            </svg>
            预览文章
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
              <img 
                :src="form.coverImage" 
                :alt="form.title" 
                class="preview-cover-image"
              >
            </div>
            
            <div class="flex flex-sb flex-ac mb-16 flex-fw gap-12">
              <div class="flex flex-ac gap-8">
                <span class="text-muted font-medium">预览作者</span>
              </div>
              <div class="flex gap-16 flex-ac text-sm text-muted">
                <span v-if="form.categoryId" class="badge">{{ getCategoryName(form.categoryId) }}</span>
                <span>{{ new Date().toLocaleDateString('zh-CN') }}</span>
                <span>👁️ {{ form.viewCount || 0 }}</span>
                <span>❤️ {{ form.likeCount || 0 }}</span>
                <span>💬 0</span>
              </div>
            </div>
            
            <!-- 标签云 -->
            <div v-if="selectedTags.length > 0" class="preview-tags-cloud">
              <span 
                v-for="tag in selectedTags" 
                :key="tag.id" 
                class="preview-tag"
              >
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
        thumbnail: form.value.thumbnail || ''
      }
      result = await PostService.createPost(postData)
    }
    
    const actionText = isEditMode.value 
      ? (form.value.status === 'draft' ? '更新草稿' : '更新文章')
      : (form.value.status === 'draft' ? '保存草稿' : '发布文章')
    await Swal.fire('成功', `${actionText}成功！`, 'success')
    
    // 跳转到文章详情页
    const postId = isEditMode.value ? editingPostId.value : result.id
    router.push(`/post/${postId}`)
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

// 组件挂载时加载数据
onMounted(async () => {
  checkEditMode()
  
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

<style scoped>

/* 编辑器工具栏样式 */
.editor-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 20px;
  background: var(--bg-color);
  border-bottom: 1px solid var(--border-color);
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
}

.toolbar-left {
  display: flex;
  align-items: center;
  gap: 16px;
}

.back-btn {
    display: flex;
    align-items: center;
    gap: 6px;
    padding: 8px 12px;
    background: transparent;
    border: 1px solid var(--border-color);
    border-radius: 6px;
    color: var(--text-color);
    text-decoration: none;
    transition: all 0.2s;
  }
  
  .back-btn:hover {
    background: var(--hover-color);
    border-color: var(--border-color);
  }
  
  .toolbar-title {
    font-size: 16px;
    font-weight: 500;
    color: var(--text-color);
    margin: 0;
  }
 
 .toolbar-actions {
   display: flex;
   align-items: center;
   gap: 12px;
 }

.btn-secondary,
.btn-primary {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  border-radius: 6px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
  border: 1px solid;
}

.btn-secondary {
   background: var(--hover-color);
   border-color: var(--border-color);
   color: var(--text-color);
 }
 
 .btn-secondary:hover {
   background: var(--border-color);
   border-color: var(--border-color);
 }
 
 .btn-primary {
   background: var(--primary-color);
   border-color: var(--primary-color);
   color: #fff;
 }
 
 .btn-primary:hover {
   background: var(--primary-hover-color);
   border-color: var(--primary-hover-color);
 }

.btn-primary:disabled,
.btn-secondary:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

/* 图片上传组件样式 */
.image-upload-container {
  margin-top: 8px;
}

.image-preview-box {
  position: relative;
  width: 100%;
  height: 200px;
  border: 2px dashed var(--border-color);
  border-radius: 8px;
  overflow: hidden;
  cursor: pointer;
  transition: all 0.3s ease;
  background: var(--bg-color);
}

.image-preview-box:hover {
  border-color: var(--primary-color);
  background: var(--hover-color);
}

.image-preview-box.has-image {
  border-style: solid;
  border-color: var(--primary-color);
}

.thumbnail-box {
  height: 120px;
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
  flex: 1;
  display: flex;
  overflow: hidden;
}

/* 左侧编辑区 */
 .editor-main {
   flex: 1;
   display: flex;
   flex-direction: column;
   background: var(--bg-color);
   border-right: 1px solid var(--border-color);
 }
 
 .title-section {
   padding: 20px 0 0 0;
   border-bottom: 1px solid var(--border-color);
   margin-bottom: 20px;
 }
 
 .title-input {
   width: 100%;
   padding: 12px 0;
   border: none;
   outline: none;
   font-size: 24px;
   font-weight: 600;
   color: var(--text-color);
   background: transparent;
 }
 
 .title-input::placeholder {
   color: var(--tag-text-color);
 }

.content-section {
  flex: 1;
}

.content-editor {
  width: 100%;
  height: 100%;
}

/* 右侧设置面板 */
.editor-sidebar {
  width: 280px;
  /* background: var(--hover-color); */
  border-left: 1px solid var(--border-color);
  overflow-y: auto;
  padding-left: 20px;
}

.sidebar-section {
  margin-bottom: 24px;
  padding: 16px;
  background: var(--bg-color);
  border-radius: 8px;
  border: 1px solid var(--border-color);
}

.sidebar-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-color);
  margin: 0 0 12px 0;
}

.form-field {
  margin-bottom: 16px;
}

.form-field:last-child {
  margin-bottom: 0;
}

.field-label {
   display: block;
   margin-bottom: 6px;
   font-size: 12px;
   font-weight: 500;
   color: var(--tag-text-color);
   text-transform: uppercase;
   letter-spacing: 0.5px;
 }
 
 .field-input,
 .field-textarea,
 .field-select {
   width: 100%;
   padding: 8px 12px;
   border: 1px solid var(--border-color);
   border-radius: 6px;
   font-size: 14px;
   background: var(--bg-color);
   color: var(--text-color);
   transition: border-color 0.2s;
 }
 
 .field-input:focus,
 .field-textarea:focus,
 .field-select:focus {
   outline: none;
   border-color: var(--primary-color);
   box-shadow: 0 0 0 2px rgba(64, 158, 255, 0.1);
 }

.field-textarea {
  resize: vertical;
  min-height: 80px;
  font-family: inherit;
}

.tags-section {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.tags-cloud {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.tag {
   display: inline-flex;
   align-items: center;
   gap: 4px;
   padding: 4px 8px;
   background: var(--tag-bg-color);
   color: var(--primary-color);
   border-radius: 12px;
   font-size: 12px;
   font-weight: 500;
 }
 
 .tag-remove {
   background: none;
   border: none;
   color: var(--primary-color);
   cursor: pointer;
   padding: 0;
   margin-left: 4px;
   font-size: 14px;
   line-height: 1;
 }
 
 .tag-remove:hover {
   color: var(--secondary-color);
 }
 
 .btn-outline {
   display: flex;
   align-items: center;
   justify-content: center;
   gap: 8px;
   padding: 10px 16px;
   background: transparent;
   border: 1px solid var(--border-color);
   border-radius: 6px;
   color: var(--text-color);
   font-size: 14px;
   font-weight: 500;
   cursor: pointer;
   transition: all 0.2s;
 }
 
 .btn-outline:hover {
   background: var(--hover-color);
   border-color: var(--border-color);
 }

.btn-outline:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.w-full {
  width: 100%;
}

.flex {
  display: flex;
}

.gap-8 {
  gap: 8px;
}

.mb-12 {
  margin-bottom: 12px;
}

.text-sm {
  font-size: 12px;
}

.text-muted {
    color: var(--tag-text-color);
  }
 
 .char-count {
   text-align: right;
   margin-top: 4px;
 }

/* 响应式设计 */
@media (max-width: 768px) {
  .editor-container {
    flex-direction: column;
  }
  
  .editor-sidebar {
     width: 100%;
     border-left: none;
     border-top: 1px solid var(--border-color);
     max-height: 40vh;
   }
  
  .toolbar-title {
    display: none;
  }
  
  .toolbar-actions {
    gap: 8px;
  }
  
  .btn-secondary,
  .btn-primary {
    padding: 6px 12px;
    font-size: 12px;
  }
  
  .title-input {
    font-size: 20px;
  }
  
  .sidebar-section {
    padding: 12px;
    margin-bottom: 16px;
  }
}

@media (max-width: 480px) {
  .editor-toolbar {
    padding: 8px 12px;
  }
  
  .title-section {
    padding: 16px;
  }
  
  .content-section {
    padding: 16px;
  }
  
  .editor-sidebar {
    padding: 12px;
  }
  
  .btn-secondary span,
  .btn-primary span {
    display: none;
  }
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
  background: var(--bg-color);
  border-radius: 12px;
  max-width: 800px;
  max-height: 80vh;
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
  border-bottom: 1px solid var(--border-color);
}

.preview-header h3 {
  margin: 0;
  color: var(--text-color);
}

.close-btn {
  background: none;
  border: none;
  font-size: 24px;
  cursor: pointer;
  color: var(--text-color);
}

.preview-body {
  padding: 20px;
  overflow-y: auto;
}

/* 预览文章头部 */
.preview-post-header {
  position: relative;
  padding: 20px;
  border-bottom: 1px solid var(--border-color);
}

.preview-title {
  font-size: 1.8rem;
  font-weight: 700;
  color: var(--text-color);
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

/* 预览标签云 */
.preview-tags-cloud {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 12px;
}

.preview-tag {
  display: inline-flex;
  align-items: center;
  padding: 4px 8px;
  background: var(--tag-bg-color);
  color: var(--primary-color);
  border-radius: 12px;
  font-size: 12px;
  font-weight: 500;
  text-decoration: none;
  transition: all 0.2s ease;
}

.preview-tag:hover {
  background: var(--primary-color);
  color: white;
  transform: translateY(-1px);
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
  color: var(--text-color);
  line-height: 1.6;
}

/* 通用样式类 */
.flex {
  display: flex;
}

.flex-sb {
  justify-content: space-between;
}

.flex-ac {
  align-items: center;
}

.flex-fw {
  flex-wrap: wrap;
}

.gap-12 {
  gap: 12px;
}

.gap-16 {
  gap: 16px;
}

.mb-16 {
  margin-bottom: 16px;
}

.p-20 {
  padding: 20px;
}

.bg-hover {
  background: var(--hover-color);
}

.border-l-3 {
  border-left: 3px solid var(--primary-color);
}

.badge {
  background: var(--primary-color);
  color: white;
  padding: 2px 8px;
  border-radius: 8px;
  font-size: 12px;
  font-weight: 500;
}

.font-medium {
  font-weight: 500;
}

.rounded-lg {
  border-radius: 8px;
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