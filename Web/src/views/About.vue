<script setup lang="ts">
import Icon from '../components/Icon.vue'

// 关于页面组件
</script>

<template>
  <div class="about-page content">
    <!-- 顶部 Hero 区域 -->
    <div class="hero-section">
      <div class="hero-content">
        <div class="avatar-wrapper">
          <div class="user-avatar">
            <img src="@/assets/image/gif/坐下.gif" alt="刘鑫" class="liuyin" />
          </div>
        </div>
        <div class="hero-text">
          <h1 class="username">
            <svg class="name-svg" viewBox="0 0 200 60">
              <defs>
                <linearGradient id="gradient" x1="0%" y1="0%" x2="100%" y2="0%">
                  <stop offset="0%" style="stop-color:var(--color-primary);stop-opacity:1" />
                  <stop offset="100%" style="stop-color:var(--color-accent);stop-opacity:1" />
                </linearGradient>
              </defs>
              <!-- 底层：静态显示的文字 -->
              <text x="50%" y="50%" dy=".35em" text-anchor="middle" class="name-base">
                刘鑫
              </text>
              <!-- 顶层：流光描边动画 -->
              <text x="50%" y="50%" dy=".35em" text-anchor="middle" class="name-stroke">
                刘鑫
              </text>
            </svg>
          </h1>
          <p class="user-bio">全栈工程师 & 技术博主</p>
          <p class="user-motto">「代码改变世界，热爱成就未来」</p>
          <div class="social-links">
            <a href="#" class="social-item" title="GitHub">
              <Icon name="github" size="20" />
            </a>
            <a href="#" class="social-item" title="邮箱">
              <Icon name="mail" size="20" />
            </a>
            <a href="#" class="social-item" title="网站">
              <Icon name="globe" size="20" />
            </a>
          </div>
        </div>
      </div>
    </div>

    <!-- 主要内容区 -->
    <div class="main-container">
      <div class="content-grid">
        <!-- 左侧主要信息 -->
        <div class="main-column">
          <!-- 关于我 -->
          <section class="info-card">
            <h2 class="card-title">
              <Icon name="person" size="24" class="title-icon" />
              关于我
            </h2>
            <div class="card-body">
              <p class="about-text">
                你好！我是刘鑫，一名热爱技术的全栈工程师。专注于前端开发、后端架构设计，
                喜欢探索新技术并分享技术心得。拥有多年的开发经验，致力于构建高性能、
                高可用的 Web 应用。
              </p>
              <p class="about-text">
                这个博客是我记录技术学习和生活感悟的地方，希望能够帮助到更多的开发者朋友。
                在工作之余，我也活跃于开源社区，贡献代码并参与技术讨论。
              </p>
              
              <div class="stats-row">
                <div class="stat-item">
                  <span class="stat-num">3+</span>
                  <span class="stat-label">开发经验</span>
                </div>
                <div class="stat-item">
                  <span class="stat-num">20+</span>
                  <span class="stat-label">项目经历</span>
                </div>
                <div class="stat-item">
                  <span class="stat-num">50+</span>
                  <span class="stat-label">技术文章</span>
                </div>
              </div>
            </div>
          </section>

          <!-- 学习历程 -->
          <section class="info-card">
            <h2 class="card-title">
              <Icon name="history" size="24" class="title-icon" />
              经历与历程
            </h2>
            <div class="card-body">
              <div class="timeline">
                <div class="timeline-item">
                  <div class="timeline-year">2025</div>
                  <div class="timeline-content">
                    <h3>持续深耕 & AI 探索</h3>
                    <p>专注于全栈技术深耕，同时探索 AI Agent 与现有业务的结合，开发了多个实用的 AI 辅助工具。</p>
                  </div>
                </div>
                <div class="timeline-item">
                  <div class="timeline-year">2024</div>
                  <div class="timeline-content">
                    <h3>Vue 3 + TypeScript</h3>
                    <p>全面转向 Vue 3 生态，重构个人博客系统，深入学习 TypeScript 类型系统与工程化实践。</p>
                  </div>
                </div>
                <div class="timeline-item">
                  <div class="timeline-year">2023</div>
                  <div class="timeline-content">
                    <h3>Spring Boot 实战</h3>
                    <p>深入后端开发，掌握 Spring Boot 微服务架构，独立完成了首个企业级后台管理系统的开发。</p>
                  </div>
                </div>
                <div class="timeline-item">
                  <div class="timeline-year">2022</div>
                  <div class="timeline-content">
                    <h3>编程启蒙</h3>
                    <p>开始系统学习计算机基础与 Java 编程，打下扎实的基础，开启了编程探索之旅。</p>
                  </div>
                </div>
              </div>
            </div>
          </section>
        </div>

        <!-- 右侧侧边栏 -->
        <div class="sidebar-column">
          <!-- 技术栈 -->
          <section class="info-card">
            <h2 class="card-title">
              <Icon name="code" size="20" class="title-icon" />
              技术栈
            </h2>
            <div class="card-body">
              <div class="skill-group">
                <h3 class="skill-label">前端开发</h3>
                <div class="skill-tags">
                  <span class="skill-tag">Vue 3</span>
                  <span class="skill-tag">TypeScript</span>
                  <span class="skill-tag">Vite</span>
                  <span class="skill-tag">Tailwind</span>
                </div>
              </div>
              <div class="skill-group">
                <h3 class="skill-label">后端开发</h3>
                <div class="skill-tags">
                  <span class="skill-tag">Java</span>
                  <span class="skill-tag">Spring Boot</span>
                  <span class="skill-tag">MySQL</span>
                  <span class="skill-tag">Redis</span>
                </div>
              </div>
              <div class="skill-group">
                <h3 class="skill-label">工具 & 运维</h3>
                <div class="skill-tags">
                  <span class="skill-tag">Git</span>
                  <span class="skill-tag">Docker</span>
                  <span class="skill-tag">Nginx</span>
                  <span class="skill-tag">Linux</span>
                </div>
              </div>
            </div>
          </section>

          <!-- 兴趣爱好 -->
          <section class="info-card">
            <h2 class="card-title">
              <Icon name="favorite" size="20" class="title-icon" />
              兴趣爱好
            </h2>
            <div class="card-body">
              <div class="hobbies-grid">
                <div class="hobby-item">
                  <Icon name="code" size="24" class="hobby-icon" />
                  <span>编程</span>
                </div>
                <div class="hobby-item">
                  <Icon name="book" size="24" class="hobby-icon" />
                  <span>阅读</span>
                </div>
                <div class="hobby-item">
                  <Icon name="music" size="24" class="hobby-icon" />
                  <span>音乐</span>
                </div>
                <div class="hobby-item">
                  <Icon name="game" size="24" class="hobby-icon" />
                  <span>游戏</span>
                </div>
                <div class="hobby-item">
                  <Icon name="film" size="24" class="hobby-icon" />
                  <span>电影</span>
                </div>
                <div class="hobby-item">
                  <Icon name="running" size="24" class="hobby-icon" />
                  <span>跑步</span>
                </div>
                <div class="hobby-item">
                  <Icon name="camera" size="24" class="hobby-icon" />
                  <span>摄影</span>
                </div>
              </div>
            </div>
          </section>

          <!-- 联系我 -->
          <section class="info-card">
            <h2 class="card-title">
              <Icon name="chat" size="20" class="title-icon" />
              联系我
            </h2>
            <div class="card-body">
              <ul class="contact-list">
                <li>
                  <Icon name="mail" size="16" />
                  <span>liuxin@example.com</span>
                </li>
                <li>
                  <Icon name="github" size="16" />
                  <span>github.com/liuxin</span>
                </li>
                <li>
                  <Icon name="location" size="16" />
                  <span>中国 · 深圳</span>
                </li>
              </ul>
              <button class="message-btn">
                <Icon name="edit" size="16" />
                给我留言
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

// Hero 区域
.hero-section {
  width: 100%;
  position: absolute;
  left: 0;
  top: 50px;
  padding: 80px 20px 100px;
  border-bottom: 1px solid var(--border-light);
  @include respond(md) {
    position: relative;
  }
}

.hero-content {
  max-width: 800px;
  margin: 0 auto;
  text-align: center;
  position: relative;
  z-index: 1;
}

.avatar-wrapper {
  display: flex;
  justify-content: center;
  margin-bottom: 24px;
  
  .user-avatar {
    width: 120px;
    height: 120px;
    border-radius: 50%;
    border: 4px solid var(--bg-card);
    background-color: var(--bg-hover);
    box-shadow: 0 8px 24px rgba(0, 0, 0, 0.5);
    object-fit: cover;
    transition: transform 0.3s ease;
    position: relative;
    .liuyin{
      width: 160px ;
      height: 140px;
      position: absolute;
      top: 10px;
      left: -20px;
    }
    
    &:hover {
      transform: scale(1.05) rotate(5deg);
    }
  }
}

.hero-text {
  .username {
    margin-bottom: 12px;
    display: flex;
    justify-content: center;
    align-items: center;
    height: 80px;
    
    .name-svg {
      width: 100%;
      height: 100%;
      max-width: 240px;
      overflow: visible;
      
      text {
        font-size: 48px;
        font-weight: 300;
        letter-spacing: 0.2em;
      }
      
      .name-base {
        fill: #000;
        fill-opacity: 0.4;
        stroke: none;
        filter: drop-shadow(2px 2px 2px rgba(0, 0, 0, 0.5));
      }

      .name-stroke {
        fill: transparent;
        stroke: url(#gradient);
        stroke-width: 1.5px;
        stroke-linecap: round;
        /* 100px 亮光带，300px 间隔，4s 循环，确保无缝衔接 */
        stroke-dasharray: 100 300;
        stroke-dashoffset: 400;
        animation: stroke-flow 4s linear infinite;
      }
    }
  }

  @keyframes stroke-flow {
    to {
      stroke-dashoffset: 0;
    }
  }
  
  .user-bio {
    font-size: 1.1rem;
    color: rgba($color: #000000, $alpha: 0.6);
    margin-bottom: 8px;
    font-weight: 500;
    filter: drop-shadow(2px 2px 2px rgba(255, 255, 255, .5));
  }
  
  .user-motto {
    filter: drop-shadow(2px 2px 2px rgba(255, 255, 255, .5));
    color: rgba($color: #000000, $alpha: 0.6);
    font-size: 0.95rem;
    font-style: italic;
    margin-bottom: 24px;
  }
}

.social-links {
  display: flex;
  justify-content: center;
  gap: 16px;
  
  .social-item {
    width: 44px;
    height: 44px;
    border-radius: 50%;
    background: var(--bg-card);
    display: flex;
    align-items: center;
    justify-content: center;
    color: var(--text-subtle);
    transition: all 0.2s ease;
    border: 1px solid var(--border-light);
    
    &:hover {
      background: var(--color-primary);
      color: white;
      transform: translateY(-3px);
      box-shadow: 0 4px 12px rgba(var(--color-primary-rgb), 0.3);
      border-color: var(--color-primary);
    }
  }
}

// 主要内容布局
.main-container {
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 20px 60px;
  position: relative;
  z-index: 2;
}

.content-grid {
  display: grid;
  grid-template-columns: 1fr 340px;
  gap: 24px;
  
  @include respond(md) {
    grid-template-columns: 1fr;
  }
}

.info-card {
  background: var(--bg-card);
  border-radius: 16px;
  padding: 32px;
  margin-bottom: 24px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.04);
  border: 1px solid var(--border-light);
  transition: transform 0.3s ease, box-shadow 0.3s ease;
  
  &:hover {
    transform: translateY(-2px);
    box-shadow: 0 8px 30px rgba(0, 0, 0, 0.06);
  }
  
  .card-title {
    font-size: 1.25rem;
    font-weight: 700;
    color: var(--text-title);
    margin-bottom: 24px;
    display: flex;
    align-items: center;
    gap: 10px;
    padding-bottom: 16px;
    border-bottom: 1px solid var(--border-light);
    
    .title-icon {
      color: var(--color-primary);
    }
  }
}

// 统计数据
.stats-row {
  display: flex;
  gap: 40px;
  margin-top: 32px;
  padding-top: 24px;
  border-top: 1px dashed var(--border-light);
  
  .stat-item {
    display: flex;
    flex-direction: column;
    
    .stat-num {
      font-size: 1.8rem;
      font-weight: 800;
      color: var(--color-primary);
      line-height: 1;
      margin-bottom: 4px;
    }
    
    .stat-label {
      font-size: 0.85rem;
      color: var(--text-subtle);
    }
  }
}

// 关于文本
.about-text {
  line-height: 1.8;
  color: var(--text-main);
  margin-bottom: 16px;
  font-size: 1rem;
}

// 时间轴
.timeline {
  position: relative;
  padding-left: 12px;
  
  &::before {
    content: '';
    position: absolute;
    left: 0;
    top: 8px;
    bottom: 0;
    width: 2px;
    background: var(--border-light);
    border-radius: 2px;
  }
}

.timeline-item {
  position: relative;
  padding-left: 24px;
  padding-bottom: 32px;
  
  &:last-child {
    padding-bottom: 0;
  }
  
  &::after {
    content: '';
    position: absolute;
    left: -4px;
    top: 8px;
    width: 10px;
    height: 10px;
    border-radius: 50%;
    background: var(--bg-card);
    border: 2px solid var(--color-primary);
    box-shadow: 0 0 0 4px var(--bg-soft);
  }
  
  .timeline-year {
    font-size: 0.9rem;
    font-weight: 700;
    color: var(--color-primary);
    margin-bottom: 4px;
  }
  
  .timeline-content {
    h3 {
      font-size: 1.1rem;
      font-weight: 600;
      color: var(--text-title);
      margin-bottom: 8px;
    }
    
    p {
      font-size: 0.95rem;
      color: var(--text-subtle);
      line-height: 1.6;
      margin: 0;
    }
  }
}

// 技能标签
.skill-group {
  margin-bottom: 20px;
  
  &:last-child {
    margin-bottom: 0;
  }
  
  .skill-label {
    font-size: 0.9rem;
    font-weight: 600;
    color: var(--text-subtle);
    margin-bottom: 10px;
    display: block;
  }
  
  .skill-tags {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
    
    .skill-tag {
      padding: 4px 10px;
      background: var(--bg-soft);
      border-radius: 6px;
      font-size: 0.85rem;
      color: var(--text-main);
      transition: all 0.2s;
      
      &:hover {
        background: var(--color-primary);
        color: white;
      }
    }
  }
}

// 兴趣爱好网格
.hobbies-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;

  .hobby-item {
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 10px 14px;
    background: var(--bg-soft);
    border-radius: 12px;
    font-size: 0.9rem;
    color: var(--text-main);
    border: 1px solid transparent;
    transition: all 0.2s;

    .hobby-icon {
      color: var(--color-primary);
      flex-shrink: 0;
    }

    &:hover {
      border-color: var(--color-primary);
      background: var(--bg-card);
      transform: translateY(-2px);
      box-shadow: 0 4px 12px rgba(0, 0, 0, 0.05);

      .hobby-icon {
        transform: scale(1.1);
      }
    }
  }

  @include respond(sm) {
    grid-template-columns: 1fr;
  }
}

// 联系列表
.contact-list {
  list-style: none;
  padding: 0;
  margin: 0 0 24px 0;
  
  li {
    display: flex;
    align-items: center;
    gap: 12px;
    margin-bottom: 16px;
    color: var(--text-main);
    font-size: 0.95rem;
    
    :deep(svg) {
      color: var(--color-primary);
    }
    
    &:last-child {
      margin-bottom: 0;
    }
  }
}

.message-btn {
  width: 100%;
  padding: 12px;
  background: var(--color-primary);
  color: white;
  border: none;
  border-radius: 8px;
  font-weight: 600;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  transition: all 0.2s;
  
  &:hover {
    background: var(--color-primary-dark);
    transform: translateY(-2px);
    box-shadow: 0 4px 12px rgba(var(--color-primary-rgb), 0.3);
  }
}

// 响应式微调
@include respond(sm) {
  .hero-section {
    padding: 40px 20px 80px;
  }
  
  .hero-text .username {
    font-size: 2rem;
  }
  
  .info-card {
    padding: 24px;
  }
  
  .stats-row {
    gap: 20px;
    justify-content: space-between;
    
    .stat-item .stat-num {
      font-size: 1.5rem;
    }
  }
}
</style>
