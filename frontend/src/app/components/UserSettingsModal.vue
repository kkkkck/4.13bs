<template>
  <div v-if="open" class="modal-mask" @click.self="emit('close')">
    <section class="panel-card modal-dialog settings-dialog">
      <div class="panel-head settings-header">
        <div>
          <h3>账号设置</h3>
        </div>
        <button class="ghost-btn small settings-close-btn" type="button" @click="emit('close')">关闭</button>
      </div>

      <div class="modal-content-scroll settings-scroll">
        <div class="settings-stack">
          <article class="settings-account-panel">
            <div class="settings-account-top">
              <UserAvatar :avatar-url="previewAvatarUrl" :nickname="profileForm.nickname || authStore.user?.nickname" :size="88" />
              <div class="settings-account-copy">
                <strong>{{ profileForm.nickname || authStore.user?.nickname || '学习者' }}</strong>
                <small v-if="authStore.user?.email">{{ authStore.user?.email }}</small>
              </div>
            </div>

            <div class="settings-avatar-actions">
              <label class="primary-btn file-btn settings-action-btn">
                {{ uploadingAvatar ? '上传中...' : '上传头像' }}
                <input type="file" accept="image/png,image/jpeg,image/webp" :disabled="uploadingAvatar" @change="handleAvatarUpload" />
              </label>
              <button
                class="ghost-btn settings-action-btn"
                type="button"
                :disabled="removingAvatar || (!authStore.user?.avatarUrl && !avatarPreviewUrl)"
                @click="handleAvatarRemove"
              >
                {{ removingAvatar ? '移除中...' : '移除头像' }}
              </button>
            </div>

            <p class="form-tip">支持 JPG / PNG / WEBP，单张不超过 2MB。</p>
          </article>

          <article class="settings-section-card">
            <div class="settings-section-head">
              <div>
                <strong>个人资料</strong>
              </div>
            </div>

            <div class="settings-form-grid">
              <label class="field-block">
                <span>昵称</span>
                <input
                  v-model.trim="profileForm.nickname"
                  class="input"
                  type="text"
                  maxlength="50"
                  placeholder="输入昵称"
                />
              </label>

              <div class="field-block">
                  <span>当前邮箱</span>
                  <div class="settings-inline-field">
                  <input class="input" type="text" :value="currentEmail || '-'" readonly />
                  <button class="ghost-btn settings-inline-btn" type="button" @click="openEmailDialog">修改邮箱</button>
                </div>
              </div>
            </div>

            <div class="row-actions settings-row-actions">
              <button class="primary-btn settings-action-btn" type="button" :disabled="savingProfile" @click="handleSaveProfile">
                {{ savingProfile ? '保存中...' : '保存资料' }}
              </button>
            </div>
          </article>

          <article class="settings-section-card">
            <div class="settings-section-head">
              <div>
                <strong>密码修改</strong>
              </div>
            </div>

            <div class="settings-password-grid">
              <label class="field-block">
                <span>当前密码</span>
                <div class="settings-input-shell">
                  <input
                    v-model="passwordForm.currentPassword"
                    class="input"
                    :type="passwordVisibility.current ? 'text' : 'password'"
                    autocomplete="current-password"
                    maxlength="32"
                    placeholder="输入当前登录密码"
                  />
                  <button class="ghost-btn small settings-visibility-btn" type="button" @click="passwordVisibility.current = !passwordVisibility.current">
                    {{ passwordVisibility.current ? '隐藏' : '显示' }}
                  </button>
                </div>
              </label>

              <label class="field-block">
                <span>新密码</span>
                <div class="settings-input-shell">
                  <input
                    v-model="passwordForm.newPassword"
                    class="input"
                    :type="passwordVisibility.next ? 'text' : 'password'"
                    autocomplete="new-password"
                    maxlength="32"
                    placeholder="6-32 位"
                  />
                  <button class="ghost-btn small settings-visibility-btn" type="button" @click="passwordVisibility.next = !passwordVisibility.next">
                    {{ passwordVisibility.next ? '隐藏' : '显示' }}
                  </button>
                </div>
              </label>

              <label class="field-block">
                <span>确认新密码</span>
                <div class="settings-input-shell">
                  <input
                    v-model="passwordForm.confirmPassword"
                    class="input"
                    :type="passwordVisibility.confirm ? 'text' : 'password'"
                    autocomplete="new-password"
                    maxlength="32"
                    placeholder="再次输入新密码"
                  />
                  <button class="ghost-btn small settings-visibility-btn" type="button" @click="passwordVisibility.confirm = !passwordVisibility.confirm">
                    {{ passwordVisibility.confirm ? '隐藏' : '显示' }}
                  </button>
                </div>
              </label>
            </div>

            <div class="row-actions settings-row-actions">
              <button class="primary-btn settings-action-btn" type="button" :disabled="savingPassword" @click="handleChangePassword">
                {{ savingPassword ? '提交中...' : '更新密码' }}
              </button>
            </div>
          </article>
        </div>
      </div>

      <div class="settings-feedback-bar">
        <p v-if="message" class="form-success">{{ message }}</p>
        <p v-if="error" class="form-error">{{ error }}</p>
      </div>

      <div v-if="emailDialogOpen" class="settings-submask" @click.self="closeEmailDialog">
        <section class="settings-email-dialog">
          <div class="panel-head compact settings-email-head">
            <div>
              <h4>修改邮箱</h4>
              <p>验证码会发送到当前邮箱，请注意查收。</p>
            </div>
            <button class="ghost-btn small" type="button" @click="closeEmailDialog">关闭</button>
          </div>

          <div class="settings-email-grid">
            <label class="field-block">
              <span>当前邮箱</span>
              <input class="input" type="text" :value="currentEmail || '-'" readonly />
            </label>

            <label class="field-block">
              <span>新邮箱</span>
              <input
                v-model.trim="emailForm.newEmail"
                class="input"
                type="email"
                maxlength="100"
                placeholder="输入新邮箱"
              />
            </label>

            <div class="field-block settings-email-code-field">
              <span>验证码</span>
              <div class="settings-inline-field">
                <input
                  v-model.trim="emailForm.verificationCode"
                  class="input"
                  type="text"
                  maxlength="6"
                  placeholder="输入 6 位验证码"
                />
                <button
                  class="ghost-btn settings-inline-btn"
                  type="button"
                  :disabled="sendingCode || emailCodeCooldownRemaining > 0"
                  @click="handleSendEmailCode"
                >
                  {{ sendingCode ? '发送中...' : sendCodeButtonText }}
                </button>
              </div>
            </div>
          </div>

          <p v-if="debugCode" class="form-tip">开发环境验证码：{{ debugCode }}</p>

          <div class="row-actions settings-row-actions">
            <button class="primary-btn settings-action-btn" type="button" :disabled="savingEmail" @click="handleConfirmEmailChange">
              {{ savingEmail ? '保存中...' : '验证并更新邮箱' }}
            </button>
          </div>
        </section>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import UserAvatar from '@/app/components/UserAvatar.vue'
import {
  changeCurrentUserPassword,
  deleteCurrentUserAvatar,
  sendProfileEmailCode,
  updateCurrentUserProfile,
  uploadCurrentUserAvatar
} from '@/app/api/auth'
import { useAuthStore } from '@/app/stores/auth'

const props = defineProps<{
  open: boolean
}>()

const emit = defineEmits<{
  close: []
}>()

const authStore = useAuthStore()
const savingProfile = ref(false)
const savingPassword = ref(false)
const savingEmail = ref(false)
const uploadingAvatar = ref(false)
const removingAvatar = ref(false)
const sendingCode = ref(false)
const emailDialogOpen = ref(false)
const message = ref('')
const error = ref('')
const debugCode = ref('')
const emailCodeCooldownRemaining = ref(0)
const avatarPreviewUrl = ref('')
let emailCodeTimer: number | null = null

const profileForm = reactive({
  nickname: ''
})

const emailForm = reactive({
  newEmail: '',
  verificationCode: ''
})

const passwordForm = reactive({
  currentPassword: '',
  newPassword: '',
  confirmPassword: ''
})

const passwordVisibility = reactive({
  current: false,
  next: false,
  confirm: false
})

const currentEmail = computed(() => authStore.user?.email || '')
const previewAvatarUrl = computed(() => avatarPreviewUrl.value || authStore.user?.avatarUrl || '')
const sendCodeButtonText = computed(() => (emailCodeCooldownRemaining.value > 0 ? `${emailCodeCooldownRemaining.value}s 后重发` : '发送验证码'))

const resetFeedback = () => {
  message.value = ''
  error.value = ''
}

const clearAvatarPreview = () => {
  if (avatarPreviewUrl.value) {
    URL.revokeObjectURL(avatarPreviewUrl.value)
    avatarPreviewUrl.value = ''
  }
}

const resetPasswordForm = () => {
  passwordForm.currentPassword = ''
  passwordForm.newPassword = ''
  passwordForm.confirmPassword = ''
  passwordVisibility.current = false
  passwordVisibility.next = false
  passwordVisibility.confirm = false
}

const resetEmailForm = () => {
  emailForm.newEmail = ''
  emailForm.verificationCode = ''
  debugCode.value = ''
  if (emailCodeTimer) {
    window.clearInterval(emailCodeTimer)
    emailCodeTimer = null
  }
  emailCodeCooldownRemaining.value = 0
}

const syncForm = () => {
  profileForm.nickname = authStore.user?.nickname || ''
  resetPasswordForm()
  resetEmailForm()
  emailDialogOpen.value = false
  clearAvatarPreview()
  resetFeedback()
}

const startEmailCodeCooldown = (expiresInSeconds: number) => {
  emailCodeCooldownRemaining.value = 60
  if (emailCodeTimer) {
    window.clearInterval(emailCodeTimer)
  }
  emailCodeTimer = window.setInterval(() => {
    emailCodeCooldownRemaining.value = Math.max(0, emailCodeCooldownRemaining.value - 1)
    if (emailCodeCooldownRemaining.value <= 0 && emailCodeTimer) {
      window.clearInterval(emailCodeTimer)
      emailCodeTimer = null
    }
  }, 1000)
  if (expiresInSeconds <= 0) {
    emailCodeCooldownRemaining.value = 0
  }
}

const handleSaveProfile = async () => {
  resetFeedback()
  if (!profileForm.nickname.trim()) {
    error.value = '昵称不能为空'
    return
  }

  savingProfile.value = true
  try {
    const user = await updateCurrentUserProfile({
      nickname: profileForm.nickname.trim(),
      email: currentEmail.value
    })
    authStore.setUser(user)
    message.value = '资料已更新'
  } catch (err) {
    error.value = err instanceof Error ? err.message : '资料更新失败'
  } finally {
    savingProfile.value = false
  }
}

const openEmailDialog = () => {
  resetFeedback()
  resetEmailForm()
  emailDialogOpen.value = true
}

const closeEmailDialog = () => {
  emailDialogOpen.value = false
  resetEmailForm()
}

const handleSendEmailCode = async () => {
  resetFeedback()
  if (!emailForm.newEmail.trim()) {
    error.value = '请先输入新邮箱'
    return
  }
  if (emailForm.newEmail.trim().toLowerCase() === currentEmail.value.trim().toLowerCase()) {
    error.value = '新邮箱不能与当前邮箱相同'
    return
  }
  if (emailCodeCooldownRemaining.value > 0) {
    return
  }

  sendingCode.value = true
  try {
    const result = await sendProfileEmailCode()
    debugCode.value = result.debugCode || ''
    startEmailCodeCooldown(result.expiresInSeconds)
    message.value = result.message
  } catch (err) {
    error.value = err instanceof Error ? err.message : '验证码发送失败'
  } finally {
    sendingCode.value = false
  }
}

const handleConfirmEmailChange = async () => {
  resetFeedback()
  if (!emailForm.newEmail.trim()) {
    error.value = '新邮箱不能为空'
    return
  }
  if (emailForm.newEmail.trim().toLowerCase() === currentEmail.value.trim().toLowerCase()) {
    error.value = '新邮箱不能与当前邮箱相同'
    return
  }
  if (!emailForm.verificationCode.trim()) {
    error.value = '请填写验证码'
    return
  }
  if (!profileForm.nickname.trim()) {
    error.value = '昵称不能为空'
    return
  }

  savingEmail.value = true
  try {
    const user = await updateCurrentUserProfile({
      nickname: profileForm.nickname.trim(),
      email: emailForm.newEmail.trim(),
      verificationCode: emailForm.verificationCode.trim()
    })
    authStore.setUser(user)
    closeEmailDialog()
    message.value = '邮箱已更新'
  } catch (err) {
    error.value = err instanceof Error ? err.message : '邮箱修改失败'
  } finally {
    savingEmail.value = false
  }
}

const handleAvatarUpload = async (event: Event) => {
  resetFeedback()
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) {
    return
  }
  if (file.size > 2 * 1024 * 1024) {
    error.value = '头像图片不能超过 2MB'
    input.value = ''
    return
  }

  uploadingAvatar.value = true
  try {
    clearAvatarPreview()
    avatarPreviewUrl.value = URL.createObjectURL(file)
    const user = await uploadCurrentUserAvatar(file)
    authStore.setUser(user)
    clearAvatarPreview()
    message.value = '头像已更新'
  } catch (err) {
    error.value = err instanceof Error ? err.message : '头像上传失败'
  } finally {
    uploadingAvatar.value = false
    input.value = ''
  }
}

const handleAvatarRemove = async () => {
  resetFeedback()
  removingAvatar.value = true
  try {
    const user = await deleteCurrentUserAvatar()
    authStore.setUser(user)
    clearAvatarPreview()
    message.value = '头像已移除'
  } catch (err) {
    error.value = err instanceof Error ? err.message : '头像移除失败'
  } finally {
    removingAvatar.value = false
  }
}

const handleChangePassword = async () => {
  resetFeedback()
  if (!passwordForm.currentPassword || !passwordForm.newPassword || !passwordForm.confirmPassword) {
    error.value = '请完整填写密码信息'
    return
  }
  if (passwordForm.newPassword.length < 6 || passwordForm.newPassword.length > 32) {
    error.value = '新密码长度需在 6 到 32 位之间'
    return
  }
  if (passwordForm.newPassword !== passwordForm.confirmPassword) {
    error.value = '两次输入的新密码不一致'
    return
  }

  savingPassword.value = true
  try {
    await changeCurrentUserPassword({
      currentPassword: passwordForm.currentPassword,
      newPassword: passwordForm.newPassword,
      confirmPassword: passwordForm.confirmPassword
    })
    resetPasswordForm()
    message.value = '密码已更新'
  } catch (err) {
    error.value = err instanceof Error ? err.message : '密码修改失败'
  } finally {
    savingPassword.value = false
  }
}

watch(
  () => props.open,
  (open) => {
    if (open) {
      syncForm()
    }
  }
)

watch(
  () => authStore.user,
  () => {
    if (props.open) {
      syncForm()
    }
  },
  { deep: true }
)

onBeforeUnmount(() => {
  if (emailCodeTimer) {
    window.clearInterval(emailCodeTimer)
  }
  clearAvatarPreview()
})
</script>
