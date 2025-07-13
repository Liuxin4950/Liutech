<script setup lang="ts">
import { ref } from 'vue';
import TheHeader from '../components/TheHeader.vue';
import TheFooter from '../components/TheFooter.vue';

// 页面标题
const title = ref('我的博客');
</script>

<template>
  <div class="home-container">
    <TheHeader />
    <main class="main-content">
      <section class="hero">
        <h2>欢迎来到{{ title }}</h2>
        <p>这里记录我的学习和生活</p>
      </section>
    </main>
    <TheFooter />
  </div>
</template>

<style lang="scss" scoped>
</style>