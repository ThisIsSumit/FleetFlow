import { create } from 'zustand'
const apply = (dark: boolean) => document.documentElement.classList.toggle('dark', dark)
interface ThemeState { dark: boolean; toggle: () => void }
export const useTheme = create<ThemeState>((set, get) => ({
  dark: document.documentElement.classList.contains('dark'),
  toggle: () => { const dark = !get().dark; apply(dark); try { localStorage.setItem('ff-theme', dark ? 'dark' : 'light') } catch { /* private mode */ } set({ dark }) },
}))
