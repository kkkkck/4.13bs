import { createRouter, createWebHistory } from 'vue-router'
import { pinia } from '@/app/pinia'
import { useAuthStore } from '@/app/stores/auth'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    // public 表示不登录也能访问，主要用于登录、注册、找回密码。
    {
      path: '/login',
      name: 'login',
      component: () => import('@/app/views/LoginPage.vue'),
      meta: { title: '登录', public: true }
    },
    {
      path: '/register',
      name: 'register',
      component: () => import('@/app/views/RegisterPage.vue'),
      meta: { title: '注册', public: true }
    },
    {
      path: '/forgot-password',
      name: 'forgot-password',
      component: () => import('@/app/views/ForgotPasswordPage.vue'),
      meta: { title: '重置密码', public: true }
    },
    {
      path: '/',
      component: () => import('@/app/layouts/ConsoleLayout.vue'),
      meta: { requiresAuth: true },
      children: [
        // 普通用户登录后看到的主功能区：刷题、模考、收藏、错题和个人中心。
        { path: '', redirect: '/dashboard' },
        {
          path: 'dashboard',
          name: 'dashboard',
          component: () => import('@/app/views/DashboardPage.vue'),
          meta: { title: '首页' }
        },
        {
          path: 'categories',
          name: 'categories',
          component: () => import('@/app/views/CategoriesPage.vue'),
          meta: { title: '专题' }
        },
        {
          path: 'practice',
          name: 'practice',
          component: () => import('@/app/views/PracticePage.vue'),
          meta: { title: '练习' }
        },
        {
          path: 'mock-exam',
          name: 'mock-exam',
          component: () => import('@/app/views/MockExamPage.vue'),
          meta: { title: '模考' }
        },
        {
          path: 'favorite',
          name: 'favorite',
          component: () => import('@/app/views/FavoritePage.vue'),
          meta: { title: '收藏' }
        },
        {
          path: 'wrong-book',
          name: 'wrong-book',
          component: () => import('@/app/views/WrongBookPage.vue'),
          meta: { title: '错题' }
        },
        {
          path: 'profile',
          name: 'profile',
          component: () => import('@/app/views/ProfilePage.vue'),
          meta: { title: '我的' }
        }
      ]
    },
    {
      path: '/admin',
      component: () => import('@/app/layouts/AdminLayout.vue'),
      meta: { requiresAuth: true, requiresAdmin: true },
      children: [
        // 后台管理区只允许管理员访问；用户管理页还额外要求超级管理员。
        { path: '', redirect: '/admin/dashboard' },
        {
          path: 'dashboard',
          name: 'admin-dashboard',
          component: () => import('@/app/views/admin/AdminDashboardPage.vue'),
          meta: { title: '总览' }
        },
        {
          path: 'questions',
          name: 'admin-questions',
          component: () => import('@/app/views/admin/AdminQuestionsPage.vue'),
          meta: { title: '题库' }
        },
        {
          path: 'categories',
          name: 'admin-categories',
          component: () => import('@/app/views/admin/AdminCategoriesPage.vue'),
          meta: { title: '专题' }
        },
        {
          path: 'users',
          name: 'admin-users',
          component: () => import('@/app/views/admin/AdminUsersPage.vue'),
          meta: { title: '用户', requiresSuperAdmin: true }
        }
      ]
    },
    {
      path: '/:pathMatch(.*)*',
      name: 'not-found',
      component: () => import('@/app/views/NotFoundPage.vue'),
      meta: { title: '页面不存在', public: true }
    }
  ]
})

router.beforeEach(async (to) => {
  const authStore = useAuthStore(pinia)
  // 刷新浏览器后 Pinia 会丢失内存状态，所以每次路由跳转前先从 localStorage 恢复登录信息。
  authStore.hydrate()

  if (to.meta.title) {
    document.title = `${String(to.meta.title)} | 刷题`
  }

  if (to.meta.public && authStore.isLoggedIn && (to.path === '/login' || to.path === '/register' || to.path === '/forgot-password')) {
    return '/dashboard'
  }

  // 路由守卫的核心逻辑：需要登录但没有 token，就跳回登录页，并记录原本想去的地址。
  if (to.meta.requiresAuth && !authStore.isLoggedIn) {
    return {
      path: '/login',
      query: { redirect: to.fullPath }
    }
  }

  if (to.meta.requiresAdmin && !authStore.isAdmin) {
    try {
      // 本地存的用户信息可能过期，所以访问管理员页面前会向后端重新确认一次身份。
      await authStore.fetchProfile()
    } catch {
      authStore.logout()
      return '/login'
    }

    if (!authStore.isAdmin) {
      return '/dashboard'
    }
  }

  if (to.meta.requiresSuperAdmin && !authStore.isSuperAdmin) {
    try {
      // 超级管理员是 role=1 且 id=1，用于限制最敏感的用户管理功能。
      await authStore.fetchProfile()
    } catch {
      authStore.logout()
      return '/login'
    }

    if (!authStore.isSuperAdmin) {
      return '/admin/dashboard'
    }
  }

  return true
})

export default router
