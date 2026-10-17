<script setup lang="ts">
/**
 * LtFilterBar —— 列表页搜索区统一骨架
 * ---------------------------------------------------------------------
 * 背景：14+ 个管理页的搜索区有至少 4 种写法：
 *   A. `a-row` + `.search-actions` 类（UsersManagement）
 *   B. `a-row` + 页面私有 `.xxx-filter-actions`（CategoriesManagement）
 *   C. `.filter-toolbar` flex 自绘（TagsManagement / CarouselsManagement，参数还略不同）
 *   D. 各种临时拼凑
 * 视觉节奏不一致：字段宽度、按钮对齐、换行断点、底部间距各自为政。
 *
 * 本组件收敛为唯一节奏：
 *   - 字段从左侧开始排列，可换行
 *   - 操作组（搜索/重置/删显开关/任意附加按钮）永远右对齐
 *   - 字段容器最小 180px、最大 320px，避免极端拉宽
 *   - 间距、卡片包裹、底边距全部走 tokens.css 的 --lt-*
 *
 * 用法（默认插槽放字段，#actions 放按钮组）：
 *   <LtFilterBar>
 *     <a-form-item label="名称" class="lt-filter-bar__field">
 *       <a-input v-model:value="searchParams.name" placeholder="请输入" allow-clear />
 *     </a-form-item>
 *     <template #actions>
 *       <a-button type="primary" @click="handleSearch">搜索</a-button>
 *       <a-button @click="handleReset">重置</a-button>
 *     </template>
 *   </LtFilterBar>
 *
 * 字段统一加 `.lt-filter-bar__field` 类（或 `:class`），让本组件控制 flex 自适应。
 *
 * ⚠️ bare 模式：当搜索区需要嵌入 Tab 内部 / 抽屉 / 弹窗等
 * 已有容器时，传 `bare` 关闭卡片包裹与底边距，避免"卡片套卡片"。
 * 例：PointsManagement 的双 Tab 各自内嵌筛选。
 */

interface Props {
  /** 关闭 a-card 包裹，用于嵌入 Tab / 抽屉等已有容器 */
  bare?: boolean
}

defineProps<Props>()
</script>

<template>
  <a-card v-if="!bare" :bordered="false" class="lt-filter-bar">
    <a-form layout="horizontal" class="lt-filter-bar__form">
      <div class="lt-filter-bar__inner">
        <slot />
        <div v-if="$slots.actions" class="lt-filter-bar__actions">
          <a-space wrap>
            <slot name="actions" />
          </a-space>
        </div>
      </div>
    </a-form>
  </a-card>
  <a-form v-else layout="horizontal" class="lt-filter-bar-bare">
    <div class="lt-filter-bar__inner">
      <slot />
      <div v-if="$slots.actions" class="lt-filter-bar__actions">
        <a-space wrap>
          <slot name="actions" />
        </a-space>
      </div>
    </div>
  </a-form>
</template>

<style scoped>
.lt-filter-bar {
  margin-bottom: var(--lt-space-lg);
}

/* bare 模式：根元素用独立 class（不继承 .lt-filter-bar 的 margin-bottom），
   使用方自行加全局工具类控制节奏，例：PointsManagement 的 class="mb-16"。 */

.lt-filter-bar__inner {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  gap: var(--lt-space-md) var(--lt-space-lg);
}

/* 字段：默认插槽里的 a-form-item 通过全局规则 .lt-filter-bar__field 拿到 flex 自适应。
   这里只兜底：当字段没带类时也至少给最小宽度，避免被压成一行写不下的窄条。 */
.lt-filter-bar__inner > :deep(.ant-form-item) {
  margin-bottom: 0;
  flex: 1 1 220px;
  min-width: 180px;
  max-width: 320px;
}

.lt-filter-bar__actions {
  margin-left: auto;
  display: flex;
  align-items: center;
}

@media (max-width: 767px) {
  .lt-filter-bar__actions {
    margin-left: 0;
    width: 100%;
    justify-content: flex-start;
  }
}
</style>
