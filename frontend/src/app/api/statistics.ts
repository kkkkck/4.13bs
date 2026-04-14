import request from '@/app/request'
import type { CategoryRate, DailyRate, PracticeRecord, StatisticsOverview } from '@/app/types'

export function getOverview() {
  return request.get<StatisticsOverview>('/statistics/overview')
}

export function getCategoryRates() {
  return request.get<CategoryRate[]>('/statistics/category-rate')
}

export function getDailyRates(days = 7) {
  return request.get<DailyRate[]>('/statistics/daily-rate', {
    params: { days }
  })
}

export function getPracticeHistory(limit = 10) {
  return request.get<PracticeRecord[]>('/statistics/history', {
    params: { limit }
  })
}

export function createPracticeRecord(payload: {
  categoryId: number
  totalQuestions: number
  correctCount: number
  duration: number
}) {
  return request.post('/statistics/record', payload)
}
