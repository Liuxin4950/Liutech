<script setup lang="ts">
import { ref } from 'vue'
import Icon from '../components/Icon.vue'
import { handleImageError } from '@/composables/useImageFallback'
import MessageModal from '@/components/MessageModal.vue'
import moonImg from '@/assets/image/moon.png'
import aboutHonorsImg from '@/assets/image/about/about-honors-collage.png'

const messageModalVisible = ref(false)

const links = [
  { icon: 'github', label: 'GitHub', value: 'Liuxin4950', href: 'https://github.com/Liuxin4950' },
  { icon: 'mail', label: '邮箱', value: 'liuxin4950@gmail.com', href: 'mailto:liuxin4950@gmail.com' }
]

// 技术栈数据
const skillGroups = [
  {
    category: '前端开发',
    icon: 'layout',
    accent: 'frontend',
    skills: [
      { name: 'Vue 3', icon: 'vue' },
      { name: 'TypeScript', icon: 'typescript' },
      { name: 'Vite', icon: 'zap' },
      { name: 'SCSS', icon: 'layers' },
      { name: 'Ant Design', icon: 'grid' },
      { name: 'TailwindCSS', icon: 'wind' },
    ]
  },
  {
    category: '后端与运维',
    icon: 'server',
    accent: 'backend',
    skills: [
      { name: 'Spring Boot', icon: 'spring' },
      { name: 'Java', icon: 'coffee' },
      { name: 'MyBatis', icon: 'database' },
      { name: 'MySQL', icon: 'database' },
      { name: 'Docker', icon: 'docker' },
      { name: 'Nginx', icon: 'server' },
    ]
  },
  {
    category: 'AI & 工具',
    icon: 'bot',
    accent: 'ai',
    skills: [
      { name: 'Spring AI', icon: 'spring' },
      { name: '大模型 API', icon: 'brain' },
      { name: 'Prompt Engineering', icon: 'lightbulb' },
      { name: 'SSE 流式', icon: 'zap' },
      { name: 'Git', icon: 'gitBranch' },
      { name: 'Live2D', icon: 'live2d' },
    ]
  },
]

// 项目经历数据
const projects = [
  {
    name: 'LiuTech 博客',
    description: '全栈个人博客平台，前后端分离架构，支持文章管理、评论互动、AI 聊天助手、Live2D 看板娘等功能。',
    tags: ['Vue 3', 'Spring Boot', 'MySQL', 'Docker'],
    icon: 'home',
    link: '/'
  },
  {
    name: 'LiuTech-AI',
    description: '独立 AI 微服务，基于 Spring AI 接入 DeepSeek-V3.2 大模型，支持多轮对话、工具调用、SSE 流式响应和 TTS 语音合成。',
    tags: ['Spring AI', 'DeepSeek-V3.2', 'SSE', 'TTS'],
    icon: 'bot',
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
        <p>
          我的名字叫刘鑫。正在成为一名全栈工程师，我在高一的时候接触了web前端开发，开始进行各种技能大赛，在比赛过程中自学大部分前端的知识，在上大学后发现单纯的前端无法满足我的需求，
          我完整实现一个动态页面所以我开始学习后端开发，目前还在学校学习呢，这个博客是我在大二下学期写的，目的是实践整合我学到的前端和后端知识，并实现右下角的那个看板娘。
        </p>
        <p>
          我喜欢折腾新技术，也享受把一个东西创造出来的感觉，我的第一个博客是我上课写的，当时在学习java后端，边学边做这个live2d看板娘和博客基本功能，一直在搞ollama本地的GPT-SoVITS的本地推理，想着能不能实现一个可以和读者交流的看板娘，
          因为我发现当时的看板娘是live2d他们都不会主动说话，也不会博客内容和读者互动，又因为当时比较资金有限，所以我以本地推理为主，没有使用ollama的云推理。实现的博客也是乱七八糟的， 功能配置什么的
          都是写死在代码文件里的，本地推理在服务器上又不靠谱，博客的文本编辑器就是单纯的markdown编辑器，没有其他的功能。也没有用富文本，页面的布局和配色和主题完全没有统一， 乱七八糟的我自己看着都头疼。
          为了复活我的看板娘，所以我重新写了这个博客。使用了工程化的架构，并积极的使用vebicoding来优化代码提供思路，各种折腾设计，最后才实现了本博客。
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
    margin: 0 0 12px;
    color: var(--text-title);
    font-size: clamp(1.45rem, 3vw, 1.9rem);
    line-height: 1.25;
  }

  p {
    max-width: 760px;
    margin: 0 0 10px;
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

/* ── 技术栈 ── */
.tech-stack-section,
.projects-section {
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

.skill-groups {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 18px;
}

.skill-group {
  border: 1px solid var(--border-light);
  border-radius: 8px;
  padding: 18px;
  transition: border-color 0.2s ease, box-shadow 0.2s ease;

  &:hover {
    border-color: var(--color-primary);
    box-shadow: 0 2px 12px rgba(var(--color-primary-rgb, 53, 80, 113), 0.08);
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
  transition: all 0.2s ease;

  &:hover {
    background: var(--bg-hover);
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
  transition: border-color 0.2s ease, box-shadow 0.2s ease;

  &:hover {
    border-color: var(--color-primary);
    box-shadow: 0 2px 12px rgba(var(--color-primary-rgb, 53, 80, 113), 0.08);
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
    grid-template-columns: 1fr;
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
  .projects-section {
    padding: 24px;
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
