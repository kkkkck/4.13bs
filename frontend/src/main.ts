import { createApp } from 'vue'
import App from './App.vue'
import { setupActivityTracker } from './app/activity-tracker'
import { pinia } from './app/pinia'
import router from './app/router'
import './style.css'

const app = createApp(App)

app.use(pinia)
app.use(router)
app.mount('#app')

setupActivityTracker(router)
