import axios, { AxiosError, InternalAxiosRequestConfig } from 'axios'
import { useAuth } from '@/stores/auth'
import type { AuthResponse } from '@/types'

export const api = axios.create({ baseURL: '/api/v1', withCredentials: true })
api.interceptors.request.use((cfg) => { const t = useAuth.getState().accessToken; if (t) cfg.headers.Authorization = `Bearer ${t}`; return cfg })

let inflight: Promise<string> | null = null
/** Single-flight: concurrent 401s share one refresh call. Required because the backend rotates the token on every refresh
 *  and treats a replayed token as theft (it revokes all sessions). */
export function refreshAccessToken(): Promise<string> {
  inflight ??= axios.post<AuthResponse>('/api/v1/auth/refresh', null, { withCredentials: true })
    .then((r) => { useAuth.getState().setSession(r.data); return r.data.accessToken })
    .finally(() => { inflight = null })
  return inflight
}

const NO_REFRESH = /^\/auth\/(login|signup|refresh)/
api.interceptors.response.use((r) => r, async (error: AxiosError) => {
  const original = error.config as (InternalAxiosRequestConfig & { _retry?: boolean }) | undefined
  if (error.response?.status === 401 && original && !original._retry && !NO_REFRESH.test(original.url ?? '')) {
    original._retry = true
    try { const t = await refreshAccessToken(); original.headers.Authorization = `Bearer ${t}`; return api(original) }
    catch { useAuth.getState().clear(); window.location.assign('/login'); return new Promise(() => {}) }
  }
  return Promise.reject(error)
})

export function errorMessage(e: unknown): string {
  const ax = e as AxiosError<{ message?: string; details?: string[] }>
  const d = ax.response?.data
  if (d?.details?.length) return `${d.message ?? 'Error'}: ${d.details.join(', ')}`
  return d?.message ?? (ax.response ? `Request failed (${ax.response.status})` : 'Cannot reach the server')
}
