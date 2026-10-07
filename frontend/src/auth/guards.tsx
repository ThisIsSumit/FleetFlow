import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { useAuth } from '@/stores/auth'
import { LogoMark } from '@/components/Logo'
const Splash = () => <div className="grid min-h-screen place-items-center"><LogoMark className="h-10 w-10 animate-pulse" /></div>
export function ProtectedRoute() {
  const { user, ready } = useAuth(); const loc = useLocation()
  if (!ready) return <Splash />
  return user ? <Outlet /> : <Navigate to="/login" replace state={{ from: loc.pathname }} />
}
export function GuestRoute() {
  const { user, ready } = useAuth()
  if (!ready) return <Splash />
  return user ? <Navigate to="/dashboard" replace /> : <Outlet />
}
