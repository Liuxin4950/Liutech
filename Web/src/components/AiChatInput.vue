<script setup lang="ts">
import { computed } from 'vue'
import Icon from './Icon.vue'

const props = defineProps<{
  modelValue: string
  isLoading: boolean
  expanded?: boolean
  quickPrompts: string[]
  voiceSupported: boolean
  voiceListening: boolean
  voiceInterimText: string
  voiceError: string
}>()

const emit = defineEmits<{
  'update:modelValue': [value: string]
  send: []
  startVoice: []
  stopVoice: []
  applyPrompt: [prompt: string]
}>()

const textValue = computed({
  get: () => props.modelValue,
  set: (value: string) => emit('update:modelValue', value)
})

const canSend = computed(() => props.modelValue.trim().length > 0 && !props.isLoading)
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
  <div class="chat-input" :class="{ expanded, compact: !expanded }">
    <div class="quick-prompts" v-if="quickPrompts.length">
      <button
        v-for="prompt in quickPrompts"
        :key="prompt"
        class="prompt-pill"
        @click="emit('applyPrompt', prompt)"
      >
        {{ prompt }}
      </button>
    </div>

    <div class="input-container">
      <textarea
        v-model="textValue"
        @keypress="handleKeyPress"
        :placeholder="inputPlaceholder"
        rows="1"
        :disabled="isLoading"
      ></textarea>

      <button
        class="voice-toggle-btn"
        :class="{ listening: voiceListening }"
        :disabled="!voiceSupported"
        :title="voiceListening ? '停止语音识别' : (voiceSupported ? '开始语音识别' : '当前浏览器不支持语音输入')"
        @click="toggleVoice"
      >
        <Icon :name="voiceSupported ? (voiceListening ? 'close' : 'mic') : 'micOff'" :size="16" />
      </button>

      <button @click="emit('send')" :disabled="!canSend" class="send-btn" title="发送消息">
        {{ isLoading ? '发送中' : '发送' }}
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
  background: rgba(255, 255, 255, 0.88);
  backdrop-filter: blur(14px);
  -webkit-backdrop-filter: blur(14px);
  box-shadow: 0 18px 40px rgba(15, 23, 42, 0.08);
}

.chat-input.expanded {
  border-radius: 0;
  background: #ffffff;
  backdrop-filter: none;
  -webkit-backdrop-filter: none;
  box-shadow: none;
}

.chat-input.compact {
  border-radius: 0 0 16px 16px;
}

.quick-prompts {
  display: inline-flex;
  gap: 8px;
  align-self: flex-start;
  flex-wrap: wrap;
}

.prompt-pill {
  display: inline-flex;
  align-items: center;
  padding: 8px 12px;
  border: 1px solid var(--border-light);
  border-radius: 999px;
  background: var(--bg-hover);
  color: var(--text-subtle);
  cursor: pointer;
  transition: all 0.2s ease;
  font-size: 13px;
}

.prompt-pill:hover {
  background: var(--bg-active);
  border-color: #bfdbfe;
  color: var(--color-primary);
}

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
  background: #fef2f2;
  border-color: #fecaca;
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
  color: white;
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
