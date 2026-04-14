<template>
  <div class="auth-page">
    <section class="auth-hero">
      <p class="eyebrow">登录学习系统</p>
      <h1>继续你上一次的学习节奏，把专题、章节、模拟考试串成一条稳定的复习路线。</h1>
      <p>支持用户名或邮箱直接登录，登录后可直接回到学习台。</p>

      <div class="auth-list">
        <span class="tag">专题练习</span>
        <span class="tag">章节练习</span>
        <span class="tag">模拟考试</span>
        <span class="tag">错题复盘</span>
        <span class="tag">后台管理</span>
      </div>
    </section>

    <section class="auth-card">
      <div class="panel-head compact">
        <div>
          <h2>欢迎回来</h2>
          <p>输入用户名或邮箱，快速回到练习状态。</p>
        </div>
      </div>

      <form class="auth-form" @submit.prevent="handleSubmit">
        <label class="field-block">
          <span>账号</span>
          <input
            v-model.trim="form.account"
            class="input"
            type="text"
            placeholder="用户名或邮箱"
            autocomplete="username"
          />
        </label>

        <label class="field-block">
          <span>密码</span>
          <div class="password-field">
            <input
              v-model="form.password"
              class="input"
              :type="passwordVisible ? 'text' : 'password'"
              placeholder="请输入密码"
              autocomplete="current-password"
            />
            <button class="ghost-btn small password-toggle" type="button" @click="passwordVisible = !passwordVisible">
              {{ passwordVisible ? '隐藏' : '显示' }}
            </button>
          </div>
        </label>

        <p v-if="error" class="form-error">{{ error }}</p>

        <button class="primary-btn" :disabled="loading" type="submit">
          {{ loading ? '登录中...' : '登录' }}
        </button>
      </form>

      <div class="support-line">
        <span>还没有账号？</span>
        <RouterLink to="/register">立即注册</RouterLink>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/app/stores/auth'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()

const form = reactive({
  account: '',
  password: ''
})

const loading = ref(false)
const error = ref('')
const passwordVisible = ref(false)

const normalizeAccount = (account: string) => {
  const trimmed = account.trim()
  if (trimmed.includes('@')) {
    return trimmed.toLowerCase()
  }
  return trimmed
}

const handleSubmit = async () => {
  error.value = ''

  if (!form.account.trim() || !form.password) {
    error.value = '请完整填写账号和密码'
    return
  }

  if (form.password.length < 6) {
    error.value = '密码长度至少 6 位'
    return
  }

  loading.value = true
  try {
    await authStore.login({
      account: normalizeAccount(form.account),
      password: form.password
    })
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/dashboard'
    router.push(redirect)
  } catch (err) {
    error.value = err instanceof Error ? err.message : '登录失败'
  } finally {
    loading.value = false
  }
}
</script>
