<template>
  <div class="auth-page">
    <section class="auth-hero">
      <p class="eyebrow">重置密码</p>
      <h1>用注册邮箱找回账号。</h1>
      <p>验证码会发送到之前注册的邮箱。</p>
    </section>

    <section class="auth-card">
      <div class="panel-head compact">
        <div>
          <h2>忘记密码</h2>
          <p>验证邮箱后设置新密码。</p>
        </div>
      </div>

      <form class="auth-form" @submit.prevent="handleReset">
        <label class="field-block">
          <span>注册邮箱</span>
          <input v-model.trim="form.email" class="input" type="email" placeholder="name@example.com" autocomplete="email" />
        </label>

        <label class="field-block">
          <span>验证码</span>
          <div class="inline-form">
            <input v-model.trim="form.code" class="input" type="text" maxlength="6" placeholder="6 位验证码" />
            <button class="ghost-btn" type="button" :disabled="!canSendCode" @click="handleSendCode">
              {{ countdown > 0 ? `${countdown}s` : sendingCode ? '发送中...' : '发送验证码' }}
            </button>
          </div>
          <small v-if="codeSent" class="form-tip">验证码已发送，{{ expireMinutes }}分钟内有效</small>
        </label>

        <label class="field-block">
          <span>新密码</span>
          <div class="password-field">
            <input
              v-model="form.newPassword"
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
          <span>确认密码</span>
          <input
            v-model="form.confirmPassword"
            class="input"
            :type="passwordVisible ? 'text' : 'password'"
            placeholder="再次输入新密码"
            autocomplete="new-password"
            maxlength="32"
          />
        </label>

        <div v-if="debugCode" class="dev-code-card">
          <strong>开发环境验证码</strong>
          <div class="dev-code-row">
            <span>{{ debugCode }}</span>
            <button class="ghost-btn small" type="button" @click="fillDebugCode">填入验证码</button>
          </div>
        </div>

        <p v-if="message" class="form-success">{{ message }}</p>
        <p v-if="error" class="form-error">{{ error }}</p>

        <button class="primary-btn" :disabled="loading" type="submit">
          {{ loading ? '提交中...' : '重置密码' }}
        </button>
      </form>

      <div class="support-line">
        <span>想起密码？</span>
        <RouterLink to="/login">返回登录</RouterLink>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import { resetPassword, sendPasswordResetCode } from '@/app/api/auth'

const router = useRouter()

const form = reactive({
  email: '',
  code: '',
  newPassword: '',
  confirmPassword: ''
})

const loading = ref(false)
const sendingCode = ref(false)
const countdown = ref(0)
const error = ref('')
const message = ref('')
const debugCode = ref('')
const expiresInSeconds = ref(300)
const codeSent = ref(false)
const passwordVisible = ref(false)

let timer: number | null = null

const normalizedEmail = computed(() => form.email.trim().toLowerCase())
const canSendCode = computed(() => !sendingCode.value && countdown.value === 0 && isEmailValid(normalizedEmail.value))
const expireMinutes = computed(() => Math.max(1, Math.round(expiresInSeconds.value / 60)))

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

const validateEmail = () => {
  if (!normalizedEmail.value) {
    error.value = '请先输入注册邮箱'
    return false
  }
  if (!isEmailValid(normalizedEmail.value)) {
    error.value = '请输入正确的邮箱格式'
    return false
  }
  return true
}

const handleSendCode = async () => {
  error.value = ''
  message.value = ''
  debugCode.value = ''
  codeSent.value = false

  if (!validateEmail()) {
    return
  }

  sendingCode.value = true
  try {
    const result = await sendPasswordResetCode(normalizedEmail.value)
    debugCode.value = result.debugCode || ''
    expiresInSeconds.value = result.expiresInSeconds || 300
    codeSent.value = true
    startCountdown()
  } catch (err) {
    error.value = err instanceof Error ? err.message : '验证码发送失败'
  } finally {
    sendingCode.value = false
  }
}

const handleReset = async () => {
  error.value = ''
  message.value = ''

  if (!validateEmail()) {
    return
  }
  if (!form.code.trim()) {
    error.value = '请填写验证码'
    return
  }
  if (form.newPassword.length < 6 || form.newPassword.length > 32) {
    error.value = '新密码长度需在 6 到 32 位之间'
    return
  }
  if (form.newPassword !== form.confirmPassword) {
    error.value = '两次输入的新密码不一致'
    return
  }

  loading.value = true
  try {
    await resetPassword({
      email: normalizedEmail.value,
      code: form.code.trim(),
      newPassword: form.newPassword,
      confirmPassword: form.confirmPassword
    })
    message.value = '密码已重置，请重新登录'
    window.setTimeout(() => router.push('/login'), 900)
  } catch (err) {
    error.value = err instanceof Error ? err.message : '密码重置失败'
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
