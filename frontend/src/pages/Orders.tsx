import { useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { Package, Plus, Search } from 'lucide-react'
import { orderApi } from '@/api/orderApi'
import { driverApi } from '@/api/fleetApi'
import { errorMessage } from '@/lib/api'
import { Button } from '@/components/ui/Button'
import { Input, Select } from '@/components/ui/Field'
import { Card, OrderStatusBadge, PriorityBadge, Skeleton } from '@/components/ui/Card'
import { EmptyState, ErrorState } from '@/components/ui/States'
import { Pagination } from '@/components/ui/Pagination'
import { DatePicker } from '@/components/ui/Extras'
import { fmtDateTime, shortId, titleCase } from '@/utils/format'
import { CreateOrderModal } from './CreateOrderModal'
import type { OrderStatus, Priority } from '@/types'

const STATUSES: OrderStatus[] = ['CREATED', 'DISPATCH_REQUESTED', 'ASSIGNED', 'ARRIVED', 'IN_TRANSIT', 'DELIVERED', 'CANCELLED', 'FAILED']
export function Orders() {
  const [page, setPage] = useState(0); const [q, setQ] = useState(''); const [status, setStatus] = useState<OrderStatus | ''>(''); const [priority, setPriority] = useState<Priority | ''>('')
  const [sort, setSort] = useState('createdAt'); const [params, setParams] = useSearchParams(); const [open, setOpen] = useState(params.get('new') === '1')
  const [from, setFrom] = useState(''); const [to, setTo] = useState('')
  const closeModal = () => { setOpen(false); if (params.has('new')) { params.delete('new'); setParams(params, { replace: true }) } }
  const iso = (d: string, plusDay = 0) => (d ? new Date(new Date(d + 'T00:00:00Z').getTime() + plusDay * 86400000).toISOString() : '')
  const orders = useQuery({ queryKey: ['orders', { page, q, status, priority, sort, from, to }], queryFn: () => orderApi.list({ page, q, status, priority, sort, size: 15, from: iso(from), to: iso(to, 1) }), placeholderData: keepPreviousData, refetchInterval: 15000 })
  const drivers = useQuery({ queryKey: ['drivers'], queryFn: () => driverApi.list() })
  const names = Object.fromEntries((drivers.data?.content ?? []).map((d) => [d.id, d.name]))
  const reset = <T,>(set: (v: T) => void) => (v: T) => { set(v); setPage(0) }
  const filtered = !!(q || status || priority || from || to)
  return (<div className="space-y-4">
    <div className="flex flex-wrap items-center justify-between gap-3"><div><h1 className="text-2xl font-semibold tracking-tight">Orders</h1><p className="text-sm text-muted-foreground">Create, track and manage deliveries.</p></div>
      <Button onClick={() => setOpen(true)}><Plus className="h-4 w-4" />Create order</Button></div>
    <Card><div className="grid gap-3 border-b p-4 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-6">
      <div className="relative"><Search className="pointer-events-none absolute left-3 top-[2.35rem] h-4 w-4 text-muted-foreground" /><Input label="Search customer" className="pl-9" value={q} onChange={(e) => reset(setQ)(e.target.value)} placeholder="Name…" /></div>
      <Select label="Status" value={status} onChange={(e) => reset(setStatus)(e.target.value as OrderStatus | '')}><option value="">All statuses</option>{STATUSES.map((s) => <option key={s} value={s}>{titleCase(s)}</option>)}</Select>
      <Select label="Priority" value={priority} onChange={(e) => reset(setPriority)(e.target.value as Priority | '')}><option value="">All priorities</option>{['LOW', 'NORMAL', 'HIGH', 'URGENT'].map((s) => <option key={s} value={s}>{titleCase(s)}</option>)}</Select>
      <Select label="Sort by" value={sort} onChange={(e) => reset(setSort)(e.target.value)}><option value="createdAt">Newest</option><option value="priority">Priority</option><option value="status">Status</option><option value="customerName">Customer</option></Select>
      <DatePicker label="From" value={from} onChange={reset(setFrom)} /><DatePicker label="To" value={to} onChange={reset(setTo)} /></div>
    {orders.isLoading ? <div className="space-y-3 p-4">{[0, 1, 2, 3, 4].map((i) => <Skeleton key={i} className="h-10" />)}</div> :
      orders.isError ? <ErrorState message={errorMessage(orders.error)} onRetry={() => orders.refetch()} /> :
      orders.data!.content.length === 0 ? <EmptyState icon={<Package className="h-5 w-5" />} title={filtered ? 'No orders match your filters' : 'No orders yet'} description={filtered ? 'Try clearing a filter.' : 'Create your first order to see automatic dispatch in action.'} action={!filtered && <Button onClick={() => setOpen(true)}>Create order</Button>} /> :
      <div className="overflow-x-auto"><table className="w-full text-left text-sm"><thead className="border-b text-xs uppercase text-muted-foreground"><tr>
        {['Order', 'Customer', 'Pickup', 'Drop', 'Priority', 'Driver', 'Status', 'Created'].map((h) => <th key={h} className="whitespace-nowrap px-4 py-3 font-medium">{h}</th>)}</tr></thead>
        <tbody className="divide-y">{orders.data!.content.map((o) => (<tr key={o.id} className="hover:bg-muted/50">
          <td className="px-4 py-3"><Link to={`/orders/${o.id}`} className="font-mono text-xs font-medium text-primary hover:underline">{shortId(o.id)}</Link></td>
          <td className="px-4 py-3 font-medium">{o.customerName}</td><td className="max-w-[12rem] truncate px-4 py-3 text-muted-foreground">{o.pickupAddress}</td><td className="max-w-[12rem] truncate px-4 py-3 text-muted-foreground">{o.dropAddress}</td>
          <td className="px-4 py-3"><PriorityBadge priority={o.priority} /></td><td className="whitespace-nowrap px-4 py-3">{o.driverId ? names[o.driverId] ?? 'Driver' : <span className="text-muted-foreground">Unassigned</span>}</td>
          <td className="px-4 py-3"><OrderStatusBadge status={o.status} /></td><td className="whitespace-nowrap px-4 py-3 text-muted-foreground">{fmtDateTime(o.createdAt)}</td></tr>))}</tbody></table></div>}
    {orders.data && orders.data.content.length > 0 && <Pagination page={orders.data.page} totalPages={orders.data.totalPages} total={orders.data.totalElements} onPage={setPage} />}
    </Card>
    <CreateOrderModal open={open} onClose={closeModal} />
  </div>)
}
