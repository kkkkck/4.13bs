import request from '@/app/request'
import type { User } from '@/app/types'

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

export interface AuthResult {
  token: string
  user: User
}

export interface SendCodeResult {
  message: string
  debugCode?: string | null
  expiresInSeconds: number
  mailEnabled: boolean
}

export function sendCode(email: string) {
  return request.post<SendCodeResult>('/auth/send-code', { email })
}

export function register(payload: RegisterRequest) {
  return request.post<User>('/auth/register', payload)
}

export function login(payload: LoginRequest) {
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

export function sendProfileEmailCode() {
  return request.post<SendCodeResult>('/auth/profile/email-code')
}

export function uploadCurrentUserAvatar(file: File) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post<User>('/auth/avatar', formData, {
    timeout: 2 * 60 * 1000
  })
}

export function deleteCurrentUserAvatar() {
  return request.delete<User>('/auth/avatar')
}
