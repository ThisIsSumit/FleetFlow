import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Bar, BarChart, CartesianGrid, Legend, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { analyticsApi } from '@/api/moreApi'
import { errorMessage } from '@/lib/api'
import { Card, Skeleton } from '@/components/ui/Card'
import { Select } from '@/components/ui/Field'
import { EmptyState, ErrorState } from '@/components/ui/States'

const tip = { contentStyle: { background: 'hsl(var(--card))', border: '1px solid hsl(var(--border))', borderRadius: 8 } }
const pct = (v: number | null) => (v == null ? '—' : `${(v * 100).toFixed(1)}%`)
export function AnalyticsPage() {
  const [days, setDays] = useState(14)
  const q = useQuery({ queryKey: ['analytics', days], queryFn: () => analyticsApi.get(days) })
  const a = q.data; const series = (a?.series ?? []).map((d) => ({ ...d, day: new Date(d.date + 'T00:00:00').toLocaleDateString(undefined, { month: 'short', day: 'numeric' }) }))
  const stats: [string, string][] = a ? [['Completion rate', pct(a.completionRate)], ['Cancellation rate', pct(a.cancellationRate)], ['Avg delivery time', a.avgDeliveryMinutes == null ? '—' : `${Math.round(a.avgDeliveryMinutes)} min`],
    ['Avg assignment time', a.avgAssignmentSeconds == null ? '—' : `${a.avgAssignmentSeconds.toFixed(1)} s`], ['Driver utilization now', pct(a.driverUtilization)], ['Orders per driver', a.avgOrdersPerDriver == null ? '—' : a.avgOrdersPerDriver.toFixed(1)]] : []
  const empty = a && a.series.every((d) => d.created === 0 && d.delivered === 0)
  return (<div className="space-y-5">
    <div className="flex flex-wrap items-end justify-between gap-3"><div><h1 className="text-2xl font-semibold tracking-tight">Analytics</h1><p className="text-sm text-muted-foreground">Delivery performance and fleet utilisation (UTC days).</p></div>
      <div className="w-40"><Select label="Period" value={days} onChange={(e) => setDays(Number(e.target.value))}>{[7, 14, 30, 90].map((d) => <option key={d} value={d}>Last {d} days</option>)}</Select></div></div>
    {q.isError ? <Card><ErrorState message={errorMessage(q.error)} onRetry={() => q.refetch()} /></Card> : (<>
      <div className="grid grid-cols-2 gap-3 md:grid-cols-3 xl:grid-cols-6">{q.isLoading ? [0, 1, 2, 3, 4, 5].map((i) => <Skeleton key={i} className="h-20" />) : stats.map(([k, v]) => (<Card key={k} className="p-4"><p className="text-sm text-muted-foreground">{k}</p><p className="mt-1 text-2xl font-semibold tabular-nums">{v}</p></Card>))}</div>
      {empty ? <Card><EmptyState title="No activity in this period" description="Create or seed some orders to see trends." /></Card> : (<div className="grid gap-4 lg:grid-cols-2">
        <Card className="p-4"><h2 className="mb-3 font-medium">Orders and deliveries per day</h2><div className="h-64">{q.isLoading ? <Skeleton className="h-full" /> : <ResponsiveContainer><LineChart data={series}><CartesianGrid strokeDasharray="3 3" stroke="hsl(var(--border))" /><XAxis dataKey="day" fontSize={12} /><YAxis allowDecimals={false} fontSize={12} width={28} /><Tooltip {...tip} /><Legend />
          <Line dataKey="created" name="Created" stroke="hsl(var(--primary))" strokeWidth={2} dot={false} /><Line dataKey="delivered" name="Delivered" stroke="hsl(var(--success))" strokeWidth={2} dot={false} /></LineChart></ResponsiveContainer>}</div></Card>
        <Card className="p-4"><h2 className="mb-3 font-medium">Cancellations and failures per day</h2><div className="h-64">{q.isLoading ? <Skeleton className="h-full" /> : <ResponsiveContainer><BarChart data={series}><CartesianGrid strokeDasharray="3 3" stroke="hsl(var(--border))" /><XAxis dataKey="day" fontSize={12} /><YAxis allowDecimals={false} fontSize={12} width={28} /><Tooltip {...tip} /><Legend />
          <Bar dataKey="cancelled" name="Cancelled" stackId="a" fill="hsl(var(--warning))" /><Bar dataKey="failed" name="Failed" stackId="a" fill="hsl(var(--danger))" /></BarChart></ResponsiveContainer>}</div></Card>
        <Card className="p-4 lg:col-span-2"><h2 className="mb-3 font-medium">Orders per driver</h2><div className="h-72">{q.isLoading ? <Skeleton className="h-full" /> : <ResponsiveContainer><BarChart data={a?.ordersPerDriver ?? []} layout="vertical" margin={{ left: 20 }}><CartesianGrid strokeDasharray="3 3" stroke="hsl(var(--border))" /><XAxis type="number" allowDecimals={false} fontSize={12} /><YAxis type="category" dataKey="name" fontSize={12} width={110} /><Tooltip {...tip} /><Legend />
          <Bar dataKey="orders" name="Assigned" fill="hsl(var(--primary))" /><Bar dataKey="delivered" name="Delivered" fill="hsl(var(--success))" /></BarChart></ResponsiveContainer>}</div></Card>
      </div>)}</>)}
  </div>)
}
