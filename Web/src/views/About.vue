<script setup lang="ts">
import { ref } from "vue"
import Icon from "../components/Icon.vue"
import { handleImageError } from "@/composables/useImageFallback"
import MessageModal from "@/components/MessageModal.vue"
import moonImg from "@/assets/image/moon.png"
import aboutHonorsImg from "@/assets/image/about/about-honors-collage.png"

const messageModalVisible = ref(false)

const links = [
  { icon: "github", label: "GitHub", value: "Liuxin4950", href: "https://github.com/Liuxin4950" },
  { icon: "mail", label: "邮箱", value: "liuxin4950@gmail.com", href: "mailto:liuxin4950@gmail.com" }
]

// 技术栈：前端 / 后端 / 工程化 / AI 四类
const skillGroups = [
  {
    category: "前端开发",
    icon: "layout",
    accent: "frontend",
    skills: [
      { name: "Vue 3", icon: "vue" },
      { name: "TypeScript", icon: "typescript" },
      { name: "Vite", icon: "zap" },
      { name: "SCSS", icon: "layers" },
      { name: "Ant Design", icon: "grid" },
      { name: "uni-app", icon: "smartphone" },
    ]
  },
  {
    category: "后端开发",
    icon: "server",
    accent: "backend",
    skills: [
      { name: "Spring Boot", icon: "spring" },
      { name: "Java", icon: "coffee" },
      { name: "MyBatis-Plus", icon: "database" },
      { name: "MySQL", icon: "database" },
      { name: "Redis", icon: "database" },
      { name: "Spring Security", icon: "shield" },
    ]
  },
  {
    category: "工程化",
    icon: "wrench",
    accent: "devops",
    skills: [
      { name: "Docker", icon: "docker" },
      { name: "Compose", icon: "package" },
      { name: "Nginx", icon: "server" },
      { name: "Linux", icon: "terminal" },
      { name: "Actions", icon: "gitBranch" },
      { name: "CI/CD", icon: "refresh" },
    ]
  },
  {
    category: "AI 探索",
    icon: "bot",
    accent: "ai",
    skills: [
      { name: "Spring AI", icon: "spring" },
      { name: "大模型 API", icon: "brain" },
      { name: "Prompt 工程", icon: "lightbulb" },
      { name: "MCP", icon: "cpu" },
      { name: "SSE 流式", icon: "zap" },
      { name: "Live2D", icon: "live2d" },
    ]
  },
]

// 技术理念
const philosophy = [
  { icon: "check", title: "代码不是目的", desc: "优秀的开发者不是写最多代码的人，而是能用合适的技术真正解决问题的人。" },
  { icon: "book", title: "保持学习", desc: "技术更新很快，用阅读源码、动手实践、记录博客的方式输出倒逼输入。" },
  { icon: "brain", title: "AI 是第二大脑", desc: "让 AI 成为开发者的能力延伸，融入开发流程，而不只是简单的聊天工具。" },
]

// 项目经历：文案聚焦“为什么做 + 学到什么”
const projects = [
  {
    name: "LiuTech 博客",
    description: "全栈个人博客平台。从第一版写死配置、本地推理的混乱方案，到如今 Spring Boot 微服务 + Vue 3 + Docker Compose 的工程化架构。一次 Docker 误操作导致数据丢失后，深入学习了数据持久化与备份--也让我明白，真正的工程是保证系统可靠运行。",
    tags: ["Vue 3", "Spring Boot", "MySQL", "Docker"],
    icon: "home",
    link: "/"
  },
  {
    name: "LiuTech-AI",
    description: "独立 AI 微服务，基于 Spring AI 接入 DeepSeek-V3.2，支持多轮对话、SSE 流式响应、TTS 语音合成与 Live2D 口型同步。看板娘从“不会说话”到“开口交流”的完整实践。",
    tags: ["Spring AI", "DeepSeek-V3.2", "SSE", "TTS"],
    icon: "bot",
    link: null
  },
]

const openMessageModal = () => {
  messageModalVisible.value = true
}
</script>

<template>
  <div class="about-page content">
    <div class="hero-section">
      <div class="hero-content">
        <div class="avatar-wrapper">
          <div class="user-avatar" :style="{ backgroundImage: `url(${moonImg})` }">
            <img src="@/assets/image/gif/坐下.gif" alt="刘鑫" class="liuyin" @error="handleImageError" />
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
          <p class="user-bio">全栈工程师 & 技术博主</p>
          <p class="user-motto">「代码改变世界，热爱成就未来」</p>
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

    <main class="about-main">
      <section class="about-intro">
        <h2>关于我</h2>
        <p class="intro-lead">
          我叫刘鑫，软件工程专业的学生，正在成为一名全栈开发工程师。用代码记录时间与成长，用技术创造快乐与价值。
        </p>
        <p>
          最初接触编程只是出于好奇，把它和传说中黑客的网络技术搞混了，但是在学习的过程中却渐渐发现：相比刷短视频、我更喜欢用学习到的知识来实现一些有价值的东西。
          这些年，我从一个只能写出静态页面的初学者，成长为能独立打通前后端开发、数据库设计、容器化部署与自动化交付的开发者--这个博客就是我为整合所学、
          并亲手实现一个能和读者交流的 Live2D 看板娘而搭建的。
        </p>
        <p>
          如今我把重心放在 AI 与软件工程的结合上：用大模型让看板娘开口交流，用 AI 辅助开发提效。我相信，真正的软件工程不只是写代码，更是保证系统可靠地运行、持续地创造价值。
        </p>
      </section>

      <!-- 技术栈 -->
      <section class="tech-stack-section">
        <h2>技术栈</h2>
        <div class="skill-groups">
          <div
            v-for="group in skillGroups"
            :key="group.category"
            class="skill-group"
            :class="`skill-group--${group.accent}`"
          >
            <div class="skill-group-header">
              <Icon :name="group.icon" size="16" />
              <span>{{ group.category }}</span>
            </div>
            <div class="skill-tags">
              <span v-for="skill in group.skills" :key="skill.name" class="skill-tag">
                <Icon :name="skill.icon" size="13" />
                {{ skill.name }}
              </span>
            </div>
          </div>
        </div>
      </section>

      <!-- 项目经历 -->
      <section class="projects-section">
        <h2>项目经历</h2>
        <div class="project-grid">
          <article v-for="project in projects" :key="project.name" class="project-card">
            <div class="project-icon">
              <Icon :name="project.icon" size="22" />
            </div>
            <div class="project-info">
              <h3>
                {{ project.name }}
                <a v-if="project.link" :href="project.link" class="project-link" title="访问项目">
                  <Icon name="external" size="14" />
                </a>
              </h3>
              <p>{{ project.description }}</p>
              <div class="project-tags">
                <span v-for="tag in project.tags" :key="tag" class="project-tag">{{ tag }}</span>
              </div>
            </div>
          </article>
        </div>
      </section>  

      <section class="honors-spotlight">
        <div class="honors-art">
          <img :src="aboutHonorsImg" alt="证书与奖杯插画" loading="lazy" @error="handleImageError">
        </div>
        <div class="honors-copy">
          <h2>荣誉与证书</h2>
          <p>全国职业院校技能大赛团体二等奖、重庆市选拔赛第一名、Web 应用开发一等奖、金砖国家技能大赛三等奖……持续积累中。</p>
          <router-link to="/honors" class="text-link">
            查看全部荣誉 <Icon name="chevronRight" size="15" />
          </router-link>
        </div>
      </section>

      <!-- 技术理念 -->
      <section class="philosophy-section">
        <h2>技术理念</h2>
        <div class="philosophy-grid">
          <div v-for="item in philosophy" :key="item.title" class="philosophy-card">
            <div class="philosophy-icon">
              <Icon :name="item.icon" size="18" />
            </div>
            <div class="philosophy-body">
              <h3>{{ item.title }}</h3>
              <p>{{ item.desc }}</p>
            </div>
          </div>
        </div>
      </section>

      <section class="contact-section">
        <div>
          <h2>联系我</h2>
          <p>有文章内容、项目问题或技术交流，欢迎留言。</p>
        </div>
        <div class="contact-actions">
          <button class="message-action" type="button" @click="openMessageModal">
            <Icon name="edit" size="18" />
            写留言
          </button>
          <a
            v-for="link in links"
            :key="link.label"
            :href="link.href"
            class="contact-link"
            target="_blank"
            rel="noopener noreferrer"
          >
            <Icon :name="link.icon" size="17" />
            <span>{{ link.value }}</span>
          </a>
        </div>
      </section>
    </main>

    <MessageModal v-model:visible="messageModalVisible" />
  </div>
</template>

<style scoped lang="scss">
@use "@/assets/styles/tokens" as *;

.about-page {
  max-width: 1180px;
  margin: 0 auto;
  padding: 20px 20px 56px;
}

.message-action,
.contact-link,
.text-link {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  text-decoration: none;
  font-weight: 800;
  transition: transform 0.2s ease, border-color 0.2s ease, background 0.2s ease, color 0.2s ease;
}

.message-action {
  min-height: 44px;
  border-radius: 8px;
  padding: 0 18px;
}

// 顶部 Hero 保留博客原有识别点：全局 Banner 上的名字、月亮头像和社交入口。
.hero-section {
  width: 100%;
  position: absolute;
  left: 0;
  top: 50px;
  padding: 80px 20px 100px;
  border-bottom: 1px solid var(--border-light);
  z-index: 2;
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

    .liuyin {
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
        fill-opacity: 0.9;
        stroke: none;
        filter: drop-shadow(2px 2px 4px rgba(0, 0, 0, 0.4));
      }

      .name-stroke {
        fill: transparent;
        stroke: url(#about-name-gradient);
        stroke-width: 1.8px;
        stroke-linecap: round;
        stroke-dasharray: 80 320;
        stroke-dashoffset: 400;
        animation: stroke-flow 6s linear infinite;
        opacity: 0.9;
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
      fill-opacity: 0.8;
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

.about-main {
  display: grid;
  gap: 22px;
  margin-top: 24px;
}

.about-intro,
.honors-spotlight,
.contact-section {
  border: 1px solid var(--border-base);
  border-radius: 8px;
  background: var(--bg-card);
  box-shadow: var(--shadow-sm);
}

.about-intro {
  padding: 34px;

  h2 {
    margin: 0 0 16px;
    color: var(--text-title);
    font-size: clamp(1.45rem, 3vw, 1.9rem);
    line-height: 1.25;
  }

  .intro-lead {
    margin: 0 0 18px;
    padding-left: 14px;
    border-left: 3px solid var(--color-primary);
    font-size: 1.08rem;
    font-weight: 600;
    color: var(--text-title);
    line-height: 1.7;
  }

  p {
    margin: 0 0 12px;
    color: var(--text-secondary);
    line-height: 1.9;

    &:last-child {
      margin-bottom: 0;
    }
  }
}

.honors-spotlight {
  display: grid;
  grid-template-columns: minmax(0, 1.1fr) minmax(300px, 0.9fr);
  overflow: hidden;
}

/* ── 通用区块容器：技术栈 / 项目 / 理念 ── */
.tech-stack-section,
.projects-section,
.philosophy-section {
  border: 1px solid var(--border-base);
  border-radius: 8px;
  background: var(--bg-card);
  box-shadow: var(--shadow-sm);
  padding: 34px;

  h2 {
    margin: 0 0 20px;
    color: var(--text-title);
    font-size: clamp(1.45rem, 3vw, 1.9rem);
    line-height: 1.25;
  }
}

/* ── 技术栈 ── */
.skill-groups {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
}

.skill-group {
  border: 1px solid var(--border-light);
  border-radius: 8px;
  padding: 18px;
  transition: border-color 0.2s ease;

  &:hover {
    border-color: var(--color-primary);
  }
}

.skill-group-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 14px;
  font-weight: 600;
  font-size: 0.95rem;
  color: var(--text-title);

  .skill-group--frontend & { color: #4f8c81; }
  .skill-group--backend & { color: #355071; }
  .skill-group--devops & { color: #d77a55; }
  .skill-group--ai & { color: #9b59b6; }
}

.skill-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.skill-tag {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 5px 12px;
  border-radius: 20px;
  font-size: 0.82rem;
  font-weight: 500;
  color: var(--text-secondary);
  background: var(--bg-soft);
  border: 1px solid var(--border-light);
  transition: color 0.2s ease, border-color 0.2s ease;

  &:hover {
    color: var(--text-title);
    border-color: var(--color-primary);
  }
}

/* ── 项目经历 ── */
.project-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 18px;
}

.project-card {
  display: flex;
  gap: 16px;
  padding: 20px;
  border: 1px solid var(--border-light);
  border-radius: 8px;
  transition: border-color 0.2s ease;

  &:hover {
    border-color: var(--color-primary);
  }
}

.project-icon {
  flex-shrink: 0;
  width: 44px;
  height: 44px;
  border-radius: 10px;
  background: var(--bg-soft);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--color-primary);
}

.project-info {
  flex: 1;
  min-width: 0;

  h3 {
    margin: 0 0 6px;
    font-size: 1rem;
    font-weight: 600;
    color: var(--text-title);
    display: flex;
    align-items: center;
    gap: 6px;
  }

  p {
    margin: 0 0 10px;
    font-size: 0.88rem;
    color: var(--text-secondary);
    line-height: 1.65;
  }
}

.project-link {
  color: var(--text-muted);
  transition: color 0.2s ease;

  &:hover {
    color: var(--color-primary);
  }
}

.project-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.project-tag {
  padding: 2px 9px;
  border-radius: 4px;
  font-size: 0.75rem;
  font-weight: 500;
  color: var(--text-muted);
  background: var(--bg-soft);
  border: 1px solid var(--border-light);
}

/* ── 技术理念：左对齐横向条目，克制动效 ── */
.philosophy-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 18px;
}

.philosophy-card {
  display: flex;
  gap: 14px;
  align-items: flex-start;
  padding: 20px;
  border: 1px solid var(--border-light);
  border-radius: 8px;
  transition: border-color 0.2s ease;

  &:hover {
    border-color: var(--color-primary);
  }

  h3 {
    margin: 0 0 6px;
    font-size: 0.98rem;
    font-weight: 600;
    color: var(--text-title);
  }

  p {
    margin: 0;
    font-size: 0.85rem;
    color: var(--text-secondary);
    line-height: 1.7;
  }
}

.philosophy-icon {
  flex-shrink: 0;
  width: 38px;
  height: 38px;
  border-radius: 8px;
  background: var(--bg-soft);
  color: var(--color-primary);
  display: flex;
  align-items: center;
  justify-content: center;
}

.philosophy-body {
  flex: 1;
  min-width: 0;
}

.honors-art {
  min-height: 320px;
  background: #eef3ed;

  img {
    display: block;
    width: 100%;
    height: 100%;
    object-fit: cover;
  }
}

.honors-copy {
  padding: 38px;
  display: flex;
  flex-direction: column;
  justify-content: center;

  h2 {
    margin: 10px 0 14px;
    color: var(--text-title);
    font-size: clamp(1.45rem, 3vw, 2.1rem);
    line-height: 1.3;
  }

  p {
    margin: 0;
    color: var(--text-secondary);
    line-height: 1.85;
  }
}

.text-link {
  width: fit-content;
  margin-top: 22px;
  color: #4f8c81;

  &:hover {
    color: #d77a55;
    transform: translateX(3px);
  }
}

.contact-section {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: center;
  gap: 28px;
  padding: 30px 34px;

  h2 {
    margin: 8px 0 8px;
    color: var(--text-title);
    font-size: 1.45rem;
  }

  p {
    margin: 0;
    color: var(--text-secondary);
    line-height: 1.75;
  }
}

.contact-actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 10px;
}

.message-action {
  border: 0;
  background: #355071;
  color: #fff;
  cursor: pointer;

  &:hover {
    transform: translateY(-2px);
    background: #26344a;
  }
}

.contact-link {
  min-height: 40px;
  border-radius: 8px;
  border: 1px solid var(--border-base);
  padding: 0 14px;
  color: var(--text-secondary);
  background: var(--bg-hover);

  &:hover {
    color: #4f8c81;
    border-color: #4f8c81;
  }
}

@include respond(md) {
  .about-page {
    padding: 20px 16px 44px;
  }

  .hero-section {
    top: 52px;
    padding: 18px 14px 0;
    border-bottom: 0;
  }

  .avatar-wrapper {
    margin-bottom: 8px;

    .user-avatar {
      width: 86px;
      height: 86px;

      .liuyin {
        top: 15px;
        left: -8px;
        max-width: 78px;
      }
    }
  }

  .hero-text {
    .username {
      height: 48px;
      margin-bottom: 2px;

      .name-svg {
        max-width: 168px;

        text {
          font-size: 38px;
        }
      }
    }

    .user-bio {
      font-size: 0.9rem;
      margin-bottom: 3px;
    }

    .user-motto {
      font-size: 0.76rem;
      margin-bottom: 8px;
      padding: 0 10px;
    }
  }

  .social-links {
    gap: 10px;

    .social-item {
      width: 34px;
      height: 34px;
    }
  }

  .skill-groups {
    grid-template-columns: repeat(2, 1fr);
  }

  .project-grid {
    grid-template-columns: 1fr;
  }

  .honors-spotlight,
  .contact-section {
    grid-template-columns: 1fr;
  }

  .contact-actions {
    justify-content: flex-start;
  }
}

@include respond(sm) {
  .about-page {
    padding: 20px 12px 34px;
  }

  .hero-section {
    top: 50px;
    padding-top: 14px;
  }

  .avatar-wrapper {
    margin-bottom: 6px;

    .user-avatar {
      width: 74px;
      height: 74px;

      .liuyin {
        top: 12px;
        left: -7px;
        max-width: 68px;
      }
    }
  }

  .hero-text {
    .username {
      height: 44px;
      margin-bottom: 0;

      .name-svg {
        max-width: 150px;

        text {
          font-size: 36px;
        }
      }
    }

    .user-bio {
      font-size: 0.84rem;
      margin-bottom: 2px;
    }

    .user-motto {
      font-size: 0.72rem;
      margin-bottom: 8px;
      padding: 0 10px;
    }
  }

  .social-links {
    .social-item {
      width: 32px;
      height: 32px;
    }
  }

  .about-intro,
  .honors-copy,
  .contact-section,
  .tech-stack-section,
  .projects-section,
  .philosophy-section {
    padding: 24px;
  }

  .skill-groups {
    grid-template-columns: 1fr;
  }

  .philosophy-grid {
    grid-template-columns: 1fr;
  }

  .honors-art {
    min-height: 210px;
  }

  .contact-link {
    width: 100%;
  }

  .message-action {
    width: 100%;
  }
}
</style>