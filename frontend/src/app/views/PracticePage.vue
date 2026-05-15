<template>
  <div class="page-stack">
    <section class="panel-card practice-shell">
      <header class="practice-header">
        <div>
          <p class="eyebrow">{{ practiceModeLabel }}</p>
          <h2>{{ categoryName }}</h2>
          <p class="hero-copy subtle">
            {{ pageInfo }}
            <span v-if="currentChapter"> · 当前章节：{{ currentChapter.name }}</span>
          </p>
        </div>

        <div class="header-actions">
          <button class="ghost-btn" @click="toggleFavorite">
            {{ isFavorite ? '取消收藏' : '加入收藏' }}
          </button>
          <button class="ghost-btn" @click="goBack">
            {{ goBackLabel }}
          </button>
        </div>
      </header>

      <div v-if="showSourceTypeSwitch" class="status-strip practice-source-strip">
        <span>题源 {{ currentSourceTypeLabel }}</span>
        <div class="row-actions">
          <button class="ghost-btn small" :class="{ active: practiceSourceType === 0 }" @click="setPracticeSourceType(0)">
            混合随机
          </button>
          <button class="ghost-btn small" :class="{ active: practiceSourceType === 1 }" @click="setPracticeSourceType(1)">
            只练真题
          </button>
          <button class="ghost-btn small" :class="{ active: practiceSourceType === 2 }" @click="setPracticeSourceType(2)">
            只练模拟题
          </button>
        </div>
      </div>

      <div v-if="questions.length" class="status-strip practice-status-strip">
        <span>已作答：{{ answeredCount }} / {{ questions.length }}</span>
        <span>答对：{{ correctCount }}</span>
        <span>待完成：{{ remainingQuestions }}</span>
        <span>完成度：{{ progressPercent }}%</span>
      </div>
      <div v-if="questions.length" class="sheet-progress">
        <div class="sheet-progress-bar">
          <span :style="{ width: `${progressPercent}%` }"></span>
        </div>
      </div>

      <p v-if="actionMessage" class="form-success">{{ actionMessage }}</p>
      <p v-if="actionError" class="form-error">{{ actionError }}</p>

      <div v-if="loading" class="panel-card empty-state">正在加载练习题目...</div>
      <div v-else-if="loadError" class="panel-card empty-state">{{ loadError }}</div>

      <div v-else-if="currentQuestion" class="exam-layout practice-layout">
        <div :key="`question-${currentQuestion.id}`" class="panel-card question-panel">
          <div class="question-kicker">{{ questionMetaLine }}</div>

          <h3 class="question-title">{{ currentQuestion.content }}</h3>

          <div v-if="isChoiceQuestion" :key="`opt-${currentQuestion.id}`" class="option-grid">
            <button
              v-for="option in optionList"
              :key="`${currentQuestion.id}-${option.key}-${option.answerKey}`"
              class="option-btn"
              :class="{ active: isOptionActive(option.answerKey), multiple: isMultipleChoice }"
              :disabled="Boolean(currentResult)"
              @click="selectOption(option.answerKey)"
            >
              <strong>{{ option.key }}</strong>
              <span>{{ option.value }}</span>
            </button>
          </div>

          <textarea
            v-else
            v-model="answer"
            class="textarea"
            rows="5"
            placeholder="请输入你的答案"
            :disabled="Boolean(currentResult)"
            @input="persistDraft"
          />

          <div v-if="isMultipleChoice && selectedOptions.length" class="selected-answer">
            当前已选：{{ displayedSelectedOptions.join('、') }}
          </div>

          <div class="question-actions">
            <button
              class="primary-btn"
              :disabled="submitting || !canSubmit || Boolean(currentResult)"
              @click="submitCurrentAnswer"
            >
              {{ submitting ? '提交中...' : currentResult ? '已提交' : '提交答案' }}
            </button>
            <button class="ghost-btn" :disabled="currentIndex === 0" @click="previousQuestion">上一题</button>
            <button class="ghost-btn" :disabled="questions.length < 2" @click="skipQuestion">跳过</button>
            <button class="ghost-btn" :disabled="!currentResult || finishing" @click="nextQuestion">
              {{ nextButtonLabel }}
            </button>
          </div>

          <div
            v-if="currentResult"
            class="result-box"
            :class="{ success: currentResult.correct, danger: !currentResult.correct }"
          >
            <strong>{{ currentResult.correct ? '回答正确' : '回答错误' }}</strong>
            <p>你的答案：{{ formatAnswerForDisplay(currentResult.userAnswer) || '未填写' }}</p>
            <p>正确答案：{{ formatAnswerForDisplay(currentResult.correctAnswer) }}</p>
            <div class="detail-grid">
              <article class="feature-card detail-card answer-analysis-card">
                <strong>答案解析</strong>
                <div class="detail-scroll">
                  <p>{{ currentResult.analysis || '暂无解析' }}</p>
                </div>
              </article>
              <article class="feature-card detail-card">
                <strong>解题思路</strong>
                <div class="detail-scroll">
                  <p>{{ currentResult.solutionStrategy || currentQuestion.solutionStrategy || '暂无解题思路' }}</p>
                </div>
              </article>
            </div>

            <div class="ai-helper-row">
              <button class="ghost-btn small" type="button" @click="openAiPanel">DeepSeek</button>
              <span>本地 DeepSeek 辅助解释，以题库答案为准。</span>
            </div>

            <section v-if="aiPanelOpen" class="ai-helper-panel">
              <header class="ai-helper-head">
                <div>
                  <strong>AI 答疑</strong>
                  <p>当前模型为 {{ aiModel || 'deepseek-r1:7b' }}</p>
                </div>
                <button class="ghost-btn small" type="button" @click="closeAiPanel">关闭</button>
              </header>

              <div class="ai-message-list">
                <p v-if="!aiMessages.length" class="ai-empty-tip">
                  可以让 AI 用更通俗的话解释题目、指出易错点，或追问某个选项为什么不选。
                </p>
                <article
                  v-for="(message, index) in aiMessages"
                  :key="`${message.role}-${index}`"
                  class="ai-message"
                  :class="message.role"
                >
                  <strong>{{ message.role === 'user' ? '我' : 'AI' }}</strong>
                  <p>{{ message.content }}</p>
                </article>
              </div>

              <p v-if="aiError" class="form-error">{{ aiError }}</p>
              <div class="ai-input-row">
                <textarea
                  v-model="aiQuestion"
                  class="textarea ai-question-input"
                  rows="3"
                  placeholder="例如：为什么这个选项不对？请按知识点解释。"
                  :disabled="aiLoading"
                />
                <button class="primary-btn" type="button" :disabled="aiLoading || !aiQuestion.trim()" @click="sendAiQuestion">
                  {{ aiLoading ? '思考中...' : '发送' }}
                </button>
              </div>
            </section>
          </div>
        </div>

        <aside class="exam-sidebar practice-sheet sticky-sidebar">
          <article class="feature-card summary-card">
            <div class="sheet-card-head">
              <div>
                <strong>答题卡</strong>
                <p class="form-tip">{{ currentChapter ? currentChapter.name : currentRootCategory?.name || practiceModeLabel }}</p>
              </div>
              <span class="tag muted">{{ answeredCount }}/{{ questions.length }}</span>
            </div>

            <div class="sheet-summary-grid">
              <div class="summary-metric">
                <span>已作答</span>
                <strong>{{ answeredCount }}</strong>
              </div>
              <div class="summary-metric">
                <span>答对</span>
                <strong>{{ correctCount }}</strong>
              </div>
              <div class="summary-metric">
                <span>待完成</span>
                <strong>{{ remainingQuestions }}</strong>
              </div>
              <div class="summary-metric">
                <span>正确率</span>
                <strong>{{ currentAccuracy }}%</strong>
              </div>
            </div>

            <div class="sheet-legend">
              <span class="sheet-legend-item current">当前题</span>
              <span class="sheet-legend-item draft">已暂存</span>
              <span class="sheet-legend-item correct">答对</span>
              <span class="sheet-legend-item wrong">答错</span>
              <span class="sheet-legend-item multiple">多选</span>
            </div>
          </article>

          <article v-for="group in questionGroups" :key="group.label" class="feature-card summary-card sheet-group">
            <div class="category-topline">
              <strong>{{ group.label }}</strong>
              <small>{{ group.questions.length }} 题</small>
            </div>

            <div class="sheet-grid">
              <button
                v-for="item in group.questions"
                :key="item.id"
                class="sheet-item"
                :class="questionSheetClass(item)"
                :title="buildSheetTitle(item)"
                @click="jumpToQuestion(item.index)"
              >
                <small>{{ typeShortLabel(item.type) }}</small>
                <span>{{ item.groupIndex }}</span>
              </button>
            </div>
          </article>
        </aside>
      </div>

      <div v-else class="panel-card empty-state">{{ emptyStateMessage }}</div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router'
import { getCategories } from '@/app/api/categories'
import { addFavorite, getFavorites, removeFavorite } from '@/app/api/favorites'
import { askAiTutor } from '@/app/api/ai'
import { getQuestionsByCategory, getQuestionsByIds, submitAnswer } from '@/app/api/questions'
import { saveLastPracticeCategory } from '@/app/practice-resume'
import { createPracticeRecord } from '@/app/api/statistics'
import { addWrongQuestion } from '@/app/api/wrong'
import type { AiTutorMessage, Category, Question, SubmitResult } from '@/app/types'

interface PracticeSheetQuestion extends Question {
  index: number
  groupIndex: number
}

interface PresentedOption {
  key: string
  answerKey: string
  value: string
}

const route = useRoute()
const router = useRouter()

// 练习页的核心状态：
// questions 是本次练习题目，answerSheet/resultSheet/draftSheet 分别保存已答、判题结果和草稿。
const questions = ref<Question[]>([])
const categories = ref<Category[]>([])
const currentIndex = ref(0)
const answer = ref('')
const selectedOptions = ref<string[]>([])
const resultSheet = ref<Record<number, SubmitResult>>({})
const answerSheet = ref<Record<number, string>>({})
const draftSheet = ref<Record<number, string>>({})
const optionLayoutSheet = ref<Record<number, string[]>>({})
const submitting = ref(false)
const loading = ref(false)
const loadError = ref('')
const favoriteIds = ref<number[]>([])
const actionMessage = ref('')
const actionError = ref('')
const aiPanelOpen = ref(false)
const aiQuestion = ref('请用更通俗的话解释这道题为什么这样选，并指出我容易错在哪里。')
const aiMessages = ref<AiTutorMessage[]>([])
const aiLoading = ref(false)
const aiError = ref('')
const aiModel = ref('')
const finishing = ref(false)
const startTime = ref(Date.now())
const recording = ref(false)
const sessionRecorded = ref(false)
const PRACTICE_RECORD_ENDPOINT = '/api/statistics/record'

const parseQueryNumber = (value: unknown) => {
  const parsed = Number(value)
  return Number.isFinite(parsed) && parsed > 0 ? parsed : 0
}

const parseQueryNumberList = (value: unknown) => {
  // 从 URL query 里解析 questionIds=1,2,3，用于错题重练和收藏回练。
  const raw = Array.isArray(value) ? value.join(',') : typeof value === 'string' ? value : ''
  if (!raw) {
    return [] as number[]
  }

  return [...new Set(raw.split(',').map((item) => Number(item.trim())).filter((id) => Number.isFinite(id) && id > 0))]
}

const categoryId = computed(() => parseQueryNumber(route.query.categoryId))
const requestedQuestionId = computed(() => parseQueryNumber(route.query.questionId))
const requestedQuestionIds = computed(() => parseQueryNumberList(route.query.questionIds))
const practiceSourceType = computed(() => {
  const parsed = Number(route.query.sourceType)
  return parsed === 1 || parsed === 2 ? parsed : 0
})
const returnPath = computed(() => {
  if (route.query.from === 'favorite') {
    return '/favorite'
  }
  if (route.query.from === 'wrong-book') {
    return '/wrong-book'
  }
  return '/categories'
})

const currentQuestion = computed(() => questions.value[currentIndex.value] || null)
const currentResult = computed(() =>
  currentQuestion.value ? resultSheet.value[currentQuestion.value.id] || null : null
)
const selectedCategory = computed(() => categories.value.find((entry) => entry.id === categoryId.value) || null)
const currentQuestionCategory = computed(() =>
  categories.value.find((entry) => entry.id === currentQuestion.value?.categoryId) || null
)

const currentRootCategory = computed(() => {
  if (!selectedCategory.value && !currentQuestionCategory.value) {
    return null
  }

  if (selectedCategory.value && !selectedCategory.value.parentId) {
    return selectedCategory.value
  }

  const selectedParentId = selectedCategory.value?.parentId
  if (selectedParentId) {
    return categories.value.find((entry) => entry.id === selectedParentId) || selectedCategory.value
  }

  const currentParentId = currentQuestionCategory.value?.parentId
  if (currentParentId) {
    return categories.value.find((entry) => entry.id === currentParentId) || currentQuestionCategory.value
  }

  return currentQuestionCategory.value
})

const currentChapter = computed(() => {
  if (selectedCategory.value?.parentId) {
    return selectedCategory.value
  }
  if (currentQuestionCategory.value?.parentId) {
    return currentQuestionCategory.value
  }
  return null
})

const categoryName = computed(() => currentRootCategory.value?.name || selectedCategory.value?.name || '练习模式')
const practiceModeLabel = computed(() => {
  if (requestedQuestionIds.value.length && returnPath.value === '/wrong-book') {
    return '错题重练'
  }
  if (requestedQuestionIds.value.length && returnPath.value === '/favorite') {
    return '收藏回练'
  }
  return currentChapter.value || currentRootCategory.value?.practiceMode === 2 ? '章节练习' : '专题练习'
})
const goBackLabel = computed(() => {
  if (returnPath.value === '/favorite') {
    return '返回收藏夹'
  }
  if (returnPath.value === '/wrong-book') {
    return '返回错题本'
  }
  return '返回专题页'
})
const showSourceTypeSwitch = computed(() => !requestedQuestionIds.value.length && Boolean(categoryId.value))
const currentSourceTypeLabel = computed(() => {
  if (practiceSourceType.value === 1) {
    return '只练真题'
  }
  if (practiceSourceType.value === 2) {
    return '只练模拟题'
  }
  return '混合随机'
})
const pageInfo = computed(() =>
  questions.value.length ? `第 ${currentIndex.value + 1} / ${questions.value.length} 题` : '暂无题目'
)
const questionMetaLine = computed(() => {
  if (!currentQuestion.value) {
    return ''
  }
  const parts = [
    `第 ${currentIndex.value + 1} 题`,
    typeText(currentQuestion.value.type),
    difficultyText(currentQuestion.value.difficulty),
    sourceTypeText(currentQuestion.value.sourceType)
  ]
  if (currentChapter.value?.name) {
    parts.push(currentChapter.value.name)
  } else if (currentRootCategory.value?.name) {
    parts.push(currentRootCategory.value.name)
  }
  if (currentQuestion.value.source) {
    parts.push(currentQuestion.value.source)
  }
  return parts.join(' · ')
})
const emptyStateMessage = computed(() =>
  requestedQuestionIds.value.length
    ? '暂无可重练题目，可能已下线或被移除。'
    : practiceSourceType.value === 1
    ? '当前专题下暂无真题，请先切回混合随机或补充真题。'
    : practiceSourceType.value === 2
    ? '当前专题下暂无模拟题，请先切回混合随机或补充模拟题。'
    : '当前专题暂无题目，请先在后台补充题库。'
)
const isFavorite = computed(() => Boolean(currentQuestion.value && favoriteIds.value.includes(currentQuestion.value.id)))
const answeredCount = computed(() => Object.keys(answerSheet.value).length)
const correctCount = computed(() => Object.values(resultSheet.value).filter((item) => item.correct).length)
const remainingQuestions = computed(() => Math.max(questions.value.length - answeredCount.value, 0))
const progressPercent = computed(() =>
  questions.value.length ? Math.round((answeredCount.value / questions.value.length) * 100) : 0
)
const currentAccuracy = computed(() => (answeredCount.value ? Math.round((correctCount.value / answeredCount.value) * 100) : 0))
const nextButtonLabel = computed(() => {
  if (finishing.value) {
    return '结算中...'
  }
  return currentIndex.value === questions.value.length - 1 ? '完成练习' : '下一题'
})

const displayOptionKeys = ['A', 'B', 'C', 'D'] as const

const shuffleOptionKeys = (keys: string[]) => {
  const shuffled = [...keys]
  for (let index = shuffled.length - 1; index > 0; index -= 1) {
    const swapIndex = Math.floor(Math.random() * (index + 1))
    ;[shuffled[index], shuffled[swapIndex]] = [shuffled[swapIndex], shuffled[index]]
  }
  return shuffled
}

const shuffleQuestions = <T,>(items: T[]) => {
  const shuffled = [...items]
  for (let index = shuffled.length - 1; index > 0; index -= 1) {
    const swapIndex = Math.floor(Math.random() * (index + 1))
    ;[shuffled[index], shuffled[swapIndex]] = [shuffled[swapIndex], shuffled[index]]
  }
  return shuffled
}

const getQuestionOptionEntries = (question: Question) =>
  [
    { answerKey: 'A', value: question.optionA },
    { answerKey: 'B', value: question.optionB },
    { answerKey: 'C', value: question.optionC },
    { answerKey: 'D', value: question.optionD }
  ].filter((item): item is { answerKey: string; value: string } => Boolean(item.value))

const buildPresentedOptions = (question: Question | null): PresentedOption[] => {
  // 选项展示顺序会随机打乱，但 answerKey 保留原始 A/B/C/D，提交给后端时仍按原始答案判题。
  if (!question) {
    return []
  }

  const entries = getQuestionOptionEntries(question)
  const optionMap = new Map(entries.map((item) => [item.answerKey, item.value]))
  const layout = optionLayoutSheet.value[question.id] || entries.map((item) => item.answerKey)

  return layout
    .filter((answerKey) => optionMap.has(answerKey))
    .map((answerKey, index) => ({
      key: displayOptionKeys[index] || answerKey,
      answerKey,
      value: optionMap.get(answerKey) || ''
    }))
}

const initializeOptionLayouts = (records: Question[]) => {
  // 每道选择题只在加载时随机一次选项顺序，切题回来不会重新洗牌。
  optionLayoutSheet.value = Object.fromEntries(
    records
      .filter((item) => item.type === 1 || item.type === 5)
      .map((item) => [item.id, shuffleOptionKeys(getQuestionOptionEntries(item).map((entry) => entry.answerKey))])
  )
}

const optionList = computed(() => {
  return buildPresentedOptions(currentQuestion.value)
})

const isMultipleChoice = computed(() => currentQuestion.value?.type === 5)
const isChoiceQuestion = computed(() => optionList.value.length > 0)
const normalizedAnswer = computed(() =>
  isMultipleChoice.value ? [...selectedOptions.value].sort().join(',') : answer.value.trim()
)
const canSubmit = computed(() => normalizedAnswer.value.length > 0)
const displayedSelectedOptions = computed(() =>
  normalizedAnswer.value ? formatAnswerForDisplay(normalizedAnswer.value).split(/[、,]/).filter(Boolean) : []
)

const isAnsweredQuestion = (id: number) => Object.prototype.hasOwnProperty.call(answerSheet.value, id)
const isDraftQuestion = (id: number) => Object.prototype.hasOwnProperty.call(draftSheet.value, id) && !isAnsweredQuestion(id)

const questionGroups = computed(() => {
  const groups = [
    { label: '单选题', predicate: (item: Question) => item.type === 1 },
    { label: '多选题', predicate: (item: Question) => item.type === 5 },
    { label: '填空题', predicate: (item: Question) => item.type === 2 },
    { label: '简答题', predicate: (item: Question) => item.type === 4 },
    { label: '其他题型', predicate: (item: Question) => ![1, 2, 4, 5].includes(item.type) }
  ]

  return groups
    .map((group) => ({
      label: group.label,
      questions: questions.value
        .map((item, index) => ({ ...item, index }))
        .filter((item) => group.predicate(item))
        .map((item, groupIndex) => ({ ...item, groupIndex: groupIndex + 1 })) as PracticeSheetQuestion[]
    }))
    .filter((group) => group.questions.length)
})

const difficultyText = (difficulty: number) => ['基础', '提高', '冲刺'][difficulty - 1] || '未标注'

const typeText = (type: number) => {
  const map: Record<number, string> = {
    1: '单选题',
    2: '填空题',
    4: '简答题',
    5: '多选题'
  }
  return map[type] || '题目'
}

const sourceTypeText = (sourceType = 1) => (sourceType === 2 ? '模拟题' : '真题')

const typeShortLabel = (type: number) => {
  if (type === 1) {
    return '单'
  }
  if (type === 5) {
    return '多'
  }
  return '其'
}

const formatAnswerForDisplay = (rawAnswer: string) => {
  if (!rawAnswer || !currentQuestion.value || !isChoiceQuestion.value) {
    return rawAnswer
  }

  const displayMap = new Map(optionList.value.map((item) => [item.answerKey, item.key]))
  return rawAnswer
    .split(/[，,\s]+/)
    .map((item) => item.trim())
    .filter(Boolean)
    .map((item) => displayMap.get(item.toUpperCase()) || item.toUpperCase())
    .join('、')
}

const clearFeedback = () => {
  actionMessage.value = ''
  actionError.value = ''
}

const resetAiPanel = () => {
  aiPanelOpen.value = false
  aiQuestion.value = '请用更通俗的话解释这道题为什么这样选，并指出我容易错在哪里。'
  aiMessages.value = []
  aiLoading.value = false
  aiError.value = ''
  aiModel.value = ''
}

const openAiPanel = () => {
  if (!currentQuestion.value || !currentResult.value) {
    return
  }
  aiPanelOpen.value = true
  aiError.value = ''
}

const closeAiPanel = () => {
  aiPanelOpen.value = false
}

const sendAiQuestion = async () => {
  if (!currentQuestion.value || !currentResult.value || aiLoading.value) {
    return
  }

  const message = aiQuestion.value.trim()
  if (!message) {
    return
  }

  const history = [...aiMessages.value]
  aiMessages.value = [...aiMessages.value, { role: 'user', content: message }]
  aiQuestion.value = ''
  aiError.value = ''
  aiLoading.value = true

  try {
    const response = await askAiTutor({
      questionId: currentQuestion.value.id,
      userAnswer: currentResult.value.userAnswer,
      message,
      history
    })
    aiMessages.value = [...aiMessages.value, { role: 'assistant', content: response.answer }]
    aiModel.value = response.model
  } catch (err) {
    aiError.value = err instanceof Error ? err.message : 'AI答疑失败，请确认本地Ollama已启动'
  } finally {
    aiLoading.value = false
  }
}

const shouldIgnoreKeyboard = () => {
  const active = document.activeElement
  if (!(active instanceof HTMLElement)) {
    return false
  }
  const tag = active.tagName
  return tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT'
}

const persistDraft = () => {
  if (!currentQuestion.value || currentResult.value) {
    return
  }

  const nextDraftSheet = { ...draftSheet.value }
  if (normalizedAnswer.value) {
    nextDraftSheet[currentQuestion.value.id] = normalizedAnswer.value
  } else {
    delete nextDraftSheet[currentQuestion.value.id]
  }
  draftSheet.value = nextDraftSheet
}

const syncCurrentState = () => {
  if (!currentQuestion.value) {
    answer.value = ''
    selectedOptions.value = []
    return
  }

  const savedAnswer = answerSheet.value[currentQuestion.value.id] || draftSheet.value[currentQuestion.value.id] || ''
  if (currentQuestion.value.type === 5) {
    selectedOptions.value = savedAnswer ? savedAnswer.split(',').filter(Boolean) : []
    answer.value = ''
    return
  }

  answer.value = savedAnswer
  selectedOptions.value = []
}

const resetSessionState = () => {
  answer.value = ''
  selectedOptions.value = []
  resultSheet.value = {}
  answerSheet.value = {}
  draftSheet.value = {}
  optionLayoutSheet.value = {}
  startTime.value = Date.now()
  sessionRecorded.value = false
  resetAiPanel()
  clearFeedback()
}

const loadAllQuestions = async (targetCategoryId: number, sourceType: number) => {
  // 后端是分页接口；练习页为了随机出题，会循环把当前专题下的题目全部拉完。
  const size = 50
  let page = 1
  let totalPages = 1
  const records: Question[] = []

  while (page <= totalPages) {
    const response = await getQuestionsByCategory(targetCategoryId, page, size, sourceType || undefined)
    records.push(...response.records)

    const resolvedPages =
      response.pages ||
      (response.total ? Math.ceil(response.total / (response.size || size)) : 0)

    totalPages = Math.max(totalPages, resolvedPages || (response.records.length === size ? page + 1 : page))

    if (!response.records.length || response.records.length < size) {
      break
    }

    page += 1
  }

  return records
}

const loadQuestionsByIds = async (ids: number[]) => {
  const records = await getQuestionsByIds(ids)
  const recordMap = new Map(records.map((item) => [item.id, item]))
  return ids.map((id) => recordMap.get(id)).filter((item): item is Question => Boolean(item))
}

const loadPage = async () => {
  // 页面加载主流程：拿分类和收藏 -> 按 URL 判断练习模式 -> 加载题目 -> 随机题序和选项。
  loading.value = true
  loadError.value = ''
  clearFeedback()

  try {
    const [categoryList, favoriteList] = await Promise.all([getCategories(), getFavorites()])
    categories.value = categoryList
    favoriteIds.value = favoriteList.map((item) => item.questionId)

    const hasQuestionSet = requestedQuestionIds.value.length > 0
    const fallbackCategoryId = categoryList[0]?.id || 0
    const targetCategoryId = categoryId.value || (hasQuestionSet ? 0 : fallbackCategoryId)
    if (!targetCategoryId && !hasQuestionSet) {
      questions.value = []
      currentIndex.value = 0
      resetSessionState()
      return
    }

    let records: Question[] = []
    if (hasQuestionSet) {
      records = await loadQuestionsByIds(requestedQuestionIds.value)
      const firstCategoryId = records[0]?.categoryId || categoryId.value || 0
      if (firstCategoryId) {
        saveLastPracticeCategory(firstCategoryId, 0)
      }
    } else {
      saveLastPracticeCategory(targetCategoryId, practiceSourceType.value)
      records = await loadAllQuestions(targetCategoryId, practiceSourceType.value)
    }

    records = shuffleQuestions(records)
    questions.value = records

    const matchedIndex = hasQuestionSet
      ? 0
      : requestedQuestionId.value
      ? records.findIndex((item) => item.id === requestedQuestionId.value)
      : -1

    currentIndex.value = matchedIndex >= 0 ? matchedIndex : 0
    resetSessionState()
    initializeOptionLayouts(records)
    syncCurrentState()
  } catch (err) {
    questions.value = []
    currentIndex.value = 0
    resetSessionState()
    loadError.value = err instanceof Error ? err.message : '加载题目失败'
  } finally {
    loading.value = false
  }
}

const setPracticeSourceType = async (sourceType: number) => {
  if (practiceSourceType.value === sourceType) {
    return
  }

  const nextQuery = { ...route.query }
  if (sourceType) {
    nextQuery.sourceType = String(sourceType)
  } else {
    delete nextQuery.sourceType
  }

  await router.replace({
    path: route.path,
    query: nextQuery
  })
}

const isOptionActive = (key: string) =>
  isMultipleChoice.value ? selectedOptions.value.includes(key) : answer.value === key

const selectOption = (key: string) => {
  if (currentResult.value) {
    return
  }

  if (isMultipleChoice.value) {
    selectedOptions.value = selectedOptions.value.includes(key)
      ? selectedOptions.value.filter((item) => item !== key)
      : [...selectedOptions.value, key].sort()
    persistDraft()
    return
  }

  answer.value = key
  persistDraft()
}

const submitCurrentAnswer = async () => {
  // 提交答案后，后端返回正确答案和解析；如果答错，再写入错题本。
  if (!currentQuestion.value || !canSubmit.value || currentResult.value) {
    return
  }

  submitting.value = true
  clearFeedback()
  try {
    const questionId = currentQuestion.value.id
    const submitResult = await submitAnswer(questionId, normalizedAnswer.value)
    resultSheet.value = {
      ...resultSheet.value,
      [questionId]: submitResult
    }
    answerSheet.value = {
      ...answerSheet.value,
      [questionId]: normalizedAnswer.value
    }
    draftSheet.value = {
      ...draftSheet.value,
      [questionId]: normalizedAnswer.value
    }

    if (!submitResult.correct) {
      await addWrongQuestion(questionId, normalizedAnswer.value)
    }
  } catch (err) {
    actionError.value = err instanceof Error ? err.message : '提交答案失败'
  } finally {
    submitting.value = false
  }
}

const changeQuestion = (index: number) => {
  if (index < 0 || index >= questions.value.length) {
    return
  }
  currentIndex.value = index
  clearFeedback()
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

const nextQuestion = async () => {
  if (!currentQuestion.value) {
    return
  }

  if (currentIndex.value === questions.value.length - 1) {
    finishing.value = true
    clearFeedback()
    try {
      await persistPracticeRecord(Math.max(answeredCount.value, 1))
      router.push('/profile')
    } catch (err) {
      actionError.value = err instanceof Error ? err.message : '练习记录保存失败，请重试'
    } finally {
      finishing.value = false
    }
    return
  }

  changeQuestion(currentIndex.value + 1)
}

const previousQuestion = () => {
  if (currentIndex.value === 0) {
    return
  }
  changeQuestion(currentIndex.value - 1)
}

const skipQuestion = () => {
  if (!questions.value.length) {
    return
  }

  if (currentIndex.value < questions.value.length - 1) {
    changeQuestion(currentIndex.value + 1)
    return
  }

  changeQuestion(0)
}

const jumpToQuestion = (index: number) => {
  changeQuestion(index)
}

const buildSheetTitle = (item: PracticeSheetQuestion) => {
  const state = resultSheet.value[item.id]
  const base = `第 ${item.index + 1} 题 · ${typeText(item.type)}`
  if (!state && isDraftQuestion(item.id)) {
    return `${base} · 已暂存`
  }
  if (!state) {
    return base
  }
  return `${base} · ${state.correct ? '答对' : '答错'}`
}

const questionSheetClass = (item: PracticeSheetQuestion) => ({
  current: item.index === currentIndex.value,
  draft: isDraftQuestion(item.id),
  answered: isAnsweredQuestion(item.id),
  multiple: item.type === 5,
  correct: Boolean(resultSheet.value[item.id]?.correct),
  wrong: resultSheet.value[item.id] ? !resultSheet.value[item.id].correct : false
})

const toggleFavorite = async () => {
  if (!currentQuestion.value) {
    return
  }

  clearFeedback()
  try {
    if (isFavorite.value) {
      await removeFavorite(currentQuestion.value.id)
      favoriteIds.value = favoriteIds.value.filter((id) => id !== currentQuestion.value?.id)
      actionMessage.value = '已取消收藏'
      return
    }

    await addFavorite(currentQuestion.value.id)
    favoriteIds.value = [...favoriteIds.value, currentQuestion.value.id]
    actionMessage.value = '已加入收藏'
  } catch (err) {
    actionError.value = err instanceof Error ? err.message : '收藏操作失败'
  }
}

const shouldSkipRecordPersist = () =>
  !currentQuestion.value || recording.value || sessionRecorded.value || answeredCount.value <= 0

const buildPracticeRecordPayload = (totalQuestions: number) => {
  if (!currentQuestion.value) {
    return null
  }

  return {
    categoryId: categoryId.value || currentQuestion.value.categoryId,
    totalQuestions: Math.max(1, totalQuestions),
    correctCount: correctCount.value,
    duration: Math.max(1, Math.round((Date.now() - startTime.value) / 1000))
  }
}

const persistPracticeRecord = async (totalQuestions: number) => {
  // 练习记录用于统计正确率、学习时长、历史记录；同一轮练习只保存一次，避免重复统计。
  if (shouldSkipRecordPersist()) {
    return
  }

  const payload = buildPracticeRecordPayload(totalQuestions)
  if (!payload) {
    return
  }

  recording.value = true
  try {
    await createPracticeRecord(payload)
    sessionRecorded.value = true
  } finally {
    recording.value = false
  }
}

const persistPracticeRecordOnPageHide = () => {
  // 页面关闭时普通 axios 请求可能被浏览器取消，所以这里用 fetch keepalive 尽量补交记录。
  if (finishing.value || loading.value || shouldSkipRecordPersist()) {
    return
  }

  const payload = buildPracticeRecordPayload(answeredCount.value)
  const token = localStorage.getItem('token')
  if (!payload || !token) {
    return
  }

  void fetch(PRACTICE_RECORD_ENDPOINT, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json;charset=utf-8',
      Authorization: `Bearer ${token}`
    },
    body: JSON.stringify(payload),
    keepalive: true
  }).catch(() => {})
}

const persistProgressIfNeeded = async () => {
  if (finishing.value || loading.value) {
    return
  }
  await persistPracticeRecord(answeredCount.value)
}

const goBack = async () => {
  await persistProgressIfNeeded()
  await router.push(returnPath.value)
}

const handleKeyboardShortcut = (event: KeyboardEvent) => {
  if (event.key === 'ArrowLeft' && currentIndex.value > 0 && !shouldIgnoreKeyboard()) {
    event.preventDefault()
    previousQuestion()
    return
  }

  if (event.key === 'ArrowRight' && currentResult.value && !finishing.value && !shouldIgnoreKeyboard()) {
    event.preventDefault()
    void nextQuestion()
    return
  }

  if ((event.ctrlKey || event.metaKey) && event.key === 'Enter' && canSubmit.value && !currentResult.value) {
    event.preventDefault()
    void submitCurrentAnswer()
  }
}

const handlePageHide = () => {
  persistPracticeRecordOnPageHide()
}

watch(
  () => `${categoryId.value}:${requestedQuestionId.value}:${requestedQuestionIds.value.join(',')}:${returnPath.value}:${practiceSourceType.value}`,
  () => {
    void loadPage()
  }
)

watch(currentQuestion, () => {
  resetAiPanel()
  syncCurrentState()
})

onMounted(() => {
  void loadPage()
  window.addEventListener('keydown', handleKeyboardShortcut)
  window.addEventListener('pagehide', handlePageHide)
})

onBeforeRouteLeave(async () => {
  await persistProgressIfNeeded()
})

onBeforeUnmount(() => {
  window.removeEventListener('keydown', handleKeyboardShortcut)
  window.removeEventListener('pagehide', handlePageHide)
})
</script>
