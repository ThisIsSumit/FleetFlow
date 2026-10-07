import { useEffect, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Input } from '@/components/ui/Field'
import { geocodeApi } from '@/api/moreApi'
import type { Place } from '@/types'
/** Type-ahead address search backed by the server-side geocoder (cached, rate-limited). Presets and manual coordinates remain as fallbacks. */
export function AddressSearch({ onPick }: { onPick: (p: Place) => void }) {
  const [q, setQ] = useState(''), [debounced, setDebounced] = useState(''), [open, setOpen] = useState(false)
  useEffect(() => { const t = setTimeout(() => setDebounced(q.trim()), 450); return () => clearTimeout(t) }, [q])
  const res = useQuery({ queryKey: ['geocode', debounced], queryFn: () => geocodeApi.search(debounced), enabled: debounced.length >= 3, staleTime: 5 * 60_000, retry: false })
  const show = open && debounced.length >= 3
  return (<div className="relative">
    <Input label="Search address" value={q} placeholder="Start typing an address or landmark…" autoComplete="off" onChange={(e) => { setQ(e.target.value); setOpen(true) }} onFocus={() => setOpen(true)} onBlur={() => setTimeout(() => setOpen(false), 150)} />
    {show && (<ul role="listbox" aria-label="Address suggestions" className="absolute z-10 mt-1 max-h-60 w-full overflow-y-auto rounded-lg border bg-card shadow-lg">
      {res.isFetching && <li className="px-3 py-2 text-sm text-muted-foreground">Searching…</li>}
      {res.isError && <li className="px-3 py-2 text-sm text-danger">Address search is unavailable. Use a preset or enter coordinates.</li>}
      {!res.isFetching && !res.isError && res.data?.length === 0 && <li className="px-3 py-2 text-sm text-muted-foreground">No matches. Try a preset or enter coordinates.</li>}
      {res.data?.map((p) => (<li key={`${p.lat},${p.lng},${p.label}`} role="option" aria-selected="false"><button type="button" className="w-full px-3 py-2 text-left text-sm hover:bg-muted" onMouseDown={(e) => { e.preventDefault(); onPick(p); setQ(p.label); setOpen(false) }}>{p.label}</button></li>))}
    </ul>)}
  </div>)
}
