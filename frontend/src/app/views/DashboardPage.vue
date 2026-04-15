<template>
  <div class="page-stack dashboard-page">
    <p v-if="error" class="form-error">{{ error }}</p>

    <section class="panel-card dashboard-list-panel">
      <div class="panel-head dashboard-list-head">
        <div>
          <h3>专题</h3>
          <div class="dashboard-inline-meta">
            <span>{{ authStore.user?.nickname || '当前用户' }}</span>
            <span>正确率 {{ overview.totalCorrectRate || 0 }}%</span>
            <span>累计 {{ overview.totalQuestions || 0 }} 题</span>
            <span>连续 {{ overview.continuousDays || 0 }} 天</span>
          </div>
        </div>
        <div class="dashboard-list-tools">
          <div class="row-actions">
            <RouterLink class="primary-btn small" :to="continuePracticeRoute">{{ continuePracticeLabel }}</RouterLink>
            <RouterLink class="ghost-btn small" to="/mock-exam">模考</RouterLink>
          </div>
        </div>
      </div>

      <div v-if="loading" class="empty-state">正在加载专题...</div>

      <div v-else-if="featuredCategories.length" class="dashboard-topic-table">
        <div class="dashboard-topic-header">
          <span>专题</span>
          <span>方式</span>
          <span>章节入口</span>
          <span>入口</span>
        </div>
        <article v-for="category in featuredCategories" :key="category.id" class="dashboard-topic-row">
          <div class="dashboard-topic-title">
            <strong>{{ category.name }}</strong>
            <small v-if="matchesLastPractice(category)">上次</small>
          </div>
          <span>{{ category.practiceMode === 2 ? '按章节练' : '整组练' }}</span>
          <div class="dashboard-topic-cell">
            <template v-if="chaptersOf(category.id).length">
              <div class="dashboard-chapter-links">
                <RouterLink
                  v-for="chapter in visibleChapters(category.id)"
                  :key="chapter.id"
                  class="dashboard-chapter-link"
                  :to="buildPracticeRoute(chapter.id)"
                >
                  {{ chapter.name }}
                </RouterLink>
              </div>
              <button
                v-if="chaptersOf(category.id).length > 3"
                class="inline-text-btn"
                type="button"
                @click="toggleCategoryExpanded(category.id)"
              >
                {{ isExpandedCategory(category.id) ? '收起' : `更多 ${chaptersOf(category.id).length - 3}` }}
              </button>
            </template>
            <span v-else class="dashboard-empty-copy">{{ chapterSummary(category.id) }}</span>
          </div>
          <div class="dashboard-topic-actions">
            <RouterLink class="ghost-btn small" :to="primaryEntryRoute(category.id)">{{ entryLabel(category.id) }}</RouterLink>
          </div>
        </article>
      </div>

      <div v-else class="empty-state">暂无可练习的专题。</div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { getCategories } from '@/app/api/categories'
import { getLastPracticeCategoryId, getLastPracticeRoute, hasLastPracticeCategory } from '@/app/practice-resume'
import { getOverview } from '@/app/api/statistics'
import { useAuthStore } from '@/app/stores/auth'
import type { Category, StatisticsOverview } from '@/app/types'

const authStore = useAuthStore()
const categories = ref<Category[]>([])
const loading = ref(false)
const error = ref('')
const overview = ref<StatisticsOverview>({
  totalQuestions: 0,
  totalCorrectRate: 0,
  continuousDays: 0
})

const rootCategories = computed(() => categories.value.filter((item) => !item.parentId))
const featuredCategories = computed(() => rootCategories.value.slice(0, 4))
const continuePracticeRoute = computed(() => getLastPracticeRoute())
const continuePracticeLabel = computed(() => (hasLastPracticeCategory() ? '继续' : '开始'))
const lastPracticeCategoryId = computed(() => getLastPracticeCategoryId())
const expandedCategoryIds = ref<number[]>([])

const chaptersOf = (parentId: number) => categories.value.filter((item) => item.parentId === parentId)
const matchesLastPractice = (category: Category) =>
  category.id === lastPracticeCategoryId.value || chaptersOf(category.id).some((item) => item.id === lastPracticeCategoryId.value)
const chapterSummary = (parentId: number) => {
  const count = chaptersOf(parentId).length
  return count ? `${count} 个章节` : '整组练习'
}
const buildPracticeRoute = (categoryId: number) => `/practice?categoryId=${categoryId}`
const primaryEntryRoute = (categoryId: number) => {
  const firstChapter = chaptersOf(categoryId)[0]
  return buildPracticeRoute(firstChapter?.id || categoryId)
}
const entryLabel = (categoryId: number) => (chaptersOf(categoryId).length ? '首章' : '开始')
const isExpandedCategory = (categoryId: number) => expandedCategoryIds.value.includes(categoryId)
const visibleChapters = (categoryId: number) =>
  isExpandedCategory(categoryId) ? chaptersOf(categoryId) : chaptersOf(categoryId).slice(0, 3)
const toggleCategoryExpanded = (categoryId: number) => {
  expandedCategoryIds.value = isExpandedCategory(categoryId)
    ? expandedCategoryIds.value.filter((item) => item !== categoryId)
    : [...expandedCategoryIds.value, categoryId]
}

const loadData = async () => {
  loading.value = true
  error.value = ''

  try {
    const [categoryList, overviewData] = await Promise.all([getCategories(), getOverview()])
    categories.value = categoryList
    overview.value = overviewData
  } catch (err) {
    categories.value = []
    error.value = err instanceof Error ? err.message : '加载首页数据失败'
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  void loadData()
})
</script>
