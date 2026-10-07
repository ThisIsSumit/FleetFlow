import { create } from 'zustand'
import type { AuthResponse, User } from '@/types'
/** The access token lives in memory only. The refresh token is an HttpOnly cookie the JS never sees. */
interface AuthState { accessToken: string | null; user: User | null; ready: boolean; setSession: (r: AuthResponse) => void; clear: () => void; setReady: () => void }
export const useAuth = create<AuthState>((set) => ({
  accessToken: null, user: null, ready: false,
  setSession: (r) => set({ accessToken: r.accessToken, user: r.user }),
  clear: () => set({ accessToken: null, user: null }),
  setReady: () => set({ ready: true }),
}))
