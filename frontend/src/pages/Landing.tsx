import { Link } from 'react-router-dom'
import { ArrowRight, BarChart3, Check, Cpu, Map, Radio, Route, Server, PackagePlus, UserCheck, MapPinned, PackageCheck } from 'lucide-react'
import { Logo } from '@/components/Logo'
import { Button } from '@/components/ui/Button'
import { Card } from '@/components/ui/Card'
import { useAuth } from '@/stores/auth'

const features = [
  { icon: Route, title: 'Intelligent Dispatch', text: 'Automatically match orders with suitable drivers.' },
  { icon: Map, title: 'Live Fleet Tracking', text: 'Monitor active drivers in real time.' },
  { icon: Radio, title: 'Event-Driven Architecture', text: 'Process delivery operations reliably through asynchronous events.' },
  { icon: BarChart3, title: 'Operational Analytics', text: 'Understand delivery performance and fleet utilization.' },
  { icon: Server, title: 'Built for Scale', text: 'Designed for concurrent orders and horizontally scalable workloads.' },
]
const steps = [{ icon: PackagePlus, t: 'Create Order' }, { icon: Cpu, t: 'Dispatch Engine' }, { icon: UserCheck, t: 'Driver Assigned' }, { icon: MapPinned, t: 'Live Tracking' }, { icon: PackageCheck, t: 'Delivery Completed' }]

export function Landing() {
  const user = useAuth((s) => s.user)
  return (<div className="min-h-screen">
    <header className="sticky top-0 z-30 border-b bg-background/80 backdrop-blur"><div className="mx-auto flex h-16 max-w-6xl items-center justify-between px-4">
      <Link to="/"><Logo /></Link>
      <nav aria-label="Primary" className="hidden gap-6 text-sm text-muted-foreground md:flex">{[['Product', '#features'], ['How it works', '#how'], ['Features', '#features']].map(([l, h]) => <a key={l} href={h} className="hover:text-foreground">{l}</a>)}</nav>
      <div className="flex items-center gap-2">{user ? <Link to="/dashboard"><Button size="sm">Open dashboard</Button></Link> : <><Link to="/login"><Button variant="ghost" size="sm">Sign in</Button></Link><Link to="/signup"><Button size="sm">Start free</Button></Link></>}</div></div></header>
    <main>
      <section className="mx-auto max-w-6xl px-4 pb-16 pt-16 text-center sm:pt-24">
        <h1 className="mx-auto max-w-3xl text-4xl font-semibold tracking-tight sm:text-6xl">Dispatch smarter. Deliver faster.</h1>
        <p className="mx-auto mt-5 max-w-2xl text-lg text-muted-foreground">FleetFlow helps delivery teams automate driver dispatch, monitor live operations, and scale delivery workflows with event-driven infrastructure.</p>
        <div className="mt-8 flex justify-center gap-3"><Link to="/signup"><Button className="h-11 px-6">Start free<ArrowRight className="h-4 w-4" /></Button></Link><a href="#how"><Button variant="secondary" className="h-11 px-6">View demo</Button></a></div>
        <Card className="mx-auto mt-14 max-w-4xl overflow-hidden text-left shadow-xl" aria-label="Illustrative product preview">
          <div className="flex items-center gap-1.5 border-b bg-muted px-4 py-2"><span className="h-2.5 w-2.5 rounded-full bg-danger/60" /><span className="h-2.5 w-2.5 rounded-full bg-warning/60" /><span className="h-2.5 w-2.5 rounded-full bg-success/60" /><span className="ml-3 text-xs text-muted-foreground">Illustrative preview</span></div>
          <div className="grid gap-px bg-border sm:grid-cols-3">
            <div className="space-y-2 bg-card p-4 sm:col-span-2"><p className="text-sm font-medium">Live fleet</p>
              <div className="relative h-44 overflow-hidden rounded-lg bg-muted">{[[18, 30], [45, 60], [70, 25], [60, 75], [30, 70]].map(([x, y], i) => <span key={i} className="absolute h-3 w-3 animate-pulse rounded-full bg-primary ring-4 ring-primary/20" style={{ left: `${x}%`, top: `${y}%`, animationDelay: `${i * 300}ms` }} />)}</div></div>
            <div className="space-y-3 bg-card p-4"><p className="text-sm font-medium">Dispatch queue</p>{['Assigned', 'Assigned', 'Dispatching'].map((s, i) => <div key={i} className="flex items-center justify-between rounded-lg border p-2 text-xs"><span className="h-2 w-16 rounded bg-muted" /><span className={s === 'Assigned' ? 'text-success' : 'text-warning'}>{s}</span></div>)}</div>
          </div></Card>
      </section>
      <section id="features" className="mx-auto max-w-6xl scroll-mt-20 px-4 py-16"><h2 className="text-center text-3xl font-semibold tracking-tight">Everything a dispatch team needs</h2>
        <div className="mt-10 grid gap-4 sm:grid-cols-2 lg:grid-cols-3">{features.map(({ icon: I, title, text }) => (<Card key={title} className="p-5 transition-shadow hover:shadow-md"><div className="mb-3 inline-flex rounded-lg bg-primary/10 p-2 text-primary"><I className="h-5 w-5" /></div><h3 className="font-medium">{title}</h3><p className="mt-1 text-sm text-muted-foreground">{text}</p></Card>))}</div></section>
      <section id="how" className="scroll-mt-20 border-y bg-muted/40 py-16"><div className="mx-auto max-w-6xl px-4"><h2 className="text-center text-3xl font-semibold tracking-tight">How it works</h2>
        <ol className="mt-10 grid gap-4 sm:grid-cols-5">{steps.map(({ icon: I, t }, i) => (<li key={t}><Card className="h-full p-4 text-center"><span className="mx-auto mb-2 grid h-10 w-10 place-items-center rounded-full bg-primary/10 text-primary"><I className="h-5 w-5" /></span><p className="text-xs text-muted-foreground">Step {i + 1}</p><p className="text-sm font-medium">{t}</p></Card></li>))}</ol></div></section>
      
      {/* <section id="pricing" className="mx-auto max-w-6xl scroll-mt-20 px-4 pb-20"><h2 className="text-center text-3xl font-semibold tracking-tight">Pricing</h2>
        <div className="mt-10 grid gap-4 md:grid-cols-3">{plans.map((p) => (<Card key={p.name} className={`p-6 ${p.featured ? 'ring-2 ring-primary' : ''}`}><h3 className="font-medium">{p.name}</h3><p className="mt-2 text-3xl font-semibold">{p.price}<span className="text-sm font-normal text-muted-foreground">{p.per}</span></p>
          <ul className="mt-5 space-y-2 text-sm">{p.items.map((i) => <li key={i} className="flex gap-2"><Check className="mt-0.5 h-4 w-4 text-success" />{i}</li>)}</ul><Link to="/signup"><Button variant={p.featured ? 'primary' : 'secondary'} className="mt-6 w-full">{p.cta}</Button></Link></Card>))}</div></section> */}
    </main>
    <footer className="border-t py-8 text-center text-sm text-muted-foreground"><Logo className="mb-2 text-base" /><p>Intelligent dispatch infrastructure for modern delivery operations.</p></footer>
  </div>)
}
