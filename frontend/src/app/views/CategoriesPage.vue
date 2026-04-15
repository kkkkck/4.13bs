<template>
  <div class="page-stack categories-page">
    <section class="panel-card page-intro-card compact-overview-card categories-intro-card">
      <div>
        <p class="eyebrow">专题</p>
        <h2>选一个专题开始。</h2>
      </div>

      <div class="hero-aside compact-overview-aside">
        <div class="status-strip compact-overview-strip">
          <span>专题 {{ rootCategories.length }}</span>
          <span>章节专题 {{ chapterReadyCount }}</span>
          <span>综合专题 {{ integratedCount }}</span>
        </div>
        <div class="row-actions">
          <RouterLink class="primary-btn" :to="continuePracticeRoute">{{ continuePracticeLabel }}</RouterLink>
          <RouterLink class="ghost-btn" to="/mock-exam">开始模考</RouterLink>
        </div>
      </div>
    </section>

    <section class="panel-card">
      <div class="panel-head">
        <div>
          <h2>专题与章节</h2>
        </div>
      </div>

      <div class="status-strip">
        <span>练习题源 {{ sourceTypeLabel }}</span>
        <div class="row-actions">
          <button class="ghost-btn small" :class="{ active: selectedSourceType === 0 }" @click="setSelectedSourceType(0)">
            混合随机
          </button>
          <button class="ghost-btn small" :class="{ active: selectedSourceType === 1 }" @click="setSelectedSourceType(1)">
            只练真题
          </button>
          <button class="ghost-btn small" :class="{ active: selectedSourceType === 2 }" @click="setSelectedSourceType(2)">
            只练模拟题
          </button>
        </div>
      </div>

      <p v-if="error" class="form-error">{{ error }}</p>
      <div v-if="loading" class="empty-state">正在加载专题列表...</div>

      <div v-else-if="rootCategories.length" class="compact-row-list">
        <article v-for="category in rootCategories" :key="category.id" class="compact-row topic-list-row">
          <div class="compact-row-main">
            <div class="category-topline">
              <strong>{{ category.name }}</strong>
              <span v-if="matchesLastPractice(category)" class="record-pill success">上次练到这里</span>
            </div>
            <div class="compact-row-meta">
              <span>{{ category.practiceMode === 2 ? '按章节练' : '整组练' }}</span>
              <span>{{ chaptersOf(category.id).length ? `${chaptersOf(category.id).length} 个章节` : '整组练习' }}</span>
            </div>
            <div v-if="chaptersOf(category.id).length" class="chapter-pills compact-inline-pills">
              <RouterLink
                v-for="chapter in chaptersOf(category.id).slice(0, 6)"
                :key="chapter.id"
                class="chapter-pill action"
                :to="buildPracticeRoute(chapter.id)"
              >
                {{ chapter.name }}
              </RouterLink>
            </div>
          </div>
          <div class="compact-row-actions">
            <RouterLink class="primary-btn small" :to="buildPracticeRoute(category.id)">进入</RouterLink>
          </div>
        </article>
      </div>

      <div v-else class="empty-state">暂无可用专题。</div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'
import { getCategories } from '@/app/api/categories'
import { getLastPracticeCategoryId, getLastPracticeRoute, hasLastPracticeCategory } from '@/app/practice-resume'
import type { Category } from '@/app/types'

const categories = ref<Category[]>([])
const loading = ref(false)
const error = ref('')
const PRACTICE_SOURCE_FILTER_KEY = 'shuati:practice-source-filter'
const selectedSourceType = ref(0)
const rootCategories = computed(() => categories.value.filter((item) => !item.parentId))
const chapterReadyCount = computed(() => rootCategories.value.filter((item) => chaptersOf(item.id).length > 0).length)
const integratedCount = computed(() => rootCategories.value.filter((item) => chaptersOf(item.id).length === 0).length)
const continuePracticeRoute = computed(() => getLastPracticeRoute())
const continuePracticeLabel = computed(() => (hasLastPracticeCategory() ? '继续练习' : '开始练习'))
const lastPracticeCategoryId = computed(() => getLastPracticeCategoryId())
const sourceTypeLabel = computed(() => {
  if (selectedSourceType.value === 1) {
    return '只练真题'
  }
  if (selectedSourceType.value === 2) {
    return '只练模拟题'
  }
  return '混合随机'
})

const chaptersOf = (parentId: number) => categories.value.filter((item) => item.parentId === parentId)
const matchesLastPractice = (category: Category) =>
  category.id === lastPracticeCategoryId.value || chaptersOf(category.id).some((item) => item.id === lastPracticeCategoryId.value)

const restoreSelectedSourceType = () => {
  const raw = Number(localStorage.getItem(PRACTICE_SOURCE_FILTER_KEY) || '0')
  selectedSourceType.value = raw === 1 || raw === 2 ? raw : 0
}

const setSelectedSourceType = (sourceType: number) => {
  selectedSourceType.value = sourceType
}

const buildPracticeRoute = (categoryId: number) => ({
  path: '/practice',
  query: {
    categoryId: String(categoryId),
    ...(selectedSourceType.value ? { sourceType: String(selectedSourceType.value) } : {})
  }
})

const loadCategories = async () => {
  loading.value = true
  error.value = ''

  try {
    categories.value = await getCategories()
  } catch (err) {
    categories.value = []
    error.value = err instanceof Error ? err.message : '加载专题列表失败'
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  restoreSelectedSourceType()
  void loadCategories()
})

watch(selectedSourceType, () => {
  localStorage.setItem(PRACTICE_SOURCE_FILTER_KEY, String(selectedSourceType.value))
})
</script>
