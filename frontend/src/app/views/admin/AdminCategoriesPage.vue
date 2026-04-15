<template>
  <div class="page-stack admin-page-dense">
    <section class="panel-card">
      <div class="panel-head">
        <div>
          <h2>专题与章节</h2>
          <p>这里集中管理专题、章节和练习层级。</p>
        </div>

        <div class="row-actions">
          <button class="ghost-btn" :class="{ active: chapterListCollapsed }" @click="toggleChapterListCollapsed">
            {{ chapterListCollapsed ? '展开' : '收起' }}
          </button>
          <button class="ghost-btn" @click="openCreateChapter">新增章节</button>
          <button class="primary-btn" @click="openCreateRoot">新增专题</button>
        </div>
      </div>

      <div class="admin-search-bar compact">
        <input
          v-model.trim="categoryKeyword"
          class="input admin-search-input"
          type="text"
          placeholder="搜索专题或章节名称"
        />
      </div>

      <div v-if="!loading" class="status-strip">
        <span>专题 {{ filteredRootCategories.length }} / {{ rootCategories.length }}</span>
        <span>章节 {{ chapterCount }}</span>
        <span>启用专题 {{ enabledRootCount }}</span>
      </div>

      <p v-if="message" class="form-success">{{ message }}</p>
      <p v-if="error" class="form-error">{{ error }}</p>

      <div v-if="loading" class="empty-state">正在加载专题列表...</div>

      <div v-else-if="filteredRootCategories.length" class="root-stack">
        <article v-for="item in filteredRootCategories" :key="item.id" class="topic-card highlight">
          <div class="category-topline">
            <div>
              <span class="eyebrow">{{ item.practiceMode === 2 ? '章节专题' : '综合专题' }}</span>
              <strong>{{ item.name }}</strong>
            </div>

            <div class="row-actions">
              <button class="ghost-btn small" @click="openChapterManager(item)">管理章节</button>
              <button class="ghost-btn small" @click="openEdit(item)">编辑专题</button>
              <button class="ghost-btn small danger" @click="handleDelete(item)">删除专题</button>
            </div>
          </div>

          <p v-if="item.description">{{ item.description }}</p>

          <div class="record-meta">
            <span class="record-pill">{{ item.practiceMode === 2 ? '章节练习模式' : '专题练习模式' }}</span>
            <span class="record-pill">{{ item.status === 1 ? '启用中' : '已停用' }}</span>
            <span class="record-pill muted">排序 {{ item.sort || 0 }}</span>
          </div>

          <div v-show="!chapterListCollapsed" class="chapter-rail">
            <div class="category-topline">
              <span class="eyebrow">章节列表</span>
              <div class="row-actions">
                <span class="tag muted">{{ chaptersOf(item.id).length }} 个章节</span>
                <button class="ghost-btn small" @click="openChapterManager(item)">管理</button>
              </div>
            </div>

            <div v-if="chaptersOf(item.id).length" class="chapter-grid">
              <article v-for="chapter in chaptersOf(item.id)" :key="chapter.id" class="feature-card summary-card">
                <div class="category-topline">
                  <strong>{{ chapter.name }}</strong>
                  <span class="record-pill" :class="chapter.status === 1 ? 'success' : 'danger'">
                    {{ chapter.status === 1 ? '启用' : '停用' }}
                  </span>
                </div>
                <div class="record-meta">
                  <span class="record-pill muted">排序 {{ chapter.sort || 0 }}</span>
                </div>
              </article>
            </div>

            <div v-else class="form-tip">当前专题暂无章节。</div>
          </div>
        </article>
      </div>

      <div v-else class="empty-state">暂无符合条件的专题。</div>
    </section>

    <div v-if="showEditor" class="modal-mask" @click.self="closeEditor">
      <section class="panel-card modal-dialog large">
        <div class="panel-head">
          <div>
            <h3>{{ editorTitle }}</h3>
            <p>{{ isChapterForm ? '章节必须归属某个专题。' : '专题可继续向下拆章节。' }}</p>
          </div>
          <button class="ghost-btn small" type="button" @click="closeEditor">关闭</button>
        </div>

        <div class="modal-content-scroll modal-body-stack">
          <form class="editor-grid" @submit.prevent="handleSave">
            <label class="field-block">
              <span>{{ isChapterForm ? '章节名称' : '专题名称' }}</span>
              <input v-model="form.name" class="input" type="text" />
            </label>

            <label class="field-block">
              <span>排序值</span>
              <input v-model.number="form.sort" class="input" type="number" min="1" />
            </label>

            <label class="field-block">
              <span>父级专题</span>
              <select v-model.number="form.parentId" class="input select">
                <option :value="0">无（作为顶层专题）</option>
                <option v-for="item in parentOptions" :key="item.id" :value="item.id">
                  {{ item.name }}
                </option>
              </select>
            </label>

            <label v-if="!isChapterForm" class="field-block">
              <span>练习模式</span>
              <select v-model.number="form.practiceMode" class="input select">
                <option :value="1">专题练习</option>
                <option :value="2">章节练习</option>
              </select>
            </label>

            <label class="field-block">
              <span>状态</span>
              <select v-model.number="form.status" class="input select">
                <option :value="1">启用</option>
                <option :value="0">停用</option>
              </select>
            </label>

            <label class="field-block field-span-2">
              <span>{{ isChapterForm ? '章节描述' : '专题描述' }}</span>
              <textarea v-model="form.description" class="textarea" rows="4" />
            </label>

            <div class="editor-actions field-span-2">
              <button class="ghost-btn" type="button" @click="closeEditor">取消</button>
              <button class="primary-btn" type="submit">{{ saving ? '保存中...' : saveLabel }}</button>
            </div>
          </form>

          <section v-if="editingRootTopic" ref="chapterManagerSection" class="chapter-modal-section">
            <div class="category-topline">
              <div>
                <strong>本专题章节管理</strong>
                <p class="form-tip">可直接在这里新增、编辑或删除章节。</p>
              </div>
              <button class="ghost-btn small" @click="openCreateChapterForParent(editingRootTopic.id, true)">
                新增章节
              </button>
            </div>

            <div v-if="editingRootChapters.length" class="chapter-grid">
              <article v-for="chapter in editingRootChapters" :key="chapter.id" class="feature-card summary-card">
                <div class="category-topline">
                  <strong>{{ chapter.name }}</strong>
                  <span class="record-pill" :class="chapter.status === 1 ? 'success' : 'danger'">
                    {{ chapter.status === 1 ? '启用' : '停用' }}
                  </span>
                </div>
                <div class="record-meta">
                  <span class="record-pill muted">排序 {{ chapter.sort || 0 }}</span>
                </div>
                <div class="row-actions">
                  <button class="ghost-btn small" @click="openEdit(chapter, true)">编辑章节</button>
                  <button class="ghost-btn small danger" @click="handleDelete(chapter)">删除章节</button>
                </div>
              </article>
            </div>
            <p v-else class="form-tip">当前专题暂无章节。</p>
          </section>
        </div>
      </section>
    </div>

    <div v-if="pendingDeleteCategory" class="modal-mask" @click.self="closeDeleteModal">
      <section class="panel-card modal-dialog confirm-dialog">
        <div class="panel-head compact">
          <div>
            <h3>{{ pendingDeleteLabel }}</h3>
            <p>删除后不可恢复；如仍有关联题目或章节，系统会拦截删除。</p>
          </div>
        </div>

        <div class="modal-body-stack">
          <article class="confirm-card">
            <strong>{{ pendingDeleteCategory.name }}</strong>
            <div class="record-meta">
              <span class="record-pill">{{ pendingDeleteCategory.parentId ? '章节' : '专题' }}</span>
              <span v-if="pendingDeleteCategory.parentId" class="record-pill muted">{{ pendingDeleteParentName }}</span>
              <span class="record-pill muted">排序 {{ pendingDeleteCategory.sort || 0 }}</span>
              <span class="record-pill" :class="pendingDeleteCategory.status === 1 ? 'success' : 'danger'">
                {{ pendingDeleteCategory.status === 1 ? '启用' : '停用' }}
              </span>
            </div>
            <p v-if="pendingDeleteCategory.description">{{ pendingDeleteCategory.description }}</p>
          </article>

          <div class="row-actions confirm-actions">
            <button class="ghost-btn" @click="closeDeleteModal">取消</button>
            <button class="primary-btn danger-btn" :disabled="deleting" @click="confirmDelete">
              {{ deleting ? '删除中...' : `确认${pendingDeleteLabel}` }}
            </button>
          </div>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
import {
  createAdminCategory,
  deleteAdminCategory,
  getAdminCategories,
  updateAdminCategory
} from '@/app/api/admin'
import type { Category } from '@/app/types'

const categories = ref<Category[]>([])
const loading = ref(false)
const showEditor = ref(false)
const editingId = ref<number | null>(null)
const saving = ref(false)
const deleting = ref(false)
const message = ref('')
const error = ref('')
const categoryKeyword = ref('')
const returnRootId = ref<number | null>(null)
const CATEGORY_LAYOUT_STORAGE_KEY = 'admin-categories-layout-state-v1'
const chapterListCollapsed = ref(false)
const chapterManagerSection = ref<HTMLElement | null>(null)
const pendingDeleteCategory = ref<Category | null>(null)

const form = reactive<Partial<Category>>({
  name: '',
  description: '',
  sort: 1,
  parentId: 0,
  practiceMode: 1,
  status: 1
})

const rootCategories = computed(() => categories.value.filter((item) => !item.parentId))
const chapterCount = computed(() => categories.value.filter((item) => Boolean(item.parentId)).length)
const enabledRootCount = computed(() => rootCategories.value.filter((item) => item.status === 1).length)
const filteredRootCategories = computed(() => {
  const keyword = categoryKeyword.value.trim().toLowerCase()
  if (!keyword) {
    return rootCategories.value
  }

  return rootCategories.value.filter((item) => {
    const rootMatch = `${item.name} ${item.description || ''}`.toLowerCase().includes(keyword)
    if (rootMatch) {
      return true
    }
    return chaptersOf(item.id).some((chapter) =>
      `${chapter.name} ${chapter.description || ''}`.toLowerCase().includes(keyword)
    )
  })
})
const parentOptions = computed(() => rootCategories.value.filter((item) => item.id !== editingId.value))
const isChapterForm = computed(() => Boolean(form.parentId))
const editingCategory = computed(() => categories.value.find((item) => item.id === editingId.value) || null)
const editingRootTopic = computed(() => {
  if (!editingCategory.value || editingCategory.value.parentId) {
    return null
  }
  return editingCategory.value
})
const editingRootChapters = computed(() =>
  editingRootTopic.value ? chaptersOf(editingRootTopic.value.id) : []
)
const pendingDeleteLabel = computed(() => (pendingDeleteCategory.value?.parentId ? '删除章节' : '删除专题'))
const pendingDeleteParentName = computed(() => {
  const parentId = pendingDeleteCategory.value?.parentId
  if (!parentId) {
    return '所属专题'
  }
  return rootCategories.value.find((item) => item.id === parentId)?.name || '所属专题'
})
const editorTitle = computed(() => {
  if (editingId.value) {
    return isChapterForm.value ? '编辑章节' : '编辑专题'
  }
  return isChapterForm.value ? '新增章节' : '新增专题'
})
const saveLabel = computed(() => (isChapterForm.value ? '保存章节' : '保存专题'))

const chaptersOf = (parentId: number) => categories.value.filter((item) => item.parentId === parentId)

const clearFeedback = () => {
  message.value = ''
  error.value = ''
}

const restoreLayoutState = () => {
  try {
    const raw = localStorage.getItem(CATEGORY_LAYOUT_STORAGE_KEY)
    if (!raw) {
      return
    }
    const parsed = JSON.parse(raw) as { chapterListCollapsed?: boolean }
    chapterListCollapsed.value = Boolean(parsed.chapterListCollapsed)
  } catch {
    localStorage.removeItem(CATEGORY_LAYOUT_STORAGE_KEY)
  }
}

const persistLayoutState = () => {
  localStorage.setItem(
    CATEGORY_LAYOUT_STORAGE_KEY,
    JSON.stringify({
      chapterListCollapsed: chapterListCollapsed.value
    })
  )
}

const loadData = async () => {
  clearFeedback()
  loading.value = true

  try {
    categories.value = await getAdminCategories()
  } catch (err) {
    categories.value = []
    error.value = err instanceof Error ? err.message : '加载专题列表失败'
  } finally {
    loading.value = false
  }
}

const resetEditor = (parentId = 0) => {
  showEditor.value = false
  editingId.value = null
  returnRootId.value = null
  form.name = ''
  form.description = ''
  form.sort = 1
  form.parentId = parentId
  form.practiceMode = parentId ? 2 : 1
  form.status = 1
}

const returnToRootEditorIfNeeded = () => {
  if (!showEditor.value || !isChapterForm.value || !returnRootId.value) {
    return false
  }

  const root = categories.value.find((item) => item.id === returnRootId.value && !item.parentId)
  if (!root) {
    return false
  }

  openEdit(root)
  return true
}

const closeEditor = () => {
  if (returnToRootEditorIfNeeded()) {
    return
  }
  resetEditor()
}

const openCreateRoot = () => {
  clearFeedback()
  resetEditor(0)
  showEditor.value = true
}

const openCreateChapter = () => {
  clearFeedback()
  if (!rootCategories.value.length) {
    error.value = '请先创建至少一个专题，再为它添加章节'
    return
  }

  openCreateChapterForParent(rootCategories.value[0].id)
}

const openCreateChapterForParent = (parentId: number, keepRoot = false) => {
  clearFeedback()
  returnRootId.value = keepRoot ? parentId : null
  resetEditor(parentId)
  returnRootId.value = keepRoot ? parentId : null
  showEditor.value = true
}

const openEdit = (category: Category, keepRootContext = false) => {
  clearFeedback()
  returnRootId.value = keepRootContext && category.parentId ? category.parentId : null
  editingId.value = category.id
  form.name = category.name
  form.description = category.description || ''
  form.sort = category.sort || 1
  form.parentId = category.parentId || 0
  form.practiceMode = category.parentId ? 2 : category.practiceMode || 1
  form.status = category.status ?? 1
  showEditor.value = true
}

const openChapterManager = (category: Category) => {
  openEdit(category)
  void nextTick(() => {
    chapterManagerSection.value?.scrollIntoView({ behavior: 'smooth', block: 'start' })
  })
}

const toggleChapterListCollapsed = () => {
  chapterListCollapsed.value = !chapterListCollapsed.value
}

const closeDeleteModal = () => {
  if (deleting.value) {
    return
  }
  pendingDeleteCategory.value = null
}

const handleSave = async () => {
  const trimmedName = form.name?.trim()
  if (!trimmedName) {
    error.value = '名称不能为空'
    return
  }

  if (!form.sort || form.sort < 1) {
    error.value = '排序值必须大于 0'
    return
  }

  if (form.parentId && !rootCategories.value.some((item) => item.id === form.parentId && item.id !== editingId.value)) {
    error.value = '请选择有效的父级专题'
    return
  }

  clearFeedback()
  saving.value = true
  try {
    const payload = {
      name: trimmedName,
      description: form.description?.trim() || '',
      sort: form.sort,
      parentId: form.parentId || 0,
      practiceMode: form.parentId ? 2 : form.practiceMode,
      status: form.status
    }

    if (editingId.value) {
      await updateAdminCategory(editingId.value, payload)
      await loadData()
      message.value = isChapterForm.value ? '章节更新成功' : '专题更新成功'
    } else {
      await createAdminCategory(payload)
      await loadData()
      message.value = isChapterForm.value ? '章节创建成功' : '专题创建成功'
    }
    if (isChapterForm.value && returnRootId.value) {
      const root = categories.value.find((item) => item.id === returnRootId.value && !item.parentId)
      if (root) {
        openEdit(root)
        return
      }
    }
    resetEditor()
  } catch (err) {
    error.value = err instanceof Error ? err.message : '保存失败'
  } finally {
    saving.value = false
  }
}

const handleDelete = (category: Category) => {
  clearFeedback()
  pendingDeleteCategory.value = category
}

const confirmDelete = async () => {
  const category = pendingDeleteCategory.value
  if (!category) {
    return
  }

  const label = category.parentId ? '章节' : '专题'
  deleting.value = true
  try {
    await deleteAdminCategory(category.id)
    await loadData()
    message.value = `${label}已删除`
    pendingDeleteCategory.value = null
  } catch (err) {
    error.value = err instanceof Error ? err.message : '删除失败'
  } finally {
    deleting.value = false
  }
}

onMounted(() => {
  restoreLayoutState()
  void loadData()
})

watch(chapterListCollapsed, () => {
  persistLayoutState()
})
</script>
