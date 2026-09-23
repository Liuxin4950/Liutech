<script setup lang="ts">
import { computed } from 'vue'
import type { Component } from 'vue'
import { CheckCircleOutlined, CloseCircleOutlined } from '@ant-design/icons-vue'

/**
 * LtStatusTag —— 统一的状态标签（设计系统里一直缺的那个组件）
 * ---------------------------------------------------------------------
 * 背景：`tokens.css` 的注释里早就写了「LtStatusTag / LtSemanticTag 会用到」这些
 * preset 色，但组件一直没建，于是 14 个管理页各自手写「已删除/正常」「启用/禁用」
 * 「待审核/已通过/已拒绝」这类「颜色 + 文案」的 if/else 映射，同一份映射散落多处。
 * 这里把它收敛成唯一一份真源。
 *
 * 内部仍然是 `a-tag`，颜色与文案与替换前逐字一致；class / style / size /
 * bordered 等属性与 `#icon` 插槽都会照常透传，因此替换不影响既有样式。
 *
 * 用法：
 *   <LtStatusTag :status="record.deletedAt ? 'deleted' : 'normal'" />
 *   <LtStatusTag status="published" text="已上线" />
 */

/** 可用的状态键 */
export type StatusKey =
  | 'normal' | 'deleted'
  | 'enabled' | 'disabled'
  | 'pending' | 'approved' | 'rejected'
  | 'published' | 'draft'
  | 'success' | 'failed'
  | 'valid' | 'invalid'
  | 'top' | 'plain'

interface Preset {
  color: string
  label: string
  icon?: Component
}

/** 状态 → 颜色/文案 的唯一映射表（新增状态只改这里） */
const PRESETS: Record<StatusKey, Preset> = {
  normal: { color: 'green', label: '正常' },
  deleted: { color: 'red', label: '已删除' },
  enabled: { color: 'green', label: '启用' },
  disabled: { color: 'red', label: '禁用' },
  pending: { color: 'orange', label: '待审核' },
  approved: { color: 'green', label: '已通过' },
  rejected: { color: 'red', label: '已拒绝' },
  published: { color: 'green', label: '已发布' },
  draft: { color: 'orange', label: '草稿' },
  success: { color: 'success', label: '成功', icon: CheckCircleOutlined },
  failed: { color: 'error', label: '失败', icon: CloseCircleOutlined },
  valid: { color: 'green', label: '有效' },
  invalid: { color: 'orange', label: '无效' },
  top: { color: 'red', label: '置顶' },
  plain: { color: 'default', label: '普通' },
}

interface Props {
  /** 状态键；未知值回退为 default 色 + 原样文案 */
  status: string
  /** 覆盖默认文案（仅文案不同、状态语义相同时用） */
  text?: string
}

const props = defineProps<Props>()

const preset = computed<Preset>(() => {
  const hit = PRESETS[props.status as StatusKey]
  return hit ?? { color: 'default', label: props.status }
})
</script>

<template>
  <!-- status 为空串时整体不渲染，保持与原来 v-else-if 链"都不匹配就不显示"一致 -->
  <a-tag v-if="status" :color="preset.color">
    <template v-if="preset.icon" #icon>
      <component :is="preset.icon" />
    </template>
    {{ text ?? preset.label }}
  </a-tag>
</template>
