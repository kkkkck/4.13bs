import { defineStore } from 'pinia'
import { getCurrentUser, login as loginApi } from '@/app/api/auth'
import type { User } from '@/app/types'

interface LoginPayload {
  account: string
  password: string
}

interface AuthState {
  token: string
  user: User | null
  hydrated: boolean
}

const SUPER_ADMIN_ID = 1

export const useAuthStore = defineStore('auth', {
  state: (): AuthState => ({
    token: '',
    user: null,
    hydrated: false
  }),
  getters: {
    isLoggedIn: (state) => Boolean(state.token),
    isAdmin: (state) => state.user?.role === 1,
    isSuperAdmin: (state) => state.user?.role === 1 && state.user?.id === SUPER_ADMIN_ID
  },
  actions: {
    hydrate() {
      if (this.hydrated) {
        return
      }
      this.token = localStorage.getItem('token') || ''
      const rawUser = localStorage.getItem('user')
      if (rawUser) {
        try {
          this.user = JSON.parse(rawUser)
        } catch {
          this.user = null
          localStorage.removeItem('user')
        }
      } else {
        this.user = null
      }
      this.hydrated = true
    },
    persist() {
      if (this.token) {
        localStorage.setItem('token', this.token)
      } else {
        localStorage.removeItem('token')
      }

      if (this.user) {
        localStorage.setItem('user', JSON.stringify(this.user))
        localStorage.setItem('role', String(this.user.role))
      } else {
        localStorage.removeItem('user')
        localStorage.removeItem('role')
      }
    },
    async login(payload: LoginPayload) {
      const data = await loginApi(payload)
      this.token = data.token
      this.user = data.user
      this.persist()
    },
    async fetchProfile() {
      if (!this.token) {
        return null
      }
      const user = await getCurrentUser()
      this.user = user
      this.persist()
      return user
    },
    setUser(user: User | null) {
      this.user = user
      this.persist()
    },
    logout() {
      this.token = ''
      this.user = null
      this.persist()
    }
  }
})
