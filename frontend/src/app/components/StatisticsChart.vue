<template>
  <div class="panel-card profile-compact-panel">
    <div class="panel-head">
      <div>
        <h3>专题正确率</h3>
      </div>
      <div class="record-meta chart-meta">
        <span class="record-pill muted">{{ practicedDaysLabel }}</span>
        <span class="record-pill muted">{{ practicedQuestionLabel }}</span>
      </div>
    </div>

    <div class="profile-table-scroll rate-table-scroll">
      <div v-if="sortedCategoryData.length" class="profile-table rate-table">
        <div class="profile-table-row profile-table-head">
          <span>专题</span>
          <span>答对</span>
          <span>总题</span>
          <span>正确率</span>
        </div>
        <div v-for="item in sortedCategoryData" :key="item.categoryName" class="profile-table-row">
          <strong>{{ item.categoryName }}</strong>
          <span>{{ item.correctCount }}</span>
          <span>{{ item.totalCount }}</span>
          <span class="profile-rate-value">{{ item.correctRate }}%</span>
        </div>
      </div>
      <div v-else class="empty-state profile-empty">暂无专题数据。</div>
    </div>
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
</script>
