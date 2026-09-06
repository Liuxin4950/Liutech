<script setup lang="ts">
import type { PostDetail } from '@/services/post'
defineProps<{ series: NonNullable<PostDetail['series']>; items: NonNullable<PostDetail['seriesCatalog']> }>()
</script>

<template>
  <section class="series-catalog" aria-label="所属系列">
    <router-link class="series-heading" :to="`/series-detail/${series.id}`">{{ series.name }}</router-link>
    <ol>
      <li v-for="(item, index) in items" :key="item.id">
        <router-link :to="`/post/${item.id}`" :aria-current="item.current ? 'page' : undefined" :title="item.title">
          <span class="number">{{ index + 1 }}</span><span class="title">{{ item.title }}</span>
        </router-link>
      </li>
    </ol>
  </section>
</template>

<style scoped>
.series-catalog { border-top: 1px solid var(--border-light); padding: 12px 8px 4px; }
.series-heading { display: block; padding: 0 8px 8px; color: var(--text-title); font-size: 13px; font-weight: 600; overflow-wrap: anywhere; }
ol { list-style: none; margin: 0; padding: 0; }
li a { display: flex; gap: 8px; padding: 9px 8px; min-height: 36px; color: var(--text-subtle); font-size: 12px; line-height: 1.5; border-radius: 8px; text-decoration: none; }
li a:hover, li a[aria-current] { color: var(--color-primary); background: var(--bg-hover); }
.number { flex: 0 0 18px; font-variant-numeric: tabular-nums; }
.title { overflow-wrap: anywhere; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; }
a:focus-visible { outline: 2px solid var(--color-primary); outline-offset: -2px; }
</style>
