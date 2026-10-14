<script setup lang="ts">
import { computed } from 'vue'
import Icon from './Icon.vue'

const props = defineProps<{
  modelValue: string
  isLoading: boolean
  responseActive?: boolean
  expanded?: boolean
  voiceSupported: boolean
  voiceListening: boolean
  voiceInterimText: string
  voiceError: string
  dialogueStatus?: string
  handsFree?: boolean
  handsFreeAvailable?: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [value: string]
  send: []
  stopReply: []
  startVoice: []
  stopVoice: []
  toggleHandsFree: []
}>()

const textValue = computed({
  get: () => props.modelValue,
  set: (value: string) => emit('update:modelValue', value)
})

const canSend = computed(() => props.modelValue.trim().length > 0)
const responseBusy = computed(() => props.responseActive ?? props.isLoading)
const inputPlaceholder = computed(() => {
  if (props.voiceListening) {
    return props.voiceInterimText || '正在识别语音...'
  }
  if (props.voiceError) {
    return props.voiceError
  }
  return '输入消息...'
})

const handleKeyPress = (event: KeyboardEvent) => {
  if (event.key === 'Enter' && !event.shiftKey) {
    event.preventDefault()
    if (canSend.value) {
      emit('send')
    }
  }
}

const toggleVoice = () => {
  if (!props.voiceSupported) return
  if (props.voiceListening) {
    emit('stopVoice')
    return
  }
  emit('startVoice')
}
</script>

<template>
  <div data-onboarding="chat-input" class="chat-input" :class="{ expanded, compact: !expanded }">
    <div class="voice-options">
      <button
        class="hands-free-btn"
        :class="{ active: handsFree }"
        :aria-pressed="!!handsFree"
        :disabled="!voiceSupported || (!handsFree && !handsFreeAvailable)"
        :title="!voiceSupported ? '当前浏览器不支持语音输入' : (!handsFreeAvailable ? '免提需要显示看板娘、开启语音并使用流式回复' : '轮流对话：说完自动发送，回复播完自动聆听；点击麦克风可打断')"
        @click="emit('toggleHandsFree')"
      >{{ handsFree ? '退出免提' : '免提对话' }}</button>
      <span v-if="dialogueStatus && !voiceError" role="status">{{ dialogueStatus }}</span>
      <span v-if="voiceError" class="voice-error" role="status">{{ voiceError }}</span>
    </div>
    <div class="input-container">
      <textarea
        v-model="textValue"
        @keypress="handleKeyPress"
        :placeholder="inputPlaceholder"
        rows="1"
      ></textarea>

      <button
        class="voice-toggle-btn"
        :class="{ listening: voiceListening }"
        :disabled="!voiceSupported"
        :aria-pressed="voiceListening"
        :title="voiceListening ? '停止语音识别' : (voiceSupported ? '接话（打断当前回复）' : '当前浏览器不支持语音输入')"
        @click="toggleVoice"
      >
        <Icon :name="voiceSupported ? (voiceListening ? 'close' : 'mic') : 'micOff'" :size="16" />
      </button>

      <button @click="canSend ? emit('send') : emit('stopReply')" :disabled="!canSend && !responseBusy" class="send-btn" :title="canSend ? (responseBusy ? '接话并发送新消息' : '发送消息') : (responseBusy ? '停止当前回复' : '发送消息')">
        {{ canSend ? '发送' : (responseBusy ? '停止' : '发送') }}
      </button>
    </div>
  </div>
</template>

<style scoped>
.chat-input {
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 16px;
  border: 1px solid var(--border-light);
  background: var(--bg-card);
  box-shadow: var(--shadow-xl);
}

.chat-input.expanded {
  border-radius: 0;
  background: var(--bg-card);
  box-shadow: none;
}

.chat-input.compact {
  border-radius: 0 0 16px 16px;
}

.voice-options {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: var(--text-subtle);
  flex-wrap: wrap;
}
.hands-free-btn {
  padding: 4px 8px;
  border: 1px solid var(--border-light);
  border-radius: 8px;
  background: var(--bg-hover);
  color: var(--text-main);
  cursor: pointer;
}
.hands-free-btn.active { color: var(--color-primary); border-color: var(--color-primary); }
.hands-free-btn:disabled { opacity: 0.5; cursor: not-allowed; }
.voice-error { color: var(--color-error); }

.input-container {
  position: relative;
  display: flex;
  gap: 8px;
  align-items: flex-end;
}

.input-container textarea {
  flex: 1;
  padding: 10px 16px;
  border: 1px solid var(--border-light);
  border-radius: 12px;
  font-size: 14px;
  font-family: inherit;
  resize: none;
  outline: none;
  background: var(--bg-main);
  color: var(--text-main);
  min-height: 44px;
  max-height: 132px;
}

.input-container textarea:focus {
  border-color: var(--color-primary);
}

.input-container textarea:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.voice-toggle-btn,
.send-btn,
.voice-control-btn,
.voice-text-btn {
  min-height: 44px;
  border: none;
  border-radius: 12px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s ease;
}

.voice-toggle-btn {
  min-width: 44px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: var(--bg-hover);
  color: var(--text-main);
  border: 1px solid var(--border-light);
}

.voice-toggle-btn.listening {
  background: var(--bg-error);
  border-color: var(--color-error);
  color: var(--color-error);
}

.voice-toggle-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.send-btn,
.voice-control-btn {
  padding: 0 16px;
  background: var(--color-primary);
  color: var(--text-on-primary);
}

.voice-control-btn.listening {
  background: var(--color-error);
}

.voice-text-btn {
  padding: 0 16px;
  background: var(--bg-hover);
  color: var(--text-main);
  border: 1px solid var(--border-light);
}

.send-btn:hover:not(:disabled),
.voice-control-btn:hover,
.voice-text-btn:hover,
.voice-toggle-btn:hover:not(:disabled) {
  transform: translateY(-1px);
}

.send-btn:disabled {
  background: var(--bg-hover);
  color: var(--text-subtle);
  cursor: not-allowed;
  transform: none;
}

</style>
