import { createRouter, createWebHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'

/**
 * 路由配置
 * 定义应用的页面路由
 */
const routes: RouteRecordRaw[] = [
  {
    path: '/',
    name: 'home',
    component: () => import('../views/Home.vue'),
    meta: {
      title: '首页'
    }
  },
  // 404页面
  {
    path: '/:pathMatch(.*)*',
    name: 'not-found',
    redirect: '/'
  }
]

/**
 * 创建路由实例
 */
const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes
})

/**
 * 路由前置守卫
 * 设置页面标题
 */
// router.beforeEach((to, from, next) => {
//   // 设置页面标题
//   document.title = `${to.meta.title || '博客'} - MyBlog`
//   next()
// })

export default router