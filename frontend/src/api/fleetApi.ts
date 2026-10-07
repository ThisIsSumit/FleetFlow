import { api } from '@/lib/api'
import type { DashboardSummary, Driver, DriverStatus, HealthComponent, Page, Vehicle, VehicleType } from '@/types'
export const driverApi = {
  list: (size = 100) => api.get<Page<Driver>>('/drivers', { params: { size } }).then((r) => r.data),
  create: (b: { name: string; phone?: string; vehicleId?: string }) => api.post<Driver>('/drivers', b).then((r) => r.data),
  setStatus: (id: string, status: DriverStatus) => api.patch<Driver>(`/drivers/${id}/status`, { status }).then((r) => r.data),
  sendLocation: (id: string, latitude: number, longitude: number) => api.post(`/drivers/${id}/location`, { latitude, longitude }),
}
export const vehicleApi = {
  list: (size = 100) => api.get<Page<Vehicle>>('/vehicles', { params: { size } }).then((r) => r.data),
  create: (b: { registrationNumber: string; type: VehicleType; capacityKg: number }) => api.post<Vehicle>('/vehicles', b).then((r) => r.data),
}
export const dashboardApi = {
  summary: () => api.get<DashboardSummary>('/dashboard/summary').then((r) => r.data),
  health: () => api.get<HealthComponent[]>('/system/health').then((r) => r.data),
}
