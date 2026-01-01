<template>
  <div class="card bg-card">
    <h4 class="card-title"><Icon name="link" size="18" /> 友情链接</h4>
    <div class="list gap-8">
      <a
          v-for="link in links"
          :key="link.id"
          :href="link.url"
          class="flex flex-ac gap-8 p-8 rounded transition hover-bg link"
          target="_blank"
      >
        <Icon :name="link.iconName" size="18" />
        <span class="text-lg font-medium">{{ link.text }}</span>
      </a>
    </div>
  </div>
</template>

<script setup lang="ts">
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
