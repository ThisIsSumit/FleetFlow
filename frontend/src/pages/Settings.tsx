import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { authApi } from '@/api/authApi'
import { driverApi } from '@/api/fleetApi'
import { dispatchApi, organizationApi, teamApi } from '@/api/moreApi'
import { errorMessage } from '@/lib/api'
import { useAuth } from '@/stores/auth'
import { useTheme } from '@/stores/theme'
import { toast } from '@/stores/toast'
import { Button } from '@/components/ui/Button'
import { Input, Select } from '@/components/ui/Field'
import { Badge, Card, Skeleton } from '@/components/ui/Card'
import { ConfirmDialog, Tabs } from '@/components/ui/Extras'
import { EmptyState, ErrorState } from '@/components/ui/States'
import { Modal } from '@/components/ui/Modal'
import { timeAgo, titleCase } from '@/utils/format'
import type { DispatchSettings, Member, Role } from '@/types'

const TABS = [{ id: 'profile', label: 'Profile' }, { id: 'organization', label: 'Organization' }, { id: 'team', label: 'Team' }, { id: 'dispatch', label: 'Dispatch rules' },
  { id: 'notifications', label: 'Notifications' }, { id: 'security', label: 'Security' }, { id: 'sessions', label: 'Sessions' }, { id: 'appearance', label: 'Appearance' }]
export function parseUA(ua?: string) {
  if (!ua) return 'Unknown device'
  const b = /Edg\//.test(ua) ? 'Edge' : /Chrome\//.test(ua) ? 'Chrome' : /Firefox\//.test(ua) ? 'Firefox' : /Safari\//.test(ua) ? 'Safari' : 'Browser'
  const os = /Windows/.test(ua) ? 'Windows' : /iPhone|iPad/.test(ua) ? 'iOS' : /Android/.test(ua) ? 'Android' : /Mac OS X|Macintosh/.test(ua) ? 'macOS' : /Linux/.test(ua) ? 'Linux' : 'Unknown OS'
  return `${b} • ${os}`
}
const Section = ({ title, children, desc }: { title: string; desc?: string; children: React.ReactNode }) => <Card className="p-5"><h2 className="font-medium">{title}</h2>{desc && <p className="mb-4 mt-1 text-sm text-muted-foreground">{desc}</p>}<div className={desc ? '' : 'mt-3'}>{children}</div></Card>

function Profile() {
  const user = useAuth((s) => s.user)!
  const resend = useMutation({ mutationFn: authApi.resendVerification, onSuccess: () => toast.success('Verification email sent'), onError: (e) => toast.error(errorMessage(e)) })
  return (<Section title="Profile"><dl className="divide-y text-sm">{[['Name', user.name], ['Email', user.email], ['Role', titleCase(user.role)]].map(([k, v]) => <div key={k} className="flex justify-between py-2"><dt className="text-muted-foreground">{k}</dt><dd>{v}</dd></div>)}
    <div className="flex items-center justify-between py-2"><dt className="text-muted-foreground">Email status</dt><dd className="flex items-center gap-2">{user.emailVerified ? <Badge tone="success">Verified</Badge> : <><Badge tone="warning">Not verified</Badge><Button size="sm" variant="secondary" loading={resend.isPending} onClick={() => resend.mutate()}>Resend link</Button></>}</dd></div></dl></Section>)
}
function Organization() {
  const user = useAuth((s) => s.user)!; const setSession = useAuth((s) => s.setSession); const token = useAuth((s) => s.accessToken)
  const org = useQuery({ queryKey: ['org'], queryFn: organizationApi.get }); const [name, setName] = useState(''); useEffect(() => { if (org.data) setName(org.data.name) }, [org.data])
  const save = useMutation({ mutationFn: () => organizationApi.rename(name), onSuccess: () => { toast.success('Organization renamed'); if (token) setSession({ accessToken: token, expiresIn: 0, user: { ...user, organizationName: name } }) }, onError: (e) => toast.error(errorMessage(e)) })
  const owner = user.role === 'OWNER'
  return (<Section title="Organization" desc="Your workspace. All data is isolated to this organization."><div className="max-w-sm space-y-3"><Input label="Name" value={name} disabled={!owner} onChange={(e) => setName(e.target.value)} hint={owner ? undefined : 'Only the owner can rename the organization.'} />
    {owner && <Button loading={save.isPending} disabled={!name.trim() || name === org.data?.name} onClick={() => save.mutate()}>Save</Button>}</div></Section>)
}
const inviteSchema = z.object({ email: z.string().email('Enter a valid email'), role: z.enum(['ADMIN', 'DISPATCHER', 'DRIVER']), driverId: z.string().optional() })
function Team() {
  const me = useAuth((s) => s.user)!; const qc = useQueryClient(); const [invite, setInvite] = useState(false); const [del, setDel] = useState<Member | null>(null)
  const members = useQuery({ queryKey: ['team'], queryFn: teamApi.members, enabled: me.role === 'OWNER' || me.role === 'ADMIN' }); const invites = useQuery({ queryKey: ['invitations'], queryFn: teamApi.invitations, enabled: me.role === 'OWNER' || me.role === 'ADMIN' })
  const refresh = () => { qc.invalidateQueries({ queryKey: ['team'] }); qc.invalidateQueries({ queryKey: ['invitations'] }) }
  const onErr = (e: unknown) => toast.error(errorMessage(e))
  const role = useMutation({ mutationFn: (a: { id: string; r: Role }) => teamApi.setRole(a.id, a.r), onSuccess: () => { toast.success('Role updated. They must sign in again.'); refresh() }, onError: onErr })
  const enable = useMutation({ mutationFn: (a: { id: string; e: boolean }) => teamApi.setEnabled(a.id, a.e), onSuccess: refresh, onError: onErr })
  const remove = useMutation({ mutationFn: (id: string) => teamApi.remove(id), onSuccess: () => { toast.success('Member removed'); setDel(null); refresh() }, onError: onErr })
  const { register, handleSubmit, reset, watch, formState: { errors } } = useForm<z.infer<typeof inviteSchema>>({ resolver: zodResolver(inviteSchema), defaultValues: { role: 'DISPATCHER' } })
  const inviteRole = watch('role'); const drivers = useQuery({ queryKey: ['drivers'], queryFn: () => driverApi.list(), enabled: invite && inviteRole === 'DRIVER' })
  const send = useMutation({ mutationFn: (v: z.infer<typeof inviteSchema>) => teamApi.invite(v.email, v.role, v.driverId), onSuccess: () => { toast.success('Invitation sent'); setInvite(false); reset(); refresh() }, onError: onErr })
  if (me.role !== 'OWNER' && me.role !== 'ADMIN') return <Card><EmptyState title="Owners and admins only" description="Ask an owner or admin to manage the team." /></Card>
  const grant: Role[] = me.role === 'OWNER' ? ['ADMIN', 'DISPATCHER', 'DRIVER'] : ['DISPATCHER', 'DRIVER']
  const locked = (m: Member) => m.role === 'OWNER' || m.id === me.id || (me.role === 'ADMIN' && m.role === 'ADMIN')
  return (<div className="space-y-4"><Card>
    <div className="flex items-center justify-between border-b p-4"><h2 className="font-medium">Team</h2><Button size="sm" onClick={() => setInvite(true)}>Invite member</Button></div>
    {members.isLoading ? <Skeleton className="m-4 h-24" /> : members.isError ? <ErrorState message={errorMessage(members.error)} onRetry={() => members.refetch()} /> :
      <ul className="divide-y">{members.data!.map((m) => (<li key={m.id} className="flex flex-wrap items-center gap-3 px-4 py-3 text-sm"><div className="min-w-0 flex-1"><p className="font-medium">{m.name}{m.id === me.id && <span className="text-muted-foreground"> (you)</span>}</p><p className="truncate text-xs text-muted-foreground">{m.email}</p></div>
        {!m.enabled && <Badge tone="warning">Disabled</Badge>}
        {locked(m) ? <Badge>{titleCase(m.role)}</Badge> : <select aria-label={`Role for ${m.name}`} className="h-8 rounded-md border bg-card px-2 text-xs" value={m.role} onChange={(e) => role.mutate({ id: m.id, r: e.target.value as Role })}>{[...new Set([m.role, ...grant])].map((r) => <option key={r} value={r}>{titleCase(r)}</option>)}</select>}
        {!locked(m) && <><Button size="sm" variant="secondary" onClick={() => enable.mutate({ id: m.id, e: !m.enabled })}>{m.enabled ? 'Disable' : 'Enable'}</Button><Button size="sm" variant="ghost" onClick={() => setDel(m)}>Remove</Button></>}</li>))}</ul>}
  </Card>
    {invites.data && invites.data.length > 0 && <Card><div className="border-b p-4"><h2 className="font-medium">Pending invitations</h2></div><ul className="divide-y">{invites.data.map((i) => <li key={i.id} className="flex justify-between px-4 py-3 text-sm"><span>{i.email}</span><span className="text-muted-foreground">{titleCase(i.role)} · expires {new Date(i.expiresAt).toLocaleDateString()}</span></li>)}</ul></Card>}
    <Modal open={invite} onClose={() => setInvite(false)} title="Invite a team member"><form noValidate className="space-y-4" onSubmit={handleSubmit((v) => send.mutate(v))}>
      <Input label="Email" type="email" error={errors.email?.message} {...register('email')} /><Select label="Role" {...register('role')}>{grant.map((r) => <option key={r} value={r}>{titleCase(r)}</option>)}</Select>
      {inviteRole === 'DRIVER' && <Select label="Link to driver profile" hint="The driver signs in to the driver app and works this profile." {...register('driverId')}><option value="">Not linked yet</option>{(drivers.data?.content ?? []).filter((d) => !d.hasLogin).map((d) => <option key={d.id} value={d.id}>{d.name}</option>)}</Select>}
      <div className="flex justify-end gap-2"><Button type="button" variant="secondary" onClick={() => setInvite(false)}>Cancel</Button><Button type="submit" loading={send.isPending}>Send invitation</Button></div></form></Modal>
    <ConfirmDialog open={!!del} danger title="Remove member" message={`${del?.name} will lose access immediately. This cannot be undone.`} confirmLabel="Remove" loading={remove.isPending} onConfirm={() => del && remove.mutate(del.id)} onClose={() => setDel(null)} /></div>)
}
function DispatchRules() {
  const role = useAuth((s) => s.user?.role); const qc = useQueryClient(); const q = useQuery({ queryKey: ['dispatch-settings'], queryFn: dispatchApi.settings, enabled: role === 'OWNER' || role === 'ADMIN' })
  const [f, setF] = useState<DispatchSettings | null>(null); useEffect(() => { if (q.data) setF(q.data) }, [q.data])
  const save = useMutation({ mutationFn: dispatchApi.saveSettings, onSuccess: (d) => { toast.success('Dispatch rules saved'); qc.setQueryData(['dispatch-settings'], d) }, onError: (e) => toast.error(errorMessage(e)) })
  if (role !== 'OWNER' && role !== 'ADMIN') return <Card><EmptyState title="Owners and admins only" /></Card>
  if (q.isLoading || !f) return <Skeleton className="h-64" />; if (q.isError) return <Card><ErrorState message={errorMessage(q.error)} onRetry={() => q.refetch()} /></Card>
  const editable = role === 'OWNER'; const w = f.weights; const sum = w.distance + w.availability + w.workload + w.vehicleMatch + w.rating
  const setW = (k: keyof typeof w, v: number) => setF({ ...f, weights: { ...w, [k]: v } })
  const wf: [keyof typeof w, string][] = [['distance', 'Distance'], ['availability', 'Availability'], ['workload', 'Workload'], ['vehicleMatch', 'Vehicle match'], ['rating', 'Rating']]
  return (<Section title="Dispatch rules" desc="A deterministic weighted score, not machine learning. Weights are normalised, so only their proportions matter.">
    <div className="grid max-w-2xl gap-4 sm:grid-cols-2"><Input label="Search radius (km)" type="number" step="0.5" disabled={!editable} value={f.searchRadiusKm} onChange={(e) => setF({ ...f, searchRadiusKm: Number(e.target.value) })} />
      <Input label="Max driver workload" type="number" disabled={!editable} value={f.maxDriverWorkload} onChange={(e) => setF({ ...f, maxDriverWorkload: Number(e.target.value) })} hint="Concurrent active orders per driver." /></div>
    <fieldset className="mt-5 max-w-2xl"><legend className="mb-2 text-sm font-medium">Scoring weights</legend><div className="grid gap-4 sm:grid-cols-3">{wf.map(([k, l]) => <Input key={k} label={`${l}${sum > 0 ? ` (${Math.round((w[k] / sum) * 100)}%)` : ''}`} type="number" step="0.05" min={0} disabled={!editable} value={w[k]} onChange={(e) => setW(k, Number(e.target.value))} />)}</div></fieldset>
    <label className="mt-5 flex items-center gap-2 text-sm"><input type="checkbox" disabled={!editable} checked={f.autoDispatchEnabled} onChange={(e) => setF({ ...f, autoDispatchEnabled: e.target.checked })} />Auto-dispatch enabled</label>
    {editable ? <Button className="mt-5" loading={save.isPending} disabled={sum <= 0} onClick={() => save.mutate(f)}>Save rules</Button> : <p className="mt-5 text-sm text-muted-foreground">Only the owner can change dispatch rules.</p>}</Section>)
}
function Security() {
  const nav = useNavigate(); const clear = useAuth((s) => s.clear); const qc = useQueryClient(); const [open, setOpen] = useState(false)
  const all = useMutation({ mutationFn: authApi.logoutAll, onSuccess: () => { clear(); qc.clear(); nav('/login') }, onError: (e) => toast.error(errorMessage(e)) })
  return (<Section title="Security" desc="Access tokens last 15 minutes. Refresh tokens rotate on every use and live in an HttpOnly cookie."><div className="space-y-4 text-sm">
    <div className="flex flex-wrap items-center justify-between gap-3 rounded-lg border p-4"><div><p className="font-medium">Sign out everywhere</p><p className="text-muted-foreground">Revokes every active session on all devices, including this one.</p></div><Button variant="danger" onClick={() => setOpen(true)}>Sign out all devices</Button></div>
    <div className="rounded-lg border p-4"><p className="font-medium">Change password</p><p className="text-muted-foreground">Use the reset flow: <Link to="/forgot-password" className="text-primary hover:underline">send yourself a reset link</Link> (sign out first).</p></div></div>
    <ConfirmDialog open={open} danger title="Sign out of all devices?" message="You'll need to sign in again everywhere." confirmLabel="Sign out all" loading={all.isPending} onConfirm={() => all.mutate()} onClose={() => setOpen(false)} /></Section>)
}
function Sessions() {
  const qc = useQueryClient(); const q = useQuery({ queryKey: ['sessions'], queryFn: authApi.sessions, refetchInterval: 30000 })
  const revoke = useMutation({ mutationFn: authApi.revokeSession, onSuccess: () => { toast.success('Session revoked'); qc.invalidateQueries({ queryKey: ['sessions'] }) }, onError: (e) => toast.error(errorMessage(e)) })
  return (<Card><div className="border-b p-4"><h2 className="font-medium">Active sessions</h2></div>{q.isLoading ? <Skeleton className="m-4 h-20" /> : q.isError ? <ErrorState message={errorMessage(q.error)} onRetry={() => q.refetch()} /> :
    <ul className="divide-y">{q.data!.map((s) => (<li key={s.id} className="flex flex-wrap items-center gap-3 px-4 py-3 text-sm"><div className="min-w-0 flex-1"><p className="font-medium">{parseUA(s.userAgent)} {s.current && <Badge tone="success" className="ml-1">Current session</Badge>}</p>
      <p className="text-xs text-muted-foreground">Last active: {timeAgo(s.lastUsedAt)} · IP {s.ip ?? 'unknown'}</p></div>{!s.current && <Button size="sm" variant="secondary" loading={revoke.isPending && revoke.variables === s.id} onClick={() => revoke.mutate(s.id)}>Revoke</Button>}</li>))}</ul>}</Card>)
}
function Appearance() { const { dark, toggle } = useTheme(); return (<Section title="Appearance" desc="Your choice is saved in this browser."><Button variant="secondary" onClick={toggle}>Switch to {dark ? 'light' : 'dark'} mode</Button></Section>) }
export function SettingsPage() {
  const [tab, setTab] = useState('profile')
  const body: Record<string, React.ReactNode> = { profile: <Profile />, organization: <Organization />, team: <Team />, dispatch: <DispatchRules />, sessions: <Sessions />, security: <Security />, appearance: <Appearance />,
    notifications: <Section title="Notifications" desc="Assignments, completions, delays and alerts appear in your notification centre."><Link to="/notifications" className="text-sm font-medium text-primary hover:underline">Open notification centre</Link><p className="mt-3 text-sm text-muted-foreground">Per-type delivery preferences are not configurable yet.</p></Section> }
  return (<div className="space-y-5"><div><h1 className="text-2xl font-semibold tracking-tight">Settings</h1></div><Tabs tabs={TABS} value={tab} onChange={setTab} />{body[tab]}</div>)
}
