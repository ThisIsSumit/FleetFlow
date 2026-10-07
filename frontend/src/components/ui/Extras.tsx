import { useEffect, useMemo, useRef, useState, type ReactNode } from 'react'
import { useNavigate } from 'react-router-dom'
import { Search, X } from 'lucide-react'
import { Button } from './Button'
import { Modal } from './Modal'
import { cn } from '@/lib/cn'

export function Tabs({ tabs, value, onChange }: { tabs: { id: string; label: string }[]; value: string; onChange: (id: string) => void }) {
  return (<div role="tablist" className="flex gap-1 overflow-x-auto border-b">{tabs.map((t) => (
    <button key={t.id} role="tab" aria-selected={value === t.id} onClick={() => onChange(t.id)}
      className={cn('whitespace-nowrap border-b-2 px-3 py-2 text-sm font-medium', value === t.id ? 'border-primary text-primary' : 'border-transparent text-muted-foreground hover:text-foreground')}>{t.label}</button>))}</div>)
}
export function ConfirmDialog({ open, title, message, confirmLabel = 'Confirm', danger, loading, onConfirm, onClose }:
  { open: boolean; title: string; message: string; confirmLabel?: string; danger?: boolean; loading?: boolean; onConfirm: () => void; onClose: () => void }) {
  return (<Modal open={open} onClose={onClose} title={title}><p className="text-sm text-muted-foreground">{message}</p>
    <div className="mt-5 flex justify-end gap-2"><Button variant="secondary" onClick={onClose}>Cancel</Button><Button variant={danger ? 'danger' : 'primary'} loading={loading} onClick={onConfirm}>{confirmLabel}</Button></div></Modal>)
}
export function Drawer({ open, onClose, title, children }: { open: boolean; onClose: () => void; title: string; children: ReactNode }) {
  const closeRef = useRef(onClose); closeRef.current = onClose
  useEffect(() => { if (!open) return; const k = (e: KeyboardEvent) => e.key === 'Escape' && closeRef.current(); document.addEventListener('keydown', k); return () => document.removeEventListener('keydown', k) }, [open])
  if (!open) return null
  return (<div className="fixed inset-0 z-50"><div className="absolute inset-0 bg-black/40" onClick={onClose} />
    <aside role="dialog" aria-modal="true" aria-label={title} className="absolute inset-y-0 right-0 flex w-full max-w-md flex-col border-l bg-card shadow-xl">
      <div className="flex items-center justify-between border-b px-4 py-3"><h2 className="font-semibold">{title}</h2><button aria-label="Close" onClick={onClose} className="rounded-md p-1 hover:bg-muted"><X className="h-4 w-4" /></button></div>
      <div className="flex-1 overflow-y-auto">{children}</div></aside></div>)
}
export function Tooltip({ text, children }: { text: string; children: ReactNode }) {
  return (<span className="group relative inline-flex">{children}<span role="tooltip" className="pointer-events-none absolute left-1/2 top-full z-40 mt-1 -translate-x-1/2 whitespace-nowrap rounded-md bg-foreground px-2 py-1 text-xs text-background opacity-0 transition-opacity group-hover:opacity-100 group-focus-within:opacity-100">{text}</span></span>)
}
/** Native date input wrapper: keyboard accessible and localised by the browser. */
export function DatePicker({ label, value, onChange }: { label: string; value: string; onChange: (v: string) => void }) {
  return (<label className="block space-y-1.5"><span className="text-sm font-medium">{label}</span>
    <input type="date" value={value} onChange={(e) => onChange(e.target.value)} className="h-10 w-full rounded-lg border bg-card px-3 text-sm" /></label>)
}
interface Cmd { id: string; label: string; hint?: string; run: () => void }
export function CommandMenu({ open, onClose, extra }: { open: boolean; onClose: () => void; extra: Cmd[] }) {
  const nav = useNavigate(); const [q, setQ] = useState(''); const [i, setI] = useState(0)
  const go = (to: string) => () => nav(to)
  const all: Cmd[] = useMemo(() => [
    { id: 'new', label: 'Create order', hint: 'Orders', run: go('/orders?new=1') }, { id: 'ov', label: 'Overview', run: go('/dashboard') }, { id: 'or', label: 'Orders', run: go('/orders') },
    { id: 'di', label: 'Dispatch console', run: go('/dispatch') }, { id: 'lm', label: 'Live map', run: go('/live-map') }, { id: 'dr', label: 'Drivers', run: go('/drivers') },
    { id: 've', label: 'Vehicles', run: go('/vehicles') }, { id: 'an', label: 'Analytics', run: go('/analytics') }, { id: 'no', label: 'Notifications', run: go('/notifications') }, { id: 'se', label: 'Settings', run: go('/settings') }, ...extra,
  ], [extra]) // eslint-disable-line react-hooks/exhaustive-deps
  const list = all.filter((c) => c.label.toLowerCase().includes(q.toLowerCase()))
  useEffect(() => { setI(0) }, [q]); useEffect(() => { if (open) setQ('') }, [open])
  if (!open) return null
  const choose = (c?: Cmd) => { if (c) { onClose(); c.run() } }
  return (<div className="fixed inset-0 z-[70] flex items-start justify-center bg-black/40 p-4 pt-[15vh]" onMouseDown={(e) => e.target === e.currentTarget && onClose()}>
    <div role="dialog" aria-label="Command menu" className="w-full max-w-lg overflow-hidden rounded-xl border bg-card shadow-2xl">
      <div className="flex items-center gap-2 border-b px-3"><Search className="h-4 w-4 text-muted-foreground" />
        <input autoFocus value={q} onChange={(e) => setQ(e.target.value)} placeholder="Type a command or page…" aria-label="Search commands" className="h-12 flex-1 bg-transparent text-sm outline-none"
          onKeyDown={(e) => { if (e.key === 'Escape') onClose(); else if (e.key === 'ArrowDown') { e.preventDefault(); setI((x) => Math.min(x + 1, list.length - 1)) } else if (e.key === 'ArrowUp') { e.preventDefault(); setI((x) => Math.max(x - 1, 0)) } else if (e.key === 'Enter') choose(list[i]) }} /></div>
      <ul role="listbox" className="max-h-72 overflow-y-auto p-1">{list.length === 0 && <li className="px-3 py-6 text-center text-sm text-muted-foreground">No results</li>}
        {list.map((c, idx) => (<li key={c.id} role="option" aria-selected={idx === i}><button onMouseEnter={() => setI(idx)} onClick={() => choose(c)} className={cn('flex w-full items-center justify-between rounded-lg px-3 py-2 text-left text-sm', idx === i && 'bg-muted')}>{c.label}{c.hint && <span className="text-xs text-muted-foreground">{c.hint}</span>}</button></li>))}</ul>
    </div></div>)
}
