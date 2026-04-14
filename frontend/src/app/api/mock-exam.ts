import request from '@/app/request'
import type { MockExamPaper } from '@/app/types'

export function generateMockExam(totalQuestions = 20) {
  return request.get<MockExamPaper>('/mock-exams/paper', {
    params: { totalQuestions }
  })
}
