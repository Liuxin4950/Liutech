<script setup lang="ts">
import { computed } from 'vue'
import type { PostDetail } from '@/services/post'
import { buildPostPath } from '@/utils/postPath'
import Icon from '@/components/Icon.vue'

const props = withDefaults(defineProps<{
  series: NonNullable<PostDetail['series']>
  items: NonNullable<PostDetail['seriesCatalog']>
  currentPostId: number
  label?: string
  compact?: boolean
}>(), { label: '系列文章导航' })

// 使用文章 ID 定位，切换路由后不沿用上一页的 current 标记。
const currentIndex = computed(() => props.items.findIndex(item => item.id === props.currentPostId))
const previousPost = computed(() => currentIndex.value > 0 ? props.items[currentIndex.value - 1] : null)
const nextPost = computed(() => currentIndex.value >= 0 ? props.items[currentIndex.value + 1] || null : null)
const postLocation = (id: number) => ({
  path: buildPostPath(id),
  query: { from: 'series', seriesId: String(props.series.id) }
})
</script>

<template>
  <section class="series-navigation" :class="{ 'is-compact': compact }" :aria-label="label">
    <div class="series-navigation__header">
      <div class="series-navigation__heading">
        <span class="series-navigation__icon" aria-hidden="true"><Icon name="layers" size="16" /></span>
        <span class="series-navigation__label">{{ compact ? '系列' : '文章系列' }}</span>
        <router-link :to="`/series-detail/${series.id}`" class="series-navigation__name">{{ series.name }}</router-link>
      </div>
      <span class="series-navigation__progress">
        {{ currentIndex >= 0 ? `第 ${currentIndex + 1} / ${items.length} 篇` : `共 ${items.length} 篇` }}
      </span>
    </div>
    <nav class="series-navigation__links" aria-label="切换系列文章">
      <router-link v-if="previousPost" :to="postLocation(previousPost.id)" class="series-navigation__link" :title="previousPost.title">
        <Icon name="chevronLeft" size="16" aria-hidden="true" />
        <span class="series-navigation__text">
          <span class="series-navigation__direction">上一篇</span>
          <span v-if="!compact" class="series-navigation__title">{{ previousPost.title }}</span>
        </span>
      </router-link>
      <span v-else class="series-navigation__link is-disabled" aria-disabled="true" :title="currentIndex === 0 ? '已是第一篇' : '暂无上一篇'">
        <Icon name="chevronLeft" size="16" aria-hidden="true" />
        <span class="series-navigation__direction">{{ compact ? '上一篇' : currentIndex === 0 ? '已是第一篇' : '暂无上一篇' }}</span>
      </span>
      <router-link v-if="nextPost" :to="postLocation(nextPost.id)" class="series-navigation__link is-next" :title="nextPost.title">
        <span class="series-navigation__text">
          <span class="series-navigation__direction">下一篇</span>
          <span v-if="!compact" class="series-navigation__title">{{ nextPost.title }}</span>
        </span>
        <Icon name="chevronRight" size="16" aria-hidden="true" />
      </router-link>
      <span v-else class="series-navigation__link is-next is-disabled" aria-disabled="true" :title="currentIndex >= 0 && currentIndex === items.length - 1 ? '已是最后一篇' : '暂无下一篇'">
        <span class="series-navigation__direction">{{ compact ? '下一篇' : currentIndex >= 0 && currentIndex === items.length - 1 ? '已是最后一篇' : '暂无下一篇' }}</span>
        <Icon name="chevronRight" size="16" aria-hidden="true" />
      </span>
    </nav>
  </section>
</template>

<style scoped>
.series-navigation {
  margin: 22px 0;
  padding: 16px;
  border: 1px solid var(--border-light);
  border-radius: 12px;
  background: var(--bg-soft);
}
.series-navigation__header {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px 12px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--border-light);
}
.series-navigation__heading {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  flex: 1;
  min-width: 0;
}
.series-navigation__icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  flex-shrink: 0;
  border-radius: 8px;
  color: var(--color-primary);
  background: rgba(var(--color-primary-rgb), 0.08);
}
.series-navigation__label {
  font-size: 12px;
  color: var(--text-muted);
}
.series-navigation__name {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-title);
  text-decoration: none;
  overflow-wrap: anywhere;
}
.series-navigation__progress {
  font-size: 12px;
  color: var(--text-subtle);
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}
.series-navigation__links {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  gap: 12px;
  padding-top: 10px;
}
.series-navigation__link {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  min-height: 52px;
  padding: 8px;
  border-radius: 8px;
  color: var(--text-main);
  text-decoration: none;
  transition: background 0.15s, color 0.15s;
}
.series-navigation__link :deep(.icon) { flex-shrink: 0; }
.series-navigation__link:hover { color: var(--color-primary); background: var(--bg-hover); }
.series-navigation__link.is-next { justify-content: flex-end; text-align: right; }
.series-navigation__link.is-disabled { color: var(--text-muted); }
.series-navigation__link.is-disabled:hover { background: transparent; }
.series-navigation__text { display: flex; flex-direction: column; gap: 4px; min-width: 0; }
.series-navigation__direction { font-size: 12px; color: var(--text-muted); }
.series-navigation__title {
  font-size: 13px;
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  overflow-wrap: anywhere;
}
.series-navigation a:focus-visible { outline: 2px solid var(--color-primary); outline-offset: 2px; }
@media (max-width: 480px) {
  .series-navigation { padding: 12px; margin: 18px 0; }
  .series-navigation__header { align-items: flex-start; }
  .series-navigation__heading { gap: 6px; }
  .series-navigation__label { display: none; }
  .series-navigation__progress { padding-top: 5px; }
  .series-navigation__links { gap: 8px; }
  .series-navigation__link { gap: 4px; padding: 6px 2px; }
}

/* 顶部融入文章资料区，只提供轻量切换；完整文章标题留在页尾。 */
.series-navigation.is-compact {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 6px 16px;
  margin: 16px 0 0;
  padding: 0;
  border: 0;
  border-radius: 0;
  background: transparent;
}
.is-compact .series-navigation__header {
  flex: 1;
  min-width: 0;
  gap: 8px 12px;
  padding: 0;
  border: 0;
}
.is-compact .series-navigation__heading { flex: initial; gap: 6px; flex-wrap: nowrap; }
.is-compact .series-navigation__icon { width: auto; height: auto; background: transparent; color: var(--text-muted); }
.is-compact .series-navigation__label { display: inline; }
.is-compact .series-navigation__name { font-size: 12px; font-weight: 500; color: var(--text-subtle); }
.is-compact .series-navigation__progress { padding: 0; }
.is-compact .series-navigation__links { display: flex; gap: 4px; padding: 0; }
.is-compact .series-navigation__link { min-height: 32px; padding: 6px 8px; gap: 4px; white-space: nowrap; }
.is-compact .series-navigation__link:not(.is-disabled) .series-navigation__direction { color: var(--text-subtle); }
.is-compact .series-navigation__link.is-disabled { opacity: 0.5; }
</style>
