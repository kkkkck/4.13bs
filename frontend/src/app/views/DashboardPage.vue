<template>
  <div class="page-stack">
    <section class="hero-card compact tight-hero nano-hero band-hero dashboard-hero dashboard-hero-minimal">
      <div>
        <p class="eyebrow">今日策略</p>
        <h2>{{ authStore.user?.nickname || '同学' }}，先推进一个专题，再用模拟考试检查覆盖面和节奏感。</h2>
        <p class="hero-copy">
          这套系统已经把专题练习、章节训练、模拟考试、错题复盘和数据总结串成了一条完整的复习路径。
        </p>
        <div class="hero-badges">
          <span class="tag">专题推进</span>
          <span class="tag">章节训练</span>
          <span class="tag">模拟检验</span>
          <span class="tag">错题回补</span>
        </div>
      </div>

      <div class="hero-aside">
        <div class="banner-stat">
          <span>连续学习</span>
          <strong>{{ overview.continuousDays || 0 }} 天</strong>
        </div>
        <div class="banner-stat">
          <span>累计练习</span>
          <strong>{{ overview.totalQuestions || 0 }} 题</strong>
        </div>
        <div class="row-actions">
          <RouterLink class="primary-btn" :to="continuePracticeRoute">{{ continuePracticeLabel }}</RouterLink>
          <RouterLink class="ghost-btn" to="/mock-exam">模拟考试</RouterLink>
        </div>
      </div>
    </section>

    <section class="stats-grid compact-stats">
      <article class="metric-card mini">
        <span>整体正确率</span>
        <strong>{{ overview.totalCorrectRate || 0 }}%</strong>
        <small>跨专题的综合表现</small>
      </article>
      <article class="metric-card mini">
        <span>专题总数</span>
        <strong>{{ rootCategories.length }}</strong>
        <small>当前可直接进入训练的根专题数</small>
      </article>
      <article class="metric-card mini">
        <span>章节入口</span>
        <strong>{{ chapterTotal }}</strong>
        <small>已配置章节的训练入口总数</small>
      </article>
      <article class="metric-card mini">
        <span>推荐节奏</span>
        <strong>练 → 测 → 补</strong>
        <small>专题推进、模拟检测、错题回补</small>
      </article>
    </section>

    <p v-if="error" class="form-error">{{ error }}</p>

    <section class="panel-card">
      <div class="panel-head">
        <div>
          <h3>今日学习动线</h3>
          <p>把高频入口、当前状态和下一步动作压缩在同一屏，减少切换成本。</p>
        </div>
      </div>

      <div class="study-flow-grid">
        <article class="study-flow-card">
          <span class="study-flow-step">01</span>
          <strong>先推章节 / 专题</strong>
          <p>平时训练以章节或专题推进为主，把知识点一个个拉通。</p>
          <RouterLink class="ghost-btn" to="/categories">进入专题页</RouterLink>
        </article>
        <article class="study-flow-card">
          <span class="study-flow-step">02</span>
          <strong>再做模拟考试</strong>
          <p>用整卷视角检查章节覆盖和时间分配是否稳定。</p>
          <RouterLink class="ghost-btn" to="/mock-exam">去模拟考试</RouterLink>
        </article>
        <article class="study-flow-card">
          <span class="study-flow-step">03</span>
          <strong>最后回补错题</strong>
          <p>高频错题优先清空，形成更短的复习闭环。</p>
          <RouterLink class="ghost-btn" to="/wrong-book">打开错题本</RouterLink>
        </article>
      </div>
    </section>

    <section class="panel-card">
      <div class="panel-head">
        <div>
          <h3>优先推进的专题</h3>
          <p>先把一两个专题推进到位，再切下一个；有章节的专题可直接从章节入口进入。</p>
        </div>
      </div>

      <div v-if="loading" class="empty-state">正在加载专题与统计数据...</div>

      <div v-else-if="featuredCategories.length" class="topic-overview-grid dashboard-topic-grid">
        <article v-for="category in featuredCategories" :key="category.id" class="category-card compact">
          <div class="category-topline">
            <strong>{{ category.name }}</strong>
            <span class="tag muted">{{ chaptersOf(category.id).length }} 个章节</span>
          </div>
          <p>{{ category.description || '当前专题已开放训练入口。' }}</p>
          <div v-if="chaptersOf(category.id).length" class="chapter-pills">
            <span
              v-for="chapter in chaptersOf(category.id).slice(0, 3)"
              :key="chapter.id"
              class="chapter-pill"
            >
              {{ chapter.name }}
            </span>
          </div>
          <div class="row-actions">
            <RouterLink class="ghost-btn" :to="`/practice?categoryId=${category.id}`">
              {{ category.practiceMode === 2 ? '进入章节训练' : '进入专题训练' }}
            </RouterLink>
          </div>
        </article>
      </div>

      <div v-else class="empty-state">当前还没有可练习的专题，请先在后台启用专题和题目。</div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { getCategories } from '@/app/api/categories'
import { getLastPracticeRoute, hasLastPracticeCategory } from '@/app/practice-resume'
import { getOverview } from '@/app/api/statistics'
import { useAuthStore } from '@/app/stores/auth'
import type { Category, StatisticsOverview } from '@/app/types'

const authStore = useAuthStore()
const categories = ref<Category[]>([])
const loading = ref(false)
const error = ref('')
const overview = ref<StatisticsOverview>({
  totalQuestions: 0,
  totalCorrectRate: 0,
  continuousDays: 0
})

const rootCategories = computed(() => categories.value.filter((item) => !item.parentId))
const featuredCategories = computed(() => rootCategories.value.slice(0, 4))
const chapterTotal = computed(() => categories.value.filter((item) => Boolean(item.parentId)).length)
const continuePracticeRoute = computed(() => getLastPracticeRoute())
const continuePracticeLabel = computed(() => (hasLastPracticeCategory() ? '继续上次练习' : '开始练习'))

const chaptersOf = (parentId: number) => categories.value.filter((item) => item.parentId === parentId)

const loadData = async () => {
  loading.value = true
  error.value = ''

  try {
    const [categoryList, overviewData] = await Promise.all([getCategories(), getOverview()])
    categories.value = categoryList
    overview.value = overviewData
  } catch (err) {
    categories.value = []
    error.value = err instanceof Error ? err.message : '加载首页数据失败'
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  void loadData()
})
</script>
