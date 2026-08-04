<template>
  <div class="profile-card card bg-card relative gap-20">
    <div class="flex flex-col flex-ac">
      <div class="avatar-wrapper">
        <img :src="avatar" :alt="name" class="avatar" @error="handleImageError">
      </div>

      <div class="flex flex-col flex-ac">
        <h3 class="font-semibold" style="font-size: 1.125rem; margin-bottom: 2px">{{ name }}</h3>
        <p class="text-muted mb-0" style="font-size: 0.8rem">{{ title }}</p>
      </div>
    </div>

    <div class="profile-bio">{{ bio }}</div>

    <div class="profile-stats flex flex-sb mb-16">
      <div class="flex-1 text-center stat-item">
        <span class="stat-number">{{ stats.posts }}</span>
        <span class="stat-label">文章</span>
      </div>
      <div class="flex-1 text-center stat-item">
        <span class="stat-number">{{ stats.comments }}</span>
        <span class="stat-label">评论</span>
      </div>
      <div class="flex-1 text-center stat-item">
        <span class="stat-number">{{ stats.views }}</span>
        <span class="stat-label">访问</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { getUserStats } from '@/services/user'
import { handleImageError } from '@/composables/useImageFallback'

// 定义props
interface Stats {
  posts: number
  comments: number
  views: number
}

interface Props {
  name: string
  title: string
  avatar: string
  bio: string
  stats: Stats
}


withDefaults(defineProps<Props>(), {
  avatar: '/洛天依.png',
  name: 'Liuxin'
  })
</script>

<style scoped lang="scss">
@use "@/assets/styles/tokens" as *;

.profile-card {
  transition: transform 0.25s ease, box-shadow 0.25s ease;
}

.avatar-wrapper {
  width: 64px;
  height: 64px;
  border-radius: 50%;
  padding: 3px;
  background: linear-gradient(135deg, var(--color-primary) 0%, var(--color-secondary) 100%);
  margin-bottom: 4px;
}

.avatar {
  width: 100%;
  height: 100%;
  border-radius: 50%;
  object-fit: cover;
  border: 3px solid var(--bg-card);
  background: var(--bg-card);
  transition: transform 0.3s ease;
}

.profile-card:hover .avatar {
  transform: scale(1.05);
}

.profile-bio {
  font-size: 0.875rem;
  line-height: 1.6;
  text-align: center;
  color: var(--text-subtle);
}

.profile-stats {
  padding: 12px;
  border-top: 1px solid var(--border-light);
  border-bottom: 1px solid var(--border-light);
}

.profile-card h3 {
  margin-bottom: 4px;
}

.stat-item {
  position: relative;

  &:not(:last-child)::after {
    content: '';
    position: absolute;
    right: 0;
    top: 50%;
    transform: translateY(-50%);
    width: 1px;
    height: 24px;
    background: var(--border-light);
  }
}

.stat-number {
  display: block;
  font-size: 1.125rem;
  font-weight: 700;
  color: var(--text-title);
  line-height: 1.2;
}

.stat-label {
  display: block;
  font-size: 0.75rem;
  color: var(--text-muted);
  margin-top: 2px;
}

</style>
