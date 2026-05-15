import type { RouteLocationRaw } from 'vue-router'

const LAST_PRACTICE_KEY = 'shuati:last-practice'

// 这里保存“上次练到哪个专题/题源”，首页就能提供继续练习入口。
// 只存 categoryId 和 sourceType，不存题目答案，避免 localStorage 变得复杂。
interface LastPracticePayload {
  categoryId: number
  sourceType?: number
  updatedAt: string
}

export function saveLastPracticeCategory(categoryId: number, sourceType = 0) {
  // 每次进入练习页都会更新，刷新或重新打开浏览器后也能继续找到上次专题。
  if (!Number.isFinite(categoryId) || categoryId <= 0) {
    return
  }

  const payload: LastPracticePayload = {
    categoryId,
    sourceType: Number.isFinite(sourceType) && sourceType > 0 ? sourceType : 0,
    updatedAt: new Date().toISOString()
  }

  localStorage.setItem(LAST_PRACTICE_KEY, JSON.stringify(payload))
}

export function getLastPracticeCategoryId() {
  try {
    const raw = localStorage.getItem(LAST_PRACTICE_KEY)
    if (!raw) {
      return 0
    }

    const parsed = JSON.parse(raw) as Partial<LastPracticePayload>
    return Number.isFinite(parsed.categoryId) && Number(parsed.categoryId) > 0 ? Number(parsed.categoryId) : 0
  } catch {
    localStorage.removeItem(LAST_PRACTICE_KEY)
    return 0
  }
}

export function hasLastPracticeCategory() {
  return getLastPracticeCategoryId() > 0
}

export function getLastPracticeSourceType() {
  try {
    const raw = localStorage.getItem(LAST_PRACTICE_KEY)
    if (!raw) {
      return 0
    }

    const parsed = JSON.parse(raw) as Partial<LastPracticePayload>
    return Number.isFinite(parsed.sourceType) && Number(parsed.sourceType) > 0 ? Number(parsed.sourceType) : 0
  } catch {
    localStorage.removeItem(LAST_PRACTICE_KEY)
    return 0
  }
}

export function getLastPracticeRoute(): RouteLocationRaw {
  // 根据保存的专题生成 Vue Router 可跳转对象；没有记录就回到专题页。
  const categoryId = getLastPracticeCategoryId()
  if (!categoryId) {
    return '/categories'
  }

  const sourceType = getLastPracticeSourceType()

  return {
    path: '/practice',
    query: {
      categoryId: String(categoryId),
      ...(sourceType ? { sourceType: String(sourceType) } : {})
    }
  }
}
