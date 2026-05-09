<script setup lang="ts">
import Icon from '../components/Icon.vue'
import { ref } from 'vue'
import MessageModal from '@/components/MessageModal.vue'
import MessageList from '@/components/MessageList.vue'

// 留言弹窗显示状态
const messageModalVisible = ref(false)
const messageListRef = ref()

// 打开留言弹窗
const openMessageModal = () => {
  messageModalVisible.value = true
}

// 留言成功后刷新列表
const handleMessageSuccess = () => {
  messageListRef.value?.refresh()
}
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
          <p class="user-bio">全栈开发学习者 / AI 应用实践者</p>
          <p class="user-motto">「慢慢把想法做成东西，也慢慢把自己整理清楚」</p>
          <div class="social-links">
            <a href="https://github.com/liuxin77777" class="social-item" title="GitHub" target="_blank" rel="noopener noreferrer">
              <Icon name="github" size="20" />
            </a>
            <a href="/" class="social-item" title="liuxin.chat">
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
                你好，我是刘鑫，目前正在学习和实践全栈开发。这个网站是我自己搭建的个人博客，
                也是我用来记录技术学习、项目复盘、AI 实践和个人成长的地方。
              </p>
              <p class="about-text">
                我的主要方向是 Java 后端和 Vue 前端，平时会接触 Spring Boot、MyBatis-Plus、
                MySQL、Redis、Vue 3、TypeScript、Docker、Nginx 等技术。相比于单纯堆技术名词，
                我更在意能不能把一个真实业务从需求、数据库、接口、前端页面到部署运行完整做出来。
              </p>
              <p class="about-text">
                最近我也在尝试把 AI 融入自己的开发流程，比如先整理 PRD，再让 AI 辅助分析项目结构、
                生成代码、补充测试和排查问题。这个博客后续也会继续升级 AI 模块，尝试做成一个更像
                “个人技术助手”的系统，而不是只会回答问题的聊天窗口。
              </p>
              <p class="about-text">
                我不算什么大佬，只是在一点点补齐自己的工程能力。这个博客记录的也不是完美答案，
                而是我从不会、混乱、踩坑，到逐渐理解系统设计和工程实践的过程。
              </p>
              
              <div class="stats-row">
                <div class="stat-item">
                  <span class="stat-num">2023</span>
                  <span class="stat-label">开始深入 Web 开发</span>
                </div>
                <div class="stat-item">
                  <span class="stat-num">AI</span>
                  <span class="stat-label">辅助开发实践中</span>
                </div>
                <div class="stat-item">
                  <span class="stat-num">Blog</span>
                  <span class="stat-label">持续建设中</span>
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
                  <div class="timeline-year">2026</div>
                  <div class="timeline-content">
                    <h3>实习、求职与项目复盘</h3>
                    <p>
                      进入真实项目环境后，开始更重视业务理解、数据库设计、接口规范、测试验证和部署流程。
                      也逐渐意识到，写代码只是开发的一部分，真正困难的是把混乱需求变成可落地的系统。
                    </p>
                  </div>
                </div>
                <div class="timeline-item">
                  <div class="timeline-year">2025</div>
                  <div class="timeline-content">
                    <h3>全栈能力建设</h3>
                    <p>
                      持续学习 Spring Boot、Vue、MySQL、Redis、Docker 等技术，并尝试独立完成博客系统、
                      后台管理、接口设计和前后端联调，把零散知识逐渐串成完整项目经验。
                    </p>
                  </div>
                </div>
                <div class="timeline-item">
                  <div class="timeline-year">2024</div>
                  <div class="timeline-content">
                    <h3>前后端分离实践</h3>
                    <p>
                      开始更系统地理解前后端分离项目，接触 Vue 3、TypeScript、组件化开发和接口对接，
                      也逐渐形成了从页面、接口到数据表一起思考的习惯。
                    </p>
                  </div>
                </div>
                <div class="timeline-item">
                  <div class="timeline-year">2023</div>
                  <div class="timeline-content">
                    <h3>比赛与方向确认</h3>
                    <p>
                      通过 Web 开发相关比赛积累了更扎实的实践经验，也逐渐确认自己想继续往全栈开发方向走。
                      这段经历让我真正开始相信，技术不是背概念，而是把东西做出来。
                    </p>
                  </div>
                </div>
              </div>
            </div>
          </section>
          
          <!-- 留言列表 -->
          <section class="info-card">
            <h2 class="card-title">
              <Icon name="chat" size="20" class="title-icon" />
              公开留言
            </h2>
            <div class="card-body">
              <MessageList ref="messageListRef" />
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
                  <span class="skill-tag">Pinia</span>
                  <span class="skill-tag">Vue Router</span>
                  <span class="skill-tag">Element Plus</span>
                </div>
              </div>
              <div class="skill-group">
                <h3 class="skill-label">后端开发</h3>
                <div class="skill-tags">
                  <span class="skill-tag">Java</span>
                  <span class="skill-tag">Spring Boot</span>
                  <span class="skill-tag">MyBatis-Plus</span>
                  <span class="skill-tag">Spring Security</span>
                  <span class="skill-tag">JWT</span>
                  <span class="skill-tag">MySQL</span>
                  <span class="skill-tag">Redis</span>
                </div>
              </div>
              <div class="skill-group">
                <h3 class="skill-label">工程与部署</h3>
                <div class="skill-tags">
                  <span class="skill-tag">Git</span>
                  <span class="skill-tag">Docker</span>
                  <span class="skill-tag">Docker Compose</span>
                  <span class="skill-tag">Nginx</span>
                  <span class="skill-tag">Linux</span>
                  <span class="skill-tag">Apifox</span>
                </div>
              </div>
              <div class="skill-group">
                <h3 class="skill-label">AI 实践</h3>
                <div class="skill-tags">
                  <span class="skill-tag">AI 辅助开发</span>
                  <span class="skill-tag">PRD 驱动开发</span>
                  <span class="skill-tag">Agent 工作流</span>
                  <span class="skill-tag">MCP</span>
                  <span class="skill-tag">代码审查</span>
                  <span class="skill-tag">测试辅助</span>
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
                  <Icon name="book" size="24" class="hobby-icon" />
                  <span>小说</span>
                </div>
                <div class="hobby-item">
                  <Icon name="game" size="24" class="hobby-icon" />
                  <span>游戏</span>
                </div>
                <div class="hobby-item">
                  <Icon name="music" size="24" class="hobby-icon" />
                  <span>音乐</span>
                </div>
                <div class="hobby-item">
                  <Icon name="code" size="24" class="hobby-icon" />
                  <span>折腾项目</span>
                </div>
                <div class="hobby-item">
                  <Icon name="chat" size="24" class="hobby-icon" />
                  <span>AI 工具</span>
                </div>
                <div class="hobby-item">
                  <Icon name="film" size="24" class="hobby-icon" />
                  <span>二次元</span>
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
                  <span>邮箱：暂未公开</span>
                </li>
                <li>
                  <Icon name="github" size="16" />
                  <span>GitHub：待补充</span>
                </li>
                <li>
                  <Icon name="location" size="16" />
                  <span>中国 · 重庆</span>
                </li>
              </ul>
              <button class="message-btn" @click="openMessageModal">
                <Icon name="edit" size="16" />
                给我留言
              </button>
            </div>
          </section>

          
        </div>
      </div>
    </div>

    <!-- 留言弹窗组件 -->
    <MessageModal
      v-model:visible="messageModalVisible"
      @success="handleMessageSuccess"
    />
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
      position: absolute;
      top: 20px;
      left: -10px;
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
        fill: var(--text-main);
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
    color: var(--text-subtle);
    margin-bottom: 8px;
    font-weight: 500;
    filter: drop-shadow(2px 2px 2px rgba(255, 255, 255, .5));
  }
  
  .user-motto {
    filter: drop-shadow(2px 2px 2px rgba(255, 255, 255, .5));
    color: var(--text-subtle);
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
