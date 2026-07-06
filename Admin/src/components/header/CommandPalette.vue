<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { SearchOutlined, RightOutlined, ClockCircleOutlined } from '@ant-design/icons-vue'
import { useI18n } from '@/i18n'

const { t } = useI18n()

/**
 * Ctrl+K / Cmd+K 命令面板：跨页面快速跳转 + 最近访问
 * 数据源：router.options.routes 里所有带 meta.title 的路由
 */
interface CommandItem {
  key: string
  title: string
  path: string
  section?: string
  keywords?: string
}

const props = defineProps<{ open: boolean }>()
const emit = defineEmits<{ (e: 'update:open', v: boolean): void }>()

const router = useRouter()
const query = ref('')
const activeIndex = ref(0)
const inputRef = ref<HTMLInputElement | null>(null)

const RECENTS_KEY = 'lt-command-recents'
const recents = ref<string[]>([])

function loadRecents() {
  try {
    const raw = localStorage.getItem(RECENTS_KEY)
    recents.value = raw ? JSON.parse(raw) : []
  } catch { recents.value = [] }
}
function pushRecent(path: string) {
  const next = [path, ...recents.value.filter((p) => p !== path)].slice(0, 5)
  recents.value = next
  try { localStorage.setItem(RECENTS_KEY, JSON.stringify(next)) } catch { /* ignore */ }
}

/** 展平路由，只留下有 title 的叶子节点 */
const allCommands = computed<CommandItem[]>(() => {
  const out: CommandItem[] = []
  const walk = (routes: any[], base = '') => {
    for (const r of routes) {
      const path = r.path?.startsWith('/') ? r.path : `${base}/${r.path || ''}`.replace(/\/+/g, '/')
      if (r.meta?.title) {
        out.push({
          key: r.name || path,
          title: r.meta.title,
          path,
          section: r.meta.section,
          keywords: `${r.meta.title} ${r.name || ''} ${path}`.toLowerCase(),
        })
      }
      if (r.children?.length) walk(r.children, path)
    }
  }
  walk(router.options.routes as any[])
  return out
})

const filtered = computed<CommandItem[]>(() => {
  const q = query.value.trim().toLowerCase()
  if (!q) {
    // 无输入时展示最近访问 + 全部
    const recentItems = recents.value
      .map((p) => allCommands.value.find((c) => c.path === p))
      .filter(Boolean) as CommandItem[]
    const remaining = allCommands.value.filter((c) => !recents.value.includes(c.path))
    return [...recentItems, ...remaining]
  }
  return allCommands.value.filter((c) => c.keywords?.includes(q))
})

const recentPaths = computed(() => new Set(recents.value))

function close() {
  emit('update:open', false)
  query.value = ''
  activeIndex.value = 0
}

function pickItem(item: CommandItem) {
  pushRecent(item.path)
  router.push(item.path)
  close()
}

function onKeydown(e: KeyboardEvent) {
  if (!props.open) return
  if (e.key === 'ArrowDown') {
    e.preventDefault()
    activeIndex.value = Math.min(activeIndex.value + 1, filtered.value.length - 1)
  } else if (e.key === 'ArrowUp') {
    e.preventDefault()
    activeIndex.value = Math.max(activeIndex.value - 1, 0)
  } else if (e.key === 'Enter') {
    e.preventDefault()
    const it = filtered.value[activeIndex.value]
    if (it) pickItem(it)
  } else if (e.key === 'Escape') {
    e.preventDefault()
    close()
  }
}

watch(() => props.open, (v) => {
  if (v) {
    activeIndex.value = 0
    nextTick(() => inputRef.value?.focus())
  }
})

watch(query, () => { activeIndex.value = 0 })

// Ctrl+K 全局唤起在 TheHeader 的 useShortcuts 里统一注册，这里只管组件内部键盘导航
onMounted(() => {
  loadRecents()
  window.addEventListener('keydown', onKeydown)
})
onUnmounted(() => {
  window.removeEventListener('keydown', onKeydown)
})
</script>

<template>
  <a-modal
    :open="open"
    :footer="null"
    :closable="false"
    :mask-closable="true"
    :width="560"
    :body-style="{ padding: 0 }"
    :destroy-on-close="false"
    wrap-class-name="lt-cmdk-modal"
    @update:open="(v: boolean) => emit('update:open', v)"
  >
    <div class="lt-cmdk">
      <div class="lt-cmdk__input">
        <SearchOutlined class="lt-cmdk__icon" />
        <input
          ref="inputRef"
          v-model="query"
          type="text"
          :placeholder="t('header.searchPlaceholder')"
          class="lt-cmdk__field"
        />
        <span class="lt-cmdk__hint">ESC</span>
      </div>

      <div class="lt-cmdk__list">
        <div v-if="!filtered.length" class="lt-cmdk__empty">
          {{ t('cmdk.empty') }}
        </div>

        <template v-for="(item, idx) in filtered" :key="item.key">
          <div
            v-if="!query && recents.length && idx === 0"
            class="lt-cmdk__section"
          >
            <ClockCircleOutlined /> {{ t('cmdk.recent') }}
          </div>
          <div
            v-if="!query && recents.length && idx === recents.length"
            class="lt-cmdk__section"
          >
            {{ t('cmdk.all') }}
          </div>
          <div
            class="lt-cmdk__item"
            :class="{ 'lt-cmdk__item--active': idx === activeIndex }"
            @mouseenter="activeIndex = idx"
            @click="pickItem(item)"
          >
            <span class="lt-cmdk__item-title">{{ item.title }}</span>
            <span class="lt-cmdk__item-path">{{ item.path }}</span>
            <RightOutlined class="lt-cmdk__item-arrow" />
          </div>
        </template>
      </div>

      <div class="lt-cmdk__footer">
        <span><kbd>↑↓</kbd> {{ t('cmdk.hintMove') }}</span>
        <span><kbd>↵</kbd> {{ t('cmdk.hintOpen') }}</span>
        <span><kbd>Esc</kbd> {{ t('cmdk.hintClose') }}</span>
      </div>
    </div>
  </a-modal>
</template>

<style scoped>
.lt-cmdk {
  display: flex;
  flex-direction: column;
  max-height: 70vh;
}

.lt-cmdk__input {
  display: flex;
  align-items: center;
  gap: var(--lt-space-sm);
  padding: var(--lt-space-md) var(--lt-space-lg);
  border-bottom: 1px solid var(--lt-color-border-secondary);
}

.lt-cmdk__icon {
  color: var(--lt-color-text-tertiary);
  font-size: var(--lt-font-size-md);
}

.lt-cmdk__field {
  flex: 1;
  border: none;
  outline: none;
  background: transparent;
  color: var(--lt-color-text);
  font-size: var(--lt-font-size-md);
}
.lt-cmdk__field::placeholder {
  color: var(--lt-color-text-tertiary);
}

.lt-cmdk__hint {
  font-size: var(--lt-font-size-xs);
  color: var(--lt-color-text-tertiary);
  padding: 2px 6px;
  border: 1px solid var(--lt-color-border-secondary);
  border-radius: var(--lt-radius-sm);
  font-family: var(--lt-font-family-mono);
}

.lt-cmdk__list {
  overflow-y: auto;
  padding: var(--lt-space-xs) 0;
  min-height: 200px;
}

.lt-cmdk__section {
  padding: var(--lt-space-sm) var(--lt-space-lg) var(--lt-space-xs);
  font-size: var(--lt-font-size-xs);
  color: var(--lt-color-text-tertiary);
  display: flex;
  align-items: center;
  gap: var(--lt-space-xs);
}

.lt-cmdk__item {
  display: flex;
  align-items: center;
  gap: var(--lt-space-md);
  padding: var(--lt-space-sm) var(--lt-space-lg);
  cursor: pointer;
  color: var(--lt-color-text);
  transition: background var(--lt-duration-fast) var(--lt-ease-in-out);
}
.lt-cmdk__item--active {
  background: var(--lt-color-hover-bg);
}

.lt-cmdk__item-title {
  flex: 0 0 auto;
  font-weight: var(--lt-font-weight-medium);
}
.lt-cmdk__item-path {
  flex: 1;
  color: var(--lt-color-text-tertiary);
  font-size: var(--lt-font-size-xs);
  font-family: var(--lt-font-family-mono);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.lt-cmdk__item-arrow {
  color: var(--lt-color-text-quaternary);
  font-size: var(--lt-font-size-xs);
}

.lt-cmdk__empty {
  padding: var(--lt-space-2xl) var(--lt-space-lg);
  text-align: center;
  color: var(--lt-color-text-tertiary);
  font-size: var(--lt-font-size-sm);
}

.lt-cmdk__footer {
  display: flex;
  gap: var(--lt-space-lg);
  padding: var(--lt-space-sm) var(--lt-space-lg);
  border-top: 1px solid var(--lt-color-border-secondary);
  font-size: var(--lt-font-size-xs);
  color: var(--lt-color-text-tertiary);
  background: var(--lt-color-bg-spotlight);
}
.lt-cmdk__footer kbd {
  padding: 1px 6px;
  border: 1px solid var(--lt-color-border-secondary);
  border-radius: var(--lt-radius-sm);
  font-family: var(--lt-font-family-mono);
  font-size: var(--lt-font-size-xs);
  background: var(--lt-color-bg-container);
  margin-right: var(--lt-space-xs);
}
</style>

<style>
.lt-cmdk-modal .ant-modal {
  top: 15vh;
}
.lt-cmdk-modal .ant-modal-content {
  padding: 0 !important;
  overflow: hidden;
}
</style>
