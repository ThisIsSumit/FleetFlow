import { createBrowserRouter } from 'react-router-dom'
import { GuestRoute, ProtectedRoute } from '@/auth/guards'
import { AppLayout } from '@/layouts/AppLayout'
import { Landing } from '@/pages/Landing'
import { Login, Signup } from '@/pages/AuthPages'
import { Dashboard } from '@/pages/Dashboard'
import { Orders } from '@/pages/Orders'
import { OrderDetail } from '@/pages/OrderDetail'
import { Drivers, Vehicles } from '@/pages/Fleet'
import { NotFound } from '@/pages/Misc'
import { DriverHome, DriverLayout } from '@/pages/DriverApp'
import { LiveMap } from '@/pages/LiveMap'
import { AnalyticsPage } from '@/pages/Analytics'
import { Dispatch } from '@/pages/Dispatch'
import { DriverDetail } from '@/pages/DriverDetail'
import { NotificationsPage } from '@/pages/Notifications'
import { SettingsPage } from '@/pages/Settings'
import { AcceptInvitation, ForgotPassword, ResetPassword, VerifyEmail } from '@/pages/AuthExtra'

export const router = createBrowserRouter([
  { path: '/', element: <Landing /> },
  { element: <GuestRoute />, children: [{ path: '/login', element: <Login /> }, { path: '/signup', element: <Signup /> },
    { path: '/forgot-password', element: <ForgotPassword /> }, { path: '/reset-password', element: <ResetPassword /> }, { path: '/accept-invitation', element: <AcceptInvitation /> }] },
  { path: '/verify-email', element: <VerifyEmail /> },
  { element: <ProtectedRoute />, children: [{ path: '/driver', element: <DriverLayout />, children: [{ index: true, element: <DriverHome /> }] }, { element: <AppLayout />, children: [
    { path: '/dashboard', element: <Dashboard /> }, { path: '/orders', element: <Orders /> }, { path: '/orders/:id', element: <OrderDetail /> },
    { path: '/drivers', element: <Drivers /> }, { path: '/drivers/:id', element: <DriverDetail /> }, { path: '/vehicles', element: <Vehicles /> },
    { path: '/dispatch', element: <Dispatch /> }, { path: '/live-map', element: <LiveMap /> }, { path: '/analytics', element: <AnalyticsPage /> },
    { path: '/settings', element: <SettingsPage /> }, { path: '/notifications', element: <NotificationsPage /> },
  ] }] },
  { path: '*', element: <NotFound /> },
])
