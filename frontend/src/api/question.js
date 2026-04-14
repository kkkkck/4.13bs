import request from '../utils/request'

// 获取题目详情
export function getQuestionById(id) {
  return request({
    url: `/api/questions/${id}`,
    method: 'get'
  })
}

// 按分类获取题目列表
export function getQuestionsByCategory(categoryId, page = 1, size = 20) {
  return request({
    url: '/api/questions',
    method: 'get',
    params: { categoryId, page, size }
  })
}

// 提交答案
export function submitQuestionAnswer(questionId, data) {
  return request({
    url: `/api/questions/${questionId}/submit`,
    method: 'post',
    data
  })
}
