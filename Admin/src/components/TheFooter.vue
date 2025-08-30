<script setup lang="ts">
// 获取当前年份
const currentYear = new Date().getFullYear()
</script>

<template>
  <footer class="admin-footer">
    <div class="footer-content">
      <div class="footer-left">
        <span class="copyright">
          &copy; {{ currentYear }} LiuTech 管理后台 - 刘鑫开发
        </span>
      </div>
      <div class="footer-right">
        <span class="version">v1.0.0</span>
        <span class="divider">|</span>
        <span class="powered">Powered by Vue 3 + Ant Design Vue</span>
      </div>
    </div>
  </footer>
</template>

<style scoped>
.admin-footer {
  background: var(--bg-main);
  border-top: 1px solid var(--border-color);
  padding: 16px 24px;
  font-size: 12px;
  color: var(--text-secondary);
  transition: all 0.3s ease;
}

.footer-content {
  display: flex;
  justify-content: space-between;
  align-items: center;
  max-width: 100%;
}

.footer-left {
  display: flex;
  align-items: center;
}

.copyright {
  color: var(--text-secondary);
  font-size: 12px;
}

.footer-right {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: var(--text-secondary);
}

.version {
  color: var(--primary-color);
  font-weight: 500;
}

.divider {
  color: var(--border-color);
  margin: 0 4px;
}

.powered {
  color: var(--text-secondary);
}

/* 响应式设计 */
@media (max-width: 768px) {
  .footer-content {
    flex-direction: column;
    gap: 8px;
    text-align: center;
  }
  
  .footer-right {
    justify-content: center;
  }
}

/* 深色主题适配 */
:global(.dark) .admin-footer {
  background: var(--bg-main);
  border-top-color: var(--border-color);
}
</style>