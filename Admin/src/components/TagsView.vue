<script setup lang="ts">
import { ref, onMounted, watch, inject, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useTagsStore, type TagView } from '@/stores/tabs'
import { ColumnWidthOutlined, ReloadOutlined, CloseOutlined, SwitcherOutlined, DeleteOutlined } from '@ant-design/icons-vue'
import { useI18n } from '@/i18n'

const route = useRoute()
const router = useRouter()
const tagsStore = useTagsStore()
const { t } = useI18n()

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
 *
 * MainLayout 的 KeepAlive 只会「丢弃缓存条目」而不会卸载当前实例
 * （Vue pruneCacheEntry 对正在渲染的 vnode 只清标志位），所以必须：
 * 1) 先把本页从 include 摘掉 —— 缓存条目作废，旧实例在下次换 key 时真正卸载；
 * 2) 由 MainLayout 递增 key 触发重新挂载，页面重新执行 onMounted 拉数据；
 * 3) 再把名字加回 include，让该标签继续享有缓存。
 */
const reloadCurrentView = inject<(() => void) | undefined>('ltReloadCurrentView', undefined)

const handleRefresh = async (): Promise<void> => {
  const name = route.name as string
  refreshing.value = true
  const cacheIndex = tagsStore.cachedViews.indexOf(name)
  if (cacheIndex > -1) {
    tagsStore.cachedViews.splice(cacheIndex, 1)
  }
  await nextTick()
  reloadCurrentView?.()
  await nextTick()
  tagsStore.addCachedView(name)
  refreshing.value = false
}

/**
 * 右键菜单动作（菜单由 a-dropdown + a-menu 提供，避免自管定位/外部点击/ESC）
 * 菜单项通过 key 区分，目标标签由每个 dropdown 自己的闭包带入
 */
const handleMenuClick = (info: { key?: string | number }, tag: TagView): void => {
  switch (String(info?.key)) {
    case 'refresh':
      handleRefresh()
      break
    case 'close-current':
      if (!tag.affix) tagsStore.delVisitedView(tag)
      break
    case 'close-other':
      tagsStore.delOtherViews(tag)
      break
    case 'close-all':
      tagsStore.delAllViews()
      break
  }
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
  <div class="tags-view-container">
    <!-- 标签列表 -->
    <div class="tags-view-wrapper">
      <transition-group name="tag-list" tag="div" class="tags-view-list">
        <!-- 右键菜单用 a-dropdown(trigger=contextmenu)：跟随鼠标、贴边自动翻转、
             外部点击/ESC 关闭都由 antd 托管，不再自管定位与全局监听。
             a-dropdown 的根是 Fragment，但 Trigger 只把事件挂在子节点上、
             不额外包一层元素，所以 transition-group 的子元素仍是 a-tag 本身。 -->
        <a-dropdown v-for="tag in tagsStore.visitedViews" :key="tag.path" :trigger="['contextmenu']">
          <a-tag
            :class="['tag-view', { active: isActive(tag), affix: tag.affix }]"
            :closable="!tag.affix"
            :bordered="false"
            @click="handleClick(tag)"
            @close="handleClose($event, tag)"
          >
            <span class="tag-icon" v-if="tag.affix">
              <ColumnWidthOutlined />
            </span>
            {{ tag.title }}
          </a-tag>
          <template #overlay>
            <a-menu @click="handleMenuClick($event, tag)">
              <a-menu-item key="refresh">
                <template #icon><ReloadOutlined /></template>{{ t('tabsView.refresh') }}
              </a-menu-item>
              <a-menu-item key="close-current" :disabled="tag.affix">
                <template #icon><CloseOutlined /></template>{{ t('tabsView.closeCurrent') }}
              </a-menu-item>
              <a-menu-item key="close-other">
                <template #icon><SwitcherOutlined /></template>{{ t('tabsView.closeOther') }}
              </a-menu-item>
              <a-menu-item key="close-all" danger>
                <template #icon><DeleteOutlined /></template>{{ t('tabsView.closeAll') }}
              </a-menu-item>
            </a-menu>
          </template>
        </a-dropdown>
      </transition-group>
    </div>

    <!-- 刷新当前页：标签缓存生效后页面不会自行重载，必须给一个显式入口 -->
    <a-tooltip :title="t('tabsView.refresh')">
      <a-button
        class="tags-view-refresh"
        type="text"
        size="small"
        :loading="refreshing"
        :aria-label="t('tabsView.refresh')"
        @click="handleRefresh"
      >
        <template #icon><ReloadOutlined /></template>
      </a-button>
    </a-tooltip>
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

.tags-view-refresh {
  flex: 0 0 auto;
  margin-right: var(--lt-space-sm);
  color: var(--lt-color-text-secondary);
}

.tags-view-refresh:hover {
  color: var(--lt-color-primary);
  background: var(--lt-color-hover-bg);
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
