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

    <div class="secondary-stack">
      <div class="panel-card">
        <div class="panel-head">
          <div>
            <h3>近期趋势</h3>
          </div>
        </div>
        <div v-if="trendData.length" ref="lineRef" class="chart-box compact-chart-box"></div>
        <div v-else class="chart-box compact-chart-box empty-state chart-empty">暂无趋势数据。</div>
      </div>

      <div class="panel-card">
        <div class="panel-head compact">
          <div>
            <h3>复盘摘要</h3>
          </div>
        </div>

        <div class="modal-body-stack">
          <div class="insight-grid compact-insight-grid">
            <article class="feature-card insight-card compact">
              <span class="eyebrow">最近一次</span>
              <strong>{{ latestRateLabel }}</strong>
              <p>{{ latestDateLabel }}</p>
            </article>
            <article class="feature-card insight-card compact">
              <span class="eyebrow">相较上一轮</span>
              <strong>{{ trendDirectionLabel }}</strong>
              <p>{{ trendDirectionCopy }}</p>
            </article>
            <article class="feature-card insight-card compact">
              <span class="eyebrow">下一步</span>
              <strong>{{ actionLabel }}</strong>
              <p>{{ actionCopy }}</p>
            </article>
          </div>
        </div>
      </div>
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

const latestTrend = computed(() => props.trendData.at(-1) || null)
const previousTrend = computed(() => (props.trendData.length > 1 ? props.trendData.at(-2) || null : null))
const latestRateLabel = computed(() => (latestTrend.value ? `${latestTrend.value.correctRate}%` : '-'))
const latestDateLabel = computed(() => (latestTrend.value ? latestTrend.value.date : '继续练习后更新'))
const trendDelta = computed(() => {
  if (!latestTrend.value || !previousTrend.value) {
    return 0
  }
  return Math.round((latestTrend.value.correctRate - previousTrend.value.correctRate) * 100) / 100
})
const trendDirectionLabel = computed(() => {
  if (!latestTrend.value || !previousTrend.value) {
    return '等待更多记录'
  }
  if (trendDelta.value > 0) {
    return `上升 ${trendDelta.value}%`
  }
  if (trendDelta.value < 0) {
    return `回落 ${Math.abs(trendDelta.value)}%`
  }
  return '基本持平'
})
const trendDirectionCopy = computed(() => {
  if (!latestTrend.value || !previousTrend.value) {
    return '再完成一轮练习后更新。'
  }
  if (trendDelta.value > 0) {
    return '最近一轮更稳。'
  }
  if (trendDelta.value < 0) {
    return '最近一轮有回落。'
  }
  return '最近两次表现接近。'
})
const actionLabel = computed(() => {
  const weakest = sortedCategoryData.value.at(-1)
  if (!weakest) {
    return '先完成练习'
  }
  return weakest.categoryName
})
const actionCopy = computed(() => {
  const weakest = sortedCategoryData.value.at(-1)
  if (!weakest) {
    return '先完成几次练习。'
  }
  return `正确率 ${weakest.correctRate}%`
})
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
