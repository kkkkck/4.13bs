<template>
  <div class="page-stack favorite-page">
    <section class="panel-card favorite-content-panel">
      <div class="panel-head">
        <div>
          <h2>收藏内容</h2>
          <p>优先处理你真正想反复看的题，而不是把收藏夹变成第二个题库。</p>
        </div>
      </div>

      <div class="status-strip">
        <span>收藏 {{ items.length }}</span>
        <span>可回练 {{ practiceReadyCount }}</span>
        <span>带解析 {{ withAnalysisCount }}</span>
        <span>可追溯来源 {{ withSourceCount }}</span>
        <span>最近收藏 {{ latestFavoriteLabel }}</span>
      </div>

      <p v-if="message" class="form-success">{{ message }}</p>
      <p v-if="error" class="form-error">{{ error }}</p>
      <div v-if="loading" class="empty-state">正在加载收藏内容...</div>

      <div v-else-if="items.length" class="list-stack">
        <article v-for="item in items" :key="item.record.id" class="list-card compact review-record-card">
          <div class="list-stack">
            <div class="category-topline">
              <strong>{{ item.question?.content || `题目 #${item.record.questionId}` }}</strong>
              <div class="record-meta">
                <span class="record-pill">{{ formatTime(item.record.createdAt) }}</span>
                <span v-if="item.question" class="record-pill">{{ difficultyText(item.question.difficulty) }}</span>
              </div>
            </div>

            <p>{{ item.question?.analysis || '这道题暂时还没有解析，建议补充后再放入高频复习池。' }}</p>

            <div class="record-meta">
              <span v-if="item.question" class="record-pill">{{ sourceTypeText(item.question.sourceType) }}</span>
              <span class="record-pill">{{ item.question?.source || '未标注来源' }}</span>
              <span v-for="tag in tagList(item.question)" :key="tag" class="record-pill muted">{{ tag }}</span>
            </div>
          </div>

          <div class="list-actions">
            <RouterLink class="ghost-btn" :to="buildPracticeLink(item.question)">去练习</RouterLink>
            <button
              class="ghost-btn"
              :disabled="removingQuestionId === item.record.questionId"
              @click="handleRemove(item.record.questionId)"
            >
              {{ removingQuestionId === item.record.questionId ? '移除中...' : '取消收藏' }}
            </button>
          </div>
        </article>
      </div>

      <div v-else class="empty-state">你还没有收藏题目，练习时点击“加入收藏”即可保留。</div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink, type RouteLocationRaw } from 'vue-router'
import { getFavorites, removeFavorite } from '@/app/api/favorites'
import { getQuestionsByIds } from '@/app/api/questions'
import type { FavoriteRecord, Question } from '@/app/types'

interface FavoriteItem {
  record: FavoriteRecord
  question: Question | null
}

const items = ref<FavoriteItem[]>([])
const loading = ref(false)
const error = ref('')
const message = ref('')
const removingQuestionId = ref<number | null>(null)

const sortedItems = computed(() =>
  [...items.value].sort((left, right) => new Date(right.record.createdAt).getTime() - new Date(left.record.createdAt).getTime())
)
const withAnalysisCount = computed(() => items.value.filter((item) => Boolean(item.question?.analysis)).length)
const withSourceCount = computed(() => items.value.filter((item) => Boolean(item.question?.source)).length)
const practiceReadyCount = computed(() => items.value.filter((item) => Boolean(item.question)).length)
const latestFavoriteLabel = computed(() => {
  const latest = sortedItems.value[0]
  if (!latest) {
    return '暂无收藏'
  }
  return new Date(latest.record.createdAt).toLocaleDateString('zh-CN')
})

const buildPracticeLink = (question: Question | null): RouteLocationRaw => {
  if (!question) {
    return '/categories'
  }

  return {
    path: '/practice',
    query: {
      categoryId: String(question.categoryId),
      questionId: String(question.id),
      from: 'favorite'
    }
  }
}

const difficultyText = (difficulty = 0) => ['基础', '提高', '冲刺'][difficulty - 1] || '未标注难度'
const sourceTypeText = (sourceType = 1) => (sourceType === 2 ? '模拟题' : '真题')
const tagList = (question: Question | null) =>
  question?.tags
    ? question.tags
        .split(/[，,、\s]+/)
        .map((item) => item.trim())
        .filter(Boolean)
        .slice(0, 3)
    : []

const formatTime = (value: string) => {
  if (!value) {
    return '-'
  }
  return new Date(value).toLocaleDateString('zh-CN')
}

const loadData = async () => {
  loading.value = true
  error.value = ''
  message.value = ''

  try {
    const records = await getFavorites()
    const questions = await getQuestionsByIds(records.map((record) => record.questionId)).catch(() => [])
    const questionMap = new Map(questions.map((question) => [question.id, question]))
    items.value = records.map((record) => ({
      record,
      question: questionMap.get(record.questionId) || null
    }))
  } catch (err) {
    items.value = []
    error.value = err instanceof Error ? err.message : '加载收藏内容失败'
  } finally {
    loading.value = false
  }
}

const handleRemove = async (questionId: number) => {
  error.value = ''
  message.value = ''
  removingQuestionId.value = questionId
  try {
    await removeFavorite(questionId)
    items.value = items.value.filter((item) => item.record.questionId !== questionId)
    message.value = '已取消收藏'
  } catch (err) {
    error.value = err instanceof Error ? err.message : '取消收藏失败'
  } finally {
    removingQuestionId.value = null
  }
}

onMounted(() => {
  void loadData()
})
</script>
