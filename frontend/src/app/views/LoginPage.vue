<template>
  <div class="auth-page login-page">
    <CosmicBackground />

    <div class="login-brand-logo" aria-label="考研政治">
      <span class="login-brand-mark" aria-hidden="true">
        <GraduationCap :size="25" :stroke-width="2.4" />
      </span>
      <strong>考研政治</strong>
    </div>

    <section class="auth-card login-card">
      <div class="panel-head compact">
        <div>
          <h2>欢迎回来</h2>
          <p>输入账号和密码即可。</p>
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
        <span>没有账号？</span>
        <div class="support-links">
          <RouterLink to="/register">去注册</RouterLink>
          <RouterLink to="/forgot-password">忘记密码</RouterLink>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { GraduationCap } from 'lucide-vue-next'
import { reactive, ref } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import CosmicBackground from '@/app/components/CosmicBackground.vue'
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
  // 邮箱登录统一转小写，昵称登录保留原样，避免把用户昵称意外改掉。
  const trimmed = account.trim()
  if (trimmed.includes('@')) {
    return trimmed.toLowerCase()
  }
  return trimmed
}

const handleSubmit = async () => {
  // 登录按钮的完整流程：前端基础校验 -> 调 auth store 调后端 -> 成功后跳回原本想访问的页面。
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
    // redirect 来自路由守卫：未登录访问受保护页面时，会被带到 /login?redirect=原地址。
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/dashboard'
    router.push(redirect)
  } catch (err) {
    error.value = err instanceof Error ? err.message : '登录失败'
  } finally {
    loading.value = false
  }
}
</script>
