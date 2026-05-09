<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import type { ChatMode } from '@/stores/chat'
import Icon from './Icon.vue'
import { BREAKPOINT_MD } from '@/utils/breakpoints'

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
const showMoreMenu = ref(false)
const isMobile = ref(typeof window !== 'undefined' ? window.innerWidth < BREAKPOINT_MD : false)
const toolbarIconSize = 15

const modeLabel = computed(() => props.mode === 'stream' ? '实时响应' : '完整响应')
const sessionLabel = computed(() => props.isGuestMode ? '游客体验' : '已登录')
const modeToggleTitle = () => `当前：${modeLabel.value}，点击切换回复方式`
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

const handleMoreClickOutside = (event: MouseEvent) => {
  const target = event.target as HTMLElement
  if (!target.closest('.more-menu-container')) {
    showMoreMenu.value = false
  }
}

const handleResize = () => {
  isMobile.value = window.innerWidth < BREAKPOINT_MD
  if (!isMobile.value) showMoreMenu.value = false
}

const selectMode = (mode: ChatMode) => {
  emit('setMode', mode)
  isModeDropdownOpen.value = false
}

onMounted(() => {
  document.addEventListener('click', handleClickOutside)
  document.addEventListener('click', handleMoreClickOutside)
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  document.removeEventListener('click', handleClickOutside)
  document.removeEventListener('click', handleMoreClickOutside)
  window.removeEventListener('resize', handleResize)
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
      <!-- Desktop or compact: show all buttons -->
      <template v-if="!isMobile || !expanded">
      <div class="mode-selector">
        <button
          class="icon-action-btn mode-toggle-btn"
          :class="{ active: mode === 'stream' }"
          @click.stop="isModeDropdownOpen = !isModeDropdownOpen"
          :title="modeToggleTitle()"
        >
          <Icon :name="mode === 'stream' ? 'zap' : 'message'" :size="toolbarIconSize" />
        </button>

        <div v-show="isModeDropdownOpen" class="mode-dropdown">
          <button :class="['mode-option', { active: mode === 'stream' }]" @click="selectMode('stream')">
            <span class="mode-option-dot stream"></span>
            实时响应
          </button>
          <button :class="['mode-option', { active: mode === 'normal' }]" @click="selectMode('normal')">
            <span class="mode-option-dot normal"></span>
            完整响应
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
        <Icon name="music" :size="toolbarIconSize" />
      </button>

      <button
        v-if="showHistoryButton"
        class="icon-action-btn history-btn"
        @click="emit('toggleHistory')"
        title="查看会话历史"
      >
        <Icon name="conversations" :size="toolbarIconSize" />
      </button>

      <button
        v-if="showModelToggleButton"
        class="icon-action-btn model-btn"
        :class="{ active: !modelVisible }"
        @click="emit('toggleModel')"
        :title="modelToggleTitle()"
      >
        <Icon :name="modelVisible ? 'eyeOff' : 'eye'" :size="toolbarIconSize" />
      </button>

      <button class="icon-action-btn control-btn" @click="emit('clear')" title="清空聊天">
        <Icon name="trash2" :size="toolbarIconSize" />
      </button>

      <button class="icon-action-btn expand-btn" :class="{ active: !!expanded }" @click="emit('expand')" :title="expandToggleTitle()">
        <Icon name="layout" :size="toolbarIconSize" />
      </button>

      <button v-if="expanded" class="icon-action-btn close-btn" @click="emit('close')" title="关闭聊天窗口">
        <Icon name="close" :size="toolbarIconSize" />
      </button>
      </template>

      <!-- Mobile expanded: mode + more menu -->
      <template v-else>
      <div class="mode-selector">
        <button
          class="icon-action-btn mode-toggle-btn"
          :class="{ active: mode === 'stream' }"
          @click.stop="isModeDropdownOpen = !isModeDropdownOpen"
          :title="modeToggleTitle()"
        >
          <Icon :name="mode === 'stream' ? 'zap' : 'message'" :size="toolbarIconSize" />
        </button>

        <div v-show="isModeDropdownOpen" class="mode-dropdown">
          <button :class="['mode-option', { active: mode === 'stream' }]" @click="selectMode('stream')">
            <span class="mode-option-dot stream"></span>
            实时响应
          </button>
          <button :class="['mode-option', { active: mode === 'normal' }]" @click="selectMode('normal')">
            <span class="mode-option-dot normal"></span>
            完整响应
          </button>
        </div>
      </div>

      <div class="more-menu-container">
        <button
          class="icon-action-btn more-btn"
          @click.stop="showMoreMenu = !showMoreMenu"
          title="更多选项"
        >
          <Icon name="more" :size="toolbarIconSize" />
        </button>

        <div v-show="showMoreMenu" class="more-dropdown">
          <button class="more-option" @click="emit('toggleTts'); showMoreMenu = false" :disabled="!ttsAvailable">
            <Icon name="music" :size="14" />
            <span>{{ ttsEnabled ? '关闭语音' : '开启语音' }}</span>
          </button>
          <button v-if="showHistoryButton" class="more-option" @click="emit('toggleHistory'); showMoreMenu = false">
            <Icon name="conversations" :size="14" />
            <span>会话历史</span>
          </button>
          <button v-if="showModelToggleButton" class="more-option" @click="emit('toggleModel'); showMoreMenu = false">
            <Icon :name="modelVisible ? 'eyeOff' : 'eye'" :size="14" />
            <span>{{ modelVisible ? '隐藏模型' : '显示模型' }}</span>
          </button>
          <button class="more-option" @click="emit('clear'); showMoreMenu = false">
            <Icon name="trash2" :size="14" />
            <span>清空聊天</span>
          </button>
          <button class="more-option" @click="emit('expand'); showMoreMenu = false">
            <Icon name="layout" :size="14" />
            <span>退出大窗</span>
          </button>
          <button class="more-option danger" @click="emit('close'); showMoreMenu = false">
            <Icon name="close" :size="14" />
            <span>关闭聊天</span>
          </button>
        </div>
      </div>
      </template>
    </div>
  </div>
</template>

<style scoped lang="scss">
@use "@/assets/styles/tokens" as *;
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
  transition: all 0.2s ease;
}

.chat-header.compact .assistant-avatar {
  width: 28px;
  height: 28px;
  border-radius: 8px;
  background: transparent;
  border: none;
}

.chat-header.compact .assistant-avatar .assistant-mode-dot {
  width: 7px;
  height: 7px;
  right: 2px;
  bottom: 2px;
  border-width: 1.5px;
}

.chat-header.compact .assistant-avatar .assistant-guest-badge {
  width: 14px;
  height: 14px;
  top: -3px;
  left: -3px;
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

.more-menu-container {
  position: relative;
}

.more-btn {
  background: none;
  border-color: transparent;
}

.more-btn:hover {
  background: var(--bg-hover);
}

.more-dropdown {
  position: absolute;
  top: 100%;
  right: 0;
  margin-top: 4px;
  background: var(--bg-card);
  border: 1px solid var(--border-light);
  border-radius: 10px;
  box-shadow: var(--shadow-lg);
  overflow: hidden;
  z-index: 1000;
  min-width: 160px;
  padding: 4px;
}

.more-option {
  width: 100%;
  padding: 10px 12px;
  background: none;
  border: none;
  text-align: left;
  cursor: pointer;
  transition: background-color 0.15s ease;
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 0.8125rem;
  color: var(--text-main);
  border-radius: 6px;
}

.more-option:hover {
  background: var(--bg-hover);
}

.more-option:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.more-option.danger {
  color: var(--color-error);
}

.more-option.danger:hover {
  background: var(--bg-error);
}

@include respond(md) {
  .chat-header:not(.compact) {
    padding: 10px 12px;
    gap: 8px;
  }

  .chat-header:not(.compact) .assistant-meta h3 {
    font-size: 14px;
  }

  .chat-header:not(.compact) .assistant-avatar {
    width: 30px;
    height: 30px;
  }

  .chat-header:not(.compact) .header-right {
    gap: 4px;
  }

  .chat-header:not(.compact) .icon-action-btn {
    width: 32px;
    height: 32px;
  }
}
</style>
