<script setup lang="ts">
defineProps<{ modelValue: 'latest' | 'popular' }>()
const emit = defineEmits<{ 'update:modelValue': [value: 'latest' | 'popular'] }>()
const change = (event: Event) => {
  emit('update:modelValue', (event.target as HTMLSelectElement).value === 'popular' ? 'popular' : 'latest')
}
</script>

<template>
  <label class="post-sort">
    <span>排序</span>
    <span class="post-sort-control">
      <select :value="modelValue" aria-label="文章排序" @change="change">
        <option value="latest">最新发布</option>
        <option value="popular">最多浏览</option>
      </select>
      <svg viewBox="0 0 16 16" aria-hidden="true"><path d="m4 6 4 4 4-4" /></svg>
    </span>
  </label>
</template>

<style scoped>
.post-sort { display: inline-flex; align-items: center; gap: 10px; color: var(--text-subtle); font-size: 13px; }
.post-sort-control { position: relative; }
select { appearance: none; font: inherit; color: var(--text-main); background: var(--bg-card); border: 1px solid var(--border-base); border-radius: 10px; padding: 10px 34px 10px 14px; cursor: pointer; min-height: 40px; }
select:hover { border-color: var(--color-primary); }
select:focus-visible { outline: 2px solid var(--color-primary); outline-offset: 2px; }
svg { position: absolute; pointer-events: none; right: 12px; top: 50%; transform: translateY(-50%); width: 14px; fill: none; stroke: currentColor; stroke-width: 1.5; }
</style>
