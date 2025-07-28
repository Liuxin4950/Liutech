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
          title: '首页'
        }
      },
      {
        path: 'post/:id',
        name: 'post-detail',
        component: () => import('../views/PostDetail.vue'),
        meta: {
          title: '文章详情'
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
  {
    path: '/category/:id',
    name: 'CategoryPosts',
    component: () => import('@/views/CategoryPosts.vue')
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