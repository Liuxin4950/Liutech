<template>
  <div class="card bg-card ">
    <h4 class="card-title">🔗 友情链接</h4>
    <div class="list gap-8">
      <a 
        v-for="link in links" 
        :key="link.id" 
        :href="link.url" 
        target="_blank" 
        class="flex flex-ac gap-8 p-8 rounded transition hover-bg link"
      >
        <span class="text-lg">{{ link.icon }}</span>
        <span class="text-lg font-medium">{{ link.text }}</span>
      </a>
    </div>
  </div>
</template>

<script setup lang="ts">

// 定义props
interface Link {
  id: number
  url: string
  icon: string
  text: string
}

interface Props {
  links: Link[]
}

const { links } = withDefaults(defineProps<Props>(), {
  links: () => [
    {
      id: 1,
      url: 'https://github.com',
      icon: '🐙',
      text: 'GitHub'
    },
    {
      id: 2,
      url: 'https://vue.js.org',
      icon: '💚',
      text: 'Vue.js'
    },
    {
      id: 3,
      url: 'https://spring.io',
      icon: '🍃',
      text: 'Spring'
    }
  ]
})
</script>

<style scoped>
/* 使用全局样式，这里只定义组件特有的样式 */
</style>