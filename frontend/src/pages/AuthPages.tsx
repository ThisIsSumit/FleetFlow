import { useState, type ReactNode } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { ArrowLeft, CheckCircle, Lightning, ShieldCheck } from '@phosphor-icons/react'
import { Logo } from '@/components/Logo'
import { Button } from '@/components/ui/Button'
import { Input } from '@/components/ui/Field'
import { authApi } from '@/api/authApi'
import { errorMessage } from '@/lib/api'
import { useAuth } from '@/stores/auth'

export function Shell({ title, subtitle, children, footer }: { title: string; subtitle: string; children: ReactNode; footer: ReactNode }) {
  return (
    <div className="min-h-screen bg-black font-[Manrope] text-white antialiased [text-wrap:pretty]">
      <div className="mx-auto grid min-h-screen max-w-[1440px] lg:grid-cols-[minmax(0,1.05fr)_minmax(460px,0.95fr)]">
        <aside className="relative hidden overflow-hidden border-r border-[#272727] bg-[#181818] p-10 lg:flex lg:flex-col lg:justify-between">
          <div className="absolute -right-24 top-20 h-72 w-72 rounded-full border border-white/[0.06]" aria-hidden />
          <div className="absolute -bottom-32 -left-24 h-80 w-80 rounded-full border border-white/[0.06]" aria-hidden />
          <Link to="/" className="relative z-10 w-fit text-white" aria-label="FleetFlow home">
            <Logo className="[&_rect]:fill-white [&_path]:stroke-black [&_circle]:fill-black" />
          </Link>

          <div className="relative z-10 max-w-xl">
            <span className="inline-flex items-center gap-2 rounded-full border border-[#313131] bg-[#1F1F1F] px-4 py-1.5 text-sm text-white/70">
              <Lightning weight="fill" className="h-3.5 w-3.5 text-white" />
              Built for delivery teams
            </span>
            <h2 className="mt-8 max-w-lg text-4xl font-semibold leading-tight text-white">
              Every delivery moving in the right direction.
            </h2>
            <p className="mt-5 max-w-md text-base leading-7 text-white/60">
              Automatic driver assignment, live fleet visibility, and a dispatch engine that stays reliable when
              demand spikes.
            </p>
            <div className="mt-8 grid max-w-md gap-3 text-sm text-white/70 sm:grid-cols-2">
              <p className="flex items-center gap-2"><CheckCircle weight="fill" className="h-4 w-4 text-white" /> Dispatch in under 2 seconds</p>
              <p className="flex items-center gap-2"><ShieldCheck weight="fill" className="h-4 w-4 text-white" /> Your organization stays isolated</p>
            </div>
          </div>

          <p className="relative z-10 text-xs text-white/40">Demo environment. Do not enter real customer data.</p>
        </aside>

        <main className="flex min-h-screen items-center justify-center px-6 py-10 sm:px-10">
          <div className="w-full max-w-md">
            <div className="mb-8 flex items-center justify-between">
              <Link to="/" className="lg:hidden" aria-label="FleetFlow home"><Logo className="[&_rect]:fill-white [&_path]:stroke-black [&_circle]:fill-black" /></Link>
              <Link to="/" className="ml-auto inline-flex items-center gap-2 text-sm text-white/50 transition-colors duration-300 ease-[cubic-bezier(0.32,0.72,0,1)] hover:text-white">
                <ArrowLeft className="h-4 w-4" /> Back to home
              </Link>
            </div>

            <section className="rounded-2xl border border-[#272727] bg-[#181818] p-6 shadow-2xl shadow-black/30 sm:p-8">
              <h1 className="bg-gradient-to-r from-white to-[#9B9B9B] bg-clip-text text-3xl font-semibold leading-tight text-transparent">{title}</h1>
              <p className="mb-8 mt-2 text-sm leading-6 text-white/55">{subtitle}</p>
              <div className="[&_input]:border-[#313131] [&_input]:bg-[#1F1F1F] [&_input]:text-white [&_input]:placeholder:text-white/35 [&_label]:text-white/80 [&_p]:text-white/50 [&_p[role=alert]]:text-red-300">
                {children}
              </div>
              <p className="mt-8 text-center text-sm text-white/50">{footer}</p>
            </section>
            <p className="mt-6 text-center text-xs text-white/30">By continuing, you agree to use FleetFlow for legitimate delivery operations.</p>
          </div>
        </main>
      </div>
    </div>
  )
}
const loginSchema = z.object({ email: z.string().email('Enter a valid email'), password: z.string().min(1, 'Enter your password') })
export function Login() {
  const nav = useNavigate(); const from = (useLocation().state as { from?: string } | null)?.from ?? '/dashboard'
  const setSession = useAuth((s) => s.setSession); const [err, setErr] = useState('')
  const { register, handleSubmit, formState: { errors, isSubmitting } } = useForm<z.infer<typeof loginSchema>>({ resolver: zodResolver(loginSchema) })
  const submit = handleSubmit(async (v) => { setErr(''); try { setSession(await authApi.login(v)); nav(from, { replace: true }) } catch (e) { setErr(errorMessage(e)) } })
  return (<Shell title="Sign in" subtitle="Welcome back to FleetFlow." footer={<>New to FleetFlow? <Link to="/signup" className="font-medium text-white hover:text-white/70 hover:underline">Start free</Link></>}>
    <form onSubmit={submit} className="space-y-4" noValidate>
      <Input label="Email" type="email" autoComplete="email" error={errors.email?.message} {...register('email')} />
      <Input label="Password" type="password" autoComplete="current-password" error={errors.password?.message} {...register('password')} />
      <div className="text-right text-sm"><Link to="/forgot-password" className="text-white/60 hover:text-white hover:underline">Forgot password?</Link></div>
      {err && <p role="alert" className="text-sm text-red-300">{err}</p>}
      <Button type="submit" className="mt-2 h-12 w-full rounded-full bg-white text-base font-semibold text-black transition-transform duration-300 ease-[cubic-bezier(0.32,0.72,0,1)] hover:scale-[1.02] hover:bg-white/90 active:scale-95" loading={isSubmitting}>Sign in</Button>
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
  return (<Shell title="Create your workspace" subtitle="Start free. You become the owner of a new organization." footer={<>Already have an account? <Link to="/login" className="font-medium text-white hover:text-white/70 hover:underline">Sign in</Link></>}>
    <form onSubmit={submit} className="space-y-4" noValidate>
      <Input label="Your name" autoComplete="name" error={errors.name?.message} {...register('name')} />
      <Input label="Company" autoComplete="organization" error={errors.organizationName?.message} {...register('organizationName')} />
      <Input label="Work email" type="email" autoComplete="email" error={errors.email?.message} {...register('email')} />
      <Input label="Password" type="password" autoComplete="new-password" hint="At least 10 characters." error={errors.password?.message} {...register('password')} />
      {err && <p role="alert" className="text-sm text-red-300">{err}</p>}
      <Button type="submit" className="mt-2 h-12 w-full rounded-full bg-white text-base font-semibold text-black transition-transform duration-300 ease-[cubic-bezier(0.32,0.72,0,1)] hover:scale-[1.02] hover:bg-white/90 active:scale-95" loading={isSubmitting}>Create workspace</Button>
    </form></Shell>)
}
