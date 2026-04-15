<template>
  <div class="shell">
    <aside class="shell-sidebar">
      <div class="sidebar-account-card">
        <button class="sidebar-avatar-button" type="button" @click="settingsOpen = true">
          <UserAvatar :avatar-url="authStore.user?.avatarUrl" :nickname="authStore.user?.nickname" :size="76" />
        </button>
        <div class="sidebar-account-copy">
          <p class="eyebrow">账号</p>
          <strong>{{ authStore.user?.nickname || '学习者' }}</strong>
          <small v-if="authStore.user?.email">{{ authStore.user?.email }}</small>
          <span class="record-pill muted">{{ authStore.isAdmin ? '管理员' : '普通用户' }}</span>
        </div>
        <div class="row-actions">
          <button class="ghost-btn small" type="button" @click="settingsOpen = true">账号设置</button>
        </div>
      </div>

      <div class="nav-group">
        <p class="nav-group-title">导航</p>
        <nav class="nav-list">
          <RouterLink v-for="item in items" :key="item.key" :to="item.to" class="nav-item">
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
          <RouterLink v-if="authStore.isAdmin" to="/admin/dashboard" class="ghost-btn">
            进入后台
          </RouterLink>
          <button class="ghost-btn" @click="logout">退出登录</button>
        </div>
      </header>

      <section class="shell-content">
        <RouterView />
      </section>
    </main>

    <UserSettingsModal v-if="authStore.user" :open="settingsOpen" @close="settingsOpen = false" />
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { RouterLink, RouterView, useRoute, useRouter } from 'vue-router'
import UserAvatar from '@/app/components/UserAvatar.vue'
import UserSettingsModal from '@/app/components/UserSettingsModal.vue'
import { useAuthStore } from '@/app/stores/auth'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const settingsOpen = ref(false)

const items = [
  { key: 'categories', to: '/categories', label: '专题', icon: '专' },
  { key: 'mock-exam', to: '/mock-exam', label: '模考', icon: '模' },
  { key: 'wrong-book', to: '/wrong-book', label: '错题', icon: '错' },
  { key: 'favorite', to: '/favorite', label: '收藏', icon: '藏' },
  { key: 'profile', to: '/profile', label: '我的', icon: '我' }
]

const pageTitle = computed(() => String(route.meta.title || '首页'))

const logout = () => {
  authStore.logout()
  router.push('/login')
}
</script>
