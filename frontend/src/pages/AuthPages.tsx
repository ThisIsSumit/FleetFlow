import { useState, type ReactNode } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { Logo } from '@/components/Logo'
import { Button } from '@/components/ui/Button'
import { Input } from '@/components/ui/Field'
import { authApi } from '@/api/authApi'
import { errorMessage } from '@/lib/api'
import { useAuth } from '@/stores/auth'

export function Shell({ title, subtitle, children, footer }: { title: string; subtitle: string; children: ReactNode; footer: ReactNode }) {
  return (<div className="grid min-h-screen lg:grid-cols-2">
    <div className="hidden flex-col justify-between bg-primary p-10 text-primary-foreground lg:flex">
      <Link to="/" className="text-white"><Logo className="[&_rect]:fill-white [&_path]:stroke-[hsl(221_83%_50%)] [&_circle]:fill-[hsl(221_83%_50%)]" /></Link>
      <div><h2 className="text-3xl font-semibold leading-tight">Intelligent dispatch infrastructure for modern delivery operations.</h2>
        <p className="mt-3 max-w-md text-sm opacity-80">Automatic driver assignment, live fleet visibility, and an event-driven backend built for concurrent workloads.</p></div>
      <p className="text-xs opacity-70">Demo environment. Do not enter real customer data.</p>
    </div>
    <div className="flex items-center justify-center p-6"><div className="w-full max-w-sm">
      <Link to="/" className="mb-8 block lg:hidden"><Logo /></Link>
      <h1 className="text-2xl font-semibold tracking-tight">{title}</h1><p className="mb-6 mt-1 text-sm text-muted-foreground">{subtitle}</p>
      {children}<p className="mt-6 text-center text-sm text-muted-foreground">{footer}</p>
    </div></div>
  </div>)
}
const loginSchema = z.object({ email: z.string().email('Enter a valid email'), password: z.string().min(1, 'Enter your password') })
export function Login() {
  const nav = useNavigate(); const from = (useLocation().state as { from?: string } | null)?.from ?? '/dashboard'
  const setSession = useAuth((s) => s.setSession); const [err, setErr] = useState('')
  const { register, handleSubmit, formState: { errors, isSubmitting } } = useForm<z.infer<typeof loginSchema>>({ resolver: zodResolver(loginSchema) })
  const submit = handleSubmit(async (v) => { setErr(''); try { setSession(await authApi.login(v)); nav(from, { replace: true }) } catch (e) { setErr(errorMessage(e)) } })
  return (<Shell title="Sign in" subtitle="Welcome back to FleetFlow." footer={<>New to FleetFlow? <Link to="/signup" className="font-medium text-primary hover:underline">Start free</Link></>}>
    <form onSubmit={submit} className="space-y-4" noValidate>
      <Input label="Email" type="email" autoComplete="email" error={errors.email?.message} {...register('email')} />
      <Input label="Password" type="password" autoComplete="current-password" error={errors.password?.message} {...register('password')} />
      <div className="text-right text-sm"><Link to="/forgot-password" className="text-primary hover:underline">Forgot password?</Link></div>
      {err && <p role="alert" className="text-sm text-danger">{err}</p>}
      <Button type="submit" className="w-full" loading={isSubmitting}>Sign in</Button>
    </form></Shell>)
}
const signupSchema = z.object({
  name: z.string().min(1, 'Enter your name').max(120), organizationName: z.string().min(1, 'Enter your company name').max(160),
  email: z.string().email('Enter a valid email'), password: z.string().min(10, 'Use at least 10 characters').max(72, 'Use at most 72 characters'),
})
export function Signup() {
  const nav = useNavigate(); const setSession = useAuth((s) => s.setSession); const [err, setErr] = useState('')
  const { register, handleSubmit, formState: { errors, isSubmitting } } = useForm<z.infer<typeof signupSchema>>({ resolver: zodResolver(signupSchema) })
  const submit = handleSubmit(async (v) => { setErr(''); try { setSession(await authApi.signup(v)); nav('/dashboard', { replace: true }) } catch (e) { setErr(errorMessage(e)) } })
  return (<Shell title="Create your workspace" subtitle="Start free. You become the owner of a new organization." footer={<>Already have an account? <Link to="/login" className="font-medium text-primary hover:underline">Sign in</Link></>}>
    <form onSubmit={submit} className="space-y-4" noValidate>
      <Input label="Your name" autoComplete="name" error={errors.name?.message} {...register('name')} />
      <Input label="Company" autoComplete="organization" error={errors.organizationName?.message} {...register('organizationName')} />
      <Input label="Work email" type="email" autoComplete="email" error={errors.email?.message} {...register('email')} />
      <Input label="Password" type="password" autoComplete="new-password" hint="At least 10 characters." error={errors.password?.message} {...register('password')} />
      {err && <p role="alert" className="text-sm text-danger">{err}</p>}
      <Button type="submit" className="w-full" loading={isSubmitting}>Create workspace</Button>
    </form></Shell>)
}
