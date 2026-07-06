<script setup lang="ts">
import { computed } from 'vue'
import { useShortcutRegistry } from '@/composables/useShortcuts'

/**
 * 快捷键帮助面板：按 ? 打开，展示所有已注册的快捷键
 */
defineProps<{ open: boolean }>()
const emit = defineEmits<{ (e: 'update:open', v: boolean): void }>()

const registry = useShortcutRegistry()

/** 按功能分组：导航 / 操作 / 全局 */
const grouped = computed(() => {
  const groups: Record<string, { key: string; description: string }[]> = {
    '导航': [],
    '操作': [],
    '全局': [],
  }
  for (const s of registry.value) {
    if (!s.description) continue
    const category = s.key.startsWith('g ') ? '导航' :
      (s.key.includes('ctrl') || s.key.includes('meta') || s.key === '?' || s.key === 'Escape' || s.key === '/') ? '全局' :
      '操作'
    groups[category].push({ key: s.key, description: s.description })
  }
  return Object.entries(groups).filter(([, list]) => list.length > 0)
})

/** 把 'ctrl+k' 拆成 ['Ctrl', 'K']，'g h' 拆成 ['G', 'H'] */
function keyParts(key: string): string[] {
  if (key.includes(' ')) {
    return key.split(' ').map((s) => s.toUpperCase())
  }
  return key.split('+').map((s) => {
    if (s === 'ctrl') return 'Ctrl'
    if (s === 'meta') return '⌘'
    if (s === 'alt') return 'Alt'
    if (s === 'shift') return 'Shift'
    return s.length === 1 ? s.toUpperCase() : s
  })
}
</script>

<template>
  <a-modal
    :open="open"
    title="键盘快捷键"
    :footer="null"
    :width="480"
    @update:open="(v: boolean) => emit('update:open', v)"
  >
    <div class="lt-shortcuts">
      <section v-for="[title, list] in grouped" :key="title" class="lt-shortcuts__section">
        <h4 class="lt-shortcuts__title">{{ title }}</h4>
        <div class="lt-shortcuts__list">
          <div v-for="s in list" :key="s.key" class="lt-shortcuts__row">
            <span class="lt-shortcuts__desc">{{ s.description }}</span>
            <span class="lt-shortcuts__keys">
              <template v-for="(part, i) in keyParts(s.key)" :key="i">
                <kbd>{{ part }}</kbd>
                <span v-if="i < keyParts(s.key).length - 1 && !s.key.includes(' ')" class="lt-shortcuts__plus">+</span>
                <span v-else-if="i < keyParts(s.key).length - 1" class="lt-shortcuts__then">然后</span>
              </template>
            </span>
          </div>
        </div>
      </section>

      <div v-if="!grouped.length" class="lt-shortcuts__empty">
        暂无快捷键
      </div>
    </div>
  </a-modal>
</template>

<style scoped>
.lt-shortcuts {
  display: flex;
  flex-direction: column;
  gap: var(--lt-space-lg);
}

.lt-shortcuts__section {
  display: flex;
  flex-direction: column;
  gap: var(--lt-space-sm);
}

.lt-shortcuts__title {
  margin: 0;
  font-size: var(--lt-font-size-xs);
  font-weight: var(--lt-font-weight-semibold);
  color: var(--lt-color-text-tertiary);
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.lt-shortcuts__list {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.lt-shortcuts__row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--lt-space-xs) var(--lt-space-sm);
  border-radius: var(--lt-radius-sm);
  transition: background var(--lt-duration-fast) var(--lt-ease-in-out);
}
.lt-shortcuts__row:hover {
  background: var(--lt-color-hover-bg);
}

.lt-shortcuts__desc {
  color: var(--lt-color-text);
  font-size: var(--lt-font-size-sm);
}

.lt-shortcuts__keys {
  display: inline-flex;
  align-items: center;
  gap: var(--lt-space-xs);
  font-family: var(--lt-font-family-mono);
}
.lt-shortcuts__keys kbd {
  padding: 2px 8px;
  border: 1px solid var(--lt-color-border-secondary);
  border-bottom-width: 2px;
  border-radius: var(--lt-radius-sm);
  background: var(--lt-color-bg-spotlight);
  color: var(--lt-color-text-secondary);
  font-size: var(--lt-font-size-xs);
  font-family: var(--lt-font-family-mono);
  min-width: 20px;
  text-align: center;
}
.lt-shortcuts__plus {
  color: var(--lt-color-text-tertiary);
  font-size: var(--lt-font-size-xs);
}
.lt-shortcuts__then {
  color: var(--lt-color-text-tertiary);
  font-size: var(--lt-font-size-xs);
  margin: 0 2px;
}

.lt-shortcuts__empty {
  padding: var(--lt-space-xl);
  text-align: center;
  color: var(--lt-color-text-tertiary);
}
</style>
