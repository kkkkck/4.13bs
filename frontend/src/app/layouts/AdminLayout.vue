<template>
  <div class="shell admin-shell">
    <aside class="shell-sidebar admin-sidebar">
      <div class="sidebar-account-card admin-sidebar-card">
        <div class="sidebar-account-copy">
          <p class="eyebrow">后台</p>
          <strong>后台</strong>
          <span class="record-pill muted">仅管理员可见</span>
        </div>
      </div>

      <div class="nav-group">
        <p class="nav-group-title">导航</p>
        <nav class="nav-list">
          <RouterLink v-for="item in items" :key="item.to" :to="item.to" class="nav-item">
            <div class="nav-item-top">
              <span class="nav-icon">{{ item.icon }}</span>
              <span>{{ item.label }}</span>
            </div>
          </RouterLink>
        </nav>
      </div>
    </aside>

    <main class="shell-main">
      <header class="shell-header">
        <div>
          <p class="eyebrow">页面</p>
          <h2>{{ pageTitle }}</h2>
        </div>

        <div class="header-actions">
          <RouterLink to="/dashboard" class="ghost-btn">前台</RouterLink>
          <button class="ghost-btn" @click="logout">退出登录</button>
        </div>
      </header>

      <section class="shell-content">
        <RouterView />
      </section>
    </main>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink, RouterView, useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/app/stores/auth'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const baseItems = [
  { to: '/dashboard', label: '首页', icon: '首' },
  { to: '/admin/dashboard', label: '总览', icon: '览' },
  { to: '/admin/questions', label: '题库', icon: '题' },
  { to: '/admin/categories', label: '专题', icon: '类' },
  { to: '/admin/users', label: '用户', icon: '人', superAdminOnly: true }
]
const items = computed(() => baseItems.filter((item) => !item.superAdminOnly || authStore.isSuperAdmin))

const pageTitle = computed(() => String(route.meta.title || '后台'))

const logout = () => {
  authStore.logout()
  router.push('/login')
}
</script>
