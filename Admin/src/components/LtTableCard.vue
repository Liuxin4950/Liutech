<script setup lang="ts">
/**
 * LtTableCard —— 列表页表格卡片统一骨架
 * ---------------------------------------------------------------------
 * 背景：每个管理页都重复同一段样板：
 *   <a-card :bordered="false">
 *     <template #title><span>XX 列表</span></template>
 *     <template #extra>
 *       <a-space>
 *         <TableExportButton /> <TableColumnSettings />
 *         <a-button type="primary">新建</a-button>
 *         <a-button danger>批量删除</a-button>
 *         ...
 *       </a-space>
 *     </template>
 *     <a-table ... />
 *   </a-card>
 *
 * 标题文案、icon、操作组顺序、空表格的边距，每个页面都略有差异。
 * 本组件收敛这一骨架，业务页面只需关心：
 *   1. 标题文案（title prop 或 #title 插槽）
 *   2. 右上角操作（#actions 插槽，已经自带 a-space 包裹）
 *   3. 表格本体（默认插槽）
 *
 * 用法：
 *   <LtTableCard title="标签列表">
 *     <template #actions>
 *       <TableExportButton :ctrl="exportCtrl" />
 *       <a-button type="primary" @click="openCreate">新建</a-button>
 *     </template>
 *     <a-table ... />
 *   </LtTableCard>
 */

interface Props {
  /** 卡片标题；如需更复杂结构可改用 #title 插槽 */
  title?: string
}

defineProps<Props>()
</script>

<template>
  <a-card :bordered="false" class="lt-table-card">
    <template v-if="title || $slots.title" #title>
      <span v-if="title" class="lt-table-card__title">{{ title }}</span>
      <slot v-else name="title" />
    </template>
    <template v-if="$slots.actions" #extra>
      <a-space wrap>
        <slot name="actions" />
      </a-space>
    </template>
    <slot />
  </a-card>
</template>

<style scoped>
.lt-table-card__title {
  font-size: var(--lt-font-size-base);
  font-weight: var(--lt-font-weight-semibold);
  color: var(--lt-color-text);
}

/* 让卡片 body 紧贴表格，去掉 antd 默认 24px 的内边距抖动 */
.lt-table-card :deep(.ant-card-body) {
  padding-top: var(--lt-space-md);
}
</style>
