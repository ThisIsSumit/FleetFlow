import { useEffect, useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { Button } from '@/components/ui/Button'
import { Input } from '@/components/ui/Field'
import { Shell } from './AuthPages'
import { authApi } from '@/api/authApi'
import { errorMessage } from '@/lib/api'
import { useAuth } from '@/stores/auth'

const emailSchema = z.object({ email: z.string().email('Enter a valid email') })
export function ForgotPassword() {
  const [sent, setSent] = useState(false); const [err, setErr] = useState('')
  const { register, handleSubmit, formState: { errors, isSubmitting } } = useForm<z.infer<typeof emailSchema>>({ resolver: zodResolver(emailSchema) })
  return (<Shell title="Reset your password" subtitle="We'll email you a link that works for 30 minutes." footer={<Link to="/login" className="font-medium text-white hover:text-white/70 hover:underline">Back to sign in</Link>}>
    {sent ? <p role="status" className="rounded-xl border border-[#313131] bg-[#1F1F1F] p-4 text-sm text-white/70">If an account exists for that email, a reset link is on its way. In development the email is written to the backend log.</p> :
      <form noValidate className="space-y-4" onSubmit={handleSubmit(async (v) => { setErr(''); try { await authApi.forgotPassword(v.email); setSent(true) } catch (e) { setErr(errorMessage(e)) } })}>
        <Input label="Email" type="email" autoComplete="email" error={errors.email?.message} {...register('email')} />{err && <p role="alert" className="text-sm text-red-300">{err}</p>}
        <Button type="submit" className="mt-2 h-12 w-full rounded-full bg-white text-base font-semibold text-black transition-transform duration-300 ease-[cubic-bezier(0.32,0.72,0,1)] hover:scale-[1.02] hover:bg-white/90 active:scale-95" loading={isSubmitting}>Send reset link</Button></form>}</Shell>)
}
const pwSchema = z.object({ password: z.string().min(10, 'Use at least 10 characters').max(72), confirm: z.string() }).refine((v) => v.password === v.confirm, { path: ['confirm'], message: 'Passwords do not match' })
export function ResetPassword() {
  const token = useSearchParams()[0].get('token') ?? ''; const nav = useNavigate(); const [err, setErr] = useState('')
  const { register, handleSubmit, formState: { errors, isSubmitting } } = useForm<z.infer<typeof pwSchema>>({ resolver: zodResolver(pwSchema) })
  return (<Shell title="Choose a new password" subtitle="You'll be signed out of every device." footer={<Link to="/login" className="font-medium text-white hover:text-white/70 hover:underline">Back to sign in</Link>}>
    {!token ? <p role="alert" className="text-sm text-red-300">This reset link is missing its token. Request a new one.</p> :
      <form noValidate className="space-y-4" onSubmit={handleSubmit(async (v) => { setErr(''); try { await authApi.resetPassword(token, v.password); nav('/login', { replace: true }) } catch (e) { setErr(errorMessage(e)) } })}>
        <Input label="New password" type="password" autoComplete="new-password" error={errors.password?.message} {...register('password')} /><Input label="Confirm password" type="password" autoComplete="new-password" error={errors.confirm?.message} {...register('confirm')} />
        {err && <p role="alert" className="text-sm text-red-300">{err}</p>}<Button type="submit" className="mt-2 h-12 w-full rounded-full bg-white text-base font-semibold text-black transition-transform duration-300 ease-[cubic-bezier(0.32,0.72,0,1)] hover:scale-[1.02] hover:bg-white/90 active:scale-95" loading={isSubmitting}>Update password</Button></form>}</Shell>)
}
export function VerifyEmail() {
  const token = useSearchParams()[0].get('token') ?? ''; const [state, setState] = useState<'working' | 'ok' | 'bad'>('working'); const user = useAuth((s) => s.user); const setSession = useAuth((s) => s.setSession); const accessToken = useAuth((s) => s.accessToken)
  useEffect(() => { let alive = true; authApi.verifyEmail(token).then(() => { if (alive) { setState('ok'); if (user && accessToken) setSession({ accessToken, expiresIn: 0, user: { ...user, emailVerified: true } }) } }).catch(() => alive && setState('bad')); return () => { alive = false } }, [token]) // eslint-disable-line react-hooks/exhaustive-deps
  return (<Shell title="Email verification" subtitle="" footer={<Link to={user ? '/dashboard' : '/login'} className="font-medium text-white hover:text-white/70 hover:underline">{user ? 'Go to dashboard' : 'Sign in'}</Link>}>
    <p role={state === 'bad' ? 'alert' : 'status'} className={state === 'bad' ? 'text-sm text-red-300' : 'rounded-xl border border-[#313131] bg-[#1F1F1F] p-4 text-sm text-white/70'}>{state === 'working' ? 'Verifying…' : state === 'ok' ? 'Your email is verified. Thank you.' : 'This link is invalid or has expired. Sign in and request a new one from Settings.'}</p></Shell>)
}
const inviteSchema = z.object({ name: z.string().min(1, 'Enter your name').max(120), password: z.string().min(10, 'Use at least 10 characters').max(72) })
export function AcceptInvitation() {
  const token = useSearchParams()[0].get('token') ?? ''; const nav = useNavigate(); const setSession = useAuth((s) => s.setSession); const [err, setErr] = useState('')
  const { register, handleSubmit, formState: { errors, isSubmitting } } = useForm<z.infer<typeof inviteSchema>>({ resolver: zodResolver(inviteSchema) })
  return (<Shell title="Join your team" subtitle="Create your password to accept the invitation." footer={<Link to="/login" className="font-medium text-white hover:text-white/70 hover:underline">Already have an account?</Link>}>
    {!token ? <p role="alert" className="text-sm text-red-300">This invitation link is missing its token.</p> :
      <form noValidate className="space-y-4" onSubmit={handleSubmit(async (v) => { setErr(''); try { setSession(await authApi.acceptInvitation({ token, ...v })); nav('/dashboard', { replace: true }) } catch (e) { setErr(errorMessage(e)) } })}>
        <Input label="Your name" autoComplete="name" error={errors.name?.message} {...register('name')} /><Input label="Password" type="password" autoComplete="new-password" error={errors.password?.message} {...register('password')} />
        {err && <p role="alert" className="text-sm text-red-300">{err}</p>}<Button type="submit" className="mt-2 h-12 w-full rounded-full bg-white text-base font-semibold text-black transition-transform duration-300 ease-[cubic-bezier(0.32,0.72,0,1)] hover:scale-[1.02] hover:bg-white/90 active:scale-95" loading={isSubmitting}>Join organization</Button></form>}</Shell>)
}
