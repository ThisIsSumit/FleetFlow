export const fmtDateTime = (iso?: string) => (iso ? new Date(iso).toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' }) : '—')
export function timeAgo(iso: string) {
  const s = Math.max(0, Math.round((Date.now() - new Date(iso).getTime()) / 1000))
  if (s < 60) return 'just now'
  if (s < 3600) return `${Math.floor(s / 60)} min ago`
  if (s < 86400) return `${Math.floor(s / 3600)} h ago`
  return `${Math.floor(s / 86400)} d ago`
}
export const titleCase = (s: string) => s.toLowerCase().replace(/_/g, ' ').replace(/^\w/, (c) => c.toUpperCase())
export const shortId = (id: string) => id.slice(0, 8)
export const PLACES = [
  { label: 'Sector 17, Chandigarh', lat: 30.7398, lng: 76.7827 }, { label: 'Phase 5, Mohali', lat: 30.7046, lng: 76.7179 },
  { label: 'Sector 5, Panchkula', lat: 30.6942, lng: 76.8606 }, { label: 'Connaught Place, Delhi', lat: 28.6315, lng: 77.2167 },
  { label: 'Cyber Hub, Gurgaon', lat: 28.495, lng: 77.089 }, { label: 'Koramangala, Bengaluru', lat: 12.9352, lng: 77.6245 },
  { label: 'Andheri East, Mumbai', lat: 19.1197, lng: 72.8468 },
]
