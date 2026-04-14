<template>
  <div class="page-stack">
    <p v-if="error" class="form-error">{{ error }}</p>
    <div v-if="loading" class="panel-card empty-state">正在加载后台总览...</div>

    <template v-else>
      <section class="hero-card compact tight-hero nano-hero band-hero admin-overview-hero">
        <div>
          <p class="eyebrow">管理总览</p>
          <h2>把题库、用户和活跃信息压到一屏里，先看趋势，再做维护。</h2>
          <p class="hero-copy">这一页现在只保留稳定可读的信息块，不再浪费空间做重复占位。</p>
        </div>

        <div class="hero-aside">
          <div class="banner-stat">
            <span>注册用户</span>
            <strong>{{ users.totalUsers }}</strong>
          </div>
          <div class="banner-stat">
            <span>题库总量</span>
            <strong>{{ overview.totalQuestions }}</strong>
          </div>
        </div>
      </section>

      <section class="stats-grid compact-stats">
        <article class="metric-card mini">
          <span>题库总量</span>
          <strong>{{ overview.totalQuestions }}</strong>
          <small>当前系统已收录的题目数量</small>
        </article>
        <article class="metric-card mini">
          <span>专题数量</span>
          <strong>{{ overview.totalCategories }}</strong>
          <small>当前前台可见的根专题数量</small>
        </article>
        <article class="metric-card mini">
          <span>注册用户</span>
          <strong>{{ users.totalUsers }}</strong>
          <small>当前可管理的账号总量</small>
        </article>
        <article class="metric-card mini">
          <span>人均停留时长</span>
          <strong>{{ formatDuration(users.averageActiveDurationSeconds) }}</strong>
          <small>{{ users.trackedUsers }} 位用户已有停留记录</small>
        </article>
      </section>

      <section class="insight-grid admin-insight-grid">
        <article class="feature-card insight-card">
          <span class="eyebrow">题库最重专题</span>
          <strong>{{ topCategory?.categoryName || '暂无' }}</strong>
          <p>{{ topCategory ? `当前题量 ${topCategory.count} 题` : '当前还没有专题题量数据。' }}</p>
        </article>
        <article class="feature-card insight-card">
          <span class="eyebrow">主力题型</span>
          <strong>{{ dominantTypeLabel }}</strong>
          <p>{{ dominantTypeCount ? `当前题量 ${dominantTypeCount} 题` : '当前还没有题型结构数据。' }}</p>
        </article>
        <article class="feature-card insight-card">
          <span class="eyebrow">停留主区间</span>
          <strong>{{ largestDurationBucket?.bucketLabel || '暂无' }}</strong>
          <p>{{ largestDurationBucket ? `当前用户数 ${largestDurationBucket.userCount}` : '当前还没有停留分段数据。' }}</p>
        </article>
      </section>

      <section class="chart-grid admin-chart-grid">
        <article class="panel-card">
          <div class="panel-head">
            <h3>用户停留分段</h3>
            <p>按用户累计停留时长分档，直接看活跃层级。</p>
          </div>
          <div class="duration-bar-chart">
            <article v-for="bucket in durationBuckets" :key="bucket.bucketOrder" class="duration-bar-card">
              <div class="duration-bar-head">
                <span>{{ bucket.bucketLabel }}</span>
                <strong>{{ bucket.userCount }}</strong>
              </div>
              <div class="duration-bar-track">
                <span
                  class="duration-bar-fill"
                  :style="{ width: buildBarWidth(bucket.userCount, durationBucketMax, 8) }"
                  :class="{ empty: bucket.userCount === 0 }"
                ></span>
              </div>
            </article>
          </div>
          <p v-if="!users.trackedUsers" class="form-tip">当前暂无有效停留记录，先显示空分段柱状图。</p>
        </article>

        <article class="panel-card">
          <div class="panel-head">
            <h3>专题题量分布</h3>
            <p>直接看题库主要集中在哪些专题。</p>
          </div>
          <div v-if="categoryBars.length" class="duration-bar-chart">
            <article v-for="item in categoryBars" :key="item.label" class="duration-bar-card">
              <div class="duration-bar-head">
                <span>{{ item.label }}</span>
                <strong>{{ item.value }}</strong>
              </div>
              <div class="duration-bar-track">
                <span class="duration-bar-fill" :style="{ width: buildBarWidth(item.value, categoryBarMax) }"></span>
              </div>
            </article>
          </div>
          <p v-else class="form-tip">当前还没有专题题量数据。</p>
        </article>

        <article class="panel-card panel-card-stack">
          <div class="panel-head">
            <h3>题型结构</h3>
            <p>看清单选、多选、填空和简答题的整体占比。</p>
          </div>
          <div v-if="typeBars.length" class="duration-bar-chart">
            <article v-for="item in typeBars" :key="item.label" class="duration-bar-card">
              <div class="duration-bar-head">
                <span>{{ item.label }}</span>
                <strong>{{ item.value }}</strong>
              </div>
              <div class="duration-bar-track">
                <span class="duration-bar-fill" :style="{ width: buildBarWidth(item.value, typeBarMax) }"></span>
              </div>
            </article>
          </div>
          <p v-else class="form-tip">当前还没有题型结构数据。</p>

          <div class="panel-subsection">
            <div class="panel-subhead">
              <strong>热度最高专题</strong>
            </div>
            <div v-if="hottestTopics.length" class="hot-topic-list hot-topic-list-inline">
              <article v-for="item in hottestTopics" :key="item.label" class="hot-topic-item">
                <span class="hot-topic-rank">TOP {{ item.rank }}</span>
                <div class="hot-topic-copy">
                  <strong>{{ item.label }}</strong>
                  <small>{{ item.value }} 题</small>
                </div>
              </article>
            </div>
            <p v-else class="form-tip">当前还没有可展示的专题热度数据。</p>
          </div>
        </article>
      </section>

      <section class="panel-card">
        <div class="panel-head">
          <h3>快捷入口</h3>
          <p>先看数，再进页改，降低在后台模块间切换时的认知成本。</p>
        </div>
        <div class="feature-grid">
          <RouterLink class="feature-card" to="/admin/questions">
            <strong>题库管理</strong>
            <p>维护题目内容、题型结构、状态与导入结果。</p>
          </RouterLink>
          <RouterLink class="feature-card" to="/admin/categories">
            <strong>专题管理</strong>
            <p>调整专题、章节、排序与练习模式。</p>
          </RouterLink>
          <RouterLink v-if="authStore.isSuperAdmin" class="feature-card" to="/admin/users">
            <strong>用户管理</strong>
            <p>查看活跃度、角色与账号状态，判断谁在持续使用。</p>
          </RouterLink>
        </div>
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

const topCategory = computed(() => [...overview.value.categoryStats].sort((left, right) => right.count - left.count)[0] || null)
const dominantType = computed(() => [...overview.value.typeStats].sort((left, right) => right.count - left.count)[0] || null)
const dominantTypeLabel = computed(() =>
  dominantType.value ? typeNameMap[dominantType.value.typeName] || dominantType.value.typeName : '暂无'
)
const dominantTypeCount = computed(() => dominantType.value?.count || 0)
const durationBuckets = computed(() => normalizeDurationDistribution(users.value.durationDistribution))
const durationBucketMax = computed(() => Math.max(1, ...durationBuckets.value.map((item) => item.userCount)))
const largestDurationBucket = computed(() =>
  [...durationBuckets.value].sort((left, right) => right.userCount - left.userCount)[0] || null
)
const categoryBars = computed(() =>
  overview.value.categoryStats
    .slice(0, 8)
    .map((item) => ({ label: item.categoryName, value: Math.max(0, toNumber(item.count)) }))
)
const categoryBarMax = computed(() => Math.max(1, ...categoryBars.value.map((item) => item.value)))
const hottestTopics = computed(() =>
  [...categoryBars.value]
    .sort((left, right) => right.value - left.value)
    .slice(0, 3)
    .map((item, index) => ({ ...item, rank: index + 1 }))
)
const typeBars = computed(() =>
  overview.value.typeStats.map((item) => ({
    label: typeNameMap[item.typeName] || item.typeName,
    value: Math.max(0, toNumber(item.count))
  }))
)
const typeBarMax = computed(() => Math.max(1, ...typeBars.value.map((item) => item.value)))

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

const buildBarWidth = (value: number, max: number, minPercent = 10) => {
  if (max <= 0) {
    return `${minPercent}%`
  }
  return `${Math.max((value / max) * 100, minPercent)}%`
}

const loadDashboard = async () => {
  loading.value = true
  error.value = ''

  try {
    const [overviewResult, usersResult] = await Promise.allSettled([getAdminOverview(), getAdminUsersSummary()])

    overview.value = overviewResult.status === 'fulfilled' ? overviewResult.value : buildEmptyOverview()
    users.value = usersResult.status === 'fulfilled' ? normalizeUsersSummary(usersResult.value) : normalizeUsersSummary()

    if (overviewResult.status === 'rejected' || usersResult.status === 'rejected') {
      error.value = '部分总览数据加载失败，已展示可用内容。'
    }
  } catch (err) {
    overview.value = buildEmptyOverview()
    users.value = normalizeUsersSummary()
    error.value = err instanceof Error ? err.message : '加载后台总览失败'
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  void loadDashboard()
})
</script>
