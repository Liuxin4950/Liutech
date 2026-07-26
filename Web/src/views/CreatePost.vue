<template>
  <div class="content">
    <!-- 页面标题 -->
    <div class="page-header">
      <div class="page-title">
        <Icon :name="isEditMode ? 'edit' : 'plus'" size="20" />
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

      <div v-if="isAdminWritingAvailable" class="editor-ai-panel">
        <AdminWritingAssistant
          :draft="adminDraftSnapshot"
          @field-update="handleFieldUpdate"
        />
        <section v-if="hasAiTaxonomySuggestions" class="ai-taxonomy-card">
          <div class="ai-taxonomy-title">
            <Icon name="plus" size="15" />
            <span>AI 建议创建</span>
          </div>
          <div v-if="aiSuggestedCategoryName" class="ai-taxonomy-row">
            <span class="ai-taxonomy-label">分类</span>
            <button
              type="button"
              class="ai-taxonomy-chip"
              :disabled="!!creatingAiSuggestion"
              @click="createAiSuggestedCategory(aiSuggestedCategoryName)"
            >
              + {{ aiSuggestedCategoryName }}
            </button>
          </div>
          <div v-if="aiSuggestedCategoryName && aiSuggestedTagNames.length" class="ai-taxonomy-row">
            <span class="ai-taxonomy-label">全部</span>
            <button
              type="button"
              class="ai-taxonomy-chip primary"
              :disabled="!!creatingAiSuggestion"
              @click="createAllAiSuggestedTaxonomy"
            >
              创建分类和标签并选中
            </button>
          </div>
          <div v-if="aiSuggestedTagNames.length" class="ai-taxonomy-row">
            <span class="ai-taxonomy-label">标签</span>
            <button
              v-for="name in aiSuggestedTagNames"
              :key="name"
              type="button"
              class="ai-taxonomy-chip"
              :disabled="!!creatingAiSuggestion"
              @click="createAiSuggestedTag(name)"
            >
              + {{ name }}
            </button>
            <button
              type="button"
              class="ai-taxonomy-chip primary"
              :disabled="!!creatingAiSuggestion"
              @click="createAllAiSuggestedTags"
            >
              全部创建并选中
            </button>
          </div>
          <p class="ai-taxonomy-hint">
            这些不是已有分类/标签，点击后才会新增到后台并写入当前文章。
          </p>
        </section>
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
                  <div class="att-name-row">
                    <Icon :name="attachment.resourceType === 'link' ? 'link' : 'file'" size="14" class="text-muted" />
                    <span class="att-name" :title="attachment.name">{{ attachment.name }}</span>
                  </div>
                  <div class="att-meta">
                    <span class="att-type text-muted">{{ attachment.resourceType === 'link' ? '外链' : (attachment.size > 0 ? formatFileSize(attachment.size) : '文件') }}</span>
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
          <div class="sidebar-item flex-col gap-8">
            <div class="sidebar-title">文章标签</div>
            <div class="sidebar-content">
              <SearchableSelect
                :options="tags.map(t => ({ label: t.name, value: t.id }))"
                :model-value="selectedTagIds"
                multiple
                placeholder="请选择标签"
                search-placeholder="搜索标签..."
                creatable
                create-label="新建标签"
                @update:model-value="onTagIdsChange"
                @create="showCreateTagDialog"
              />
            </div>
          </div>
          <!-- 图片 -->
          <div class="sidebar-item flex-col gap-8">
            <div class="sidebar-title">添加封面</div>
            <div class="sidebar-content flex gap-12 flex-fw">
              <!-- 封面图片上传 -->
              <div class="image-upload-container">
                <div class="image-preview-box" @click="triggerCoverImageUpload"
                  :class="{ 'has-image': form.coverImage }">
                  <img v-if="form.coverImage" :src="form.coverImage" alt="封面图片预览" class="preview-image" @error="handleImageError">
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
                  <img v-if="form.thumbnail" :src="form.thumbnail" alt="缩略图预览" class="preview-image" @error="handleImageError">
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

          <div class="sidebar-item flex-col gap-8 relative">
            <div class="sidebar-title">文章摘要</div>
            <div class="sidebar-content">
              <textarea v-model="form.summary" class="field-textarea" placeholder="请输入文章摘要（可选）" rows="4"
                maxlength="200"></textarea>
              <div class="char-count text-sm text-muted">{{ (form.summary || '').length }}/200</div>
            </div>
          </div>

          <div class="sidebar-item flex-col gap-8">
            <div class="sidebar-title">文章分类</div>
            <div class="sidebar-content">
              <SearchableSelect
                :options="categoryOptions"
                :model-value="form.categoryId"
                placeholder="请选择分类"
                search-placeholder="搜索分类..."
                creatable
                create-label="新建分类"
                @update:model-value="val => form.categoryId = String(val)"
                @create="showCreateCategoryDialog"
              />
            </div>
          </div>


          <div class="sidebar-item flex-col gap-8">
            <div class="sidebar-title">文章系列</div>
            <div class="sidebar-content">
              <SearchableSelect
                :options="seriesOptions"
                :model-value="form.seriesId"
                placeholder="不属于任何系列"
                search-placeholder="搜索系列..."
                creatable
                create-label="新建系列"
                @update:model-value="val => form.seriesId = String(val)"
                @create="showCreateSeriesDialog"
              />
              <p v-if="form.seriesId" style="font-size: 12px; color: var(--text-muted); margin: 6px 0 0;">新文章自动排到该系列末尾，顺序可在「系列管理」拖拽调整</p>
            </div>
          </div>

          <div class="sidebar-item flex-col gap-8">
            <div class="sidebar-title">发布状态</div>
            <div class="sidebar-content">
              <select v-model="form.status" class="field-select w-full">
                <option value="draft">草稿</option>
                <option value="published">发布</option>
              </select>
            </div>
          </div>

          <div class="sidebar-item flex-col gap-8">
            <div class="sidebar-title">文章预览</div>
            <div class="sidebar-content">
              <button @click="previewPost" class="btn-secondary w-full" :disabled="!form.title || !form.content">
                <Icon name="eye" size="14" />
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
        <div v-if="undoStack.length > 0" class="undo-inline">
          <span class="undo-label">AI 已修改 {{ undoStack.length }} 个字段</span>
          <div class="undo-actions">
            <button
              v-for="entry in undoStack"
              :key="entry.field"
              type="button"
              class="undo-field-btn"
              @click="undoField(entry.field)"
            >
              撤销{{ fieldLabels[entry.field] || entry.field }}
            </button>
          </div>
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
              <img :src="form.coverImage" :alt="form.title" class="preview-cover-image" @error="handleImageError">
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
            <div ref="previewContentRef" class="markdown-content" v-html="renderedPreviewContent"></div>
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

  <!-- 创建系列对话框 -->
  <div v-if="showCreateSeriesDialogVisible" class="modal-overlay" @click="showCreateSeriesDialogVisible = false">
    <div class="modal-content" @click.stop>
      <div class="modal-header">
        <h3>创建新系列</h3>
        <button @click="showCreateSeriesDialogVisible = false" class="close-btn">×</button>
      </div>
      <div class="modal-body">
        <div class="form-group">
          <label>系列名称 *</label>
          <input v-model="newSeriesName" type="text" placeholder="请输入系列名称" maxlength="50" @keyup.enter="createSeries">
        </div>
        <div class="form-group">
          <label>系列描述</label>
          <textarea v-model="newSeriesDescription" placeholder="请输入系列描述（可选）" maxlength="200" rows="3"></textarea>
        </div>
      </div>
      <div class="modal-footer">
        <button @click="showCreateSeriesDialogVisible = false" class="btn btn-secondary">取消</button>
        <button @click="createSeries" :disabled="creatingSeries" class="btn btn-primary">
          {{ creatingSeries ? '创建中...' : '创建' }}
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, computed, ref, watch, nextTick } from 'vue'
import TinyMCEEditor from '@/components/TinyMCEEditor.vue'
import SearchableSelect from '@/components/SearchableSelect.vue'
import Icon from '@/components/Icon.vue'
import AdminWritingAssistant from '@/components/AdminWritingAssistant.vue'
import { handleImageError } from '@/composables/useImageFallback'
import { formatDate } from '@/utils/utils'
import { usePostEditor } from '@/composables/usePostEditor'
import { highlightCodeBlocks } from '@/composables/useRichContent'

const {
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
  handleCoverImageUpload, handleThumbnailUpload,
  triggerAttachmentUpload, handleAttachmentUpload,
  createExternalLinkResource, removeAttachment,
  onDownloadTypeChange, handlePointsInput, onPointsNeededInput,
  formatFileSize,
  showCreateCategoryDialog, showCreateTagDialog, showCreateSeriesDialog,
  createCategory, createTag, createSeries,
  createAiSuggestedCategory, createAiSuggestedTag,
  createAllAiSuggestedTags, createAllAiSuggestedTaxonomy,
  saveDraft, previewPost, closePreview, handleSubmit, goBack,
  loadPostData, checkEditMode
} = usePostEditor()

// 预览内容容器引用，用于代码高亮
const previewContentRef = ref<HTMLElement | null>(null)
// 预览打开后高亮代码块，保证预览效果与发布后一致
watch(showPreview, (open) => {
  if (open) nextTick(() => highlightCodeBlocks(previewContentRef.value))
})
// value 统一为 string，与 form.categoryId/seriesId（string）保持一致，避免 === 比较失败回退显示 id
const categoryOptions = computed(() => categories.value.map(c => ({ label: c.name, value: String(c.id) })))
// 系列首项为"不属于任何系列"，省去清空按钮
const seriesOptions = computed(() => [
  { label: '不属于任何系列', value: '' },
  ...seriesList.value.map(s => ({ label: s.name, value: String(s.id) }))
])
const selectedTagIds = computed<(string | number)[]>(() => selectedTags.value.map(t => t.id))
const onTagIdsChange = (ids: string | number | (string | number)[]) => {
  const arr = Array.isArray(ids) ? ids : []
  selectedTags.value = arr.map(id => tags.value.find(t => t.id === id)!).filter(Boolean)
}

onMounted(async () => {
  checkEditMode()
  draftKey.value = generateDraftKey()
  await Promise.all([loadCategories(), loadTags(), loadSeries()])
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
  flex: 1;
  min-width: 120px;
}

.image-preview-box {
  position: relative;
  width: 100%;
  min-height: 150px;
  height: 100%;
  border: 2px dashed var(--border-soft);
  border-radius: 8px;
  overflow: hidden;
  cursor: pointer;
  transition: all 0.3s ease;
  background: var(--bg-main);
  display: flex;
  align-items: center;
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
  background: var(--color-error);
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
  background: var(--overlay-bg-strong);
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
  display: flex;
  align-items: center;
  gap: 8px;
  color: white;
  font-weight: 500;
}

.image-preview-box:not(.has-image) .upload-text {
  color: var(--text-muted);
}

.overlay-icon {
  display: flex;
  font-size: 20px;
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

.editor-ai-panel {
  margin-bottom: 20px;
}

.ai-taxonomy-card {
  margin-top: 12px;
  padding: 14px 16px;
  border: 1px solid var(--border-soft);
  border-radius: 8px;
  background: var(--bg-card);
  box-shadow: var(--shadow-sm);
}

.ai-taxonomy-title,
.ai-taxonomy-row {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.ai-taxonomy-title {
  margin-bottom: 10px;
  color: var(--text-main);
  font-weight: 700;
}

.ai-taxonomy-row + .ai-taxonomy-row {
  margin-top: 10px;
}

.ai-taxonomy-label {
  min-width: 36px;
  color: var(--text-muted);
  font-size: 13px;
}

.ai-taxonomy-chip {
  min-height: 30px;
  padding: 5px 10px;
  border: 1px solid var(--border-base);
  border-radius: 999px;
  background: var(--bg-soft);
  color: var(--text-main);
  cursor: pointer;
  font-size: 13px;
  line-height: 1.3;
  transition: all 0.2s ease;
}

.ai-taxonomy-chip:hover:not(:disabled),
.ai-taxonomy-chip.primary {
  border-color: var(--color-primary);
  background: color-mix(in srgb, var(--color-primary) 12%, var(--bg-card));
  color: var(--color-primary);
}

.ai-taxonomy-chip:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.ai-taxonomy-hint {
  margin: 10px 0 0;
  color: var(--text-muted);
  font-size: 12px;
  line-height: 1.5;
}

.title-section {
  border-bottom: 1px solid var(--border-soft);
  margin-bottom: 20px;
}

.title-input {
  width: 100%;
  padding: 16px 18px;
  border: 1px solid var(--border-light);
  outline: none;
  font-size: 24px;
  font-weight: 600;
  color: var(--text-main);
  background: var(--bg-card);
  border-radius: 8px;
  box-shadow: var(--shadow-xs);
  transition: border-color 0.2s ease, box-shadow 0.2s ease;
}

.title-input:focus {
  border-color: var(--color-primary);
  box-shadow: 0 0 0 3px rgba(var(--color-primary-rgb), 0.14);
}

.title-input::placeholder {
  color: var(--text-subtle);
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
  padding: 30px;
  border-radius: 8px;
  box-shadow: var(--shadow-sm);
  background-color: var(--bg-card);
  
  @include respond(md) {
    padding: 20px;
  }
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
  text-align: left;
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

.field-select {
  min-height: 44px;
  padding-right: 40px;
  color-scheme: light;
  appearance: none;
  background-color: var(--bg-soft);
  background-image: url("data:image/svg+xml,%3csvg xmlns='http://www.w3.org/2000/svg' width='18' height='18' viewBox='0 0 24 24' fill='none' stroke='%236B7280' stroke-width='2' stroke-linecap='round' stroke-linejoin='round'%3e%3cpath d='m6 9 6 6 6-6'/%3e%3c/svg%3e");
  background-repeat: no-repeat;
  background-position: right 12px center;
  background-size: 16px;
}

.field-select option {
  background-color: var(--bg-card);
  color: var(--text-main);
}

.field-select option:checked {
  background-color: var(--bg-hover);
  color: var(--text-title);
}

:global(.dark) .field-select {
  color-scheme: dark;
  background-color: var(--bg-element);
  background-image: url("data:image/svg+xml,%3csvg xmlns='http://www.w3.org/2000/svg' width='18' height='18' viewBox='0 0 24 24' fill='none' stroke='%23CBD5E1' stroke-width='2' stroke-linecap='round' stroke-linejoin='round'%3e%3cpath d='m6 9 6 6 6-6'/%3e%3c/svg%3e");
}

.field-input:focus,
.field-textarea:focus,
.field-select:focus {
  outline: none;
  border-color: var(--color-primary);
  box-shadow: 0 0 0 2px rgba(var(--color-primary-rgb), 0.18);
}

.field-select:hover {
  border-color: var(--color-primary);
  background-color: var(--bg-hover);
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
  background: var(--overlay-bg);
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
  font-size: clamp(2rem, 4vw, 3rem);
  font-weight: 750;
  color: var(--text-title);
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
  color: var(--text-subtle);
  font-size: 1rem;
  line-height: 1.7;
  opacity: 0.85;
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
  background-color: var(--bg-element);
  color: var(--text-main);
  padding: 2px 6px;
  border-radius: 4px;
  font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
  font-size: 0.9em;
}

.markdown-content :deep(.token) {
  background: none !important;
  text-shadow: none !important;
  color: inherit !important;
}

.markdown-content :deep(pre) {
  background: var(--bg-code) !important;
  color: var(--text-code) !important;
  border: 1px solid rgba(148, 163, 184, 0.28);
  border-radius: 12px;
  padding: 24px;
  margin: 24px 0;
  overflow-x: auto;
  font-family: 'JetBrains Mono', 'Fira Code', 'Consolas', 'Monaco', 'Courier New', monospace;
  font-size: 14px;
  line-height: 1.6;
  position: relative;
}

.markdown-content :deep(pre code) {
  background: none;
  border: none;
  padding: 0;
  font-size: inherit;
  color: inherit !important;
  white-space: pre;
}

.markdown-content :deep(pre code *) {
  color: inherit !important;
}

.markdown-content :deep(table) {
  width: 100%;
  border-collapse: collapse;
  margin: 20px 0;
  background-color: var(--bg-card);
  border-radius: 8px;
  overflow: hidden;
  box-shadow: var(--shadow-sm);
}

.markdown-content :deep(th),
.markdown-content :deep(td) {
  padding: 12px 16px;
  text-align: left;
  border-bottom: 1px solid var(--border-base);
  color: var(--text-main);
}

.markdown-content :deep(th) {
  background-color: var(--bg-soft);
  font-weight: 600;
  color: var(--text-title);
  border-bottom: 2px solid var(--color-primary);
}

.markdown-content :deep(tr:last-child td) {
  border-bottom: none;
}

.markdown-content :deep(tr:hover) {
  background-color: var(--bg-hover);
}

:global(.dark) .markdown-content :deep(pre) {
  border-color: rgba(148, 163, 184, 0.3);
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
  background: var(--overlay-bg);
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
  display: block;
  padding: 4px 0 10px;
  margin-bottom: 14px;
  background: transparent;
  border: 0;
  border-radius: 0;
}

.page-title {
  position: relative;
  display: flex;
  align-items: center;
  gap: 8px;
  padding-left: 14px;
  font-size: 18px;
  font-weight: 600;
  color: var(--text-title);
}

.page-title::before {
  content: '';
  position: absolute;
  left: 0;
  top: 4px;
  bottom: 4px;
  width: 4px;
  border-radius: 999px;
  background: var(--color-primary);
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

.att-name-row {
  display: flex;
  align-items: center;
  gap: 6px;
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
  width: 48px;
  padding: 2px 4px;
  border: 1px solid var(--border-base);
  border-radius: 4px;
  font-size: 12px;
  text-align: center;
  background: var(--bg-main);
  color: var(--text-main);
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
  background: var(--overlay-bg);
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

.undo-inline {
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 12px;
  margin: 0 16px;
}

.undo-label {
  font-size: 13px;
  color: var(--text-subtle);
  white-space: nowrap;
}

.undo-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  min-width: 0;
}

.undo-field-btn {
  padding: 5px 12px;
  border: 1px solid var(--border-light);
  border-radius: 6px;
  background: var(--bg-soft);
  color: var(--text-main);
  font-size: 12px;
  cursor: pointer;
  transition: all 0.2s ease;
  white-space: nowrap;
}

.undo-field-btn:hover {
  border-color: var(--color-primary);
  color: var(--color-primary);
  background: var(--bg-hover);
}

/* ===== 响应式适配 ===== */
@include respond(lg) {
  .sidebar-section {
    padding: 24px;
  }
}

@include respond(md) {
  .page-header {
    padding: 4px 0 8px;
  }

  .title-input {
    font-size: 20px;
    padding: 12px 14px;
  }

  .sidebar-section {
    padding: 16px;
  }

  .sidebar-item > .sidebar-title {
    width: auto;
  }

  .sidebar-title {
    font-size: 15px;
  }

  .attach-actions {
    flex-direction: row; /* 平板端仍可以横向排列 */
  }

  .image-upload-container {
    min-width: 100px;
  }

  .image-preview-box {
    min-height: 120px;
  }

  .preview-modal .preview-content {
    width: 95%;
    max-height: 90vh;
  }

  .modal-content {
    width: 95%;
    margin: 20px;
  }

  .tool .toot-content {
    flex-direction: column;
    gap: 8px;
    padding: 8px 16px;
    align-items: stretch;
  }

  .tool {
    height: auto;
    min-height: 60px;
  }

  .undo-inline {
    flex-direction: column;
    align-items: flex-start;
    gap: 4px;
    margin: 4px 0;
  }

  .markdown-content {
    padding: 20px;
  }
}

@include respond(sm) {
  .title-input {
    font-size: 17px;
    padding: 10px 12px;
  }

  .sidebar-section {
    padding: 12px;
  }

  .sidebar-item {
    margin-bottom: 16px;
  }

  .btn-secondary,
  .btn-primary {
    padding: 8px 12px;
    font-size: 13px;
  }

  .attach-actions {
    flex-direction: column; /* 小屏竖排 */
  }

  .image-upload-container {
    min-width: 80px;
  }

  .image-preview-box {
    min-height: 100px;
  }

  .upload-overlay .upload-text span {
    font-size: 11px;
  }

  .att-pricing {
    flex-wrap: wrap;
    gap: 2px;
  }

  .tool .toot-content {
    flex-direction: column;
    gap: 8px;
    align-items: stretch;
  }

  .tool {
    height: auto;
    min-height: 60px;
  }

  .preview-content {
    width: 100%;
    max-height: 100vh;
    border-radius: 0;
  }

  .preview-title {
    font-size: 18px;
  }

  .preview-cover {
    height: 140px;
  }

  .markdown-content {
    padding: 16px;
    font-size: 14px;
    margin: 12px 0;
  }
}

</style>
