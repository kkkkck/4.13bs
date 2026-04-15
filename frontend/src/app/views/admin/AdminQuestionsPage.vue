<template>
  <div class="page-stack admin-page-dense">
    <section class="panel-card">
      <div class="panel-head">
        <div>
          <h2>题库管理</h2>
          <p>集中处理筛选、导入、编辑和状态切换。</p>
        </div>
      </div>

      <div class="admin-search-bar">
        <input
          v-model.trim="filters.keyword"
          class="input admin-search-input"
          type="text"
          placeholder="搜索题干、标签或来源"
          @keyup.enter="searchQuestions"
        />
        <div class="row-actions">
          <button class="ghost-btn" @click="searchQuestions">搜索</button>
          <button class="ghost-btn" :class="{ active: filtersCollapsed }" @click="toggleFiltersCollapsed">
            {{ filtersCollapsed ? '展开' : '收起' }}
          </button>
          <button class="ghost-btn" @click="downloadTemplate">下载模板</button>
          <label class="ghost-btn file-btn">
            {{ importing ? `导入中：${importFileName || '请稍候...'}` : 'Excel 导入' }}
            <input type="file" accept=".xlsx,.xls" :disabled="importing" @change="handleImport" />
          </label>
          <button class="primary-btn" @click="openCreate">新增题目</button>
        </div>
      </div>

      <div v-show="!filtersCollapsed" class="question-filter-toolbar">
        <label class="field-block toolbar-field">
          <span>专题</span>
          <select v-model.number="filters.categoryId" class="input select">
            <option :value="0">全部专题</option>
            <option v-for="category in categories" :key="category.id" :value="category.id">
              {{ categoryLabel(category) }}
            </option>
          </select>
        </label>

        <label class="field-block toolbar-field">
          <span>状态</span>
          <select v-model.number="filters.status" class="input select">
            <option :value="-1">全部状态</option>
            <option :value="1">启用</option>
            <option :value="0">停用</option>
          </select>
        </label>

        <label class="field-block toolbar-field">
          <span>题型</span>
          <select v-model.number="filters.type" class="input select">
            <option :value="0">全部题型</option>
            <option :value="1">单选题</option>
            <option :value="5">多选题</option>
            <option :value="2">填空题</option>
            <option :value="4">简答题</option>
          </select>
        </label>

        <label class="field-block toolbar-field">
          <span>难度</span>
          <select v-model.number="filters.difficulty" class="input select">
            <option :value="0">全部难度</option>
            <option :value="1">基础</option>
            <option :value="2">提高</option>
            <option :value="3">冲刺</option>
          </select>
        </label>

        <label class="field-block toolbar-field">
          <span>来源</span>
          <select v-model.number="filters.sourceType" class="input select">
            <option :value="0">全部来源</option>
            <option :value="1">真题</option>
            <option :value="2">模拟题</option>
          </select>
        </label>

        <div class="toolbar-actions">
          <button class="ghost-btn" @click="resetFilters">清空筛选</button>
          <button class="primary-btn" @click="searchQuestions">应用筛选</button>
        </div>
      </div>

      <div class="status-strip">
        <span>{{ filterSummary }}</span>
        <span>本页 {{ questions.length }} 题 / 总计 {{ totalItems }} 题</span>
      </div>

      <p v-if="message" class="form-success">{{ message }}</p>
      <p v-if="error" class="form-error">{{ error }}</p>

      <div v-if="lastImportResult" class="status-strip">
        <span>最近导入：共 {{ lastImportResult.total }} 条</span>
        <span>成功 {{ lastImportResult.successCount }} 条 / 失败 {{ lastImportResult.failCount }} 条</span>
        <span v-if="lastImportResult.enrichedCount">补全旧题 {{ lastImportResult.enrichedCount }} 条</span>
        <span v-if="lastImportResult.duplicateCount">重复跳过 {{ lastImportResult.duplicateCount }} 条</span>
      </div>

      <div v-if="importErrors.length" class="import-errors">
        <strong>导入失败明细</strong>
        <p v-for="item in importErrors" :key="item">{{ item }}</p>
      </div>

      <div v-if="loading" class="empty-state">正在加载题目列表...</div>

      <div v-else-if="questions.length" class="table-shell">
        <table class="table">
          <thead>
            <tr>
              <th class="col-id">ID</th>
              <th class="col-wide">题目</th>
              <th>专题</th>
              <th>题型 / 难度</th>
              <th class="table-status-cell">状态</th>
              <th class="table-actions-cell">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in questions" :key="item.id">
              <td class="col-id">{{ item.id }}</td>
              <td class="col-wide">
                <div class="table-primary-cell">
                  <strong>{{ item.content }}</strong>
                  <div class="record-meta">
                    <span class="record-pill">{{ sourceTypeText(item.sourceType) }}</span>
                    <span v-if="item.source" class="record-pill">{{ item.source }}</span>
                    <span v-for="tag in tagList(item.tags)" :key="`${item.id}-${tag}`" class="record-pill muted">
                      {{ tag }}
                    </span>
                  </div>
                </div>
              </td>
              <td>{{ categoryMap[item.categoryId] || '-' }}</td>
              <td>
                <div class="table-primary-cell">
                  <strong>{{ typeText(item.type) }}</strong>
                  <small>{{ difficultyText(item.difficulty) }}</small>
                </div>
              </td>
              <td class="table-status-cell">
                <span class="record-pill" :class="item.status === 1 ? 'success' : 'danger'">
                  {{ item.status === 1 ? '启用' : '停用' }}
                </span>
              </td>
              <td class="table-actions-cell">
                <div class="row-actions">
                  <button class="ghost-btn small" @click="openEdit(item)">编辑</button>
                  <button
                    class="ghost-btn small"
                    :disabled="togglingId === item.id"
                    @click="handleToggleStatus(item)"
                  >
                    {{ togglingId === item.id ? '处理中...' : item.status === 1 ? '停用' : '启用' }}
                  </button>
                  <button class="ghost-btn small danger" @click="handleDelete(item.id)">删除</button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <div v-else class="empty-state">暂无符合条件的题目。</div>

      <div v-if="totalItems > 0" class="pagination-bar">
        <span class="pagination-meta">第 {{ currentPage }} / {{ totalPages }} 页，共 {{ totalItems }} 道题目</span>
        <div class="row-actions">
          <button class="ghost-btn small" :disabled="loading || currentPage === 1" @click="changePage(currentPage - 1)">
            上一页
          </button>
          <button
            class="ghost-btn small"
            :disabled="loading || currentPage >= totalPages"
            @click="changePage(currentPage + 1)"
          >
            下一页
          </button>
        </div>
      </div>
    </section>

    <div v-if="showEditor" class="modal-mask" @click.self="resetEditor">
      <section class="panel-card modal-dialog large">
        <div class="panel-head">
          <div>
            <h3>{{ editingId ? '编辑题目' : '新增题目' }}</h3>
          </div>
          <button class="ghost-btn small" type="button" @click="resetEditor">关闭</button>
        </div>

        <div class="modal-content-scroll">
          <form class="editor-grid" @submit.prevent="handleSave">
            <label class="field-block field-span-2">
              <span>题目内容</span>
              <textarea v-model="form.content" class="textarea" rows="4" />
            </label>

            <label class="field-block">
              <span>专题</span>
              <select v-model.number="form.categoryId" class="input select">
                <option v-for="category in categories" :key="category.id" :value="category.id">
                  {{ categoryLabel(category) }}
                </option>
              </select>
            </label>

            <label class="field-block">
              <span>来源</span>
              <input v-model="form.source" class="input" type="text" placeholder="如：2026 模拟卷" />
            </label>

            <label class="field-block">
              <span>来源类型</span>
              <select v-model.number="form.sourceType" class="input select">
                <option :value="1">真题</option>
                <option :value="2">模拟题</option>
              </select>
            </label>

            <label class="field-block">
              <span>难度</span>
              <select v-model.number="form.difficulty" class="input select">
                <option :value="1">基础</option>
                <option :value="2">提高</option>
                <option :value="3">冲刺</option>
              </select>
            </label>

            <label class="field-block">
              <span>题型</span>
              <select v-model.number="form.type" class="input select">
                <option :value="1">单选题</option>
                <option :value="5">多选题</option>
                <option :value="2">填空题</option>
                <option :value="4">简答题</option>
              </select>
            </label>

            <label class="field-block">
              <span>状态</span>
              <select v-model.number="form.status" class="input select">
                <option :value="1">启用</option>
                <option :value="0">停用</option>
              </select>
            </label>

            <label class="field-block field-span-2">
              <span>标签</span>
              <input v-model="form.tags" class="input" type="text" placeholder="如：马原,矛盾,辩证法" />
            </label>

            <label v-if="showChoiceFields" class="field-block">
              <span>选项 A</span>
              <textarea v-model="form.optionA" class="textarea" rows="2" />
            </label>

            <label v-if="showChoiceFields" class="field-block">
              <span>选项 B</span>
              <textarea v-model="form.optionB" class="textarea" rows="2" />
            </label>

            <label v-if="showChoiceFields" class="field-block">
              <span>选项 C</span>
              <textarea v-model="form.optionC" class="textarea" rows="2" />
            </label>

            <label v-if="showChoiceFields" class="field-block">
              <span>选项 D</span>
              <textarea v-model="form.optionD" class="textarea" rows="2" />
            </label>

            <label class="field-block field-span-2">
              <span>正确答案</span>
              <input
                v-model="form.correctAnswer"
                class="input"
                type="text"
                :placeholder="correctAnswerPlaceholder"
              />
            </label>

            <label class="field-block field-span-2">
              <span>答案解析</span>
              <textarea v-model="form.analysis" class="textarea" rows="4" />
            </label>

            <label class="field-block field-span-2">
              <span>解题思路</span>
              <textarea v-model="form.solutionStrategy" class="textarea" rows="4" />
            </label>

            <div class="editor-actions field-span-2">
              <button class="ghost-btn" type="button" @click="resetEditor">取消</button>
              <button class="primary-btn" type="submit">{{ saving ? '保存中...' : '保存题目' }}</button>
            </div>
          </form>
        </div>
      </section>
    </div>

    <div v-if="pendingDeleteQuestion" class="modal-mask" @click.self="closeDeleteModal">
      <section class="panel-card modal-dialog confirm-dialog">
        <div class="panel-head compact">
          <div>
            <h3>删除题目</h3>
            <p>删除后不可恢复，请确认后再继续。</p>
          </div>
        </div>

        <div class="modal-body-stack">
          <article class="confirm-card">
            <strong>{{ pendingDeleteQuestion.content }}</strong>
            <div class="record-meta">
              <span v-if="categoryMap[pendingDeleteQuestion.categoryId]" class="record-pill">{{ categoryMap[pendingDeleteQuestion.categoryId] }}</span>
              <span class="record-pill muted">{{ sourceTypeText(pendingDeleteQuestion.sourceType) }}</span>
              <span class="record-pill muted">{{ typeText(pendingDeleteQuestion.type) }}</span>
              <span class="record-pill muted">{{ difficultyText(pendingDeleteQuestion.difficulty) }}</span>
              <span class="record-pill" :class="pendingDeleteQuestion.status === 1 ? 'success' : 'danger'">
                {{ pendingDeleteQuestion.status === 1 ? '启用' : '停用' }}
              </span>
            </div>
          </article>

          <div class="row-actions confirm-actions">
            <button class="ghost-btn" @click="closeDeleteModal">取消</button>
            <button class="primary-btn danger-btn" :disabled="Boolean(deletingId)" @click="confirmDelete">
              {{ deletingId ? '删除中...' : '确认删除' }}
            </button>
          </div>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import {
  createAdminQuestion,
  deleteAdminQuestion,
  getAdminCategories,
  getAdminQuestions,
  importAdminQuestions,
  updateAdminQuestion,
  updateAdminQuestionStatus,
  type AdminImportResult
} from '@/app/api/admin'
import { API_BASE_URL } from '@/app/request'
import type { Category, Question } from '@/app/types'

const categories = ref<Category[]>([])
const questions = ref<Question[]>([])
const loading = ref(false)
const showEditor = ref(false)
const editingId = ref<number | null>(null)
const saving = ref(false)
const togglingId = ref<number | null>(null)
const deletingId = ref<number | null>(null)
const message = ref('')
const error = ref('')
const importErrors = ref<string[]>([])
const lastImportResult = ref<AdminImportResult | null>(null)
const importing = ref(false)
const importFileName = ref('')
const currentPage = ref(1)
const pageSize = 20
const totalItems = ref(0)
const QUESTION_FILTER_STORAGE_KEY = 'admin-questions-filter-state-v1'
const QUESTION_PAGE_STORAGE_KEY = 'admin-questions-page-state-v1'
const QUESTION_LAYOUT_STORAGE_KEY = 'admin-questions-layout-state-v1'
const filtersCollapsed = ref(false)
const pendingDeleteQuestion = ref<Question | null>(null)

const filters = reactive({
  keyword: '',
  categoryId: 0,
  status: -1,
  type: 0,
  difficulty: 0,
  sourceType: 0
})

const form = reactive<Partial<Question>>({
  content: '',
  categoryId: 0,
  difficulty: 1,
  type: 1,
  tags: '',
  source: '',
  sourceType: 1,
  optionA: '',
  optionB: '',
  optionC: '',
  optionD: '',
  correctAnswer: '',
  analysis: '',
  solutionStrategy: '',
  status: 1
})

const categoryMap = computed(() =>
  Object.fromEntries(categories.value.map((item) => [item.id, categoryLabel(item)]))
)
const totalPages = computed(() => Math.max(1, Math.ceil(totalItems.value / pageSize)))
const showChoiceFields = computed(() => form.type === 1 || form.type === 5)
const currentCategoryLabel = computed(() =>
  filters.categoryId ? categoryMap.value[filters.categoryId] || '指定专题' : '全部专题'
)
const currentTypeLabel = computed(() => (filters.type ? typeText(filters.type) : '全部题型'))
const currentDifficultyLabel = computed(() => (filters.difficulty ? difficultyText(filters.difficulty) : '全部难度'))
const currentSourceTypeLabel = computed(() => (filters.sourceType ? sourceTypeText(filters.sourceType) : '全部来源'))
const filterSummary = computed(() => {
  const statusText = filters.status < 0 ? '全部状态' : filters.status === 1 ? '仅启用' : '仅停用'
  const keywordText = filters.keyword ? `关键词 ${filters.keyword}` : '未设关键词'
  return `${currentCategoryLabel.value} · ${statusText} · ${currentTypeLabel.value} · ${currentDifficultyLabel.value} · ${currentSourceTypeLabel.value} · ${keywordText}`
})
const correctAnswerPlaceholder = computed(() => {
  if (form.type === 1) {
    return '单选题请填写 A-D'
  }
  if (form.type === 5) {
    return '多选题请使用 A,C 这样的格式'
  }
  return '如：关键词 / 核心结论'
})

const difficultyText = (difficulty: number) => ['基础', '提高', '冲刺'][difficulty - 1] || '-'

const typeText = (type: number) => {
  const map: Record<number, string> = {
    1: '单选题',
    2: '填空题',
    4: '简答题',
    5: '多选题'
  }
  return map[type] || '未知题型'
}

const sourceTypeText = (sourceType = 1) => (sourceType === 2 ? '模拟题' : '真题')

const tagList = (value?: string) =>
  value
    ? value
        .split(/[，,、\s]+/)
        .map((item) => item.trim())
        .filter(Boolean)
        .slice(0, 3)
    : []

const categoryLabel = (category: Category) => {
  if (!category.parentId) {
    return category.name
  }
  const parent = categories.value.find((item) => item.id === category.parentId)
  return parent ? `${parent.name} / ${category.name}` : category.name
}

const clearFeedback = () => {
  message.value = ''
  error.value = ''
}

const restoreFilters = () => {
  try {
    const raw = localStorage.getItem(QUESTION_FILTER_STORAGE_KEY)
    if (!raw) {
      return
    }
    const parsed = JSON.parse(raw) as Partial<typeof filters>
    filters.keyword = typeof parsed.keyword === 'string' ? parsed.keyword : ''
    filters.categoryId = typeof parsed.categoryId === 'number' ? parsed.categoryId : 0
    filters.status = typeof parsed.status === 'number' ? parsed.status : -1
    filters.type = typeof parsed.type === 'number' ? parsed.type : 0
    filters.difficulty = typeof parsed.difficulty === 'number' ? parsed.difficulty : 0
    filters.sourceType = typeof parsed.sourceType === 'number' ? parsed.sourceType : 0
  } catch {
    localStorage.removeItem(QUESTION_FILTER_STORAGE_KEY)
  }
}

const restorePage = () => {
  const raw = localStorage.getItem(QUESTION_PAGE_STORAGE_KEY)
  const parsed = Number(raw)
  currentPage.value = Number.isFinite(parsed) && parsed > 0 ? parsed : 1
}

const restoreLayoutState = () => {
  try {
    const raw = localStorage.getItem(QUESTION_LAYOUT_STORAGE_KEY)
    if (!raw) {
      return
    }
    const parsed = JSON.parse(raw) as { filtersCollapsed?: boolean }
    filtersCollapsed.value = Boolean(parsed.filtersCollapsed)
  } catch {
    localStorage.removeItem(QUESTION_LAYOUT_STORAGE_KEY)
  }
}

const persistFilterState = () => {
  localStorage.setItem(
    QUESTION_FILTER_STORAGE_KEY,
    JSON.stringify({
      keyword: filters.keyword,
      categoryId: filters.categoryId,
      status: filters.status,
      type: filters.type,
      difficulty: filters.difficulty,
      sourceType: filters.sourceType
    })
  )
}

const persistPageState = () => {
  localStorage.setItem(QUESTION_PAGE_STORAGE_KEY, String(currentPage.value))
}

const persistLayoutState = () => {
  localStorage.setItem(
    QUESTION_LAYOUT_STORAGE_KEY,
    JSON.stringify({
      filtersCollapsed: filtersCollapsed.value
    })
  )
}

const normalizeCorrectAnswer = (value: string) => {
  const raw = value.trim()
  if (!raw) {
    return ''
  }

  if (form.type === 1) {
    return raw.toUpperCase()
  }

  if (form.type === 5) {
    return [...new Set(raw.toUpperCase().replace(/，/g, ',').split(/[,\s]+/).filter(Boolean))].sort().join(',')
  }

  return raw
}

const buildPayload = () => {
  const isChoice = form.type === 1 || form.type === 5
  return {
    ...form,
    content: form.content?.trim(),
    source: form.source?.trim() || '',
    sourceType: form.sourceType || 1,
    tags: form.tags?.trim() || '',
    optionA: isChoice ? form.optionA?.trim() || '' : '',
    optionB: isChoice ? form.optionB?.trim() || '' : '',
    optionC: isChoice ? form.optionC?.trim() || '' : '',
    optionD: isChoice ? form.optionD?.trim() || '' : '',
    correctAnswer: normalizeCorrectAnswer(form.correctAnswer || ''),
    analysis: form.analysis?.trim() || '',
    solutionStrategy: form.solutionStrategy?.trim() || ''
  }
}

const validateForm = () => {
  if (!form.content?.trim()) {
    error.value = '题目内容不能为空'
    return false
  }

  if (!form.categoryId) {
    error.value = '请选择专题'
    return false
  }

  if ((form.type === 1 || form.type === 5) && (!form.optionA?.trim() || !form.optionB?.trim())) {
    error.value = '选择题至少需要填写选项 A 和选项 B'
    return false
  }

  const normalizedCorrectAnswer = normalizeCorrectAnswer(form.correctAnswer || '')
  if (!normalizedCorrectAnswer) {
    error.value = '正确答案不能为空'
    return false
  }

  if (form.type === 1 && !/^[A-D]$/.test(normalizedCorrectAnswer)) {
    error.value = '单选题答案必须是 A-D 中的一个选项'
    return false
  }

  if (form.type === 5 && !/^[A-D](,[A-D])+$/.test(normalizedCorrectAnswer)) {
    error.value = '多选题答案必须使用 A,C 这样的格式，且至少包含两个选项'
    return false
  }

  if (form.type === 1 || form.type === 5) {
    const optionMap: Record<string, string> = {
      A: form.optionA?.trim() || '',
      B: form.optionB?.trim() || '',
      C: form.optionC?.trim() || '',
      D: form.optionD?.trim() || ''
    }

    const missingOption = normalizedCorrectAnswer
      .split(',')
      .find((key) => key && !optionMap[key])

    if (missingOption) {
      error.value = `答案 ${missingOption} 对应的选项内容不能为空`
      return false
    }
  }

  form.correctAnswer = normalizedCorrectAnswer
  return true
}

const resetEditor = () => {
  showEditor.value = false
  editingId.value = null
  form.content = ''
  form.categoryId = categories.value[0]?.id || 0
  form.difficulty = 1
  form.type = 1
  form.tags = ''
  form.source = ''
  form.sourceType = 1
  form.optionA = ''
  form.optionB = ''
  form.optionC = ''
  form.optionD = ''
  form.correctAnswer = ''
  form.analysis = ''
  form.solutionStrategy = ''
  form.status = 1
}

const closeDeleteModal = () => {
  if (deletingId.value) {
    return
  }
  pendingDeleteQuestion.value = null
}

const resetFilters = async () => {
  filters.keyword = ''
  filters.categoryId = 0
  filters.status = -1
  filters.type = 0
  filters.difficulty = 0
  filters.sourceType = 0
  currentPage.value = 1
  await loadQuestions(1)
}

const openCreate = () => {
  clearFeedback()
  resetEditor()
  showEditor.value = true
}

const openEdit = (question: Question) => {
  clearFeedback()
  editingId.value = question.id
  form.content = question.content
  form.categoryId = question.categoryId
  form.difficulty = question.difficulty
  form.type = question.type
  form.tags = question.tags || ''
  form.source = question.source || ''
  form.sourceType = question.sourceType || 1
  form.optionA = question.optionA || ''
  form.optionB = question.optionB || ''
  form.optionC = question.optionC || ''
  form.optionD = question.optionD || ''
  form.correctAnswer = question.correctAnswer
  form.analysis = question.analysis || ''
  form.solutionStrategy = question.solutionStrategy || ''
  form.status = question.status ?? 1
  showEditor.value = true
}

const searchQuestions = async () => {
  currentPage.value = 1
  await loadQuestions(1)
}

const toggleFiltersCollapsed = () => {
  filtersCollapsed.value = !filtersCollapsed.value
}

const changePage = async (page: number) => {
  currentPage.value = page
  await loadQuestions(page)
}

const loadQuestions = async (page = currentPage.value) => {
  clearFeedback()
  loading.value = true
  try {
    const targetPage = Math.max(1, page)
    const data = await getAdminQuestions({
      page: targetPage,
      size: pageSize,
      keyword: filters.keyword || undefined,
      categoryId: filters.categoryId || undefined,
      status: filters.status >= 0 ? filters.status : undefined,
      type: filters.type || undefined,
      difficulty: filters.difficulty || undefined,
      sourceType: filters.sourceType || undefined
    })

    const resolvedTotalPages = Math.max(1, Math.ceil((data.total || 0) / pageSize))
    if (!data.records.length && data.total > 0 && targetPage > resolvedTotalPages) {
      currentPage.value = resolvedTotalPages
      await loadQuestions(resolvedTotalPages)
      return
    }

    questions.value = data.records
    totalItems.value = data.total
    currentPage.value = targetPage
  } catch (err) {
    questions.value = []
    totalItems.value = 0
    error.value = err instanceof Error ? err.message : '加载题目失败'
  } finally {
    loading.value = false
  }
}

const loadCategories = async () => {
  try {
    categories.value = await getAdminCategories()
    if (!form.categoryId && categories.value.length) {
      form.categoryId = categories.value[0].id
    }
  } catch (err) {
    categories.value = []
    error.value = err instanceof Error ? err.message : '加载专题列表失败'
  }
}

const handleSave = async () => {
  clearFeedback()
  importErrors.value = []
  if (!validateForm()) {
    return
  }
  saving.value = true
  try {
    const payload = buildPayload()
    if (editingId.value) {
      await updateAdminQuestion(editingId.value, payload)
      await loadQuestions()
      message.value = '题目更新成功'
    } else {
      currentPage.value = 1
      await createAdminQuestion(payload)
      await loadQuestions(1)
      message.value = '题目创建成功'
    }
    resetEditor()
  } catch (err) {
    error.value = err instanceof Error ? err.message : '保存失败'
  } finally {
    saving.value = false
  }
}

const handleToggleStatus = async (question: Question) => {
  clearFeedback()
  togglingId.value = question.id
  try {
    const nextStatus = question.status === 1 ? 0 : 1
    await updateAdminQuestionStatus(question.id, nextStatus)
    await loadQuestions(currentPage.value)
    message.value = nextStatus === 1 ? '题目已启用' : '题目已停用'
  } catch (err) {
    error.value = err instanceof Error ? err.message : '状态更新失败'
  } finally {
    togglingId.value = null
  }
}

const handleDelete = (id: number) => {
  const target = questions.value.find((item) => item.id === id) || null
  if (!target) {
    return
  }
  clearFeedback()
  pendingDeleteQuestion.value = target
}

const confirmDelete = async () => {
  const target = pendingDeleteQuestion.value
  if (!target) {
    return
  }

  deletingId.value = target.id
  try {
    await deleteAdminQuestion(target.id)
    const targetPage = questions.value.length === 1 && currentPage.value > 1 ? currentPage.value - 1 : currentPage.value
    await loadQuestions(targetPage)
    message.value = '题目已删除'
    pendingDeleteQuestion.value = null
  } catch (err) {
    error.value = err instanceof Error ? err.message : '删除失败'
  } finally {
    deletingId.value = null
  }
}

const handleImport = async (event: Event) => {
  clearFeedback()
  importErrors.value = []

  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) {
    return
  }

  importing.value = true
  importFileName.value = file.name
  message.value = `正在导入 ${file.name}，题量较大时可能需要等待一段时间。`

  try {
    const result = await importAdminQuestions(file)
    lastImportResult.value = result
    importErrors.value = result.errors || []
    currentPage.value = 1
    await loadQuestions(1)
    message.value = result.message
  } catch (err) {
    error.value = err instanceof Error ? err.message : '导入失败'
  } finally {
    importing.value = false
    importFileName.value = ''
    input.value = ''
  }
}

const downloadTemplate = async () => {
  clearFeedback()
  try {
    const token = localStorage.getItem('token') || ''
    const response = await fetch(`${API_BASE_URL}/admin/questions/import-template`, {
      headers: {
        Authorization: token ? `Bearer ${token}` : ''
      }
    })

    if (!response.ok) {
      throw new Error('模板下载失败')
    }

    const blob = await response.blob()
    const url = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = 'question-import-template.xls'
    link.click()
    window.URL.revokeObjectURL(url)
  } catch (err) {
    error.value = err instanceof Error ? err.message : '模板下载失败'
  }
}

onMounted(async () => {
  restoreFilters()
  restorePage()
  restoreLayoutState()
  await loadCategories()
  await loadQuestions(currentPage.value)
})

watch(
  () => ({ ...filters }),
  () => {
    persistFilterState()
  },
  { deep: true }
)

watch(currentPage, () => {
  persistPageState()
})

watch(filtersCollapsed, () => {
  persistLayoutState()
})
</script>
