import { createApp } from 'vue'
import App from './App.vue'
import { setupActivityTracker } from './app/activity-tracker'
import { pinia } from './app/pinia'
import router from './app/router'
import './style.css'

const app = createApp(App)

// 前端启动顺序：
// 1. 挂载 Pinia，让所有页面都能读取登录用户、token 等全局状态。
// 2. 挂载 Router，让地址栏路径能对应到不同页面。
// 3. mount('#app') 把 Vue 应用真正渲染到 index.html 的 <div id="app"> 里。
app.use(pinia)
app.use(router)
app.mount('#app')

// 页面加载完成后再启动学习时长统计；它会监听路由变化并向后端发送心跳。
setupActivityTracker(router)
