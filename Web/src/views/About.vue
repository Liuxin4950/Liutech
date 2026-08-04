<script setup lang="ts">
import { ref, computed, onMounted } from "vue"
import { useHead } from '@vueuse/head'
import Icon from "../components/Icon.vue"
import SectionTitle from "@/components/SectionTitle.vue"
import { handleImageError } from "@/composables/useImageFallback"
import { useScrollReveal } from "@/composables/useScrollReveal"
import { useBannerStore } from "@/stores/banner"
import bannerFallback from "@/assets/image/banner/banner0.png"
import MessageModal from "@/components/MessageModal.vue"
import moonImg from "@/assets/image/moon.png"
import aboutHonorsImg from "@/assets/image/about/about-honors-collage.png"
import { getAuthorProfile } from "@/services/user"
import type { ProfileInfo } from "@/services/user"

const messageModalVisible = ref(false)

const profileInfo = ref<ProfileInfo>({
  name: 'LiuTech',
  title: '全栈工程师',
  avatar: '/洛天依.png',
  bio: '专注于前端开发、后端架构和技术分享。热爱编程，喜欢探索新技术。',
  stats: {
    posts: 0,
    comments: 0,
    views: 0
  }
})

const links = [
  { icon: "github", label: "GitHub", value: "Liuxin4950", href: "https://github.com/Liuxin4950" },
  { icon: "mail", label: "邮箱", value: "liuxin4950@gmail.com", href: "mailto:liuxin4950@gmail.com" }
]

const stats = [
  { icon: "fileText", label: "文章", value: computed(() => profileInfo.value.stats.posts || 0) },
  { icon: "messageSquare", label: "评论", value: computed(() => profileInfo.value.stats.comments || 0) },
  { icon: "eye", label: "访问", value: computed(() => profileInfo.value.stats.views || 0) }
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

// 项目经历
const projects = [
  {
    name: "LiuTech 博客",
    description: "全栈个人博客平台。从第一版写死配置、本地推理的混乱方案，到如今 Spring Boot 微服务 + Vue 3 + Docker Compose 的工程化架构。一次 Docker 误操作导致数据丢失后，深入学习了数据持久化与备份——也让我明白，真正的工程是保证系统可靠运行。",
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

const loadProfile = async () => {
  try {
    const response = await getAuthorProfile()
    if (response) profileInfo.value = response
  } catch {
    // 静默失败
  }
}

useHead({
  title: '关于我 - LiuTech',
  meta: [
    { name: 'description', content: '关于 LiuTech 作者刘鑫：全栈工程师、技术博主，专注于 Spring Boot、Vue 3、AI 应用与软件工程实践。' }
  ]
})

onMounted(() => {
  loadProfile()
})

// Banner 页眉：关于我（一级页面，500px hero 大横幅承担页面标题）
const bannerStore = useBannerStore()
bannerStore.setBanner({
  slides: [{
    title: '关于',
    description: '全栈工程师 & 技术博主 · 专注 Spring Boot、Vue 3 与 AI 应用实践',
    imageUrl: bannerFallback,
    sortOrder: 0,
    status: 1
  }],
  badgeText: 'About Me',
  titleAs: 'h1',
  titleHighlight: '我',
  mode: 'hero'
})

useScrollReveal('.reveal')
</script>

<template>
  <div class="about-page">
    <!-- Hero -->
    <section class="hero-section">
      <div class="hero-content">
        <div class="avatar-wrapper reveal">
          <div class="user-avatar" :style="{ backgroundImage: `url(${moonImg})` }">
            <img src="@/assets/image/gif/坐下.gif" alt="刘鑫" class="liuyin" @error="handleImageError" />
          </div>
        </div>
        <div class="hero-text reveal reveal-delay-1">
          <h1 class="username">
            <svg class="name-svg" viewBox="0 0 200 60">
              <defs>
                <linearGradient id="about-name-gradient" x1="0%" y1="0%" x2="100%" y2="0%">
                  <stop offset="0%" style="stop-color:var(--color-primary);stop-opacity:1" />
                  <stop offset="100%" style="stop-color:var(--color-secondary);stop-opacity:1" />
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
    </section>

    <!-- 简介 + 数据 -->
    <section class="about-intro section-card reveal">
      <div class="intro-grid">
        <div class="intro-copy">
          <SectionTitle align="left" subtitle="About Me" title="关于" highlight="我" />
          <p class="intro-lead">
            我叫刘鑫，软件工程专业的学生，正在成为一名全栈开发工程师。用代码记录时间与成长，用技术创造快乐与价值。
          </p>
          <p>
            最初接触编程只是出于好奇，把它和传说中黑客的网络技术搞混了，但是在学习的过程中却渐渐发现：相比刷短视频，我更喜欢用学习到的知识来实现一些有价值的东西。
          </p>
          <p>
            这些年，我从一个只能写出静态页面的初学者，成长为能独立打通前后端开发、数据库设计、容器化部署与自动化交付的开发者——这个博客就是我为整合所学、并亲手实现一个能和读者交流的 Live2D 看板娘而搭建的。
          </p>
          <p>
            如今我把重心放在 AI 与软件工程的结合上：用大模型让看板娘开口交流，用 AI 辅助开发提效。我相信，真正的软件工程不只是写代码，更是保证系统可靠地运行、持续地创造价值。
          </p>
        </div>

        <div class="stats-grid">
          <div v-for="stat in stats" :key="stat.label" class="stat-card">
            <Icon :name="stat.icon" size="26" />
            <div class="stat-value">{{ stat.value }}</div>
            <div class="stat-name">{{ stat.label }}</div>
          </div>
        </div>
      </div>
    </section>

    <!-- 技术栈 -->
    <section class="tech-stack-section section-card reveal">
      <SectionTitle subtitle="Tech Stack" title="技术" highlight="栈" />
      <div class="skill-groups">
        <div
          v-for="group in skillGroups"
          :key="group.category"
          class="skill-group"
          :class="`skill-group--${group.accent}`"
        >
          <div class="skill-group-header">
            <Icon :name="group.icon" size="18" />
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
    <section class="projects-section section-card reveal">
      <SectionTitle subtitle="Projects" title="项目" highlight="经历" />
      <div class="project-grid">
        <article v-for="project in projects" :key="project.name" class="project-card">
          <div class="project-icon">
            <Icon :name="project.icon" size="24" />
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

    <!-- 荣誉 -->
    <section class="honors-spotlight reveal">
      <div class="honors-art">
        <img :src="aboutHonorsImg" alt="证书与奖杯插画" loading="lazy" @error="handleImageError">
      </div>
      <div class="honors-copy">
        <SectionTitle align="left" subtitle="Honors" title="荣誉与" highlight="证书" />
        <p>全国职业院校技能大赛团体二等奖、重庆市选拔赛第一名、Web 应用开发一等奖、金砖国家技能大赛三等奖……持续积累中。</p>
        <router-link to="/honors" class="text-link">
          查看全部荣誉 <Icon name="chevronRight" size="15" />
        </router-link>
      </div>
    </section>

    <!-- 技术理念 -->
    <section class="philosophy-section section-card reveal">
      <SectionTitle subtitle="Philosophy" title="技术" highlight="理念" />
      <div class="philosophy-grid">
        <div v-for="item in philosophy" :key="item.title" class="philosophy-card">
          <div class="philosophy-icon">
            <Icon :name="item.icon" size="20" />
          </div>
          <div class="philosophy-body">
            <h3>{{ item.title }}</h3>
            <p>{{ item.desc }}</p>
          </div>
        </div>
      </div>
    </section>

    <!-- 联系我 -->
    <section class="contact-section reveal">
      <div>
        <SectionTitle align="left" subtitle="Contact" title="联系" highlight="我" />
        <p>有文章内容、项目问题或技术交流，欢迎留言。</p>
      </div>
      <div class="contact-actions">
        <button class="btn-primary" type="button" @click="openMessageModal">
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

.section-card {
  border: 1px solid var(--border-base);
  border-radius: $card-radius;
  background: var(--bg-card);
  box-shadow: var(--shadow-sm);
  padding: 34px;
}

.contact-link,
.text-link {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  text-decoration: none;
  font-weight: 700;
  transition: transform 0.2s ease, border-color 0.2s ease, background 0.2s ease, color 0.2s ease;
}

// Hero
.hero-section {
  width: 100%;
  position: relative;
  padding: 80px 20px 100px;
  margin-bottom: 24px;
  border-radius: $card-radius;
  background:
    radial-gradient(circle at 20% 30%, rgba(var(--color-primary-rgb), 0.12) 0%, transparent 40%),
    radial-gradient(circle at 80% 70%, rgba(var(--color-secondary-rgb), 0.12) 0%, transparent 40%),
    var(--bg-section);
  border: 1px solid var(--border-light);
  overflow: hidden;
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
    background-size: cover;
    background-position: center;
    box-shadow: 0 8px 32px rgba(0, 0, 0, 0.15);
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
        fill: var(--text-title);
        fill-opacity: 0.95;
        stroke: none;
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
    to { stroke-dashoffset: 0; }
  }

  .user-bio {
    font-size: 1.1rem;
    color: var(--text-subtle);
    margin-bottom: 8px;
    font-weight: 500;
  }

  .user-motto {
    color: var(--text-muted);
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

// 简介与数据
.about-intro {
  margin-bottom: 24px;
}

.intro-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.4fr) minmax(260px, 0.6fr);
  gap: 48px;
  align-items: start;
}

.intro-copy {
  p {
    margin: 0 0 14px;
    color: var(--text-secondary);
    line-height: 1.9;

    &:last-child {
      margin-bottom: 0;
    }
  }
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

.stats-grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: 16px;
}

.stat-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 24px;
  border-radius: 12px;
  background: var(--bg-soft);
  border: 1px solid var(--border-light);
  color: var(--color-primary);
  transition: transform 0.25s ease, box-shadow 0.25s ease;

  &:hover {
    transform: translateY(-4px);
    box-shadow: var(--shadow-md);
  }

  .stat-value {
    font-size: 2rem;
    font-weight: 800;
    color: var(--text-title);
    line-height: 1;
  }

  .stat-name {
    font-size: 0.85rem;
    color: var(--text-muted);
    font-weight: 500;
  }
}

// 技术栈
.tech-stack-section {
  margin-bottom: 24px;
}

.skill-groups {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
}

.skill-group {
  border: 1px solid var(--border-light);
  border-radius: 12px;
  padding: 20px;
  transition: border-color 0.2s ease, transform 0.2s ease, box-shadow 0.2s ease;
  background: var(--bg-card);

  &:hover {
    border-color: var(--color-primary);
    transform: translateY(-3px);
    box-shadow: var(--shadow-md);
  }
}

.skill-group-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 16px;
  font-weight: 700;
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

// 项目经历
.projects-section {
  margin-bottom: 24px;
}

.project-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 18px;
}

.project-card {
  display: flex;
  gap: 16px;
  padding: 24px;
  border: 1px solid var(--border-light);
  border-radius: 12px;
  transition: border-color 0.2s ease, transform 0.25s ease, box-shadow 0.25s ease;

  &:hover {
    border-color: var(--color-primary);
  }
}

.project-icon {
  flex-shrink: 0;
  width: 48px;
  height: 48px;
  border-radius: 12px;
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
    margin: 0 0 8px;
    font-size: 1.05rem;
    font-weight: 700;
    color: var(--text-title);
    display: flex;
    align-items: center;
    gap: 6px;
  }

  p {
    margin: 0 0 12px;
    font-size: 0.88rem;
    color: var(--text-secondary);
    line-height: 1.7;
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
  padding: 3px 10px;
  border-radius: 20px;
  font-size: 0.75rem;
  font-weight: 600;
  color: var(--color-primary);
  background: rgba(var(--color-primary-rgb), 0.08);
  border: 1px solid rgba(var(--color-primary-rgb), 0.16);
}

// 荣誉
.honors-spotlight {
  display: grid;
  grid-template-columns: minmax(0, 1.1fr) minmax(300px, 0.9fr);
  gap: 0;
  border-radius: $card-radius;
  overflow: hidden;
  border: 1px solid var(--border-base);
  background: var(--bg-card);
  margin-bottom: 24px;
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

  p {
    margin: 0;
    color: var(--text-secondary);
    line-height: 1.85;
  }
}

.text-link {
  width: fit-content;
  margin-top: 22px;
  color: var(--color-primary);

  &:hover {
    color: var(--color-secondary);
    transform: translateX(3px);
  }
}

// 理念
.philosophy-section {
  margin-bottom: 24px;
}

.philosophy-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 18px;
}

.philosophy-card {
  display: flex;
  gap: 14px;
  align-items: flex-start;
  padding: 24px;
  border: 1px solid var(--border-light);
  border-radius: 12px;
  transition: border-color 0.2s ease, transform 0.25s ease, box-shadow 0.25s ease;

  &:hover {
    border-color: var(--color-primary);
  }

  h3 {
    margin: 0 0 6px;
    font-size: 1rem;
    font-weight: 700;
    color: var(--text-title);
  }

  p {
    margin: 0;
    font-size: 0.88rem;
    color: var(--text-secondary);
    line-height: 1.7;
  }
}

.philosophy-icon {
  flex-shrink: 0;
  width: 42px;
  height: 42px;
  border-radius: 10px;
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

// 联系我
.contact-section {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: center;
  gap: 28px;
  padding: 34px;
  border: 1px solid var(--border-base);
  border-radius: $card-radius;
  background: var(--bg-section);

  p {
    margin: 12px 0 0;
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

.contact-link {
  min-height: 44px;
  border-radius: 30px;
  border: 1px solid var(--border-base);
  padding: 0 18px;
  color: var(--text-secondary);
  background: var(--bg-card);

  &:hover {
    color: var(--color-primary);
    border-color: var(--color-primary);
    transform: translateY(-2px);
  }
}

@include respond(md) {
  .about-page {
    padding: 20px 16px 44px;
  }

  .section-card {
    padding: 24px;
  }

  .hero-section {
    padding: 60px 16px 80px;
  }

  .intro-grid {
    grid-template-columns: 1fr;
    gap: 32px;
  }

  .stats-grid {
    grid-template-columns: repeat(3, 1fr);
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

  .honors-art {
    min-height: 240px;
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
    padding: 40px 12px 60px;
  }

  .avatar-wrapper {
    margin-bottom: 16px;

    .user-avatar {
      width: 96px;
      height: 96px;

      .liuyin {
        top: 15px;
        left: -8px;
        max-width: 78px;
      }
    }
  }

  .hero-text {
    .username {
      height: 56px;

      .name-svg {
        max-width: 180px;

        text {
          font-size: 40px;
        }
      }
    }

    .user-bio {
      font-size: 0.95rem;
    }

    .user-motto {
      font-size: 0.8rem;
    }
  }

  .social-links {
    gap: 10px;

    .social-item {
      width: 38px;
      height: 38px;
    }
  }

  .stats-grid {
    grid-template-columns: 1fr;
  }

  .skill-groups {
    grid-template-columns: 1fr;
  }

  .philosophy-grid {
    grid-template-columns: 1fr;
  }

  .honors-art {
    min-height: 200px;
  }

  .contact-section {
    padding: 24px;
  }

  .contact-link,
  .contact-actions .btn-primary {
    width: 100%;
    justify-content: center;
  }
}
</style>
