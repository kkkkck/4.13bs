<template>
  <div class="shell admin-shell">
    <aside class="shell-sidebar admin-sidebar">
      <div class="brand-block">
        <p class="eyebrow">运营后台</p>
        <h1>管理后台</h1>
        <p class="brand-copy">
          题库、专题、用户和核心数据都集中在这里维护，后台不是展示页，而是实际可用的工作台。
        </p>
      </div>

      <div class="nav-group">
        <p class="nav-group-title">后台导航</p>
        <nav class="nav-list">
          <RouterLink v-for="item in items" :key="item.to" :to="item.to" class="nav-item">
            <div class="nav-item-top">
              <span class="nav-icon">{{ item.icon }}</span>
              <span>{{ item.label }}</span>
            </div>
            <small>{{ item.note }}</small>
          </RouterLink>
        </nav>
      </div>

      <div class="sidebar-card">
        <span class="card-title">管理建议</span>
        <strong>先看趋势，再做修改</strong>
        <small>优先根据题型、专题和用户活跃度判断下一步维护动作，而不是盲目逐页修改。</small>
      </div>
    </aside>

    <main class="shell-main">
      <header class="shell-header">
        <div>
          <p class="eyebrow">当前页面</p>
          <h2>{{ pageTitle }}</h2>
        </div>

        <div class="header-actions">
          <RouterLink to="/dashboard" class="ghost-btn">返回前台</RouterLink>
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
  { to: '/dashboard', label: '学习总览', note: '回到学习台继续刷题与复盘', icon: '学' },
  { to: '/admin/dashboard', label: '后台总览', note: '看题库、用户与活跃度全貌', icon: '览' },
  { to: '/admin/questions', label: '题库管理', note: '维护题目、题型、状态与导入', icon: '题' },
  { to: '/admin/categories', label: '专题管理', note: '配置专题、章节与训练模式', icon: '类' },
  { to: '/admin/users', label: '用户管理', note: '查看活跃度并维护账号状态', icon: '人', superAdminOnly: true }
]
const items = computed(() => baseItems.filter((item) => !item.superAdminOnly || authStore.isSuperAdmin))

const pageTitle = computed(() => String(route.meta.title || '管理后台'))

const logout = () => {
  authStore.logout()
  router.push('/login')
}
</script>
