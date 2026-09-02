<script setup lang="ts">
import { useRouteLoading } from '@/composables/useRouteLoading'

const { isNavigating, isInitialLoading, pendingTitle } = useRouteLoading()
</script>

<template>
  <Transition name="route-progress">
    <div
      v-if="isNavigating && !isInitialLoading"
      class="route-progress"
      role="progressbar"
      :aria-label="pendingTitle ? `正在前往${pendingTitle}` : '正在切换页面'"
    >
      <span class="route-progress__bar" />
    </div>
  </Transition>
</template>

<style scoped>
.route-progress {
  position: fixed;
  top: 0;
  left: 0;
  z-index: 10000;
  width: 100%;
  height: 3px;
  overflow: hidden;
  pointer-events: none;
  opacity: 0;
  animation: route-progress-reveal 0s linear 120ms forwards;
}

.route-progress__bar {
  display: block;
  width: 38%;
  height: 100%;
  border-radius: 0 999px 999px 0;
  background: linear-gradient(90deg, var(--color-primary), var(--color-info));
  box-shadow: 0 0 10px rgba(var(--color-primary-rgb), 0.45);
  animation: route-progress-move 1.15s cubic-bezier(0.4, 0, 0.2, 1) infinite;
}

@keyframes route-progress-reveal {
  to { opacity: 1; }
}

@keyframes route-progress-move {
  from { transform: translateX(-110%); }
  to { transform: translateX(300%); }
}

.route-progress-leave-active {
  transition: opacity 0.18s ease;
}

.route-progress-leave-to {
  opacity: 0;
}

@media (prefers-reduced-motion: reduce) {
  .route-progress {
    animation-delay: 0s;
  }

  .route-progress__bar {
    width: 72%;
    animation: none;
  }
}
</style>
