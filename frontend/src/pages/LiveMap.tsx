import { useEffect, useMemo, useRef, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import L from 'leaflet'
import 'leaflet/dist/leaflet.css'
import 'leaflet.markercluster'
import 'leaflet.markercluster/dist/MarkerCluster.css'
import 'leaflet.markercluster/dist/MarkerCluster.Default.css'
import { Radio } from 'lucide-react'
import { liveApi } from '@/api/moreApi'
import { errorMessage } from '@/lib/api'
import { useLiveSocket } from '@/hooks/useLiveSocket'
import { Badge, Card, DriverStatusBadge, Skeleton } from '@/components/ui/Card'
import { EmptyState, ErrorState } from '@/components/ui/States'
import { titleCase } from '@/utils/format'
import type { LiveDriver, LiveOrder } from '@/types'

const COLOR: Record<string, string> = { AVAILABLE: '#16a34a', BUSY: '#d97706', ON_BREAK: '#2563eb', OFFLINE: '#6b7280' }
const km = (a: [number, number], b: [number, number]) => { const r = (x: number) => (x * Math.PI) / 180, dLat = r(b[0] - a[0]), dLng = r(b[1] - a[1])
  return 12742 * Math.asin(Math.sqrt(Math.sin(dLat / 2) ** 2 + Math.cos(r(a[0])) * Math.cos(r(b[0])) * Math.sin(dLng / 2) ** 2)) }
const line = (parent: HTMLElement, text: string, bold = false) => { const p = document.createElement('div'); p.textContent = text; if (bold) p.style.fontWeight = '600'; parent.appendChild(p) } // textContent only: tenant data is never parsed as HTML

function popup(d: LiveDriver, o?: LiveOrder, pos?: [number, number]) {
  const el = document.createElement('div'); line(el, d.name, true); line(el, `Status: ${titleCase(d.status)}`)
  line(el, `Vehicle: ${d.registration ? `${d.registration} (${d.vehicleType})` : 'None'}`)
  line(el, `Current order: ${o ? `${o.customerName} · ${titleCase(o.status)}` : 'None'}`)
  if (o && pos) { const target: [number, number] = o.status === 'IN_TRANSIT' ? [o.dropLat, o.dropLng] : [o.pickupLat, o.pickupLng]
    line(el, `Est. ETA: ~${Math.max(1, Math.round((km(pos, target) / 25) * 60))} min (straight line, 25 km/h assumed)`) }
  return el
}
export function LiveMap() {
  const snap = useQuery({ queryKey: ['live'], queryFn: liveApi.snapshot, refetchInterval: 15000 })
  const [live, setLive] = useState<Record<string, [number, number]>>({}); const [selected, setSelected] = useState<string | null>(null)
  const { status } = useLiveSocket((m) => { if (m.type === 'driver.location' && m.driverId && m.latitude != null && m.longitude != null) setLive((p) => ({ ...p, [m.driverId!]: [m.latitude!, m.longitude!] })) })
  const el = useRef<HTMLDivElement>(null), map = useRef<L.Map | null>(null), cluster = useRef<L.MarkerClusterGroup | null>(null), overlay = useRef<L.LayerGroup | null>(null), fitted = useRef(false), markers = useRef<Record<string, L.Marker>>({})
  useEffect(() => {
    const m = L.map(el.current!).setView([22.5, 78.9], 5)
    L.tileLayer(import.meta.env.VITE_TILE_URL ?? 'https://tile.openstreetmap.org/{z}/{x}/{y}.png', { maxZoom: 19, attribution: import.meta.env.VITE_TILE_ATTRIBUTION ?? '© OpenStreetMap contributors' }).addTo(m)
    cluster.current = L.markerClusterGroup({ showCoverageOnHover: false, maxClusterRadius: 50 }).addTo(m); overlay.current = L.layerGroup().addTo(m); map.current = m
    return () => { m.remove(); map.current = null }
  }, [])
  const positions = useMemo(() => { const out: Record<string, [number, number]> = {}
    snap.data?.drivers.forEach((d) => { if (d.lat != null && d.lng != null) out[d.id] = [d.lat, d.lng] }); return { ...out, ...live } }, [snap.data, live])
  const posRef = useRef(positions); posRef.current = positions
  const drivers_ = useRef<Record<string, LiveDriver>>({}), orders_ = useRef<Record<string, LiveOrder>>({})
  /** Creates or updates one driver marker in place. Markers are never rebuilt on a location ping, so an open popup stays open. */
  const place = (d: LiveDriver, pos: [number, number]) => {
    const html = `<div style="width:18px;height:18px;border-radius:50%;background:${COLOR[d.status] ?? '#6b7280'};border:3px solid #fff;box-shadow:0 0 0 1px rgba(0,0,0,.3)"></div>`
    const content = popup(d, d.currentOrderId ? orders_.current[d.currentOrderId] : undefined, pos)
    const mk = markers.current[d.id]
    if (mk) { mk.setLatLng(pos); if ((mk.options.icon as L.DivIcon | undefined)?.options.html !== html) mk.setIcon(L.divIcon({ className: '', iconSize: [18, 18], html })); mk.setPopupContent(content); return }
    const created = L.marker(pos, { icon: L.divIcon({ className: '', iconSize: [18, 18], html }), title: d.name }).bindPopup(content)
    cluster.current!.addLayer(created); markers.current[d.id] = created
  }
  // Snapshot (every 15 s): redraw order overlays, reconcile the set of driver markers.
  useEffect(() => {
    if (!snap.data || !map.current) return
    overlay.current!.clearLayers()
    orders_.current = Object.fromEntries(snap.data.orders.map((o) => [o.id, o]))
    snap.data.orders.forEach((o) => {
      L.circleMarker([o.pickupLat, o.pickupLng], { radius: 5, color: '#2563eb', fillColor: '#2563eb', fillOpacity: 0.9 }).bindTooltip(`Pickup · ${o.customerName}`).addTo(overlay.current!)
      L.circleMarker([o.dropLat, o.dropLng], { radius: 5, color: '#7c3aed', fillColor: '#fff', fillOpacity: 1 }).bindTooltip(`Drop · ${o.customerName}`).addTo(overlay.current!)
      if (o.driverId) L.polyline([[o.pickupLat, o.pickupLng], [o.dropLat, o.dropLng]], { color: '#94a3b8', weight: 1.5, dashArray: '4 6' }).addTo(overlay.current!)
    })
    drivers_.current = Object.fromEntries(snap.data.drivers.map((d) => [d.id, d]))
    for (const id of Object.keys(markers.current)) if (!drivers_.current[id] || !posRef.current[id]) { cluster.current!.removeLayer(markers.current[id]); delete markers.current[id] }
    snap.data.drivers.forEach((d) => { const pos = posRef.current[d.id]; if (pos) place(d, pos) })
    if (!fitted.current) { const pts = Object.values(posRef.current); if (pts.length) { map.current.fitBounds(L.latLngBounds(pts), { padding: [40, 40], maxZoom: 13 }); fitted.current = true } }
  }, [snap.data]) // eslint-disable-line react-hooks/exhaustive-deps
  // Live pings: move markers in place.
  useEffect(() => {
    if (!map.current || !cluster.current) return
    for (const [id, pos] of Object.entries(live)) { const d = drivers_.current[id]; if (d) place(d, pos) }
  }, [live]) // eslint-disable-line react-hooks/exhaustive-deps
  const focus = (d: LiveDriver) => { setSelected(d.id); const p = positions[d.id]; if (p && map.current) { map.current.flyTo(p, 15); cluster.current?.zoomToShowLayer(markers.current[d.id], () => markers.current[d.id]?.openPopup()) } }
  const drivers = snap.data?.drivers ?? []
  return (<div className="space-y-4">
    <div className="flex flex-wrap items-center justify-between gap-3"><div><h1 className="text-2xl font-semibold tracking-tight">Live map</h1><p className="text-sm text-muted-foreground">Driver positions and active deliveries.</p></div>
      <Badge tone={status === 'open' ? 'success' : status === 'connecting' ? 'warning' : 'danger'}><Radio className="mr-1 h-3 w-3" />{status === 'open' ? 'Live' : status === 'connecting' ? 'Connecting' : 'Reconnecting'}</Badge></div>
    {snap.isError && <Card><ErrorState message={errorMessage(snap.error)} onRetry={() => snap.refetch()} /></Card>}
    <div className="grid gap-4 lg:grid-cols-[300px_1fr]">
      <Card className="max-h-[calc(100vh-11rem)] min-h-[200px] overflow-y-auto">
        {snap.isLoading ? <div className="space-y-2 p-3">{[0, 1, 2, 3].map((i) => <Skeleton key={i} className="h-10" />)}</div> : drivers.length === 0 ? <EmptyState title="No drivers yet" description="Add drivers and send locations to see them here." /> :
          <ul className="divide-y">{drivers.map((d) => (<li key={d.id}><button onClick={() => focus(d)} className={`flex w-full items-center justify-between gap-2 px-3 py-2.5 text-left text-sm hover:bg-muted/60 ${selected === d.id ? 'bg-muted' : ''}`}>
            <span className="min-w-0"><span className="block truncate font-medium">{d.name}</span><span className="block text-xs text-muted-foreground">{positions[d.id] ? 'Location known' : 'No location yet'}</span></span><DriverStatusBadge status={d.status} /></button></li>))}</ul>}
      </Card>
      <Card className="overflow-hidden"><div ref={el} className="h-[calc(100vh-11rem)] min-h-[420px] w-full" aria-label="Live fleet map" /></Card>
    </div></div>)
}
