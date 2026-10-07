import { useCallback, useEffect, useRef, useState } from 'react'
import { Navigate, Outlet, useNavigate } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { LogOut, MapPin, Moon, Navigation, Phone, Sun } from 'lucide-react'
import { Logo } from '@/components/Logo'
import { Button } from '@/components/ui/Button'
import { Badge, Card, DriverStatusBadge, OrderStatusBadge, Skeleton } from '@/components/ui/Card'
import { ConfirmDialog } from '@/components/ui/Extras'
import { EmptyState, ErrorState } from '@/components/ui/States'
import { authApi } from '@/api/authApi'
import { driverPortalApi } from '@/api/moreApi'
import { errorMessage } from '@/lib/api'
import { useLiveSocket } from '@/hooks/useLiveSocket'
import { useAuth } from '@/stores/auth'
import { useTheme } from '@/stores/theme'
import { toast } from '@/stores/toast'
import { PLACES, fmtDateTime, titleCase } from '@/utils/format'
import type { DriverStatus, Order, OrderStatus } from '@/types'

/** Mobile-first shell for the DRIVER role. Staff users are sent to the dashboard. */
export function DriverLayout() {
  const { user, clear } = useAuth(); const { dark, toggle } = useTheme(); const nav = useNavigate(); const qc = useQueryClient()
  const logout = async () => { try { await authApi.logout() } finally { clear(); qc.clear(); nav('/login') } }
  if (user && user.role !== 'DRIVER') return <Navigate to="/dashboard" replace />
  return (<div className="min-h-screen">
    <header className="sticky top-0 z-20 flex h-14 items-center justify-between border-b bg-background/90 px-4 backdrop-blur"><Logo />
      <div className="flex items-center gap-1"><button onClick={toggle} aria-label="Toggle theme" className="rounded-md p-2 hover:bg-muted">{dark ? <Sun className="h-4 w-4" /> : <Moon className="h-4 w-4" />}</button>
        <button onClick={logout} aria-label="Log out" className="rounded-md p-2 hover:bg-muted"><LogOut className="h-4 w-4" /></button></div></header>
    <main className="mx-auto max-w-lg space-y-4 p-4"><Outlet /></main></div>)
}

const NEXT: Record<string, { to: OrderStatus; label: string }[]> = {
  ASSIGNED: [{ to: 'ARRIVED', label: "I've arrived at pickup" }], ARRIVED: [{ to: 'IN_TRANSIT', label: 'Start delivery' }], IN_TRANSIT: [{ to: 'DELIVERED', label: 'Mark delivered' }] }
const ACTIVE = ['ASSIGNED', 'ARRIVED', 'IN_TRANSIT']
const directions = (lat: number, lng: number) => `https://www.openstreetmap.org/directions?engine=fossgis_osrm_car&route=;${lat},${lng}`
type Mode = 'off' | 'gps' | 'sim'

export function DriverHome() {
  const qc = useQueryClient(); const q = useQuery({ queryKey: ['driver-home'], queryFn: driverPortalApi.me, refetchInterval: 8000, retry: false })
  const refresh = () => qc.invalidateQueries({ queryKey: ['driver-home'] })
  const home = q.data; const active = home?.deliveries.filter((o) => ACTIVE.includes(o.status)) ?? []; const history = home?.deliveries.filter((o) => !ACTIVE.includes(o.status)).slice(0, 10) ?? []
  const status = useMutation({ mutationFn: (s: DriverStatus) => driverPortalApi.setStatus(s), onSuccess: refresh, onError: (e) => toast.error(errorMessage(e)) })
  const move = useMutation({ mutationFn: (a: { id: string; s: OrderStatus }) => driverPortalApi.setOrderStatus(a.id, a.s), onSuccess: () => { toast.success('Updated'); refresh() }, onError: (e) => toast.error(errorMessage(e)) })
  const [failing, setFailing] = useState<Order | null>(null)

  // ----- location sharing: real GPS, or a simulator for demos -----
  const [mode, setMode] = useState<Mode>('off'); const [lastSent, setLastSent] = useState('')
  const { status: socket, send } = useLiveSocket(() => {})
  const push = useCallback((lat: number, lng: number) => { // WebSocket first; REST fallback keeps working when the socket is down
    if (!send({ type: 'location', latitude: lat, longitude: lng })) driverPortalApi.sendLocation(lat, lng).catch(() => {})
    setLastSent(new Date().toLocaleTimeString())
  }, [send])
  useEffect(() => {
    if (mode !== 'gps') return
    if (!navigator.geolocation) { toast.error('GPS is not available in this browser'); setMode('off'); return }
    let last = 0
    const id = navigator.geolocation.watchPosition((p) => { const now = Date.now(); if (now - last >= 5000) { last = now; push(p.coords.latitude, p.coords.longitude) } },
      () => { toast.error('Location permission was denied or is unavailable'); setMode('off') }, { enableHighAccuracy: true, maximumAge: 5000 })
    return () => navigator.geolocation.clearWatch(id)
  }, [mode, push])
  const activeRef = useRef(active); activeRef.current = active; const pos = useRef<[number, number] | null>(null)
  useEffect(() => {
    if (mode !== 'sim') return
    const tick = () => {
      const o = activeRef.current[0]; const t: [number, number] | null = o ? (o.status === 'IN_TRANSIT' ? [o.dropLat, o.dropLng] : [o.pickupLat, o.pickupLng]) : null
      if (!pos.current) { const b: [number, number] = t ?? [PLACES[0].lat, PLACES[0].lng]; pos.current = [b[0] + 0.012, b[1] + 0.012] }
      if (t) { const [la, ln] = pos.current; pos.current = Math.hypot(t[0] - la, t[1] - ln) < 0.0005 ? t : [la + (t[0] - la) * 0.25, ln + (t[1] - ln) * 0.25] }
      push(pos.current[0], pos.current[1])
    }
    tick(); const id = setInterval(tick, 3000); return () => clearInterval(id)
  }, [mode, push])

  if (q.isLoading) return <div className="space-y-3"><Skeleton className="h-24" /><Skeleton className="h-40" /></div>
  if (q.isError || !home) return <Card><ErrorState message={errorMessage(q.error)} onRetry={() => q.refetch()} /></Card>
  const d = home.driver; const busy = active.length > 0
  return (<>
    <Card className="p-4"><div className="flex items-center justify-between"><div><p className="text-lg font-semibold">{d.name}</p><p className="text-xs text-muted-foreground">{home.vehicle ? `${home.vehicle.registrationNumber} · ${home.vehicle.type}` : 'No vehicle assigned'}</p></div><DriverStatusBadge status={d.status} /></div>
      <div className="mt-3 grid grid-cols-3 gap-2" role="group" aria-label="Set my status">{(['AVAILABLE', 'ON_BREAK', 'OFFLINE'] as DriverStatus[]).map((s) => (
        <Button key={s} size="sm" variant={d.status === s ? 'primary' : 'secondary'} disabled={(s !== 'AVAILABLE' && busy) || (s === 'AVAILABLE' && d.status === 'BUSY')} loading={status.isPending && status.variables === s} onClick={() => status.mutate(s)}>{titleCase(s)}</Button>))}</div>
      {busy && <p className="mt-2 text-xs text-muted-foreground">Finish your active delivery before going on break or offline.</p>}</Card>
    <Card className="p-4"><div className="flex items-center justify-between"><h2 className="flex items-center gap-2 font-medium"><MapPin className="h-4 w-4" />Location sharing</h2><Badge tone={mode === 'off' ? 'neutral' : socket === 'open' ? 'success' : 'warning'}>{mode === 'off' ? 'Off' : socket === 'open' ? 'Live' : 'Via REST'}</Badge></div>
      <div className="mt-3 grid grid-cols-3 gap-2"><Button size="sm" variant={mode === 'gps' ? 'primary' : 'secondary'} onClick={() => setMode('gps')}>Use GPS</Button><Button size="sm" variant={mode === 'sim' ? 'primary' : 'secondary'} onClick={() => setMode('sim')}>Simulate</Button><Button size="sm" variant="secondary" disabled={mode === 'off'} onClick={() => setMode('off')}>Stop</Button></div>
      <p className="mt-2 text-xs text-muted-foreground">{mode === 'off' ? 'Dispatch only considers drivers who shared a location in the last 2 minutes.' : `Last sent: ${lastSent || 'waiting…'}`}{mode === 'sim' && ' · Simulation moves towards your current pickup or drop.'} GPS needs HTTPS (localhost is fine).</p></Card>
    <h2 className="pt-2 font-medium">Active deliveries</h2>
    {active.length === 0 ? <Card><EmptyState title="No active delivery" description={d.status === 'AVAILABLE' ? 'You are available. Share your location and wait for an assignment.' : 'Set yourself as available to receive orders.'} /></Card> :
      active.map((o) => (<Card key={o.id} className="space-y-3 p-4"><div className="flex items-center justify-between"><p className="font-semibold">{o.customerName}</p><OrderStatusBadge status={o.status} /></div>
        {o.customerPhone && <a href={`tel:${o.customerPhone.replace(/\s/g, '')}`} className="inline-flex items-center gap-1 text-sm text-primary"><Phone className="h-3.5 w-3.5" />{o.customerPhone}</a>}
        <div className="space-y-2 text-sm"><div><p className="text-xs uppercase text-muted-foreground">Pickup</p><p>{o.pickupAddress}</p><a className="inline-flex items-center gap-1 text-xs text-primary" href={directions(o.pickupLat, o.pickupLng)} target="_blank" rel="noreferrer"><Navigation className="h-3 w-3" />Directions</a></div>
          <div><p className="text-xs uppercase text-muted-foreground">Drop</p><p>{o.dropAddress}</p><a className="inline-flex items-center gap-1 text-xs text-primary" href={directions(o.dropLat, o.dropLng)} target="_blank" rel="noreferrer"><Navigation className="h-3 w-3" />Directions</a></div>
          <p className="text-muted-foreground">{o.items.map((i) => `${i.quantity}× ${i.name}`).join(', ')}</p></div>
        <div className="flex flex-wrap gap-2">{(NEXT[o.status] ?? []).map((n) => <Button key={n.to} className="flex-1" loading={move.isPending && move.variables?.id === o.id} onClick={() => move.mutate({ id: o.id, s: n.to })}>{n.label}</Button>)}
          {o.status === 'IN_TRANSIT' && <Button variant="secondary" onClick={() => setFailing(o)}>Failed delivery</Button>}</div></Card>))}
    <h2 className="pt-2 font-medium">Recent</h2>
    {history.length === 0 ? <p className="text-sm text-muted-foreground">Nothing yet.</p> : <Card><ul className="divide-y">{history.map((o) => (<li key={o.id} className="flex items-center gap-3 px-4 py-3 text-sm"><span className="min-w-0 flex-1 truncate">{o.customerName}<span className="block text-xs text-muted-foreground">{fmtDateTime(o.updatedAt)}</span></span><OrderStatusBadge status={o.status} /></li>))}</ul></Card>}
    <ConfirmDialog open={!!failing} danger title="Mark delivery as failed?" message="Use this only if you could not complete the delivery. Your dispatcher will be alerted." confirmLabel="Mark failed" loading={move.isPending}
      onConfirm={() => { if (failing) move.mutate({ id: failing.id, s: 'FAILED' }, { onSettled: () => setFailing(null) }) }} onClose={() => setFailing(null)} />
  </>)
}
