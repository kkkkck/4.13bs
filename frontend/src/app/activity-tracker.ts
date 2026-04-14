import type { Router } from 'vue-router'
import type { ActivityHeartbeatPayload } from '@/app/api/activity'
import { sendActivityHeartbeat, sendActivityHeartbeatKeepalive } from '@/app/api/activity'
import { pinia } from '@/app/pinia'
import { useAuthStore } from '@/app/stores/auth'

const HEARTBEAT_INTERVAL_MS = 30000
const MIN_FLUSH_SECONDS = 5
const STORAGE_KEY = 'activity_session_id'

let currentPath = '/'
let lastVisibleAt = Date.now()
let inFlight = false
let queuedPayloads: ActivityHeartbeatPayload[] = []

const getSessionId = () => {
  const existing = sessionStorage.getItem(STORAGE_KEY)
  if (existing) {
    return existing
  }

  const generated = `sess_${Date.now()}_${Math.random().toString(36).slice(2, 10)}`
  sessionStorage.setItem(STORAGE_KEY, generated)
  return generated
}

const resolveElapsedSeconds = () => {
  const seconds = Math.round((Date.now() - lastVisibleAt) / 1000)
  return Math.max(0, Math.min(seconds, 120))
}

const resetClock = () => {
  lastVisibleAt = Date.now()
}

const enqueuePayload = (payload: ActivityHeartbeatPayload) => {
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
