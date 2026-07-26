<script setup lang="ts">
import { computed, ref } from 'vue'
import Icon from '@/components/Icon.vue'
import { handleImageError } from '@/composables/useImageFallback'
import softwareTestingFirstPlace from '@/assets/image/honors/职业技能大赛重庆市选拔赛软件测试项目-第一名.jpg'
import webAppFirstPrize from '@/assets/image/honors/Web应用开发-一等奖.jpg'
import wechatMiniappFirstPrize from '@/assets/image/honors/微信小程序开发赛项-一等奖.jpg'
import lowcodeNvwaFirstPrize from '@/assets/image/honors/低代码女娲杯-一等奖.jpg'
import mobileAppSecondPrize from '@/assets/image/honors/移动应用开发-二等奖.jpg'
import appSoftwareThirdPrize from '@/assets/image/honors/应用软件开发-三等奖.jpg'
import appSoftwareBronzeAward from '@/assets/image/honors/应用软件开发-铜奖.jpg'
import textAppExcellenceAward from '@/assets/image/honors/文本应用开发-优秀奖.jpg'
import cProgrammingEngineerCert from '@/assets/image/honors/C语言程序设计工程师证书.jpg'

import webFrontendSecondPrize from '@/assets/image/honors/中职技能大赛Web前端开发-二等奖.jpg'
import mobileAppNationalSecondPrize from '@/assets/image/honors/全国职业院校技能大赛移动应用与开发-二等奖.jpg'
import bricsDataVizThirdPrize from '@/assets/image/honors/金砖国家技能大赛数据分析与可视化-三等奖.jpg'

type HonorCategory = 'all' | 'dev' | 'test' | 'miniapp' | 'lowcode' | 'cert'

interface HonorItem {
  id: string
  title: string
  level: string
  year: string
  category: Exclude<HonorCategory, 'all'>
  image: string
}

const honorCategories: Array<{ label: string; value: HonorCategory }> = [
  { label: '全部', value: 'all' },
  { label: '软件开发', value: 'dev' },
  { label: '软件测试', value: 'test' },
  { label: '小程序', value: 'miniapp' },
  { label: '低代码', value: 'lowcode' },
  { label: '技术认证', value: 'cert' }
]

const honors: HonorItem[] = [
  {
    id: 'software-testing-first-place',
    title: '中华人民共和国第三届职业技能大赛重庆市选拔赛软件测试（世）项目',
    level: '第一名',
    year: '2025',
    category: 'test',
    image: softwareTestingFirstPlace
  },
  {
    id: 'web-app-first-prize',
    title: 'Web 应用开发',
    level: '一等奖',
    year: '2026',
    category: 'dev',
    image: webAppFirstPrize
  },
  {
    id: 'wechat-miniapp-first-prize',
    title: '重庆市专项职业能力技能大赛微信小程序开发赛项',
    level: '一等奖',
    year: '2025',
    category: 'miniapp',
    image: wechatMiniappFirstPrize
  },
  {
    id: 'lowcode-nvwa-first-prize',
    title: '第十四届全国大学生计算机应用能力与数字素养大赛（久其女娲杯低代码编程赛项）',
    level: '一等奖',
    year: '2023',
    category: 'lowcode',
    image: lowcodeNvwaFirstPrize
  },
  {
    id: 'mobile-app-second-prize',
    title: '重庆市职业院校技能大赛高职组移动应用设计与开发',
    level: '二等奖',
    year: '2024',
    category: 'dev',
    image: mobileAppSecondPrize
  },
  {
    id: 'c-programming-engineer-cert',
    title: 'C语言程序设计工程师证书',
    level: '工程师证书',
    year: '2024',
    category: 'cert',
    image: cProgrammingEngineerCert
  },
  {
    id: 'app-software-third-prize',
    title: '重庆市职业院校技能大赛高职组应用软件系统开发',
    level: '三等奖',
    year: '2024',
    category: 'dev',
    image: appSoftwareThirdPrize
  },
  {
    id: 'app-software-bronze-award',
    title: '应用软件开发',
    level: '铜奖',
    year: '2025',
    category: 'dev',
    image: appSoftwareBronzeAward
  },
  {
    id: 'text-app-excellence-award',
    title: '蓝桥杯全国总决赛Web应用开发职业院校组',
    level: '优秀奖',
    year: '2025',
    category: 'dev',
    image: textAppExcellenceAward
  },
  {
    id: 'web-frontend-second-prize',
    title: '重庆市第十三届中职技能大赛 Web 前端开发',
    level: '二等奖',
    year: '2021',
    category: 'dev',
    image: webFrontendSecondPrize
  },
  {
    id: 'mobile-app-national-second-prize',
    title: '全国职业院校技能大赛中职组 移动应用与开发',
    level: '团体二等奖',
    year: '2023',
    category: 'dev',
    image: mobileAppNationalSecondPrize
  },
  {
    id: 'brics-data-viz-third-prize',
    title: '一带一路暨金砖国家技能发展与技术创新大赛 数据分析与可视化',
    level: '团体三等奖',
    year: '2021',
    category: 'dev',
    image: bricsDataVizThirdPrize
  }
]

const activeCategory = ref<HonorCategory>('all')
const selectedHonor = ref<HonorItem | null>(null)

const filteredHonors = computed(() => {
  if (activeCategory.value === 'all') return honors
  return honors.filter((honor) => honor.category === activeCategory.value)
})

const openPreview = (honor: HonorItem) => {
  selectedHonor.value = honor
}

const closePreview = () => {
  selectedHonor.value = null
}
</script>

<template>
  <div class="content">
    <section class="honors-hero">
      <div class="hero-copy">
        <h1>荣誉与证书</h1>
        <p>软件开发、软件测试、小程序、低代码和技术认证相关证书。</p>
      </div>
    </section>

    <nav class="category-tabs" aria-label="荣誉分类">
      <button
        v-for="category in honorCategories"
        :key="category.value"
        type="button"
        class="category-tab"
        :class="{ active: activeCategory === category.value }"
        @click="activeCategory = category.value"
      >
        {{ category.label }}
      </button>
    </nav>

    <section class="honor-grid" aria-label="荣誉证书列表">
      <article v-for="honor in filteredHonors" :key="honor.id" class="honor-card">
        <button type="button" class="honor-image-button" @click="openPreview(honor)">
          <img :src="honor.image" :alt="`${honor.title}${honor.level}证书预览`" loading="lazy" @error="handleImageError">
          <span class="image-overlay">
            <Icon name="maximize" size="18" />
            查看证书
          </span>
        </button>
        <div class="honor-content">
          <div class="honor-meta">
            <span>{{ honor.year }}</span>
            <span>{{ honor.level }}</span>
          </div>
          <h2>{{ honor.title }}</h2>
        </div>
      </article>
    </section>

    <Teleport to="body">
      <div v-if="selectedHonor" class="preview-overlay" @click="closePreview">
        <div class="preview-dialog" role="dialog" aria-modal="true" @click.stop>
          <header class="preview-header">
            <div>
              <span>{{ selectedHonor.year }} · {{ selectedHonor.level }}</span>
              <h2>{{ selectedHonor.title }}</h2>
            </div>
            <button type="button" class="preview-close" aria-label="关闭证书预览" @click="closePreview">
              <Icon name="close" size="20" />
            </button>
          </header>
          <div class="preview-body">
            <img :src="selectedHonor.image" :alt="`${selectedHonor.title}${selectedHonor.level}证书大图`" @error="handleImageError">
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>

<style scoped lang="scss">
@use "@/assets/styles/tokens" as *;

.honors-hero {
  margin-bottom: 18px;
}

.hero-copy,
.honor-card {
  background: var(--bg-card);
  border: 1px solid var(--border-base);
  box-shadow: var(--shadow-sm);
}

.hero-copy {
  border-radius: 16px;
  padding: 34px;

  h1 {
    margin: 14px 0 12px;
    color: var(--text-title);
    font-size: 2rem;
    line-height: 1.2;
  }

  p {
    margin: 0;
    color: var(--text-secondary);
    line-height: 1.85;
  }
}

.category-tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 20px;
}

.category-tab {
  border: 1px solid var(--border-base);
  border-radius: 999px;
  background: var(--bg-card);
  color: var(--text-secondary);
  padding: 9px 16px;
  font-size: 0.92rem;
  cursor: pointer;
  transition: color 0.2s ease, border-color 0.2s ease, background 0.2s ease;

  &.active,
  &:hover {
    color: var(--color-primary);
    border-color: var(--color-primary);
    background: var(--color-primary-light);
  }
}

.honor-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 18px;

  @include respond(md) {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  @include respond(sm) {
    grid-template-columns: 1fr;
  }
}

.honor-card {
  border-radius: 16px;
  overflow: hidden;
  transition: transform 0.25s ease, box-shadow 0.25s ease;

  &:hover {
    transform: translateY(-3px);
    box-shadow: var(--shadow-md);
  }
}

.honor-image-button {
  position: relative;
  display: block;
  width: 100%;
  aspect-ratio: 4 / 3;
  border: 0;
  padding: 0;
  background: var(--bg-hover);
  overflow: hidden;
  cursor: pointer;

  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
    object-position: center top;
    display: block;
    transition: transform 0.25s ease;
  }

  &:hover img {
    transform: scale(1.03);
  }

  &:hover .image-overlay {
    opacity: 1;
  }
}

.image-overlay {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  color: #fff;
  background: rgba(15, 23, 42, 0.5);
  opacity: 0;
  transition: opacity 0.2s ease;
  font-size: 0.92rem;
  font-weight: 700;
}

.honor-content {
  padding: 18px;

  h2 {
    margin: 8px 0 0;
    color: var(--text-title);
    font-size: 1rem;
    line-height: 1.45;
  }
}

.honor-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--color-primary);
  font-size: 0.82rem;
  font-weight: 700;
}

.preview-overlay {
  position: fixed;
  inset: 0;
  z-index: 3000;
  background: rgba(15, 23, 42, 0.72);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
}

.preview-dialog {
  width: min(960px, 100%);
  max-height: 92vh;
  background: var(--bg-card);
  border-radius: 16px;
  border: 1px solid var(--border-base);
  box-shadow: var(--shadow-modal);
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.preview-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 18px 20px;
  border-bottom: 1px solid var(--border-base);

  span {
    color: var(--color-primary);
    font-size: 0.82rem;
    font-weight: 700;
  }

  h2 {
    margin: 4px 0 0;
    color: var(--text-title);
    font-size: 1.05rem;
  }
}

.preview-close {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  border: 1px solid var(--border-base);
  background: var(--bg-hover);
  color: var(--text-secondary);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
}

.preview-body {
  padding: 18px;
  overflow: auto;
  background: var(--bg-main);

  img {
    display: block;
    width: 100%;
    height: auto;
    border-radius: 12px;
  }
}

@include respond(sm) {
  .honors-page {
    padding: 76px 14px 36px;
  }

  .hero-copy {
    padding: 24px;

    h1 {
      font-size: 1.55rem;
    }
  }

  .preview-overlay {
    padding: 12px;
  }
}
</style>
