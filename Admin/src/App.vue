<script setup lang="ts">
import { computed } from 'vue'
import { ConfigProvider, theme as antdTheme } from 'ant-design-vue'
import { useSettingsStore } from '@/stores/settings'
import { useI18n } from '@/i18n'

const settings = useSettingsStore()
const { antdLocale } = useI18n()

/**
 * AntD ConfigProvider 主题配置
 * ------------------------------------------------------------
 * token / components 引用 CSS 变量（--lt-*）配合 antdTheme.darkAlgorithm，
 * 让整套 antd 组件的颜色/间距/圆角自动跟随全局 tokens 变化。
 * 主题切换仅需修改 <html data-lt-theme>，无需重挂载 ConfigProvider。
 */
const antTheme = computed(() => ({
  algorithm: settings.isDark ? antdTheme.darkAlgorithm : antdTheme.defaultAlgorithm,
  token: {
    colorPrimary: '#1677ff',
    colorSuccess: '#52c41a',
    colorWarning: '#faad14',
    colorError: '#ff4d4f',
    colorInfo: '#1677ff',
    borderRadius: 6,
    borderRadiusLG: 8,
    borderRadiusSM: 4,
    borderRadiusXS: 2,
    controlHeight: 32,
    controlHeightLG: 40,
    controlHeightSM: 24,
    fontFamily: `-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'PingFang SC', 'Hiragino Sans GB', 'Microsoft YaHei', 'Helvetica Neue', Helvetica, Arial, sans-serif`,
    fontSize: 14,
    wireframe: false,
  },
  components: {
    Button: {
      controlHeight: 32,
      fontWeight: 400,
      primaryShadow: 'none',
      defaultShadow: 'none',
      dangerShadow: 'none',
    },
    Menu: {
      itemHeight: 40,
      itemMarginBlock: 4,
      itemMarginInline: 8,
      itemBorderRadius: 6,
      iconSize: 16,
      collapsedIconSize: 16,
      activeBarWidth: 0,
      activeBarBorderWidth: 0,
    },
    Card: {
      borderRadiusLG: 8,
      headerFontSize: 16,
      headerHeight: 48,
      headerHeightSM: 36,
      paddingLG: 16,
      padding: 16,
      actionsBg: 'transparent',
    },
    Table: {
      borderRadius: 8,
      cellPaddingBlock: 10,
      cellPaddingInline: 12,
    },
    Modal: {
      borderRadiusLG: 8,
      titleFontSize: 16,
    },
    Descriptions: {
      itemPaddingBottom: 12,
      titleMarginBottom: 12,
    },
    Tag: {
      borderRadiusSM: 4,
      fontSize: 12,
    },
    Input: { borderRadius: 6, controlHeight: 32 },
    Select: { borderRadius: 6, controlHeight: 32 },
    Form: { itemMarginBottom: 16, labelFontSize: 14, verticalLabelPadding: '0 0 4px' },
  },
}))
</script>

<template>
  <ConfigProvider :theme="antTheme" :component-size="settings.tableSize as any" :locale="antdLocale">
    <div class="app-container">
      <router-view />
    </div>
  </ConfigProvider>
</template>

<style>
html,
body,
#app,
.app-container {
  height: 100%;
  margin: 0;
  padding: 0;
  background: var(--lt-color-bg-layout);
  color: var(--lt-color-text);
  font-family: var(--lt-font-family);
  font-size: var(--lt-font-size-base);
  line-height: var(--lt-line-height-base);
}
</style>
