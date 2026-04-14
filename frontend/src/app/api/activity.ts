import request, { API_BASE_URL } from '@/app/request'

export interface ActivityHeartbeatPayload {
  sessionId: string
  path: string
  activeSeconds: number
}

export function sendActivityHeartbeat(payload: ActivityHeartbeatPayload) {
  return request.post('/activity/heartbeat', payload)
}

export async function sendActivityHeartbeatKeepalive(payload: ActivityHeartbeatPayload) {
  const token = localStorage.getItem('token')
  if (!token) {
    return
  }

  await fetch(`${API_BASE_URL}/activity/heartbeat`, {
    method: 'POST',
    keepalive: true,
    headers: {
      'Content-Type': 'application/json;charset=utf-8',
      Authorization: `Bearer ${token}`
    },
    body: JSON.stringify(payload)
  }).catch(() => undefined)
}
