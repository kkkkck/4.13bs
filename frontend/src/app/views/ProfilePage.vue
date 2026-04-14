<template>
  <div class="page-stack">
    <section class="hero-card compact tight-hero profile-hero">
      <div class="profile-hero-copy">
        <p class="eyebrow">个人中心</p>
        <h2>{{ authStore.user?.nickname || '学习者' }}</h2>
        <p class="hero-copy">{{ authStore.user?.email || '未绑定邮箱' }}</p>
        <div class="hero-badges">
          <span class="tag">{{ authStore.user?.email ? '邮箱已绑定' : '邮箱未绑定' }}</span>
          <span class="tag muted">已记录 {{ sortedHistory.length }} 次练习</span>
        </div>
      </div>

      <div class="hero-stats">
        <article class="hero-stat-card">
          <span>累计刷题</span>
          <strong>{{ overview.totalQuestions || 0 }}</strong>
        </article>
        <article class="hero-stat-card">
          <span>整体正确率</span>
          <strong>{{ overview.totalCorrectRate || 0 }}%</strong>
        </article>
        <article class="hero-stat-card">
          <span>连续练习</span>
          <strong>{{ overview.continuousDays || 0 }} 天</strong>
        </article>
      </div>
    </section>

    <p v-if="error" class="form-error">{{ error }}</p>
    <div v-if="loading" class="panel-card empty-state">正在加载个人学习画像...</div>

    <template v-else>
      <section class="profile-insight-grid">
        <article class="feature-card insight-card">
          <span class="eyebrow">学习覆盖</span>
          <strong>{{ coveredCategoryCount }}</strong>
          <p>已形成正确率记录的专题数，可直接据此判断自己目前在哪些专题上有实战积累。</p>
        </article>
        <article class="feature-card insight-card">
          <span class="eyebrow">最佳专题</span>
          <strong>{{ bestCategory?.categoryName || '暂无' }}</strong>
          <p>{{ bestCategory ? `当前正确率 ${bestCategory.correctRate}%` : '先完成几次练习，系统会自动识别。' }}</p>
        </article>
        <article class="feature-card insight-card">
          <span class="eyebrow">待补专题</span>
          <strong>{{ weakestCategory?.categoryName || '暂无' }}</strong>
          <p>
            {{
              weakestCategory
                ? `当前正确率 ${weakestCategory.correctRate}% ，建议下一轮优先回补。`
                : '暂无专题薄弱项，继续保持。'
            }}
          </p>
        </article>
        <article class="feature-card insight-card">
          <span class="eyebrow">最近练习</span>
          <strong>{{ latestPracticeLabel }}</strong>
          <p>{{ totalDurationLabel }}，最近的数据会自动沉淀到下方趋势和历史记录。</p>
        </article>
      </section>

      <section class="feature-grid">
        <RouterLink class="feature-card action-card" :to="weakestCategoryRoute">
          <strong>继续薄弱专题</strong>
          <p>{{ weakestCategory ? `优先回到 ${weakestCategory.categoryName} 再做一轮训练。` : '先进入专题页开始新的训练。' }}</p>
        </RouterLink>
        <RouterLink class="feature-card action-card" to="/mock-exam">
          <strong>开始模拟考试</strong>
          <p>把近期训练结果放进整卷场景里验证节奏、覆盖面和稳定性。</p>
        </RouterLink>
        <RouterLink class="feature-card action-card" to="/wrong-book">
          <strong>回看错题本</strong>
          <p>优先清空最近暴露和高频失分的题，缩短下一轮复习闭环。</p>
        </RouterLink>
      </section>

      <StatisticsChart :category-data="categoryRates" :trend-data="dailyRates" />
      <PracticeHistory :items="sortedHistory" :categories="categories" />
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, defineAsyncComponent, onBeforeUnmount, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { getCategories } from '@/app/api/categories'
import { getCategoryRates, getDailyRates, getOverview, getPracticeHistory } from '@/app/api/statistics'
import { useAuthStore } from '@/app/stores/auth'
import type { Category, CategoryRate, DailyRate, PracticeRecord, StatisticsOverview } from '@/app/types'

const StatisticsChart = defineAsyncComponent(() => import('@/app/components/StatisticsChart.vue'))
const PracticeHistory = defineAsyncComponent(() => import('@/app/components/PracticeHistory.vue'))

const authStore = useAuthStore()
const categories = ref<Category[]>([])
const loading = ref(false)
const error = ref('')
const overview = ref<StatisticsOverview>({
  totalQuestions: 0,
  totalCorrectRate: 0,
  continuousDays: 0
})
const categoryRates = ref<CategoryRate[]>([])
const dailyRates = ref<DailyRate[]>([])
const history = ref<PracticeRecord[]>([])

const sortedHistory = computed(() =>
  [...history.value].sort((left, right) => new Date(right.createdAt).getTime() - new Date(left.createdAt).getTime())
)
const coveredCategoryCount = computed(() => categoryRates.value.length)
const bestCategory = computed(() => {
  return [...categoryRates.value].sort((left, right) => {
    if (right.correctRate !== left.correctRate) {
      return right.correctRate - left.correctRate
    }
    return right.totalCount - left.totalCount
  })[0] || null
})
const weakestCategory = computed(() => {
  return [...categoryRates.value].sort((left, right) => {
    if (left.correctRate !== right.correctRate) {
      return left.correctRate - right.correctRate
    }
    return right.totalCount - left.totalCount
  })[0] || null
})
const latestPracticeLabel = computed(() => {
  const latest = sortedHistory.value[0]
  if (!latest) {
    return '暂无记录'
  }
  return new Date(latest.createdAt).toLocaleDateString('zh-CN')
})
const totalDurationLabel = computed(() => {
  const seconds = sortedHistory.value.reduce((sum, item) => sum + item.duration, 0)
  if (seconds < 60) {
    return `累计学习 ${seconds} 秒`
  }
  const minutes = Math.floor(seconds / 60)
  if (minutes < 60) {
    return `累计学习 ${minutes} 分钟`
  }
  const hours = Math.floor(minutes / 60)
  const remainMinutes = minutes % 60
  return `累计学习 ${hours} 小时 ${remainMinutes} 分`
})
const weakestCategoryRoute = computed(() => {
  if (!weakestCategory.value) {
    return '/categories'
  }
  return weakestCategory.value.categoryId > 0
    ? `/practice?categoryId=${weakestCategory.value.categoryId}`
    : '/categories'
})

const loadProfile = async () => {
  loading.value = true
  error.value = ''

  try {
    const [categoryList, overviewData, categoryData, dailyData, historyData] = await Promise.all([
      getCategories(),
      getOverview(),
      getCategoryRates(),
      getDailyRates(),
      getPracticeHistory()
    ])

    categories.value = categoryList
    overview.value = overviewData
    categoryRates.value = categoryData
    dailyRates.value = dailyData
    history.value = historyData
  } catch (err) {
    categories.value = []
    categoryRates.value = []
    dailyRates.value = []
    history.value = []
    error.value = err instanceof Error ? err.message : '加载个人画像失败'
  } finally {
    loading.value = false
  }
}

const handlePageShow = (event: PageTransitionEvent) => {
  if (event.persisted) {
    void loadProfile()
  }
}

onMounted(() => {
  void loadProfile()
  window.addEventListener('pageshow', handlePageShow)
})

onBeforeUnmount(() => {
  window.removeEventListener('pageshow', handlePageShow)
})
</script>
