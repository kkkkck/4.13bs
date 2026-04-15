<template>
  <div class="auth-page">
    <section class="auth-hero">
      <p class="eyebrow">注册</p>
      <h1>先注册，再开始做题。</h1>
      <p>用邮箱验证码完成注册。</p>

      <div class="auth-list">
        <span class="tag">邮箱验证码</span>
        <span class="tag">5 分钟有效</span>
        <span class="tag">60 秒后可重发</span>
        <span class="tag">注册后自动登录</span>
      </div>
    </section>

    <section class="auth-card">
      <div class="panel-head compact">
        <div>
          <h2>注册</h2>
          <p>注册后会自动登录。</p>
        </div>
      </div>

      <form class="auth-form" @submit.prevent="handleSubmit">
        <label class="field-block">
          <span>用户名</span>
          <input v-model.trim="form.nickname" class="input" type="text" placeholder="请输入登录用户名" />
          <small class="form-tip">用户名需保持唯一，后续可直接拿来登录。</small>
        </label>

        <label class="field-block">
          <span>邮箱</span>
          <input v-model.trim="form.email" class="input" type="email" placeholder="name@example.com" />
          <small class="form-tip">邮箱会自动转成小写。</small>
        </label>

        <label class="field-block">
          <span>密码</span>
          <div class="password-field">
            <input
              v-model="form.password"
              class="input"
              :type="passwordVisible ? 'text' : 'password'"
              placeholder="6-32 位密码"
              autocomplete="new-password"
              maxlength="32"
            />
            <button class="ghost-btn small password-toggle" type="button" @click="passwordVisible = !passwordVisible">
              {{ passwordVisible ? '隐藏' : '显示' }}
            </button>
          </div>
        </label>

        <label class="field-block">
          <span>验证码</span>
          <div class="inline-form">
            <input v-model.trim="form.code" class="input" type="text" maxlength="6" placeholder="6 位验证码" />
            <button class="ghost-btn" type="button" :disabled="!canSendCode" @click="handleSendCode">
              {{ countdown > 0 ? `${countdown}s` : sendingCode ? '发送中...' : '发送验证码' }}
            </button>
          </div>
        </label>

        <div class="auth-status-grid">
          <article class="auth-status-card">
            <span>发送状态</span>
            <strong>{{ deliveryStatus }}</strong>
          </article>
          <article class="auth-status-card">
            <span>验证码时效</span>
            <strong>{{ expiresText }}</strong>
          </article>
        </div>

        <div v-if="debugCode" class="dev-code-card">
          <strong>开发环境验证码</strong>
          <div class="dev-code-row">
            <span>{{ debugCode }}</span>
            <button class="ghost-btn small" type="button" @click="fillDebugCode">填入验证码</button>
          </div>
          <small>当前环境未启用真实邮箱发送，因此会直接展示验证码。</small>
        </div>

        <p v-if="success" class="form-success">{{ success }}</p>
        <p v-if="error" class="form-error">{{ error }}</p>

        <button class="primary-btn" :disabled="loading" type="submit">
          {{ loading ? '提交中...' : '注册并登录' }}
        </button>
      </form>

      <div class="support-line">
        <span>已经有账号？</span>
        <RouterLink to="/login">去登录</RouterLink>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { login, register, sendCode } from '@/app/api/auth'
import { useAuthStore } from '@/app/stores/auth'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()

const form = reactive({
  nickname: '',
  email: '',
  password: '',
  code: ''
})

const loading = ref(false)
const sendingCode = ref(false)
const countdown = ref(0)
const error = ref('')
const success = ref('')
const debugCode = ref('')
const expiresInSeconds = ref(300)
const mailEnabled = ref(false)
const passwordVisible = ref(false)

let timer: number | null = null

const normalizedEmail = computed(() => form.email.trim().toLowerCase())
const normalizedNickname = computed(() => form.nickname.trim())
const canSendCode = computed(() => !sendingCode.value && countdown.value === 0 && isEmailValid(normalizedEmail.value))
const deliveryStatus = computed(() =>
  mailEnabled.value ? '验证码将发送到你的邮箱' : '当前环境会展示调试验证码'
)
const expiresText = computed(() => `${Math.max(1, Math.round(expiresInSeconds.value / 60))} 分钟内有效`)

const isEmailValid = (email: string) => /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)

const startCountdown = () => {
  if (timer) {
    window.clearInterval(timer)
    timer = null
  }

  countdown.value = 60
  timer = window.setInterval(() => {
    countdown.value -= 1
    if (countdown.value <= 0 && timer) {
      window.clearInterval(timer)
      timer = null
    }
  }, 1000)
}

const fillDebugCode = () => {
  form.code = debugCode.value
}

const handleSendCode = async () => {
  error.value = ''
  success.value = ''
  debugCode.value = ''

  if (!normalizedEmail.value) {
    error.value = '请先输入邮箱'
    return
  }

  if (!isEmailValid(normalizedEmail.value)) {
    error.value = '请输入正确的邮箱格式'
    return
  }

  sendingCode.value = true
  try {
    const result = await sendCode(normalizedEmail.value)
    success.value = result.message || '验证码已发送'
    debugCode.value = result.debugCode || ''
    expiresInSeconds.value = result.expiresInSeconds || 300
    mailEnabled.value = Boolean(result.mailEnabled)
    if (debugCode.value) {
      form.code = debugCode.value
    }
    startCountdown()
  } catch (err) {
    error.value = err instanceof Error ? err.message : '验证码发送失败'
  } finally {
    sendingCode.value = false
  }
}

const handleSubmit = async () => {
  error.value = ''
  success.value = ''

  if (!normalizedNickname.value || !normalizedEmail.value || !form.password || !form.code.trim()) {
    error.value = '请填写完整注册信息'
    return
  }

  if (!isEmailValid(normalizedEmail.value)) {
    error.value = '请输入正确的邮箱格式'
    return
  }

  if (normalizedNickname.value.length < 2 || normalizedNickname.value.length > 32) {
    error.value = '用户名长度需在 2 到 32 个字符之间'
    return
  }

  if (form.password.length < 6 || form.password.length > 32) {
    error.value = '密码长度需在 6 到 32 位之间'
    return
  }

  loading.value = true
  try {
    await register({
      email: normalizedEmail.value,
      password: form.password,
      nickname: normalizedNickname.value,
      code: form.code.trim()
    })

    const authData = await login({
      account: normalizedEmail.value,
      password: form.password
    })

    authStore.token = authData.token
    authStore.user = authData.user
    authStore.persist()

    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/dashboard'
    router.push(redirect)
  } catch (err) {
    error.value = err instanceof Error ? err.message : '注册失败'
  } finally {
    loading.value = false
  }
}

onBeforeUnmount(() => {
  if (timer) {
    window.clearInterval(timer)
    timer = null
  }
})
</script>
