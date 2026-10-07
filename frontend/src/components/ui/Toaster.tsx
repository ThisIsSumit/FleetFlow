import { CheckCircle2, Info, XCircle } from 'lucide-react'
import { useToasts } from '@/stores/toast'
const icon = { success: <CheckCircle2 className="h-4 w-4 text-success" />, error: <XCircle className="h-4 w-4 text-danger" />, info: <Info className="h-4 w-4 text-primary" /> }
export function Toaster() {
  const { items, dismiss } = useToasts()
  return (<div aria-live="polite" className="fixed bottom-4 right-4 z-[60] flex w-80 max-w-[calc(100vw-2rem)] flex-col gap-2">
    {items.map((t) => (<button key={t.id} onClick={() => dismiss(t.id)} className="flex items-start gap-2 rounded-lg border bg-card p-3 text-left text-sm shadow-lg">{icon[t.kind]}<span>{t.message}</span></button>))}
  </div>)
}
