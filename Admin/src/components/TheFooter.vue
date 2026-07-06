<script setup lang="ts">
import { useI18n } from '@/i18n'
const { t } = useI18n()
const currentYear = new Date().getFullYear()
</script>

<template>
  <footer class="admin-footer">
    <span>© {{ currentYear }} LiuTech</span>
    <span class="admin-footer__sep">·</span>
    <a href="https://github.com" target="_blank" rel="noopener">{{ t('footer.website') }}</a>
    <a href="https://github.com" target="_blank" rel="noopener">{{ t('footer.docs') }}</a>
    <a href="https://github.com" target="_blank" rel="noopener">{{ t('footer.about') }}</a>
  </footer>
</template>

<style scoped>
.admin-footer {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--lt-space-md);
  padding: var(--lt-space-sm) var(--lt-space-page-x);
  color: var(--lt-color-text-tertiary);
  font-size: var(--lt-font-size-xs);
  background: transparent;
  line-height: 1;
}

.admin-footer__sep {
  color: var(--lt-color-text-quaternary);
}

.admin-footer a {
  color: var(--lt-color-text-tertiary);
  transition: color var(--lt-duration-base) var(--lt-ease-in-out);
}

.admin-footer a:hover {
  color: var(--lt-color-primary);
}
</style>
