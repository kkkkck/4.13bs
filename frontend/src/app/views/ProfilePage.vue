<template>
  <div class="page-stack profile-page">
    <section class="panel-card page-intro-card compact-overview-card profile-overview-card">
      <div class="profile-hero-copy">
        <p class="eyebrow">我的</p>
        <div class="profile-account-line">
          <UserAvatar :avatar-url="authStore.user?.avatarUrl" :nickname="authStore.user?.nickname" :size="72" />
          <div>
            <h2>{{ authStore.user?.nickname || '学习者' }}</h2>
            <p v-if="authStore.user?.email" class="hero-copy">{{ authStore.user?.email }}</p>
          </div>
        </div>
        <div class="record-meta profile-meta-row">
          <span v-if="authStore.user?.email" class="record-pill muted">邮箱已绑定</span>
          <span class="record-pill muted">记录 {{ sortedHistory.length }} 次</span>
          <button class="ghost-btn small" type="button" @click="settingsOpen = true">账号设置</button>
        </div>
      </div>

      <div class="hero-aside compact-overview-aside">
        <div class="status-strip compact-overview-strip">
          <span>累计 {{ overview.totalQuestions || 0 }} 题</span>
          <span>正确率 {{ overview.totalCorrectRate || 0 }}%</span>
          <span>连续 {{ overview.continuousDays || 0 }} 天</span>
        </div>
      </div>
    </section>

    <p v-if="error" class="form-error">{{ error }}</p>
    <div v-if="loading" class="panel-card empty-state">正在加载个人数据...</div>

    <template v-else>
      <section class="panel-card">
        <div class="compact-row-list">
          <div class="compact-row compact-row-static">
            <div class="compact-row-main">
              <strong>最高正确率</strong>
              <div class="compact-row-meta">
                <span>{{ bestCategory?.categoryName || '-' }}</span>
                <span>{{ bestCategory ? `${bestCategory.correctRate}%` : '暂无记录' }}</span>
              </div>
            </div>
          </div>
          <div class="compact-row compact-row-static">
            <div class="compact-row-main">
              <strong>最低正确率</strong>
              <div class="compact-row-meta">
                <span>{{ weakestCategory?.categoryName || '-' }}</span>
                <span>{{ weakestCategory ? `${weakestCategory.correctRate}%` : '暂无记录' }}</span>
              </div>
            </div>
          </div>
          <div class="compact-row compact-row-static">
            <div class="compact-row-main">
              <strong>练习时长</strong>
              <div class="compact-row-meta">
                <span>{{ totalDurationLabel }}</span>
                <span>{{ latestPracticeLabel === '-' ? '暂无练习记录' : `${latestPracticeLabel} · 共 ${sortedHistory.length} 次记录` }}</span>
              </div>
            </div>
          </div>
        </div>
      </section>

      <section class="profile-content-stack">
        <StatisticsChart :category-data="categoryRates" :trend-data="dailyRates" />
        <PracticeHistory :items="sortedHistory" :categories="categories" />
      </section>
    </template>

    <UserSettingsModal v-if="authStore.user" :open="settingsOpen" @close="settingsOpen = false" />
  </div>
</template>

<script setup lang="ts">
import { computed, defineAsyncComponent, onBeforeUnmount, onMounted, ref } from 'vue'
import UserAvatar from '@/app/components/UserAvatar.vue'
import UserSettingsModal from '@/app/components/UserSettingsModal.vue'
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
const settingsOpen = ref(false)
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
    return '-'
  }
  return new Date(latest.createdAt).toLocaleDateString('zh-CN')
})
const totalDurationLabel = computed(() => {
  const seconds = sortedHistory.value.reduce((sum, item) => sum + item.duration, 0)
  if (seconds < 60) {
    return `${seconds} 秒`
  }
  const minutes = Math.floor(seconds / 60)
  if (minutes < 60) {
    return `${minutes} 分钟`
  }
  const hours = Math.floor(minutes / 60)
  const remainMinutes = minutes % 60
  return `${hours} 小时 ${remainMinutes} 分`
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
    error.value = err instanceof Error ? err.message : '加载个人数据失败'
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
