import request from '@/app/request'
import type { PageResult, WrongQuestionRecord } from '@/app/types'

export function getWrongQuestions(page = 1, size = 10) {
  return request.get<PageResult<WrongQuestionRecord>>('/wrong-questions', {
    params: { page, size }
  })
}

export function addWrongQuestion(questionId: number, userAnswer: string) {
  return request.post<boolean>('/wrong-questions', null, {
    params: { questionId, userAnswer }
  })
}

export function removeWrongQuestion(id: number) {
  return request.delete<boolean>(`/wrong-questions/${id}`)
}
