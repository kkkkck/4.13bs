import request from '@/app/request'
import type { PageResult, Question, SubmitResult } from '@/app/types'

// 题目相关 API：PracticePage.vue 通过这些函数拿题目、批量恢复题目、提交答案。
export function getQuestionById(id: number) {
  return request.get<Question>(`/questions/${id}`)
}

export function getQuestionsByIds(ids: number[]) {
  // 收藏回练/错题重练会传一组题目 id；前端先去重，避免同一题重复请求。
  const uniqueIds = [...new Set(ids.filter((id) => Number.isFinite(id) && id > 0))]
  if (!uniqueIds.length) {
    return Promise.resolve([] as Question[])
  }

  return request.post<Question[]>('/questions/batch', {
    ids: uniqueIds
  })
}

export function getQuestionsByCategory(categoryId: number, page = 1, size = 20, sourceType?: number) {
  // sourceType: 1=真题，2=模拟题，不传表示混合。
  return request.get<PageResult<Question>>('/questions', {
    params: { categoryId, page, size, sourceType }
  })
}

export function submitAnswer(questionId: number, answer: string) {
  // 后端负责标准化答案并判题，前端只提交当前题 id 和用户答案。
  return request.post<SubmitResult>(`/questions/${questionId}/submit`, { answer })
}
