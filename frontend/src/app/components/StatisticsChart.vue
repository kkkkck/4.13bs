<template>
  <div class="profile-analysis-stack">
    <section class="panel-card profile-compact-panel">
      <div class="panel-head profile-tight-head">
        <h3>专题正确率</h3>
        <span class="record-pill muted">{{ sortedCategoryData.length }} 项</span>
      </div>

      <div v-if="sortedCategoryData.length" class="rate-tile-grid">
        <article v-for="item in sortedCategoryData" :key="item.categoryName" class="rate-tile">
          <strong :title="item.categoryName">{{ item.categoryName }}</strong>
          <span>{{ item.correctRate }}%</span>
          <small>{{ item.correctCount }}/{{ item.totalCount }}</small>
        </article>
      </div>
      <div v-else class="empty-state profile-empty">暂无专题数据。</div>
    </section>

    <section class="panel-card profile-compact-panel trend-compact-panel">
      <div class="panel-head profile-tight-head">
        <h3>近期趋势</h3>
        <span class="record-pill muted">{{ practicedQuestionLabel }}</span>
      </div>

      <div v-if="trendData.length" class="trend-bar-card">
        <svg class="trend-bar-chart" viewBox="0 0 180 88" preserveAspectRatio="none" aria-hidden="true">
          <line class="trend-axis" x1="6" y1="76" x2="174" y2="76" />
          <rect
            v-for="bar in trendBars"
            :key="bar.key"
            class="trend-bar"
            :x="bar.x"
            :y="bar.y"
            :width="bar.width"
            :height="bar.height"
            rx="2"
          />
        </svg>
        <div class="trend-bar-meta">
          <span>{{ practicedDaysLabel }}</span>
          <strong>{{ latestRateLabel }}</strong>
        </div>
      </div>
      <div v-else class="empty-state profile-empty">暂无趋势数据。</div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { CategoryRate, DailyRate } from '@/app/types'

const props = defineProps<{
  categoryData: CategoryRate[]
  trendData: DailyRate[]
}>()

const sortedCategoryData = computed(() =>
  [...props.categoryData].sort((left, right) => {
    if (right.correctRate !== left.correctRate) {
      return right.correctRate - left.correctRate
    }
    return right.totalCount - left.totalCount
  })
)

const practicedTrendData = computed(() => props.trendData.filter((item) => item.totalCount > 0))
const practicedDaysLabel = computed(() => `${practicedTrendData.value.length}/${props.trendData.length} 天有记录`)
const practicedQuestionLabel = computed(() => `${practicedTrendData.value.reduce((sum, item) => sum + item.totalCount, 0)} 题`)
const latestTrend = computed(() => practicedTrendData.value.at(-1) || props.trendData.at(-1) || null)
const latestRateLabel = computed(() => (latestTrend.value ? `${latestTrend.value.correctRate}%` : '-'))
const trendBars = computed(() => {
  const count = props.trendData.length
  if (!count) {
    return []
  }

  const gap = 5
  const chartWidth = 156
  const barWidth = Math.max(8, Math.floor((chartWidth - gap * (count - 1)) / count))
  return props.trendData.map((item, index) => {
    const value = Math.max(0, Math.min(Number(item.correctRate) || 0, 100))
    const height = Math.max(3, Math.round((value / 100) * 62))
    return {
      key: `${item.date}-${index}`,
      x: 12 + index * (barWidth + gap),
      y: 76 - height,
      width: barWidth,
      height
    }
  })
})
</script>
