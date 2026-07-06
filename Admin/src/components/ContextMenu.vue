<script setup lang="ts">
import { ref, watch, onMounted, onUnmounted } from 'vue'
import { ReloadOutlined, CloseOutlined, DeleteOutlined, SwitcherOutlined } from '@ant-design/icons-vue'

/**
 * 组件属性
 */
interface Props {
  visible: boolean      // 是否显示
  x: number            // 菜单位置 X
  y: number            // 菜单位置 Y
  isAffix: boolean     // 是否是固定标签
}

/**
 * 组件事件
 */
interface Emits {
  (e: 'update:visible', value: boolean): void
  (e: 'refresh'): void
  (e: 'close-current'): void
  (e: 'close-other'): void
  (e: 'close-all'): void
}

const props = withDefaults(defineProps<Props>(), {
  visible: false,
  x: 0,
  y: 0,
  isAffix: false
})

const emit = defineEmits<Emits>()

// 菜单元素引用
const menuRef = ref<HTMLElement>()

/**
 * 关闭菜单
 */
const closeMenu = (): void => {
  emit('update:visible', false)
}

/**
 * 刷新当前页
 */
const handleRefresh = (): void => {
  emit('refresh')
  closeMenu()
}

/**
 * 关闭当前
 */
const handleCloseCurrent = (): void => {
  if (props.isAffix) {
    // 固定标签不能关闭
    return
  }
  emit('close-current')
  closeMenu()
}

/**
 * 关闭其他
 */
const handleCloseOther = (): void => {
  emit('close-other')
  closeMenu()
}

/**
 * 关闭所有
 */
const handleCloseAll = (): void => {
  emit('close-all')
  closeMenu()
}

/**
 * 点击外部关闭
 */
const handleClickOutside = (event: MouseEvent): void => {
  if (menuRef.value && !menuRef.value.contains(event.target as Node)) {
    closeMenu()
  }
}

/**
 * ESC 键关闭
 */
const handleKeydown = (event: KeyboardEvent): void => {
  if (event.key === 'Escape') {
    closeMenu()
  }
}

// 监听 visibility 变化时添加/移除事件监听
watch(() => props.visible, (visible) => {
  if (visible) {
    document.addEventListener('click', handleClickOutside)
    document.addEventListener('keydown', handleKeydown)
  } else {
    document.removeEventListener('click', handleClickOutside)
    document.removeEventListener('keydown', handleKeydown)
  }
})

// 组件卸载时清理
onMounted(() => {
  document.addEventListener('click', handleClickOutside)
  document.addEventListener('keydown', handleKeydown)
})

onUnmounted(() => {
  document.removeEventListener('click', handleClickOutside)
  document.removeEventListener('keydown', handleKeydown)
})
</script>

<template>
  <Teleport to="body">
    <Transition name="context-menu">
      <div
        v-if="visible"
        ref="menuRef"
        class="context-menu"
        :style="{ left: x + 'px', top: y + 'px' }"
      >
        <ul class="context-menu-list">
          <li class="context-menu-item" @click="handleRefresh">
            <ReloadOutlined />
            <span>刷新</span>
          </li>

          <li
            class="context-menu-item"
            :class="{ disabled: isAffix }"
            @click="handleCloseCurrent"
          >
            <CloseOutlined />
            <span>关闭当前</span>
          </li>

          <li class="context-menu-item" @click="handleCloseOther">
            <SwitcherOutlined />
            <span>关闭其他</span>
          </li>

          <li class="context-menu-item danger" @click="handleCloseAll">
            <DeleteOutlined />
            <span>关闭所有</span>
          </li>
        </ul>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.context-menu {
  position: fixed;
  z-index: var(--lt-z-dropdown);
  background: var(--lt-color-bg-elevated);
  border: 1px solid var(--lt-color-border-secondary);
  border-radius: var(--lt-radius-lg);
  box-shadow: var(--lt-shadow-md);
  padding: var(--lt-space-xs) 0;
  min-width: 140px;
  user-select: none;
}

.context-menu-list {
  list-style: none;
  margin: 0;
  padding: 0;
}

.context-menu-item {
  display: flex;
  align-items: center;
  gap: var(--lt-space-sm);
  padding: var(--lt-space-sm) var(--lt-space-lg);
  cursor: pointer;
  transition: var(--lt-motion-hover);
  color: var(--lt-color-text);
  font-size: var(--lt-font-size-base);
}

.context-menu-item:hover {
  background: var(--lt-color-primary-bg);
  color: var(--lt-color-primary);
}

.context-menu-item.disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.context-menu-item.disabled:hover {
  background: transparent;
  color: var(--lt-color-text);
}

.context-menu-item.danger:hover {
  background: var(--lt-color-error-bg);
  color: var(--lt-color-error);
}

.context-menu-enter-active,
.context-menu-leave-active {
  transition: opacity var(--lt-duration-fast) var(--lt-ease-in-out),
              transform var(--lt-duration-fast) var(--lt-ease-in-out);
}

.context-menu-enter-from,
.context-menu-leave-to {
  opacity: 0;
  transform: scale(0.95);
}
</style>
