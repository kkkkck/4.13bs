<template>
  <div class="page-stack admin-page-dense">
    <section class="panel-card">
      <div class="panel-head">
        <div>
          <h2>用户管理</h2>
          <p>集中处理筛选、批量操作和账号维护。</p>
        </div>
      </div>

      <div class="admin-search-bar">
        <input
          v-model.trim="filters.keyword"
          class="input admin-search-input"
          type="text"
          placeholder="搜索邮箱或用户名"
          @keyup.enter="searchUsers"
        />
        <div class="row-actions">
          <button class="ghost-btn" @click="searchUsers">搜索</button>
          <button class="ghost-btn" :class="{ active: filtersCollapsed }" @click="toggleFiltersCollapsed">
            {{ filtersCollapsed ? '展开' : '收起' }}
          </button>
          <div class="batch-toolbar">
            <select v-model="batchAction" class="input select">
              <option value="disable">批量禁用</option>
              <option value="enable">批量恢复</option>
              <option value="setAdmin">批量设为管理员</option>
              <option value="setUser">批量设为普通用户</option>
            </select>
            <button class="ghost-btn" :disabled="!selectedIds.length || batchProcessing" @click="handleBatchProcess">
              {{ batchProcessing ? '处理中...' : `批量处理（${selectedIds.length}）` }}
            </button>
          </div>
        </div>
      </div>

      <div v-show="!filtersCollapsed" class="admin-filter-toolbar">
        <label class="field-block toolbar-field">
          <span>角色</span>
          <select v-model.number="filters.role" class="input select">
            <option :value="-1">全部角色</option>
            <option :value="0">普通用户</option>
            <option :value="1">管理员</option>
          </select>
        </label>

        <label class="field-block toolbar-field">
          <span>状态</span>
          <select v-model.number="filters.status" class="input select">
            <option :value="-1">全部状态</option>
            <option :value="1">正常</option>
            <option :value="0">禁用</option>
          </select>
        </label>

        <label class="field-block toolbar-field">
          <span>活跃</span>
          <select v-model="filters.activityStatus" class="input select">
            <option value="all">全部活跃状态</option>
            <option value="active24h">24 小时内活跃</option>
            <option value="active7d">7 天内活跃</option>
            <option value="inactive7d">7 天未活跃</option>
          </select>
        </label>

        <label class="field-block toolbar-field">
          <span>排序字段</span>
          <select v-model="filters.sortField" class="input select">
            <option value="createdAt">注册时间</option>
            <option value="lastSeenAt">最近活跃</option>
            <option value="nickname">用户名</option>
            <option value="role">角色</option>
            <option value="status">状态</option>
          </select>
        </label>

        <label class="field-block toolbar-field">
          <span>排序方向</span>
          <select v-model="filters.sortOrder" class="input select">
            <option value="desc">倒序</option>
            <option value="asc">正序</option>
          </select>
        </label>

        <div class="toolbar-actions">
          <button class="ghost-btn" @click="resetFilters">清空筛选</button>
          <button class="primary-btn" @click="searchUsers">应用筛选</button>
        </div>
      </div>

      <div class="status-strip">
        <span>{{ filterSummary }}</span>
        <span>本页 {{ users.length }} 人 / 总计 {{ totalItems }} 人</span>
        <span>已勾选 {{ selectedIds.length }} 人</span>
        <button v-if="selectedIds.length" class="ghost-btn small" @click="clearSelection">清空勾选</button>
      </div>

      <p v-if="message" class="form-success">{{ message }}</p>
      <p v-if="error" class="form-error">{{ error }}</p>

      <div v-if="loading" class="empty-state">正在加载用户列表...</div>

      <div v-else-if="users.length" class="table-shell">
        <table class="table">
          <thead>
            <tr>
              <th class="table-checkbox-cell">
                <input
                  type="checkbox"
                  :checked="allCurrentPageSelected"
                  @change="toggleSelectAll($event)"
                />
              </th>
              <th class="col-id">ID</th>
              <th class="col-wide">用户</th>
              <th class="table-time-cell">最近活跃</th>
              <th class="table-status-cell">角色</th>
              <th class="table-status-cell">状态</th>
              <th class="table-time-cell">注册时间</th>
              <th class="table-actions-cell">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="user in users" :key="user.id">
              <td class="table-checkbox-cell">
                <input
                  type="checkbox"
                  :checked="selectedIds.includes(user.id)"
                  @change="toggleUserSelection(user.id)"
                />
              </td>
              <td class="col-id">{{ user.id }}</td>
              <td class="col-wide">
                <div class="table-primary-cell">
                  <strong>{{ user.nickname || '-' }}</strong>
                  <small>{{ user.email }}</small>
                </div>
              </td>
              <td class="table-time-cell">
                <div class="table-primary-cell">
                  <strong>{{ formatTime(user.lastSeenAt) }}</strong>
                  <small>{{ lastSeenHint(user.lastSeenAt) }}</small>
                </div>
              </td>
              <td class="table-status-cell">
                <span class="record-pill" :class="{ success: user.role === 1 }">
                  {{ user.role === 1 ? '管理员' : '普通用户' }}
                </span>
              </td>
              <td class="table-status-cell">
                <span class="record-pill" :class="user.status === 1 ? 'success' : 'danger'">
                  {{ user.status === 1 ? '正常' : '禁用' }}
                </span>
              </td>
              <td class="table-time-cell">{{ user.createdAt ? new Date(user.createdAt).toLocaleString('zh-CN') : '-' }}</td>
              <td class="table-actions-cell">
                <div class="row-actions">
                  <button class="ghost-btn small" @click="openEdit(user)">编辑</button>
                  <button
                    class="ghost-btn small"
                    :disabled="togglingId === user.id"
                    @click="handleToggleStatus(user)"
                  >
                    {{ togglingId === user.id ? '处理中...' : user.status === 1 ? '禁用' : '恢复' }}
                  </button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <div v-else class="empty-state">暂无符合条件的用户。</div>

      <div v-if="totalItems > 0" class="pagination-bar">
        <span class="pagination-meta">第 {{ currentPage }} / {{ totalPages }} 页，共 {{ totalItems }} 个用户</span>
        <div class="row-actions">
          <button class="ghost-btn small" :disabled="loading || currentPage === 1" @click="changePage(currentPage - 1)">
            上一页
          </button>
          <button
            class="ghost-btn small"
            :disabled="loading || currentPage >= totalPages"
            @click="changePage(currentPage + 1)"
          >
            下一页
          </button>
        </div>
      </div>
    </section>

    <div v-if="showEditor" class="modal-mask" @click.self="resetEditor">
      <section class="panel-card modal-dialog">
        <div class="panel-head">
          <div>
            <h3>编辑用户</h3>
            <p v-if="editingEmail">{{ editingEmail }}</p>
          </div>
          <button class="ghost-btn small" type="button" @click="resetEditor">关闭</button>
        </div>

        <form class="editor-grid" @submit.prevent="handleSave">
          <label class="field-block">
            <span>用户名</span>
            <input v-model.trim="form.nickname" class="input" type="text" />
          </label>

          <label class="field-block">
            <span>角色</span>
            <select v-model.number="form.role" class="input select">
              <option :value="0">普通用户</option>
              <option :value="1">管理员</option>
            </select>
          </label>

          <label class="field-block">
            <span>状态</span>
            <select v-model.number="form.status" class="input select">
              <option :value="1">正常</option>
              <option :value="0">禁用</option>
            </select>
          </label>

          <div class="editor-actions field-span-2">
            <button class="ghost-btn" type="button" @click="resetEditor">取消</button>
            <button class="primary-btn" type="submit">{{ saving ? '保存中...' : '保存变更' }}</button>
          </div>
        </form>
      </section>
    </div>

    <div v-if="showBatchConfirm" class="modal-mask" @click.self="closeBatchConfirm">
      <section class="panel-card modal-dialog confirm-dialog">
        <div class="panel-head compact">
          <div>
            <h3>批量处理用户</h3>
            <p>将对已勾选用户执行批量操作，请确认。</p>
          </div>
        </div>

        <div class="modal-body-stack">
          <article class="confirm-card">
            <strong>{{ batchActionLabel }}</strong>
            <div class="record-meta">
              <span class="record-pill">已勾选 {{ selectedIds.length }} 人</span>
              <span class="record-pill muted">当前页 {{ users.length }} 人</span>
            </div>
          </article>

          <div class="row-actions confirm-actions">
            <button class="ghost-btn" @click="closeBatchConfirm">取消</button>
            <button class="primary-btn danger-btn" :disabled="batchProcessing" @click="confirmBatchProcess">
              {{ batchProcessing ? '处理中...' : `确认${batchActionLabel}` }}
            </button>
          </div>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { batchUpdateAdminUserRole, batchUpdateAdminUserStatus, getAdminUsers, updateAdminUser } from '@/app/api/admin'
import type { User } from '@/app/types'
type BatchAction = 'disable' | 'enable' | 'setAdmin' | 'setUser'

const users = ref<User[]>([])
const loading = ref(false)
const showEditor = ref(false)
const editingId = ref<number | null>(null)
const editingEmail = ref('')
const saving = ref(false)
const togglingId = ref<number | null>(null)
const batchProcessing = ref(false)
const message = ref('')
const error = ref('')
const selectedIds = ref<number[]>([])
const currentPage = ref(1)
const pageSize = 20
const totalItems = ref(0)
const totalPages = computed(() => Math.max(1, Math.ceil(totalItems.value / pageSize)))
const USER_FILTER_STORAGE_KEY = 'admin-users-filter-state-v1'
const USER_PAGE_STORAGE_KEY = 'admin-users-page-state-v1'
const USER_LAYOUT_STORAGE_KEY = 'admin-users-layout-state-v1'
const filtersCollapsed = ref(false)
const showBatchConfirm = ref(false)

const filters = reactive({
  keyword: '',
  role: -1,
  status: -1,
  activityStatus: 'all',
  sortField: 'createdAt',
  sortOrder: 'desc'
})
const batchAction = ref<BatchAction>('disable')

const form = reactive<Partial<User>>({
  nickname: '',
  role: 0,
  status: 1
})

const sortFieldLabelMap: Record<string, string> = {
  createdAt: '注册时间',
  lastSeenAt: '最近活跃',
  nickname: '用户名',
  role: '角色',
  status: '状态'
}
const sortOrderLabelMap: Record<string, string> = {
  desc: '倒序',
  asc: '正序'
}
const allCurrentPageSelected = computed(
  () => users.value.length > 0 && users.value.every((user) => selectedIds.value.includes(user.id))
)

const filterSummary = computed(() => {
  const roleText = filters.role < 0 ? '全部角色' : filters.role === 1 ? '仅管理员' : '仅普通用户'
  const statusText = filters.status < 0 ? '全部状态' : filters.status === 1 ? '仅正常' : '仅禁用'
  const activityMap: Record<string, string> = {
    all: '全部活跃状态',
    active24h: '24 小时内活跃',
    active7d: '7 天内活跃',
    inactive7d: '7 天未活跃'
  }
  const keywordText = filters.keyword ? `关键词 ${filters.keyword}` : '未设关键词'
  return `${roleText} · ${statusText} · ${activityMap[filters.activityStatus]} · ${sortSummary.value} · ${keywordText}`
})
const batchActionLabel = computed(() => {
  const actionLabelMap: Record<BatchAction, string> = {
    disable: '批量禁用',
    enable: '批量恢复',
    setAdmin: '批量设为管理员',
    setUser: '批量设为普通用户'
  }
  return actionLabelMap[batchAction.value]
})
const sortSummary = computed(() => {
  const fieldLabel = sortFieldLabelMap[filters.sortField] || '注册时间'
  const orderLabel = sortOrderLabelMap[filters.sortOrder] || '倒序'
  return `${fieldLabel}${orderLabel}`
})

const formatTime = (value?: string | null) => {
  if (!value) {
    return '-'
  }
  return new Date(value).toLocaleString('zh-CN')
}

const lastSeenHint = (value?: string | null) => {
  if (!value) {
    return '暂无记录'
  }
  const diff = Date.now() - new Date(value).getTime()
  const hours = Math.floor(diff / (60 * 60 * 1000))
  if (hours < 1) {
    return '1 小时内有活跃'
  }
  if (hours < 24) {
    return `${hours} 小时前活跃`
  }
  const days = Math.floor(hours / 24)
  return `${days} 天前活跃`
}

const resetEditor = () => {
  showEditor.value = false
  editingId.value = null
  editingEmail.value = ''
  form.nickname = ''
  form.role = 0
  form.status = 1
}

const clearFeedback = () => {
  message.value = ''
  error.value = ''
}

const clearSelection = () => {
  selectedIds.value = []
}

const closeBatchConfirm = () => {
  if (batchProcessing.value) {
    return
  }
  showBatchConfirm.value = false
}

const restoreFilters = () => {
  try {
    const raw = localStorage.getItem(USER_FILTER_STORAGE_KEY)
    if (!raw) {
      return
    }
    const parsed = JSON.parse(raw) as Partial<typeof filters>
    filters.keyword = typeof parsed.keyword === 'string' ? parsed.keyword : ''
    filters.role = typeof parsed.role === 'number' ? parsed.role : -1
    filters.status = typeof parsed.status === 'number' ? parsed.status : -1
    filters.activityStatus = typeof parsed.activityStatus === 'string' ? parsed.activityStatus : 'all'
    filters.sortField = typeof parsed.sortField === 'string' ? parsed.sortField : 'createdAt'
    filters.sortOrder = typeof parsed.sortOrder === 'string' ? parsed.sortOrder : 'desc'
  } catch {
    localStorage.removeItem(USER_FILTER_STORAGE_KEY)
  }
}

const restorePage = () => {
  const raw = localStorage.getItem(USER_PAGE_STORAGE_KEY)
  const parsed = Number(raw)
  currentPage.value = Number.isFinite(parsed) && parsed > 0 ? parsed : 1
}

const restoreLayoutState = () => {
  try {
    const raw = localStorage.getItem(USER_LAYOUT_STORAGE_KEY)
    if (!raw) {
      return
    }
    const parsed = JSON.parse(raw) as { filtersCollapsed?: boolean }
    filtersCollapsed.value = Boolean(parsed.filtersCollapsed)
  } catch {
    localStorage.removeItem(USER_LAYOUT_STORAGE_KEY)
  }
}

const persistFilterState = () => {
  localStorage.setItem(
    USER_FILTER_STORAGE_KEY,
    JSON.stringify({
      keyword: filters.keyword,
      role: filters.role,
      status: filters.status,
      activityStatus: filters.activityStatus,
      sortField: filters.sortField,
      sortOrder: filters.sortOrder
    })
  )
}

const persistPageState = () => {
  localStorage.setItem(USER_PAGE_STORAGE_KEY, String(currentPage.value))
}

const persistLayoutState = () => {
  localStorage.setItem(
    USER_LAYOUT_STORAGE_KEY,
    JSON.stringify({
      filtersCollapsed: filtersCollapsed.value
    })
  )
}

const loadUsers = async (options: { preserveSelection?: boolean } = {}) => {
  const preserveSelection = options.preserveSelection ?? true
  clearFeedback()
  loading.value = true

  try {
    const targetPage = Math.max(1, currentPage.value)
    const data = await getAdminUsers({
      page: targetPage,
      size: pageSize,
      keyword: filters.keyword || undefined,
      role: filters.role >= 0 ? filters.role : undefined,
      status: filters.status >= 0 ? filters.status : undefined,
      activityStatus: filters.activityStatus !== 'all' ? filters.activityStatus : undefined,
      sortField: filters.sortField !== 'createdAt' ? filters.sortField : undefined,
      sortOrder: filters.sortOrder !== 'desc' ? filters.sortOrder : undefined
    })

    const resolvedTotalPages = Math.max(1, Math.ceil((data.total || 0) / pageSize))
    if (!data.records.length && data.total > 0 && targetPage > resolvedTotalPages) {
      currentPage.value = resolvedTotalPages
      await loadUsers()
      return
    }

    users.value = data.records
    totalItems.value = data.total
    currentPage.value = targetPage
    if (!preserveSelection) {
      clearSelection()
    }
  } catch (err) {
    users.value = []
    totalItems.value = 0
    if (!preserveSelection) {
      clearSelection()
    }
    error.value = err instanceof Error ? err.message : '加载用户列表失败'
  } finally {
    loading.value = false
  }
}

const resetFilters = async () => {
  filters.keyword = ''
  filters.role = -1
  filters.status = -1
  filters.activityStatus = 'all'
  filters.sortField = 'createdAt'
  filters.sortOrder = 'desc'
  currentPage.value = 1
  clearSelection()
  await loadUsers({ preserveSelection: false })
}

const searchUsers = async () => {
  currentPage.value = 1
  clearSelection()
  await loadUsers({ preserveSelection: false })
}

const toggleFiltersCollapsed = () => {
  filtersCollapsed.value = !filtersCollapsed.value
}

const changePage = async (page: number) => {
  currentPage.value = page
  await loadUsers({ preserveSelection: true })
}

const openEdit = (user: User) => {
  clearFeedback()
  editingId.value = user.id
  editingEmail.value = user.email
  form.nickname = user.nickname || ''
  form.role = user.role
  form.status = user.status
  showEditor.value = true
}

const toggleSelectAll = (event: Event) => {
  const checked = (event.target as HTMLInputElement).checked
  const currentPageIds = users.value.map((user) => user.id)
  if (!checked) {
    selectedIds.value = selectedIds.value.filter((id) => !currentPageIds.includes(id))
    return
  }
  selectedIds.value = [...new Set([...selectedIds.value, ...currentPageIds])]
}

const toggleUserSelection = (id: number) => {
  if (selectedIds.value.includes(id)) {
    selectedIds.value = selectedIds.value.filter((item) => item !== id)
    return
  }
  selectedIds.value = [...selectedIds.value, id]
}

const handleBatchProcess = async () => {
  if (!selectedIds.value.length || batchProcessing.value) {
    return
  }

  clearFeedback()
  showBatchConfirm.value = true
}

const confirmBatchProcess = async () => {
  if (!selectedIds.value.length || batchProcessing.value) {
    return
  }

  batchProcessing.value = true
  try {
    const result =
      batchAction.value === 'disable' || batchAction.value === 'enable'
        ? await batchUpdateAdminUserStatus({
            userIds: selectedIds.value,
            status: batchAction.value === 'disable' ? 0 : 1
          })
        : await batchUpdateAdminUserRole({
            userIds: selectedIds.value,
            role: batchAction.value === 'setAdmin' ? 1 : 0
          })
    await loadUsers({ preserveSelection: false })
    clearSelection()
    message.value =
      result.skippedCount > 0
        ? `${batchActionLabel.value}完成：成功 ${result.updatedCount} 个，跳过 ${result.skippedCount} 个`
        : `${batchActionLabel.value}成功（${result.updatedCount} 个）`
    showBatchConfirm.value = false
  } catch (err) {
    error.value = err instanceof Error ? err.message : '批量处理失败'
  } finally {
    batchProcessing.value = false
  }
}

const handleToggleStatus = async (user: User) => {
  clearFeedback()
  togglingId.value = user.id
  try {
    const nextStatus = user.status === 1 ? 0 : 1
    await updateAdminUser(user.id, {
      nickname: user.nickname,
      role: user.role,
      status: nextStatus
    })
    await loadUsers()
    message.value = nextStatus === 1 ? '用户已恢复' : '用户已禁用'
  } catch (err) {
    error.value = err instanceof Error ? err.message : '状态更新失败'
  } finally {
    togglingId.value = null
  }
}

const handleSave = async () => {
  if (!editingId.value) {
    return
  }

  if (!form.nickname?.trim()) {
    error.value = '用户名不能为空'
    return
  }

  clearFeedback()
  saving.value = true
  try {
    await updateAdminUser(editingId.value, {
      nickname: form.nickname.trim(),
      role: form.role,
      status: form.status
    })
    await loadUsers()
    resetEditor()
    message.value = '用户信息已更新'
  } catch (err) {
    error.value = err instanceof Error ? err.message : '保存失败'
  } finally {
    saving.value = false
  }
}

watch(
  () => ({ ...filters }),
  () => {
    persistFilterState()
  },
  { deep: true }
)

watch(currentPage, () => {
  persistPageState()
})

onMounted(() => {
  restoreFilters()
  restorePage()
  restoreLayoutState()
  void loadUsers()
})

watch(filtersCollapsed, () => {
  persistLayoutState()
})
</script>
