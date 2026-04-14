<template>
  <div class="shell">
    <aside class="shell-sidebar">
      <div class="brand-block">
        <p class="eyebrow">学习控制台</p>
        <h1>考研政治刷题台</h1>
        <p class="brand-copy">
          把专题练习、章节推进、模拟考试、错题复盘和数据总结串成一条完整的学习链路。
        </p>
      </div>

      <div class="nav-group">
        <p class="nav-group-title">学习导航</p>
        <nav class="nav-list">
          <RouterLink v-for="item in items" :key="item.key" :to="item.to" class="nav-item">
            <div class="nav-item-top">
              <span class="nav-icon">{{ item.icon }}</span>
              <span>{{ item.label }}</span>
            </div>
            <small>{{ item.note }}</small>
          </RouterLink>
        </nav>
      </div>

      <div class="sidebar-card">
        <span class="card-title">推荐节奏</span>
        <strong>专题推进 → 模拟检验 → 错题回补</strong>
        <small>先建立章节覆盖，再用整卷检查稳定度，最后针对薄弱点快速回补。</small>
      </div>
    </aside>

    <main class="shell-main">
      <header class="shell-header">
        <div>
          <p class="eyebrow">当前页面</p>
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
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink, RouterView, useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/app/stores/auth'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const items = [
  { key: 'categories', to: '/categories', label: '专题练习 / 快速开始', note: '直接进入专题或章节训练入口', icon: '练' },
  { key: 'mock-exam', to: '/mock-exam', label: '模拟考试', note: '按专题与章节比例组卷', icon: '模' },
  { key: 'wrong-book', to: '/wrong-book', label: '错题本', note: '集中处理高频失分点', icon: '错' },
  { key: 'favorite', to: '/favorite', label: '收藏夹', note: '沉淀重点题与经典题', icon: '藏' },
  { key: 'profile', to: '/profile', label: '个人中心', note: '查看趋势、历史与复盘数据', icon: '我' }
]

const pageTitle = computed(() => String(route.meta.title || '考研政治刷题台'))

const logout = () => {
  authStore.logout()
  router.push('/login')
}
</script>
