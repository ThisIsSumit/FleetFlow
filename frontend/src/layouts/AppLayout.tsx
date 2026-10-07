import { useEffect, useMemo, useState } from 'react'
import { Link, NavLink, Navigate, Outlet, useLocation, useNavigate } from 'react-router-dom'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { BarChart3, Bell, Bike, Command, Gauge, LogOut, Map, Menu, Moon, Package, Route, Settings, Sun, Truck, X } from 'lucide-react'
import { Logo } from '@/components/Logo'
import { CommandMenu, Drawer, Tooltip } from '@/components/ui/Extras'
import { Button } from '@/components/ui/Button'
import { NotificationList, useNotifications } from '@/pages/Notifications'
import { authApi } from '@/api/authApi'
import { errorMessage } from '@/lib/api'
import { toast } from '@/stores/toast'
import { useAuth } from '@/stores/auth'
import { useTheme } from '@/stores/theme'
import { cn } from '@/lib/cn'

const NAV = [
  { to: '/dashboard', label: 'Overview', icon: Gauge }, { to: '/orders', label: 'Orders', icon: Package }, { to: '/dispatch', label: 'Dispatch', icon: Route },
  { to: '/live-map', label: 'Live Map', icon: Map }, { to: '/drivers', label: 'Drivers', icon: Bike }, { to: '/vehicles', label: 'Vehicles', icon: Truck }, { to: '/analytics', label: 'Analytics', icon: BarChart3 },
]
function NavItem({ to, label, icon: Icon, onClick, badge }: { to: string; label: string; icon: typeof Gauge; onClick?: () => void; badge?: number }) {
  return (<NavLink to={to} onClick={onClick} className={({ isActive }) => cn('flex items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium transition-colors', isActive ? 'bg-primary/10 text-primary' : 'text-muted-foreground hover:bg-muted hover:text-foreground')}>
    <Icon className="h-4 w-4" />{label}{!!badge && <span className="ml-auto rounded-full bg-primary px-1.5 text-xs text-primary-foreground">{badge}</span>}</NavLink>)
}
function Crumbs() {
  const parts = useLocation().pathname.split('/').filter(Boolean)
  const label = (s: string) => (/^[0-9a-f-]{20,}$/i.test(s) ? 'Details' : s.replace(/-/g, ' ').replace(/^\w/, (c) => c.toUpperCase()))
  return (<nav aria-label="Breadcrumb" className="hidden items-center gap-1.5 text-sm text-muted-foreground sm:flex"><Link to="/dashboard" className="hover:text-foreground">FleetFlow</Link>
    {parts.map((p, i) => (<span key={i} className="flex items-center gap-1.5"><span>/</span>{i === parts.length - 1 ? <span className="text-foreground">{label(p)}</span> : <Link to={'/' + parts.slice(0, i + 1).join('/')} className="hover:text-foreground">{label(p)}</Link>}</span>))}</nav>)
}
function StaffLayout() {
  const [menu, setMenu] = useState(false), [cmd, setCmd] = useState(false), [bell, setBell] = useState(false)
  const { user, clear } = useAuth(); const { dark, toggle } = useTheme(); const nav = useNavigate(); const qc = useQueryClient()
  const notes = useNotifications(8); const unread = notes.data?.unread ?? 0
  const resend = useMutation({ mutationFn: authApi.resendVerification, onSuccess: () => toast.success('Verification email sent'), onError: (e) => toast.error(errorMessage(e)) })
  const logout = async () => { try { await authApi.logout() } finally { clear(); qc.clear(); nav('/login') } }
  useEffect(() => { const k = (e: KeyboardEvent) => { if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === 'k') { e.preventDefault(); setCmd((v) => !v) } }; document.addEventListener('keydown', k); return () => document.removeEventListener('keydown', k) }, [])
  const extra = useMemo(() => [{ id: 'theme', label: dark ? 'Switch to light mode' : 'Switch to dark mode', run: toggle }, { id: 'out', label: 'Log out', run: logout }], [dark]) // eslint-disable-line react-hooks/exhaustive-deps
  const sidebar = (<div className="flex h-full flex-col p-4">
    <Link to="/dashboard" className="mb-6 px-2"><Logo /></Link>
    <nav className="flex-1 space-y-1">{NAV.map((n) => <NavItem key={n.to} {...n} onClick={() => setMenu(false)} />)}</nav>
    <div className="space-y-1 border-t pt-3"><NavItem to="/notifications" label="Notifications" icon={Bell} badge={unread} onClick={() => setMenu(false)} /><NavItem to="/settings" label="Settings" icon={Settings} onClick={() => setMenu(false)} /></div>
    <div className="mt-3 border-t pt-3"><div className="px-2 text-sm"><p className="truncate font-medium">{user?.name}</p><p className="truncate text-xs text-muted-foreground">{user?.organizationName} · {user?.role}</p></div>
      <button onClick={logout} className="mt-2 flex w-full items-center gap-3 rounded-lg px-3 py-2 text-sm text-muted-foreground hover:bg-muted hover:text-foreground"><LogOut className="h-4 w-4" />Log out</button></div>
  </div>)
  return (<div className="min-h-screen">
    <aside className="fixed inset-y-0 left-0 z-30 hidden w-60 border-r bg-card lg:block">{sidebar}</aside>
    {menu && <div className="fixed inset-0 z-40 lg:hidden"><div className="absolute inset-0 bg-black/50" onClick={() => setMenu(false)} /><aside className="absolute inset-y-0 left-0 w-64 bg-card shadow-xl">{sidebar}</aside></div>}
    <div className="lg:pl-60">
      {user && !user.emailVerified && <div role="status" className="flex flex-wrap items-center justify-center gap-3 border-b bg-warning/10 px-4 py-2 text-sm">Please verify your email address.<Button size="sm" variant="secondary" loading={resend.isPending} onClick={() => resend.mutate()}>Resend link</Button></div>}
      <header className="sticky top-0 z-20 flex h-14 items-center gap-2 border-b bg-background/80 px-4 backdrop-blur">
        <button className="rounded-md p-2 hover:bg-muted lg:hidden" aria-label={menu ? 'Close menu' : 'Open menu'} onClick={() => setMenu(!menu)}>{menu ? <X className="h-5 w-5" /> : <Menu className="h-5 w-5" />}</button>
        <Crumbs />
        <div className="ml-auto flex items-center gap-1">
          <button onClick={() => setCmd(true)} className="hidden items-center gap-2 rounded-lg border px-3 py-1.5 text-sm text-muted-foreground hover:bg-muted sm:flex"><Command className="h-3.5 w-3.5" />Search<kbd className="rounded border px-1 text-xs">Ctrl K</kbd></button>
          <Tooltip text="Notifications"><button onClick={() => setBell(true)} aria-label={`Notifications, ${unread} unread`} className="relative rounded-md p-2 hover:bg-muted"><Bell className="h-4 w-4" />{unread > 0 && <span className="absolute right-0.5 top-0.5 grid h-4 min-w-4 place-items-center rounded-full bg-primary px-1 text-[10px] text-primary-foreground">{unread > 9 ? '9+' : unread}</span>}</button></Tooltip>
          <Tooltip text="Toggle theme"><button onClick={toggle} aria-label="Toggle theme" className="rounded-md p-2 hover:bg-muted">{dark ? <Sun className="h-4 w-4" /> : <Moon className="h-4 w-4" />}</button></Tooltip>
        </div>
      </header>
      <main className="mx-auto max-w-7xl p-4 sm:p-6"><Outlet /></main>
    </div>
    <Drawer open={bell} onClose={() => setBell(false)} title="Notifications"><NotificationList limit={30} onNavigate={() => setBell(false)} /></Drawer>
    <CommandMenu open={cmd} onClose={() => setCmd(false)} extra={extra} />
  </div>)
}

/** Drivers use the mobile driver app; everything under this layout is for staff. */
export function AppLayout() {
  const role = useAuth((s) => s.user?.role)
  return role === 'DRIVER' ? <Navigate to="/driver" replace /> : <StaffLayout />
}
