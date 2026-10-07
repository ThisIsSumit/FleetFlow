import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { RefreshCw, Route } from 'lucide-react'
import { dispatchApi } from '@/api/moreApi'
import { driverApi } from '@/api/fleetApi'
import { orderApi } from '@/api/orderApi'
import { errorMessage } from '@/lib/api'
import { useAuth } from '@/stores/auth'
import { toast } from '@/stores/toast'
import { Button } from '@/components/ui/Button'
import { Badge, Card, OrderStatusBadge, PriorityBadge, Skeleton } from '@/components/ui/Card'
import { EmptyState, ErrorState } from '@/components/ui/States'
import { shortId, timeAgo } from '@/utils/format'

export function Dispatch() {
  const role = useAuth((s) => s.user?.role); const qc = useQueryClient(); const [pick, setPick] = useState<Record<string, string>>({})
  const queue = useQuery({ queryKey: ['orders', 'queue'], queryFn: () => orderApi.list({ status: 'DISPATCH_REQUESTED', size: 50, sort: 'createdAt' }), refetchInterval: 5000 })
  const recent = useQuery({ queryKey: ['orders', 'assigned'], queryFn: () => orderApi.list({ status: 'ASSIGNED', size: 8 }), refetchInterval: 10000 })
  const drivers = useQuery({ queryKey: ['drivers'], queryFn: () => driverApi.list(), refetchInterval: 15000 })
  const settings = useQuery({ queryKey: ['dispatch-settings'], queryFn: dispatchApi.settings, enabled: role === 'OWNER' || role === 'ADMIN' })
  const names = Object.fromEntries((drivers.data?.content ?? []).map((d) => [d.id, d.name])); const free = (drivers.data?.content ?? []).filter((d) => d.status === 'AVAILABLE')
  const refresh = () => { qc.invalidateQueries({ queryKey: ['orders'] }); qc.invalidateQueries({ queryKey: ['drivers'] }); qc.invalidateQueries({ queryKey: ['dashboard'] }) }
  const assign = useMutation({ mutationFn: (a: { o: string; d: string }) => dispatchApi.assign(a.o, a.d), onSuccess: () => { toast.success('Driver assigned'); refresh() }, onError: (e) => toast.error(errorMessage(e)) })
  const redo = useMutation({ mutationFn: dispatchApi.redispatch, onSuccess: () => toast.success('Dispatch re-requested'), onError: (e) => toast.error(errorMessage(e)) })
  return (<div className="space-y-5">
    <div><h1 className="text-2xl font-semibold tracking-tight">Dispatch</h1><p className="text-sm text-muted-foreground">Orders waiting for a driver. Auto-dispatch retries these every ~30 seconds; you can also step in.</p></div>
    {settings.data && <Card className="flex flex-wrap items-center gap-x-6 gap-y-2 p-4 text-sm"><Badge tone={settings.data.autoDispatchEnabled ? 'success' : 'warning'}>{settings.data.autoDispatchEnabled ? 'Auto-dispatch on' : 'Auto-dispatch off'}</Badge>
      <span>Search radius <b>{settings.data.searchRadiusKm} km</b></span><span>Max workload <b>{settings.data.maxDriverWorkload}</b></span><Link to="/settings" className="ml-auto text-primary hover:underline">Edit rules</Link></Card>}
    <Card><div className="flex items-center justify-between border-b p-4"><h2 className="font-medium">Awaiting dispatch</h2><span className="text-sm text-muted-foreground">{free.length} driver{free.length === 1 ? '' : 's'} available</span></div>
      {queue.isLoading ? <div className="space-y-2 p-4">{[0, 1, 2].map((i) => <Skeleton key={i} className="h-10" />)}</div> : queue.isError ? <ErrorState message={errorMessage(queue.error)} onRetry={() => queue.refetch()} /> :
        queue.data!.content.length === 0 ? <EmptyState icon={<Route className="h-5 w-5" />} title="Queue is clear" description="Every order has a driver or is waiting to be created." /> :
        <div className="overflow-x-auto"><table className="w-full text-left text-sm"><thead className="border-b text-xs uppercase text-muted-foreground"><tr>{['Order', 'Customer', 'Route', 'Priority', 'Waiting', 'Actions'].map((h) => <th key={h} className="px-4 py-3 font-medium">{h}</th>)}</tr></thead>
          <tbody className="divide-y">{queue.data!.content.map((o) => (<tr key={o.id}><td className="px-4 py-3"><Link className="font-mono text-xs text-primary hover:underline" to={`/orders/${o.id}`}>{shortId(o.id)}</Link></td><td className="px-4 py-3 font-medium">{o.customerName}</td>
            <td className="max-w-[16rem] truncate px-4 py-3 text-muted-foreground">{o.pickupAddress} → {o.dropAddress}</td><td className="px-4 py-3"><PriorityBadge priority={o.priority} /></td><td className="whitespace-nowrap px-4 py-3 text-muted-foreground">{timeAgo(o.updatedAt)}</td>
            <td className="px-4 py-3"><div className="flex items-center gap-2">
              <select aria-label="Choose driver" className="h-8 rounded-md border bg-card px-2 text-xs" value={pick[o.id] ?? ''} onChange={(e) => setPick({ ...pick, [o.id]: e.target.value })}><option value="">Choose driver…</option>{free.map((d) => <option key={d.id} value={d.id}>{d.name}</option>)}</select>
              <Button size="sm" disabled={!pick[o.id]} loading={assign.isPending && assign.variables?.o === o.id} onClick={() => assign.mutate({ o: o.id, d: pick[o.id] })}>Assign</Button>
              <Button size="sm" variant="secondary" aria-label="Retry auto-dispatch" title="Retry auto-dispatch" loading={redo.isPending && redo.variables === o.id} onClick={() => redo.mutate(o.id)}><RefreshCw className="h-3.5 w-3.5" /></Button></div></td></tr>))}</tbody></table></div>}
    </Card>
    <Card><div className="border-b p-4"><h2 className="font-medium">Recently assigned</h2></div>
      {recent.data && recent.data.content.length === 0 ? <EmptyState title="No assignments yet" /> : <ul className="divide-y">{recent.data?.content.map((o) => (<li key={o.id}><Link to={`/orders/${o.id}`} className="flex items-center gap-3 px-4 py-3 text-sm hover:bg-muted/60"><span className="min-w-0 flex-1 truncate font-medium">{o.customerName}</span><span className="text-muted-foreground">{o.driverId ? names[o.driverId] ?? 'Driver' : ''}</span><OrderStatusBadge status={o.status} /></Link></li>))}</ul>}
    </Card></div>)
}
