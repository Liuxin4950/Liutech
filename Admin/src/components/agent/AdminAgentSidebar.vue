<script setup lang="ts">
import { useWritingSession, writingQuickPrompts } from '@/services/writingSession'
import type { WritingLocalActivity } from '@/services/writingSession'
import { SendOutlined } from '@ant-design/icons-vue'
import AgentService from '../../services/agent'
import type { AdminArticleDraftSnapshot, FieldUpdatePayload } from '../../types/agent'
const props = defineProps<{ draft: AdminArticleDraftSnapshot; localActivities?: WritingLocalActivity[] }>()
const emit = defineEmits<{ fieldUpdate: [payload: FieldUpdatePayload, original: AdminArticleDraftSnapshot] }>()
const session = useWritingSession({
  getDraft: () => props.draft, getLocalActivities: () => props.localActivities,
  onApply: (update, original) => emit('fieldUpdate', update, original),
  stream: (request, handlers, signal) => AgentService.stream(request as Parameters<typeof AgentService.stream>[0], handlers, signal), source: 'admin-post-editor'
})
const { prompt, loading, showFullAnswer, activity, streamError, articles, contentMode,
  pendingUpdate, draftConflict, canApply, hasChanges, missingMedia, applied, previewHtml, patchPreviews,
  displayAnswer, canExpand: canExpandAnswer, previewText: answerPreviewText, statusLine, showProcessCard, canSend,
  activityStatus, activityMeta, activityTitle, activityInput, activityOutcome, stop, send, handleKeydown, apply, openWritingPreviewLink } = session
const quickPrompts = writingQuickPrompts
</script>

<template>
  <aside class="agent-sidebar">
    <div class="agent-header">
      <div>
        <h3>看板娘 Agent</h3>
        <p>生成、预览和修改文章</p>
      </div>
    </div>

    <div class="quick-actions">
      <a-button
        v-for="item in quickPrompts"
        :key="item.label"
        size="small"
        @click="send(item.message, item.mode)"
        :disabled="loading"
      >
        {{ item.label }}
      </a-button>
    </div>

    <label class="writing-mode">正文处理
      <select v-model="contentMode" :disabled="loading">
        <option value="patch">局部修改 · 保留其他段落</option>
        <option value="replace">整篇生成 / 重写</option>
      </select>
    </label>

    <a-textarea
      v-model:value="prompt"
      :rows="4"
      placeholder="告诉我你想怎么处理这篇文章...（Enter发送，Shift+Enter换行）"
      :disabled="loading"
      @keydown="handleKeydown"
    />
    <a-button type="primary" block class="send-button" :disabled="!canSend" :loading="loading" @click="send()">
      <template #icon><SendOutlined /></template>
      发送给 Agent
    </a-button>
    <a-button v-if="loading" @click="stop">停止生成</a-button>

    <div v-if="showProcessCard" class="agent-section process-card">
      <div class="process-title">
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
    </div>


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
    <div v-if="displayAnswer" class="agent-section answer-container">
      <div class="answer-header">
        <span class="answer-title">回复</span>
        <button v-if="canExpandAnswer" type="button" class="expand-btn" @click="showFullAnswer = !showFullAnswer">
          {{ showFullAnswer ? '收起' : '展开' }}
        </button>
      </div>
      <div 
        class="answer-box"
        :class="{ collapsed: canExpandAnswer && !showFullAnswer }"
        @click="canExpandAnswer && !showFullAnswer && (showFullAnswer = true)"
      >{{ answerPreviewText }}</div>
    </div>

    <div v-if="articles.length" class="agent-section">
      <div class="section-title">文章结果</div>
      <a-list size="small" :data-source="articles">
        <template #renderItem="{ item }">
          <a-list-item>
            <a :href="item.adminUrl || item.url" target="_blank">{{ item.title }}</a>
          </a-list-item>
        </template>
      </a-list>
    </div>

  </aside>
</template>

<style scoped>
.writing-mode { display: flex; flex-direction: column; gap: 6px; font-size: 12px; }
.writing-mode select { padding: 7px; border-radius: 6px; border: 1px solid var(--lt-color-border-secondary); background: transparent; color: inherit; }
.writing-patch { margin-top: 10px; border-top: 1px solid var(--lt-color-border-secondary); padding-top: 10px; }
.patch-before, .patch-after { margin-top: 8px; padding: 8px; border-radius: 6px; max-height: 180px; overflow: auto; overflow-wrap: anywhere; }
.patch-before { background: var(--lt-color-error-bg); }
.patch-after { background: var(--lt-color-success-bg); }
.patch-before > span, .patch-after > span { font-size: 11px; opacity: .7; }

.writing-preview { border: 1px solid var(--lt-color-border-secondary); border-radius: 8px; padding: 12px; }
.writing-preview-body { max-height: 320px; overflow: auto; overflow-wrap: anywhere; }
.writing-preview button { margin-top: 8px; cursor: pointer; }
.writing-preview button:disabled { cursor: default; opacity: .5; }

.agent-sidebar {
  width: 340px;
  flex: 0 0 340px;
  border-left: 1px solid var(--lt-color-border-secondary);
  padding-left: 16px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  max-height: 72vh;
  overflow-y: auto !important;
}

.agent-header h3 {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: var(--lt-color-text);
}

.agent-header p {
  margin: 4px 0 0;
  color: var(--lt-color-text-tertiary);
  font-size: 12px;
}

.quick-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.send-button {
  margin-top: -4px;
}

.agent-section {
  border-top: 1px solid var(--lt-color-border-secondary);
  padding-top: 12px;
}

.section-title {
  font-size: 13px;
  color: var(--lt-color-text-secondary);
  margin-bottom: 8px;
  font-weight: 600;
}

.agent-meta {
  margin-bottom: 8px;
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

.process-card {
  background: var(--lt-color-bg-spotlight);
  border: 1px solid var(--lt-color-border-secondary);
  border-radius: 8px;
  padding: 12px;
}

.process-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  font-size: 13px;
  font-weight: 600;
  margin-bottom: 8px;
}

/* 状态行：替代原来的假百分比，文案可换行（结束后的汇总比运行中的动作长） */
.process-title strong.process-status {
  max-width: 100%;
  margin-left: auto;
  text-align: right;
  white-space: normal;
  line-height: 1.5;
  color: var(--lt-color-primary);
  font-size: 12px;
}

.process-status.is-error {
  color: var(--lt-color-error);
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
  color: var(--lt-color-text-secondary);
}

.activity-sub {
  margin-top: 2px;
  font-size: 11px;
  line-height: 1.5;
  color: var(--lt-color-text-tertiary);
  white-space: normal;
  word-break: break-word;
}

.activity-meta {
  white-space: nowrap;
  color: var(--lt-color-text-tertiary);
}

.activity-item.running .activity-meta {
  color: var(--lt-color-primary);
}

.activity-item.success .activity-meta {
  color: var(--lt-color-success);
}

.activity-item.failed .activity-title,
.activity-item.failed .activity-sub,
.activity-item.failed .activity-meta {
  color: var(--lt-color-error);
}

.trace-row {
  display: grid;
  grid-template-columns: 10px minmax(0, 1fr) auto;
  gap: 8px;
  align-items: center;
  min-height: 22px;
  font-size: 12px;
  color: var(--lt-color-text-tertiary);
}

.trace-row span:nth-child(2),
.trace-row em {
  min-width: 0;
  overflow: hidden !important;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.trace-row em {
  font-style: normal;
}

.trace-dot {
  width: 7px;
  height: 7px;
  border-radius: 999px;
  background: var(--lt-color-border);
}

.trace-row.running .trace-dot {
  background: var(--lt-color-primary);
  box-shadow: 0 0 0 4px var(--lt-color-primary-bg);
}

.trace-row.success .trace-dot,
.trace-row.completed .trace-dot {
  background: var(--lt-color-success);
}

.trace-row.waiting .trace-dot {
  background: var(--lt-color-warning);
}

.trace-row.failed .trace-dot {
  background: var(--lt-color-error);
}

.answer-container {
  margin-top: 4px;
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
  color: var(--lt-color-text);
}
.expand-btn {
  border: 1px solid var(--lt-color-primary);
  background: var(--lt-color-primary-bg);
  padding: 3px 10px;
  font-size: 12px;
  color: var(--lt-color-primary);
  cursor: pointer;
  border-radius: 6px;
  transition: all 0.2s;
  font-weight: 500;
}
.expand-btn:hover {
  background: var(--lt-color-primary);
  color: var(--lt-color-text-inverse);
}
.answer-box {
  margin-top: 0;
}
.answer-box.collapsed {
  -webkit-line-clamp: 3;
}
.answer-box {
  font-size: 12px;
  line-height: 1.6;
  color: var(--lt-color-text-secondary);
  background: var(--lt-color-bg-spotlight);
  border-radius: 8px;
  padding: 10px 12px;
  white-space: pre-wrap;
  word-break: break-word;
}
.answer-box.collapsed {
  max-height: 88px;
  overflow: hidden;
  position: relative;
  cursor: pointer;
  display: -webkit-box;
  -webkit-line-clamp: 4;
  -webkit-box-orient: vertical;
  -webkit-mask-image: linear-gradient(to bottom, black 60%, transparent 100%);
  mask-image: linear-gradient(to bottom, black 60%, transparent 100%);
}
.answer-box:not(.collapsed) {
  max-height: 360px;
  overflow-y: auto;
}
.answer-box:not(.collapsed)::-webkit-scrollbar {
  width: 4px;
}
.answer-box:not(.collapsed)::-webkit-scrollbar-thumb {
  background: var(--lt-color-border);
  border-radius: 2px;
}

.answer-box:not(.collapsed) {
  max-height: 400px !important;
  overflow-y: auto !important;
  cursor: default;
}

.answer-box.collapsed::after {
  content: '';
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  height: 30px;
  background: linear-gradient(transparent, var(--lt-color-bg-spotlight));
  pointer-events: none;
}

.confirm-actions {
  margin-top: 8px;
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.apply-notice {
  margin: 0;
  padding: 9px 10px;
  border-radius: 6px;
  background: var(--lt-color-success-bg);
  color: var(--lt-color-success);
  font-size: 13px;
}
</style>
