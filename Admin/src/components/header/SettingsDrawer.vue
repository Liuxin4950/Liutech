<script setup lang="ts">
import { computed } from 'vue'
import { useSettingsStore, type ThemeMode, type TableSize, type Locale } from '@/stores/settings'
import { BulbOutlined, BulbFilled, DesktopOutlined, GlobalOutlined } from '@ant-design/icons-vue'
import { useI18n } from '@/i18n'

defineProps<{ open: boolean }>()
const emit = defineEmits<{ (e: 'update:open', v: boolean): void }>()

const settings = useSettingsStore()
const { t } = useI18n()

const themeOptions = computed<{ key: ThemeMode; icon: any; label: string }[]>(() => [
  { key: 'light', icon: BulbOutlined, label: t('settings.themeLight') },
  { key: 'dark', icon: BulbFilled, label: t('settings.themeDark') },
  { key: 'auto', icon: DesktopOutlined, label: t('settings.themeAuto') },
])

const sizeOptions = computed<{ key: TableSize; label: string }[]>(() => [
  { key: 'small', label: t('settings.densitySmall') },
  { key: 'middle', label: t('settings.densityMiddle') },
  { key: 'large', label: t('settings.densityLarge') },
])

const localeOptions: { key: Locale; label: string }[] = [
  { key: 'zh-CN', label: '中文' },
  { key: 'en-US', label: 'English' },
]

const themeMode = computed({
  get: () => settings.themeMode,
  set: (v: ThemeMode) => settings.setThemeMode(v),
})
const tableSize = computed({
  get: () => settings.tableSize,
  set: (v: TableSize) => settings.setTableSize(v),
})
const sidebarCollapsed = computed({
  get: () => settings.sidebarCollapsed,
  set: (v: boolean) => settings.setSidebarCollapsed(v),
})
const locale = computed({
  get: () => settings.locale,
  set: (v: Locale) => settings.setLocale(v),
})
</script>

<template>
  <a-drawer
    :open="open"
    :title="t('settings.title')"
    :width="320"
    placement="right"
    @update:open="(v: boolean) => emit('update:open', v)"
  >
    <div class="lt-settings">
      <section class="lt-settings__group">
        <h3 class="lt-settings__title">{{ t('settings.themeMode') }}</h3>
        <a-radio-group v-model:value="themeMode" button-style="solid" class="lt-settings__radio">
          <a-radio-button v-for="opt in themeOptions" :key="opt.key" :value="opt.key">
            <component :is="opt.icon" />
            <span class="lt-settings__radio-label">{{ opt.label }}</span>
          </a-radio-button>
        </a-radio-group>
      </section>

      <section class="lt-settings__group">
        <h3 class="lt-settings__title">{{ t('settings.tableDensity') }}</h3>
        <a-radio-group v-model:value="tableSize" button-style="outline" class="lt-settings__radio">
          <a-radio-button v-for="opt in sizeOptions" :key="opt.key" :value="opt.key">
            {{ opt.label }}
          </a-radio-button>
        </a-radio-group>
        <p class="lt-settings__hint">{{ t('settings.densityHint') }}</p>
      </section>

      <section class="lt-settings__group">
        <h3 class="lt-settings__title">
          <GlobalOutlined /> {{ t('settings.language') }}
        </h3>
        <a-radio-group v-model:value="locale" button-style="outline" class="lt-settings__radio">
          <a-radio-button v-for="opt in localeOptions" :key="opt.key" :value="opt.key">
            {{ opt.label }}
          </a-radio-button>
        </a-radio-group>
      </section>

      <section class="lt-settings__group">
        <div class="lt-settings__row">
          <div>
            <h3 class="lt-settings__title">{{ t('settings.collapseSidebar') }}</h3>
            <p class="lt-settings__hint">{{ t('settings.collapseHint') }}</p>
          </div>
          <a-switch v-model:checked="sidebarCollapsed" />
        </div>
      </section>

      <section class="lt-settings__group lt-settings__group--info">
        <div class="lt-settings__row">
          <span>{{ t('settings.commandK') }}</span>
          <span><kbd>Ctrl</kbd> + <kbd>K</kbd></span>
        </div>
      </section>
    </div>
  </a-drawer>
</template>

<style scoped>
.lt-settings {
  display: flex;
  flex-direction: column;
  gap: var(--lt-space-xl);
}

.lt-settings__group {
  display: flex;
  flex-direction: column;
  gap: var(--lt-space-sm);
}

.lt-settings__group--info {
  padding: var(--lt-space-md);
  background: var(--lt-color-bg-spotlight);
  border-radius: var(--lt-radius-md);
  gap: var(--lt-space-sm);
  color: var(--lt-color-text-secondary);
  font-size: var(--lt-font-size-sm);
}

.lt-settings__title {
  margin: 0;
  font-size: var(--lt-font-size-base);
  font-weight: var(--lt-font-weight-semibold);
  color: var(--lt-color-text);
  display: inline-flex;
  align-items: center;
  gap: var(--lt-space-xs);
}

.lt-settings__hint {
  margin: 0;
  font-size: var(--lt-font-size-xs);
  color: var(--lt-color-text-tertiary);
}

.lt-settings__radio {
  display: flex;
}
.lt-settings__radio :deep(.ant-radio-button-wrapper) {
  flex: 1;
  text-align: center;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: var(--lt-space-xs);
}

.lt-settings__radio-label {
  font-size: var(--lt-font-size-sm);
}

.lt-settings__row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--lt-space-md);
}
.lt-settings__row kbd {
  padding: 1px 6px;
  border: 1px solid var(--lt-color-border-secondary);
  border-radius: var(--lt-radius-sm);
  font-family: var(--lt-font-family-mono);
  font-size: var(--lt-font-size-xs);
  background: var(--lt-color-bg-container);
  margin: 0 2px;
}
</style>
