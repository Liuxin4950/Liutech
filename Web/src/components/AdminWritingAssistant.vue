<template>
  <aside class="writing-assistant">
    <div class="assistant-header">
      <div>
        <h3>纳西妲写作助手</h3>
        <p>生成、润色、排版和发布前检查</p>
      </div>
    </div>

    <div class="quick-actions">
      <button v-for="item in quickPrompts" :key="item.label" type="button" :disabled="loading" @click="send(item.message, item.mode)">
        {{ item.label }}
      </button>
    </div>

    <label class="writing-mode">正文处理
      <select v-model="contentMode" :disabled="loading">
        <option value="patch">局部修改 · 保留其他段落</option>
        <option value="replace">整篇生成 / 重写</option>
      </select>
    </label>

    <textarea v-model="prompt" :disabled="loading" placeholder="告诉纳西妲你想写什么...（Enter发送，Shift+Enter换行）" rows="4" @keydown="handleKeydown"></textarea>
    <button type="button" class="send-btn" :disabled="!canSend" @click="send()">
      {{ loading ? '生成中...' : '发送给纳西妲' }}
    </button>
    <button v-if="loading" type="button" @click="stop">停止生成</button>

    <section v-if="showProcessCard" class="assistant-section process-card">
      <div class="section-title process-title">
        <span>执行过程</span>
        <strong class="process-status" :class="{ 'is-error': !!streamError }">{{ statusLine }}</strong>
      </div>
      <div class="activity-list">
        <div
          v-for="item in activity"
          :key="item.activityId"
          class="trace-row activity-item"
          :class="activityStatus(item)"
        >
          <span class="trace-dot"></span>
          <div class="activity-main">
            <div class="activity-title">{{ activityTitle(item) }}</div>
            <div v-if="activityInput(item)" class="activity-sub">{{ activityInput(item) }}</div>
            <div v-if="activityOutcome(item)" class="activity-sub">{{ activityOutcome(item) }}</div>
          </div>
          <em class="activity-meta">{{ activityMeta(item) }}</em>
        </div>
      </div>
    </section>

    <section v-if="hasChanges" class="writing-preview">
      <strong>本轮修改预览</strong>
      <p v-if="pendingUpdate.title">标题：{{ pendingUpdate.title }}</p>
      <p v-if="pendingUpdate.summary !== undefined">摘要：{{ pendingUpdate.summary || '清空摘要' }}</p>
      <p v-if="pendingUpdate.categoryId || pendingUpdate.suggestedCategoryName">分类：{{ pendingUpdate.categoryName || pendingUpdate.suggestedCategoryName || '调整为现有分类' }}</p>
      <p v-if="pendingUpdate.tagIds !== undefined || pendingUpdate.suggestedTagNames?.length">标签：{{ pendingUpdate.tagIds?.length === 0 ? '清空所有标签' : [...(pendingUpdate.tagNames || (pendingUpdate.tagIds?.length ? [`${pendingUpdate.tagIds.length} 个现有标签`] : [])), ...(pendingUpdate.suggestedTagNames || [])].join('、') }}</p>
      <div v-if="patchPreviews.length" class="writing-patches">
        <p>局部修改 {{ patchPreviews.length }} 处，其他内容保持原样</p>
        <article v-for="patch in patchPreviews" :key="patch.id" class="writing-patch">
          <strong>修改 {{ patch.id + 1 }}</strong>
          <div class="patch-before"><span>修改前</span><div v-html="patch.before" @click="openWritingPreviewLink" @keydown="openWritingPreviewLink"></div></div>
          <div class="patch-after"><span>修改后</span><div v-html="patch.after" @click="openWritingPreviewLink" @keydown="openWritingPreviewLink"></div></div>
        </article>
      </div>
      <div v-if="previewHtml" class="writing-preview-body" v-html="previewHtml" @click="openWritingPreviewLink" @keydown="openWritingPreviewLink"></div>
      <p v-if="missingMedia" role="alert">本轮将移除 {{ missingMedia }} 处原稿媒体，请检查后再应用；应用后可撤销。</p>
      <p v-if="draftConflict && !applied" role="alert">原稿在生成后已修改，请根据当前原稿重新生成。</p>
      <button type="button" :disabled="!canApply || loading" @click="apply()">{{ applied ? '本轮修改已应用' : '应用本轮修改' }}</button>
      <p v-if="!session.review.succeeded.value && !loading">本轮未完整成功，不能应用到原稿。</p>
    </section>

    <!-- AI回复区域 -->
    <section v-if="displayAnswer" class="assistant-section answer-container">
      <div class="answer-header">
        <span class="answer-title">纳西妲</span>
        <button v-if="canExpand" type="button" class="expand-btn" @click="showFullAnswer = !showFullAnswer">
          {{ showFullAnswer ? "收起" : "展开" }}
        </button>
      </div>
      <div 
        class="answer-content"
        :class="{ collapsed: canExpand && !showFullAnswer }"
        @click="canExpand && !showFullAnswer && (showFullAnswer = true)"
      >{{ previewText }}</div>
    </section>

    <!-- AI 引用的关联文章 -->
    <section v-if="articleResults.length" class="assistant-section article-results">
      <div class="answer-header">
        <span class="answer-title">{{ articleResultReason }}</span>
      </div>
      <div class="article-result-list">
        <a
          v-for="post in articleResults"
          :key="post.id"
          class="article-result-item"
          :href="`/post/${post.id}`"
          target="_blank"
          rel="noopener"
        >
          <span class="article-result-title">{{ post.title }}</span>
          <span class="article-result-arrow">›</span>
        </a>
      </div>
    </section>

    <!-- 错误文案统一由执行过程卡片的状态行展示，避免同一张卡片里重复出现两次 -->
  </aside>
</template>

<script setup lang="ts">
import { useWritingSession, writingQuickPrompts } from '@/services/writingSession'
import type { WritingLocalActivity } from '@/services/writingSession'
import { AdminAgentService, type AdminArticleDraftSnapshot, type FieldUpdatePayload } from '@/services/adminAgent'
const props = defineProps<{ draft: AdminArticleDraftSnapshot; localActivities?: WritingLocalActivity[] }>()
const emit = defineEmits<{ fieldUpdate: [payload: FieldUpdatePayload, original: AdminArticleDraftSnapshot] }>()
const session = useWritingSession({
  getDraft: () => props.draft, getLocalActivities: () => props.localActivities,
  onApply: (update, original) => emit('fieldUpdate', update, original),
  stream: (request, handlers, signal) => AdminAgentService.stream(request, handlers, signal), source: 'web-create-post'
})
const { prompt, loading, showFullAnswer, activity, streamError, articleResultReason, contentMode,
  pendingUpdate, draftConflict, canApply, hasChanges, missingMedia, applied, previewHtml, patchPreviews,
  displayAnswer, canExpand, previewText, statusLine, showProcessCard, canSend,
  activityStatus, activityMeta, activityTitle, activityInput, activityOutcome, stop, send, handleKeydown, apply, openWritingPreviewLink } = session
const articleResults = session.articles
const quickPrompts = writingQuickPrompts
</script>

<style scoped>
.writing-mode { display: flex; flex-direction: column; gap: 6px; font-size: 12px; }
.writing-mode select { padding: 7px; border-radius: 6px; border: 1px solid var(--border-light, #ddd); background: transparent; color: inherit; }
.writing-patch { margin-top: 10px; border-top: 1px solid var(--border-light, #ddd); padding-top: 10px; }
.patch-before, .patch-after { margin-top: 8px; padding: 8px; border-radius: 6px; max-height: 180px; overflow: auto; overflow-wrap: anywhere; }
.patch-before { background: rgba(180, 70, 70, .08); }
.patch-after { background: rgba(40, 140, 70, .08); }
.patch-before > span, .patch-after > span { font-size: 11px; opacity: .7; }

.writing-preview { border: 1px solid var(--border-light, #ddd); border-radius: 8px; padding: 12px; }
.writing-preview-body { max-height: 320px; overflow: auto; overflow-wrap: anywhere; }
.writing-preview button { margin-top: 8px; cursor: pointer; }
.writing-preview button:disabled { cursor: default; opacity: .5; }

.writing-assistant {
  background: var(--bg-card);
  border: 1px solid var(--border-light);
  border-radius: 8px;
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  color: var(--text-main);
  transition: background-color 0.2s ease, border-color 0.2s ease, color 0.2s ease;
}

.assistant-header h3 {
  margin: 0;
  font-size: 16px;
  color: var(--text-title);
}

.assistant-header p,
.summary,
.hint {
  margin: 4px 0 0;
  color: var(--text-subtle);
  font-size: 13px;
}

.quick-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

button {
  border: 1px solid var(--border-light);
  background: var(--bg-element);
  color: var(--text-main);
  border-radius: 6px;
  padding: 7px 10px;
  cursor: pointer;
  transition: background-color 0.2s ease, border-color 0.2s ease, color 0.2s ease;
}

button:hover:not(:disabled) {
  border-color: var(--border-base);
  background: var(--bg-hover);
}

button:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.send-btn {
  background: var(--color-primary);
  color: var(--text-on-primary);
  border-color: var(--color-primary);
}

textarea {
  width: 100%;
  border: 1px solid var(--border-light);
  border-radius: 6px;
  padding: 10px;
  resize: vertical;
  background: var(--bg-element);
  color: var(--text-main);
  outline: none;
  transition: background-color 0.2s ease, border-color 0.2s ease, color 0.2s ease;
}

textarea::placeholder {
  color: var(--text-muted);
}

textarea:focus {
  border-color: var(--color-primary);
  box-shadow: 0 0 0 2px color-mix(in srgb, var(--color-primary) 22%, transparent);
}

.assistant-section {
  border-top: 1px solid var(--border-light);
  padding-top: 12px;
}

.process-card {
  background: var(--bg-soft);
  border: 1px solid var(--border-light);
  border-radius: 8px;
  padding: 12px;
}

.section-title {
  font-size: 13px;
  font-weight: 600;
  margin-bottom: 8px;
}

.process-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

/* 状态行：替代原来的假百分比，文案可换行（结束后的汇总比运行中的动作长） */
.process-title strong.process-status {
  max-width: 100%;
  margin-left: auto;
  text-align: right;
  white-space: normal;
  line-height: 1.5;
  color: var(--color-primary);
  font-size: 12px;
  font-weight: 600;
}

.process-status.is-error {
  color: var(--color-error);
}

/* 真实活动时间线：每一行都来自后端 SSE 事件 */
.activity-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.activity-item {
  align-items: start;
}

.activity-item .trace-dot {
  margin-top: 6px;
}

.activity-main {
  min-width: 0;
}

.activity-title {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--text-subtle);
}

.activity-sub {
  margin-top: 2px;
  font-size: 11px;
  line-height: 1.5;
  color: var(--text-muted);
  white-space: normal;
  word-break: break-word;
}

.activity-meta {
  white-space: nowrap;
  color: var(--text-muted);
}

.activity-item.running .activity-meta {
  color: var(--color-primary);
}

.activity-item.success .activity-meta {
  color: var(--color-success);
}

.activity-item.failed .activity-title,
.activity-item.failed .activity-sub,
.activity-item.failed .activity-meta {
  color: var(--color-error);
}

.trace-row {
  display: grid;
  grid-template-columns: 10px minmax(0, 1fr) auto;
  gap: 8px;
  align-items: center;
  font-size: 12px;
  color: var(--text-subtle);
  min-height: 22px;
}

.trace-row span:nth-child(2),
.trace-row em {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.trace-dot {
  width: 7px;
  height: 7px;
  border-radius: 999px;
  background: var(--border-base);
}

.trace-row em {
  color: var(--text-muted);
  font-style: normal;
  white-space: nowrap;
}

.trace-row.running .trace-dot {
  background: var(--color-primary);
  box-shadow: 0 0 0 4px rgba(45, 144, 205, 0.12);
}

.trace-row.success .trace-dot,
.trace-row.completed .trace-dot {
  background: var(--color-success);
}

.trace-row.waiting .trace-dot {
  background: var(--color-warning);
}

.trace-row.failed .trace-dot {
  background: var(--color-error);
}

.answer {
  white-space: pre-wrap;
  font-size: 13px;
  color: var(--text-subtle);
}
.answer-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 6px;
}
.answer-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-main);
}
.expand-btn {
  border: 1px solid var(--color-primary);
  background: color-mix(in srgb, var(--color-primary) 8%, transparent);
  padding: 3px 10px;
  font-size: 12px;
  color: var(--color-primary);
  cursor: pointer;
  border-radius: 6px;
  transition: all 0.2s;
  font-weight: 500;
}
.expand-btn:hover {
  background: var(--color-primary);
  color: white;
}
.answer-content {
  font-size: 13px;
  line-height: 1.6;
  color: var(--text-subtle);
  background: var(--bg-soft);
  border-radius: 8px;
  padding: 10px 12px;
  white-space: pre-wrap;
  word-break: break-word;
}
.answer-content.collapsed {
  max-height: 88px;
  overflow: hidden;
  position: relative;
  cursor: pointer;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  -webkit-mask-image: linear-gradient(to bottom, black 60%, transparent 100%);
  mask-image: linear-gradient(to bottom, black 60%, transparent 100%);
}
.answer-content:not(.collapsed) {
  max-height: 360px;
  overflow-y: auto;
}

.assistant-success {
  margin: 0;
  padding: 9px 10px;
  border-radius: 6px;
  background: rgba(42, 157, 143, 0.12);
  color: var(--color-success);
  font-size: 13px;
}

.article-result-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.article-result-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 8px 10px;
  border: 1px solid var(--border-light);
  border-radius: 6px;
  text-decoration: none;
  transition: all 0.2s;
}
.article-result-item:hover {
  border-color: var(--color-primary);
  background: color-mix(in srgb, var(--color-primary) 6%, transparent);
}
.article-result-title {
  font-size: 13px;
  color: var(--text-main);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.article-result-arrow {
  color: var(--text-subtle);
  font-size: 16px;
  flex-shrink: 0;
}
</style>

