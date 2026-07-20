import { ref, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import DOMPurify from 'dompurify'
import type { AdminArticleDraftSnapshot, FieldUpdatePayload } from '@/services/adminAgent'
import { PostService, type PostDetail } from '@/services/post'
import type { Tag } from '@/services/tag'
import { CategoryService, type CreateCategoryRequest } from '@/services/category'
import { TagService, type CreateTagRequest } from '@/services/tag'
import { SeriesService, type PostSeries } from '@/services/series'
import { ImageUploadService } from '@/services/utils'
import { useCategoryStore } from '@/stores/category'
import { useTagStore } from '@/stores/tag'
import { useUserStore } from '@/stores/user'
import { useErrorHandler } from '@/composables/useErrorHandler'
import { useMarkdown } from '@/composables/useMarkdown'
import Swal from 'sweetalert2'

/** 判断值非 undefined 且非 null（用于可选字段更新前的守卫） */
const isPresent = <T>(value: T | undefined | null): value is T =>
  value !== undefined && value !== null

export interface AttachmentItem {
  id: string
  name: string
  size: number
  type: string
  url: string
  resourceId?: number
  attachmentId?: number
  resourceType?: 'file' | 'link'
  externalLink?: string
  purchasedNote?: string
  downloadType: number
  pointsNeeded: number
  _prevPointsNeeded?: number
  _updateTimer?: any
}

export function usePostEditor() {
  const router = useRouter()
  const route = useRoute()
  const { handleAsync } = useErrorHandler()
  const { processMarkdown } = useMarkdown()
  const userStore = useUserStore()

  // 表单数据
  const form = ref({
    title: '',
    content: '',
    summary: '',
    categoryId: '',
    seriesId: '',
    seriesSort: 0,
    status: 'published' as 'draft' | 'published',
    coverImage: '',
    thumbnail: '',
    viewCount: 0,
    likeCount: 0
  })

  const draftKey = ref('')
  const generateDraftKey = () => {
    // 优先用原生 UUID v4；非安全上下文（http 非 localhost）回退到时间戳+随机串
    if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
      return crypto.randomUUID()
    }
    return `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 10)}`
  }

  // Pinia stores
  const categoryStore = useCategoryStore()
  const tagStore = useTagStore()

  const categories = computed(() => categoryStore.categories)
  const tags = computed(() => tagStore.tags)
  const seriesList = ref<PostSeries[]>([])
  const selectedTags = ref<Tag[]>([])
  const selectedTagId = ref('')

  const loadSeries = async () => {
    try {
      const data = await SeriesService.getSeriesList()
      seriesList.value = data || []
    } catch {
      seriesList.value = []
    }
  }

  // 状态
  const saving = ref(false)
  const showPreview = ref(false)
  const isEditMode = ref(false)
  const editingPostId = ref<number | null>(null)
  const loading = ref(false)

  const renderedPreviewContent = computed(() => {
    if (!form.value.content) return ''
    return processMarkdown(form.value.content)
  })

  // 附件
  const attachments = ref<AttachmentItem[]>([])
  const uploadingAttachment = ref(false)
  const attachmentType = ref<'file' | 'link'>('file')
  const externalLinkForm = ref({
    name: '',
    description: '',
    externalLink: '',
    purchasedNote: '',
    downloadType: 0,
    pointsNeeded: 0
  })
  const showExternalLinkForm = ref(false)

  // 创建分类/标签对话框
  const showCreateCategoryDialogVisible = ref(false)
  const showCreateTagDialogVisible = ref(false)
  const showCreateSeriesDialogVisible = ref(false)
  const creatingCategory = ref(false)
  const creatingTag = ref(false)
  const creatingSeries = ref(false)
  const newCategoryName = ref('')
  const newCategoryDescription = ref('')
  const newTagName = ref('')
  const newSeriesName = ref('')
  const newSeriesDescription = ref('')
  const aiSuggestedCategoryName = ref('')
  const aiSuggestedTagNames = ref<string[]>([])
  const creatingAiSuggestion = ref('')

  const availableTags = computed(() => {
    return tags.value.filter(tag =>
      !selectedTags.value.some(selected => selected.id === tag.id)
    )
  })

  const hasAiTaxonomySuggestions = computed(() =>
    !!aiSuggestedCategoryName.value || aiSuggestedTagNames.value.length > 0
  )

  const isAdminWritingAvailable = computed(() => userStore.isAdmin)

  const adminDraftSnapshot = computed<AdminArticleDraftSnapshot>(() => ({
    postId: editingPostId.value,
    title: form.value.title,
    content: form.value.content,
    summary: form.value.summary,
    categoryId: form.value.categoryId || undefined,
    tagIds: selectedTags.value.map(tag => tag.id),
    status: form.value.status,
    coverImage: form.value.coverImage,
    thumbnail: form.value.thumbnail
  }))

  // AI 字段回退栈
  const undoStack = ref<Array<{ field: string, oldValue: any }>>([])
  const fieldLabels: Record<string, string> = {
    title: '标题',
    summary: '摘要',
    content: '正文',
    categoryId: '分类',
    tagIds: '标签'
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
      const existing = categories.value.find(category => sameName(category.name, suggestedCategory))
      if (existing) {
        form.value.categoryId = String(existing.id)
        aiSuggestedCategoryName.value = ''
      } else {
        aiSuggestedCategoryName.value = suggestedCategory
      }
    }

    if (payload.suggestedTagNames?.length) {
      const nextSuggestions: string[] = []
      for (const name of normalizeSuggestedNames(payload.suggestedTagNames)) {
        const existing = tags.value.find(tag => sameName(tag.name, name))
        if (existing) {
          if (!selectedTags.value.some(tag => tag.id === existing.id)) {
            selectedTags.value.push(existing)
          }
        } else if (!nextSuggestions.some(item => sameName(item, name))) {
          nextSuggestions.push(name)
        }
      }
      aiSuggestedTagNames.value = nextSuggestions
    }
  }

  const handleFieldUpdate = (payload: FieldUpdatePayload) => {
    const nextUndoStack: Array<{ field: string, oldValue: any }> = []
    if (isPresent(payload.title)) {
      nextUndoStack.push({ field: 'title', oldValue: form.value.title })
      form.value.title = payload.title
    }
    if (isPresent(payload.summary)) {
      nextUndoStack.push({ field: 'summary', oldValue: form.value.summary })
      form.value.summary = payload.summary
    }
    if (isPresent(payload.contentHtml)) {
      nextUndoStack.push({ field: 'content', oldValue: form.value.content })
      form.value.content = DOMPurify.sanitize(payload.contentHtml)
    }
    if (isPresent(payload.categoryId)) {
      nextUndoStack.push({ field: 'categoryId', oldValue: form.value.categoryId })
      form.value.categoryId = String(payload.categoryId)
    }
    if (payload.tagIds?.length) {
      nextUndoStack.push({ field: 'tagIds', oldValue: [...selectedTags.value] })
      const nextTags = tags.value.filter(tag => payload.tagIds?.includes(tag.id))
      if (nextTags.length) {
        selectedTags.value = nextTags
      }
      aiSuggestedTagNames.value = aiSuggestedTagNames.value.filter(name =>
        !nextTags.some(tag => sameName(tag.name, name))
      )
    }
    rememberAiTaxonomySuggestions(payload)
    if (nextUndoStack.length) {
      undoStack.value = nextUndoStack
    }
  }

  const undoField = (field: string) => {
    let index = -1
    for (let i = undoStack.value.length - 1; i >= 0; i--) {
      if (undoStack.value[i].field === field) { index = i; break }
    }
    if (index < 0) return

    const entry = undoStack.value[index]
    switch (entry.field) {
      case 'title': form.value.title = entry.oldValue; break
      case 'summary': form.value.summary = entry.oldValue; break
      case 'content': form.value.content = entry.oldValue; break
      case 'categoryId': form.value.categoryId = entry.oldValue; break
      case 'tagIds': selectedTags.value = entry.oldValue; break
    }
    undoStack.value = undoStack.value.filter(e => e.field !== field)
  }

  const getCategoryName = (categoryId: string | number) => {
    const category = categories.value.find(c => c.id === Number(categoryId))
    return category?.name || '未分类'
  }

  const loadCategories = async () => {
    await handleAsync(async () => {
      await categoryStore.fetchCategories()
    }, {
      onError: () => {
        Swal.fire('错误', '加载分类失败，请刷新页面重试', 'error')
      }
    })
  }

  const loadTags = async () => {
    await handleAsync(async () => {
      await tagStore.fetchTags()
    }, {
      onError: () => {}
    })
  }

  const addTag = () => {
    if (!selectedTagId.value) return
    const tag = tags.value.find(t => t.id === Number(selectedTagId.value))
    if (tag && !selectedTags.value.some(t => t.id === tag.id)) {
      selectedTags.value.push(tag)
    }
    selectedTagId.value = ''
  }

  const removeTag = (tagId: number) => {
    selectedTags.value = selectedTags.value.filter(tag => tag.id !== tagId)
  }

  // 图片上传
  const coverImageInput = ref<HTMLInputElement>()
  const thumbnailInput = ref<HTMLInputElement>()
  const attachmentInput = ref<HTMLInputElement>()

  const triggerCoverImageUpload = () => { coverImageInput.value?.click() }
  const triggerThumbnailUpload = () => { thumbnailInput.value?.click() }

  const handleCoverImageUpload = async (event: Event) => {
    const target = event.target as HTMLInputElement
    const file = target.files?.[0]
    if (!file) return
    await uploadImage(file, 'cover')
  }

  const handleThumbnailUpload = async (event: Event) => {
    const target = event.target as HTMLInputElement
    const file = target.files?.[0]
    if (!file) return
    await uploadImage(file, 'thumbnail')
  }

  const uploadImage = async (file: File, type: 'cover' | 'thumbnail') => {
    await handleAsync(async () => {
      try {
        const result = await ImageUploadService.uploadImage(file)
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
        Swal.fire('错误', err.message || '图片上传失败，请重试', 'error')
      }
    })
  }

  // 附件管理
  const triggerAttachmentUpload = () => { attachmentInput.value?.click() }

  const handleAttachmentUpload = async (event: Event) => {
    const target = event.target as HTMLInputElement
    const files = target.files
    if (!files || files.length === 0) return
    uploadingAttachment.value = true
    try {
      for (let i = 0; i < files.length; i++) {
        await uploadAttachment(files[i])
      }
      target.value = ''
    } catch {} finally {
      uploadingAttachment.value = false
    }
  }

  const uploadAttachment = async (file: File) => {
    await handleAsync(async () => {
      const downloadType = 0
      const pointsNeeded = 0
      const result = await PostService.uploadAttachment(file, draftKey.value, 'attachment', downloadType, pointsNeeded)
      attachments.value.push({
        id: result.resourceId?.toString() || Date.now().toString(),
        name: file.name,
        size: file.size,
        type: file.type || '未知类型',
        url: result.fileUrl,
        resourceId: result.resourceId,
        attachmentId: result.attachmentId,
        downloadType,
        pointsNeeded
      })
      Swal.fire('成功', `附件 "${file.name}" 上传成功！`, 'success')
    }, {
      onError: () => {
        Swal.fire('错误', `附件 "${file.name}" 上传失败，请重试`, 'error')
      }
    })
  }

  const switchAttachmentType = (type: 'file' | 'link') => {
    attachmentType.value = type
    showExternalLinkForm.value = (type === 'link')
  }

  const createExternalLinkResource = async () => {
    await handleAsync(async () => {
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
      attachments.value.push({
        id: result.resourceId?.toString() || Date.now().toString(),
        name: externalLinkForm.value.name,
        size: 0,
        type: '外部链接',
        resourceType: 'link',
        url: externalLinkForm.value.externalLink,
        resourceId: result.resourceId,
        attachmentId: result.attachmentId,
        externalLink: externalLinkForm.value.externalLink,
        purchasedNote: externalLinkForm.value.purchasedNote,
        downloadType: externalLinkForm.value.downloadType,
        pointsNeeded: externalLinkForm.value.pointsNeeded
      })
      showExternalLinkForm.value = false
      externalLinkForm.value = { name: '', description: '', externalLink: '', purchasedNote: '', downloadType: 0, pointsNeeded: 0 }
      Swal.fire('成功', '外部链接资源添加成功！', 'success')
    }, {
      onError: () => {
        Swal.fire('错误', '创建外部链接资源失败，请重试', 'error')
      }
    })
  }

  const removeAttachment = async (attachmentId: string) => {
    const result = await Swal.fire({
      title: '确认删除？', text: '删除后无法恢复', icon: 'warning',
      showCancelButton: true, confirmButtonText: '确认删除', cancelButtonText: '取消'
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
        onError: () => { Swal.fire('错误', '删除附件失败，请重试', 'error') }
      })
    }
  }

  const META_UPDATE_DELAY = 500

  const onDownloadTypeChange = async (attachment: AttachmentItem) => {
    const newType = attachment.downloadType
    const prevType = newType === 0 ? 1 : 0
    if (!attachment.resourceId) {
      Swal.fire('错误', '资源标识缺失，无法更新附件设置', 'error')
      attachment.downloadType = prevType
      return
    }
    if (newType === 1 && (!attachment.pointsNeeded || attachment.pointsNeeded < 1)) {
      attachment.pointsNeeded = 1
    }
    try {
      await PostService.updateAttachmentMeta(attachment.resourceId, newType, newType === 1 ? attachment.pointsNeeded : 0)
    } catch {
      Swal.fire('错误', '更新附件下载类型失败，已为你恢复原值', 'error')
      attachment.downloadType = prevType
    }
  }

  const onPointsNeededChange = async (attachment: AttachmentItem) => {
    if (attachment.downloadType !== 1) return
    const prevPoints = attachment._prevPointsNeeded ?? attachment.pointsNeeded
    let newPoints = Number(attachment.pointsNeeded)
    if (!Number.isFinite(newPoints) || newPoints < 1) newPoints = 1
    newPoints = Math.floor(newPoints)
    attachment.pointsNeeded = newPoints
    if (!attachment.resourceId) {
      Swal.fire('错误', '资源标识缺失，无法更新附件设置', 'error')
      attachment.pointsNeeded = prevPoints
      return
    }
    try {
      await PostService.updateAttachmentMeta(attachment.resourceId, 1, newPoints)
    } catch {
      Swal.fire('错误', '更新附件积分失败，已为你恢复原值', 'error')
      attachment.pointsNeeded = prevPoints
    }
  }

  const handlePointsInput = async (attachment: AttachmentItem, event: Event) => {
    const input = event.target as HTMLInputElement
    attachment.pointsNeeded = Number(input.value) || 1
    await onPointsNeededChange(attachment)
  }

  const onPointsNeededInput = async (attachment: AttachmentItem) => {
    if (attachment.downloadType !== 1) return
    let val = Number(attachment.pointsNeeded)
    if (!Number.isFinite(val) || val < 1) val = 1
    attachment.pointsNeeded = Math.floor(val)
    if (!attachment.resourceId) return
    if (attachment._updateTimer) {
      clearTimeout(attachment._updateTimer)
      attachment._updateTimer = null
    }
    attachment._updateTimer = setTimeout(async () => {
      try {
        await PostService.updateAttachmentMeta(attachment.resourceId!, 1, attachment.pointsNeeded)
      } catch {} finally {
        attachment._updateTimer = null
      }
    }, META_UPDATE_DELAY)
  }

  const flushAttachmentMetaUpdates = async () => {
    for (const att of attachments.value) {
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
      } catch {}
    }
  }

  const formatFileSize = (bytes: number): string => {
    if (bytes === 0) return '0 B'
    const k = 1024
    const sizes = ['B', 'KB', 'MB', 'GB']
    const i = Math.floor(Math.log(bytes) / Math.log(k))
    return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i]
  }

  // 创建分类/标签
  const showCreateCategoryDialog = () => {
    showCreateCategoryDialogVisible.value = true
    newCategoryName.value = ''
    newCategoryDescription.value = ''
  }

  const showCreateTagDialog = () => {
    showCreateTagDialogVisible.value = true
    newTagName.value = ''
  }

  const showCreateSeriesDialog = () => {
    showCreateSeriesDialogVisible.value = true
    newSeriesName.value = ''
    newSeriesDescription.value = ''
  }

  const createSeries = async () => {
    if (!newSeriesName.value.trim()) {
      Swal.fire('提示', '请输入系列名称', 'warning')
      return
    }
    await handleAsync(async () => {
      creatingSeries.value = true
      await SeriesService.createSeries({
        name: newSeriesName.value.trim(),
        description: newSeriesDescription.value.trim() || undefined
      })
      await loadSeries()
      const found = seriesList.value.find(s => sameName(s.name, newSeriesName.value.trim()))
      if (found) form.value.seriesId = String(found.id)
      Swal.fire('成功', '系列创建成功！', 'success')
    }, {
      onError: (err) => { Swal.fire('错误', `创建系列失败: ${err.message || '请重试'}`, 'error') },
      onFinally: () => {
        creatingSeries.value = false
        newSeriesName.value = ''
        newSeriesDescription.value = ''
        showCreateSeriesDialogVisible.value = false
      }
    })
  }

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
      await categoryStore.fetchCategories(true)
      if (newCategory && newCategory.id) {
        form.value.categoryId = newCategory.id.toString()
      }
      Swal.fire('成功', '分类创建成功！', 'success')
    }, {
      onError: (err) => { Swal.fire('错误', `创建分类失败: ${err.message || '请重试'}`, 'error') },
      onFinally: () => {
        creatingCategory.value = false
        newCategoryName.value = ''
        newCategoryDescription.value = ''
        showCreateCategoryDialogVisible.value = false
      }
    })
  }

  const createTag = async () => {
    if (!newTagName.value.trim()) {
      Swal.fire('提示', '请输入标签名称', 'warning')
      return
    }
    await handleAsync(async () => {
      creatingTag.value = true
      const tagData: CreateTagRequest = { name: newTagName.value.trim() }
      const newTag = await TagService.createTag(tagData)
      await tagStore.fetchTags(true)
      if (newTag && newTag.id) {
        selectedTags.value.push(newTag)
      }
      Swal.fire('成功', '标签创建成功！', 'success')
    }, {
      onError: (err) => { Swal.fire('错误', `创建标签失败: ${err.message || '请重试'}`, 'error') },
      onFinally: () => {
        creatingTag.value = false
        newTagName.value = ''
        showCreateTagDialogVisible.value = false
      }
    })
  }

  // AI 建议分类/标签
  const createAiSuggestedCategory = async (name: string) => {
    const categoryName = name.trim()
    if (!categoryName || creatingAiSuggestion.value) return
    const existing = categories.value.find(category => sameName(category.name, categoryName))
    if (existing) {
      form.value.categoryId = String(existing.id)
      aiSuggestedCategoryName.value = ''
      Swal.fire('已选中', `已选中分类「${categoryName}」`, 'success')
      return
    }
    await handleAsync(async () => {
      creatingAiSuggestion.value = `category:${categoryName}`
      const created = await CategoryService.createCategory({ name: categoryName, description: '由 AI 写作助手建议创建' })
      await categoryStore.fetchCategories(true)
      if (created?.id) {
        await categoryStore.fetchCategoryById(created.id)
        form.value.categoryId = String(created.id)
      } else {
        const found = categories.value.find(category => sameName(category.name, categoryName))
        if (found) form.value.categoryId = String(found.id)
      }
      aiSuggestedCategoryName.value = ''
      Swal.fire('成功', `分类「${categoryName}」已创建并选中`, 'success')
    }, {
      onError: (err) => { Swal.fire('错误', `创建分类失败: ${err.message || '请重试'}`, 'error') },
      onFinally: () => { creatingAiSuggestion.value = '' }
    })
  }

  const createAiSuggestedTag = async (name: string, silent = false) => {
    const tagName = name.trim()
    if (!tagName || creatingAiSuggestion.value) return
    const existing = tags.value.find(tag => sameName(tag.name, tagName))
    if (existing) {
      if (!selectedTags.value.some(tag => tag.id === existing.id)) {
        selectedTags.value.push(existing)
      }
      aiSuggestedTagNames.value = aiSuggestedTagNames.value.filter(item => !sameName(item, tagName))
      if (!silent) Swal.fire('已选中', `已选中标签「${tagName}」`, 'success')
      return
    }
    await handleAsync(async () => {
      creatingAiSuggestion.value = `tag:${tagName}`
      const created = await TagService.createTag({ name: tagName })
      await tagStore.fetchTags(true)
      let tagToSelect = created
      if (created?.id) {
        const fetched = await tagStore.fetchTagById(created.id)
        if (fetched) tagToSelect = fetched
      } else {
        tagToSelect = tags.value.find(tag => sameName(tag.name, tagName)) as Tag
      }
      if (tagToSelect?.id && !selectedTags.value.some(tag => tag.id === tagToSelect.id)) {
        selectedTags.value.push(tagToSelect)
      }
      aiSuggestedTagNames.value = aiSuggestedTagNames.value.filter(item => !sameName(item, tagName))
      if (!silent) Swal.fire('成功', `标签「${tagName}」已创建并选中`, 'success')
    }, {
      onError: (err) => { Swal.fire('错误', `创建标签失败: ${err.message || '请重试'}`, 'error') },
      onFinally: () => { creatingAiSuggestion.value = '' }
    })
  }

  const createAllAiSuggestedTags = async () => {
    const names = [...aiSuggestedTagNames.value]
    if (!names.length || creatingAiSuggestion.value) return
    for (const name of names) { await createAiSuggestedTag(name, true) }
    Swal.fire('成功', 'AI 建议标签已创建并选中', 'success')
  }

  const createAllAiSuggestedTaxonomy = async () => {
    if (creatingAiSuggestion.value) return
    if (aiSuggestedCategoryName.value) { await createAiSuggestedCategory(aiSuggestedCategoryName.value) }
    for (const name of [...aiSuggestedTagNames.value]) { await createAiSuggestedTag(name, true) }
    Swal.fire('成功', 'AI 建议分类和标签已处理完成', 'success')
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

  const previewPost = () => {
    if (!form.value.title || !form.value.content) {
      Swal.fire('提示', '请填写标题和内容后再预览', 'warning')
      return
    }
    showPreview.value = true
  }

  const closePreview = () => { showPreview.value = false }

  const handleSubmit = async () => { await submitPost() }

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
      await flushAttachmentMetaUpdates()

      let postId = editingPostId.value
      if (isEditMode.value && editingPostId.value) {
        const updateData = {
          id: editingPostId.value,
          title: form.value.title.trim(),
          content: form.value.content,
          summary: form.value.summary?.trim() || '',
          categoryId: Number(form.value.categoryId),
          seriesId: form.value.seriesId ? Number(form.value.seriesId) : null,
          seriesSort: form.value.seriesSort ?? 0,
          status: form.value.status,
          tagIds: selectedTags.value.map(tag => tag.id),
          coverImage: form.value.coverImage || '',
          thumbnail: form.value.thumbnail || '',
          draftKey: draftKey.value
        }
        await PostService.updatePost(editingPostId.value, updateData)
      } else {
        const postData = {
          title: form.value.title.trim(),
          content: form.value.content,
          summary: form.value.summary?.trim() || '',
          categoryId: Number(form.value.categoryId),
          seriesId: form.value.seriesId ? Number(form.value.seriesId) : null,
          seriesSort: form.value.seriesSort ?? 0,
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
      router.push(`/post/${postId}?from=home`)
    }, {
      onError: () => {
        const actionText = isEditMode.value
          ? (form.value.status === 'draft' ? '更新草稿' : '更新文章')
          : (form.value.status === 'draft' ? '保存草稿' : '发布文章')
        Swal.fire('错误', `${actionText}失败，请重试`, 'error')
      },
      onFinally: () => { saving.value = false }
    })
  }

  const goBack = () => {
    if (form.value.title || form.value.content) {
      Swal.fire({
        title: '确认离开？', text: '当前编辑的内容将会丢失', icon: 'warning',
        showCancelButton: true, confirmButtonText: '确认离开', cancelButtonText: '继续编辑'
      }).then((result) => { if (result.isConfirmed) router.back() })
    } else {
      router.back()
    }
  }

  const loadPostData = async (postId: number) => {
    await handleAsync(async () => {
      loading.value = true
      const postData: PostDetail = await PostService.getPostDetailForAdmin(postId)
      form.value = {
        title: postData.title,
        content: postData.content,
        summary: postData.summary || '',
        categoryId: postData.category.id.toString(),
        seriesId: postData.series?.id ? String(postData.series.id) : '',
        seriesSort: postData.series?.sort ?? 0,
        status: postData.status === 'draft' ? 'draft' : 'published',
        coverImage: postData.coverImage || '',
        thumbnail: postData.thumbnail || '',
        viewCount: postData.viewCount || 0,
        likeCount: postData.likeCount || 0
      }
      if (postData.tags) {
        selectedTags.value = postData.tags.map(tag => ({ id: tag.id, name: tag.name, postCount: 0 }))
      }
      try {
        const existing = await PostService.getPostAttachments(postId)
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
      } catch {}
    }, {
      onError: () => { Swal.fire('错误', '加载文章数据失败，请重试', 'error'); router.back() },
      onFinally: () => { loading.value = false }
    })
  }

  const checkEditMode = () => {
    const draftParam = route.query.draft
    const editParam = route.query.edit
    if (draftParam && draftParam !== 'true') {
      isEditMode.value = true
      editingPostId.value = Number(draftParam)
      form.value.status = 'draft'
    } else if (editParam) {
      isEditMode.value = true
      editingPostId.value = Number(editParam)
      form.value.status = 'published'
    } else if (draftParam === 'true') {
      form.value.status = 'draft'
    }
  }

  return {
    form, draftKey, generateDraftKey,
    categories, tags, seriesList, selectedTags, selectedTagId, availableTags,
    saving, showPreview, isEditMode, editingPostId, loading,
    renderedPreviewContent,
    attachments, uploadingAttachment, attachmentType,
    externalLinkForm, showExternalLinkForm,
    showCreateCategoryDialogVisible, showCreateTagDialogVisible, showCreateSeriesDialogVisible,
    creatingCategory, creatingTag, creatingSeries, newCategoryName, newCategoryDescription,
    newTagName, newSeriesName, newSeriesDescription,
    aiSuggestedCategoryName, aiSuggestedTagNames, creatingAiSuggestion,
    hasAiTaxonomySuggestions, isAdminWritingAvailable, adminDraftSnapshot,
    undoStack, fieldLabels,
    handleFieldUpdate, undoField, getCategoryName,
    loadCategories, loadSeries, loadTags, addTag, removeTag,
    coverImageInput, thumbnailInput, attachmentInput,
    triggerCoverImageUpload, triggerThumbnailUpload,
    handleCoverImageUpload, handleThumbnailUpload, uploadImage,
    triggerAttachmentUpload, handleAttachmentUpload, uploadAttachment,
    switchAttachmentType, createExternalLinkResource, removeAttachment,
    onDownloadTypeChange, onPointsNeededChange, handlePointsInput, onPointsNeededInput,
    flushAttachmentMetaUpdates, formatFileSize,
    showCreateCategoryDialog, showCreateTagDialog, showCreateSeriesDialog,
    createCategory, createTag, createSeries,
    createAiSuggestedCategory, createAiSuggestedTag,
    createAllAiSuggestedTags, createAllAiSuggestedTaxonomy,
    saveDraft, previewPost, closePreview, handleSubmit, submitPost, goBack,
    loadPostData, checkEditMode,
    sameName, normalizeSuggestedNames, rememberAiTaxonomySuggestions
  }
}
