import request from '@/app/request'
import type { User } from '@/app/types'

// 这个文件是“认证相关接口清单”。页面组件不直接写 URL，而是调用这里的函数。
// 好处是后端接口路径变动时，只需要改这一处。
export interface LoginRequest {
  account: string
  password: string
}

export interface RegisterRequest {
  email: string
  password: string
  nickname: string
  code: string
}

export interface UpdateProfileRequest {
  nickname: string
  email: string
  verificationCode?: string
}

export interface ChangePasswordRequest {
  currentPassword: string
  newPassword: string
  confirmPassword: string
}

export interface ResetPasswordRequest {
  email: string
  code: string
  newPassword: string
  confirmPassword: string
}

export interface AuthResult {
  token: string
  user: User
}

export interface SendCodeResult {
  // mailEnabled=true 表示后端启用了真实邮箱发送；debugCode 只允许开发调试环境返回。
  message: string
  debugCode?: string | null
  expiresInSeconds: number
  mailEnabled: boolean
}

export function sendCode(email: string) {
  // 注册验证码：后端会先判断邮箱是否已注册，再决定是否发送验证码。
  return request.post<SendCodeResult>('/auth/send-code', { email })
}

export function register(payload: RegisterRequest) {
  // 注册接口只创建账号，不直接写入本地登录态；注册成功后页面会再调用 login 自动登录。
  return request.post<User>('/auth/register', payload)
}

export function login(payload: LoginRequest) {
  // 登录成功会返回 token + user，auth store 会保存到 localStorage。
  return request.post<AuthResult>('/auth/login', payload)
}

export function getCurrentUser() {
  return request.get<User>('/auth/me')
}

export function updateCurrentUserProfile(payload: UpdateProfileRequest) {
  return request.put<User>('/auth/profile', payload)
}

export function changeCurrentUserPassword(payload: ChangePasswordRequest) {
  return request.put<void>('/auth/password', payload)
}

export function sendPasswordResetCode(email: string) {
  return request.post<SendCodeResult>('/auth/password/reset-code', { email })
}

export function resetPassword(payload: ResetPasswordRequest) {
  return request.post<void>('/auth/password/reset', payload)
}

export function sendProfileEmailCode() {
  return request.post<SendCodeResult>('/auth/profile/email-code')
}

export function uploadCurrentUserAvatar(file: File) {
  // 上传文件要用 FormData；request.ts 会自动移除 JSON Content-Type，让浏览器生成 multipart 边界。
  const formData = new FormData()
  formData.append('file', file)
  return request.post<User>('/auth/avatar', formData, {
    timeout: 2 * 60 * 1000
  })
}

export function deleteCurrentUserAvatar() {
  return request.delete<User>('/auth/avatar')
}
