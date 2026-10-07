import { useFieldArray, useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { Plus, Trash2 } from 'lucide-react'
import { Modal } from '@/components/ui/Modal'
import { Button } from '@/components/ui/Button'
import { Input, Select } from '@/components/ui/Field'
import { orderApi } from '@/api/orderApi'
import { errorMessage } from '@/lib/api'
import { toast } from '@/stores/toast'
import { PLACES } from '@/utils/format'
import { AddressSearch } from '@/components/AddressSearch'

const num = (min: number, max: number) => z.number({ invalid_type_error: 'Required' }).min(min, `Min ${min}`).max(max, `Max ${max}`)
const schema = z.object({
  customerName: z.string().min(1, 'Required').max(120), customerPhone: z.string().max(20).optional(),
  pickupAddress: z.string().min(1, 'Required').max(300), pickupLat: num(-90, 90), pickupLng: num(-180, 180),
  dropAddress: z.string().min(1, 'Required').max(300), dropLat: num(-90, 90), dropLng: num(-180, 180),
  items: z.array(z.object({ name: z.string().min(1, 'Required').max(120), quantity: z.number({ invalid_type_error: 'Required' }).int().min(1, 'Min 1') })).min(1).max(50),
  priority: z.enum(['LOW', 'NORMAL', 'HIGH', 'URGENT']), vehicleRequirement: z.enum(['', 'BIKE', 'CAR', 'VAN', 'TRUCK']), scheduledAt: z.string().optional(),
})
type Values = z.infer<typeof schema>

export function CreateOrderModal({ open, onClose }: { open: boolean; onClose: () => void }) {
  const qc = useQueryClient()
  const { register, control, handleSubmit, setValue, reset, formState: { errors } } = useForm<Values>({
    resolver: zodResolver(schema), defaultValues: { priority: 'NORMAL', vehicleRequirement: '', items: [{ name: '', quantity: 1 }] } })
  const items = useFieldArray({ control, name: 'items' })
  const create = useMutation({
    mutationFn: (v: Values) => orderApi.create({ ...v, customerPhone: v.customerPhone || undefined, vehicleRequirement: v.vehicleRequirement || undefined, scheduledAt: v.scheduledAt ? new Date(v.scheduledAt).toISOString() : undefined }),
    onSuccess: () => { toast.success('Order created. Dispatch has been requested.'); qc.invalidateQueries({ queryKey: ['orders'] }); qc.invalidateQueries({ queryKey: ['dashboard'] }); reset(); onClose() },
    onError: (e) => toast.error(errorMessage(e)),
  })
  const applyPlace = (kind: 'pickup' | 'drop', p: { label: string; lat: number; lng: number }) => {
    setValue(`${kind}Address`, p.label, { shouldValidate: true }); setValue(`${kind}Lat`, p.lat, { shouldValidate: true }); setValue(`${kind}Lng`, p.lng, { shouldValidate: true }) }
  const pick = (kind: 'pickup' | 'drop', idx: string) => { const p = PLACES[Number(idx)]; if (p) applyPlace(kind, p) }
  const quick = (kind: 'pickup' | 'drop') => (<><AddressSearch onPick={(p) => applyPlace(kind, p)} /><Select label="Or choose a preset" defaultValue="" onChange={(e) => pick(kind, e.target.value)}><option value="">Choose…</option>{PLACES.map((p, i) => <option key={p.label} value={i}>{p.label}</option>)}</Select></>)
  return (<Modal open={open} onClose={onClose} title="Create order" wide>
    <form onSubmit={handleSubmit((v) => create.mutate(v))} className="space-y-5" noValidate>
      <div className="grid gap-4 sm:grid-cols-2"><Input label="Customer" error={errors.customerName?.message} {...register('customerName')} /><Input label="Phone (optional)" error={errors.customerPhone?.message} {...register('customerPhone')} /></div>
      {(['pickup', 'drop'] as const).map((k) => (<fieldset key={k} className="space-y-3 rounded-lg border p-3"><legend className="px-1 text-sm font-medium">{k === 'pickup' ? 'Pickup location' : 'Drop location'}</legend>
        {quick(k)}<Input label="Address" error={errors[`${k}Address`]?.message} {...register(`${k}Address`)} />
        <div className="grid grid-cols-2 gap-3"><Input label="Latitude" type="number" step="any" error={errors[`${k}Lat`]?.message} {...register(`${k}Lat`, { valueAsNumber: true })} />
          <Input label="Longitude" type="number" step="any" error={errors[`${k}Lng`]?.message} {...register(`${k}Lng`, { valueAsNumber: true })} /></div></fieldset>))}
      <fieldset className="space-y-2"><legend className="text-sm font-medium">Items</legend>
        {items.fields.map((f, i) => (<div key={f.id} className="flex items-start gap-2"><div className="flex-1"><Input aria-label="Item name" placeholder="Item" error={errors.items?.[i]?.name?.message} {...register(`items.${i}.name`)} /></div>
          <div className="w-24"><Input aria-label="Quantity" type="number" min={1} error={errors.items?.[i]?.quantity?.message} {...register(`items.${i}.quantity`, { valueAsNumber: true })} /></div>
          <Button type="button" variant="ghost" aria-label="Remove item" disabled={items.fields.length === 1} onClick={() => items.remove(i)}><Trash2 className="h-4 w-4" /></Button></div>))}
        <Button type="button" variant="secondary" size="sm" onClick={() => items.append({ name: '', quantity: 1 })}><Plus className="h-4 w-4" />Add item</Button></fieldset>
      <div className="grid gap-4 sm:grid-cols-3">
        <Select label="Priority" {...register('priority')}>{['LOW', 'NORMAL', 'HIGH', 'URGENT'].map((p) => <option key={p}>{p}</option>)}</Select>
        <Select label="Vehicle requirement" {...register('vehicleRequirement')}><option value="">Any</option>{['BIKE', 'CAR', 'VAN', 'TRUCK'].map((p) => <option key={p}>{p}</option>)}</Select>
        <Input label="Scheduled (optional)" type="datetime-local" {...register('scheduledAt')} /></div>
      <div className="flex justify-end gap-2 border-t pt-4"><Button type="button" variant="secondary" onClick={onClose}>Cancel</Button><Button type="submit" loading={create.isPending}>Create order</Button></div>
    </form></Modal>)
}
