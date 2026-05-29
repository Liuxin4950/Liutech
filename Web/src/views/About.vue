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
          我是刘鑫，全栈开发者，前端、后端、AI 都在做。这个博客记录我学到的东西和正在走的路。
        </p>
      </section>

      <section class="honors-spotlight">
        <div class="honors-art">
          <img :src="aboutHonorsImg" alt="证书与奖杯插画" loading="lazy" @error="handleImageError">
        </div>
        <div class="honors-copy">
          <h2>荣誉与证书</h2>
          <p>软件开发、软件测试、小程序、低代码和技术认证相关证书。</p>
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
  .contact-section {
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
