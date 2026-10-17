<script setup lang="ts">
/**
 * LtPageHeader —— 非列表页（设置页/表单页/详情页）的统一页面头
 * ---------------------------------------------------------------------
 * 背景：AiSettings / AboutPageSettings / CommunityAiManagement 等
 * 非列表页没有"搜索卡片 + 表格卡片"的固定节奏，各自用 h1/h2/div 拼页面标题，
 * 副标题、右侧操作按钮的位置不一致。
 *
 * 本组件提供唯一骨架：
 *   - 主标题（大字号 + 半粗）
 *   - 可选副标题（描述性小字）
 *   - 右侧操作插槽（刷新/保存/检测 等）
 *   - 底部一条细分隔线，与内容区拉开层级
 *
 * 用法：
 *   <LtPageHeader title="AI 设置" subtitle="管理 TTS 语音与文本模型运行时">
 *     <template #actions>
 *       <a-button @click="detectStatus">重新检测</a-button>
 *       <a-button type="primary" @click="save">保存</a-button>
 *     </template>
 *   </LtPageHeader>
 */

interface Props {
  title: string
  subtitle?: string
}

defineProps<Props>()
</script>

<template>
  <header class="lt-page-header">
    <div class="lt-page-header__main">
      <h1 class="lt-page-header__title">{{ title }}</h1>
      <p v-if="subtitle" class="lt-page-header__subtitle">{{ subtitle }}</p>
    </div>
    <div v-if="$slots.actions" class="lt-page-header__actions">
      <a-space wrap>
        <slot name="actions" />
      </a-space>
    </div>
  </header>
</template>

<style scoped>
.lt-page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--lt-space-lg);
  padding-bottom: var(--lt-space-md);
  margin-bottom: var(--lt-space-lg);
  border-bottom: 1px solid var(--lt-color-border-secondary);
}

.lt-page-header__main {
  min-width: 0;
  flex: 1;
}

.lt-page-header__title {
  margin: 0;
  font-size: var(--lt-font-size-xl);
  font-weight: var(--lt-font-weight-semibold);
  color: var(--lt-color-text);
  line-height: var(--lt-line-height-tight);
}

.lt-page-header__subtitle {
  margin: var(--lt-space-xs) 0 0;
  color: var(--lt-color-text-tertiary);
  font-size: var(--lt-font-size-sm);
}

.lt-page-header__actions {
  flex: 0 0 auto;
}

@media (max-width: 575px) {
  .lt-page-header {
    flex-direction: column;
    align-items: stretch;
  }
  .lt-page-header__actions {
    width: 100%;
  }
}
</style>
