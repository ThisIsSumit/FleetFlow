import type { HTMLAttributes } from 'react'
import { cn } from '@/lib/cn'
export const Card = ({ className, ...p }: HTMLAttributes<HTMLDivElement>) => <div className={cn('rounded-2xl border bg-card shadow-sm shadow-black/20', className)} {...p} />
export const Skeleton = ({ className }: { className?: string }) => <div aria-hidden className={cn('animate-pulse rounded-lg bg-muted', className)} />
const tones = {
  neutral: 'bg-muted text-muted-foreground', primary: 'bg-primary/10 text-primary', success: 'bg-success/10 text-success',
  warning: 'bg-warning/10 text-warning', danger: 'bg-danger/10 text-danger',
} as const
export type Tone = keyof typeof tones
export const Badge = ({ tone = 'neutral', className, ...p }: HTMLAttributes<HTMLSpanElement> & { tone?: Tone }) =>
  <span className={cn('inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium', tones[tone], className)} {...p} />
const orderTone: Record<string, Tone> = { CREATED: 'neutral', DISPATCH_REQUESTED: 'warning', ASSIGNED: 'primary', ARRIVED: 'primary', IN_TRANSIT: 'primary', DELIVERED: 'success', CANCELLED: 'neutral', FAILED: 'danger' }
const driverTone: Record<string, Tone> = { AVAILABLE: 'success', BUSY: 'warning', OFFLINE: 'neutral', ON_BREAK: 'primary' }
const prioTone: Record<string, Tone> = { LOW: 'neutral', NORMAL: 'neutral', HIGH: 'warning', URGENT: 'danger' }
const label = (s: string) => s.toLowerCase().replace(/_/g, ' ').replace(/^\w/, (c) => c.toUpperCase())
export const OrderStatusBadge = ({ status }: { status: string }) => <Badge tone={orderTone[status] ?? 'neutral'}>{label(status)}</Badge>
export const DriverStatusBadge = ({ status }: { status: string }) => <Badge tone={driverTone[status] ?? 'neutral'}>{label(status)}</Badge>
export const PriorityBadge = ({ priority }: { priority: string }) => <Badge tone={prioTone[priority] ?? 'neutral'}>{label(priority)}</Badge>
