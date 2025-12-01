import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/LoginView.vue'),
      meta: { requiresAuth: false }
    },
    {
      path: '/register',
      name: 'register',
      component: () => import('@/views/RegisterView.vue'),
      meta: { requiresAuth: false }
    },
    {
      path: '/',
      name: 'layout',
      component: () => import('@/views/LayoutView.vue'),
      meta: { requiresAuth: true },
      redirect: '/chat',
      children: [
        {
          path: 'chat',
          name: 'chat',
          component: () => import('@/views/ChatView.vue')
        },
        {
          path: 'nocode',
          name: 'nocode',
          component: () => import('@/views/NoCodeView.vue')
        },
        {
          path: 'knowledge',
          name: 'knowledge',
          component: () => import('@/views/KnowledgeView.vue')
        },
        {
          path: 'agents',
          name: 'agents',
          component: () => import('@/views/AgentsView.vue')
        },
        {
          path: 'polyp',
          name: 'polyp',
          component: () => import('@/views/PolypSegmentationView.vue')
        },
        {
          path: 'profile',
          name: 'profile',
          component: () => import('@/views/ProfileView.vue')
        }
      ]
    }
  ]
})

// 路由守卫
router.beforeEach((to, from, next) => {
  const authStore = useAuthStore()
  
  // 如果需要认证但未登录，跳转到登录页
  if (to.meta.requiresAuth && !authStore.isAuthenticated) {
    next({ name: 'login' })
  } 
  // 如果已登录访问登录/注册页，跳转到聊天页
  else if ((to.name === 'login' || to.name === 'register') && authStore.isAuthenticated) {
    next({ name: 'chat' })
  } 
  else {
    next()
  }
})

export default router

