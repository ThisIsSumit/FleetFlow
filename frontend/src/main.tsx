import React, { useEffect } from 'react'
import ReactDOM from 'react-dom/client'
import { RouterProvider } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { router } from '@/routes/router'
import { refreshAccessToken } from '@/lib/api'
import { useAuth } from '@/stores/auth'
import { Toaster } from '@/components/ui/Toaster'
import './index.css'

const queryClient = new QueryClient({ defaultOptions: { queries: { retry: (n, e) => n < 2 && (e as { response?: { status: number } }).response?.status !== 401 && (e as { response?: { status: number } }).response?.status !== 404, staleTime: 5000, refetchOnWindowFocus: false } } })

function Root() {
  useEffect(() => { refreshAccessToken().catch(() => {}).finally(() => useAuth.getState().setReady()) }, []) // restore the session from the refresh cookie
  return (<QueryClientProvider client={queryClient}><RouterProvider router={router} /><Toaster /></QueryClientProvider>)
}
ReactDOM.createRoot(document.getElementById('root')!).render(<React.StrictMode><Root /></React.StrictMode>)
