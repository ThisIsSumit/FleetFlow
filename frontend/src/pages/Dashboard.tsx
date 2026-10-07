import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { CartesianGrid, Legend, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis, Bar, BarChart } from 'recharts'
import { dashboardApi, driverApi } from '@/api/fleetApi'
import { orderApi } from '@/api/orderApi'
import { errorMessage } from '@/lib/api'
import { useAuth } from '@/stores/auth'
import { Badge, Card, OrderStatusBadge, Skeleton } from '@/components/ui/Card'
import { EmptyState, ErrorState } from '@/components/ui/States'
import { timeAgo, titleCase } from '@/utils/format'

const greeting = () => { const h = new Date().getHours(); return h < 12 ? 'Good morning' : h < 18 ? 'Good afternoon' : 'Good evening' }
const dash = (v: number | null, f: (n: number) => string) => (v == null ? '—' : f(v))

function Stat({ label, value, loading }: { label: string; value: string | number; loading: boolean }) {
  return (<Card className="p-4"><p className="text-sm text-muted-foreground">{label}</p>
    {loading ? <Skeleton className="mt-2 h-8 w-20" /> : <p className="mt-1 text-2xl font-semibold tabular-nums">{value}</p>}</Card>)
}
const healthTone = { UP: 'success', DEGRADED: 'warning', DOWN: 'danger', DISABLED: 'neutral' } as const
const healthLabel = { UP: 'Operational', DEGRADED: 'Degraded', DOWN: 'Down', DISABLED: 'Not configured' } as const

export function Dashboard() {
  const user = useAuth((s) => s.user)
  const sum = useQuery({ queryKey: ['dashboard'], queryFn: dashboardApi.summary, refetchInterval: 15000 })
  const health = useQuery({ queryKey: ['health'], queryFn: dashboardApi.health, refetchInterval: 30000 })
  const recent = useQuery({ queryKey: ['orders', 'recent'], queryFn: () => orderApi.list({ size: 6 }), refetchInterval: 15000 })
  const drivers = useQuery({ queryKey: ['drivers'], queryFn: () => driverApi.list() })
  const s = sum.data; const loading = sum.isLoading
  const names = Object.fromEntries((drivers.data?.content ?? []).map((d) => [d.id, d.name]))
  const chart = (s?.ordersByDay ?? []).map((d) => ({ ...d, day: new Date(d.date + 'T00:00:00').toLocaleDateString(undefined, { weekday: 'short' }) }))
  const driverBars = Object.entries(s?.driversByStatus ?? {}).map(([k, v]) => ({ status: titleCase(k), drivers: v }))
  return (<div className="space-y-6">
    <div><h1 className="text-2xl font-semibold tracking-tight">{greeting()}, {user?.name.split(' ')[0]}</h1><p className="text-sm text-muted-foreground">Here's what's happening with your fleet today.</p></div>
    {sum.isError && <Card><ErrorState message={errorMessage(sum.error)} onRetry={() => sum.refetch()} /></Card>}
    <div className="grid grid-cols-2 gap-3 md:grid-cols-3 xl:grid-cols-6">
      <Stat label="Total orders" value={s?.totalOrders ?? 0} loading={loading} /><Stat label="Active deliveries" value={s?.activeDeliveries ?? 0} loading={loading} />
      <Stat label="Available drivers" value={s?.availableDrivers ?? 0} loading={loading} /><Stat label="Completed today" value={s?.completedToday ?? 0} loading={loading} />
      <Stat label="Avg delivery time" value={dash(s?.avgDeliveryMinutes ?? null, (n) => `${Math.round(n)} min`)} loading={loading} />
      <Stat label="Failed today" value={s?.failedToday ?? 0} loading={loading} />
    </div>
    <div className="grid gap-4 lg:grid-cols-3">
      <Card className="p-4 lg:col-span-2"><h2 className="mb-3 font-medium">Orders overview <span className="text-xs font-normal text-muted-foreground">last 7 days (UTC)</span></h2>
        {loading ? <Skeleton className="h-64" /> : s && s.totalOrders === 0 ? <EmptyState title="No orders yet" description="Create your first order and it will show up here." action={<Link to="/orders" className="text-sm font-medium text-primary hover:underline">Go to orders</Link>} /> :
          <div className="h-64"><ResponsiveContainer><LineChart data={chart}><CartesianGrid strokeDasharray="3 3" stroke="hsl(var(--border))" /><XAxis dataKey="day" fontSize={12} /><YAxis allowDecimals={false} fontSize={12} width={28} />
            <Tooltip contentStyle={{ background: 'hsl(var(--card))', border: '1px solid hsl(var(--border))', borderRadius: 8 }} /><Legend />
            <Line type="monotone" dataKey="created" name="Created" stroke="hsl(var(--primary))" strokeWidth={2} dot={false} /><Line type="monotone" dataKey="delivered" name="Delivered" stroke="hsl(var(--success))" strokeWidth={2} dot={false} /></LineChart></ResponsiveContainer></div>}
      </Card>
      <Card className="p-4"><h2 className="mb-3 font-medium">Live fleet</h2>
        {loading ? <Skeleton className="h-64" /> : driverBars.length === 0 ? <EmptyState title="No drivers yet" description="Add drivers to see fleet utilisation." /> :
          <div className="h-64"><ResponsiveContainer><BarChart data={driverBars}><CartesianGrid strokeDasharray="3 3" stroke="hsl(var(--border))" /><XAxis dataKey="status" fontSize={12} /><YAxis allowDecimals={false} fontSize={12} width={28} />
            <Tooltip contentStyle={{ background: 'hsl(var(--card))', border: '1px solid hsl(var(--border))', borderRadius: 8 }} /><Bar dataKey="drivers" fill="hsl(var(--primary))" radius={[4, 4, 0, 0]} /></BarChart></ResponsiveContainer></div>}
      </Card>
    </div>
    <div className="grid gap-4 lg:grid-cols-3">
      <Card className="lg:col-span-2"><div className="flex items-center justify-between border-b p-4"><h2 className="font-medium">Recent deliveries</h2><Link to="/orders" className="text-sm text-primary hover:underline">View all</Link></div>
        {recent.isLoading ? <div className="space-y-3 p-4">{[0, 1, 2].map((i) => <Skeleton key={i} className="h-8" />)}</div> : recent.isError ? <ErrorState message={errorMessage(recent.error)} onRetry={() => recent.refetch()} /> :
          recent.data && recent.data.content.length === 0 ? <EmptyState title="Nothing here yet" /> :
          <ul className="divide-y">{recent.data?.content.map((o) => (<li key={o.id}><Link to={`/orders/${o.id}`} className="flex items-center gap-3 px-4 py-3 text-sm hover:bg-muted/60">
            <div className="min-w-0 flex-1"><p className="truncate font-medium">{o.customerName}</p><p className="truncate text-xs text-muted-foreground">{o.pickupAddress} → {o.dropAddress}</p></div>
            <span className="hidden text-xs text-muted-foreground sm:block">{o.driverId ? names[o.driverId] ?? 'Driver' : 'Unassigned'}</span><OrderStatusBadge status={o.status} /><span className="hidden w-20 text-right text-xs text-muted-foreground sm:block">{timeAgo(o.createdAt)}</span></Link></li>))}</ul>}
      </Card>
      <Card className="p-4"><h2 className="mb-3 font-medium">System health</h2>
        {health.isLoading ? <Skeleton className="h-24" /> : health.isError ? <p className="text-sm text-muted-foreground">Health status unavailable.</p> :
          <ul className="space-y-3">{health.data?.map((c) => (<li key={c.name} className="flex items-center justify-between text-sm"><span>{c.name}</span><Badge tone={healthTone[c.status]}>{healthLabel[c.status]}</Badge></li>))}</ul>}
        <div className="mt-4 border-t pt-3 text-sm text-muted-foreground"><p>Completion rate: <span className="font-medium text-foreground">{dash(s?.completionRate ?? null, (n) => `${(n * 100).toFixed(1)}%`)}</span></p>
          <p>Avg assignment time: <span className="font-medium text-foreground">{dash(s?.avgAssignmentSeconds ?? null, (n) => `${n.toFixed(1)} s`)}</span></p></div>
      </Card>
    </div>
  </div>)
}
