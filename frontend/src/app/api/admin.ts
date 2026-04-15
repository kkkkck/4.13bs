import request from '@/app/request'
import type { Category, Question, User } from '@/app/types'

export interface AdminOverview {
  totalQuestions: number
  totalCategories: number
  categoryStats: Array<{ categoryName: string; count: number }>
  hotTopicStats: Array<{ categoryName: string; count: number }>
  typeStats: Array<{ typeName: string; count: number }>
}

export interface AdminUsersSummary {
  totalUsers: number
  averageActiveDurationSeconds: number
  trackedUsers: number
  dailyActiveDuration: Array<{ date: string; durationSeconds: number }>
  durationDistribution: Array<{ bucketOrder: number; bucketLabel: string; userCount: number }>
}

export interface AdminUserPage {
  records: User[]
  total: number
  page: number
  size: number
}

export interface AdminImportResult {
  success: boolean
  total: number
  successCount: number
  enrichedCount?: number
  duplicateCount?: number
  failCount: number
  message: string
  errors?: string[]
}

export function getAdminOverview() {
  return request.get<AdminOverview>('/admin/statistics/overview')
}

export function getAdminUsersSummary() {
  return request.get<AdminUsersSummary>('/admin/statistics/users')
}

export function getAdminQuestions(params: {
  page: number
  size: number
  keyword?: string
  categoryId?: number
  status?: number
  type?: number
  difficulty?: number
  sourceType?: number
}) {
  return request.get<{
    records: Question[]
    total: number
    page: number
    size: number
  }>('/admin/questions', {
    params
  })
}

export function createAdminQuestion(payload: Partial<Question>) {
  return request.post('/admin/questions', payload)
}

export function updateAdminQuestion(id: number, payload: Partial<Question>) {
  return request.put(`/admin/questions/${id}`, payload)
}

export function updateAdminQuestionStatus(id: number, status: number) {
  return request.put(`/admin/questions/${id}/status`, { status })
}

export function deleteAdminQuestion(id: number) {
  return request.delete(`/admin/questions/${id}`)
}

export function importAdminQuestions(file: File) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post<AdminImportResult>('/admin/questions/import', formData, {
    timeout: 10 * 60 * 1000
  })
}

export function getAdminCategories() {
  return request.get<Category[]>('/admin/categories')
}

export function createAdminCategory(payload: Partial<Category>) {
  return request.post('/admin/categories', payload)
}

export function updateAdminCategory(id: number, payload: Partial<Category>) {
  return request.put(`/admin/categories/${id}`, payload)
}

export function deleteAdminCategory(id: number) {
  return request.delete(`/admin/categories/${id}`)
}

export function getAdminUsers(params: {
  page: number
  size: number
  keyword?: string
  role?: number
  status?: number
  activityStatus?: string
  sortField?: string
  sortOrder?: string
}) {
  return request.get<AdminUserPage>('/admin/users', {
    params
  })
}

export function updateAdminUser(id: number, payload: Partial<User>) {
  return request.put<User>(`/admin/users/${id}`, payload)
}

export function batchUpdateAdminUserStatus(payload: {
  userIds: number[]
  status: number
}) {
  return request.put<{ updatedCount: number; requestedCount: number; skippedCount: number; status: number }>(
    '/admin/users/batch/status',
    payload
  )
}

export function batchUpdateAdminUserRole(payload: {
  userIds: number[]
  role: number
}) {
  return request.put<{ updatedCount: number; requestedCount: number; skippedCount: number; role: number }>(
    '/admin/users/batch/role',
    payload
  )
}
