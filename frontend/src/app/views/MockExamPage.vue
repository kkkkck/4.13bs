<template>
  <div class="page-stack mock-exam-page">
    <section class="panel-card page-intro-card compact-overview-card mock-intro-card">
      <div>
        <p class="eyebrow">模拟考试</p>
        <h2>按考试大纲比例组一套客观题模考。</h2>
      </div>

      <div class="hero-aside compact-overview-aside">
        <div class="status-strip compact-overview-strip">
          <span>专题 {{ rootCategories.length }}</span>
          <span>预计 {{ paper?.suggestedDurationMinutes || suggestedMinutes }} 分钟</span>
        </div>
      </div>
    </section>

    <section v-if="!paper" class="panel-card">
      <div class="panel-head">
        <div>
          <h3>组卷设置</h3>
        </div>
      </div>

      <p v-if="message" class="form-success">{{ message }}</p>
      <p v-if="error" class="form-error">{{ error }}</p>

      <div class="mock-config-grid">
        <label class="field-block">
          <span>题量</span>
          <select v-model.number="totalQuestions" class="input select">
            <option :value="16">16 题 · 单选摸底</option>
            <option :value="33">33 题 · 客观题标准模考</option>
            <option :value="50">50 题 · 强化客观题</option>
          </select>
          <small class="form-tip">按大纲比例抽题。</small>
        </label>
      </div>

      <div class="row-actions">
        <button class="primary-btn" :disabled="loading || !rootCategories.length" @click="handleGenerate">
          {{ loading ? '组卷中...' : '生成试卷' }}
        </button>
      </div>

      <div v-if="loading" class="empty-state">正在生成模拟试卷...</div>

      <div v-else-if="rootCategories.length" class="compact-row-list">
        <article v-for="category in rootCategories" :key="category.id" class="compact-row">
          <div class="compact-row-main">
            <strong>{{ category.name }}</strong>
            <div class="compact-row-meta">
              <span>{{ category.practiceMode === 2 ? '章节专题' : '综合专题' }}</span>
              <span>{{ syllabusWeightLabel(category.id) }}</span>
              <span>{{ chapterCount(category.id) }} 个章节</span>
            </div>
            <div v-if="chapterDisplayLabels(category.id).length" class="chapter-pills compact-inline-pills mock-chapter-pills">
              <span
                v-for="label in chapterDisplayLabels(category.id)"
                :key="`${category.id}-${label}`"
                class="chapter-pill mock-chapter-pill"
              >
                {{ label }}
              </span>
            </div>
          </div>
        </article>
      </div>

      <div v-else class="empty-state">暂无可用于组卷的专题。</div>
    </section>

    <section v-else class="panel-card">
      <div class="panel-head">
        <div>
          <h3>{{ finished ? '模拟考试已完成' : '模拟考试进行中' }}</h3>
          <p>{{ pageInfo }} · 已用时 {{ elapsedText }} · {{ finished ? `正确率 ${accuracyText}` : '交卷后统一判分' }}</p>
        </div>

        <div class="row-actions">
          <button class="ghost-btn" @click="resetPaper">重新组卷</button>
        </div>
      </div>

      <div class="status-strip practice-status-strip">
        <span>已作答：{{ answeredCount }} / {{ paper.questions.length }}</span>
        <span>答对：{{ finished ? correctCount : '待交卷' }}</span>
        <span>待完成：{{ remainingCount }}</span>
        <span>预计用时：{{ paper.suggestedDurationMinutes }} 分钟</span>
      </div>
      <div class="sheet-progress">
        <div class="sheet-progress-bar">
          <span :style="{ width: `${progressPercent}%` }"></span>
        </div>
      </div>

      <p v-if="message" class="form-success">{{ message }}</p>
      <p v-if="error" class="form-error">{{ error }}</p>

      <div class="exam-layout">
        <div v-if="currentQuestion" :key="`question-${currentQuestion.id}`" class="panel-card question-panel">
          <div class="question-kicker">{{ questionMetaLine }}</div>

          <h3 class="question-title">{{ currentQuestion.content }}</h3>

          <div v-if="isChoiceQuestion" :key="`opt-${currentQuestion.id}`" class="option-grid">
            <button
              v-for="option in optionList"
              :key="`${currentQuestion.id}-${option.key}-${option.answerKey}`"
              class="option-btn"
              :class="{ active: isOptionActive(option.answerKey), multiple: isMultipleChoice }"
              :disabled="finished"
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
            :disabled="finished"
            @input="persistDraft"
          />

          <div v-if="isMultipleChoice && selectedOptions.length" class="selected-answer">
            当前已选：{{ displayedSelectedOptions.join('、') }}
          </div>

          <div class="question-actions">
            <button
              class="primary-btn"
              :disabled="finished || submitting || !canSubmit"
              @click="saveCurrentAnswer"
            >
              {{ submitting ? '暂存中...' : finished ? '已交卷' : '暂存答案' }}
            </button>
            <button class="ghost-btn" :disabled="currentIndex === 0" @click="previousQuestion">上一题</button>
            <button class="ghost-btn" :disabled="savingRecord" @click="nextQuestion">
              {{ currentIndex === paper.questions.length - 1 ? '交卷' : '下一题' }}
            </button>
          </div>

          <div
            v-if="finished && currentResult"
            class="result-box"
            :class="{ success: currentResult.correct, danger: !currentResult.correct }"
          >
            <strong>{{ currentResult.correct ? '回答正确' : '回答错误' }}</strong>
            <p>你的答案：{{ formatAnswerForDisplay(currentResult.userAnswer) || '未填写' }}</p>
            <p>正确答案：{{ formatAnswerForDisplay(currentResult.correctAnswer) }}</p>
            <div class="detail-grid">
              <article class="feature-card detail-card">
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
          </div>
        </div>

        <aside class="exam-sidebar sticky-sidebar">
          <article class="panel-card summary-card">
            <div class="sheet-card-head">
              <div>
                <strong>答题卡</strong>
                <p class="form-tip">{{ finished ? '已交卷。' : '交卷后统一判分。' }}</p>
              </div>
              <span class="tag muted">{{ answeredCount }}/{{ paper.questions.length }}</span>
            </div>

            <div class="sheet-summary-grid">
              <div class="summary-metric">
                <span>已作答</span>
                <strong>{{ answeredCount }}</strong>
              </div>
              <div class="summary-metric">
                <span>待完成</span>
                <strong>{{ remainingCount }}</strong>
              </div>
              <div class="summary-metric">
                <span>状态</span>
                <strong>{{ finished ? '已交卷' : '作答中' }}</strong>
              </div>
              <div class="summary-metric">
                <span>正确率</span>
                <strong>{{ finished ? accuracyText : '--' }}</strong>
              </div>
            </div>

            <div class="sheet-legend">
              <span class="sheet-legend-item current">当前题</span>
              <span class="sheet-legend-item draft">暂存中</span>
              <span class="sheet-legend-item answered">已作答</span>
              <span v-if="finished" class="sheet-legend-item correct">答对</span>
              <span v-if="finished" class="sheet-legend-item wrong">答错</span>
              <span class="sheet-legend-item multiple">多选</span>
            </div>
          </article>

          <article v-for="group in questionGroups" :key="group.label" class="panel-card summary-card sheet-group">
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
                <span>{{ item.index + 1 }}</span>
              </button>
            </div>
          </article>
        </aside>
      </div>
    </section>

    <section v-if="finished && paper" class="panel-card">
      <div class="panel-head">
        <div>
          <h3>本次结果</h3>
          <p>{{ recordSaved ? '成绩已写入练习历史。' : '本次结算已完成。' }}</p>
        </div>
      </div>

      <div class="stats-grid compact-stats">
        <article class="metric-card tiny">
          <span>正确题数</span>
          <strong>{{ correctCount }}</strong>
          <small>共 {{ paper.questions.length }} 题</small>
        </article>
        <article class="metric-card tiny">
          <span>正确率</span>
          <strong>{{ accuracyText }}</strong>
          <small>按整卷交卷结果统计</small>
        </article>
        <article class="metric-card tiny">
          <span>用时</span>
          <strong>{{ elapsedText }}</strong>
          <small>建议控制在 {{ paper.suggestedDurationMinutes }} 分钟内</small>
        </article>
      </div>

      <div class="row-actions">
        <button class="primary-btn" @click="handleGenerate">重新组卷</button>
      </div>

      <div class="compact-row-list">
        <div class="compact-row compact-row-static">
          <div class="compact-row-main">
            <strong>错题回看</strong>
            <div class="compact-row-meta">
              <span>{{ wrongQuestions.length }} 题</span>
            </div>
            <div v-if="wrongQuestions.length" class="chapter-pills compact-inline-pills">
              <button
                v-for="item in wrongQuestions"
                :key="item.id"
                class="chapter-pill action"
                @click="jumpToQuestion(item.index)"
              >
                {{ item.index + 1 }}
              </button>
            </div>
            <p v-else class="form-tip">本次全对。</p>
          </div>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { getCategories } from '@/app/api/categories'
import { generateMockExam } from '@/app/api/mock-exam'
import { submitAnswer } from '@/app/api/questions'
import { createPracticeRecord } from '@/app/api/statistics'
import type { Category, MockExamPaper, Question, SubmitResult } from '@/app/types'

interface MockSheetQuestion extends Question {
  index: number
}

interface PresentedOption {
  key: string
  answerKey: string
  value: string
}

const categories = ref<Category[]>([])
const totalQuestions = ref(33)
const loading = ref(false)
const submitting = ref(false)
const error = ref('')
const message = ref('')
const paper = ref<MockExamPaper | null>(null)
const currentIndex = ref(0)
const answer = ref('')
const selectedOptions = ref<string[]>([])
const resultSheet = ref<Record<number, SubmitResult>>({})
const answerSheet = ref<Record<number, string>>({})
const draftSheet = ref<Record<number, string>>({})
const optionLayoutSheet = ref<Record<number, string[]>>({})
const finished = ref(false)
const savingRecord = ref(false)
const recordSaved = ref(false)
const startTime = ref(Date.now())
const elapsedSeconds = ref(0)

let timer: number | null = null
const SYLLABUS_WEIGHT_MAP: Record<number, string> = {
  1: '约 22%',
  2: '约 35%（毛中特 13% + 新思想 22%）',
  3: '约 15%',
  4: '约 15%',
  5: '约 13%'
}

const rootCategories = computed(() => categories.value.filter((item) => !item.parentId))
const suggestedMinutes = computed(() => Math.max(30, totalQuestions.value * 2))
const currentQuestion = computed(() => paper.value?.questions[currentIndex.value] || null)
const currentResult = computed(() =>
  currentQuestion.value && finished.value ? resultSheet.value[currentQuestion.value.id] || null : null
)
const currentCategory = computed(() =>
  categories.value.find((item) => item.id === currentQuestion.value?.categoryId) || null
)
const currentRootCategory = computed(() => {
  const current = currentCategory.value
  if (!current) {
    return null
  }
  if (!current.parentId) {
    return current
  }
  return categories.value.find((item) => item.id === current.parentId) || current
})
const questionLocationText = computed(() => {
  if (!currentCategory.value) {
    return '模拟考试'
  }
  if (!currentCategory.value.parentId) {
    return currentCategory.value.name
  }
  return `${currentRootCategory.value?.name || '专题'} · ${currentCategory.value.name}`
})
const pageInfo = computed(() =>
  paper.value ? `第 ${currentIndex.value + 1} / ${paper.value.questions.length} 题` : '尚未组卷'
)
const questionMetaLine = computed(() => {
  if (!currentQuestion.value) {
    return ''
  }
  return [
    `第 ${currentIndex.value + 1} 题`,
    typeText(currentQuestion.value.type),
    difficultyText(currentQuestion.value.difficulty),
    sourceTypeText(currentQuestion.value.sourceType),
    questionLocationText.value
  ]
    .filter(Boolean)
    .join(' · ')
})
const answeredCount = computed(() => Object.keys(answerSheet.value).length)
const correctCount = computed(() => Object.values(resultSheet.value).filter((item) => item.correct).length)
const remainingCount = computed(() => Math.max((paper.value?.questions.length || 0) - answeredCount.value, 0))
const progressPercent = computed(() => {
  const total = paper.value?.questions.length || 0
  return total ? Math.round((answeredCount.value / total) * 100) : 0
})
const accuracyText = computed(() => {
  if (!answeredCount.value || !finished.value) {
    return '0%'
  }
  return `${Math.round((correctCount.value / answeredCount.value) * 100)}%`
})
const elapsedText = computed(() => {
  const minutes = Math.floor(elapsedSeconds.value / 60)
  const seconds = elapsedSeconds.value % 60
  return `${minutes} 分 ${String(seconds).padStart(2, '0')} 秒`
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

const getQuestionOptionEntries = (question: Question) =>
  [
    { answerKey: 'A', value: question.optionA },
    { answerKey: 'B', value: question.optionB },
    { answerKey: 'C', value: question.optionC },
    { answerKey: 'D', value: question.optionD }
  ].filter((item): item is { answerKey: string; value: string } => Boolean(item.value))

const buildPresentedOptions = (question: Question | null): PresentedOption[] => {
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
const findNextIndex = (predicate: (item: Question) => boolean) => {
  const questions = paper.value?.questions || []
  if (!questions.length) {
    return -1
  }

  for (let offset = 1; offset < questions.length; offset += 1) {
    const index = (currentIndex.value + offset) % questions.length
    if (predicate(questions[index])) {
      return index
    }
  }

  return -1
}
const nextUnansweredIndex = computed(() => findNextIndex((item) => !isAnsweredQuestion(item.id)))
const wrongQuestions = computed(() => {
  const questions = paper.value?.questions || []
  return questions
    .map((item, index) => ({ ...item, index }))
    .filter((item) => resultSheet.value[item.id] && !resultSheet.value[item.id].correct)
})

const questionGroups = computed(() => {
  const chunkSize = 25
  const list = (paper.value?.questions || []).map((item, index) => ({ ...item, index })) as MockSheetQuestion[]
  const groups: Array<{ label: string; questions: MockSheetQuestion[] }> = []

  for (let start = 0; start < list.length; start += chunkSize) {
    const chunk = list.slice(start, start + chunkSize)
    groups.push({
      label: `第 ${start + 1} - ${start + chunk.length} 题`,
      questions: chunk
    })
  }

  return groups
})

const chaptersOf = (parentId: number) => categories.value.filter((item) => item.parentId === parentId)
const chapterCount = (parentId: number) => chaptersOf(parentId).length
const syllabusWeightLabel = (categoryId: number) => SYLLABUS_WEIGHT_MAP[categoryId] || '纲要专题'
const chapterLabelOf = (name: string) => {
  const normalizedName = name.trim()
  if (/^(导论|绪论)/.test(normalizedName)) {
    return normalizedName.startsWith('绪论') ? '绪论' : '导论'
  }

  const chapterMatch = normalizedName.match(/第[一二三四五六七八九十百\d]+章/)
  return chapterMatch ? chapterMatch[0] : ''
}
const compactRawChapterName = (name: string) => {
  const normalizedName = name.trim()
  return normalizedName.length > 8 ? `${normalizedName.slice(0, 8)}…` : normalizedName
}
const chapterDisplayLabels = (parentId: number) => {
  const chapters = chaptersOf(parentId)
  const labels: string[] = []
  const seen = new Set<string>()

  for (const chapter of chapters) {
    const label = chapterLabelOf(chapter.name || '')
    if (label && !seen.has(label)) {
      seen.add(label)
      labels.push(label)
    }
  }

  if (labels.length) {
    return labels
  }

  return chapters.map((chapter) => compactRawChapterName(chapter.name || '')).filter(Boolean)
}

const difficultyText = (difficulty: number) => ['基础', '提高', '冲刺'][difficulty - 1] || '未标注'

const typeText = (type: number) => {
  const map: Record<number, string> = {
    1: '单选题',
    2: '填空题',
    4: '简答题',
    5: '多选题'
  }
  return map[type] || '其他题型'
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
  error.value = ''
  message.value = ''
}

const shouldIgnoreKeyboard = () => {
  const active = document.activeElement
  if (!(active instanceof HTMLElement)) {
    return false
  }
  const tag = active.tagName
  return tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT'
}

const stopTimer = () => {
  if (timer) {
    window.clearInterval(timer)
    timer = null
  }
}

const startTimer = () => {
  stopTimer()
  elapsedSeconds.value = 0
  startTime.value = Date.now()
  timer = window.setInterval(() => {
    elapsedSeconds.value = Math.max(0, Math.round((Date.now() - startTime.value) / 1000))
  }, 1000)
}

const persistDraft = () => {
  if (!currentQuestion.value || finished.value) {
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

const loadCategories = async () => {
  try {
    categories.value = await getCategories()
  } catch (err) {
    categories.value = []
    error.value = err instanceof Error ? err.message : '加载专题失败'
  }
}

const resetPaper = () => {
  paper.value = null
  currentIndex.value = 0
  answer.value = ''
  selectedOptions.value = []
  resultSheet.value = {}
  answerSheet.value = {}
  draftSheet.value = {}
  optionLayoutSheet.value = {}
  finished.value = false
  savingRecord.value = false
  recordSaved.value = false
  elapsedSeconds.value = 0
  clearFeedback()
  stopTimer()
}

const handleGenerate = async () => {
  clearFeedback()
  loading.value = true

  try {
    const generatedPaper = await generateMockExam(totalQuestions.value)
    paper.value = generatedPaper
    currentIndex.value = 0
    resultSheet.value = {}
    answerSheet.value = {}
    draftSheet.value = {}
    initializeOptionLayouts(generatedPaper.questions)
    finished.value = false
    savingRecord.value = false
    recordSaved.value = false
    syncCurrentState()
    startTimer()
    message.value = `模拟卷已生成，共 ${generatedPaper.totalQuestions} 题。`
  } catch (err) {
    resetPaper()
    error.value = err instanceof Error ? err.message : '生成模拟试卷失败'
  } finally {
    loading.value = false
  }
}

const isOptionActive = (key: string) =>
  isMultipleChoice.value ? selectedOptions.value.includes(key) : answer.value === key

const selectOption = (key: string) => {
  if (finished.value) {
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

const saveCurrentAnswer = () => {
  if (!currentQuestion.value || !canSubmit.value || finished.value) {
    return
  }

  clearFeedback()
  submitting.value = true
  answerSheet.value = {
    ...answerSheet.value,
    [currentQuestion.value.id]: normalizedAnswer.value
  }
  draftSheet.value = {
    ...draftSheet.value,
    [currentQuestion.value.id]: normalizedAnswer.value
  }
  message.value = '本题已暂存'
  submitting.value = false
}

const saveCurrentAnswerSilently = () => {
  if (!currentQuestion.value || !canSubmit.value || finished.value) {
    return
  }

  answerSheet.value = {
    ...answerSheet.value,
    [currentQuestion.value.id]: normalizedAnswer.value
  }
  draftSheet.value = {
    ...draftSheet.value,
    [currentQuestion.value.id]: normalizedAnswer.value
  }
}

const saveExamRecord = async () => {
  if (!paper.value || recordSaved.value || savingRecord.value) {
    return
  }

  savingRecord.value = true
  try {
    await createPracticeRecord({
      categoryId: 0,
      totalQuestions: paper.value.questions.length,
      correctCount: correctCount.value,
      duration: Math.max(1, elapsedSeconds.value)
    })
    recordSaved.value = true
    message.value = '模拟考试已完成，成绩已写入练习历史。'
  } catch (err) {
    error.value = err instanceof Error ? err.message : '模拟考试记录保存失败'
    message.value = '模拟考试已完成。'
  } finally {
    savingRecord.value = false
  }
}

const finishExam = async () => {
  if (!paper.value || finished.value) {
    return
  }

  saveCurrentAnswerSilently()

  if (answeredCount.value < paper.value.questions.length) {
    error.value = '还有未作答题目。'
    if (nextUnansweredIndex.value >= 0) {
      changeQuestion(nextUnansweredIndex.value)
    }
    return
  }

  savingRecord.value = true
  clearFeedback()
  try {
    const entries = await Promise.all(
      paper.value.questions.map(async (question) => {
        const result = await submitAnswer(question.id, answerSheet.value[question.id] || '')
        return [question.id, result] as const
      })
    )
    resultSheet.value = Object.fromEntries(entries)
    finished.value = true
    stopTimer()
    await saveExamRecord()
  } catch (err) {
    error.value = err instanceof Error ? err.message : '交卷失败，请稍后重试'
  } finally {
    savingRecord.value = false
  }
}

const changeQuestion = (index: number) => {
  const total = paper.value?.questions.length || 0
  if (index < 0 || index >= total) {
    return
  }
  currentIndex.value = index
  clearFeedback()
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

const previousQuestion = () => {
  if (currentIndex.value === 0) {
    return
  }
  saveCurrentAnswerSilently()
  changeQuestion(currentIndex.value - 1)
}

const nextQuestion = async () => {
  if (!paper.value) {
    return
  }

  if (currentIndex.value === paper.value.questions.length - 1) {
    await finishExam()
    return
  }

  saveCurrentAnswerSilently()
  changeQuestion(currentIndex.value + 1)
}

const jumpToQuestion = (index: number) => {
  saveCurrentAnswerSilently()
  changeQuestion(index)
}

const buildSheetTitle = (item: MockSheetQuestion) => {
  const base = `第 ${item.index + 1} 题 · ${typeText(item.type)}`
  if (!finished.value) {
    if (isDraftQuestion(item.id)) {
      return `${base} · 暂存中`
    }
    if (isAnsweredQuestion(item.id)) {
      return `${base} · 已作答`
    }
    return base
  }

  const state = resultSheet.value[item.id]
  if (!state) {
    return base
  }
  return `${base} · ${state.correct ? '答对' : '答错'}`
}

const questionSheetClass = (item: MockSheetQuestion) => ({
  current: item.index === currentIndex.value,
  draft: isDraftQuestion(item.id),
  answered: isAnsweredQuestion(item.id),
  multiple: item.type === 5,
  correct: finished.value && Boolean(resultSheet.value[item.id]?.correct),
  wrong: finished.value && resultSheet.value[item.id] ? !resultSheet.value[item.id].correct : false
})

const handleKeyboardShortcut = (event: KeyboardEvent) => {
  if (event.key === 'ArrowLeft' && currentIndex.value > 0 && !shouldIgnoreKeyboard()) {
    event.preventDefault()
    previousQuestion()
    return
  }

  if (event.key === 'ArrowRight' && !shouldIgnoreKeyboard()) {
    event.preventDefault()
    void nextQuestion()
    return
  }

  if ((event.ctrlKey || event.metaKey) && event.key === 'Enter' && canSubmit.value && !finished.value) {
    event.preventDefault()
    saveCurrentAnswer()
  }
}

watch(currentQuestion, () => {
  syncCurrentState()
})

onMounted(async () => {
  await loadCategories()
  window.addEventListener('keydown', handleKeyboardShortcut)
})

onBeforeUnmount(() => {
  stopTimer()
  window.removeEventListener('keydown', handleKeyboardShortcut)
})
</script>
