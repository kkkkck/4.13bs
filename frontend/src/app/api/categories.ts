import request from '@/app/request'
import type { Category } from '@/app/types'

export function getCategories() {
  return request.get<Category[]>('/categories')
}
