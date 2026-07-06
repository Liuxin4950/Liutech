<script setup lang="ts">
import { computed, inject, ref, type Ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  UserOutlined,
  LogoutOutlined,
  DownOutlined,
  BulbOutlined,
  BulbFilled,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  HomeOutlined,
  SearchOutlined,
  FullscreenOutlined,
  FullscreenExitOutlined,
  SettingOutlined,
} from '@ant-design/icons-vue'
import { useUserStore } from '@/stores/user'
import { useSettingsStore } from '@/stores/settings'
import { useBreadcrumbs } from '@/composables/useBreadcrumbs'
import { useFullscreen } from '@/composables/useFullscreen'
import { useShortcuts } from '@/composables/useShortcuts'
import { useI18n } from '@/i18n'
import CommandPalette from './header/CommandPalette.vue'
import NotificationCenter from './header/NotificationCenter.vue'
import SettingsDrawer from './header/SettingsDrawer.vue'
import ShortcutsHelp from './header/ShortcutsHelp.vue'

const router = useRouter()
const userStore = useUserStore()
const settings = useSettingsStore()
const { t } = useI18n()

// 折叠状态由 MainLayout 通过 provide 下发
const collapsed = inject<Ref<boolean>>('sidebarCollapsed')!
const toggleCollapsed = () => { collapsed.value = !collapsed.value }

const themeIcon = computed(() => (settings.isDark ? BulbFilled : BulbOutlined))
const themeLabel = computed(() => t('header.themeSwitch'))

const { breadcrumbs } = useBreadcrumbs()
const { isFullscreen, toggle: toggleFullscreen } = useFullscreen()

const commandOpen = ref(false)
const settingsOpen = ref(false)
const helpOpen = ref(false)

const navigateTo = (path: string) => {
  if (path !== router.currentRoute.value.path) router.push(path)
}

const handleLogout = () => {
  userStore.logout()
  router.push('/login')
}

// 全局快捷键
useShortcuts([
  { key: 'ctrl+k', handler: () => { commandOpen.value = true }, description: '快速搜索', allowInInput: true },
  { key: 'meta+k', handler: () => { commandOpen.value = true }, description: '快速搜索（Mac）', allowInInput: true },
  { key: '?', handler: () => { helpOpen.value = true }, description: '显示快捷键帮助' },
  { key: 'ctrl+/', handler: () => { helpOpen.value = true }, description: '显示快捷键帮助', allowInInput: true },
  { key: 'ctrl+,', handler: () => { settingsOpen.value = true }, description: '打开界面设置', allowInInput: true },
  { key: 'ctrl+b', handler: () => { collapsed.value = !collapsed.value }, description: '折叠/展开侧栏', allowInInput: true },
  { key: 'f', handler: () => { toggleFullscreen() }, description: '切换全屏' },
  // vim 风格序列跳转
  { key: 'g h', handler: () => router.push('/'), description: '跳转到首页' },
  { key: 'g p', handler: () => router.push('/posts'), description: '跳转到文章管理' },
  { key: 'g c', handler: () => router.push('/categories'), description: '跳转到分类管理' },
  { key: 'g t', handler: () => router.push('/tags'), description: '跳转到标签管理' },
  { key: 'g u', handler: () => router.push('/users'), description: '跳转到用户管理' },
  { key: 'g m', handler: () => router.push('/messages'), description: '跳转到留言管理' },
  { key: 'g a', handler: () => router.push('/announcements'), description: '跳转到公告管理' },
  { key: 'g l', handler: () => router.push('/logs'), description: '跳转到操作日志' },
  { key: 'g s', handler: () => router.push('/settings'), description: '跳转到系统设置' },
])
</script>

<template>
  <header class="lt-header">
    <!-- 左侧：折叠按钮 + 面包屑 -->
    <div class="lt-header__left">
      <button
        type="button"
        class="lt-header__collapse"
        :aria-label="collapsed ? '展开侧边栏' : '折叠侧边栏'"
        @click="toggleCollapsed"
      >
        <MenuUnfoldOutlined v-if="collapsed" />
        <MenuFoldOutlined v-else />
      </button>

      <a-breadcrumb v-if="breadcrumbs.length" class="lt-header__breadcrumb">
        <a-breadcrumb-item>
          <a href="#" @click.prevent="navigateTo('/')"><HomeOutlined /></a>
        </a-breadcrumb-item>
        <a-breadcrumb-item v-for="item in breadcrumbs.slice(1)" :key="item.path">
          <a v-if="!item.disabled && item.path" href="#" @click.prevent="navigateTo(item.path)">
            {{ item.title }}
          </a>
          <span v-else class="lt-header__breadcrumb-current">{{ item.title }}</span>
        </a-breadcrumb-item>
      </a-breadcrumb>
    </div>

    <!-- 右侧：搜索 + 通知 + 全屏 + 主题 + 设置 + 用户 -->
    <div class="lt-header__right">
      <a-tooltip :title="t('header.search') + ' (Ctrl+K)'">
        <button
          type="button"
          class="lt-header__icon-btn"
          :aria-label="t('header.search')"
          @click="commandOpen = true"
        >
          <SearchOutlined />
        </button>
      </a-tooltip>

      <NotificationCenter />

      <a-tooltip :title="isFullscreen ? t('header.exitFullscreen') : t('header.fullscreen')">
        <button
          type="button"
          class="lt-header__icon-btn"
          :aria-label="isFullscreen ? t('header.exitFullscreen') : t('header.fullscreen')"
          @click="toggleFullscreen"
        >
          <FullscreenExitOutlined v-if="isFullscreen" />
          <FullscreenOutlined v-else />
        </button>
      </a-tooltip>

      <a-tooltip :title="themeLabel">
        <button
          type="button"
          class="lt-header__icon-btn"
          :aria-label="themeLabel"
          @click="settings.toggleThemeMode"
        >
          <component :is="themeIcon" />
        </button>
      </a-tooltip>

      <a-tooltip :title="t('header.settings')">
        <button
          type="button"
          class="lt-header__icon-btn"
          :aria-label="t('header.settings')"
          @click="settingsOpen = true"
        >
          <SettingOutlined />
        </button>
      </a-tooltip>

      <a-dropdown v-if="userStore.isLoggedIn" placement="bottomRight" :trigger="['click']">
        <template #overlay>
          <a-menu>
            <a-menu-item key="profile" @click="router.push('/profile')">
              <UserOutlined />
              {{ t('header.profile') }}
            </a-menu-item>
            <a-menu-divider />
            <a-menu-item key="logout" @click="handleLogout">
              <LogoutOutlined />
              {{ t('header.logout') }}
            </a-menu-item>
          </a-menu>
        </template>
        <button type="button" class="lt-header__user-btn">
          <UserOutlined />
          <span class="lt-header__user-name">{{ userStore.username || 'Admin' }}</span>
          <DownOutlined class="lt-header__user-caret" />
        </button>
      </a-dropdown>

      <a-button v-else type="primary" size="small" @click="router.push('/login')">
        <UserOutlined />
        {{ t('header.login') }}
      </a-button>
    </div>

    <CommandPalette v-model:open="commandOpen" />
    <SettingsDrawer v-model:open="settingsOpen" />
    <ShortcutsHelp v-model:open="helpOpen" />
  </header>
</template>

<style scoped>
.lt-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: var(--lt-size-header);
  padding: 0 var(--lt-space-md) 0 0;
  background: var(--lt-color-bg-container);
  border-bottom: 1px solid var(--lt-color-border-secondary);
}

.lt-header__left {
  display: flex;
  align-items: center;
  gap: var(--lt-space-md);
  min-width: 0;
  flex: 1;
}

.lt-header__collapse {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: var(--lt-size-header);
  height: var(--lt-size-header);
  background: transparent;
  border: none;
  cursor: pointer;
  color: var(--lt-color-text-secondary);
  font-size: var(--lt-font-size-md);
  transition: var(--lt-motion-hover);
}
.lt-header__collapse:hover {
  color: var(--lt-color-primary);
  background: var(--lt-color-hover-bg);
}

.lt-header__breadcrumb {
  font-size: var(--lt-font-size-base);
  min-width: 0;
}
.lt-header__breadcrumb :deep(.ant-breadcrumb-separator) { color: var(--lt-color-text-tertiary); }
.lt-header__breadcrumb :deep(.ant-breadcrumb-link) { color: var(--lt-color-text-secondary); }
.lt-header__breadcrumb :deep(.ant-breadcrumb-link a) {
  color: var(--lt-color-text-secondary);
  transition: color var(--lt-duration-base) var(--lt-ease-in-out);
}
.lt-header__breadcrumb :deep(.ant-breadcrumb-link a:hover) { color: var(--lt-color-primary); }
.lt-header__breadcrumb-current {
  color: var(--lt-color-text);
  font-weight: var(--lt-font-weight-medium);
}

.lt-header__right {
  display: flex;
  align-items: center;
  gap: var(--lt-space-xs);
  flex: 0 0 auto;
}

.lt-header__icon-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border: none;
  border-radius: var(--lt-radius-md);
  background: transparent;
  color: var(--lt-color-text-secondary);
  cursor: pointer;
  font-size: var(--lt-font-size-md);
  transition: var(--lt-motion-hover);
}
.lt-header__icon-btn:hover {
  background: var(--lt-color-hover-bg);
  color: var(--lt-color-primary);
}

.lt-header__user-btn {
  display: inline-flex;
  align-items: center;
  gap: var(--lt-space-sm);
  padding: 0 var(--lt-space-sm);
  height: 36px;
  border: none;
  background: transparent;
  border-radius: var(--lt-radius-md);
  color: var(--lt-color-text-secondary);
  cursor: pointer;
  transition: var(--lt-motion-hover);
}
.lt-header__user-btn:hover {
  background: var(--lt-color-hover-bg);
  color: var(--lt-color-primary);
}
.lt-header__user-name {
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.lt-header__user-caret {
  font-size: var(--lt-font-size-xs);
  color: var(--lt-color-text-tertiary);
}
</style>
