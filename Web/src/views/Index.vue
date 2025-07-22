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
    <router-view />
    <TheFooter />
  </div>
</template>

<style lang="scss" scoped>
</style>