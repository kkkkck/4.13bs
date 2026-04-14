import request from '@/app/request'
import type { FavoriteRecord } from '@/app/types'

export function getFavorites() {
  return request.get<FavoriteRecord[]>('/favorites')
}

export function addFavorite(questionId: number) {
  return request.post<boolean>('/favorites', null, {
    params: { questionId }
  })
}

export function removeFavorite(questionId: number) {
  return request.delete<boolean>(`/favorites/${questionId}`)
}
