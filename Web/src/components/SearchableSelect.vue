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
  hideSearch?: boolean
  creatable?: boolean
  createLabel?: string
}>(), {
  multiple: false,
  placeholder: '请选择',
  searchPlaceholder: '搜索...',
  hideSearch: false,
  creatable: false,
  createLabel: '新建',
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
    <div class="ss-trigger" :class="{ open }" @click="toggleOpen">
      <div v-if="multiple && selectedLabels.length" class="ss-chips">
        <span v-for="(label, i) in selectedLabels" :key="i" class="ss-chip">
          {{ label }}
          <span class="ss-chip-x" @click.stop="remove(selectedValues[i])">×</span>
        </span>
      </div>
      <span v-else-if="selectedLabels.length" class="ss-single">{{ selectedLabels[0] }}</span>
      <span v-else class="ss-placeholder">{{ placeholder }}</span>
      <Icon name="chevronDown" size="14" class="ss-arrow" :class="{ open }" />
    </div>

    <div v-if="open" class="ss-dropdown">
      <div v-if="!hideSearch" class="ss-search">
        <div class="search-box ss-search-box">
          <input
            ref="searchInputRef"
            v-model="query"
            type="text"
            :placeholder="searchPlaceholder"
            class="search-input"
            @keyup.enter="onEnter"
          />
          <Icon name="search" size="14" class="search-icon" />
        </div>
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
  border: 1px solid var(--border-base);
  border-radius: 6px;
  background: var(--bg-soft);
  cursor: pointer;
  transition: border-color 0.2s, box-shadow 0.2s;
  &:hover { border-color: var(--color-primary); }
  /* 展开态与全局输入框 focus 描边一致 */
  &.open {
    border-color: var(--color-primary);
    box-shadow: 0 0 0 2px rgba(var(--color-primary-rgb), 0.1);
  }
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
  background: rgba(var(--color-primary-rgb), 0.1);
  color: var(--color-primary);
  border-radius: 6px;
  font-size: 0.75rem;
}

.ss-chip-x {
  cursor: pointer;
  opacity: 0.6;
  &:hover { opacity: 1; }
}

.ss-single {
  flex: 1;
  color: var(--text-main);
  font-size: 0.875rem;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ss-placeholder {
  flex: 1;
  color: var(--text-muted);
  font-size: 0.875rem;
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
  border: 1px solid var(--border-soft);
  border-radius: 8px;
  box-shadow: 0 6px 20px rgba(0, 0, 0, 0.12);
  overflow: hidden;
}

/* 展开态搜索框：复用全局 .search-input（styles.scss 唯一实现），面板内放开 max-width */
.ss-search {
  padding: 8px;
  border-bottom: 1px solid var(--border-light);
}

.ss-search-box {
  flex: 1;
  max-width: none;
}

.ss-options {
  max-height: 220px;
  overflow-y: auto;
  padding: 4px;
}

.ss-empty {
  padding: 16px;
  text-align: center;
  color: var(--text-muted);
  font-size: 0.875rem;
}

/* 选项：8px 圆角条目 + hover bg-hover（与全局 .list-item 反馈一致） */
.ss-option {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  border-radius: 8px;
  cursor: pointer;
  font-size: 0.875rem;
  color: var(--text-main);
  transition: background 0.15s;
  &:hover { background: var(--bg-hover); }
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
  border-top: 1px dashed var(--border-soft);
  color: var(--color-primary);
  font-size: 0.875rem;
  cursor: pointer;
  &:hover { background: var(--bg-hover); }
}
</style>
