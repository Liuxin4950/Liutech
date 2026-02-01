import { createRouter, createWebHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'
import { useTagsStore } from '@/stores/tabs'

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
          section: 'home',
          affix: true  // 固定标签，不可关闭
        }
      },
      {
        path: 'posts',
        name: 'posts-management',
        component: () => import('../views/admin/PostsManagement.vue'),
        meta: {
          title: '文章管理',
          section: 'posts'
        }
      },
      {
        path: 'categories',
        name: 'categories-management',
        component: () => import('../views/admin/CategoriesManagement.vue'),
        meta: {
          title: '分类管理',
          section: 'categories'
        }
      },
      {
        path: 'tags',
        name: 'tags-management',
        component: () => import('../views/admin/TagsManagement.vue'),
        meta: {
          title: '标签管理',
          section: 'tags'
        }
      },
      {
        path: 'users',
        name: 'users-management',
        component: () => import('../views/admin/UsersManagement.vue'),
        meta: {
          title: '用户管理',
          section: 'users'
        }
      },
      {
        path: 'announcements',
        name: 'announcements-management',
        component: () => import('../views/admin/AnnouncementsManagement.vue'),
        meta: {
          title: '公告管理',
          section: 'announcements'
        }
      },
      {
        path: 'carousels',
        name: 'carousels-management',
        component: () => import('../views/admin/CarouselsManagement.vue'),
        meta: {
          title: '轮播图管理',
          section: 'carousels'
        }
      },
      {
        path: 'logs',
        name: 'logs-management',
        component: () => import('../views/admin/LogsManagement.vue'),
        meta: {
          title: '操作日志',
          section: 'logs'
        }
      },
      {
        path: 'music',
        name: 'music-management',
        component: () => import('../views/admin/MusicManagement.vue'),
        meta: {
          title: 'AI音乐管理',
          section: 'music'
        }
      },
      {
        path: 'ai-models',
        name: 'ai-models-management',
        component: () => import('../views/admin/AiModelsManagement.vue'),
        meta: {
          title: 'AI模型管理',
          section: 'ai-models'
        }
      },
      {
        path: 'tts',
        name: 'tts-settings',
        component: () => import('../views/admin/TtsSettings.vue'),
        meta: {
          title: '语音推理配置',
          section: 'tts'
        }
      },
      {
        path: 'messages',
        name: 'messages-management',
        component: () => import('../views/admin/MessagesManagement.vue'),
        meta: {
          title: '留言管理',
          section: 'messages'
        }
      }
    ]
  },{
    path: '/login',
    name: 'login',
    component: () => import('../views/Login.vue'),
    meta: {
      title: '登录',
      section: 'login'
    }
  },{
    path: '/403',
    name: 'forbidden',
    component: () => import('../views/403.vue'),
    meta: {
      title: '权限不足',
      section: 'forbidden'
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

/**
 * 路由后置守卫
 * 添加标签页
 */
router.afterEach((to) => {
  // 跳过登录页和 403 页
  if (to.name === 'login' || to.name === 'forbidden') {
    return
  }

  const tagsStore = useTagsStore()

  // 添加到标签栏
  if (to.name && to.meta?.title) {
    tagsStore.addVisitedView({
      name: to.name as string,
      path: to.path,
      title: to.meta.title as string,
      affix: ((to.meta as { affix?: boolean }).affix ?? false),
      fullPath: to.fullPath
    })

    // 添加到缓存
    tagsStore.addCachedView(to.name as string)
  }
})

export default router
