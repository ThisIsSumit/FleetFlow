import { useCallback, useEffect, useRef, useState } from 'react'
import { useAuth } from '@/stores/auth'
import { refreshAccessToken } from '@/lib/api'
export interface LiveMessage { type: string; driverId?: string; latitude?: number; longitude?: number; code?: string }

/** Seconds-since-epoch expiry of a JWT, or 0 if it cannot be read. Only used to decide whether to refresh before reconnecting. */
function expiry(token: string): number {
  try { return JSON.parse(atob(token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/'))).exp ?? 0 } catch { return 0 }
}

/** Native WebSocket with exponential-backoff reconnect. The access token rides in the query string because browsers cannot set WS headers.
 *  The server closes a socket when its token expires or its session is revoked (code 4001); reconnects always use the newest token and
 *  refresh it first when it is about to expire. */
export function useLiveSocket(onMessage: (m: LiveMessage) => void) {
  const signedIn = useAuth((s) => !!s.accessToken); const cb = useRef(onMessage); cb.current = onMessage
  const [status, setStatus] = useState<'connecting' | 'open' | 'closed'>('connecting'); const wsRef = useRef<WebSocket | null>(null)
  useEffect(() => {
    if (!signedIn) return
    let ws: WebSocket | null = null, retry = 0, stop = false, timer = 0
    const connect = async () => {
      let token = useAuth.getState().accessToken
      if (token && expiry(token) * 1000 < Date.now() + 30_000) token = await refreshAccessToken().catch(() => null)
      if (stop) return
      if (!token) { setStatus('closed'); return } // signed out; the HTTP layer redirects to login
      ws = new WebSocket(`${location.protocol === 'https:' ? 'wss' : 'ws'}://${location.host}/ws/live?token=${encodeURIComponent(token)}`)
      wsRef.current = ws
      ws.onopen = () => { retry = 0; setStatus('open') }
      ws.onmessage = (e) => { try { cb.current(JSON.parse(e.data)) } catch { /* ignore malformed frames */ } }
      ws.onclose = (e) => {
        setStatus('closed'); if (stop) return
        const delay = e.code === 4001 ? 0 : Math.min(15000, 1000 * 2 ** retry++) // token expired/revoked: reconnect at once with a fresh one
        timer = window.setTimeout(() => { void connect() }, delay)
      }
    }
    void connect()
    return () => { stop = true; clearTimeout(timer); ws?.close() }
  }, [signedIn])
  /** Returns false if the socket is not open, so callers can fall back to REST. */
  const send = useCallback((m: object) => { const w = wsRef.current; if (w && w.readyState === WebSocket.OPEN) { w.send(JSON.stringify(m)); return true } return false }, [])
  return { status, send }
}
