import { api } from '@/lib/api'
import type { AuthResponse, Session } from '@/types'
export const authApi = {
  login: (b: { email: string; password: string }) => api.post<AuthResponse>('/auth/login', b).then((r) => r.data),
  signup: (b: { name: string; email: string; password: string; organizationName: string }) => api.post<AuthResponse>('/auth/signup', b).then((r) => r.data),
  logout: () => api.post('/auth/logout'),
  logoutAll: () => api.post('/auth/logout-all'),
  forgotPassword: (email: string) => api.post('/auth/forgot-password', { email }),
  resetPassword: (token: string, newPassword: string) => api.post('/auth/reset-password', { token, newPassword }),
  verifyEmail: (token: string) => api.post('/auth/verify-email', { token }),
  resendVerification: () => api.post('/auth/resend-verification'),
  acceptInvitation: (b: { token: string; name: string; password: string }) => api.post<AuthResponse>('/auth/accept-invitation', b).then((r) => r.data),
  sessions: () => api.get<Session[]>('/auth/sessions').then((r) => r.data),
  revokeSession: (id: string) => api.delete(`/auth/sessions/${id}`),
}
