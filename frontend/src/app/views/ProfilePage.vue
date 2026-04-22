<template>
  <div class="profile-page-v2">
    <section class="profile-header-v2">
      <div class="profile-user-block">
        <UserAvatar :avatar-url="authStore.user?.avatarUrl" :nickname="authStore.user?.nickname" :size="64" />
        <div class="profile-user-copy">
          <p class="eyebrow">个人中心</p>
          <h2>{{ authStore.user?.nickname || '学习者' }}</h2>
          <span v-if="authStore.user?.email">{{ authStore.user.email }}</span>
        </div>
      </div>

      <div class="profile-header-stats">
        <div class="profile-stat-chip">
          <span>备考</span>
          <strong>{{ prepDays }}</strong>
          <small>天</small>
        </div>
        <div class="profile-countdown-chip">考研倒计时 {{ examCountdownDays }} 天</div>
      </div>

      <div class="profile-goal-card">
        <div class="goal-ring" :style="goalRingStyle">
          <strong>{{ todaySolvedCount }}</strong>
          <span>/{{ dailyGoal }}</span>
        </div>
        <div>
          <strong>今日刷题目标</strong>
          <span>{{ goalPercent }}%</span>
        </div>
      </div>
    </section>

    <p v-if="error" class="form-error">{{ error }}</p>
    <div v-if="loading" class="panel-card empty-state">正在加载个人数据...</div>

    <template v-else>
      <section class="profile-dashboard-grid">
        <article class="profile-panel profile-radar-panel">
          <div class="profile-panel-head">
            <h3>能力模型评估</h3>
            <span>{{ radarAverage }}%</span>
          </div>
          <div ref="radarRef" class="profile-chart profile-radar-chart"></div>
        </article>

        <article class="profile-panel profile-trend-panel">
          <div class="profile-panel-head">
            <h3>学习趋势</h3>
            <span>过去 7 天</span>
          </div>
          <div ref="trendRef" class="profile-chart profile-trend-chart"></div>
        </article>
      </section>

      <section class="profile-panel">
        <div class="profile-panel-head">
          <h3>学习足迹</h3>
          <span>过去 1 年 · {{ heatmapActiveDays }} 天有记录</span>
        </div>
        <div class="profile-heatmap-shell">
          <div class="heatmap-months" :style="heatmapGridStyle">
            <span
              v-for="label in heatmapMonthLabels"
              :key="label.key"
              :style="{ gridColumn: `${label.column} / span ${label.span}` }"
            >
              {{ label.label }}
            </span>
          </div>
          <div class="profile-heatmap-body">
            <div class="heatmap-weekdays">
              <span v-for="(label, index) in heatmapWeekdayLabels" :key="index">{{ label }}</span>
            </div>
            <div class="profile-heatmap" :style="heatmapGridStyle">
              <div v-for="week in heatmapWeeks" :key="week.key" class="heatmap-week">
                <span
                  v-for="(day, dayIndex) in week.days"
                  :key="day?.date || `${week.key}-${dayIndex}`"
                  class="heatmap-cell"
                  :class="day ? `level-${day.level}` : 'is-empty'"
                  :title="day ? `${day.date}: ${day.count} 题` : undefined"
                ></span>
              </div>
            </div>
          </div>
          <div class="heatmap-legend" aria-hidden="true">
            <span>少</span>
            <i class="heatmap-cell level-0"></i>
            <i class="heatmap-cell level-1"></i>
            <i class="heatmap-cell level-2"></i>
            <i class="heatmap-cell level-3"></i>
            <i class="heatmap-cell level-4"></i>
            <span>多</span>
          </div>
        </div>
      </section>

      <section class="profile-panel">
        <div class="profile-panel-head">
          <h3>最近练习历史</h3>
          <span>最近 3 次</span>
        </div>
        <div v-if="recentHistory.length" class="profile-history-list">
          <RouterLink
            v-for="item in recentHistory"
            :key="item.id"
            class="profile-history-row"
            :to="historyTarget(item)"
          >
            <div>
              <strong>{{ resolveCategoryName(item.categoryId) }}</strong>
              <span>{{ formatDateTime(item.createdAt) }}</span>
            </div>
            <div>
              <strong>{{ scoreOf(item) }}</strong>
              <span>{{ formatDuration(item.duration) }}</span>
            </div>
          </RouterLink>
        </div>
        <div v-else class="empty-state profile-empty">暂无练习记录。</div>
      </section>
    </template>

    <UserSettingsModal v-if="authStore.user" :open="settingsOpen" @close="settingsOpen = false" />
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'
import { use, type EChartsType } from 'echarts/core'
import { LineChart, RadarChart } from 'echarts/charts'
import { GridComponent, LegendComponent, RadarComponent, TooltipComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import UserAvatar from '@/app/components/UserAvatar.vue'
import UserSettingsModal from '@/app/components/UserSettingsModal.vue'
import { getCategories } from '@/app/api/categories'
import { getCategoryRates, getDailyRates, getOverview, getPracticeHistory } from '@/app/api/statistics'
import { useAuthStore } from '@/app/stores/auth'
import type { Category, CategoryRate, DailyRate, PracticeRecord, StatisticsOverview } from '@/app/types'
import { init } from 'echarts/core'

use([LineChart, RadarChart, GridComponent, LegendComponent, RadarComponent, TooltipComponent, CanvasRenderer])

type RadarDimension = {
  label: string
  patterns: RegExp[]
}

type HeatmapDay = {
  date: string
  count: number
  level: number
}

type HeatmapWeek = {
  key: string
  days: Array<HeatmapDay | null>
}

type HeatmapMonthLabel = {
  key: string
  label: string
  column: number
  span: number
}

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
const yearDailyRates = ref<DailyRate[]>([])
const history = ref<PracticeRecord[]>([])
const radarRef = ref<HTMLDivElement | null>(null)
const trendRef = ref<HTMLDivElement | null>(null)

let radarChart: EChartsType | null = null
let trendChart: EChartsType | null = null

const dailyGoal = 30
const radarDimensions: RadarDimension[] = [
  { label: '马原', patterns: [/马克思|马原/] },
  { label: '毛中特', patterns: [/毛泽东|毛中特|习近平|新思想|中国特色社会主义/] },
  { label: '史纲', patterns: [/近现代史|史纲/] },
  { label: '思修', patterns: [/思想道德|法治|思修/] },
  { label: '时政', patterns: [/形势|时政|当代世界/] }
]
const heatmapWeekdayLabels = ['', '一', '', '三', '', '五', '']

const sortedHistory = computed(() =>
  [...history.value].sort((left, right) => new Date(right.createdAt).getTime() - new Date(left.createdAt).getTime())
)
const recentHistory = computed(() => sortedHistory.value.slice(0, 3))
const categoryMap = computed(() =>
  Object.fromEntries([[0, '模拟考试'], ...categories.value.map((item) => [item.id, item.name])])
)
const todayKey = computed(() => toDateKey(new Date()))
const todaySolvedCount = computed(() => {
  const todayRate = dailyRates.value.find((item) => item.date === todayKey.value)
  if (todayRate) {
    return todayRate.totalCount
  }
  return sortedHistory.value
    .filter((item) => toDateKey(new Date(item.createdAt)) === todayKey.value)
    .reduce((sum, item) => sum + item.totalQuestions, 0)
})
const goalPercent = computed(() => Math.min(100, Math.round((todaySolvedCount.value / dailyGoal) * 100)))
const goalRingStyle = computed(() => ({
  background: `conic-gradient(#16a34a ${goalPercent.value * 3.6}deg, #e5edf4 0deg)`
}))
const prepDays = computed(() => {
  const start = sortedHistory.value.at(-1)?.createdAt || authStore.user?.createdAt
  if (!start) {
    return 1
  }
  return Math.max(1, Math.ceil((Date.now() - new Date(start).getTime()) / 86_400_000) + 1)
})
const examCountdownDays = computed(() => {
  const now = new Date()
  let target = new Date(now.getFullYear(), 11, 20)
  if (target.getTime() < now.getTime()) {
    target = new Date(now.getFullYear() + 1, 11, 20)
  }
  return Math.max(0, Math.ceil((target.getTime() - now.getTime()) / 86_400_000))
})
const radarValues = computed(() =>
  radarDimensions.map((dimension) => {
    const matches = categoryRates.value.filter((item) =>
      dimension.patterns.some((pattern) => pattern.test(item.categoryName))
    )
    const total = matches.reduce((sum, item) => sum + item.totalCount, 0)
    if (!total) {
      return 0
    }
    const correct = matches.reduce((sum, item) => sum + item.correctCount, 0)
    return Math.round((correct / total) * 100)
  })
)
const radarAverage = computed(() => {
  if (!radarValues.value.length) {
    return 0
  }
  return Math.round(radarValues.value.reduce((sum, item) => sum + item, 0) / radarValues.value.length)
})
const heatmapRange = computed(() => {
  const today = startOfDay(new Date())
  const firstDay = addDays(today, -364)
  return {
    firstDay,
    today,
    alignedStart: addDays(firstDay, -firstDay.getDay()),
    alignedEnd: addDays(today, 6 - today.getDay())
  }
})
const heatmapWeeks = computed<HeatmapWeek[]>(() => {
  const totals = new Map(yearDailyRates.value.map((item) => [item.date, Number(item.totalCount) || 0]))
  const maxCount = Math.max(1, ...Array.from(totals.values()))
  const { firstDay, today, alignedStart, alignedEnd } = heatmapRange.value
  const weeks: HeatmapWeek[] = []

  for (let cursor = new Date(alignedStart); cursor.getTime() <= alignedEnd.getTime(); cursor = addDays(cursor, 7)) {
    const days: Array<HeatmapDay | null> = []
    for (let dayIndex = 0; dayIndex < 7; dayIndex += 1) {
      const date = addDays(cursor, dayIndex)
      if (date.getTime() < firstDay.getTime() || date.getTime() > today.getTime()) {
        days.push(null)
        continue
      }

      const key = toDateKey(date)
      const count = totals.get(key) || 0
      days.push({
        date: key,
        count,
        level: heatmapLevel(count, maxCount)
      })
    }
    weeks.push({ key: toDateKey(cursor), days })
  }

  return weeks
})
const heatmapMonthLabels = computed<HeatmapMonthLabel[]>(() => {
  const starts: HeatmapMonthLabel[] = []
  let lastMonth = ''

  heatmapWeeks.value.forEach((week, index) => {
    const firstVisibleDay = week.days.find(isHeatmapDay)
    if (!firstVisibleDay) {
      return
    }

    const monthKey = firstVisibleDay.date.slice(0, 7)
    if (monthKey === lastMonth) {
      return
    }

    starts.push({
      key: monthKey,
      label: `${Number(monthKey.slice(5))}月`,
      column: index + 1,
      span: 1
    })
    lastMonth = monthKey
  })

  return starts.map((item, index) => ({
    ...item,
    span: Math.max(1, (starts[index + 1]?.column || heatmapWeeks.value.length + 1) - item.column)
  }))
})
const heatmapGridStyle = computed(() => ({
  gridTemplateColumns: `repeat(${heatmapWeeks.value.length}, 12px)`
}))
const heatmapActiveDays = computed(() =>
  heatmapWeeks.value.flatMap((week) => week.days).filter((day) => day && day.count > 0).length
)

const loadProfile = async () => {
  loading.value = true
  error.value = ''
  let loaded = false

  try {
    const [categoryList, overviewData, categoryData, dailyData, heatmapData, historyData] = await Promise.all([
      getCategories(),
      getOverview(),
      getCategoryRates(),
      getDailyRates(7),
      getDailyRates(365),
      getPracticeHistory(120)
    ])

    categories.value = categoryList
    overview.value = overviewData
    categoryRates.value = categoryData
    dailyRates.value = dailyData
    yearDailyRates.value = heatmapData
    history.value = historyData
    loaded = true
  } catch (err) {
    categories.value = []
    categoryRates.value = []
    dailyRates.value = []
    yearDailyRates.value = []
    history.value = []
    error.value = err instanceof Error ? err.message : '加载个人数据失败'
  } finally {
    loading.value = false
  }

  if (loaded) {
    await syncCharts()
  }
}

const resolveCategoryName = (categoryId: number) => categoryMap.value[categoryId] || `专题 ${categoryId}`
const scoreOf = (item: PracticeRecord) => {
  if (!item.totalQuestions) {
    return '0%'
  }
  return `${Math.round((item.correctCount / item.totalQuestions) * 100)}%`
}
const historyTarget = (item: PracticeRecord) => item.categoryId ? `/practice?categoryId=${item.categoryId}` : '/mock-exam'
const formatDuration = (seconds: number) => {
  const safeSeconds = Math.max(0, Number(seconds) || 0)
  if (safeSeconds < 60) {
    return `${safeSeconds} 秒`
  }
  const minutes = Math.floor(safeSeconds / 60)
  const remainSeconds = safeSeconds % 60
  return minutes < 60 ? `${minutes}分${String(remainSeconds).padStart(2, '0')}秒` : `${Math.floor(minutes / 60)}小时${minutes % 60}分`
}
const formatDateTime = (value: string) =>
  new Date(value).toLocaleString('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  })
const toDateKey = (value: Date) => {
  const year = value.getFullYear()
  const month = String(value.getMonth() + 1).padStart(2, '0')
  const day = String(value.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}
const startOfDay = (value: Date) => new Date(value.getFullYear(), value.getMonth(), value.getDate())
const addDays = (value: Date, days: number) => {
  const next = new Date(value)
  next.setDate(next.getDate() + days)
  return next
}
const heatmapLevel = (count: number, maxCount: number) => {
  if (count <= 0) {
    return 0
  }

  const ratio = count / Math.max(maxCount, 1)
  if (ratio <= 0.25) {
    return 1
  }
  if (ratio <= 0.5) {
    return 2
  }
  if (ratio <= 0.75) {
    return 3
  }
  return 4
}
const isHeatmapDay = (day: HeatmapDay | null): day is HeatmapDay => day !== null

const syncCharts = async () => {
  await nextTick()
  renderRadar()
  renderTrend()
  requestAnimationFrame(resizeCharts)
}
const renderRadar = () => {
  if (!radarRef.value) {
    return
  }
  radarChart ??= init(radarRef.value)
  radarChart.setOption({
    tooltip: { trigger: 'item' },
    radar: {
      radius: '62%',
      indicator: radarDimensions.map((item) => ({ name: item.label, max: 100 })),
      splitNumber: 4,
      axisName: { color: '#475569', fontSize: 12 },
      splitLine: { lineStyle: { color: '#dbe7f0' } },
      splitArea: { areaStyle: { color: ['#f8fafc', '#eef7f1'] } },
      axisLine: { lineStyle: { color: '#dbe7f0' } }
    },
    series: [
      {
        type: 'radar',
        data: [{ value: radarValues.value, name: '正确率' }],
        symbolSize: 4,
        areaStyle: { color: 'rgba(37, 99, 235, 0.14)' },
        lineStyle: { color: '#2563eb', width: 2 },
        itemStyle: { color: '#16a34a' }
      }
    ]
  }, true)
}
const renderTrend = () => {
  if (!trendRef.value) {
    return
  }
  trendChart ??= init(trendRef.value)
  const dates = dailyRates.value.map((item) => item.date.slice(5))
  const questionCounts = dailyRates.value.map((item) => Number(item.totalCount) || 0)
  const correctRates = dailyRates.value.map((item) => Number(item.correctRate) || 0)

  trendChart.setOption({
    tooltip: { trigger: 'axis' },
    legend: {
      top: 0,
      right: 4,
      itemWidth: 10,
      itemHeight: 8,
      textStyle: { color: '#64748b', fontSize: 11 }
    },
    grid: { left: 42, right: 44, top: 36, bottom: 28 },
    xAxis: {
      type: 'category',
      data: dates,
      axisLabel: { color: '#64748b', fontSize: 11 },
      axisLine: { lineStyle: { color: '#dbe7f0' } },
      axisTick: { show: false }
    },
    yAxis: [
      {
        type: 'value',
        name: '刷题数',
        min: 0,
        minInterval: 1,
        nameTextStyle: { color: '#64748b', fontSize: 10 },
        axisLabel: { color: '#64748b', fontSize: 10 },
        splitLine: { lineStyle: { color: '#eef2f7' } }
      },
      {
        type: 'value',
        name: '正确率',
        min: 0,
        max: 100,
        nameTextStyle: { color: '#64748b', fontSize: 10 },
        axisLabel: { color: '#64748b', fontSize: 10, formatter: '{value}%' },
        splitLine: { show: false }
      }
    ],
    series: [
      {
        name: '刷题数量',
        type: 'line',
        smooth: true,
        data: questionCounts,
        symbol: 'circle',
        symbolSize: 6,
        lineStyle: { color: '#2563eb', width: 3 },
        itemStyle: { color: '#2563eb' },
        areaStyle: { color: 'rgba(37, 99, 235, 0.1)' }
      },
      {
        name: '正确率',
        type: 'line',
        smooth: true,
        yAxisIndex: 1,
        data: correctRates,
        symbol: 'circle',
        symbolSize: 6,
        lineStyle: { color: '#16a34a', width: 3 },
        itemStyle: { color: '#16a34a' }
      }
    ]
  }, true)
}
const resizeCharts = () => {
  radarChart?.resize()
  trendChart?.resize()
}
const disposeCharts = () => {
  radarChart?.dispose()
  trendChart?.dispose()
  radarChart = null
  trendChart = null
}
const handlePageShow = (event: PageTransitionEvent) => {
  if (event.persisted) {
    void loadProfile()
  }
}

watch([categoryRates, dailyRates], () => {
  if (!loading.value) {
    void syncCharts()
  }
}, { deep: true })

onMounted(() => {
  void loadProfile()
  window.addEventListener('resize', resizeCharts)
  window.addEventListener('pageshow', handlePageShow)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', resizeCharts)
  window.removeEventListener('pageshow', handlePageShow)
  disposeCharts()
})
</script>
