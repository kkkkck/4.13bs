<template>
  <div class="chart-grid profile-chart-grid">
    <div class="panel-card">
      <div class="panel-head">
        <div>
          <h3>专题正确率</h3>
        </div>
      </div>

      <div v-if="sortedCategoryData.length" class="rate-list">
        <article v-for="item in sortedCategoryData" :key="item.categoryName" class="rate-item">
          <div class="rate-item-head">
            <strong>{{ item.categoryName }}</strong>
            <span>{{ item.correctRate }}%</span>
          </div>
          <div class="rate-track">
            <span class="rate-fill" :style="{ width: `${Math.max(item.correctRate, 6)}%` }"></span>
          </div>
          <small>答对 {{ item.correctCount }} / 共 {{ item.totalCount }}</small>
        </article>
      </div>
      <div v-else class="chart-box compact-chart-box empty-state chart-empty">暂无专题数据。</div>
    </div>

    <div class="panel-card">
      <div class="panel-head">
        <div>
          <h3>近期趋势</h3>
        </div>
        <div class="record-meta chart-meta">
          <span class="record-pill muted">{{ practicedDaysLabel }}</span>
          <span class="record-pill muted">{{ practicedQuestionLabel }}</span>
        </div>
      </div>
      <div v-if="trendData.length" ref="lineRef" class="chart-box compact-chart-box"></div>
      <div v-else class="chart-box compact-chart-box empty-state chart-empty">暂无趋势数据。</div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { graphic, init, use, type EChartsType } from 'echarts/core'
import { LineChart } from 'echarts/charts'
import { TooltipComponent, GridComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import type { CategoryRate, DailyRate } from '@/app/types'

use([LineChart, TooltipComponent, GridComponent, CanvasRenderer])

const props = defineProps<{
  categoryData: CategoryRate[]
  trendData: DailyRate[]
}>()

const lineRef = ref<HTMLDivElement | null>(null)

let lineChart: EChartsType | null = null

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
const disposeLine = () => {
  lineChart?.dispose()
  lineChart = null
}

const renderLine = () => {
  if (!lineRef.value) {
    return
  }

  lineChart ??= init(lineRef.value)
  lineChart.setOption({
    tooltip: { trigger: 'axis' },
    grid: { top: 20, right: 20, bottom: 28, left: 38 },
    xAxis: {
      type: 'category',
      data: props.trendData.map((item) => item.date),
      axisLabel: { color: '#6d3b2b' },
      axisLine: { lineStyle: { color: '#d8c6ad' } }
    },
    yAxis: {
      type: 'value',
      min: 0,
      max: 100,
      axisLabel: {
        formatter: '{value}%',
        color: '#6d3b2b'
      },
      splitLine: { lineStyle: { color: '#ead8c2' } }
    },
    series: [
      {
        type: 'line',
        smooth: true,
        data: props.trendData.map((item) => item.correctRate),
        symbolSize: 7,
        lineStyle: { width: 3, color: '#9f1c22' },
        itemStyle: { color: '#d97706' },
        areaStyle: {
          color: new graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(159, 28, 34, 0.24)' },
            { offset: 1, color: 'rgba(159, 28, 34, 0.02)' }
          ])
        }
      }
    ]
  })
}

const resize = () => {
  lineChart?.resize()
}

const syncLine = async () => {
  await nextTick()
  if (!props.trendData.length) {
    disposeLine()
    return
  }
  renderLine()
}

watch(() => props.trendData, () => void syncLine(), { deep: true })

onMounted(() => {
  void syncLine()
  window.addEventListener('resize', resize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', resize)
  disposeLine()
})
</script>
