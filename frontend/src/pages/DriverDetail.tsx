import { Link, useParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { ArrowLeft } from 'lucide-react'
import { driverApi, vehicleApi } from '@/api/fleetApi'
import { liveApi } from '@/api/moreApi'
import { orderApi } from '@/api/orderApi'
import { errorMessage } from '@/lib/api'
import { Card, DriverStatusBadge, OrderStatusBadge, Skeleton } from '@/components/ui/Card'
import { EmptyState, ErrorState } from '@/components/ui/States'
import { fmtDateTime, shortId } from '@/utils/format'

export function DriverDetail() {
  const { id = '' } = useParams()
  const drivers = useQuery({ queryKey: ['drivers'], queryFn: () => driverApi.list() })
  const vehicles = useQuery({ queryKey: ['vehicles'], queryFn: () => vehicleApi.list() })
  const history = useQuery({ queryKey: ['orders', 'driver', id], queryFn: () => orderApi.list({ driverId: id, size: 100 }), refetchInterval: 15000 })
  const live = useQuery({ queryKey: ['live'], queryFn: liveApi.snapshot, refetchInterval: 15000, retry: false })
  if (drivers.isLoading) return <Skeleton className="h-64" />
  const d = drivers.data?.content.find((x) => x.id === id)
  if (drivers.isError) return <Card><ErrorState message={errorMessage(drivers.error)} onRetry={() => drivers.refetch()} /></Card>
  if (!d) return <Card><ErrorState message="This driver does not exist or you do not have access to it." /></Card>
  const v = vehicles.data?.content.find((x) => x.id === d.vehicleId); const orders = history.data?.content ?? []; const pos = live.data?.drivers.find((x) => x.id === id)
  const delivered = orders.filter((o) => o.status === 'DELIVERED').length, closed = orders.filter((o) => ['DELIVERED', 'FAILED', 'CANCELLED'].includes(o.status)).length
  const today = orders.filter((o) => o.status === 'DELIVERED' && new Date(o.updatedAt).toDateString() === new Date().toDateString()).length
  const current = orders.find((o) => ['ASSIGNED', 'ARRIVED', 'IN_TRANSIT'].includes(o.status))
  const Row = ({ k, children }: { k: string; children: React.ReactNode }) => <div className="flex justify-between gap-4 py-2 text-sm"><dt className="text-muted-foreground">{k}</dt><dd>{children}</dd></div>
  return (<div className="space-y-5">
    <Link to="/drivers" className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground"><ArrowLeft className="h-4 w-4" />Drivers</Link>
    <div className="flex items-center gap-3"><h1 className="text-2xl font-semibold tracking-tight">{d.name}</h1><DriverStatusBadge status={d.status} /></div>
    <div className="grid gap-4 lg:grid-cols-3">
      <Card className="p-4"><h2 className="mb-1 font-medium">Profile</h2><dl className="divide-y"><Row k="Phone">{d.phone ?? '—'}</Row><Row k="Rating">{Number(d.rating).toFixed(2)}</Row><Row k="Vehicle">{v ? `${v.registrationNumber} · ${v.type} · ${v.capacityKg} kg` : 'None'}</Row>
        <Row k="Location">{pos?.lat != null ? `${pos.lat.toFixed(4)}, ${pos.lng!.toFixed(4)}` : 'Unknown'}</Row></dl></Card>
      <Card className="p-4"><h2 className="mb-1 font-medium">Performance <span className="text-xs font-normal text-muted-foreground">last 100 orders</span></h2><dl className="divide-y">
        <Row k="Delivered today">{today}</Row><Row k="Total delivered">{delivered}</Row><Row k="Completion rate">{closed === 0 ? '—' : `${((delivered / closed) * 100).toFixed(1)}%`}</Row></dl></Card>
      <Card className="p-4"><h2 className="mb-2 font-medium">Current assignment</h2>{current ? (<Link to={`/orders/${current.id}`} className="block rounded-lg border p-3 text-sm hover:bg-muted/60"><p className="font-medium">{current.customerName}</p><p className="text-xs text-muted-foreground">{current.pickupAddress} → {current.dropAddress}</p><div className="mt-2"><OrderStatusBadge status={current.status} /></div></Link>) : <p className="text-sm text-muted-foreground">No active order.</p>}</Card>
    </div>
    <Card><div className="border-b p-4"><h2 className="font-medium">Delivery history</h2></div>
      {history.isLoading ? <Skeleton className="m-4 h-24" /> : orders.length === 0 ? <EmptyState title="No deliveries yet" /> : <ul className="divide-y">{orders.slice(0, 20).map((o) => (<li key={o.id}><Link to={`/orders/${o.id}`} className="flex items-center gap-3 px-4 py-3 text-sm hover:bg-muted/60"><span className="w-20 font-mono text-xs text-primary">{shortId(o.id)}</span><span className="min-w-0 flex-1 truncate">{o.customerName}</span><span className="hidden text-xs text-muted-foreground sm:block">{fmtDateTime(o.createdAt)}</span><OrderStatusBadge status={o.status} /></Link></li>))}</ul>}
    </Card></div>)
}
