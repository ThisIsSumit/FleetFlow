import { forwardRef, useId, type InputHTMLAttributes, type SelectHTMLAttributes } from 'react'
import { cn } from '@/lib/cn'
const base = 'h-10 w-full rounded-xl border bg-card px-3 text-sm placeholder:text-muted-foreground transition-colors focus:border-foreground/50 disabled:opacity-50'
interface FieldProps { label?: string; error?: string; hint?: string }
function Wrap({ id, label, error, hint, children }: FieldProps & { id: string; children: React.ReactNode }) {
  return (<div className="space-y-1.5">
    {label && <label htmlFor={id} className="text-sm font-medium">{label}</label>}
    {children}
    {error ? <p role="alert" className="text-xs text-danger">{error}</p> : hint ? <p className="text-xs text-muted-foreground">{hint}</p> : null}
  </div>)
}
export const Input = forwardRef<HTMLInputElement, InputHTMLAttributes<HTMLInputElement> & FieldProps>(({ label, error, hint, className, ...p }, ref) => {
  const id = useId()
  return <Wrap id={id} label={label} error={error} hint={hint}><input id={id} ref={ref} aria-invalid={!!error} className={cn(base, error && 'border-danger', className)} {...p} /></Wrap>
})
export const Select = forwardRef<HTMLSelectElement, SelectHTMLAttributes<HTMLSelectElement> & FieldProps>(({ label, error, hint, className, children, ...p }, ref) => {
  const id = useId()
  return <Wrap id={id} label={label} error={error} hint={hint}><select id={id} ref={ref} aria-invalid={!!error} className={cn(base, error && 'border-danger', className)} {...p}>{children}</select></Wrap>
})
