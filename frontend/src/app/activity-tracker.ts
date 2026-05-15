import type { Router } from 'vue-router'
import type { ActivityHeartbeatPayload } from '@/app/api/activity'
import { sendActivityHeartbeat, sendActivityHeartbeatKeepalive } from '@/app/api/activity'
import { pinia } from '@/app/pinia'
import { useAuthStore } from '@/app/stores/auth'

const HEARTBEAT_INTERVAL_MS = 30000
const MIN_FLUSH_SECONDS = 5
const STORAGE_KEY = 'activity_session_id'

// 活跃度追踪器：记录用户在哪个页面停留了多久，上报给后端用于管理员查看最近活跃情况。
// 它不是刷题成绩统计，成绩统计在 PracticePage.vue 完成练习时单独上报。
let currentPath = '/'
let lastVisibleAt = Date.now()
let inFlight = false
let queuedPayloads: ActivityHeartbeatPayload[] = []

const getSessionId = () => {
  // sessionStorage 只在当前浏览器标签页有效，刷新不变，关闭标签页后失效。
  const existing = sessionStorage.getItem(STORAGE_KEY)
  if (existing) {
    return existing
  }

  const generated = `sess_${Date.now()}_${Math.random().toString(36).slice(2, 10)}`
  sessionStorage.setItem(STORAGE_KEY, generated)
  return generated
}

const resolveElapsedSeconds = () => {
  // 最多一次上报 120 秒，避免电脑睡眠/长时间后台导致一次性写入夸张时长。
  const seconds = Math.round((Date.now() - lastVisibleAt) / 1000)
  return Math.max(0, Math.min(seconds, 120))
}

const resetClock = () => {
  lastVisibleAt = Date.now()
}

const enqueuePayload = (payload: ActivityHeartbeatPayload) => {
  // 如果上一次心跳还没发完，新心跳先排队；同一路径的相邻心跳会合并，减少请求数。
  const lastPayload = queuedPayloads.at(-1)
  if (!lastPayload) {
    queuedPayloads.push(payload)
    return
  }

  if (lastPayload.path === payload.path && lastPayload.sessionId === payload.sessionId) {
    lastPayload.activeSeconds = Math.min(120, lastPayload.activeSeconds + payload.activeSeconds)
    return
  }

  queuedPayloads.push(payload)
}

const sendPayload = async (payload: ActivityHeartbeatPayload, keepalive = false) => {
  if (keepalive) {
    await sendActivityHeartbeatKeepalive(payload)
    return
  }

  inFlight = true
  try {
    await sendActivityHeartbeat(payload)
  } finally {
    inFlight = false
  }
}

const flushQueuedPayloads = async () => {
  while (!inFlight && queuedPayloads.length) {
    const payload = queuedPayloads.shift()
    if (!payload) {
      return
    }

    try {
      await sendPayload(payload, false)
    } catch {
      queuedPayloads.unshift(payload)
      return
    }
  }
}

const buildCurrentPayload = () => {
  // 小于 5 秒的停留不记录，过滤掉快速切页面造成的噪声。
  const activeSeconds = resolveElapsedSeconds()
  if (activeSeconds < MIN_FLUSH_SECONDS) {
    return null
  }

  return {
    sessionId: getSessionId(),
    path: currentPath,
    activeSeconds
  }
}

const flushHeartbeat = async (keepalive = false, nextPath?: string) => {
  // keepalive 用于页面关闭/切后台时尽量把最后一段停留时间送到后端。
  const authStore = useAuthStore(pinia)
  authStore.hydrate()

  if (!authStore.isLoggedIn) {
    if (nextPath) {
      currentPath = nextPath
    }
    resetClock()
    queuedPayloads = []
    return
  }

  const payload = buildCurrentPayload()
  if (!payload) {
    if (nextPath) {
      currentPath = nextPath
    }
    if (!document.hidden) {
      resetClock()
    }
    return
  }

  const previousLastVisibleAt = lastVisibleAt
  lastVisibleAt = Date.now()
  if (nextPath) {
    currentPath = nextPath
  }

  if (keepalive) {
    try {
      await sendPayload(payload, true)
    } catch {
      lastVisibleAt = previousLastVisibleAt
    }
    return
  }

  if (inFlight) {
    enqueuePayload(payload)
    return
  }

  try {
    await sendPayload(payload, false)
    await flushQueuedPayloads()
  } catch {
    lastVisibleAt = previousLastVisibleAt
  }
}

export const setupActivityTracker = (router: Router) => {
  // 在 main.ts 里调用一次即可：路由切换、页面隐藏、定时器都会触发心跳上报。
  currentPath = router.currentRoute.value.fullPath
  resetClock()

  router.afterEach((to) => {
    void flushHeartbeat(false, to.fullPath)
  })

  document.addEventListener('visibilitychange', () => {
    if (document.visibilityState === 'hidden') {
      void flushHeartbeat(true)
      return
    }
    resetClock()
  })

  window.addEventListener('focus', resetClock)
  window.addEventListener('beforeunload', () => {
    void flushHeartbeat(true)
  })

  window.setInterval(() => {
    if (document.visibilityState !== 'visible') {
      return
    }
    void flushHeartbeat(false)
  }, HEARTBEAT_INTERVAL_MS)
}
