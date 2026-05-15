import request from '@/app/request'
import type { AiTutorMessage, AiTutorResponse } from '@/app/types'

export function askAiTutor(payload: {
  questionId: number
  userAnswer?: string
  message: string
  history?: AiTutorMessage[]
}) {
  // 本地 DeepSeek 推理可能比较慢，所以这里给 AI 接口单独放宽超时时间到 3 分钟。
  return request.post<AiTutorResponse>('/ai/tutor', payload, {
    timeout: 180000
  })
}
