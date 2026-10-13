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
  return (<Card className="border-[#272727] bg-[#181818] p-4 shadow-none"><p className="text-sm text-white/50">{label}</p>
    {loading ? <Skeleton className="mt-2 h-8 w-20 bg-[#272727]" /> : <p className="mt-1 text-2xl font-semibold tabular-nums text-white">{value}</p>}</Card>)
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
  return (<div className="space-y-6 rounded-2xl bg-black p-1 font-[Manrope] text-white [text-wrap:pretty] sm:p-2">
    <div><p className="mb-2 text-sm text-white/45">Fleet operations overview</p><h1 className="bg-gradient-to-r from-white to-[#9B9B9B] bg-clip-text text-3xl font-semibold leading-tight text-transparent">{greeting()}, {user?.name.split(' ')[0]}</h1><p className="mt-2 text-sm text-white/55">Here's what's happening with your fleet today.</p></div>
    {sum.isError && <Card className="border-[#5d2929] bg-[#1F1F1F] text-white"><ErrorState message={errorMessage(sum.error)} onRetry={() => sum.refetch()} /></Card>}
    <div className="grid grid-cols-2 gap-3 md:grid-cols-3 xl:grid-cols-6">
      <Stat label="Total orders" value={s?.totalOrders ?? 0} loading={loading} /><Stat label="Active deliveries" value={s?.activeDeliveries ?? 0} loading={loading} />
      <Stat label="Available drivers" value={s?.availableDrivers ?? 0} loading={loading} /><Stat label="Completed today" value={s?.completedToday ?? 0} loading={loading} />
      <Stat label="Avg delivery time" value={dash(s?.avgDeliveryMinutes ?? null, (n) => `${Math.round(n)} min`)} loading={loading} />
      <Stat label="Failed today" value={s?.failedToday ?? 0} loading={loading} />
    </div>
    <div className="grid gap-4 lg:grid-cols-3">
      <Card className="border-[#272727] bg-[#181818] p-4 shadow-none lg:col-span-2"><h2 className="mb-3 font-medium text-white">Orders overview <span className="text-xs font-normal text-white/40">last 7 days (UTC)</span></h2>
        {loading ? <Skeleton className="h-64 bg-[#272727]" /> : s && s.totalOrders === 0 ? <EmptyState title="No orders yet" description="Create your first order and it will show up here." action={<Link to="/orders" className="text-sm font-medium text-white hover:text-white/70 hover:underline">Go to orders</Link>} /> :
          <div className="h-64"><ResponsiveContainer><LineChart data={chart}><CartesianGrid strokeDasharray="3 3" stroke="#272727" /><XAxis dataKey="day" fontSize={12} tick={{ fill: '#9B9B9B' }} /><YAxis allowDecimals={false} fontSize={12} width={28} tick={{ fill: '#9B9B9B' }} />
            <Tooltip contentStyle={{ background: '#181818', border: '1px solid #313131', borderRadius: 16, color: '#fff' }} /><Legend wrapperStyle={{ color: '#9B9B9B' }} />
            <Line type="monotone" dataKey="created" name="Created" stroke="#FFFFFF" strokeWidth={2} dot={false} /><Line type="monotone" dataKey="delivered" name="Delivered" stroke="#8B8B8B" strokeWidth={2} dot={false} /></LineChart></ResponsiveContainer></div>}
      </Card>
      <Card className="border-[#272727] bg-[#181818] p-4 shadow-none"><h2 className="mb-3 font-medium text-white">Live fleet</h2>
        {loading ? <Skeleton className="h-64 bg-[#272727]" /> : driverBars.length === 0 ? <EmptyState title="No drivers yet" description="Add drivers to see fleet utilisation." /> :
          <div className="h-64"><ResponsiveContainer><BarChart data={driverBars}><CartesianGrid strokeDasharray="3 3" stroke="#272727" /><XAxis dataKey="status" fontSize={12} tick={{ fill: '#9B9B9B' }} /><YAxis allowDecimals={false} fontSize={12} width={28} tick={{ fill: '#9B9B9B' }} />
            <Tooltip contentStyle={{ background: '#181818', border: '1px solid #313131', borderRadius: 16, color: '#fff' }} /><Bar dataKey="drivers" fill="#FFFFFF" radius={[4, 4, 0, 0]} /></BarChart></ResponsiveContainer></div>}
      </Card>
    </div>
    <div className="grid gap-4 lg:grid-cols-3">
      <Card className="border-[#272727] bg-[#181818] shadow-none lg:col-span-2"><div className="flex items-center justify-between border-b border-[#272727] p-4"><h2 className="font-medium text-white">Recent deliveries</h2><Link to="/orders" className="text-sm text-white/60 hover:text-white hover:underline">View all</Link></div>
        {recent.isLoading ? <div className="space-y-3 p-4">{[0, 1, 2].map((i) => <Skeleton key={i} className="h-8 bg-[#272727]" />)}</div> : recent.isError ? <ErrorState message={errorMessage(recent.error)} onRetry={() => recent.refetch()} /> :
          recent.data && recent.data.content.length === 0 ? <EmptyState title="Nothing here yet" /> :
          <ul className="divide-y divide-[#272727]">{recent.data?.content.map((o) => (<li key={o.id}><Link to={`/orders/${o.id}`} className="flex items-center gap-3 px-4 py-3 text-sm transition-colors duration-300 ease-[cubic-bezier(0.32,0.72,0,1)] hover:bg-[#1F1F1F]">
            <div className="min-w-0 flex-1"><p className="truncate font-medium text-white">{o.customerName}</p><p className="truncate text-xs text-white/45">{o.pickupAddress} → {o.dropAddress}</p></div>
            <span className="hidden text-xs text-white/45 sm:block">{o.driverId ? names[o.driverId] ?? 'Driver' : 'Unassigned'}</span><OrderStatusBadge status={o.status} /><span className="hidden w-20 text-right text-xs text-white/45 sm:block">{timeAgo(o.createdAt)}</span></Link></li>))}</ul>}
      </Card>
      <Card className="border-[#272727] bg-[#181818] p-4 shadow-none"><h2 className="mb-3 font-medium text-white">System health</h2>
        {health.isLoading ? <Skeleton className="h-24 bg-[#272727]" /> : health.isError ? <p className="text-sm text-white/50">Health status unavailable.</p> :
          <ul className="space-y-3">{health.data?.map((c) => (<li key={c.name} className="flex items-center justify-between text-sm"><span>{c.name}</span><Badge tone={healthTone[c.status]}>{healthLabel[c.status]}</Badge></li>))}</ul>}
        <div className="mt-4 border-t border-[#272727] pt-3 text-sm text-white/50"><p>Completion rate: <span className="font-medium text-white">{dash(s?.completionRate ?? null, (n) => `${(n * 100).toFixed(1)}%`)}</span></p>
          <p>Avg assignment time: <span className="font-medium text-white">{dash(s?.avgAssignmentSeconds ?? null, (n) => `${n.toFixed(1)} s`)}</span></p></div>
      </Card>
    </div>
  </div>)
}
