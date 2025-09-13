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
      {
        path: 'post/:id',
        name: 'post-detail',
        component: () => import('../views/PostDetail.vue'),
        meta: {
          title: '文章详情',
          section: 'home'
        }
      },
      {
        path: 'create',
        name: 'create-post',
        component: () => import('../views/CreatePost.vue'),
        meta: {
          title: '发布文章'
        }
      },
      {
        path: 'drafts',
        name: 'drafts',
        component: () => import('../views/Drafts.vue'),
        meta: {
          title: '草稿箱'
        }
      },
      {
        path: 'my-posts',
        name: 'my-posts',
        component: () => import('../views/MyPosts.vue'),
        meta: {
          title: '我的文章'
        }
      },
      {
        path: 'posts',
        name: 'posts',
        component: () => import('../views/Posts.vue'),
        meta: {
          title: '全部文章',
          section: 'home'
        }
      },
      {
        path: 'categories',
        name: 'category-list',
        component: () => import('../views/Categories.vue'),
        meta: {
          title: '分类',
          section: 'categories'
        }
      },
      {
        path: 'category-detail/:id',
        name: 'category-detail',
        component: () => import('../views/CategoryDetail.vue'),
        meta: {
          title: '分类详情',
          section: 'categories'
        }
      },
      {
        path: 'tags',
        name: 'tags',
        component: () => import('../views/Tags.vue'),
        meta: {
          title: '标签',
          section: 'tags'
        }
      },
      {
        path: 'tags/:id',
        name: 'tag-detail',
        component: () => import('../views/TagDetail.vue'),
        meta: {
          title: '标签详情',
          section: 'tags'
        }
      },
      {
        path: 'archive',
        name: 'archive',
        component: () => import('../views/Archive.vue'),
        meta: {
          title: '文章归档',
          section: 'archive'
        }
      },
      {
        path: 'profile',
        name: 'profile',
        component: () => import('../views/Profile.vue'),
        meta: {
          title: '个人资料'
        }
      },
      {
        path: 'about',
        name: 'about',
        component: () => import('../views/About.vue'),
        meta: {
          title: '关于我',
          section: 'about'
        }
      },
      {
        path: 'chat-history',
        name: 'chat-history',
        component: () => import('../components/ChatHistory.vue'),
        meta: {
          title: '聊天历史记录'
        }
      }
    ]
  },
  {
    path: '/login',
    name: 'login',
    component: () => import('../views/Login.vue'),
    meta: {
      title: '登录'
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
  const requiresAuth = ['create-post', 'drafts', 'my-posts', 'profile', 'chat-history']
  
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