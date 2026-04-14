<template>
  <div class="admin-layout">
    <aside class="sidebar">
      <div class="logo">
        <h1>管理员后台</h1>
      </div>
      <nav class="nav-menu">
        <div 
          class="nav-item" 
          :class="{ active: currentPath === '/admin/dashboard' }"
          @click="navigate('/admin/dashboard')"
        >
          <span class="nav-icon">📊</span>
          <span class="nav-text">数据统计</span>
        </div>
        <div 
          class="nav-item" 
          :class="{ active: currentPath === '/admin/questions' }"
          @click="navigate('/admin/questions')"
        >
          <span class="nav-icon">📚</span>
          <span class="nav-text">题库管理</span>
        </div>
        <div 
          class="nav-item" 
          :class="{ active: currentPath === '/admin/categories' }"
          @click="navigate('/admin/categories')"
        >
          <span class="nav-icon">📁</span>
          <span class="nav-text">分类管理</span>
        </div>
      </nav>
      <div class="logout" @click="logout">
        <span class="nav-icon">🚪</span>
        <span class="nav-text">退出登录</span>
      </div>
    </aside>
    
    <main class="main-content">
      <header class="header">
        <h2>{{ pageTitle }}</h2>
        <div class="user-info">
          <span>管理员</span>
        </div>
      </header>
      <div class="content">
        <router-view />
      </div>
    </main>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'

const router = useRouter()
const route = useRoute()

const currentPath = ref('')

const pageTitle = computed(() => {
  const titles = {
    '/admin/dashboard': '数据统计',
    '/admin/questions': '题库管理',
    '/admin/categories': '分类管理'
  }
  return titles[currentPath.value] || '管理员后台'
})

const navigate = (path) => {
  currentPath.value = path
  router.push(path)
}

const logout = () => {
  localStorage.removeItem('token')
  localStorage.removeItem('role')
  router.push('/login')
}

onMounted(() => {
  currentPath.value = route.path
})
</script>

<style scoped>
.admin-layout {
  display: flex;
  height: 100vh;
  background: #f5f5f5;
}

.sidebar {
  width: 250px;
  background: linear-gradient(180deg, #1a1a2e 0%, #16213e 100%);
  color: white;
  display: flex;
  flex-direction: column;
}

.logo {
  padding: 20px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
}

.logo h1 {
  margin: 0;
  font-size: 20px;
  font-weight: bold;
}

.nav-menu {
  flex: 1;
  padding: 20px 0;
}

.nav-item {
  display: flex;
  align-items: center;
  padding: 15px 25px;
  cursor: pointer;
  transition: background 0.3s;
}

.nav-item:hover {
  background: rgba(255, 255, 255, 0.1);
}

.nav-item.active {
  background: #007bff;
}

.nav-icon {
  font-size: 20px;
  margin-right: 12px;
}

.nav-text {
  font-size: 16px;
}

.logout {
  display: flex;
  align-items: center;
  padding: 15px 25px;
  cursor: pointer;
  border-top: 1px solid rgba(255, 255, 255, 0.1);
  color: #ff6b6b;
  transition: background 0.3s;
}

.logout:hover {
  background: rgba(255, 255, 255, 0.1);
}

.main-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: auto;
}

.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 20px 30px;
  background: white;
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.1);
}

.header h2 {
  margin: 0;
  color: #333;
}

.user-info {
  font-size: 16px;
  color: #666;
}

.content {
  padding: 30px;
}
</style>
