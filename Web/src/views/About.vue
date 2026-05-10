<script setup lang="ts">
import Icon from '../components/Icon.vue'
import moonImg from '@/assets/image/moon.png'
import { ref } from 'vue'
import MessageModal from '@/components/MessageModal.vue'
import MessageList from '@/components/MessageList.vue'

const messageModalVisible = ref(false)
const messageListRef = ref()

const openMessageModal = () => {
  messageModalVisible.value = true
}

const handleMessageSuccess = () => {
  messageListRef.value?.refresh()
}

// 代表项目
const projects = [
  {
    icon: 'globe',
    title: '个人博客系统',
    tags: ['Spring Boot', 'Vue 3', 'TypeScript', 'MySQL'],
    desc: '这是我自己搭建的个人技术博客，用来记录技术学习、项目复盘、AI 实践和个人成长。它不只是一个展示页面，也是我整理知识、沉淀经历、继续完善个人 AI 助手系统的起点。',
    link: '#'
  },
  {
    icon: 'bot',
    title: '博客 AI 模块 / 看板娘',
    tags: ['AI 应用', 'Spring AI', 'Python Agent', 'MCP'],
    desc: '我一直想做一个能陪伴、能记录、能辅助成长的 AI 系统。早期尝试过 Spring AI，后续计划逐步迁移到 Python Agent，让博客里的 AI 更像一个真正能理解上下文的个人助手。',
    link: '#'
  },
  {
    icon: 'code',
    title: '全栈项目实践',
    tags: ['Java', 'Vue', 'Docker', 'Nginx'],
    desc: '从前端页面、后端接口、数据库设计，到部署上线和问题排查，我希望自己不只是会写某一段代码，而是能把一个真实业务从需求到运行完整地做出来。',
    link: '#'
  }
]

// 成长时间线
const timeline = [
  {
    date: '2020',
    title: '高中阶段开始学习前端',
    desc: '我在 2020 年上高中后开始接触前端开发，从 HTML、CSS、JavaScript 这些基础内容开始，第一次感受到自己可以用代码把页面和想法做出来。'
  },
  {
    date: '2023',
    title: '进入大学，开始系统学习后端',
    desc: '2023 年进入大学后，我开始把学习重点逐渐放到 Java 后端，接触 Spring Boot、MyBatis-Plus、MySQL、Redis 等技术，也开始理解一个系统背后的数据、接口和业务逻辑。'
  },
  {
    date: '2024',
    title: '从学习走向真实项目实践',
    desc: '在项目和实习过程中，我开始接触更真实的业务开发，包括前后端联调、数据看板、订单状态、支付退款、权限、部署等内容，也逐渐意识到工程能力不只是写代码。'
  },
  {
    date: '现在',
    title: '构建自己的博客和 AI 成长系统',
    desc: '现在我想把个人博客继续完善下去，让它成为技术记录、项目复盘、AI 实践和自我整理的长期空间。相比单纯展示技术栈，我更希望这里能留下我真实成长的轨迹。'
  }
]

// 博客定位
const blogPositions = [
  {
    icon: 'book',
    title: '技术学习记录',
    desc: '记录我从前端到后端、从页面到系统、从单点功能到完整项目的学习过程。这里不追求包装得很厉害，而是尽量留下真实的理解、踩坑和复盘。'
  },
  {
    icon: 'shield',
    title: '项目实践复盘',
    desc: '把自己做过的项目、遇到的问题、业务理解和技术取舍整理下来。对我来说，项目不是简历上的几个名词，而是一次次把想法落地的训练。'
  },
  {
    icon: 'bot',
    title: 'AI 应用探索',
    desc: '我对 AI 很感兴趣，尤其是能记录人生轨迹、辅助学习和提高开发效率的 AI 助手。这个博客也是我探索个人 AI 工作流和智能体系统的实验场。'
  }
]

// 博客统计
const stats = [
  { icon: 'fileText', label: '学习阶段', value: '4年+', bg: 'blue' },
  { icon: 'code', label: '实践项目', value: '多个', bg: 'blue' },
  { icon: 'tag', label: '技术方向', value: '全栈', bg: 'green' },
  { icon: 'trendingUp', label: '当前状态', value: '持续构建', bg: 'orange' }
]

// 联系方式
const contacts = [
  { icon: 'github', label: 'GitHub', value: 'github.com/Liuxin4950', link: 'https://github.com/Liuxin4950' },
  { icon: 'mail', label: '邮箱', value: 'liuxin4950@gmail.com', link: 'mailto:liuxin4950@gmail.com' },
  { icon: 'globe', label: '博客', value: 'liuxin.chat', link: 'https://liuxin.chat' },
  { icon: 'edit', label: '留言', value: '欢迎在博客留言交流' }
]
</script>

<template>
  <div class="about-page content">
    <!-- ==================== 顶部 Hero 区域（保持不动） ==================== -->
    <div class="hero-section">
      <div class="hero-content">
        <div class="avatar-wrapper">
          <div class="user-avatar" :style="{ backgroundImage: `url(${moonImg})` }">
            <img src="@/assets/image/gif/坐下.gif" alt="刘鑫" class="liuyin" />
          </div>
        </div>
        <div class="hero-text">
          <h1 class="username">
            <svg class="name-svg" viewBox="0 0 200 60">
              <defs>
                <linearGradient id="about-name-gradient" x1="0%" y1="0%" x2="100%" y2="0%">
                  <stop offset="0%" style="stop-color:var(--color-primary);stop-opacity:1" />
                  <stop offset="100%" style="stop-color:var(--color-accent);stop-opacity:1" />
                </linearGradient>
              </defs>
              <text x="50%" y="50%" dy=".35em" text-anchor="middle" class="name-base">
                刘鑫
              </text>
              <text x="50%" y="50%" dy=".35em" text-anchor="middle" class="name-stroke">
                刘鑫
              </text>
            </svg>
          </h1>
          <p class="user-bio">从前端开始，向全栈和 AI 应用继续探索</p>
          <p class="user-motto">「把学习、项目和生活的痕迹，慢慢整理成自己的系统」</p>
          <div class="social-links">
            <a href="https://github.com/Liuxin4950" class="social-item" title="GitHub" target="_blank" rel="noopener noreferrer">
              <Icon name="github" size="20" />
            </a>
            <a href="/" class="social-item" title="liuxin.chat">
              <Icon name="globe" size="20" />
            </a>
          </div>
        </div>
      </div>
    </div>

    <!-- ==================== 内容区（新布局） ==================== -->
    <div class="content-wrapper">
      <div class="main-container">
        <div class="content-grid">

          <!-- ========== 左侧主内容区 ========== -->
          <div class="main-column">

            <!-- 1. 关于我 -->
            <section class="info-card card-about">
              <h2 class="card-title">
                <span class="title-icon"><Icon name="person" size="20" /></span>
                关于我
              </h2>
              <div class="card-body">
                <p class="about-text">
                  你好，我是刘鑫，目前正在学习和实践全栈开发。这个网站是我自己搭建的个人博客，
                  也是我用来记录技术学习、项目复盘、AI 实践和个人成长的地方。
                </p>
                <p class="about-text">
                  我在 2020 年上高中后开始接触前端开发，从最基础的 HTML、CSS、JavaScript 开始，
                  慢慢学会用页面表达自己的想法。2023 年进入大学后，我开始系统学习 Java 后端，
                  接触 Spring Boot、MyBatis-Plus、MySQL、Redis 等技术，也开始理解一个系统背后的数据、
                  接口、权限和业务流程。
                </p>
                <p class="about-text">
                  相比单纯堆技术名词，我更在意能不能把一个真实业务从需求、数据库、接口、前端页面，
                  到部署运行完整地做出来。写代码对我来说不只是完成任务，也是把混乱的想法慢慢整理清楚的过程。
                </p>
                <p class="about-text">
                  我对 AI 应用也很感兴趣，尤其是能记录个人成长、辅助学习和提高开发效率的 AI 助手。
                  未来我希望把这个博客继续完善成一个属于自己的技术空间和成长系统，持续沉淀作品、经验和思考。
                </p>
              </div>
            </section>

            <!-- 2. 博客定位 -->
            <section class="info-card">
              <h2 class="card-title">
                <span class="title-icon"><Icon name="lightbulb" size="20" /></span>
                博客定位
              </h2>
              <div class="card-body">
                <div class="position-cards">
                  <div class="position-card" v-for="pos in blogPositions" :key="pos.title">
                    <span class="position-icon"><Icon :name="pos.icon" size="22" /></span>
                    <h3>{{ pos.title }}</h3>
                    <p>{{ pos.desc }}</p>
                  </div>
                </div>
              </div>
            </section>

            <!-- 3. 代表项目 -->
            <section class="info-card">
              <h2 class="card-title">
                <span class="title-icon"><Icon name="award" size="20" /></span>
                代表项目
              </h2>
              <div class="card-body">
                <div class="project-cards">
                  <div class="project-card" v-for="proj in projects" :key="proj.title">
                    <span class="project-icon"><Icon :name="proj.icon" size="22" /></span>
                    <h3>{{ proj.title }}</h3>
                    <div class="project-tags">
                      <span class="project-tag" v-for="tag in proj.tags" :key="tag">{{ tag }}</span>
                    </div>
                    <p>{{ proj.desc }}</p>
                    <a :href="proj.link" class="project-link">
                      查看详情 <Icon name="arrowRight" size="14" />
                    </a>
                  </div>
                </div>
              </div>
            </section>

            <!-- 4. 成长时间线 -->
            <section class="info-card">
              <h2 class="card-title">
                <span class="title-icon"><Icon name="clock" size="20" /></span>
                成长时间线
              </h2>
              <div class="card-body">
                <div class="timeline">
                  <div class="timeline-item" v-for="item in timeline" :key="item.date">
                    <div class="timeline-dot"></div>
                    <div class="timeline-date">{{ item.date }}</div>
                    <div class="timeline-content">
                      <h3>{{ item.title }}</h3>
                      <p>{{ item.desc }}</p>
                    </div>
                  </div>
                </div>
              </div>
            </section>

            <!-- 5. 公开留言 -->
            <section class="info-card">
              <h2 class="card-title">
                <span class="title-icon"><Icon name="chat" size="20" /></span>
                公开留言
              </h2>
              <div class="card-body">
                <MessageList ref="messageListRef" />
              </div>
            </section>

          </div>

          <!-- ========== 右侧信息栏 ========== -->
          <div class="sidebar-column">

            <!-- 1. 个人信息 -->
            <section class="info-card">
              <h2 class="card-title">
                <span class="title-icon"><Icon name="user" size="18" /></span>
                个人信息
              </h2>
              <div class="card-body">
                <div class="info-list">
                  <div class="info-row">
                    <span class="info-label">昵称</span>
                    <span class="info-value">刘鑫</span>
                  </div>
                  <div class="info-row">
                    <span class="info-label">身份</span>
                    <span class="info-value">全栈开发学习者</span>
                  </div>
                  <div class="info-row">
                    <span class="info-label">方向</span>
                    <span class="info-value">Java 后端 / Vue 前端 / AI 应用</span>
                  </div>
                  <div class="info-row">
                    <span class="info-label">所在地</span>
                    <span class="info-value">中国 · 重庆</span>
                  </div>
                  <div class="info-row">
                    <span class="info-label">博客</span>
                    <a href="https://liuxin.chat" class="info-link" target="_blank" rel="noopener noreferrer">liuxin.chat</a>
                  </div>
                  <div class="info-row">
                    <span class="info-label">GitHub</span>
                    <a href="https://github.com/Liuxin4950" class="info-link" target="_blank" rel="noopener noreferrer">Liuxin4950</a>
                  </div>
                </div>
              </div>
            </section>

            <!-- 2. 技术栈 -->
            <section class="info-card">
              <h2 class="card-title">
                <span class="title-icon"><Icon name="code" size="18" /></span>
                技术栈
              </h2>
              <div class="card-body">
                <div class="skill-group">
                  <h3 class="skill-label">前端开发</h3>
                  <div class="skill-tags">
                    <span class="skill-tag">HTML</span>
                    <span class="skill-tag">CSS</span>
                    <span class="skill-tag">JavaScript</span>
                    <span class="skill-tag">Vue 3</span>
                    <span class="skill-tag">TypeScript</span>
                    <span class="skill-tag">Vite</span>
                    <span class="skill-tag">Pinia</span>
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
                    <span class="skill-tag">Docker</span>
                    <span class="skill-tag">Nginx</span>
                    <span class="skill-tag">Linux</span>
                    <span class="skill-tag">Git</span>
                    <span class="skill-tag">Apifox</span>
                  </div>
                </div>
                <div class="skill-group">
                  <h3 class="skill-label">AI 实践</h3>
                  <div class="skill-tags">
                    <span class="skill-tag">AI 编程</span>
                    <span class="skill-tag">Prompt</span>
                    <span class="skill-tag">Python Agent</span>
                    <span class="skill-tag">MCP</span>
                    <span class="skill-tag">多智能体探索</span>
                  </div>
                </div>
              </div>
            </section>

            <!-- 3. 数据统计 -->
            <section class="info-card">
              <h2 class="card-title">
                <span class="title-icon"><Icon name="barChart" size="18" /></span>
                数据统计
              </h2>
              <div class="card-body">
                <div class="stats-grid">
                  <div class="stat-block" v-for="stat in stats" :key="stat.label" :class="'stat-' + stat.bg">
                    <span class="stat-icon"><Icon :name="stat.icon" size="18" /></span>
                    <span class="stat-num">{{ stat.value }}</span>
                    <span class="stat-label">{{ stat.label }}</span>
                  </div>
                </div>
              </div>
            </section>

            <!-- 4. 联系方式 -->
            <section class="info-card">
              <h2 class="card-title">
                <span class="title-icon"><Icon name="mail" size="18" /></span>
                联系方式
              </h2>
              <div class="card-body">
                <div class="contact-list">
                  <template v-for="contact in contacts" :key="contact.label">
                    <a v-if="contact.link"
                       :href="contact.link"
                       class="contact-item contact-item--link"
                       target="_blank"
                       rel="noopener noreferrer"
                    >
                      <span class="contact-icon"><Icon :name="contact.icon" size="16" /></span>
                      <span class="contact-label">{{ contact.label }}</span>
                      <span class="contact-value">{{ contact.value }}</span>
                    </a>
                    <span v-else class="contact-item">
                      <span class="contact-icon"><Icon :name="contact.icon" size="16" /></span>
                      <span class="contact-label">{{ contact.label }}</span>
                      <span class="contact-value">{{ contact.value }}</span>
                    </span>
                  </template>
                </div>
                <button class="message-btn" @click="openMessageModal">
                  <Icon name="edit" size="16" />
                  给我留言
                </button>
              </div>
            </section>

          </div>

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

// ===========================
// Hero 区域（保持不动）
// ===========================
.hero-section {
  width: 100%;
  position: absolute;
  left: 0;
  top: 50px;
  padding: 80px 20px 100px;
  border-bottom: 1px solid var(--border-light);
  z-index: 2;
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
    border: 4px solid var(--bg-main);
    background-color: var(--bg-hover);
    background-size: cover;
    background-position: center;
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
        fill: #ffffff;
        fill-opacity: 0.5;
        stroke: none;
        filter: drop-shadow(2px 2px 2px rgba(0, 0, 0, 0.3));
      }

      .name-stroke {
        fill: transparent;
        stroke: url(#about-name-gradient);
        stroke-width: 1.5px;
        stroke-linecap: round;
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
    color: rgba(255, 255, 255, 0.85);
    margin-bottom: 8px;
    font-weight: 500;
  }

  .user-motto {
    color: rgba(255, 255, 255, 0.65);
    font-size: 0.95rem;
    font-style: italic;
    margin-bottom: 24px;
  }

  :root.dark & {
    .name-base {
      fill: #1a1a1a;
      fill-opacity: 0.6;
    }
    .user-bio {
      color: var(--text-main);
    }
    .user-motto {
      color: var(--text-subtle);
    }
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

// ===========================
// 内容区（新布局）
// ===========================
.content-wrapper {
  padding: 0 20px 80px;
  position: relative;
  z-index: 1;
}

.main-container {
  max-width: 1200px;
  margin: 0 auto;
}

.content-grid {
  display: grid;
  grid-template-columns: 1fr 320px;
  gap: 24px;

  @include respond(md) {
    grid-template-columns: 1fr;
  }
}

// ===========================
// 卡片通用
// ===========================
.info-card {
  background: var(--bg-card);
  border-radius: 16px;
  padding: 28px;
  margin-bottom: 20px;
  border: 1px solid var(--border-base);
  box-shadow: var(--shadow-sm);
  transition: box-shadow 0.3s ease;

  &:hover {
    box-shadow: var(--shadow-md);
  }

  .card-title {
    font-size: 1.1rem;
    font-weight: 700;
    color: var(--text-title);
    margin-bottom: 20px;
    display: flex;
    align-items: center;
    gap: 10px;
    padding-bottom: 14px;
    border-bottom: 1px solid var(--border-base);

    .title-icon {
      color: var(--color-primary);
      display: flex;
      align-items: center;
    }
  }

  .card-body {
    color: var(--text-main);
  }
}

// 关于我卡片稍大
.card-about {
  .card-body {
    padding: 0;
  }
}

// ===========================
// 关于我 - 正文
// ===========================
.about-text {
  line-height: 1.85;
  color: var(--text-main);
  font-size: 0.95rem;
  margin-bottom: 14px;

  &:last-child {
    margin-bottom: 0;
  }
}

// ===========================
// 博客定位 - 三列小卡
// ===========================
.position-cards {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;

  @include respond(sm) {
    grid-template-columns: 1fr;
  }
}

.position-card {
  background: linear-gradient(135deg, var(--color-primary-light), var(--bg-card));
  border-radius: 12px;
  padding: 20px;
  border: 1px solid var(--border-base);
  transition: transform 0.25s ease, box-shadow 0.25s ease;

  &:hover {
    transform: translateY(-3px);
    box-shadow: var(--shadow-md);
  }

  .position-icon {
    color: var(--color-primary);
    display: block;
    margin-bottom: 12px;
  }

  h3 {
    font-size: 0.95rem;
    font-weight: 600;
    color: var(--color-primary);
    margin: 0 0 8px 0;
  }

  p {
    font-size: 0.82rem;
    color: var(--text-subtle);
    line-height: 1.6;
    margin: 0;
  }
}

// ===========================
// 代表项目 - 三列项目卡
// ===========================
.project-cards {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;

  @include respond(sm) {
    grid-template-columns: 1fr;
  }
}

.project-card {
  background: var(--bg-card);
  border: 1px solid var(--border-base);
  border-radius: 12px;
  padding: 20px;
  transition: transform 0.25s ease, box-shadow 0.25s ease;

  &:hover {
    transform: translateY(-3px);
    box-shadow: var(--shadow-md);
  }

  .project-icon {
    color: var(--color-primary);
    display: block;
    margin-bottom: 10px;
  }

  h3 {
    font-size: 0.95rem;
    font-weight: 700;
    color: var(--text-title);
    margin: 0 0 10px 0;
  }

  .project-tags {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
    margin-bottom: 10px;
  }

  .project-tag {
    font-size: 0.75rem;
    color: var(--text-subtle);
    background: var(--bg-hover);
    border-radius: 20px;
    padding: 3px 10px;
  }

  p {
    font-size: 0.82rem;
    color: var(--text-subtle);
    line-height: 1.6;
    margin: 0 0 12px 0;
  }

  .project-link {
    font-size: 0.85rem;
    color: var(--color-primary);
    text-decoration: none;
    font-weight: 600;
    display: inline-flex;
    align-items: center;
    gap: 4px;
    transition: gap 0.2s;

    &:hover {
      gap: 8px;
    }
  }
}

// ===========================
// 成长时间线
// ===========================
.timeline {
  position: relative;
  padding-left: 20px;

  &::before {
    content: '';
    position: absolute;
    left: 0;
    top: 4px;
    bottom: 0;
    width: 2px;
    background: linear-gradient(to bottom, var(--color-primary), rgba(var(--color-primary-rgb), 0.15));
    border-radius: 2px;
  }
}

.timeline-item {
  position: relative;
  padding-left: 24px;
  padding-bottom: 28px;

  &:last-child {
    padding-bottom: 0;
  }

  .timeline-dot {
    position: absolute;
    left: -26px;
    top: 5px;
    width: 12px;
    height: 12px;
    border-radius: 50%;
    background: var(--color-primary);
    border: 3px solid var(--bg-card);
    box-shadow: 0 0 0 4px rgba(var(--color-primary-rgb), 0.12);
    z-index: 1;
  }

  .timeline-date {
    font-size: 0.82rem;
    font-weight: 600;
    color: var(--color-primary);
    margin-bottom: 4px;
  }

  .timeline-content {
    h3 {
      font-size: 0.95rem;
      font-weight: 600;
      color: var(--text-title);
      margin: 0 0 6px 0;
    }

    p {
      font-size: 0.85rem;
      color: var(--text-subtle);
      line-height: 1.6;
      margin: 0;
    }
  }
}

// ===========================
// 个人信息 - 键值对
// ===========================
.info-list {
  .info-row {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 8px 0;
    border-bottom: 1px dashed var(--border-light);

    &:last-child {
      border-bottom: none;
    }
  }

  .info-label {
    font-size: 0.85rem;
    color: var(--text-subtle);
    flex-shrink: 0;
  }

  .info-value {
    font-size: 0.85rem;
    color: var(--text-main);
    text-align: right;
  }

  .info-link {
    font-size: 0.85rem;
    color: var(--color-primary);
    text-decoration: none;
    text-align: right;

    &:hover {
      text-decoration: underline;
    }
  }
}

// ===========================
// 技术栈标签
// ===========================
.skill-group {
  margin-bottom: 16px;

  &:last-child {
    margin-bottom: 0;
  }

  .skill-label {
    font-size: 0.8rem;
    font-weight: 600;
    color: var(--text-subtle);
    margin-bottom: 8px;
    display: block;
  }

  .skill-tags {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;

    .skill-tag {
      padding: 4px 10px;
      background: var(--bg-hover);
      border-radius: 6px;
      font-size: 0.8rem;
      color: var(--text-subtle);
      transition: all 0.2s;

      &:hover {
        background: var(--color-primary);
        color: white;
      }
    }
  }
}

// ===========================
// 数据统计 - 2x2 网格
// ===========================
.stats-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

.stat-block {
  border-radius: 12px;
  padding: 16px 14px;
  display: flex;
  flex-direction: column;
  gap: 4px;
  transition: transform 0.2s;

  &:hover {
    transform: translateY(-2px);
  }

  .stat-icon {
    opacity: 0.6;
    margin-bottom: 2px;
  }

  .stat-num {
    font-size: 1.35rem;
    font-weight: 800;
    color: var(--text-main);
    line-height: 1;
  }

  .stat-label {
    font-size: 0.75rem;
    color: var(--text-subtle);
  }

  &.stat-blue {
    background: var(--bg-info);
    .stat-icon { color: #4b8ef1; }
  }

  &.stat-green {
    background: var(--bg-success);
    .stat-icon { color: #4caf7d; }
  }

  &.stat-orange {
    background: var(--bg-warning);
    .stat-icon { color: #f0a04b; }
  }
}

// ===========================
// 联系方式
// ===========================
.contact-list {
  display: flex;
  flex-direction: column;
  gap: 2px;
  margin-bottom: 20px;
}

.contact-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 8px;
  border-radius: 8px;
  text-decoration: none;
  color: inherit;
  transition: background 0.2s;

  &--link {
    cursor: pointer;

    &:hover {
      background: var(--bg-hover);
    }
  }

  .contact-icon {
    color: var(--color-primary);
    display: flex;
    align-items: center;
    flex-shrink: 0;
    width: 20px;
    justify-content: center;
  }

  .contact-label {
    font-size: 0.85rem;
    color: var(--text-subtle);
    flex-shrink: 0;
    width: 50px;
  }

  .contact-value {
    font-size: 0.82rem;
    color: var(--color-primary);
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
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
  font-size: 0.9rem;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  transition: all 0.2s;

  &:hover {
    background: var(--color-primary-dark);
    transform: translateY(-2px);
    box-shadow: 0 4px 14px rgba(var(--color-primary-rgb), 0.3);
  }
}

// ===========================
// 响应式
// ===========================
@include respond(md) {
  .content-wrapper {
    padding: 32px 16px 60px;
  }

  .position-cards,
  .project-cards {
    grid-template-columns: 1fr 1fr;
  }
}

@include respond(sm) {
  .hero-section {
    padding: 40px 20px 80px;
  }

  .hero-text .username {
    font-size: 2rem;
  }

  .content-wrapper {
    padding: 24px 12px 48px;
  }

  .info-card {
    padding: 20px;
    border-radius: 14px;
  }

  .position-cards,
  .project-cards {
    grid-template-columns: 1fr;
  }

  .stats-grid {
    grid-template-columns: 1fr 1fr;
    gap: 8px;
  }

  .stat-block {
    padding: 14px 12px;

    .stat-num {
      font-size: 1.15rem;
    }
  }
}
</style>
