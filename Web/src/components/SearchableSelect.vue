<script setup lang="ts">
import { ref, computed, nextTick, onMounted, onUnmounted } from 'vue'
import Icon from '@/components/Icon.vue'

interface Option {
  label: string
  value: string | number
}

const props = withDefaults(defineProps<{
  options: Option[]
  modelValue: string | number | (string | number)[]
  multiple?: boolean
  placeholder?: string
  searchPlaceholder?: string
  creatable?: boolean
  createLabel?: string
  clearable?: boolean
}>(), {
  multiple: false,
  placeholder: '请选择',
  searchPlaceholder: '搜索...',
  creatable: false,
  createLabel: '新建',
  clearable: false,
})

const emit = defineEmits<{
  'update:modelValue': [value: string | number | (string | number)[]]
  'create': []
}>()

const open = ref(false)
const query = ref('')
const rootRef = ref<HTMLElement>()
const searchInputRef = ref<HTMLInputElement>()

const selectedValues = computed<(string | number)[]>(() => {
  const v = props.modelValue
  if (Array.isArray(v)) return props.multiple ? v : []
  return (v === '' || v === null || v === undefined) ? [] : [v]
})

const selectedLabels = computed(() =>
  selectedValues.value.map(v => {
    const opt = props.options.find(o => o.value === v)
    return opt ? opt.label : String(v)
  })
)

const filteredOptions = computed(() => {
  const q = query.value.trim().toLowerCase()
  if (!q) return props.options
  return props.options.filter(o => o.label.toLowerCase().includes(q))
})

const isSelected = (value: string | number) => selectedValues.value.includes(value)

const toggleOption = (opt: Option) => {
  if (props.multiple) {
    const next = isSelected(opt.value)
      ? selectedValues.value.filter(v => v !== opt.value)
      : [...selectedValues.value, opt.value]
    emit('update:modelValue', next)
  } else {
    emit('update:modelValue', opt.value)
    open.value = false
  }
  query.value = ''
}

const remove = (value: string | number) => {
  if (props.multiple) {
    emit('update:modelValue', selectedValues.value.filter(v => v !== value))
  } else {
    emit('update:modelValue', '')
  }
}

const toggleOpen = () => {
  open.value = !open.value
  if (open.value) {
    query.value = ''
    nextTick(() => searchInputRef.value?.focus())
  }
}

const onEnter = () => {
  if (filteredOptions.value.length > 0) {
    toggleOption(filteredOptions.value[0])
  } else if (props.creatable) {
    onCreate()
  }
}

const onCreate = () => {
  open.value = false
  emit('create')
}

const handleClickOutside = (e: MouseEvent) => {
  if (rootRef.value && !rootRef.value.contains(e.target as Node)) {
    open.value = false
  }
}

onMounted(() => document.addEventListener('click', handleClickOutside))
onUnmounted(() => document.removeEventListener('click', handleClickOutside))
</script>

<template>
  <div class="ss-select" ref="rootRef">
    <div class="ss-trigger" @click="toggleOpen">
      <div v-if="multiple && selectedLabels.length" class="ss-chips">
        <span v-for="(label, i) in selectedLabels" :key="i" class="ss-chip">
          {{ label }}
          <span class="ss-chip-x" @click.stop="remove(selectedValues[i])">×</span>
        </span>
      </div>
      <span v-else-if="selectedLabels.length" class="ss-single">{{ selectedLabels[0] }}</span>
      <span v-else class="ss-placeholder">{{ placeholder }}</span>
      <span
        v-if="clearable && !multiple && selectedLabels.length"
        class="ss-clear"
        title="清除"
        @click.stop="remove(selectedValues[0])"
      >×</span>
      <Icon name="chevronDown" size="14" class="ss-arrow" :class="{ open }" />
    </div>

    <div v-if="open" class="ss-dropdown">
      <div class="ss-search">
        <Icon name="search" size="14" class="ss-search-icon" />
        <input
          ref="searchInputRef"
          v-model="query"
          type="text"
          :placeholder="searchPlaceholder"
          @keyup.enter="onEnter"
        />
      </div>
      <div class="ss-options">
        <div v-if="filteredOptions.length === 0" class="ss-empty">无匹配项</div>
        <div
          v-for="opt in filteredOptions"
          :key="opt.value"
          class="ss-option"
          :class="{ selected: isSelected(opt.value) }"
          @click="toggleOption(opt)"
        >
          <span class="ss-option-label">{{ opt.label }}</span>
          <Icon v-if="isSelected(opt.value)" name="check" size="14" class="ss-check" />
        </div>
      </div>
      <div v-if="creatable" class="ss-create" @click="onCreate">
        <Icon name="plus" size="14" />
        <span>{{ createLabel }}</span>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
@use "@/assets/styles/tokens" as *;

.ss-select {
  position: relative;
  width: 100%;
}

.ss-trigger {
  display: flex;
  align-items: center;
  gap: 6px;
  min-height: 40px;
  padding: 6px 10px;
  border: 1px solid var(--color-border, #d9d9d9);
  border-radius: 6px;
  background: var(--bg-card, #fff);
  cursor: pointer;
  transition: border-color 0.2s;
  &:hover { border-color: var(--color-primary); }
}

.ss-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  flex: 1;
}

.ss-chip {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 2px 8px;
  background: rgba(var(--color-primary-rgb, 0, 123, 255), 0.1);
  color: var(--color-primary);
  border-radius: 4px;
  font-size: 12px;
}

.ss-chip-x {
  cursor: pointer;
  opacity: 0.6;
  &:hover { opacity: 1; }
}

.ss-single {
  flex: 1;
  color: var(--text-main);
  font-size: 14px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ss-placeholder {
  flex: 1;
  color: var(--text-muted);
  font-size: 14px;
}

.ss-clear {
  color: var(--text-muted);
  font-size: 16px;
  line-height: 1;
  padding: 0 2px;
  &:hover { color: var(--text-main); }
}

.ss-arrow {
  color: var(--text-muted);
  transition: transform 0.2s;
  flex-shrink: 0;
  &.open { transform: rotate(180deg); }
}

.ss-dropdown {
  position: absolute;
  top: calc(100% + 4px);
  left: 0;
  right: 0;
  z-index: 100;
  background: var(--bg-card, #fff);
  border: 1px solid var(--color-border, #e0e0e0);
  border-radius: 8px;
  box-shadow: 0 6px 20px rgba(0, 0, 0, 0.12);
  overflow: hidden;
}

.ss-search {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  border-bottom: 1px solid var(--color-border, #f0f0f0);
  .ss-search-icon { color: var(--text-muted); flex-shrink: 0; }
  input {
    flex: 1;
    border: none;
    outline: none;
    background: transparent;
    font-size: 13px;
    color: var(--text-main);
  }
}

.ss-options {
  max-height: 220px;
  overflow-y: auto;
  padding: 4px 0;
}

.ss-empty {
  padding: 16px;
  text-align: center;
  color: var(--text-muted);
  font-size: 13px;
}

.ss-option {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  cursor: pointer;
  font-size: 13px;
  color: var(--text-main);
  transition: background 0.15s;
  &:hover { background: rgba(var(--color-primary-rgb, 0, 123, 255), 0.06); }
  &.selected { color: var(--color-primary); font-weight: 500; }
  .ss-check { color: var(--color-primary); }
}

.ss-option-label {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ss-create {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px 12px;
  border-top: 1px dashed var(--color-border, #e0e0e0);
  color: var(--color-primary);
  font-size: 13px;
  cursor: pointer;
  &:hover { background: rgba(var(--color-primary-rgb, 0, 123, 255), 0.06); }
}
</style>
