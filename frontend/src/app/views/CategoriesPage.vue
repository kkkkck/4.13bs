<template>
  <div class="page-stack categories-page">
    <section class="hero-card compact tight-hero micro-hero band-hero categories-hero">
      <div>
        <p class="eyebrow">练习中心</p>
        <h2>按专题推进，按章节细拆，把日常刷题和阶段检测分开组织。</h2>
        <p class="hero-copy">有章节的专题可以直接进章节练习，没有章节的就先整组拉通训练。</p>
      </div>

      <div class="hero-aside">
        <div class="hero-inline-stats">
          <div class="banner-stat compact-band-stat">
            <span>启用专题</span>
            <strong>{{ rootCategories.length }}</strong>
          </div>
          <div class="banner-stat compact-band-stat">
            <span>章节专题</span>
            <strong>{{ chapterReadyCount }}</strong>
          </div>
          <div class="banner-stat compact-band-stat">
            <span>综合专题</span>
            <strong>{{ integratedCount }}</strong>
          </div>
        </div>
        <div class="row-actions">
          <RouterLink class="primary-btn" :to="continuePracticeRoute">{{ continuePracticeLabel }}</RouterLink>
          <RouterLink class="ghost-btn" to="/mock-exam">去做模拟考试</RouterLink>
        </div>
      </div>
    </section>

    <section class="panel-card">
      <div class="panel-head">
        <div>
          <h2>专题与章节</h2>
          <p>给出清晰的练习入口和下一步动作，不再浪费页面空间做重复统计。</p>
        </div>
        <span class="tag muted">共 {{ rootCategories.length }} 个专题</span>
      </div>

      <div class="status-strip">
        <span>练习题源 {{ sourceTypeLabel }}</span>
        <div class="row-actions">
          <button class="ghost-btn small" :class="{ active: selectedSourceType === 0 }" @click="setSelectedSourceType(0)">
            混合随机
          </button>
          <button class="ghost-btn small" :class="{ active: selectedSourceType === 1 }" @click="setSelectedSourceType(1)">
            只练真题
          </button>
          <button class="ghost-btn small" :class="{ active: selectedSourceType === 2 }" @click="setSelectedSourceType(2)">
            只练模拟题
          </button>
        </div>
      </div>

      <p v-if="error" class="form-error">{{ error }}</p>
      <div v-if="loading" class="empty-state">正在加载专题列表...</div>

      <div v-else-if="rootCategories.length" class="root-stack">
        <article v-for="category in rootCategories" :key="category.id" class="topic-card highlight">
          <div class="category-topline">
            <div>
              <span class="eyebrow">{{ category.practiceMode === 2 ? '章节推进' : '专题推进' }}</span>
              <strong>{{ category.name }}</strong>
            </div>
            <span class="tag muted">
              {{ chaptersOf(category.id).length ? `${chaptersOf(category.id).length} 个章节` : '未拆分章节' }}
            </span>
          </div>

          <p>{{ category.description || '当前专题已开放练习入口。' }}</p>

          <div class="record-meta">
            <span v-if="matchesLastPractice(category)" class="record-pill success">上次练到这里</span>
            <span class="record-pill">{{ chaptersOf(category.id).length ? '推荐先走章节' : '推荐先整组拉通' }}</span>
            <span class="record-pill muted">专题编号 {{ category.id }}</span>
          </div>

          <div v-if="chaptersOf(category.id).length" class="chapter-rail">
            <span class="eyebrow">章节入口</span>
            <div class="chapter-pills">
              <RouterLink
                v-for="chapter in chaptersOf(category.id)"
                :key="chapter.id"
                class="chapter-pill action"
                :to="buildPracticeRoute(chapter.id)"
              >
                {{ chapter.name }}
              </RouterLink>
            </div>
          </div>
          <p v-else class="form-tip">当前专题还没有章节拆分，系统会按整个专题组织练习。</p>

          <div class="row-actions">
            <RouterLink class="primary-btn" :to="buildPracticeRoute(category.id)">
              {{ chaptersOf(category.id).length ? '开始整组练习' : '开始专题练习' }}
            </RouterLink>
            <RouterLink class="ghost-btn" to="/mock-exam">阶段模拟</RouterLink>
          </div>
        </article>
      </div>

      <div v-else class="empty-state">当前没有可用专题，请先在后台启用专题。</div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'
import { getCategories } from '@/app/api/categories'
import { getLastPracticeCategoryId, getLastPracticeRoute, hasLastPracticeCategory } from '@/app/practice-resume'
import type { Category } from '@/app/types'

const categories = ref<Category[]>([])
const loading = ref(false)
const error = ref('')
const PRACTICE_SOURCE_FILTER_KEY = 'shuati:practice-source-filter'
const selectedSourceType = ref(0)

const rootCategories = computed(() => categories.value.filter((item) => !item.parentId))
const chapterReadyCount = computed(() => rootCategories.value.filter((item) => chaptersOf(item.id).length > 0).length)
const integratedCount = computed(() => rootCategories.value.filter((item) => chaptersOf(item.id).length === 0).length)
const continuePracticeRoute = computed(() => getLastPracticeRoute())
const continuePracticeLabel = computed(() => (hasLastPracticeCategory() ? '继续上次练习' : '先开始一轮练习'))
const lastPracticeCategoryId = computed(() => getLastPracticeCategoryId())
const sourceTypeLabel = computed(() => {
  if (selectedSourceType.value === 1) {
    return '只练真题'
  }
  if (selectedSourceType.value === 2) {
    return '只练模拟题'
  }
  return '混合随机'
})

const chaptersOf = (parentId: number) => categories.value.filter((item) => item.parentId === parentId)
const matchesLastPractice = (category: Category) =>
  category.id === lastPracticeCategoryId.value || chaptersOf(category.id).some((item) => item.id === lastPracticeCategoryId.value)

const restoreSelectedSourceType = () => {
  const raw = Number(localStorage.getItem(PRACTICE_SOURCE_FILTER_KEY) || '0')
  selectedSourceType.value = raw === 1 || raw === 2 ? raw : 0
}

const setSelectedSourceType = (sourceType: number) => {
  selectedSourceType.value = sourceType
}

const buildPracticeRoute = (categoryId: number) => ({
  path: '/practice',
  query: {
    categoryId: String(categoryId),
    ...(selectedSourceType.value ? { sourceType: String(selectedSourceType.value) } : {})
  }
})

const loadCategories = async () => {
  loading.value = true
  error.value = ''

  try {
    categories.value = await getCategories()
  } catch (err) {
    categories.value = []
    error.value = err instanceof Error ? err.message : '加载专题列表失败'
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  restoreSelectedSourceType()
  void loadCategories()
})

watch(selectedSourceType, () => {
  localStorage.setItem(PRACTICE_SOURCE_FILTER_KEY, String(selectedSourceType.value))
})
</script>
