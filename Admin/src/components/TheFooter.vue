<script setup lang="ts">
// 获取当前年份
const currentYear = new Date().getFullYear()
</script>

<template>
  <footer class="admin-footer">
    <div class="footer-links">
      <a href="#" target="_blank">LiuTech 官网</a>
      <a href="#" target="_blank">文档中心</a>
      <a href="#" target="_blank">关于我们</a>
    </div>
    <div class="copyright">
      Copyright &copy; {{ currentYear }} LiuTech Experience Technology Department
    </div>
  </footer>
</template>

<style scoped>
.admin-footer {
  padding: 24px 16px;
  text-align: center;
  color: rgba(0, 0, 0, 0.45);
  font-size: 14px;
  background: var(--bg-card);
}

.footer-links {
  margin-bottom: 8px;
}

.footer-links a {
  color: rgba(0, 0, 0, 0.45);
  margin-right: 40px;
  transition: all 0.3s;
}

.footer-links a:last-child {
  margin-right: 0;
}

.footer-links a:hover {
  color: rgba(0, 0, 0, 0.65);
}

.copyright {
  color: rgba(0, 0, 0, 0.45);
}
</style>