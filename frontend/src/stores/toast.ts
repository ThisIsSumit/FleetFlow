import { create } from 'zustand'
export interface ToastItem { id: number; kind: 'success' | 'error' | 'info'; message: string }
let n = 0
export const useToasts = create<{ items: ToastItem[]; push: (k: ToastItem['kind'], m: string) => void; dismiss: (id: number) => void }>((set) => ({
  items: [],
  push: (kind, message) => { const id = ++n; set((s) => ({ items: [...s.items, { id, kind, message }] })); setTimeout(() => set((s) => ({ items: s.items.filter((t) => t.id !== id) })), 4500) },
  dismiss: (id) => set((s) => ({ items: s.items.filter((t) => t.id !== id) })),
}))
export const toast = { success: (m: string) => useToasts.getState().push('success', m), error: (m: string) => useToasts.getState().push('error', m), info: (m: string) => useToasts.getState().push('info', m) }
