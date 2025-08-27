import { createRouter, createWebHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'

/**
 * 路由配置
 * 定义应用的页面路由
 */
const routes: RouteRecordRaw[] = [
  {
    path: '/',
    component: () => import('../layouts/MainLayout.vue'),
    children: [
      {
        path: '',
        name: 'home',
        component: () => import('../views/Home.vue'),
        meta: {
          title: '首页',
          section: 'home'
        }
      },
    ]
  },{
    path: '/login',
    name: 'login',
    component: () => import('../views/Login.vue'),
    meta: {
      title: '登录',
      section: 'login'
    }
  }
]

/**
 * 创建路由实例
 */
const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes,
  linkActiveClass: 'router-link-active',
  linkExactActiveClass: 'router-link-exact-active',
  // 滚动行为配置 - 每次路由跳转都回到页面顶部
  scrollBehavior(to, from, savedPosition) {
    // 如果有保存的滚动位置（浏览器前进后退），则恢复到该位置
    if (savedPosition) {
      return savedPosition
    }
    // 否则滚动到页面顶部
    return { top: 0, behavior: 'smooth' }
  }
})

/**
 * 路由前置守卫
 * 设置页面标题和权限检查
 */
router.beforeEach((to, from, next) => {
  // 设置页面标题
  document.title = `${to.meta.title || '博客'} - MyBlog`
  
  // 需要登录的页面
  const requiresAuth = ['create-post', 'drafts', 'my-posts', 'profile']
  
  // 检查是否需要登录
  if (requiresAuth.includes(to.name as string)) {
    const token = localStorage.getItem('token')
    if (!token) {
      // 未登录，跳转到登录页面
      next({ name: 'login', query: { redirect: to.fullPath } })
      return
    }
  }
  
  next()
})

export default router