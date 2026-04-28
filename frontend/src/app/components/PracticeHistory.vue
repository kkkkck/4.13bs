<template>
  <div class="panel-card profile-compact-panel">
    <div class="panel-head profile-tight-head">
      <h3>练习历史</h3>
      <span class="record-pill muted">{{ items.length }} 次</span>
    </div>

    <div v-if="items.length" class="history-tile-grid">
      <article v-for="item in items" :key="item.id" class="history-tile">
        <strong :title="resolveCategoryName(item.categoryId)">{{ resolveCategoryName(item.categoryId) }}</strong>
        <div>
          <span>{{ formatDate(item.createdAt) }}</span>
          <span>{{ completedCount(item) }}/{{ item.totalQuestions }} 题</span>
          <span>{{ formatDuration(item.duration) }}</span>
        </div>
      </article>
    </div>

    <div v-else class="empty-state profile-empty">暂无练习记录。</div>
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

const completedCount = (item: PracticeRecord) => Math.max(0, item.totalQuestions || 0)

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

const formatDate = (value: string) => {
  if (!value) {
    return '-'
  }
  return new Date(value).toLocaleDateString('zh-CN', {
    month: '2-digit',
    day: '2-digit'
  })
}
</script>
