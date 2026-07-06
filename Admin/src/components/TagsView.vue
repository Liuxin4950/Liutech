<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useTagsStore, type TagView } from '@/stores/tabs'
import { ColumnWidthOutlined } from '@ant-design/icons-vue'
import ContextMenu from './ContextMenu.vue'

const route = useRoute()
const router = useRouter()
const tagsStore = useTagsStore()

// 右键菜单相关
const contextMenuVisible = ref(false)
const contextMenuPosition = ref({ x: 0, y: 0 })
const contextMenuTarget = ref<TagView | null>(null)

// 刷新加载状态
const refreshing = ref(false)

/**
 * 是否为激活状态
 */
const isActive = (tag: TagView): boolean => {
  return tag.path === route.path
}

/**
 * 处理标签点击
 */
const handleClick = (tag: TagView): void => {
  if (tag.path !== route.path) {
    router.push(tag.path)
  }
}

/**
 * 关闭标签
 */
const handleClose = (event: Event | undefined, tag: TagView): void => {
  event?.stopPropagation() // 阻止冒泡，避免触发标签点击
  tagsStore.delVisitedView(tag)
}

/**
 * 刷新当前页面
 */
const handleRefresh = (): void => {
  refreshing.value = true
  // 从缓存中移除再重新添加，触发组件重新挂载
  const cacheIndex = tagsStore.cachedViews.indexOf(route.name as string)
  if (cacheIndex > -1) {
    tagsStore.cachedViews.splice(cacheIndex, 1)
  }
  setTimeout(() => {
    tagsStore.addCachedView(route.name as string)
    refreshing.value = false
  }, 100)
}

/**
 * 关闭其他标签
 */
const handleCloseOther = (): void => {
  if (contextMenuTarget.value) {
    tagsStore.delOtherViews(contextMenuTarget.value)
  } else {
    tagsStore.delOtherViews()
  }
  contextMenuVisible.value = false
}

/**
 * 关闭所有标签
 */
const handleCloseAll = (): void => {
  tagsStore.delAllViews()
  contextMenuVisible.value = false
}

/**
 * 右键点击标签
 */
const handleContextMenu = (event: MouseEvent, tag: TagView): void => {
  event.preventDefault() // 阻止默认右键菜单
  contextMenuTarget.value = tag
  contextMenuPosition.value = { x: event.clientX, y: event.clientY }
  contextMenuVisible.value = true
}

/**
 * 点击空白处关闭右键菜单
 */
const handleClickOutside = (): void => {
  contextMenuVisible.value = false
  contextMenuTarget.value = null
}

/**
 * 滚动到当前标签
 */
const scrollToActiveTag = (): void => {
  // 延迟执行，等待 DOM 更新
  setTimeout(() => {
    const activeEl = document.querySelector('.tag-view.active')
    if (activeEl) {
      activeEl.scrollIntoView({ behavior: 'smooth', block: 'nearest', inline: 'center' })
    }
  }, 100)
}

// 监听路由变化，滚动到当前标签
watch(() => route.path, () => {
  scrollToActiveTag()
})

// 初始化
onMounted(() => {
  scrollToActiveTag()
})
</script>

<template>
  <div class="tags-view-container" @click.self="handleClickOutside">
    <!-- 标签列表 -->
    <div class="tags-view-wrapper">
      <transition-group name="tag-list" tag="div" class="tags-view-list">
        <a-tag
          v-for="tag in tagsStore.visitedViews"
          :key="tag.path"
          :class="['tag-view', { active: isActive(tag), affix: tag.affix }]"
          :closable="!tag.affix"
          :bordered="false"
          @click="handleClick(tag)"
          @close="handleClose($event, tag)"
          @contextmenu.prevent="handleContextMenu($event, tag)"
        >
          <span class="tag-icon" v-if="tag.affix">
            <ColumnWidthOutlined />
          </span>
          {{ tag.title }}
        </a-tag>
      </transition-group>
    </div>

    <!-- 右键菜单 -->
    <ContextMenu
      v-model:visible="contextMenuVisible"
      :x="contextMenuPosition.x"
      :y="contextMenuPosition.y"
      :is-affix="contextMenuTarget?.affix || false"
      @refresh="handleRefresh"
      @close-current="contextMenuTarget && handleClose(undefined, contextMenuTarget)"
      @close-other="handleCloseOther"
      @close-all="handleCloseAll"
    />
  </div>
</template>

<style scoped>
.tags-view-container {
  display: flex;
  align-items: center;
  width: 100%;
  height: var(--lt-size-tags-view);
  background: var(--lt-color-bg-container);
  border-bottom: 1px solid var(--lt-color-border-secondary);
  overflow: hidden;
}

.tags-view-wrapper {
  flex: 1;
  overflow-x: auto;
  overflow-y: hidden;
  padding: var(--lt-space-xs) var(--lt-space-md);
}

.tags-view-list {
  display: flex;
  align-items: center;
  gap: var(--lt-space-sm);
}

.tag-view {
  display: inline-flex;
  align-items: center;
  gap: var(--lt-space-xs);
  height: 28px;
  padding: 0 var(--lt-space-md);
  font-size: var(--lt-font-size-sm);
  line-height: 28px;
  border-radius: var(--lt-radius-sm);
  cursor: pointer;
  transition: var(--lt-motion-hover);
  background: var(--lt-color-bg-spotlight) !important;
  color: var(--lt-color-text-secondary);
  border: 1px solid var(--lt-color-border-secondary) !important;
}

.tag-view:hover {
  background: var(--lt-color-primary-bg) !important;
  color: var(--lt-color-primary);
  border-color: var(--lt-color-primary-bg) !important;
}

.tag-view.active {
  background: var(--lt-color-primary-bg) !important;
  color: var(--lt-color-primary);
  border-color: var(--lt-color-primary) !important;
}

.tag-view.affix {
  background: var(--lt-color-bg-container) !important;
}

.tag-icon {
  font-size: var(--lt-font-size-xs);
}

.tag-view :deep(.ant-tag-close-icon) {
  margin-left: var(--lt-space-xs);
  width: 14px;
  height: 14px;
  line-height: 14px;
  border-radius: var(--lt-radius-circle);
  text-align: center;
  transition: var(--lt-motion-hover);
}

.tag-view :deep(.ant-tag-close-icon:hover) {
  background: var(--lt-color-hover-bg);
}

.tag-view.active :deep(.ant-tag-close-icon:hover) {
  background: var(--lt-color-primary-bg-hover);
}

.tag-list-enter-active,
.tag-list-leave-active {
  transition: all var(--lt-duration-slow) var(--lt-ease-in-out);
}

.tag-list-enter-from,
.tag-list-leave-to {
  opacity: 0;
  transform: translateX(20px);
}

.tag-list-move {
  transition: transform var(--lt-duration-slow) var(--lt-ease-in-out);
}

.tags-view-wrapper::-webkit-scrollbar {
  height: 4px;
}
.tags-view-wrapper::-webkit-scrollbar-track {
  background: transparent;
}
.tags-view-wrapper::-webkit-scrollbar-thumb {
  background: var(--lt-color-border);
  border-radius: var(--lt-radius-xs);
}
.tags-view-wrapper::-webkit-scrollbar-thumb:hover {
  background: var(--lt-color-border-strong);
}
</style>
