<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useTagsStore, type TagView } from '@/stores/tabs'
import { CloseOutlined, ReloadOutlined, ColumnWidthOutlined, DeleteOutlined } from '@ant-design/icons-vue'
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
  height: 40px;
  background: var(--bg-card);
  border-bottom: 1px solid var(--border-light);
  overflow: hidden;
}

.tags-view-wrapper {
  flex: 1;
  overflow-x: auto;
  overflow-y: hidden;
  padding: 4px 12px;
}

.tags-view-list {
  display: flex;
  align-items: center;
  gap: 8px;
}

/* 标签样式 */
.tag-view {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: 28px;
  padding: 0 10px;
  font-size: 13px;
  line-height: 28px;
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.2s ease;
  background: var(--bg-tag);
  color: var(--text-secondary);
  border: 1px solid var(--border-light);
}

.tag-view:hover {
  background: var(--color-primary-bg);
  color: var(--color-primary);
  border-color: var(--color-primary-bg);
}

.tag-view.active {
  background: var(--color-primary-bg);
  color: var(--color-primary);
  border-color: var(--color-primary);
}

.tag-view.affix {
  background: var(--bg-hover);
}

.tag-icon {
  font-size: 12px;
}

/* 关闭按钮样式 */
.tag-view :deep(.ant-tag-close-icon) {
  margin-left: 4px;
  width: 14px;
  height: 14px;
  line-height: 14px;
  border-radius: 50%;
  text-align: center;
  transition: all 0.2s;
}

.tag-view :deep(.ant-tag-close-icon:hover) {
  background: rgba(0, 0, 0, 0.1);
}

.tag-view.active :deep(.ant-tag-close-icon:hover) {
  background: rgba(22, 119, 255, 0.2);
}

/* 动画效果 */
.tag-list-enter-active,
.tag-list-leave-active {
  transition: all 0.3s ease;
}

.tag-list-enter-from,
.tag-list-leave-to {
  opacity: 0;
  transform: translateX(20px);
}

.tag-list-move {
  transition: transform 0.3s ease;
}

/* 滚动条样式 */
.tags-view-wrapper::-webkit-scrollbar {
  height: 4px;
}

.tags-view-wrapper::-webkit-scrollbar-track {
  background: transparent;
}

.tags-view-wrapper::-webkit-scrollbar-thumb {
  background: var(--border-base);
  border-radius: 2px;
}

.tags-view-wrapper::-webkit-scrollbar-thumb:hover {
  background: var(--border-heavy);
}
</style>
