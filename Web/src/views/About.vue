<template>
  <div class="about-page">
    <div class="about-header">
      <div class="avatar-section">
        <img src="/default-avatar.svg" alt="刘鑫" class="about-avatar" />
        <h1>刘鑫</h1>
        <p class="title">全栈工程师</p>
      </div>
    </div>

    <div class="about-content">
      <section class="intro-section">
        <h2>👋 关于我</h2>
        <p>
          你好！我是刘鑫，一名热爱技术的全栈工程师。专注于前端开发、后端架构和技术分享。
          喜欢探索新技术，记录学习过程，分享技术心得。
        </p>
        <p>
          这个博客是我用来记录学习笔记、技术总结和生活感悟的地方。
          希望通过分享，能够帮助到更多的开发者朋友。
        </p>
      </section>

      <section class="skills-section">
        <h2>🛠️ 技术栈</h2>
        <div class="skills-grid">
          <div class="skill-category">
            <h3>前端技术</h3>
            <div class="skill-tags">
              <span class="skill-tag">Vue.js</span>
              <span class="skill-tag">TypeScript</span>
              <span class="skill-tag">JavaScript</span>
              <span class="skill-tag">HTML/CSS</span>
              <span class="skill-tag">Vite</span>
            </div>
          </div>
          <div class="skill-category">
            <h3>后端技术</h3>
            <div class="skill-tags">
              <span class="skill-tag">Java</span>
              <span class="skill-tag">Spring Boot</span>
              <span class="skill-tag">MyBatis</span>
              <span class="skill-tag">MySQL</span>
              <span class="skill-tag">Spring Security</span>
            </div>
          </div>
          <div class="skill-category">
            <h3>开发工具</h3>
            <div class="skill-tags">
              <span class="skill-tag">Git</span>
              <span class="skill-tag">Maven</span>
              <span class="skill-tag">IntelliJ IDEA</span>
              <span class="skill-tag">VS Code</span>
            </div>
          </div>
        </div>
      </section>

      <section class="contact-section">
        <h2>📫 联系方式</h2>
        <div class="contact-grid">
          <div class="contact-item">
            <span class="contact-icon">📧</span>
            <span>邮箱：your-email@example.com</span>
          </div>
          <div class="contact-item">
            <span class="contact-icon">🐙</span>
            <span>GitHub：github.com/yourusername</span>
          </div>
          <div class="contact-item">
            <span class="contact-icon">💼</span>
            <span>领英：linkedin.com/in/yourusername</span>
          </div>
        </div>
      </section>

      <section class="blog-info-section">
        <h2>📝 关于本站</h2>
        <p>
          本博客基于 Vue 3 + Spring Boot 构建，采用前后端分离架构。
          支持文章发布、分类管理、标签系统、用户评论等功能。
        </p>
        <div class="tech-info">
          <div class="tech-item">
            <strong>前端：</strong>Vue 3 + TypeScript + Vite
          </div>
          <div class="tech-item">
            <strong>后端：</strong>Spring Boot + MyBatis + MySQL
          </div>
          <div class="tech-item">
            <strong>部署：</strong>Vercel + 云服务器
          </div>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
// 关于页面组件
</script>

<style scoped>
.about-page {
  max-width: 800px;
  margin: 0 auto;
  padding: 2rem;
}

.about-header {
  text-align: center;
  margin-bottom: 3rem;
  padding: 2rem;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border-radius: 1rem;
  color: white;
}

.about-avatar {
  width: 120px;
  height: 120px;
  border-radius: 50%;
  margin-bottom: 1rem;
  border: 4px solid rgba(255, 255, 255, 0.3);
}

.about-header h1 {
  margin: 0.5rem 0;
  font-size: 2rem;
}

.title {
  font-size: 1.1rem;
  opacity: 0.9;
  margin: 0;
}

.about-content {
  display: flex;
  flex-direction: column;
  gap: 2.5rem;
}

section {
  background: var(--card-bg);
  padding: 2rem;
  border-radius: 0.75rem;
  box-shadow: var(--shadow-sm);
}

section h2 {
  margin: 0 0 1.5rem 0;
  color: var(--text-primary);
  font-size: 1.5rem;
}

section p {
  line-height: 1.6;
  color: var(--text-secondary);
  margin-bottom: 1rem;
}

.skills-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(250px, 1fr));
  gap: 1.5rem;
}

.skill-category h3 {
  margin: 0 0 1rem 0;
  color: var(--text-primary);
  font-size: 1.1rem;
}

.skill-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
}

.skill-tag {
  background: var(--primary-color);
  color: white;
  padding: 0.3rem 0.8rem;
  border-radius: 1rem;
  font-size: 0.85rem;
  font-weight: 500;
}

.contact-grid {
  display: grid;
  gap: 1rem;
}

.contact-item {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding: 0.75rem;
  background: var(--bg-secondary);
  border-radius: 0.5rem;
}

.contact-icon {
  font-size: 1.2rem;
}

.tech-info {
  display: grid;
  gap: 0.75rem;
  margin-top: 1rem;
}

.tech-item {
  padding: 0.75rem;
  background: var(--bg-secondary);
  border-radius: 0.5rem;
  border-left: 4px solid var(--primary-color);
}

@media (max-width: 768px) {
  .about-page {
    padding: 1rem;
  }
  
  .about-header {
    padding: 1.5rem;
  }
  
  section {
    padding: 1.5rem;
  }
  
  .skills-grid {
    grid-template-columns: 1fr;
  }
}
</style>