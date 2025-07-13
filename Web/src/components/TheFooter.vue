<script setup lang="ts">
// 获取当前年份
const currentYear = new Date().getFullYear();
</script>

<template>
  <footer class="footer">
    <div class="container">
      <div class="footer-content">
        <div class="footer-logo">
          <h2>MyBlog</h2>
          <p>记录学习与生活的点滴</p>
        </div>
        
        <div class="footer-links">
          <div class="link-group">
            <h3>导航</h3>
            <ul>
              <li><router-link to="/">首页</router-link></li>
              <li><router-link to="/login">登录</router-link></li>
            </ul>
          </div>
          
          <div class="link-group">
            <h3>联系我</h3>
            <ul>
              <li><a href="mailto:example@example.com">邮箱</a></li>
              <li><a href="https://github.com" target="_blank">GitHub</a></li>
            </ul>
          </div>
        </div>
      </div>
      
      <div class="footer-bottom">
        <p>&copy; {{ currentYear }} MyBlog. 保留所有权利。</p>
      </div>
    </div>
  </footer>
</template>

<style scoped>
.footer {
  background-color: var(--bg-color);
  border-top: 1px solid var(--border-color);
  padding: 50px 0 20px;
  margin-top: 50px;
  transition: background-color 0.3s;
}

.container {
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 20px;
}

.footer-content {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  margin-bottom: 40px;
}

.footer-logo h2 {
  color: var(--primary-color);
  margin: 0 0 10px;
  font-size: 1.5rem;
}

.footer-logo p {
  color: var(--text-color);
  opacity: 0.8;
  margin: 0;
}

.footer-links {
  display: flex;
  gap: 60px;
}

.link-group h3 {
  color: var(--text-color);
  font-size: 1.1rem;
  margin: 0 0 15px;
}

.link-group ul {
  list-style: none;
  padding: 0;
  margin: 0;
}

.link-group li {
  margin-bottom: 10px;
}

.link-group a {
  color: var(--text-color);
  opacity: 0.8;
  text-decoration: none;
  transition: opacity 0.3s, color 0.3s;
}

.link-group a:hover {
  opacity: 1;
  color: var(--primary-color);
}

.footer-bottom {
  text-align: center;
  padding-top: 20px;
  border-top: 1px solid var(--border-color);
}

.footer-bottom p {
  color: var(--text-color);
  opacity: 0.6;
  margin: 0;
  font-size: 0.9rem;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .footer-content {
    flex-direction: column;
    gap: 30px;
  }
  
  .footer-links {
    flex-direction: column;
    gap: 30px;
  }
}
</style>