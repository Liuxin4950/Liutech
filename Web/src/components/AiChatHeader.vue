<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import type { ChatMode } from '@/stores/chat'
import Icon from './Icon.vue'

const props = defineProps<{
  expanded?: boolean
  mode: ChatMode
  isGuestMode: boolean
  ttsEnabled: boolean
  ttsAvailable: boolean
  ttsToggleTitle: string
  showHistoryButton: boolean
  showModelToggleButton: boolean
  modelVisible: boolean
}>()

const emit = defineEmits<{
  expand: []
  close: []
  clear: []
  toggleHistory: []
  toggleTts: []
  toggleModel: []
  setMode: [mode: ChatMode]
}>()

const isModeDropdownOpen = ref(false)

const modeLabel = computed(() => props.mode === 'stream' ? '流式模式' : '普通模式')
const sessionLabel = computed(() => props.isGuestMode ? '游客体验' : '已登录')
const modeToggleTitle = () => `当前：${modeLabel.value}，点击切换模式`
const expandToggleTitle = () => props.expanded ? '退出大窗模式' : '进入大窗模式'
const modelToggleTitle = () => props.modelVisible ? '隐藏模型' : '显示模型'
const identityTitle = computed(() => `纳西妲 · ${modeLabel.value} · ${sessionLabel.value}`)
const guestBadgeTitle = computed(() => props.isGuestMode ? '当前为游客体验模式' : '当前为已登录模式')

const handleClickOutside = (event: MouseEvent) => {
  const target = event.target as HTMLElement
  if (!target.closest('.mode-selector')) {
    isModeDropdownOpen.value = false
  }
}

const selectMode = (mode: ChatMode) => {
  emit('setMode', mode)
  isModeDropdownOpen.value = false
}

onMounted(() => {
  document.addEventListener('click', handleClickOutside)
})

onUnmounted(() => {
  document.removeEventListener('click', handleClickOutside)
})
</script>

<template>
  <div class="chat-header" :class="{ compact: !expanded }">
    <div class="header-left">
      <div class="assistant-identity" :title="identityTitle">
        <div class="assistant-avatar" :class="mode">
          <Icon name="bot" :size="18" />
          <span class="assistant-mode-dot" :class="mode"></span>
          <span v-if="isGuestMode" class="assistant-guest-badge" :title="guestBadgeTitle">
            <Icon name="user" :size="10" />
          </span>
        </div>
        <div v-if="expanded" class="assistant-meta">
          <h3>纳西妲</h3>
        </div>
      </div>
    </div>

    <div class="header-right">
      <div class="mode-selector">
        <button
          class="icon-action-btn mode-toggle-btn"
          :class="{ active: mode === 'stream' }"
          @click.stop="isModeDropdownOpen = !isModeDropdownOpen"
          :title="modeToggleTitle()"
        >
          <Icon :name="mode === 'stream' ? 'zap' : 'message'" :size="16" />
        </button>

        <div v-show="isModeDropdownOpen" class="mode-dropdown">
          <button :class="['mode-option', { active: mode === 'stream' }]" @click="selectMode('stream')">
            <span class="mode-option-dot stream"></span>
            流式模式（实时显示）
          </button>
          <button :class="['mode-option', { active: mode === 'normal' }]" @click="selectMode('normal')">
            <span class="mode-option-dot normal"></span>
            普通模式（等待完整回复）
          </button>
        </div>
      </div>

      <button
        class="icon-action-btn tts-toggle-btn"
        :class="{ 'is-on': ttsEnabled && ttsAvailable }"
        :disabled="!ttsAvailable"
        :title="ttsToggleTitle"
        @click="emit('toggleTts')"
      >
        <Icon name="music" :size="16" />
      </button>

      <button
        v-if="showHistoryButton"
        class="icon-action-btn history-btn"
        @click="emit('toggleHistory')"
        title="查看会话历史"
      >
        <Icon name="history" :size="16" />
      </button>

      <button
        v-if="showModelToggleButton"
        class="icon-action-btn model-btn"
        :class="{ active: !modelVisible }"
        @click="emit('toggleModel')"
        :title="modelToggleTitle()"
      >
        <Icon :name="modelVisible ? 'eyeOff' : 'eye'" :size="16" />
      </button>

      <button class="icon-action-btn control-btn" @click="emit('clear')" title="清空聊天">
        <Icon name="trash2" :size="16" />
      </button>

      <button class="icon-action-btn expand-btn" :class="{ active: !!expanded }" @click="emit('expand')" :title="expandToggleTitle()">
        <Icon name="layout" :size="16" />
      </button>

      <button class="icon-action-btn close-btn" @click="emit('close')" title="关闭聊天窗口">
        <Icon name="close" :size="16" />
      </button>
    </div>
  </div>
</template>

<style scoped>
.chat-header {
  width: 100%;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  padding: 16px 20px;
  border: 1px solid var(--border-light);
  border-radius: 18px;
  background: var(--bg-card);
  box-shadow: 0 18px 40px rgba(15, 23, 42, 0.08);
}

.chat-header:not(.compact) {
  background: var(--bg-card);
  border-radius: 0;
  box-shadow: none;
}

.chat-header.compact {
  padding: 12px 14px;
  border-radius: 16px 16px 0 0;
  border-bottom: none;
  box-shadow: none;
}

.header-left {
  display: flex;
  align-items: center;
  min-width: 0;
  flex: 1;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-left: auto;
  flex-shrink: 0;
}

.assistant-identity {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}

.assistant-avatar {
  width: 36px;
  height: 36px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 12px;
  background: var(--bg-soft);
  border: 1px solid var(--border-light);
  color: var(--color-primary);
  position: relative;
  flex-shrink: 0;
}

.assistant-avatar.stream {
  color: var(--color-success);
}

.assistant-meta {
  min-width: 0;
}

.assistant-meta h3 {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: var(--text-title);
}

.assistant-mode-dot {
  position: absolute;
  right: 4px;
  bottom: 4px;
  width: 9px;
  height: 9px;
  border-radius: 999px;
  border: 2px solid var(--bg-card);
  background: var(--color-primary);
}

.assistant-mode-dot.stream {
  background: var(--color-success);
}

.assistant-mode-dot.normal {
  background: var(--color-primary);
}

.assistant-guest-badge {
  position: absolute;
  top: -4px;
  left: -4px;
  width: 18px;
  height: 18px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 999px;
  background: var(--bg-warning);
  color: var(--color-warning);
  border: 1px solid var(--border-light);
}

.icon-action-btn {
  width: 36px;
  height: 36px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 0;
  border-radius: 10px;
  border: 1px solid var(--border-light);
  background: var(--bg-hover);
  color: var(--text-main);
  cursor: pointer;
  transition: all 0.2s ease;
  flex-shrink: 0;
}

.icon-action-btn:hover:not(:disabled) {
  background: var(--bg-active);
  border-color: var(--color-primary);
  color: var(--color-primary);
  transform: translateY(-1px);
}

.icon-action-btn:disabled {
  opacity: 0.55;
  cursor: not-allowed;
  transform: none;
}

.mode-selector {
  position: relative;
}

.mode-toggle-btn.active {
  color: var(--color-success);
  border-color: var(--color-success);
  background: var(--bg-success);
}

.tts-toggle-btn.is-on {
  border-color: var(--color-primary);
  background: var(--bg-active);
  color: var(--color-primary);
}

.expand-btn.active,
.model-btn.active {
  border-color: var(--color-primary);
  background: var(--bg-active);
  color: var(--color-primary);
}

.mode-dropdown {
  position: absolute;
  top: 100%;
  right: 0;
  margin-top: 4px;
  background: var(--bg-card);
  border: 1px solid var(--border-light);
  border-radius: 8px;
  box-shadow: var(--shadow-lg);
  overflow: hidden;
  z-index: 1000;
  min-width: 200px;
}

.mode-option {
  width: 100%;
  padding: 10px 12px;
  background: none;
  border: none;
  text-align: left;
  cursor: pointer;
  transition: background-color 0.2s ease;
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 0.875rem;
  color: var(--text-main);
}

.mode-option:hover {
  background: var(--bg-hover);
}

.mode-option.active {
  background: var(--bg-active);
  color: var(--color-primary);
}

.mode-option-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  flex-shrink: 0;
}

.mode-option-dot.stream {
  background-color: var(--color-success);
}

.mode-option-dot.normal {
  background-color: var(--color-primary);
}
</style>
