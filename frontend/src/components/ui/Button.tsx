import { forwardRef, type ButtonHTMLAttributes } from 'react'
import { Loader2 } from 'lucide-react'
import { cn } from '@/lib/cn'
type Variant = 'primary' | 'secondary' | 'ghost' | 'danger'
const variants: Record<Variant, string> = {
  primary: 'bg-primary text-primary-foreground hover:bg-primary/90',
  secondary: 'border bg-card hover:bg-muted',
  ghost: 'hover:bg-muted',
  danger: 'bg-danger text-white hover:bg-danger/90',
}
interface Props extends ButtonHTMLAttributes<HTMLButtonElement> { variant?: Variant; size?: 'sm' | 'md'; loading?: boolean }
export const Button = forwardRef<HTMLButtonElement, Props>(({ variant = 'primary', size = 'md', loading, disabled, className, children, ...p }, ref) => (
  <button ref={ref} disabled={disabled || loading} className={cn('inline-flex items-center justify-center gap-2 rounded-lg font-medium transition-colors disabled:pointer-events-none disabled:opacity-50',
    size === 'sm' ? 'h-8 px-3 text-sm' : 'h-10 px-4 text-sm', variants[variant], className)} {...p}>
    {loading && <Loader2 className="h-4 w-4 animate-spin" aria-hidden />}{children}
  </button>
))
