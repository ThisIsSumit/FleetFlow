import { Link, useParams } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ArrowLeft } from 'lucide-react'
import { orderApi } from '@/api/orderApi'
import { driverApi } from '@/api/fleetApi'
import { errorMessage } from '@/lib/api'
import { Button } from '@/components/ui/Button'
import { Card, OrderStatusBadge, PriorityBadge, Skeleton } from '@/components/ui/Card'
import { ErrorState } from '@/components/ui/States'
import { toast } from '@/stores/toast'
import { fmtDateTime, shortId, titleCase } from '@/utils/format'
import type { OrderStatus } from '@/types'

// Mirrors the backend's manually-allowed transitions for button visibility only; the server is authoritative.
const NEXT: Partial<Record<OrderStatus, OrderStatus[]>> = { ASSIGNED: ['ARRIVED', 'CANCELLED'], ARRIVED: ['IN_TRANSIT', 'CANCELLED'], IN_TRANSIT: ['DELIVERED', 'FAILED'], CREATED: ['CANCELLED'], DISPATCH_REQUESTED: ['CANCELLED'] }
const Row = ({ k, children }: { k: string; children: React.ReactNode }) => (<div className="flex justify-between gap-4 py-2 text-sm"><dt className="text-muted-foreground">{k}</dt><dd className="text-right">{children}</dd></div>)

export function OrderDetail() {
  const { id = '' } = useParams(); const qc = useQueryClient()
  const q = useQuery({ queryKey: ['orders', id], queryFn: () => orderApi.get(id), refetchInterval: 10000 })
  const drivers = useQuery({ queryKey: ['drivers'], queryFn: () => driverApi.list() })
  const move = useMutation({ mutationFn: (s: OrderStatus) => orderApi.setStatus(id, s),
    onSuccess: () => { toast.success('Status updated'); qc.invalidateQueries({ queryKey: ['orders'] }); qc.invalidateQueries({ queryKey: ['dashboard'] }); qc.invalidateQueries({ queryKey: ['drivers'] }) }, onError: (e) => toast.error(errorMessage(e)) })
  if (q.isLoading) return <div className="space-y-4"><Skeleton className="h-10 w-64" /><Skeleton className="h-64" /></div>
  if (q.isError) return <Card><ErrorState message={q.error && (q.error as { response?: { status: number } }).response?.status === 404 ? 'This order does not exist or you do not have access to it.' : errorMessage(q.error)} onRetry={() => q.refetch()} /></Card>
  const { order: o, timeline } = q.data!; const driver = drivers.data?.content.find((d) => d.id === o.driverId)
  return (<div className="space-y-5">
    <Link to="/orders" className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground"><ArrowLeft className="h-4 w-4" />Orders</Link>
    <div className="flex flex-wrap items-center justify-between gap-3"><div className="flex items-center gap-3"><h1 className="text-2xl font-semibold tracking-tight">Order <span className="font-mono">{shortId(o.id)}</span></h1><OrderStatusBadge status={o.status} /><PriorityBadge priority={o.priority} /></div>
      <div className="flex gap-2">{(NEXT[o.status] ?? []).map((s) => <Button key={s} size="sm" variant={s === 'CANCELLED' || s === 'FAILED' ? 'secondary' : 'primary'} loading={move.isPending && move.variables === s} onClick={() => move.mutate(s)}>{s === 'CANCELLED' ? 'Cancel order' : `Mark ${titleCase(s).toLowerCase()}`}</Button>)}</div></div>
    <div className="grid gap-4 lg:grid-cols-3">
      <Card className="p-4 lg:col-span-2"><h2 className="mb-2 font-medium">Details</h2><dl className="divide-y">
        <Row k="Customer">{o.customerName}{o.customerPhone && <span className="text-muted-foreground"> · {o.customerPhone}</span>}</Row>
        <Row k="Pickup">{o.pickupAddress}<br /><span className="text-xs text-muted-foreground">{o.pickupLat.toFixed(4)}, {o.pickupLng.toFixed(4)}</span></Row>
        <Row k="Drop">{o.dropAddress}<br /><span className="text-xs text-muted-foreground">{o.dropLat.toFixed(4)}, {o.dropLng.toFixed(4)}</span></Row>
        <Row k="Items">{o.items.map((i) => `${i.quantity}× ${i.name}`).join(', ')}</Row><Row k="Vehicle requirement">{o.vehicleRequirement ? titleCase(o.vehicleRequirement) : 'Any'}</Row>
        <Row k="Driver">{driver ? driver.name : o.driverId ? 'Assigned' : <span className="text-muted-foreground">Not assigned yet</span>}</Row>
        <Row k="Scheduled">{fmtDateTime(o.scheduledAt)}</Row><Row k="Created">{fmtDateTime(o.createdAt)}</Row></dl></Card>
      <Card className="p-4"><h2 className="mb-3 font-medium">Timeline</h2>
        <ol className="relative space-y-4 border-l pl-5">{timeline.map((t) => (<li key={t.eventId + t.to} className="relative"><span className="absolute -left-[1.6rem] top-1 h-2.5 w-2.5 rounded-full bg-primary" />
          <p className="text-sm font-medium">{titleCase(t.to)}</p><p className="text-xs text-muted-foreground">{fmtDateTime(t.at)}</p>
          <p className="text-xs text-muted-foreground">Actor: {t.actorUserId ? shortId(t.actorUserId) : 'System'} · Event <span className="font-mono">{shortId(t.eventId)}</span></p></li>))}</ol></Card>
    </div></div>)
}
