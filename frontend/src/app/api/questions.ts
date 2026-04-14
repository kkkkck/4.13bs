import request from '@/app/request'
import type { PageResult, Question, SubmitResult } from '@/app/types'

export function getQuestionById(id: number) {
  return request.get<Question>(`/questions/${id}`)
}

export function getQuestionsByIds(ids: number[]) {
  const uniqueIds = [...new Set(ids.filter((id) => Number.isFinite(id) && id > 0))]
  if (!uniqueIds.length) {
    return Promise.resolve([] as Question[])
  }

  return request.post<Question[]>('/questions/batch', {
    ids: uniqueIds
  })
}

export function getQuestionsByCategory(categoryId: number, page = 1, size = 20, sourceType?: number) {
  return request.get<PageResult<Question>>('/questions', {
    params: { categoryId, page, size, sourceType }
  })
}

export function submitAnswer(questionId: number, answer: string) {
  return request.post<SubmitResult>(`/questions/${questionId}/submit`, { answer })
}
