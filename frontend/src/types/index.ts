export type Role = 'OWNER' | 'ADMIN' | 'DISPATCHER' | 'DRIVER'
export type OrderStatus = 'CREATED' | 'DISPATCH_REQUESTED' | 'ASSIGNED' | 'ARRIVED' | 'IN_TRANSIT' | 'DELIVERED' | 'CANCELLED' | 'FAILED'
export type Priority = 'LOW' | 'NORMAL' | 'HIGH' | 'URGENT'
export type VehicleType = 'BIKE' | 'CAR' | 'VAN' | 'TRUCK'
export type DriverStatus = 'AVAILABLE' | 'BUSY' | 'OFFLINE' | 'ON_BREAK'
export interface User { id: string; organizationId: string; organizationName: string; name: string; email: string; role: Role; emailVerified: boolean }
export interface AuthResponse { accessToken: string; expiresIn: number; user: User }
export interface Page<T> { content: T[]; page: number; size: number; totalElements: number; totalPages: number }
export interface OrderItem { name: string; quantity: number }
export interface Order {
  id: string; customerName: string; customerPhone?: string; pickupAddress: string; pickupLat: number; pickupLng: number
  dropAddress: string; dropLat: number; dropLng: number; items: OrderItem[]; priority: Priority; vehicleRequirement?: VehicleType
  scheduledAt?: string; status: OrderStatus; driverId?: string; createdAt: string; updatedAt: string
}
export interface TimelineEntry { from?: OrderStatus; to: OrderStatus; actorUserId?: string; eventId: string; at: string }
export interface OrderDetail { order: Order; timeline: TimelineEntry[] }
export interface Driver { id: string; name: string; phone?: string; status: DriverStatus; vehicleId?: string; rating: number; lastLat?: number; lastLng?: number; hasLogin: boolean }
export interface Vehicle { id: string; registrationNumber: string; type: VehicleType; capacityKg: number; status: 'ACTIVE' | 'MAINTENANCE' | 'INACTIVE'; assignedDriverId?: string }
export interface DayPoint { date: string; created: number; delivered: number }
export interface DashboardSummary {
  totalOrders: number; activeDeliveries: number; availableDrivers: number; completedToday: number; failedToday: number
  avgDeliveryMinutes: number | null; avgAssignmentSeconds: number | null; completionRate: number | null
  ordersByDay: DayPoint[]; driversByStatus: Record<string, number>; ordersByStatus: Record<string, number>
}
export interface HealthComponent { name: string; status: 'UP' | 'DEGRADED' | 'DOWN' | 'DISABLED' }
export interface DriverHome { driver: Driver; vehicle?: Vehicle; deliveries: Order[] }
export interface Place { label: string; lat: number; lng: number }
export interface OrderInput {
  customerName: string; customerPhone?: string; pickupAddress: string; pickupLat: number; pickupLng: number
  dropAddress: string; dropLat: number; dropLng: number; items: OrderItem[]; priority: Priority; vehicleRequirement?: VehicleType; scheduledAt?: string
}

export interface Session { id: string; userAgent?: string; ip?: string; createdAt: string; lastUsedAt: string; current: boolean }
export interface Member { id: string; name: string; email: string; role: Role; enabled: boolean; emailVerified: boolean }
export interface Invitation { id: string; email: string; role: Role; expiresAt: string }
export interface NotificationItem { id: string; type: string; title: string; message: string; orderId?: string; createdAt: string; read: boolean }
export interface NotificationView { unread: number; items: NotificationItem[] }
export interface DispatchSettings { searchRadiusKm: number; maxDriverWorkload: number; weights: { distance: number; availability: number; workload: number; vehicleMatch: number; rating: number }; autoDispatchEnabled: boolean }
export interface LiveDriver { id: string; name: string; status: DriverStatus; vehicleId?: string; vehicleType?: VehicleType; registration?: string; lat?: number; lng?: number; currentOrderId?: string }
export interface LiveOrder { id: string; customerName: string; status: OrderStatus; pickupAddress: string; pickupLat: number; pickupLng: number; dropAddress: string; dropLat: number; dropLng: number; driverId?: string }
export interface LiveSnapshot { drivers: LiveDriver[]; orders: LiveOrder[] }
export interface AnalyticsDay { date: string; created: number; delivered: number; cancelled: number; failed: number }
export interface Analytics { days: number; series: AnalyticsDay[]; completionRate: number | null; cancellationRate: number | null; avgDeliveryMinutes: number | null; avgAssignmentSeconds: number | null
  driverUtilization: number | null; avgOrdersPerDriver: number | null; ordersPerDriver: { driverId: string; name: string; orders: number; delivered: number }[] }
