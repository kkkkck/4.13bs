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
