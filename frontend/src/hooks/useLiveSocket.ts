import { useCallback, useEffect, useRef, useState } from 'react'
import { useAuth } from '@/stores/auth'
export interface LiveMessage { type: string; driverId?: string; latitude?: number; longitude?: number; code?: string }
/** Native WebSocket with exponential-backoff reconnect. The access token rides in the query string because browsers cannot set WS headers;
 *  it is re-established whenever the token is refreshed. */
export function useLiveSocket(onMessage: (m: LiveMessage) => void) {
  const token = useAuth((s) => s.accessToken); const cb = useRef(onMessage); cb.current = onMessage
  const [status, setStatus] = useState<'connecting' | 'open' | 'closed'>('connecting'); const wsRef = useRef<WebSocket | null>(null)
  useEffect(() => {
    if (!token) return
    let ws: WebSocket | null = null, retry = 0, stop = false, timer = 0
    const connect = () => {
      ws = new WebSocket(`${location.protocol === 'https:' ? 'wss' : 'ws'}://${location.host}/ws/live?token=${encodeURIComponent(token)}`)
      wsRef.current = ws
      ws.onopen = () => { retry = 0; setStatus('open') }
      ws.onmessage = (e) => { try { cb.current(JSON.parse(e.data)) } catch { /* ignore malformed frames */ } }
      ws.onclose = () => { setStatus('closed'); if (!stop) timer = window.setTimeout(connect, Math.min(15000, 1000 * 2 ** retry++)) }
    }
    connect()
    return () => { stop = true; clearTimeout(timer); ws?.close() }
  }, [token])
  /** Returns false if the socket is not open, so callers can fall back to REST. */
  const send = useCallback((m: object) => { const w = wsRef.current; if (w && w.readyState === WebSocket.OPEN) { w.send(JSON.stringify(m)); return true } return false }, [])
  return { status, send }
}
