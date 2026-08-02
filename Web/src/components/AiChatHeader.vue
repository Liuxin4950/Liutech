<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import type { ChatMode } from '@/stores/chat'
import Icon from './Icon.vue'
import { BREAKPOINT_MD } from '@/utils/breakpoints'

const props = defineProps<{
  expanded?: boolean
  mode: ChatMode
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
const modeToggleTitle = () => `当前：${modeLabel.value}，点击切换回复方式`
const expandToggleTitle = () => props.expanded ? '退出大窗模式' : '进入大窗模式'
const modelToggleTitle = () => props.modelVisible ? '隐藏模型' : '显示模型'

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
        <Icon :name="ttsEnabled && ttsAvailable ? 'volume' : 'volumeOff'" :size="toolbarIconSize" />
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
        <Icon :name="expanded ? 'minimize' : 'maximize'" :size="toolbarIconSize" />
      </button>

      <button class="icon-action-btn close-btn" @click="emit('close')" title="关闭聊天窗口">
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
            <Icon :name="ttsEnabled && ttsAvailable ? 'volume' : 'volumeOff'" :size="14" />
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
        </div>
      </div>

      <button class="icon-action-btn expand-btn" :class="{ active: !!expanded }" @click="emit('expand')" :title="expandToggleTitle()">
        <Icon :name="expanded ? 'minimize' : 'maximize'" :size="toolbarIconSize" />
      </button>

      <button class="icon-action-btn close-btn" @click="emit('close')" title="关闭聊天窗口">
        <Icon name="close" :size="toolbarIconSize" />
      </button>
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
  box-shadow: var(--shadow-xl);
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

.header-right {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-left: auto;
  flex-shrink: 0;
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

  .chat-header:not(.compact) .header-right {
    gap: 4px;
  }

  .chat-header:not(.compact) .icon-action-btn {
    width: 32px;
    height: 32px;
  }
}
</style>
