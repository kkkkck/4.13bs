export interface User {
  id: number
  email: string
  nickname: string
  avatarPreset?: string
  avatarUrl?: string | null
  role: number
  status: number
  emailVerified?: boolean
  lastSeenAt?: string | null
  createdAt?: string
  updatedAt?: string
}

export interface Category {
  id: number
  name: string
  description?: string
  sort?: number
  parentId?: number
  practiceMode?: number
  status?: number
}

export interface Question {
  id: number
  content: string
  type: number
  difficulty: number
  tags?: string
  source?: string
  sourceType?: number
  optionA?: string
  optionB?: string
  optionC?: string
  optionD?: string
  correctAnswer: string
  analysis?: string
  solutionStrategy?: string
  categoryId: number
  status?: number
  createdAt?: string
  updatedAt?: string
}

export interface PageResult<T> {
  records: T[]
  total: number
  size: number
  current: number
  pages: number
}

export interface SubmitResult {
  correct: boolean
  correctAnswer: string
  userAnswer: string
  analysis: string
  solutionStrategy?: string
  message: string
}

export interface FavoriteRecord {
  id: number
  userId: number
  questionId: number
  createdAt: string
}

export interface WrongQuestionRecord {
  id: number
  userId: number
  questionId: number
  userAnswer: string
  wrongCount: number
  lastWrongTime: string
  createdAt: string
}

export interface PracticeRecord {
  id: number
  userId: number
  categoryId: number
  totalQuestions: number
  correctCount: number
  duration: number
  createdAt: string
}

export interface StatisticsOverview {
  totalQuestions: number
  totalCorrectRate: number
  continuousDays: number
}

export interface CategoryRate {
  categoryId: number
  categoryName: string
  correctCount: number
  totalCount: number
  correctRate: number
}

export interface DailyRate {
  date: string
  correctCount: number
  totalCount: number
  correctRate: number
}

export interface AdminQuestionPage {
  records: Question[]
  total: number
  page: number
  size: number
}

export interface MockExamChapter {
  categoryId: number
  categoryName: string
  questionCount: number
}

export interface MockExamSection {
  categoryId: number
  categoryName: string
  questionCount: number
  chapters: MockExamChapter[]
}

export interface MockExamPaper {
  requestedQuestions: number
  totalQuestions: number
  availableQuestions: number
  suggestedDurationMinutes: number
  questions: Question[]
  sections: MockExamSection[]
}
