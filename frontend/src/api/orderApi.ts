import { api } from '@/lib/api'
import type { Order, OrderDetail, OrderInput, OrderStatus, Page, Priority } from '@/types'
export interface OrderQuery { page?: number; size?: number; status?: OrderStatus | ''; priority?: Priority | ''; q?: string; sort?: string; driverId?: string; from?: string; to?: string }
const clean = (o: object) => Object.fromEntries(Object.entries(o).filter(([, v]) => v !== '' && v != null))
export const orderApi = {
  list: (q: OrderQuery) => api.get<Page<Order>>('/orders', { params: clean(q) }).then((r) => r.data),
  get: (id: string) => api.get<OrderDetail>(`/orders/${id}`).then((r) => r.data),
  create: (b: OrderInput) => api.post<Order>('/orders', b).then((r) => r.data),
  setStatus: (id: string, status: OrderStatus) => api.post<Order>(`/orders/${id}/status`, { status }).then((r) => r.data),
}
