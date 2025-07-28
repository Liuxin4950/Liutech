<template>
  <div class="create-post">
    <div class="page-header">
      <h1 class="page-title">
        {{ isEditMode ? '✏️ 编辑文章' : '📝 发布文章' }}
      </h1>
      <div class="header-actions">
        <button @click="saveDraft" class="draft-btn" :disabled="saving">
          💾 {{ isEditMode ? '更新草稿' : '保存草稿' }}
        </button>
        <button @click="goBack" class="back-btn">
          ← 返回
        </button>
      </div>
    </div>

    <form @submit.prevent="submitPost" class="post-form">
      <!-- 基本信息 -->
      <div class="form-section">
        <h2 class="section-title">基本信息</h2>
        
        <div class="form-group">
          <label for="title" class="form-label">文章标题 *</label>
          <input
            id="title"
            v-model="form.title"
            type="text"
            class="form-input"
            placeholder="请输入文章标题"
            required
            maxlength="100"
          >
          <div class="char-count">{{ form.title.length }}/100</div>
        </div>

        <div class="form-group">
          <label for="summary" class="form-label">文章摘要</label>
          <textarea
            id="summary"
            v-model="form.summary"
            class="form-textarea"
            placeholder="请输入文章摘要（可选）"
            rows="3"
            maxlength="200"
          ></textarea>
          <div class="char-count">{{ (form.summary || '').length }}/200</div>
        </div>

        <div class="form-row">
          <div class="form-group">
            <label for="category" class="form-label">文章分类 *</label>
            <select
              id="category"
              v-model="form.categoryId"
              class="form-select"
              required
            >
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

          <div class="form-group">
            <label for="status" class="form-label">发布状态</label>
            <select
              id="status"
              v-model="form.status"
              class="form-select"
            >
              <option value="draft">草稿</option>
              <option value="published">发布</option>
            </select>
          </div>
        </div>

        <div class="form-group">
          <label class="form-label">文章标签</label>
          <div class="tags-input">
            <div class="selected-tags">
              <span
                v-for="tag in selectedTags"
                :key="tag.id"
                class="tag-item"
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
              class="tag-select"
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
      </div>

      <!-- 文章内容 -->
      <div class="form-section">
        <h2 class="section-title">文章内容</h2>
        <div class="form-group">
          <TinyMCEEditor style="width: 100%; height: 500px;"
            v-model="form.content"
            :height="500"
            placeholder="开始编写你的文章内容..."
          />
        </div>
      </div>

      <!-- 提交按钮 -->
      <div class="form-actions">
        <button
          type="button"
          @click="previewPost"
          class="preview-btn"
          :disabled="!form.title || !form.content"
        >
          👁️ 预览
        </button>
        <button
          type="submit"
          class="submit-btn"
          :disabled="saving || !form.title || !form.content || !form.categoryId"
        >
          {{ saving 
            ? (isEditMode ? '更新中...' : '发布中...') 
            : (isEditMode ? '💾 更新文章' : '🚀 发布文章') 
          }}
        </button>
      </div>
    </form>

    <!-- 预览模态框 -->
    <div v-if="showPreview" class="preview-modal" @click="closePreview">
      <div class="preview-content" @click.stop>
        <div class="preview-header">
          <h3>文章预览</h3>
          <button @click="closePreview" class="close-btn">×</button>
        </div>
        <div class="preview-body">
          <h1 class="preview-title">{{ form.title }}</h1>
          <div class="preview-meta">
            <span class="preview-category">{{ getCategoryName(form.categoryId) }}</span>
            <span class="preview-date">{{ new Date().toLocaleDateString('zh-CN') }}</span>
          </div>
          <div v-if="form.summary" class="preview-summary">
            {{ form.summary }}
          </div>
          <div class="preview-content-body" v-html="form.content"></div>
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
  status: 'published' as 'draft' | 'published'
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
        tagIds: selectedTags.value.map(tag => tag.id)
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
        tagIds: selectedTags.value.map(tag => tag.id)
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
      status: 'published' // 编辑已发布文章时默认保持发布状态
    }
    
    // 设置标签
    if (postData.tags) {
      selectedTags.value = postData.tags
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
.create-post {
  max-width: 1000px;
  margin: 0 auto;
  padding: 20px;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 30px;
  padding-bottom: 20px;
  border-bottom: 2px solid var(--border-color);
}

.page-title {
  font-size: 2rem;
  font-weight: 700;
  color: var(--text-color);
  margin: 0;
}

.header-actions {
  display: flex;
  gap: 12px;
}

.draft-btn, .back-btn {
  padding: 8px 16px;
  border: 1px solid var(--border-color);
  border-radius: 6px;
  background: var(--bg-color);
  color: var(--text-color);
  cursor: pointer;
  transition: all 0.3s;
}

.draft-btn:hover, .back-btn:hover {
  background: var(--hover-color);
}

.draft-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.post-form {
  display: flex;
  flex-direction: column;
  gap: 30px;
}

.form-section {
  background: var(--bg-color);
  border: 1px solid var(--border-color);
  border-radius: 12px;
  padding: 24px;
}

.section-title {
  font-size: 1.3rem;
  font-weight: 600;
  color: var(--text-color);
  margin: 0 0 20px 0;
  padding-bottom: 8px;
  border-bottom: 1px solid var(--border-color);
}

.form-group {
  margin-bottom: 20px;
}

.form-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 20px;
}

.form-label {
  display: block;
  font-weight: 500;
  color: var(--text-color);
  margin-bottom: 8px;
}

.form-input, .form-textarea, .form-select {
  width: 100%;
  padding: 12px;
  border: 1px solid var(--border-color);
  border-radius: 6px;
  background: var(--bg-color);
  color: var(--text-color);
  font-size: 14px;
  transition: border-color 0.3s;
}

.form-input:focus, .form-textarea:focus, .form-select:focus {
  outline: none;
  border-color: var(--primary-color);
}

.form-textarea {
  resize: vertical;
  min-height: 80px;
}

.char-count {
  text-align: right;
  font-size: 12px;
  color: var(--text-color);
  opacity: 0.6;
  margin-top: 4px;
}

.tags-input {
  border: 1px solid var(--border-color);
  border-radius: 6px;
  padding: 8px;
  background: var(--bg-color);
}

.selected-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 8px;
}

.tag-item {
  display: flex;
  align-items: center;
  gap: 4px;
  background: var(--primary-color);
  color: white;
  padding: 4px 8px;
  border-radius: 12px;
  font-size: 12px;
}

.tag-remove {
  background: none;
  border: none;
  color: white;
  cursor: pointer;
  font-size: 14px;
  line-height: 1;
}

.tag-select {
  border: none;
  background: transparent;
  color: var(--text-color);
  font-size: 14px;
}

.form-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  padding: 20px 0;
}

.preview-btn, .submit-btn {
  padding: 12px 24px;
  border: none;
  border-radius: 6px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.3s;
}

.preview-btn {
  background: var(--hover-color);
  color: var(--text-color);
}

.submit-btn {
  background: var(--primary-color);
  color: white;
}

.preview-btn:hover {
  background: var(--border-color);
}

.submit-btn:hover {
  background: var(--secondary-color);
}

.preview-btn:disabled, .submit-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
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

.preview-title {
  font-size: 1.8rem;
  font-weight: 700;
  color: var(--text-color);
  margin: 0 0 12px 0;
}

.preview-meta {
  display: flex;
  gap: 16px;
  margin-bottom: 16px;
  font-size: 14px;
  color: var(--text-color);
  opacity: 0.7;
}

.preview-category {
  background: var(--primary-color);
  color: white;
  padding: 2px 8px;
  border-radius: 8px;
  font-size: 12px;
}

.preview-summary {
  background: var(--hover-color);
  padding: 12px;
  border-radius: 6px;
  margin-bottom: 16px;
  font-style: italic;
  color: var(--text-color);
  opacity: 0.8;
}

.preview-content-body {
  color: var(--text-color);
  line-height: 1.6;
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