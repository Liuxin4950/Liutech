<script setup lang="ts">
withDefaults(defineProps<{
  label?: string
  compact?: boolean
}>(), {
  label: '正在加载…',
  compact: false
})
</script>

<template>
  <div
    class="loading-state"
    :class="{ 'loading-state--compact': compact }"
    role="status"
    aria-live="polite"
  >
    <span class="loading-state__spinner" aria-hidden="true"></span>
    <span>{{ label }}</span>
  </div>
</template>

<style scoped>
.loading-state {
  width: 100%;
  min-height: 160px;
  padding: 36px 20px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  color: var(--text-muted);
  font-size: 0.875rem;
  line-height: 1.5;
  text-align: center;
}

.loading-state__spinner {
  width: 28px;
  height: 28px;
  border: 3px solid var(--border-soft);
  border-top-color: var(--color-primary);
  border-radius: 50%;
  animation: loading-state-spin 0.9s linear infinite;
}

.loading-state--compact {
  min-height: 96px;
  padding: 20px 12px;
  gap: 10px;
}

@keyframes loading-state-spin {
  to { transform: rotate(360deg); }
}

@media (prefers-reduced-motion: reduce) {
  .loading-state__spinner {
    animation-duration: 1.8s;
  }
}
</style>
