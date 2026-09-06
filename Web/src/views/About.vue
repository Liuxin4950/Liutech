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
import aboutHonorsImg from "@/assets/image/about/about-honors-collage.png"
import { getAboutPage } from "@/services/about"
import type { AboutPageInfo } from "@/services/about"

const messageModalVisible = ref(false)
const aboutPage = ref<AboutPageInfo | null>(null)
const loadState = ref<'loading' | 'ready' | 'error'>('loading')
const avatarSrc = computed(() => aboutPage.value?.author.avatar || '/洛天依.png')
const honorsImageSrc = computed(() => aboutPage.value?.honors.imageUrl || aboutHonorsImg)
const pageDescription = computed(() => aboutPage.value?.metaDescription || '关于 LiuTech')

const openMessageModal = () => {
  messageModalVisible.value = true
}

const bannerStore = useBannerStore()

const setBanner = (description = '正在加载关于页内容…') => {
  bannerStore.setBanner({
    slides: [{
      title: '关于',
      description,
      imageUrl: bannerFallback,
      sortOrder: 0,
      status: 1
    }],
    badgeText: 'About Me',
    titleAs: 'h1',
    titleHighlight: '我',
    mode: 'hero'
  })
}

const loadAbout = async () => {
  loadState.value = 'loading'
  try {
    const response = await getAboutPage()
    aboutPage.value = response
    setBanner(response.bannerDescription)
    loadState.value = 'ready'
  } catch {
    loadState.value = 'error'
  }
}

const handleAvatarError = (event: Event) => {
  const img = event.target as HTMLImageElement
  if (!img.src.endsWith('/洛天依.png')) {
    img.src = '/洛天依.png'
    return
  }
  handleImageError(event)
}

const isInternalLink = (href?: string | null) => Boolean(href?.startsWith('/') && !href.startsWith('//'))
const resolveSocialIcon = (href: string) => {
  const normalized = href.trim().toLowerCase()
  if (normalized.startsWith('mailto:')) return 'mail'
  if (/^(?:https?:\/\/)?(?:www\.)?github\.com(?:\/|$)/.test(normalized)) return 'github'
  return 'globe'
}

useHead({
  title: '关于我 - LiuTech',
  meta: [
    { name: 'description', content: pageDescription }
  ]
})

onMounted(() => {
  loadAbout()
})

setBanner()

// 统一滚动显现（once: false 可重播）：全部区块共用一套机制，
// 项目卡片动画由区块 is-visible 触发 CSS 交错升起（见样式），不再需要独立观察器
useScrollReveal('.reveal', { once: false })
</script>

<template>
  <div class="content">
    <section v-if="loadState === 'loading'" class="about-state" aria-live="polite">
      <Icon name="loader" size="24" class="state-spinner" />
      <span>正在加载关于页内容…</span>
    </section>

    <section v-else-if="loadState === 'error'" class="about-state" role="alert">
      <Icon name="alertCircle" size="26" />
      <strong>关于页内容加载失败</strong>
      <button type="button" class="btn-primary" @click="loadAbout">
        <Icon name="refresh" size="16" />
        重新加载
      </button>
    </section>

    <template v-else-if="aboutPage">
    <section class="hero-section reveal">
      <div class="hero-content">
        <div class="hero-visual">
          <div class="avatar-ring">
            <img :src="avatarSrc" :alt="aboutPage.author.name" class="avatar-img" @error="handleAvatarError" />
          </div>
          <div class="hero-text">
            <h2 class="username">{{ aboutPage.author.name }}</h2>
            <p class="user-bio">{{ aboutPage.author.title }}</p>
            <p class="user-motto">{{ aboutPage.motto }}</p>
            <div class="social-links">
              <a
                v-for="link in aboutPage.socialLinks"
                :key="`${link.label}-${link.href}`"
                :href="link.href"
                class="social-item"
                :title="link.label"
                target="_blank"
                rel="noopener noreferrer"
              >
                <Icon :name="resolveSocialIcon(link.href)" size="20" />
              </a>
            </div>
          </div>
        </div>

        <div class="hero-intro">
          <p
            v-for="(paragraph, index) in aboutPage.introParagraphs"
            :key="index"
            :class="{ 'intro-lead': index === 0 }"
          >{{ paragraph }}</p>
        </div>
      </div>
    </section>

    <!-- 技术栈 -->
    <section class="tech-stack-section section-card reveal">
      <SectionTitle subtitle="Tech Stack" title="技术" highlight="栈" />
      <div class="skill-groups">
        <div
          v-for="group in aboutPage.skillGroups"
          :key="group.category"
          class="skill-group"
        >
          <div class="skill-group-header">
            <span>{{ group.category }}</span>
          </div>
          <div class="skill-tags">
            <span v-for="skill in group.skills" :key="skill" class="skill-tag">
              <span class="tag-prefix" aria-hidden="true">#</span>{{ skill }}
            </span>
          </div>
        </div>
      </div>
    </section>

    <!-- 项目经历：时间轴布局，滚动到时条目依次升起 -->
    <section class="projects-section section-card reveal">
      <SectionTitle subtitle="Projects" title="项目" highlight="经历" />
      <div class="project-timeline">
        <article v-for="(project, index) in aboutPage.projects" :key="project.name" class="project-item">
          <span class="project-index">{{ String(index + 1).padStart(2, '0') }}</span>
          <div class="project-info">
            <h3>
              {{ project.name }}
              <router-link v-if="isInternalLink(project.link)" :to="project.link || '/'" class="project-link" title="访问项目">
                <Icon name="external" size="14" />
              </router-link>
              <a v-else-if="project.link" :href="project.link" class="project-link" title="访问项目" target="_blank" rel="noopener noreferrer">
                <Icon name="external" size="14" />
              </a>
            </h3>
            <p>{{ project.description }}</p>
            <div class="project-tags">
              <span v-for="technology in project.technologies" :key="technology" class="project-tag">
                <span class="tag-prefix" aria-hidden="true">#</span>{{ technology }}
              </span>
            </div>
          </div>
        </article>
      </div>
    </section>

    <!-- 荣誉 -->
    <section class="honors-spotlight reveal">
      <div class="honors-art">
        <img :src="honorsImageSrc" alt="证书与奖杯插画" loading="lazy" @error="handleImageError">
      </div>
      <div class="honors-copy">
        <SectionTitle align="left" subtitle="Honors" title="荣誉与" highlight="证书" />
        <p>{{ aboutPage.honors.summary }}</p>
        <router-link to="/honors" class="text-link">
          查看全部荣誉 <Icon name="chevronRight" size="15" />
        </router-link>
      </div>
    </section>

    <!-- 联系我 -->
    <section class="contact-section reveal">
      <div>
        <SectionTitle align="left" subtitle="Contact" title="联系" highlight="我" />
        <p>{{ aboutPage.contactText }}</p>
      </div>
      <div class="contact-actions">
        <button class="btn-primary" type="button" @click="openMessageModal">
          <Icon name="edit" size="18" />
          写留言
        </button>
        <a
          v-for="link in aboutPage.socialLinks"
          :key="link.label"
          :href="link.href"
          class="contact-link"
          target="_blank"
          rel="noopener noreferrer"
        >
          <Icon :name="resolveSocialIcon(link.href)" size="17" />
          <span>{{ link.value }}</span>
        </a>
      </div>
    </section>
    </template>

    <MessageModal v-model:visible="messageModalVisible" />
  </div>
</template>

<style scoped lang="scss">
@use "@/assets/styles/tokens" as *;

.about-state {
  min-height: 280px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 14px;
  color: var(--text-subtle);
  border: 1px solid var(--border-base);
  border-radius: $card-radius;
  background: var(--bg-card);
  box-shadow: var(--shadow-sm);

  .state-spinner {
    animation: spin 0.9s linear infinite;
  }
}

@keyframes spin {
  to { transform: rotate(360deg); }
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

// Hero（含介绍，左右合并；紧凑布局）
.hero-section {
  padding: 40px 40px;
  border-radius: $card-radius;
  background:
    radial-gradient(circle at 20% 30%, rgba(var(--color-primary-rgb), 0.12) 0%, transparent 40%),
    radial-gradient(circle at 80% 70%, rgba(var(--color-secondary-rgb), 0.12) 0%, transparent 40%),
    var(--bg-section);
  border: 1px solid var(--border-light);
  overflow: hidden;
}

.hero-content {
  max-width: 1000px;
  margin: 0 auto;
  display: grid;
  grid-template-columns: 2fr 5fr; /* 左身份 / 右介绍，右略宽减少左列拉长 */
  align-items: center;
  gap: 44px;

  @include respond(md) {
    grid-template-columns: 1fr;
    gap: 28px;
  }
}

/* 左：身份卡 */
.hero-visual {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
}

.avatar-ring {
  width: 148px;
  height: 148px;
  border-radius: 50%;
  padding: 4px;
  margin-bottom: 18px;
  background: linear-gradient(135deg, var(--color-primary) 0%, var(--color-secondary) 100%);
  box-shadow: 0 10px 30px rgba(var(--color-primary-rgb), 0.28);
  transition: transform 0.3s ease;

  &:hover {
    transform: translateY(-4px);
  }
}

.avatar-img {
  width: 100%;
  height: 100%;
  border-radius: 50%;
  object-fit: cover;
  border: 4px solid var(--bg-card);
  background: var(--bg-card);
  display: block;
}

.hero-text {
  .username {
    margin: 0 0 6px;
    font-size: 2.1rem;
    font-weight: 700;
    letter-spacing: 0.18em;
    background: linear-gradient(120deg, var(--color-primary), var(--color-secondary));
    -webkit-background-clip: text;
    background-clip: text;
    -webkit-text-fill-color: transparent;
  }

  .user-bio {
    font-size: 1.05rem;
    color: var(--text-subtle);
    margin: 0 0 6px;
    font-weight: 500;
  }

  .user-motto {
    color: var(--text-muted);
    font-size: 0.9rem;
    font-style: italic;
    margin: 0 0 20px;
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

/* 右：关于我的介绍（紧凑行距） */
.hero-intro {
  p {
    margin: 0 0 10px;
    color: var(--text-subtle);
    line-height: 1.7;

    &:last-child {
      margin-bottom: 0;
    }
  }
}

// 简介与数据
.intro-lead {
  margin: 0 0 14px;
  padding-left: 14px;
  border-left: 3px solid var(--color-primary);
  font-size: 1.05rem;
  font-weight: 600;
  color: var(--text-title);
  line-height: 1.6;
}

// 页面区块间距
.hero-section,
.tech-stack-section,
.projects-section,
.honors-spotlight {
  margin-bottom: 24px;
}

// 技术栈
.skill-groups {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;

  @include respond(lg) {
    grid-template-columns: repeat(2, 1fr);
  }
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
  color: var(--color-primary);
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
  color: var(--text-subtle);
  background: var(--bg-soft);
  border: 1px solid var(--border-light);
  transition: color 0.2s ease, border-color 0.2s ease;

  &:hover {
    color: var(--text-title);
    border-color: var(--color-primary);
  }
}

// 项目经历：左侧竖线时间轴，序号 + 内容
.project-timeline {
  --rail-center: 24px;
  --dot-center-y: 34px;
}

.tag-prefix {
  color: var(--color-primary);
  font-weight: 700;
}

.project-item {
  position: relative;
  display: grid;
  grid-template-columns: 32px minmax(0, 1fr);
  gap: 18px;
  padding: 18px 8px;
  border-radius: 10px;

  &::before {
    content: '';
    position: absolute;
    z-index: 0;
    left: calc(var(--rail-center) - 1px);
    top: 0;
    bottom: 0;
    width: 2px;
    border-radius: 2px;
    background: var(--border-base);
    pointer-events: none;
  }

  &:first-child::before {
    top: var(--dot-center-y);
  }

  &:last-child::before {
    bottom: calc(100% - var(--dot-center-y));
  }

  &:hover {
    background: var(--bg-soft);

    .project-index {
      background: var(--color-primary);
      color: var(--text-on-primary);
      border-color: var(--color-primary);
    }
  }

  &:not(:last-child)::after {
    content: '';
    position: absolute;
    left: 58px;
    right: 8px;
    bottom: 0;
    border-bottom: 1px dashed var(--border-light);
  }
}

.project-index {
  position: relative;
  z-index: 1;
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  font-size: 0.8rem;
  font-weight: 700;
  color: var(--color-primary);
  background: var(--bg-card);
  border: 2px solid rgba(var(--color-primary-rgb), 0.5);
  transition: background 0.2s ease, color 0.2s ease, border-color 0.2s ease;
}

/* 条目升起动画：由区块 reveal（is-visible）触发，依次交错（无独立 JS） */
.projects-section .project-item {
  opacity: 0;
  transform: translateY(30px);
  transition:
    background 0.2s ease,
    opacity 0.6s cubic-bezier(0.16, 1, 0.3, 1),
    transform 0.6s cubic-bezier(0.16, 1, 0.3, 1);

  &:nth-child(2) { transition-delay: 0.08s; }
  &:nth-child(3) { transition-delay: 0.16s; }
  &:nth-child(4) { transition-delay: 0.24s; }
}

.projects-section.reveal.is-visible .project-item {
  opacity: 1;
  transform: translateY(0);
}

.project-info {
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
    color: var(--text-subtle);
    line-height: 1.75;
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
  border-radius: $card-radius;
  overflow: hidden;
  border: 1px solid var(--border-base);
  background: var(--bg-card);
}

.honors-art {
  min-height: 320px;
  background: var(--bg-section);

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
    color: var(--text-subtle);
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
    color: var(--text-subtle);
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
  color: var(--text-subtle);
  background: var(--bg-card);

  &:hover {
    color: var(--color-primary);
    border-color: var(--color-primary);
    transform: translateY(-2px);
  }
}

@include respond(md) {
  .section-card {
    padding: 24px;
  }

  .hero-section {
    padding: 48px 24px;
  }

  .skill-groups {
    grid-template-columns: repeat(2, 1fr);
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
  .hero-section {
    padding: 36px 16px;
  }

  .avatar-ring {
    width: 120px;
    height: 120px;
  }

  .hero-text {
    .username {
      font-size: 1.7rem;
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

  .skill-groups {
    grid-template-columns: 1fr;
  }

  .project-item {
    grid-template-columns: 28px minmax(0, 1fr);
    gap: 12px;
    padding: 14px 4px;

    &:not(:last-child)::after {
      left: 44px;
      right: 4px;
    }
  }

  .project-index {
    width: 28px;
    height: 28px;
    font-size: 0.72rem;
  }

  .project-timeline {
    --rail-center: 18px;
    --dot-center-y: 28px;
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
