<template>
  <div class="profile-card card">
    <div class="profile-header">
      <div class="avatar-wrapper">
        <img :src="avatar" :alt="name" class="avatar" @error="handleImageError">
      </div>

      <div class="profile-identity">
        <h3 class="profile-name">{{ name }}</h3>
        <p class="profile-title">{{ title }}</p>
      </div>
    </div>

    <p class="profile-bio">{{ bio }}</p>

    <dl class="profile-stats" aria-label="博客统计">
      <div class="stat-item">
        <dt class="stat-label">文章</dt>
        <dd class="stat-number">{{ stats.posts }}</dd>
      </div>
      <div class="stat-item">
        <dt class="stat-label">评论</dt>
        <dd class="stat-number">{{ stats.comments }}</dd>
      </div>
      <div class="stat-item">
        <dt class="stat-label">访问</dt>
        <dd class="stat-number">{{ stats.views }}</dd>
      </div>
    </dl>
  </div>
</template>

<script setup lang="ts">
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
  display: flex;
  flex-direction: column;
  gap: $gap-md;
  padding: 20px;
}

.profile-header {
  display: flex;
  align-items: center;
  gap: $gap-sm;
  min-width: 0;
}

.avatar-wrapper {
  width: 56px;
  height: 56px;
  flex-shrink: 0;
  border-radius: 50%;
  padding: 2px;
  background: var(--state-primary-bg);
  border: 1px solid var(--state-primary-border);
}

.avatar {
  display: block;
  width: 100%;
  height: 100%;
  border-radius: 50%;
  object-fit: cover;
  border: 2px solid var(--bg-card);
  background: var(--bg-card);
}

.profile-identity {
  min-width: 0;
}

.profile-name {
  margin: 0;
  font-size: 1.125rem;
  font-weight: 700;
  line-height: 1.4;
  color: var(--text-title);
  overflow-wrap: anywhere;
}

.profile-title {
  margin: 3px 0 0;
  font-size: 0.75rem;
  line-height: 1.5;
  color: var(--text-subtle);
  overflow-wrap: anywhere;
}

.profile-bio {
  margin: 0;
  font-size: 0.8125rem;
  line-height: 1.75;
  color: var(--text-subtle);
  overflow-wrap: anywhere;
}

.profile-stats {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
  margin: 0;
  padding: 14px 8px;
  background: var(--bg-element);
  border-radius: 10px;
}

.stat-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  min-width: 0;
  text-align: center;
}

.stat-number {
  order: -1;
  margin: 0;
  max-width: 100%;
  font-size: 1.375rem;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
  color: var(--text-title);
  line-height: 1.3;
  overflow-wrap: anywhere;
}

.stat-label {
  font-size: 0.75rem;
  line-height: 1.5;
  color: var(--text-subtle);
}
</style>
