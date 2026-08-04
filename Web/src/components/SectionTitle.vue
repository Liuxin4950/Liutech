<script setup lang="ts">
interface Props {
  subtitle?: string
  title: string
  highlight?: string
  align?: 'left' | 'center'
}

withDefaults(defineProps<Props>(), {
  subtitle: '',
  highlight: '',
  align: 'center'
})
</script>

<template>
  <div class="page-title" :class="{ 'is-center': align === 'center' }">
    <span v-if="subtitle" class="title-badge">{{ subtitle }}</span>
    <h2 class="title-heading">
      {{ title }}
      <span v-if="highlight" class="title-highlight">{{ highlight }}</span>
    </h2>
  </div>
</template>
