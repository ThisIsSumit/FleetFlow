import type { ReactNode } from 'react'
import { AlertTriangle, Inbox } from 'lucide-react'
import { Button } from './Button'
export function EmptyState({ icon, title, description, action }: { icon?: ReactNode; title: string; description?: string; action?: ReactNode }) {
  return (<div className="flex flex-col items-center justify-center px-6 py-14 text-center">
    <div className="mb-3 rounded-full bg-muted p-3 text-muted-foreground">{icon ?? <Inbox className="h-5 w-5" />}</div>
    <h3 className="font-medium">{title}</h3>
    {description && <p className="mt-1 max-w-sm text-sm text-muted-foreground">{description}</p>}
    {action && <div className="mt-4">{action}</div>}
  </div>)
}
export function ErrorState({ message, onRetry }: { message: string; onRetry?: () => void }) {
  return (<div role="alert" className="flex flex-col items-center justify-center px-6 py-14 text-center">
    <div className="mb-3 rounded-full bg-danger/10 p-3 text-danger"><AlertTriangle className="h-5 w-5" /></div>
    <h3 className="font-medium">Something went wrong</h3>
    <p className="mt-1 max-w-sm text-sm text-muted-foreground">{message}</p>
    {onRetry && <Button variant="secondary" size="sm" className="mt-4" onClick={onRetry}>Try again</Button>}
  </div>)
}
