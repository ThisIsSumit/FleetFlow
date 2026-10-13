import { Link } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Bell, CheckCheck } from 'lucide-react'
import { notificationApi } from '@/api/moreApi'
import { errorMessage } from '@/lib/api'
import { Button } from '@/components/ui/Button'
import { Skeleton } from '@/components/ui/Card'
import { EmptyState, ErrorState } from '@/components/ui/States'
import { timeAgo } from '@/utils/format'

export const useNotifications = (limit = 30) => useQuery({ queryKey: ['notifications', limit], queryFn: () => notificationApi.list(limit), refetchInterval: 20000 })
export function NotificationList({ limit = 30, onNavigate }: { limit?: number; onNavigate?: () => void }) {
  const qc = useQueryClient(); const q = useNotifications(limit); const inv = () => qc.invalidateQueries({ queryKey: ['notifications'] })
  const read = useMutation({ mutationFn: notificationApi.read, onSuccess: inv }); const all = useMutation({ mutationFn: notificationApi.readAll, onSuccess: inv })
  if (q.isLoading) return <div className="space-y-2 p-4">{[0, 1, 2].map((i) => <Skeleton key={i} className="h-14" />)}</div>
  if (q.isError) return <ErrorState message={errorMessage(q.error)} onRetry={() => q.refetch()} />
  const d = q.data!
  return (<div>
    <div className="flex items-center justify-between border-b px-4 py-2 text-sm"><span className="text-muted-foreground">{d.unread} unread</span>
      <Button variant="ghost" size="sm" disabled={d.unread === 0} loading={all.isPending} onClick={() => all.mutate()}><CheckCheck className="h-4 w-4" />Mark all as read</Button></div>
    {d.items.length === 0 ? <EmptyState icon={<Bell className="h-5 w-5" />} title="You're all caught up" description="Assignments, completions and alerts will appear here." /> :
      <ul className="divide-y">{d.items.map((n) => (<li key={n.id} className={n.read ? '' : 'bg-primary/5'}>
        <div className="flex items-start gap-3 px-4 py-3 text-sm"><span className={`mt-1.5 h-2 w-2 shrink-0 rounded-full ${n.read ? 'bg-transparent' : 'bg-primary'}`} aria-label={n.read ? 'Read' : 'Unread'} />
          <div className="min-w-0 flex-1"><p className="font-medium">{n.title}</p><p className="text-muted-foreground">{n.message}</p>
            <p className="mt-1 text-xs text-muted-foreground">{timeAgo(n.createdAt)}{n.orderId && <> · <Link to={`/orders/${n.orderId}`} onClick={() => { read.mutate(n.id); onNavigate?.() }} className="text-primary hover:underline">View order</Link></>}</p></div>
          {!n.read && <button className="text-xs text-primary hover:underline" onClick={() => read.mutate(n.id)}>Mark read</button>}</div></li>))}</ul>}
  </div>)
}
export const NotificationsPage = () => (<div className="space-y-4"><h1 className="text-2xl font-semibold tracking-tight">Notifications</h1><div className="rounded-2xl border border-[#272727] bg-[#181818] shadow-sm shadow-black/20"><NotificationList limit={100} /></div></div>)
