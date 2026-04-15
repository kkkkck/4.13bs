<template>
  <div class="page-stack wrong-book-page">
    <section class="panel-card">
      <div class="panel-head">
        <div>
          <h2>错题记录</h2>
        </div>
        <div class="row-actions">
          <button class="primary-btn" :disabled="!filteredRetryQuestionIds.length" @click="openPracticeModal">
            开始重练
          </button>
          <button v-if="hasActiveFilters" class="ghost-btn" @click="resetFilters">清空筛选</button>
        </div>
      </div>

      <div class="status-strip">
        <span>总错题 {{ allItems.length }}</span>
        <span>当前显示 {{ filteredItems.length }}</span>
        <span>高频错题 {{ frequentWrongCount }}</span>
        <span>可回练 {{ filteredRetryQuestionIds.length }}</span>
      </div>

      <p v-if="message" class="form-success">{{ message }}</p>
      <p v-if="error" class="form-error">{{ error }}</p>

      <div v-if="loading" class="empty-state">正在加载错题记录...</div>
      <template v-else>
        <div class="filter-toolbar-grid wrong-filter-toolbar">
          <label class="field-block">
            <span>专题筛选</span>
            <select v-model.number="selectedRootId" class="input select">
              <option :value="0">全部专题</option>
              <option v-for="category in rootCategories" :key="category.id" :value="category.id">
                {{ category.name }}
              </option>
            </select>
          </label>

          <label class="field-block">
            <span>章节筛选</span>
            <select v-model.number="selectedCategoryId" class="input select" :disabled="!chapterFilterOptions.length">
              <option :value="0">{{ selectedRootId ? '整个专题' : '先选择专题' }}</option>
              <option v-for="category in chapterFilterOptions" :key="category.id" :value="category.id">
                {{ category.name }}
              </option>
            </select>
          </label>

          <div class="field-block">
            <span>排序方式</span>
            <div class="row-actions wrong-sort-actions">
              <button :class="sortMode === 'wrong-desc' ? 'primary-btn' : 'ghost-btn'" @click="setSortMode('wrong-desc')">
                错误次数
              </button>
              <button :class="sortMode === 'latest-desc' ? 'primary-btn' : 'ghost-btn'" @click="setSortMode('latest-desc')">
                最近错误
              </button>
              <button :class="sortMode === 'latest-asc' ? 'primary-btn' : 'ghost-btn'" @click="setSortMode('latest-asc')">
                最早错误
              </button>
            </div>
          </div>
        </div>

        <div v-if="paginatedItems.length" class="list-stack">
          <article v-for="item in paginatedItems" :key="item.record.id" class="list-card compact review-record-card">
            <div class="list-stack">
              <div class="category-topline">
                <strong>{{ item.question?.content || `题目 #${item.record.questionId}` }}</strong>
                <div class="record-meta">
                  <span class="record-pill danger">{{ severityText(item.record.wrongCount) }}</span>
                  <span class="record-pill">{{ formatTime(item.record.lastWrongTime) }}</span>
                </div>
              </div>

              <div class="record-meta">
                <span class="record-pill">{{ item.rootCategoryName }}</span>
                <span v-if="item.chapterName" class="record-pill muted">{{ item.chapterName }}</span>
                <span v-if="item.question" class="record-pill muted">{{ sourceTypeText(item.question.sourceType) }}</span>
                <span class="record-pill">错误次数 {{ item.record.wrongCount }}</span>
                <span v-if="item.question?.source" class="record-pill muted">{{ item.question.source }}</span>
                <span v-for="tag in tagList(item.question)" :key="tag" class="record-pill muted">{{ tag }}</span>
              </div>
          </div>

          <div class="list-actions">
            <RouterLink class="ghost-btn" :to="buildPracticeLink(item.question)">去重练</RouterLink>
            <button class="ghost-btn" :disabled="removingId === item.record.id" @click="openRemoveModal(item)">
              {{ removingId === item.record.id ? '移除中...' : '移除' }}
            </button>
            </div>
          </article>
        </div>

        <div v-else class="empty-state">暂无符合条件的错题。</div>

        <div v-if="totalItems > pageSize" class="pagination-bar">
          <span class="pagination-meta">第 {{ currentPage }} / {{ totalPages }} 页，共 {{ totalItems }} 条</span>
          <div class="row-actions">
            <button class="ghost-btn small" :disabled="currentPage === 1" @click="changePage(currentPage - 1)">上一页</button>
            <button class="ghost-btn small" :disabled="currentPage >= totalPages" @click="changePage(currentPage + 1)">
              下一页
            </button>
          </div>
        </div>
      </template>
    </section>

    <div v-if="showPracticeModal" class="modal-mask" @click.self="closePracticeModal">
      <section class="panel-card modal-dialog">
        <div class="panel-head">
          <div>
            <h3>重练设置</h3>
            <p>按当前范围筛出错题后开始重练。</p>
          </div>
          <button class="ghost-btn" @click="closePracticeModal">关闭</button>
        </div>

        <div class="modal-body-stack">
          <label class="field-block">
            <span>专题</span>
            <select v-model.number="practiceRootId" class="input select">
              <option :value="0">沿用当前筛选</option>
              <option v-for="category in rootCategories" :key="category.id" :value="category.id">
                {{ category.name }}
              </option>
            </select>
          </label>

          <label class="field-block">
            <span>章节</span>
            <select v-model.number="practiceCategoryId" class="input select" :disabled="!practiceChapterOptions.length">
              <option :value="0">{{ practiceRootId ? '整个专题' : '不额外限制章节' }}</option>
              <option v-for="category in practiceChapterOptions" :key="category.id" :value="category.id">
                {{ category.name }}
              </option>
            </select>
          </label>

          <div class="status-strip">
            <span>可重练题数 {{ practiceQuestionIds.length }}</span>
            <span>专题 {{ practiceRootLabel }}</span>
            <span>章节 {{ practiceCategoryLabel }}</span>
          </div>

          <div class="row-actions">
            <button class="ghost-btn" @click="closePracticeModal">取消</button>
            <button class="primary-btn" :disabled="!practiceQuestionIds.length" @click="startPractice">开始重练</button>
          </div>
        </div>
      </section>
    </div>

    <div v-if="pendingRemoveItem" class="modal-mask" @click.self="closeRemoveModal">
      <section class="panel-card modal-dialog confirm-dialog">
        <div class="panel-head compact">
          <div>
            <h3>移除错题记录</h3>
            <p>只会移除错题记录，不会删除原题。</p>
          </div>
        </div>

        <div class="modal-body-stack">
          <article class="confirm-card">
            <strong>{{ pendingRemoveItem.question?.content || `题目 #${pendingRemoveItem.record.questionId}` }}</strong>
            <div class="record-meta">
              <span class="record-pill">{{ pendingRemoveItem.rootCategoryName }}</span>
              <span v-if="pendingRemoveItem.chapterName" class="record-pill muted">{{ pendingRemoveItem.chapterName }}</span>
              <span class="record-pill muted">错误次数 {{ pendingRemoveItem.record.wrongCount }}</span>
              <span class="record-pill muted">{{ formatTime(pendingRemoveItem.record.lastWrongTime) }}</span>
            </div>
          </article>

          <div class="row-actions confirm-actions">
            <button class="ghost-btn" :disabled="Boolean(removingId)" @click="closeRemoveModal">取消</button>
            <button class="primary-btn danger-btn" :disabled="Boolean(removingId)" @click="confirmRemove">
              {{ removingId ? '移除中...' : '确认移除' }}
            </button>
          </div>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { RouterLink, type RouteLocationRaw, useRouter } from 'vue-router'
import { getCategories } from '@/app/api/categories'
import { getQuestionsByIds } from '@/app/api/questions'
import { getWrongQuestions, removeWrongQuestion } from '@/app/api/wrong'
import type { Category, Question, WrongQuestionRecord } from '@/app/types'

type SortMode = 'wrong-desc' | 'latest-desc' | 'latest-asc'

interface WrongItem {
  record: WrongQuestionRecord
  question: Question | null
  rootCategoryId: number
  rootCategoryName: string
  chapterId: number
  chapterName: string
}

const router = useRouter()
const allItems = ref<WrongItem[]>([])
const categories = ref<Category[]>([])
const loading = ref(false)
const error = ref('')
const message = ref('')
const removingId = ref<number | null>(null)
const pendingRemoveItem = ref<WrongItem | null>(null)
const currentPage = ref(1)
const pageSize = 10
const selectedRootId = ref(0)
const selectedCategoryId = ref(0)
const sortMode = ref<SortMode>('wrong-desc')
const showPracticeModal = ref(false)
const practiceRootId = ref(0)
const practiceCategoryId = ref(0)

const rootCategories = computed(() => categories.value.filter((item) => !item.parentId))
const chapterFilterOptions = computed(() => categories.value.filter((item) => item.parentId === selectedRootId.value))
const practiceChapterOptions = computed(() => categories.value.filter((item) => item.parentId === practiceRootId.value))

const hasActiveFilters = computed(() => selectedRootId.value > 0 || selectedCategoryId.value > 0)

const filteredItems = computed(() => {
  const list = allItems.value.filter((item) => {
    if (selectedCategoryId.value > 0) {
      return item.chapterId === selectedCategoryId.value || item.question?.categoryId === selectedCategoryId.value
    }
    if (selectedRootId.value > 0) {
      return item.rootCategoryId === selectedRootId.value
    }
    return true
  })

  return [...list].sort((left, right) => {
    if (sortMode.value === 'wrong-desc') {
      return right.record.wrongCount - left.record.wrongCount || compareTime(right, left)
    }
    if (sortMode.value === 'latest-asc') {
      return compareTime(left, right)
    }
    return compareTime(right, left)
  })
})

const paginatedItems = computed(() => filteredItems.value.slice((currentPage.value - 1) * pageSize, currentPage.value * pageSize))
const totalItems = computed(() => filteredItems.value.length)
const totalPages = computed(() => Math.max(1, Math.ceil(totalItems.value / pageSize)))
const frequentWrongCount = computed(() => filteredItems.value.filter((item) => item.record.wrongCount >= 2).length)
const filteredRetryQuestionIds = computed(() =>
  filteredItems.value.map((item) => item.question?.id || 0).filter((id) => Number.isFinite(id) && id > 0)
)

const practiceQuestionIds = computed(() => {
  const source = filteredItems.value.filter((item) => {
    if (practiceCategoryId.value > 0) {
      return item.chapterId === practiceCategoryId.value || item.question?.categoryId === practiceCategoryId.value
    }
    if (practiceRootId.value > 0) {
      return item.rootCategoryId === practiceRootId.value
    }
    return true
  })
  return source.map((item) => item.question?.id || 0).filter((id) => Number.isFinite(id) && id > 0)
})

const practiceRootLabel = computed(() => {
  if (!practiceRootId.value) {
    return '当前筛选'
  }
  return rootCategories.value.find((item) => item.id === practiceRootId.value)?.name || '当前筛选'
})

const practiceCategoryLabel = computed(() => {
  if (!practiceCategoryId.value) {
    return practiceRootId.value ? '整个专题' : '不额外限制'
  }
  return categories.value.find((item) => item.id === practiceCategoryId.value)?.name || '不额外限制'
})

const compareTime = (left: WrongItem, right: WrongItem) =>
  new Date(left.record.lastWrongTime).getTime() - new Date(right.record.lastWrongTime).getTime()

const clearFeedback = () => {
  message.value = ''
  error.value = ''
}

const formatTime = (value: string) => {
  if (!value) {
    return '-'
  }
  return new Date(value).toLocaleDateString('zh-CN')
}

const severityText = (wrongCount: number) => {
  if (wrongCount >= 4) return '高频错误'
  if (wrongCount >= 2) return '需要回练'
  return '最近错误'
}

const sourceTypeText = (sourceType = 1) => (sourceType === 2 ? '模拟题' : '真题')

const tagList = (question: Question | null) =>
  question?.tags
    ? question.tags
        .split(/[，、\s]+/)
        .map((item) => item.trim())
        .filter(Boolean)
        .slice(0, 3)
    : []

const resolveWrongItem = (record: WrongQuestionRecord, questionMap: Map<number, Question>) => {
  const question = questionMap.get(record.questionId) || null
  const questionCategory = categories.value.find((item) => item.id === question?.categoryId) || null
  const rootCategory = questionCategory?.parentId
    ? categories.value.find((item) => item.id === questionCategory.parentId) || questionCategory
    : questionCategory

  return {
    record,
    question,
    rootCategoryId: rootCategory?.id || question?.categoryId || 0,
    rootCategoryName: rootCategory?.name || '未分类',
    chapterId: questionCategory?.parentId ? questionCategory.id : 0,
    chapterName: questionCategory?.parentId ? questionCategory.name : ''
  }
}

const loadAllWrongRecords = async () => {
  const size = 100
  let page = 1
  let pages = 1
  const records: WrongQuestionRecord[] = []

  while (page <= pages) {
    const result = await getWrongQuestions(page, size)
    records.push(...result.records)
    pages = Math.max(1, result.pages || Math.ceil((result.total || 0) / size))
    if (!result.records.length || page >= pages) {
      break
    }
    page += 1
  }

  return records
}

const loadData = async () => {
  loading.value = true
  clearFeedback()

  try {
    const categoryList = await getCategories().catch(() => [] as Category[])
    categories.value = categoryList

    const wrongRecords = await loadAllWrongRecords()
    const questions = await getQuestionsByIds(wrongRecords.map((item) => item.questionId)).catch(() => [] as Question[])
    const questionMap = new Map(questions.map((item) => [item.id, item]))

    allItems.value = wrongRecords.map((record) => resolveWrongItem(record, questionMap))
    currentPage.value = 1
  } catch (err) {
    allItems.value = []
    categories.value = []
    error.value = err instanceof Error ? err.message : '加载错题记录失败'
  } finally {
    loading.value = false
  }
}

const resetFilters = () => {
  selectedRootId.value = 0
  selectedCategoryId.value = 0
  sortMode.value = 'wrong-desc'
}

const setSortMode = (mode: SortMode) => {
  sortMode.value = mode
}

const changePage = (page: number) => {
  currentPage.value = Math.min(Math.max(1, page), totalPages.value)
}

const buildPracticeLink = (question: Question | null): RouteLocationRaw => {
  if (!question) {
    return '/categories'
  }

  return {
    path: '/practice',
    query: {
      questionIds: String(question.id),
      from: 'wrong-book'
    }
  }
}

const openPracticeModal = () => {
  practiceRootId.value = selectedRootId.value
  practiceCategoryId.value = selectedCategoryId.value
  showPracticeModal.value = true
}

const openRemoveModal = (item: WrongItem) => {
  clearFeedback()
  pendingRemoveItem.value = item
}

const closeRemoveModal = () => {
  if (removingId.value) {
    return
  }
  pendingRemoveItem.value = null
}

const closePracticeModal = () => {
  showPracticeModal.value = false
}

const startPractice = async () => {
  if (!practiceQuestionIds.value.length) {
    return
  }

  closePracticeModal()
  await router.push({
    path: '/practice',
    query: {
      questionIds: practiceQuestionIds.value.join(','),
      from: 'wrong-book'
    }
  })
}

const confirmRemove = async () => {
  const target = pendingRemoveItem.value
  if (!target) {
    return
  }

  clearFeedback()
  removingId.value = target.record.id
  try {
    await removeWrongQuestion(target.record.id)
    allItems.value = allItems.value.filter((item) => item.record.id !== target.record.id)
    currentPage.value = Math.min(currentPage.value, totalPages.value)
    message.value = '错题记录已移除'
    pendingRemoveItem.value = null
  } catch (err) {
    error.value = err instanceof Error ? err.message : '移除失败'
  } finally {
    removingId.value = null
  }
}

watch(selectedRootId, () => {
  if (!chapterFilterOptions.value.some((item) => item.id === selectedCategoryId.value)) {
    selectedCategoryId.value = 0
  }
  currentPage.value = 1
})

watch([selectedCategoryId, sortMode], () => {
  currentPage.value = 1
})

watch(practiceRootId, () => {
  if (!practiceChapterOptions.value.some((item) => item.id === practiceCategoryId.value)) {
    practiceCategoryId.value = 0
  }
})

onMounted(() => {
  void loadData()
})
</script>
