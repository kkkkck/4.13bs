<template>
  <div class="page-stack admin-page-dense admin-overview-page">
    <p v-if="error" class="form-error">{{ error }}</p>
    <div v-if="loading" class="panel-card empty-state">正在加载总览...</div>

    <template v-else>
      <section class="panel-card overview-table-panel">
        <div class="panel-head compact admin-overview-head">
          <div>
            <h3>总览</h3>
            <div class="admin-overview-meta">
              <span>题库 {{ overview.totalQuestions }}</span>
              <span>专题 {{ overview.totalCategories }}</span>
              <span>用户 {{ users.totalUsers }}</span>
              <span>平均停留 {{ formatDuration(users.averageActiveDurationSeconds) }}</span>
            </div>
          </div>
          <div class="row-actions compact-link-row">
            <RouterLink class="ghost-btn small" to="/admin/questions">题库</RouterLink>
            <RouterLink class="ghost-btn small" to="/admin/categories">专题</RouterLink>
            <RouterLink v-if="authStore.isSuperAdmin" class="ghost-btn small" to="/admin/users">用户</RouterLink>
          </div>
        </div>

        <section class="overview-table-section">
          <div class="overview-table-header">
            <span>用户停留分段</span>
            <span>人数</span>
          </div>
          <div class="overview-table-body">
            <div v-for="bucket in durationBuckets" :key="bucket.bucketOrder" class="overview-table-row">
              <span>{{ bucket.bucketLabel }}</span>
              <strong>{{ bucket.userCount }}</strong>
            </div>
            <div v-if="!durationBuckets.length" class="overview-table-row muted-row">
              <span>无数据</span>
              <strong>0</strong>
            </div>
          </div>
        </section>

        <section class="overview-table-section">
          <div class="overview-table-header">
            <span>专题</span>
            <span>题数</span>
          </div>
          <div class="overview-table-body">
            <div v-for="item in categoryBars" :key="item.label" class="overview-table-row">
              <span>{{ item.label }}</span>
              <strong>{{ item.value }}</strong>
            </div>
            <div v-if="!categoryBars.length" class="overview-table-row muted-row">
              <span>无数据</span>
              <strong>0</strong>
            </div>
          </div>
        </section>

        <section class="overview-table-section">
          <div class="overview-table-header">
            <span>题型</span>
            <span>题数</span>
          </div>
          <div class="overview-table-body">
            <div v-for="item in typeBars" :key="item.label" class="overview-table-row">
              <span>{{ item.label }}</span>
              <strong>{{ item.value }}</strong>
            </div>
            <div v-if="!typeBars.length" class="overview-table-row muted-row">
              <span>无数据</span>
              <strong>0</strong>
            </div>
          </div>
        </section>

        <section class="overview-table-section">
          <div class="overview-table-header">
            <span>热度</span>
            <span>点击次数</span>
          </div>
          <div class="overview-table-body">
            <div v-for="item in hottestTopics" :key="item.label" class="overview-table-row">
              <span>{{ item.rank }}. {{ item.label }}</span>
              <strong>{{ item.value }}</strong>
            </div>
            <div v-if="!hottestTopics.length" class="overview-table-row muted-row">
              <span>无数据</span>
              <strong>0</strong>
            </div>
          </div>
        </section>
      </section>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { getAdminOverview, getAdminUsersSummary, type AdminOverview, type AdminUsersSummary } from '@/app/api/admin'
import { useAuthStore } from '@/app/stores/auth'

const authStore = useAuthStore()

const defaultDurationDistribution = [
  { bucketOrder: 1, bucketLabel: '0-10 min', userCount: 0 },
  { bucketOrder: 2, bucketLabel: '10-30 min', userCount: 0 },
  { bucketOrder: 3, bucketLabel: '30-60 min', userCount: 0 },
  { bucketOrder: 4, bucketLabel: '60-120 min', userCount: 0 },
  { bucketOrder: 5, bucketLabel: '120+ min', userCount: 0 }
]

const typeNameMap: Record<string, string> = {
  'Single Choice': '单选题',
  Blank: '填空题',
  'Short Answer': '简答题',
  'Multiple Choice': '多选题',
  Unknown: '未知题型'
}

const toNumber = (value: unknown) => {
  const parsed = Number(value)
  return Number.isFinite(parsed) ? parsed : 0
}

const buildEmptyOverview = (): AdminOverview => ({
  totalQuestions: 0,
  totalCategories: 0,
  categoryStats: [],
  hotTopicStats: [],
  typeStats: []
})

const normalizeDurationDistribution = (value: unknown) => {
  const list = Array.isArray(value) ? value : []
  const map = new Map<number, { bucketOrder: number; bucketLabel: string; userCount: number }>()

  for (const item of list as Array<Record<string, unknown>>) {
    const order = toNumber(item.bucketOrder ?? item.bucketorder ?? item.bucket_order)
    const label = String(item.bucketLabel ?? item.bucketlabel ?? item.bucket_label ?? '').trim()
    const count = Math.max(0, toNumber(item.userCount ?? item.usercount ?? item.user_count))
    if (!order || !label) {
      continue
    }
    map.set(order, { bucketOrder: order, bucketLabel: label, userCount: count })
  }

  return defaultDurationDistribution.map((fallback) => map.get(fallback.bucketOrder) || fallback)
}

const normalizeUsersSummary = (value?: Partial<AdminUsersSummary> | null): AdminUsersSummary => ({
  totalUsers: Math.max(0, toNumber(value?.totalUsers)),
  averageActiveDurationSeconds: Math.max(0, toNumber(value?.averageActiveDurationSeconds)),
  trackedUsers: Math.max(0, toNumber(value?.trackedUsers)),
  dailyActiveDuration: Array.isArray(value?.dailyActiveDuration) ? value.dailyActiveDuration : [],
  durationDistribution: normalizeDurationDistribution(value?.durationDistribution)
})

const loading = ref(false)
const error = ref('')
const overview = ref<AdminOverview>(buildEmptyOverview())
const users = ref<AdminUsersSummary>(normalizeUsersSummary())

const durationBuckets = computed(() => normalizeDurationDistribution(users.value.durationDistribution))
const categoryBars = computed(() =>
  overview.value.categoryStats
    .slice(0, 8)
    .map((item) => ({ label: item.categoryName, value: Math.max(0, toNumber(item.count)) }))
)
const hottestTopics = computed(() =>
  overview.value.hotTopicStats
    .map((item) => ({ label: item.categoryName, value: Math.max(0, toNumber(item.count)) }))
    .slice(0, 3)
    .map((item, index) => ({ ...item, rank: index + 1 }))
)
const typeBars = computed(() =>
  overview.value.typeStats.map((item) => ({
    label: typeNameMap[item.typeName] || item.typeName,
    value: Math.max(0, toNumber(item.count))
  }))
)

const formatDuration = (seconds = 0) => {
  const safeSeconds = Math.max(0, Number(seconds) || 0)
  if (safeSeconds < 60) {
    return `${safeSeconds} 秒`
  }

  const hours = Math.floor(safeSeconds / 3600)
  const minutes = Math.floor((safeSeconds % 3600) / 60)
  if (!hours) {
    return `${minutes} 分钟`
  }
  return `${hours} 小时 ${minutes} 分钟`
}

const loadDashboard = async () => {
  loading.value = true
  error.value = ''

  try {
    const [overviewResult, usersResult] = await Promise.allSettled([getAdminOverview(), getAdminUsersSummary()])

    overview.value = overviewResult.status === 'fulfilled' ? overviewResult.value : buildEmptyOverview()
    users.value = usersResult.status === 'fulfilled' ? normalizeUsersSummary(usersResult.value) : normalizeUsersSummary()

    if (overviewResult.status === 'rejected' || usersResult.status === 'rejected') {
      error.value = '部分数据加载失败。'
    }
  } catch (err) {
    overview.value = buildEmptyOverview()
    users.value = normalizeUsersSummary()
    error.value = err instanceof Error ? err.message : '加载总览失败'
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  void loadDashboard()
})
</script>
