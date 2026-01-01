<script setup lang="ts">
import Icon from '../components/Icon.vue'

// 关于页面组件


<template>
  <div class="about-page">
    <!-- 顶部个人卡片 -->
    <div class="profile-card">
      <div class="profile-inner">
        <div class="avatar-section">
          <img src="/default-avatar.svg" alt="刘鑫" class="user-avatar" />
        </div>
        <div class="user-info">
          <h1 class="username">刘鑫</h1>
          <p class="user-bio">全栈工程师 & 技术博主</p>
          <p class="user-motto">「代码改变世界，热爱成就未来」</p>
        </div>
        <div class="social-links">
          <a href="#" class="social-item" title="GitHub">
            <Icon name="github" size="18" />
          </a>
          <a href="#" class="social-item" title="邮箱">
            <Icon name="mail" size="18" />
          </a>
          <a href="#" class="social-item" title="网站">
            <Icon name="globe" size="18" />
          </a>
          <a href="#" class="social-item" title="哔哩哔哩">
            <Icon name="video" size="18" />
          </a>
        </div>
      </div>
    </div>

    <!-- 下方内容区 -->
    <div class="main-content">
      <div class="content">
        <!-- 左侧主要内容 -->
        <div class="content-left">
          <!-- 关于我 -->
          <section class="section-card">
            <div class="section-header">
              <Icon name="person" size="18" />
              <span>关于我</span>
            </div>
            <div class="section-body">
              <p class="about-text">
                你好！我是刘鑫，一名热爱技术的全栈工程师。专注于前端开发、后端架构设计，
                喜欢探索新技术并分享技术心得。
              </p>
              <p class="about-text">
                这个博客是我记录技术学习和生活感悟的地方，希望能够帮助到更多的开发者朋友。
              </p>
              <div class="highlights">
                <div class="highlight-item">
                  <Icon name="code" size="16" />
                  <span>全栈开发</span>
                </div>
                <div class="highlight-item">
                  <Icon name="lightbulb" size="16" />
                  <span>技术分享</span>
                </div>
                <div class="highlight-item">
                  <Icon name="rocket" size="16" />
                  <span>开源贡献</span>
                </div>
                <div class="highlight-item">
                  <Icon name="school" size="16" />
                  <span>终身学习</span>
                </div>
              </div>
            </div>
          </section>

          <!-- 技术栈 -->
          <section class="section-card">
            <div class="section-header">
              <Icon name="code" size="18" />
              <span>技术栈</span>
            </div>
            <div class="section-body">
              <div class="skill-category">
                <h3 class="skill-title">前端技术</h3>
                <div class="tags-cloud">
                  <span class="tag">Vue.js</span>
                  <span class="tag">React</span>
                  <span class="tag">TypeScript</span>
                  <span class="tag">JavaScript</span>
                  <span class="tag">HTML/CSS</span>
                  <span class="tag">Sass</span>
                  <span class="tag">Vite</span>
                  <span class="tag">Pinia</span>
                </div>
              </div>
              <div class="skill-category">
                <h3 class="skill-title">后端技术</h3>
                <div class="tags-cloud">
                  <span class="tag">Java</span>
                  <span class="tag">Spring Boot</span>
                  <span class="tag">MyBatis</span>
                  <span class="tag">MySQL</span>
                  <span class="tag">Redis</span>
                  <span class="tag">Docker</span>
                  <span class="tag">Nginx</span>
                </div>
              </div>
              <div class="skill-category">
                <h3 class="skill-title">开发工具</h3>
                <div class="tags-cloud">
                  <span class="tag">Git</span>
                  <span class="tag">VS Code</span>
                  <span class="tag">IntelliJ IDEA</span>
                  <span class="tag">Postman</span>
                  <span class="tag">Maven</span>
                  <span class="tag">Docker Desktop</span>
                </div>
              </div>
            </div>
          </section>

          <!-- 学习历程 -->
          <section class="section-card">
            <div class="section-header">
              <Icon name="history" size="18" />
              <span>学习历程</span>
            </div>
            <div class="section-body">
              <div class="timeline">
                <div class="timeline-item">
                  <div class="timeline-dot"></div>
                  <div class="timeline-content">
                    <span class="timeline-year">2022</span>
                    <p class="timeline-text">开始系统学习编程，掌握 Java 基础</p>
                  </div>
                </div>
                <div class="timeline-item">
                  <div class="timeline-dot"></div>
                  <div class="timeline-content">
                    <span class="timeline-year">2023</span>
                    <p class="timeline-text">深入 Spring Boot 开发，完成首个项目</p>
                  </div>
                </div>
                <div class="timeline-item">
                  <div class="timeline-dot"></div>
                  <div class="timeline-content">
                    <span class="timeline-year">2024</span>
                    <p class="timeline-text">学习 Vue 3 + TypeScript，开始写博客</p>
                  </div>
                </div>
                <div class="timeline-item">
                  <div class="timeline-dot"></div>
                  <div class="timeline-content">
                    <span class="timeline-year">2025</span>
                    <p class="timeline-text">持续深耕全栈技术，探索 AI 应用开发</p>
                  </div>
                </div>
              </div>
            </div>
          </section>

          <!-- 兴趣爱好 -->
          <section class="section-card">
            <div class="section-header">
              <Icon name="favorite" size="18" />
              <span>兴趣爱好</span>
            </div>
            <div class="section-body">
              <div class="hobbies-grid">
                <div class="hobby-item">
                  <Icon name="code" size="24" class="hobby-icon" />
                  <span class="hobby-name">编程</span>
                </div>
                <div class="hobby-item">
                  <Icon name="book" size="24" class="hobby-icon" />
                  <span class="hobby-name">阅读</span>
                </div>
                <div class="hobby-item">
                  <Icon name="music" size="24" class="hobby-icon" />
                  <span class="hobby-name">音乐</span>
                </div>
                <div class="hobby-item">
                  <Icon name="game" size="24" class="hobby-icon" />
                  <span class="hobby-name">游戏</span>
                </div>
                <div class="hobby-item">
                  <Icon name="video" size="24" class="hobby-icon" />
                  <span class="hobby-name">影视</span>
                </div>
                <div class="hobby-item">
                  <Icon name="run" size="24" class="hobby-icon" />
                  <span class="hobby-name">运动</span>
                </div>
              </div>
            </div>
          </section>

          <!-- 联系方式 -->
          <section class="section-card">
            <div class="section-header">
              <Icon name="chat" size="18" />
              <span>联系方式</span>
            </div>
            <div class="section-body">
              <div class="contact-list">
                <div class="contact-item">
                  <Icon name="mail" size="16" class="contact-icon" />
                  <span class="contact-text">liuxin@example.com</span>
                </div>
                <div class="contact-item">
                  <Icon name="github" size="16" class="contact-icon" />
                  <span class="contact-text">GitHub: @liuxin</span>
                </div>
                <div class="contact-item">
                  <Icon name="globe" size="16" class="contact-icon" />
                  <span class="contact-text">个人网站: liutech.blog</span>
                </div>
                <div class="contact-item">
                  <Icon name="location" size="16" class="contact-icon" />
                  <span class="contact-text">所在地: 中国 · 深圳</span>
                </div>
              </div>
            </div>
          </section>
        </div>

        <!-- 右侧边栏 -->
        <div class="content-right">
          <!-- 关于本站 -->
          <section class="section-card sticky">
            <div class="section-header">
              <Icon name="rocket" size="18" />
              <span>关于本站</span>
            </div>
            <div class="section-body">
              <p class="site-desc">
                本站采用现代化的技术栈构建，前后端分离架构，
                支持文章发布、分类管理、标签系统、用户评论等功能。
              </p>
              <div class="tech-stack-list">
                <div class="tech-item">
                  <span class="tech-label">前端</span>
                  <span class="tech-value">Vue 3 + TypeScript + Vite</span>
                </div>
                <div class="tech-item">
                  <span class="tech-label">后端</span>
                  <span class="tech-value">Spring Boot + MyBatis + MySQL</span>
                </div>
                <div class="tech-item">
                  <span class="tech-label">部署</span>
                  <span class="tech-value">Docker + 云服务器</span>
                </div>
              </div>
            </div>
          </section>

          <!-- 给我留言 -->
          <section class="section-card sticky">
            <div class="section-header">
              <Icon name="message" size="18" />
              <span>给我留言</span>
            </div>
            <div class="section-body">
              <p class="message-desc">
                如果你有什么想法、建议或者想要交流的内容，欢迎给我留言！
              </p>
              <button class="message-btn">
                <Icon name="edit" size="16" />
                写留言
              </button>
            </div>
          </section>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
@use "@/assets/styles/tokens" as *;

// 页面容器
.about-page {
  min-height: 100vh;
}

// 顶部悬浮卡片
.profile-card {
  margin: 0 auto 0;
  max-width: 1200px;
  padding: 0 20px;
  position: relative;
}

.profile-inner {
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  border-radius: 16px;
  padding: 32px 40px;
  display: flex;
  align-items: center;
  gap: 32px;
  min-height: 140px;
  background: var(--bg-card);
  border: 1px solid var(--border-base);
  box-shadow: var(--shadow-sm);

  @include respond(lg) {
    flex-wrap: wrap;
    justify-content: center;
    min-height: auto;
    padding: 28px;
    gap: 24px;
  }

  @include respond(md) {
    flex-direction: column;
    text-align: center;
    padding: 24px;
  }
}

.avatar-section {
  flex-shrink: 0;
}

.user-avatar {
  width: 100px;
  height: 100px;
  border-radius: 50%;
  border: 3px solid var(--bg-card);
  object-fit: cover;
  box-shadow: var(--shadow-sm);

  @include respond(sm) {
    width: 80px;
    height: 80px;
  }
}

.user-info {
  flex: 1;
  min-width: 180px;

  @include respond(md) {
    width: 100%;
  }
}

.username {
  font-size: 1.5rem;
  font-weight: 600;
  color: var(--text-title);
  margin: 0 0 6px 0;

  @include respond(md) {
    justify-content: center;
  }
}

.user-bio {
  color: var(--text-subtle);
  font-size: 0.95rem;
  margin: 0 0 8px 0;

  @include respond(md) {
    text-align: center;
  }
}

.user-motto {
  color: var(--color-primary);
  font-size: 0.9rem;
  font-style: italic;
  margin: 0;

  @include respond(md) {
    text-align: center;
  }
}

.social-links {
  display: flex;
  gap: 10px;
  flex-shrink: 0;

  @include respond(md) {
    justify-content: center;
  }
}

.social-item {
  width: 38px;
  height: 38px;
  border-radius: 10px;
  background: var(--bg-soft);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-subtle);
  transition: all 0.2s;

  &:hover {
    background: var(--color-primary);
    color: white;
    transform: translateY(-2px);
  }
}

// 主要内容区
.main-content {
  padding: 24px 0 60px;
}

.content {
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 20px;
  display: grid;
  grid-template-columns: 1fr 300px;
  gap: 20px;
  align-items: start;

  @include respond(lg) {
    grid-template-columns: 1fr;
  }
}

.content-left {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.content-right {
  display: flex;
  flex-direction: column;
  gap: 16px;

  @include respond(lg) {
    order: -1;
    flex-direction: row;
    flex-wrap: wrap;
  }
}

// 卡片样式
.section-card {
  background: var(--bg-card);
  border-radius: 12px;
  padding: 20px;
  border: 1px solid var(--border-base);
  box-shadow: var(--shadow-sm);

  &.sticky {
    position: sticky;
    top: 90px;

    @include respond(lg) {
      position: static;
      flex: 1;
      min-width: 280px;
    }
  }
}

.section-header {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 1rem;
  font-weight: 600;
  color: var(--text-title);
  margin-bottom: 16px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--border-light);
}

.section-body {
  color: var(--text-subtle);
  line-height: 1.8;
}

// 关于我
.about-text {
  margin: 0 0 12px 0;

  &:last-child {
    margin-bottom: 0;
  }
}

.highlights {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px solid var(--border-light);
}

.highlight-item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  background: var(--bg-soft);
  border-radius: 20px;
  font-size: 0.85rem;
  color: var(--text-main);

  :deep(svg) {
    color: var(--color-primary);
  }
}

// 技术栈
.skill-category {
  margin-bottom: 16px;

  &:last-child {
    margin-bottom: 0;
  }
}

.skill-title {
  font-size: 0.9rem;
  font-weight: 600;
  color: var(--color-primary);
  margin: 0 0 10px 0;
}

// 学习历程 - 时间线
.timeline {
  position: relative;
  padding-left: 20px;

  &::before {
    content: '';
    position: absolute;
    left: 5px;
    top: 8px;
    bottom: 8px;
    width: 2px;
    background: var(--border-light);
  }
}

.timeline-item {
  position: relative;
  padding-bottom: 16px;

  &:last-child {
    padding-bottom: 0;
  }
}

.timeline-dot {
  position: absolute;
  left: -20px;
  top: 6px;
  width: 12px;
  height: 12px;
  border-radius: 50%;
  background: var(--bg-card);
  border: 2px solid var(--color-primary);
}

.timeline-content {
  padding-left: 12px;
}

.timeline-year {
  font-size: 0.85rem;
  font-weight: 600;
  color: var(--color-primary);
}

.timeline-text {
  font-size: 0.9rem;
  color: var(--text-main);
  margin: 4px 0 0 0;
}

// 兴趣爱好
.hobbies-grid {
  display: grid;
  grid-template-columns: repeat(6, 1fr);
  gap: 12px;

  @include respond(md) {
    grid-template-columns: repeat(3, 1fr);
  }
}

.hobby-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 16px 8px;
  background: var(--bg-soft);
  border-radius: 12px;
  transition: all 0.2s;

  &:hover {
    transform: translateY(-2px);
    background: var(--bg-tag);
  }
}

.hobby-icon {
  color: var(--color-primary);
}

.hobby-name {
  font-size: 0.8rem;
  color: var(--text-main);
}

// 联系方式
.contact-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.contact-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 14px;
  background: var(--bg-soft);
  border-radius: 8px;
  color: var(--text-main);
  font-size: 0.9rem;
}

.contact-icon {
  color: var(--color-primary);
}

// 关于本站
.site-desc {
  margin: 0 0 16px 0;
  line-height: 1.7;
}

.tech-stack-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.tech-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 14px;
  background: var(--bg-soft);
  border-radius: 8px;
  border-left: 3px solid var(--color-primary);
}

.tech-label {
  font-size: 0.8rem;
  font-weight: 600;
  color: var(--color-primary);
  min-width: 45px;
}

.tech-value {
  font-size: 0.85rem;
  color: var(--text-main);
}

// 给我留言
.message-desc {
  margin: 0 0 16px 0;
  font-size: 0.9rem;
  line-height: 1.6;
}

.message-btn {
  width: 100%;
  padding: 12px;
  background: linear-gradient(135deg, var(--color-primary) 0%, var(--color-primary-dark) 100%);
  color: white;
  border: none;
  border-radius: 8px;
  font-size: 0.95rem;
  font-weight: 500;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  transition: all 0.2s;

  &:hover {
    transform: translateY(-1px);
    box-shadow: 0 4px 12px rgba(45, 144, 205, 0.3);
  }
}

// 响应式
@include respond(sm) {
  .profile-inner {
    padding: 20px;
    gap: 16px;
  }

  .username {
    font-size: 1.3rem;
  }

  .section-card {
    padding: 16px;
  }

  .section-header {
    margin-bottom: 12px;
    padding-bottom: 10px;
    font-size: 0.95rem;
  }

  .hobbies-grid {
    grid-template-columns: repeat(3, 1fr);
  }

  .hobby-item {
    padding: 12px 6px;
  }
}
</style>
