import { useEffect, useRef, type ReactNode } from 'react'
import { X } from 'lucide-react'
export function Modal({ open, onClose, title, children, wide }: { open: boolean; onClose: () => void; title: string; children: ReactNode; wide?: boolean }) {
  const ref = useRef<HTMLDivElement>(null)
  const closeRef = useRef(onClose); closeRef.current = onClose // keep effect deps stable so re-renders never steal focus
  useEffect(() => {
    if (!open) return
    const prev = document.activeElement as HTMLElement | null
    ref.current?.focus()
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && closeRef.current()
    document.addEventListener('keydown', onKey)
    return () => { document.removeEventListener('keydown', onKey); prev?.focus() }
  }, [open])
  if (!open) return null
  return (<div className="fixed inset-0 z-50 flex items-start justify-center overflow-y-auto bg-black/50 p-4 sm:p-10" onMouseDown={(e) => e.target === e.currentTarget && onClose()}>
    <div ref={ref} role="dialog" aria-modal="true" aria-label={title} tabIndex={-1} className={`w-full rounded-xl border bg-card shadow-xl ${wide ? 'max-w-2xl' : 'max-w-md'}`}>
      <div className="flex items-center justify-between border-b px-5 py-3"><h2 className="font-semibold">{title}</h2>
        <button aria-label="Close" onClick={onClose} className="rounded-md p-1 hover:bg-muted"><X className="h-4 w-4" /></button></div>
      <div className="p-5">{children}</div>
    </div>
  </div>)
}
