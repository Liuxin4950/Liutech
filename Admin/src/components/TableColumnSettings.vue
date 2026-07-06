<script setup lang="ts">
import { computed } from 'vue'
import { SettingOutlined, HolderOutlined, ReloadOutlined, LockOutlined } from '@ant-design/icons-vue'
import type { useTableColumnPrefs } from '@/composables/useTableColumnPrefs'

/**
 * 表格列设置：显隐 + 拖拽排序 + 重置
 * ---------------------------------------------------------------------
 * 用法：直接把 useTableColumnPrefs 的返回值透传即可。
 *   <TableColumnSettings :ctrl="columnPrefsCtrl" />
 *
 * 拖拽用原生 HTML5 Drag API，不引 sortablejs，够用即可。
 */

type Ctrl = ReturnType<typeof useTableColumnPrefs>

const props = defineProps<{ ctrl: Ctrl }>()

const visibleCount = computed(
  () => props.ctrl.allColumns.value.filter((c: any) => c._visible).length
)
const totalCount = computed(() => props.ctrl.allColumns.value.length)

/** 拖拽状态：dragging 的 key */
let draggingKey = ''

function onDragStart(e: DragEvent, key: string) {
  draggingKey = key
  if (e.dataTransfer) {
    e.dataTransfer.effectAllowed = 'move'
    e.dataTransfer.setData('text/plain', key)
  }
}

function onDragOver(e: DragEvent) {
  e.preventDefault()
  if (e.dataTransfer) e.dataTransfer.dropEffect = 'move'
}

function onDrop(targetKey: string) {
  if (!draggingKey || draggingKey === targetKey) return
  const keys = props.ctrl.allColumns.value.map((c: any) => c.key ?? c.dataIndex ?? c.title)
  const from = keys.indexOf(draggingKey)
  const to = keys.indexOf(targetKey)
  if (from === -1 || to === -1) return
  const next = [...keys]
  next.splice(from, 1)
  next.splice(to, 0, draggingKey)
  props.ctrl.setOrder(next)
  draggingKey = ''
}
</script>

<template>
  <a-popover
    trigger="click"
    placement="bottomRight"
    :arrow="false"
    overlay-class-name="lt-col-settings-popover"
  >
    <template #content>
      <div class="lt-col-settings">
        <header class="lt-col-settings__header">
          <span class="lt-col-settings__title">列显示设置</span>
          <span class="lt-col-settings__count">{{ visibleCount }} / {{ totalCount }}</span>
        </header>

        <div class="lt-col-settings__list">
          <div
            v-for="col in ctrl.allColumns.value"
            :key="(col.key ?? col.dataIndex ?? col.title) as string"
            class="lt-col-settings__row"
            :class="{ 'lt-col-settings__row--locked': col._locked }"
            :draggable="!col._locked"
            @dragstart="onDragStart($event, (col.key ?? col.dataIndex ?? col.title) as string)"
            @dragover="onDragOver"
            @drop="onDrop((col.key ?? col.dataIndex ?? col.title) as string)"
          >
            <HolderOutlined
              v-if="!col._locked"
              class="lt-col-settings__handle"
            />
            <span v-else class="lt-col-settings__handle lt-col-settings__handle--locked">
              <LockOutlined />
            </span>
            <a-checkbox
              :checked="col._visible"
              :disabled="col._locked"
              @change="ctrl.toggleVisible((col.key ?? col.dataIndex ?? col.title) as string)"
            >
              {{ col.title }}
            </a-checkbox>
          </div>
        </div>

        <footer class="lt-col-settings__footer">
          <a-button type="link" size="small" @click="ctrl.resetPrefs">
            <template #icon><ReloadOutlined /></template>
            重置默认
          </a-button>
        </footer>
      </div>
    </template>

    <a-tooltip title="列显示设置">
      <a-button type="text" size="small" :icon="undefined">
        <SettingOutlined />
      </a-button>
    </a-tooltip>
  </a-popover>
</template>

<style scoped>
.lt-col-settings {
  width: 260px;
  display: flex;
  flex-direction: column;
}

.lt-col-settings__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--lt-space-sm) var(--lt-space-md);
  border-bottom: 1px solid var(--lt-color-border-secondary);
}
.lt-col-settings__title {
  font-weight: var(--lt-font-weight-semibold);
  color: var(--lt-color-text);
  font-size: var(--lt-font-size-sm);
}
.lt-col-settings__count {
  font-size: var(--lt-font-size-xs);
  color: var(--lt-color-text-tertiary);
  font-variant-numeric: tabular-nums;
}

.lt-col-settings__list {
  padding: var(--lt-space-xs) 0;
  max-height: 320px;
  overflow-y: auto;
}

.lt-col-settings__row {
  display: flex;
  align-items: center;
  gap: var(--lt-space-sm);
  padding: 6px var(--lt-space-md);
  cursor: grab;
  transition: background var(--lt-duration-fast) var(--lt-ease-in-out);
}
.lt-col-settings__row:hover {
  background: var(--lt-color-hover-bg);
}
.lt-col-settings__row--locked {
  cursor: not-allowed;
  opacity: 0.6;
}

.lt-col-settings__handle {
  color: var(--lt-color-text-tertiary);
  font-size: var(--lt-font-size-xs);
  cursor: grab;
}
.lt-col-settings__handle--locked {
  cursor: not-allowed;
}

.lt-col-settings__footer {
  border-top: 1px solid var(--lt-color-border-secondary);
  padding: var(--lt-space-xs) var(--lt-space-sm);
  text-align: right;
}
</style>

<style>
.lt-col-settings-popover .ant-popover-inner {
  padding: 0 !important;
}
</style>
