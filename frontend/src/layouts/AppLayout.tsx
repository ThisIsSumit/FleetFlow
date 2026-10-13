import { useEffect, useMemo, useState } from 'react'
import { Link, NavLink, Navigate, Outlet, useLocation, useNavigate } from 'react-router-dom'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { BarChart3, Bell, Bike, Command, Gauge, LogOut, Map, Menu, Package, Route, Settings, Truck, X } from 'lucide-react'
import { Logo } from '@/components/Logo'
import { CommandMenu, Drawer, Tooltip } from '@/components/ui/Extras'
import { Button } from '@/components/ui/Button'
import { NotificationList, useNotifications } from '@/pages/Notifications'
import { authApi } from '@/api/authApi'
import { errorMessage } from '@/lib/api'
import { toast } from '@/stores/toast'
import { useAuth } from '@/stores/auth'
import { cn } from '@/lib/cn'

const NAV = [
  { to: '/dashboard', label: 'Overview', icon: Gauge }, { to: '/orders', label: 'Orders', icon: Package }, { to: '/dispatch', label: 'Dispatch', icon: Route },
  { to: '/live-map', label: 'Live Map', icon: Map }, { to: '/drivers', label: 'Drivers', icon: Bike }, { to: '/vehicles', label: 'Vehicles', icon: Truck }, { to: '/analytics', label: 'Analytics', icon: BarChart3 },
]
function NavItem({ to, label, icon: Icon, onClick, badge }: { to: string; label: string; icon: typeof Gauge; onClick?: () => void; badge?: number }) {
  return (<NavLink to={to} onClick={onClick} className={({ isActive }) => cn('flex items-center gap-3 rounded-xl px-3 py-2 text-sm font-medium transition-all duration-300 ease-[cubic-bezier(0.32,0.72,0,1)]', isActive ? 'bg-white text-black' : 'text-white/50 hover:bg-[#1F1F1F] hover:text-white')}>
    <Icon className="h-4 w-4" />{label}{!!badge && <span className="ml-auto rounded-full bg-primary px-1.5 text-xs text-primary-foreground">{badge}</span>}</NavLink>)
}
function Crumbs() {
  const parts = useLocation().pathname.split('/').filter(Boolean)
  const label = (s: string) => (/^[0-9a-f-]{20,}$/i.test(s) ? 'Details' : s.replace(/-/g, ' ').replace(/^\w/, (c) => c.toUpperCase()))
  return (<nav aria-label="Breadcrumb" className="hidden items-center gap-1.5 text-sm text-white/45 sm:flex"><Link to="/dashboard" className="hover:text-white">FleetFlow</Link>
    {parts.map((p, i) => (<span key={i} className="flex items-center gap-1.5"><span>/</span>{i === parts.length - 1 ? <span className="text-white">{label(p)}</span> : <Link to={'/' + parts.slice(0, i + 1).join('/')} className="hover:text-white">{label(p)}</Link>}</span>))}</nav>)
}
function StaffLayout() {
  const [menu, setMenu] = useState(false), [cmd, setCmd] = useState(false), [bell, setBell] = useState(false)
  const { user, clear } = useAuth(); const nav = useNavigate(); const qc = useQueryClient()
  const notes = useNotifications(8); const unread = notes.data?.unread ?? 0
  const resend = useMutation({ mutationFn: authApi.resendVerification, onSuccess: () => toast.success('Verification email sent'), onError: (e) => toast.error(errorMessage(e)) })
  const logout = async () => { try { await authApi.logout() } finally { clear(); qc.clear(); nav('/login') } }
  useEffect(() => { const k = (e: KeyboardEvent) => { if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === 'k') { e.preventDefault(); setCmd((v) => !v) } }; document.addEventListener('keydown', k); return () => document.removeEventListener('keydown', k) }, [])
  const extra = useMemo(() => [{ id: 'out', label: 'Log out', run: logout }], []) // eslint-disable-line react-hooks/exhaustive-deps
  const sidebar = (<div className="flex h-full flex-col p-4">
    <Link to="/dashboard" className="mb-8 px-2 text-white"><Logo className="[&_rect]:fill-white [&_path]:stroke-black [&_circle]:fill-black" /></Link>
    <nav className="flex-1 space-y-1">{NAV.map((n) => <NavItem key={n.to} {...n} onClick={() => setMenu(false)} />)}</nav>
    <div className="space-y-1 border-t border-[#272727] pt-3"><NavItem to="/notifications" label="Notifications" icon={Bell} badge={unread} onClick={() => setMenu(false)} /><NavItem to="/settings" label="Settings" icon={Settings} onClick={() => setMenu(false)} /></div>
    <div className="mt-3 border-t border-[#272727] pt-3"><div className="px-2 text-sm"><p className="truncate font-medium text-white">{user?.name}</p><p className="truncate text-xs text-white/40">{user?.organizationName} · {user?.role}</p></div>
      <button onClick={logout} className="mt-2 flex w-full items-center gap-3 rounded-xl px-3 py-2 text-sm text-white/50 transition-colors hover:bg-[#1F1F1F] hover:text-white"><LogOut className="h-4 w-4" />Log out</button></div>
  </div>)
  return (<div className="app-theme min-h-screen bg-black font-[Manrope] text-white antialiased">
    <aside className="fixed inset-y-0 left-0 z-30 hidden w-60 border-r border-[#272727] bg-[#181818] lg:block">{sidebar}</aside>
    {menu && <div className="fixed inset-0 z-40 lg:hidden"><div className="absolute inset-0 bg-black/80 backdrop-blur-sm" onClick={() => setMenu(false)} /><aside className="absolute inset-y-0 left-0 w-64 border-r border-[#272727] bg-[#181818] shadow-2xl">{sidebar}</aside></div>}
    <div className="lg:pl-60">
      {user && !user.emailVerified && <div role="status" className="flex flex-wrap items-center justify-center gap-3 border-b border-[#5b4621] bg-[#1F1F1F] px-4 py-2 text-sm text-white/80">Please verify your email address.<Button size="sm" variant="secondary" loading={resend.isPending} onClick={() => resend.mutate()}>Resend link</Button></div>}
      <header className="sticky top-0 z-20 flex h-16 items-center gap-2 border-b border-[#272727] bg-black/80 px-4 backdrop-blur-xl sm:px-6">
        <button className="rounded-xl p-2 text-white/60 transition-colors hover:bg-[#1F1F1F] hover:text-white lg:hidden" aria-label={menu ? 'Close menu' : 'Open menu'} onClick={() => setMenu(!menu)}>{menu ? <X className="h-5 w-5" /> : <Menu className="h-5 w-5" />}</button>
        <Crumbs />
        <div className="ml-auto flex items-center gap-1">
          <button onClick={() => setCmd(true)} className="hidden items-center gap-2 rounded-full border border-[#272727] px-4 py-2 text-sm text-white/45 transition-colors hover:bg-[#181818] hover:text-white sm:flex"><Command className="h-3.5 w-3.5" />Search<kbd className="rounded border border-[#313131] px-1 text-xs">Ctrl K</kbd></button>
          <Tooltip text="Notifications"><button onClick={() => setBell(true)} aria-label={`Notifications, ${unread} unread`} className="relative rounded-xl p-2 text-white/60 transition-colors hover:bg-[#1F1F1F] hover:text-white"><Bell className="h-4 w-4" />{unread > 0 && <span className="absolute right-0.5 top-0.5 grid h-4 min-w-4 place-items-center rounded-full bg-white px-1 text-[10px] text-black">{unread > 9 ? '9+' : unread}</span>}</button></Tooltip>
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
