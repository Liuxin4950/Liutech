<script setup lang="ts">
import TheHeader from '@/components/TheHeader.vue'
import TheFooter from '@/components/TheFooter.vue'

</script>

<template>
  <div class="main-layout">
    <TheHeader />
    <main class="main-content">
      <router-view />
    </main>
    <TheFooter />
    <BottomNavigation></BottomNavigation>
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
.live2d{
  position: fixed;
  bottom: 0;
  right: 0;
  width: 400px;
  height: 400px;
  z-index: 10;
}
</style>