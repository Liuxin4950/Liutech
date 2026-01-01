<script setup lang="ts">
// 获取当前年份
const currentYear = new Date().getFullYear();
</script>

<template>
  <footer class="footer">
    <div class="container">
      <!-- 主要内容区域 -->
      <div class="footer-main">
        <!-- 品牌信息 -->
        <div class="footer-brand">
          <div class="brand-logo">
            <h2>LiuTech</h2>
          </div>
          <p class="brand-description">记录学习与生活的点滴</p>
          <div class="social-links">
            <a href="https://github.com" target="_blank" class="social-link" aria-label="GitHub">
              <svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor">
                <path d="M12 0c-6.626 0-12 5.373-12 12 0 5.302 3.438 9.8 8.207 11.387.599.111.793-.261.793-.577v-2.234c-3.338.726-4.033-1.416-4.033-1.416-.546-1.387-1.333-1.756-1.333-1.756-1.089-.745.083-.729.083-.729 1.205.084 1.839 1.237 1.839 1.237 1.07 1.834 2.807 1.304 3.492.997.107-.775.418-1.305.762-1.604-2.665-.305-5.467-1.334-5.467-5.931 0-1.311.469-2.381 1.236-3.221-.124-.303-.535-1.524.117-3.176 0 0 1.008-.322 3.301 1.23.957-.266 1.983-.399 3.003-.404 1.02.005 2.047.138 3.006.404 2.291-1.552 3.297-1.23 3.297-1.23.653 1.653.242 2.874.118 3.176.77.84 1.235 1.911 1.235 3.221 0 4.609-2.807 5.624-5.479 5.921.43.372.823 1.102.823 2.222v3.293c0 .319.192.694.801.576 4.765-1.589 8.199-6.086 8.199-11.386 0-6.627-5.373-12-12-12z"/>
              </svg>
            </a>
            <a href="mailto:example@example.com" class="social-link" aria-label="Email">
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <rect x="2" y="4" width="20" height="16" rx="2"/>
                <path d="m22 7-10 5L2 7"/>
              </svg>
            </a>
          </div>
        </div>

        <!-- 导航链接 -->
        <div class="footer-sections">
          <div class="footer-section">
            <h3 class="section-title">快速导航</h3>
            <nav class="section-links">
              <router-link to="/" class="footer-link">首页</router-link>
              <router-link to="/categories" class="footer-link">分类</router-link>
              <router-link to="/archive" class="footer-link">归档</router-link>
              <router-link to="/about" class="footer-link">关于</router-link>
            </nav>
          </div>

          <div class="footer-section">
            <h3 class="section-title">更多</h3>
            <nav class="section-links">
              <router-link to="/login" class="footer-link">登录</router-link>
              <a href="/rss.xml" class="footer-link">RSS订阅</a>
              <a href="/sitemap.xml" class="footer-link">网站地图</a>
              <router-link to="/privacy" class="footer-link">隐私政策</router-link>
            </nav>
          </div>
        </div>
      </div>

      <!-- 底部版权信息 -->
      <div class="footer-bottom">
        <div class="footer-bottom-content">
          <p class="copyright">
            &copy; {{ currentYear }} LiuTech. 保留所有权利。
          </p>
          <p class="made-with">
            Made with <Icon name="heart" /> by LiuTech
          </p>
        </div>
      </div>
    </div>
  </footer>
</template>

<style scoped>
@use "@/assets/styles/tokens" as *;

.footer {
  background: linear-gradient(135deg, var(--bg-color) 0%, var(--bg-secondary) 100%);
  border-top: 1px solid var(--border-color);
  padding: 60px 0 30px;
  margin-top: 80px;
  transition: all 0.3s ease;
  position: relative;
}

.footer::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 2px;
  background: linear-gradient(90deg, var(--primary-color), var(--accent-color));
}

.container {
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 20px;
}

/* 主要内容区域 */
.footer-main {
  display: flex;
  justify-content: space-between;
}

/* 品牌信息区域 */
.footer-brand {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.brand-logo h2 {
  color: var(--primary-color);
  margin: 0;
  font-size: 2rem;
  font-weight: 700;
  letter-spacing: -0.5px;
}

.brand-description {
  color: var(--text-secondary);
  margin: 0;
  font-size: 1rem;
  line-height: 1.6;
  max-width: 300px;
}

.social-links {
  display: flex;
  gap: 16px;
}

.social-link {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 44px;
  height: 44px;
  border-radius: 50%;
  background: var(--bg-element);
  border: 1px solid var(--border-color);
  color: var(--text-secondary);
  text-decoration: none;
  transition: all 0.3s ease;
}

.social-link:hover {
  background: var(--primary-color);
  color: white;
  border-color: var(--primary-color);
  transform: translateY(-2px);
  box-shadow: 0 8px 25px rgba(var(--primary-rgb), 0.3);
}

/* 导航区域 */
.footer-sections {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 40px;
}

.footer-section {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.section-title {
  color: var(--text-primary);
  margin: 0;
  font-size: 1.1rem;
  font-weight: 600;
  letter-spacing: -0.3px;
}

.section-links {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.footer-link {
  color: var(--text-secondary);
  text-decoration: none;
  font-size: 0.95rem;
  transition: all 0.2s ease;
  position: relative;
  padding-left: 0;
}

.footer-link:hover {
  color: var(--primary-color);
  padding-left: 20px;
}

.footer-link::before {
  content: '→';
  position: absolute;
  left: -12px;
  opacity: 0;
  transition: all 0.2s ease;
}

.footer-link:hover::before {
  opacity: 1;
  left: 0;
}

/* 底部版权区域 */
.footer-bottom {
  border-top: 1px solid var(--border-color);
  padding-top: 30px;
}

.footer-bottom-content {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 16px;
}

.copyright, .made-with {
  color: var(--text-muted);
  margin: 0;
  font-size: 0.9rem;
}

.made-with {
  font-weight: 500;
}

/* 响应式设计 */
@include respond(lg) {
  .footer-main {
    gap: 40px;
  }
  
  .footer-sections {
    gap: 30px;
  }
}

@include respond(md) {
  .footer {
    padding: 40px 0 20px;
    margin-top: 60px;
  }
  
  .footer-main {
    grid-template-columns: 1fr;
    gap: 40px;
    text-align: center;
  }
  
  .footer-brand {
    align-items: center;
  }
  
  .brand-description {
    max-width: 100%;
  }
  
  .social-links {
    justify-content: center;
  }
  
  .footer-sections {
    grid-template-columns: 1fr;
    gap: 30px;
    text-align: left;
  }
  
  .footer-section {
    align-items: center;
  }
  
  .section-links {
    align-items: center;
  }
  
  .footer-bottom-content {
    flex-direction: column;
    text-align: center;
    gap: 8px;
  }
}

@include respond(sm) {
  .footer {
    padding: 30px 0 15px;
    margin-top: 40px;
  }
  
  .footer-main {
    gap: 30px;
  }
  
  .brand-logo h2 {
    font-size: 1.75rem;
  }
  
  .social-links {
    gap: 12px;
  }
  
  .social-link {
    width: 40px;
    height: 40px;
  }
  
  .footer-bottom {
    padding-top: 20px;
  }
}

/* 深色模式适配 */
@media (prefers-color-scheme: dark) {
  .footer {
    background: linear-gradient(135deg, var(--bg-color) 0%, var(--bg-secondary) 100%);
  }
  
  .social-link {
    background: var(--bg-element);
  }
  
  .social-link:hover {
    box-shadow: 0 8px 25px rgba(var(--primary-rgb), 0.4);
  }
}
</style>