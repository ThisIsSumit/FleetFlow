import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Bike, Plus, Truck } from 'lucide-react'
import { driverApi, vehicleApi } from '@/api/fleetApi'
import { errorMessage } from '@/lib/api'
import { useAuth } from '@/stores/auth'
import { toast } from '@/stores/toast'
import { Button } from '@/components/ui/Button'
import { Input, Select } from '@/components/ui/Field'
import { Badge, Card, DriverStatusBadge, Skeleton } from '@/components/ui/Card'
import { EmptyState, ErrorState } from '@/components/ui/States'
import { Modal } from '@/components/ui/Modal'
import { PLACES } from '@/utils/format'
import type { DriverStatus } from '@/types'

const canWrite = (role?: string) => role === 'OWNER' || role === 'ADMIN'
const Loading = () => <div className="space-y-3 p-4">{[0, 1, 2, 3].map((i) => <Skeleton key={i} className="h-10" />)}</div>

const driverSchema = z.object({ name: z.string().min(1, 'Required').max(120), phone: z.string().max(20).optional(), vehicleId: z.string().optional() })
export function Drivers() {
  const role = useAuth((s) => s.user?.role); const qc = useQueryClient(); const [open, setOpen] = useState(false)
  const drivers = useQuery({ queryKey: ['drivers'], queryFn: () => driverApi.list(), refetchInterval: 15000 })
  const vehicles = useQuery({ queryKey: ['vehicles'], queryFn: () => vehicleApi.list() })
  const vmap = Object.fromEntries((vehicles.data?.content ?? []).map((v) => [v.id, v]))
  const free = (vehicles.data?.content ?? []).filter((v) => !v.assignedDriverId && v.status === 'ACTIVE')
  const status = useMutation({ mutationFn: (a: { id: string; s: DriverStatus }) => driverApi.setStatus(a.id, a.s), onSuccess: () => qc.invalidateQueries({ queryKey: ['drivers'] }), onError: (e) => toast.error(errorMessage(e)) })
  const loc = useMutation({ mutationFn: (a: { id: string; i: number }) => driverApi.sendLocation(a.id, PLACES[a.i].lat, PLACES[a.i].lng), onSuccess: () => toast.success('Location sent to the dispatch index'), onError: (e) => toast.error(errorMessage(e)) })
  const { register, handleSubmit, reset, formState: { errors } } = useForm<z.infer<typeof driverSchema>>({ resolver: zodResolver(driverSchema) })
  const create = useMutation({ mutationFn: (v: z.infer<typeof driverSchema>) => driverApi.create({ name: v.name, phone: v.phone || undefined, vehicleId: v.vehicleId || undefined }),
    onSuccess: () => { toast.success('Driver added'); qc.invalidateQueries({ queryKey: ['drivers'] }); qc.invalidateQueries({ queryKey: ['vehicles'] }); reset(); setOpen(false) }, onError: (e) => toast.error(errorMessage(e)) })
  return (<div className="space-y-4">
    <div className="flex flex-wrap items-center justify-between gap-3"><div><h1 className="text-2xl font-semibold tracking-tight">Drivers</h1><p className="text-sm text-muted-foreground">Availability, vehicles and last known position.</p></div>
      {canWrite(role) && <Button onClick={() => setOpen(true)}><Plus className="h-4 w-4" />Add driver</Button>}</div>
    <Card>{drivers.isLoading ? <Loading /> : drivers.isError ? <ErrorState message={errorMessage(drivers.error)} onRetry={() => drivers.refetch()} /> :
      drivers.data!.content.length === 0 ? <EmptyState icon={<Bike className="h-5 w-5" />} title="No drivers yet" description="Add a driver, set them Available and send a location so dispatch can find them." /> :
      <div className="overflow-x-auto"><table className="w-full text-left text-sm"><thead className="border-b text-xs uppercase text-muted-foreground"><tr>{['Driver', 'Status', 'Vehicle', 'Rating', 'Send location'].map((h) => <th key={h} className="px-4 py-3 font-medium">{h}</th>)}</tr></thead>
        <tbody className="divide-y">{drivers.data!.content.map((d) => (<tr key={d.id} className="hover:bg-muted/50">
          <td className="px-4 py-3"><Link to={`/drivers/${d.id}`} className="font-medium text-primary hover:underline">{d.name}</Link>{d.hasLogin && <Badge className="ml-2">App login</Badge>}{d.phone && <p className="text-xs text-muted-foreground">{d.phone}</p>}</td>
          <td className="px-4 py-3"><div className="flex items-center gap-2"><DriverStatusBadge status={d.status} />
            <select aria-label={`Set status for ${d.name}`} className="h-8 rounded-md border bg-card px-2 text-xs" value={d.status} onChange={(e) => status.mutate({ id: d.id, s: e.target.value as DriverStatus })}>{['AVAILABLE', 'BUSY', 'OFFLINE', 'ON_BREAK'].map((s) => <option key={s}>{s}</option>)}</select></div></td>
          <td className="px-4 py-3">{d.vehicleId && vmap[d.vehicleId] ? `${vmap[d.vehicleId].registrationNumber} · ${vmap[d.vehicleId].type}` : <span className="text-muted-foreground">None</span>}</td>
          <td className="px-4 py-3 tabular-nums">{Number(d.rating).toFixed(1)}</td>
          <td className="px-4 py-3"><select aria-label={`Send location for ${d.name}`} className="h-8 rounded-md border bg-card px-2 text-xs" value="" onChange={(e) => e.target.value !== '' && loc.mutate({ id: d.id, i: Number(e.target.value) })}><option value="">Choose place…</option>{PLACES.map((p, i) => <option key={p.label} value={i}>{p.label}</option>)}</select></td></tr>))}</tbody></table></div>}
    </Card>
    <Modal open={open} onClose={() => setOpen(false)} title="Add driver"><form onSubmit={handleSubmit((v) => create.mutate(v))} className="space-y-4" noValidate>
      <Input label="Name" error={errors.name?.message} {...register('name')} /><Input label="Phone (optional)" {...register('phone')} />
      <Select label="Vehicle" {...register('vehicleId')}><option value="">No vehicle</option>{free.map((v) => <option key={v.id} value={v.id}>{v.registrationNumber} · {v.type}</option>)}</Select>
      <div className="flex justify-end gap-2"><Button type="button" variant="secondary" onClick={() => setOpen(false)}>Cancel</Button><Button type="submit" loading={create.isPending}>Add driver</Button></div></form></Modal>
  </div>)
}

const vehicleSchema = z.object({ registrationNumber: z.string().min(1, 'Required').max(32), type: z.enum(['BIKE', 'CAR', 'VAN', 'TRUCK']), capacityKg: z.number({ invalid_type_error: 'Required' }).int().min(1, 'Min 1') })
export function Vehicles() {
  const role = useAuth((s) => s.user?.role); const qc = useQueryClient(); const [open, setOpen] = useState(false)
  const vehicles = useQuery({ queryKey: ['vehicles'], queryFn: () => vehicleApi.list(), refetchInterval: 30000 })
  const drivers = useQuery({ queryKey: ['drivers'], queryFn: () => driverApi.list() })
  const dname = Object.fromEntries((drivers.data?.content ?? []).map((d) => [d.id, d.name]))
  const { register, handleSubmit, reset, formState: { errors } } = useForm<z.infer<typeof vehicleSchema>>({ resolver: zodResolver(vehicleSchema), defaultValues: { type: 'BIKE' } })
  const create = useMutation({ mutationFn: vehicleApi.create, onSuccess: () => { toast.success('Vehicle added'); qc.invalidateQueries({ queryKey: ['vehicles'] }); reset(); setOpen(false) }, onError: (e) => toast.error(errorMessage(e)) })
  return (<div className="space-y-4">
    <div className="flex flex-wrap items-center justify-between gap-3"><div><h1 className="text-2xl font-semibold tracking-tight">Vehicles</h1><p className="text-sm text-muted-foreground">Your fleet and who is driving what.</p></div>
      {canWrite(role) && <Button onClick={() => setOpen(true)}><Plus className="h-4 w-4" />Add vehicle</Button>}</div>
    <Card>{vehicles.isLoading ? <Loading /> : vehicles.isError ? <ErrorState message={errorMessage(vehicles.error)} onRetry={() => vehicles.refetch()} /> :
      vehicles.data!.content.length === 0 ? <EmptyState icon={<Truck className="h-5 w-5" />} title="No vehicles yet" description="Add your first vehicle, then assign it to a driver." /> :
      <div className="overflow-x-auto"><table className="w-full text-left text-sm"><thead className="border-b text-xs uppercase text-muted-foreground"><tr>{['Registration', 'Type', 'Capacity', 'Status', 'Assigned driver'].map((h) => <th key={h} className="px-4 py-3 font-medium">{h}</th>)}</tr></thead>
        <tbody className="divide-y">{vehicles.data!.content.map((v) => (<tr key={v.id} className="hover:bg-muted/50"><td className="px-4 py-3 font-mono text-xs font-medium">{v.registrationNumber}</td><td className="px-4 py-3">{v.type}</td><td className="px-4 py-3">{v.capacityKg} kg</td>
          <td className="px-4 py-3"><Badge tone={v.status === 'ACTIVE' ? 'success' : 'warning'}>{v.status.toLowerCase()}</Badge></td><td className="px-4 py-3">{v.assignedDriverId ? dname[v.assignedDriverId] ?? 'Assigned' : <span className="text-muted-foreground">Unassigned</span>}</td></tr>))}</tbody></table></div>}
    </Card>
    <Modal open={open} onClose={() => setOpen(false)} title="Add vehicle"><form onSubmit={handleSubmit((v) => create.mutate(v))} className="space-y-4" noValidate>
      <Input label="Registration number" placeholder="CH01AB1234" error={errors.registrationNumber?.message} {...register('registrationNumber')} />
      <Select label="Type" {...register('type')}>{['BIKE', 'CAR', 'VAN', 'TRUCK'].map((t) => <option key={t}>{t}</option>)}</Select>
      <Input label="Capacity (kg)" type="number" error={errors.capacityKg?.message} {...register('capacityKg', { valueAsNumber: true })} />
      <div className="flex justify-end gap-2"><Button type="button" variant="secondary" onClick={() => setOpen(false)}>Cancel</Button><Button type="submit" loading={create.isPending}>Add vehicle</Button></div></form></Modal>
  </div>)
}
