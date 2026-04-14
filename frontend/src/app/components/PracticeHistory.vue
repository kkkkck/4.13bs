<template>
  <div class="panel-card">
    <div class="panel-head">
      <div>
        <h3>练习历史</h3>
        <p>最近几次刷题留下的轨迹，用来辅助复盘，而不只是看总分。</p>
      </div>
    </div>

    <div v-if="items.length" class="history-card-list">
      <article v-for="item in items" :key="item.id" class="history-record-card">
        <div class="history-record-top">
          <div>
            <strong>{{ resolveCategoryName(item.categoryId) }}</strong>
            <p>{{ formatTime(item.createdAt) }}</p>
          </div>
          <span class="history-badge">{{ accuracyOf(item) }}%</span>
        </div>

        <div class="history-progress">
          <span :style="{ width: `${Math.max(accuracyOf(item), 6)}%` }"></span>
        </div>

        <div class="history-stats">
          <span>总题数 {{ item.totalQuestions }}</span>
          <span>答对 {{ item.correctCount }}</span>
          <span>正确率 {{ accuracyOf(item) }}%</span>
          <span>用时 {{ formatDuration(item.duration) }}</span>
        </div>
      </article>
    </div>

    <div v-else class="empty-state">还没有练习记录，先去刷几道题。</div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { Category, PracticeRecord } from '@/app/types'

const props = defineProps<{
  items: PracticeRecord[]
  categories: Category[]
}>()

const categoryMap = computed(() =>
  Object.fromEntries([[0, '模拟考试'], ...props.categories.map((item) => [item.id, item.name])])
)

const resolveCategoryName = (categoryId: number) => categoryMap.value[categoryId] || `专题 ${categoryId}`

const accuracyOf = (item: PracticeRecord) => {
  if (!item.totalQuestions) {
    return 0
  }
  return Math.round((item.correctCount / item.totalQuestions) * 100)
}

const formatDuration = (seconds: number) => {
  const safeSeconds = Math.max(0, Number(seconds) || 0)
  if (safeSeconds < 60) {
    return `${safeSeconds} 秒`
  }

  const minutes = Math.floor(safeSeconds / 60)
  const remainSeconds = safeSeconds % 60
  if (minutes < 60) {
    return `${minutes} 分 ${String(remainSeconds).padStart(2, '0')} 秒`
  }

  const hours = Math.floor(minutes / 60)
  const remainMinutes = minutes % 60
  return `${hours} 小时 ${remainMinutes} 分`
}

const formatTime = (value: string) => {
  if (!value) {
    return '-'
  }
  return new Date(value).toLocaleString('zh-CN')
}
</script>
