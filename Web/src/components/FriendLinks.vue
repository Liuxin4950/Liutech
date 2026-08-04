<template>
  <div class="card bg-card">
    <h4 class="card-title"><span class="card-badge"><Icon name="link" size="12" /> Links</span><span class="card-title-text">友情<span class="card-highlight">链接</span></span></h4>
    <div class="list gap-8">
      <a
          v-for="link in links"
          :key="link.id"
          :href="link.url"
          class="list-item flex flex-ac gap-8 link transition"
          target="_blank"
      >
        <Icon :name="link.iconName" size="18" />
        <span class="text-lg font-medium">{{ link.text }}</span>
      </a>
    </div>
  </div>
</template>

<script setup lang="ts">
import Icon from './Icon.vue'

interface Link {
  id: number
  url: string
  iconName: string
  text: string
}

interface Props {
  links?: Link[]
}

const props = withDefaults(defineProps<Props>(), {
  links: () => [
    { id: 1, url: 'https://github.com', iconName: 'github', text: 'GitHub' },
    { id: 2, url: 'https://vue.js.org', iconName: 'vue', text: 'Vue.js' },
    { id: 3, url: 'https://spring.io', iconName: 'spring', text: 'Spring' },
  ],
})

const { links } = props
</script>

<style scoped>
</style>
