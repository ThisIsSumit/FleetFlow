import { cn } from '@/lib/cn'
export function LogoMark({ className }: { className?: string }) {
  return (<svg viewBox="0 0 32 32" className={cn('h-8 w-8', className)} aria-hidden>
    <rect width="32" height="32" rx="8" className="fill-primary" />
    <path d="M7 21c5 0 4-10 9-10s4 10 9 10" fill="none" stroke="white" strokeWidth="3" strokeLinecap="round" /><circle cx="25" cy="21" r="2" fill="white" />
  </svg>)
}
export const Logo = ({ className }: { className?: string }) => (
  <span className={cn('inline-flex items-center gap-2 text-lg font-semibold tracking-tight', className)}><LogoMark />FleetFlow</span>)
