// src/router/index.js
import { createRouter, createWebHistory } from 'vue-router';

const routes = [

];

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes,
});
router.beforeEach((to, from, next) => {
  // 每次路由变化时，滚动到页面顶部
  window.scrollTo(0, 0);
  next();
});

export default router;
