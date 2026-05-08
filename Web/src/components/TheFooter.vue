<script setup lang="ts">
import Icon from './Icon.vue'
// 获取当前年份
const currentYear = new Date().getFullYear();
</script>

<template>
  <footer class="footer">
    <div class="container">
      <div class="footer-content">
        
        <!-- 1. 左侧：品牌信息 -->
        <div class="footer-brand">
          <h2 class="logo">LiuTech</h2>
          <p class="slogan">记录学习与生活的点滴</p>
        </div>

        <!-- 2. 中间：简洁导航 -->
        <nav class="footer-nav">
          <router-link to="/" class="nav-item">首页</router-link>
          <router-link to="/categories" class="nav-item">分类</router-link>
          <router-link to="/archive" class="nav-item">归档</router-link>
          <router-link to="/tags" class="nav-item">标签</router-link>
          <router-link to="/about" class="nav-item">关于</router-link>
          <span class="divider">|</span>
          <a href="/rss.xml" class="nav-item">RSS</a>
        </nav>

        <!-- 3. 右侧：社交链接 -->
        <div class="footer-social">
          <a href="https://github.com" target="_blank" class="social-btn" aria-label="GitHub">
            <Icon name="github" size="20" />
          </a>
          <a href="mailto:example@example.com" class="social-btn" aria-label="Email">
            <Icon name="mail" size="20" />
          </a>
        </div>
      </div>

      <!-- 底部版权 -->
      <div class="footer-bottom">
        <p class="copyright">
          &copy; {{ currentYear }} LiuTech. All Rights Reserved.
        </p>
        <p class="made-with">
          Made with <span class="heart">❤</span> by LiuTech
        </p>
      </div>
    </div>
  </footer>
</template>

<style scoped lang="scss">
@use "@/assets/styles/tokens" as *;

.footer {
  /* 使用极柔和的背景色，保持白色简洁感 */
  background-color: var(--bg-soft);
  border-top: 1px solid var(--border-light);
  padding: 64px 0 32px;
  margin-top: 80px;
  color: var(--text-subtle);
}

.container {
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 24px;
}

/* 上半部分布局 */
.footer-content {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 48px;
  flex-wrap: wrap;
  gap: 32px;

  @include respond(md) {
    flex-direction: column;
    text-align: center;
  }
}

/* 品牌样式 */
.footer-brand {
  .logo {
    font-size: 24px;
    font-weight: 700;
    color: var(--text-title);
    margin: 0 0 8px 0;
    letter-spacing: -0.5px;
  }
  
  .slogan {
    font-size: 14px;
    color: var(--text-muted);
    margin: 0;
  }
}

/* 导航链接样式 */
.footer-nav {
  display: flex;
  align-items: center;
  gap: 24px;
  
  @include respond(sm) {
    flex-wrap: wrap;
    justify-content: center;
    gap: 16px;
  }

  .nav-item {
    font-size: 15px;
    color: var(--text-subtle);
    text-decoration: none;
    transition: color 0.2s ease;
    font-weight: 500;

    &:hover {
      color: var(--color-primary);
    }
  }

  .divider {
    color: var(--border-base);
    font-size: 12px;
  }
}

/* 社交图标样式 */
.footer-social {
  display: flex;
  gap: 16px;

  .social-btn {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 40px;
    height: 40px;
    border-radius: 50%;
    background-color: var(--bg-main); /* 纯白背景 */
    color: var(--text-subtle);
    border: 1px solid var(--border-base);
    transition: all 0.3s ease;

    &:hover {
      background-color: var(--color-primary);
      color: white;
      border-color: var(--color-primary);
      transform: translateY(-2px);
    }
  }
}

/* 底部版权区域 */
.footer-bottom {
  border-top: 1px solid var(--border-light);
  padding-top: 32px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 13px;
  color: var(--text-muted);

  @include respond(md) {
    flex-direction: column;
    gap: 12px;
  }

  .heart {
    color: var(--color-error);
    display: inline-block;
    animation: heartbeat 1.5s infinite ease-in-out;
  }
}

@keyframes heartbeat {
  0%, 100% { transform: scale(1); }
  50% { transform: scale(1.1); }
}
</style>
