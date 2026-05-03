import request from '@/app/request'
import type { AiTutorMessage, AiTutorResponse } from '@/app/types'

export function askAiTutor(payload: {
  questionId: number
  userAnswer?: string
  message: string
  history?: AiTutorMessage[]
}) {
  return request.post<AiTutorResponse>('/ai/tutor', payload, {
    timeout: 180000
  })
}
