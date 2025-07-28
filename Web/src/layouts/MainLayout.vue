<script setup lang="ts">
import TheHeader from '../components/TheHeader.vue'
import TheFooter from '../components/TheFooter.vue'
import Banner from '@/components/Banner.vue'
import Breadcrumb from '@/components/Breadcrumb.vue'
</script>

<template>
  <div class="main-layout">
    <TheHeader />
    <main class="main-content">
      <Banner class="banner" />
      <Breadcrumb />
      <router-view />
    </main>
    <TheFooter />
  </div>
</template>

<style scoped>
.main-layout {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}

.main-content {
  flex: 1;
}
.banner{
  height: 300px;
}
</style>