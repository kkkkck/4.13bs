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
