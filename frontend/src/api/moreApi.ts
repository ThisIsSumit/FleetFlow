import { api } from '@/lib/api'
import type { Analytics, DispatchSettings, DriverHome, DriverStatus, Invitation, LiveSnapshot, Member, NotificationView, Order, OrderStatus, Place, Role } from '@/types'
export const notificationApi = {
  list: (limit = 30) => api.get<NotificationView>('/notifications', { params: { limit } }).then((r) => r.data),
  read: (id: string) => api.post(`/notifications/${id}/read`), readAll: () => api.post('/notifications/read-all'),
}
export const liveApi = { snapshot: () => api.get<LiveSnapshot>('/live/snapshot').then((r) => r.data) }
export const analyticsApi = { get: (days: number) => api.get<Analytics>('/analytics', { params: { days } }).then((r) => r.data) }
export const teamApi = {
  members: () => api.get<Member[]>('/team').then((r) => r.data), invitations: () => api.get<Invitation[]>('/team/invitations').then((r) => r.data),
  invite: (email: string, role: Role, driverId?: string) => api.post('/team/invitations', { email, role, driverId: driverId || undefined }),
  setRole: (id: string, role: Role) => api.patch(`/team/${id}/role`, { role }), setEnabled: (id: string, enabled: boolean) => api.patch(`/team/${id}/enabled`, { enabled }),
  remove: (id: string) => api.delete(`/team/${id}`),
}
export const organizationApi = { get: () => api.get<{ id: string; name: string }>('/organization').then((r) => r.data), rename: (name: string) => api.put('/organization', { name }) }
export const dispatchApi = {
  settings: () => api.get<DispatchSettings>('/settings/dispatch').then((r) => r.data), saveSettings: (b: DispatchSettings) => api.put<DispatchSettings>('/settings/dispatch', b).then((r) => r.data),
  assign: (orderId: string, driverId: string) => api.post(`/dispatch/orders/${orderId}/assign`, { driverId }), redispatch: (orderId: string) => api.post(`/dispatch/orders/${orderId}/redispatch`),
}
export const geocodeApi = { search: (q: string) => api.get<Place[]>('/geocode', { params: { q } }).then((r) => r.data) }
export const driverPortalApi = {
  me: () => api.get<DriverHome>('/driver/me').then((r) => r.data),
  setStatus: (status: DriverStatus) => api.patch('/driver/status', { status }),
  setOrderStatus: (id: string, status: OrderStatus) => api.post<Order>(`/driver/orders/${id}/status`, { status }).then((r) => r.data),
  sendLocation: (latitude: number, longitude: number) => api.post('/driver/location', { latitude, longitude }),
}
