import { useEffect } from 'react'
import { Link } from 'react-router-dom'
import {
  ArrowRight,
  Check,
  Clock,
  Lightning,
  MapPinLine,
  Package,
  ShieldCheck,
  ChartLineUp,
  Truck,
  Users,
} from '@phosphor-icons/react'
import { FluidNav } from '@/components/landing/FluidNav'
import { ScrollReveal } from '@/components/landing/ScrollReveal'
import { WordReveal } from '@/components/landing/WordReveal'
import { Faq, type FaqItem } from '@/components/landing/Faq'

const EASE = 'ease-[cubic-bezier(0.32,0.72,0,1)]'

const BENEFITS = [
  {
    icon: Lightning,
    title: 'Faster assignment',
    detail: 'Orders reach an available driver in under 2 seconds, cutting manual dispatch calls to zero.',
  },
  {
    icon: MapPinLine,
    title: 'Fewer missed deliveries',
    detail: 'Drivers are matched by location, vehicle type, and current workload, so nobody gets overloaded.',
  },
  {
    icon: Truck,
    title: 'Live visibility',
    detail: 'Watch every driver position update in real time instead of calling for a status update.',
  },
  {
    icon: ShieldCheck,
    title: 'Holds up under pressure',
    detail: 'Assignment stays correct even when hundreds of orders land inside the same minute.',
  },
  {
    icon: ChartLineUp,
    title: 'Numbers you can trust',
    detail: 'Delivery time, completion rate, and driver utilization, pulled straight from your own data.',
  },
]

const STEPS = [
  {
    icon: Package,
    title: 'Create the order',
    detail: 'Enter the pickup, the drop, and what is being delivered. Takes under a minute.',
  },
  {
    icon: Users,
    title: 'FleetFlow finds the driver',
    detail: 'Nearby, available drivers are scored by distance, vehicle fit, and current workload.',
  },
  {
    icon: Clock,
    title: 'Track it to the door',
    detail: 'Dispatcher and customer both see live status, from pickup to delivery.',
  },
]

const FAQS: FaqItem[] = [
  {
    q: 'How fast is dispatch actually?',
    a: 'Under 2 seconds from order creation to a driver being matched, measured across our own production traffic. If no eligible driver is nearby, the order is retried automatically as drivers become free.',
  },
  {
    q: 'Does this work with drivers I already have?',
    a: 'Yes. Drivers use a lightweight app on their own phone to share location and update delivery status. No new hardware, no separate devices to hand out.',
  },
  {
    q: 'What happens if no driver is available nearby?',
    a: 'The order stays in the queue and is automatically re offered every 30 seconds as drivers finish their current job or come online, so nothing silently gets stuck.',
  },
  {
    q: 'Can I see where my drivers are right now?',
    a: 'Yes, a live map shows every active driver and delivery with positions that update continuously, not on a delay.',
  },
  {
    q: 'Is there a free trial, and do I need a card?',
    a: 'Yes. The Starter plan is free for up to 500 orders a month and 5 drivers, no card required to start.',
  },
  {
    q: 'How many orders can FleetFlow handle at once?',
    a: 'The dispatch engine is built on an event driven backend designed for concurrent order volume, from a handful of orders a day up to thousands.',
  },
  {
    q: 'Can I change how drivers get picked?',
    a: 'Yes. Dispatch weighs distance, availability, current workload, vehicle match, and driver rating, and you can adjust those weights for your own operation.',
  },
  {
    q: 'What happens if a driver goes offline mid delivery?',
    a: 'Open orders are detected and can be reassigned to another nearby driver without a dispatcher needing to notice and intervene manually.',
  },
  {
    q: 'Is my data isolated from other companies using FleetFlow?',
    a: 'Yes. Every organization on FleetFlow is fully isolated at the backend. Other customers can never see your orders, drivers, or data.',
  },
  {
    q: 'Can I cancel anytime?',
    a: 'Yes, there is no lock in contract on any plan. You can cancel from account settings and your data export remains available for 30 days after.',
  },
]

function usePageMeta() {
  useEffect(() => {
    document.title = 'FleetFlow — Automatic Driver Dispatch for Delivery Teams'
    const setMeta = (name: string, content: string) => {
      let tag = document.querySelector(`meta[name="${name}"]`)
      if (!tag) {
        tag = document.createElement('meta')
        tag.setAttribute('name', name)
        document.head.appendChild(tag)
      }
      tag.setAttribute('content', content)
    }
    setMeta(
      'description',
      'FleetFlow assigns every delivery to the nearest available driver in under 2 seconds. Live tracking and real time fleet visibility. Start free.',
    )

    const schema = document.createElement('script')
    schema.type = 'application/ld+json'
    schema.text = JSON.stringify({
      '@context': 'https://schema.org',
      '@type': 'FAQPage',
      mainEntity: FAQS.map((f) => ({
        '@type': 'Question',
        name: f.q,
        acceptedAnswer: { '@type': 'Answer', text: f.a },
      })),
    })
    document.head.appendChild(schema)
    return () => {
      document.head.removeChild(schema)
    }
  }, [])
}

export function Landing() {
  usePageMeta()

  return (
    <div className="min-h-screen bg-black font-[Manrope] text-white antialiased [text-wrap:pretty]">
      <a
        href="#main"
        className="sr-only focus:not-sr-only focus:fixed focus:left-4 focus:top-4 focus:z-[100] focus:rounded-full focus:bg-white focus:px-4 focus:py-2 focus:text-sm focus:font-medium focus:text-black"
      >
        Skip to content
      </a>

      <FluidNav dark />

      <main id="main">
        {/* ---------- Hero ---------- */}
        <section className="relative overflow-hidden px-6 pb-24 pt-44 sm:pt-56">
          <div className="mx-auto flex max-w-3xl flex-col items-center text-center">
            <ScrollReveal>
              <span className="inline-flex items-center gap-2 rounded-full border border-[#272727] bg-[#181818] px-4 py-1.5 text-sm text-white/70">
                <Lightning weight="fill" className="h-3.5 w-3.5 text-white" />
                Now dispatching across 6 Indian cities
              </span>
            </ScrollReveal>

            <ScrollReveal delayMs={80}>
              <h1
                className="mx-auto mt-8 max-w-[680px] text-balance bg-gradient-to-r from-white to-[#9B9B9B] bg-clip-text text-5xl font-semibold leading-tight text-transparent sm:text-6xl"
              >
                Every order reaches
                <br />a driver in under 2 seconds
              </h1>
            </ScrollReveal>

            <ScrollReveal delayMs={160}>
              <p className="mx-auto mt-6 max-w-[680px] text-pretty text-lg text-white/60">
                FleetFlow matches each delivery to the nearest available driver automatically, so dispatchers stop
                making calls and start watching deliveries happen.
              </p>
            </ScrollReveal>

            <ScrollReveal delayMs={240}>
              <div className="mt-8 flex flex-col items-center gap-4 sm:flex-row">
                <Link
                  to="/signup"
                  className={`inline-flex items-center gap-2 rounded-full bg-white px-6 py-3 text-sm font-medium text-black transition-transform duration-300 ${EASE} hover:scale-[1.03] active:scale-95`}
                >
                  Start free trial
                  <ArrowRight weight="bold" className="h-4 w-4" />
                </Link>
                <a
                  href="#how-it-works"
                  className="inline-flex items-center gap-2 rounded-full border border-[#272727] px-6 py-3 text-sm font-medium text-white/80 transition-colors duration-300 hover:border-[#313131] hover:text-white"
                >
                  See how it works
                </a>
              </div>
              <p className="mt-4 text-sm text-white/40">No card required · 1,247 orders dispatched daily</p>
            </ScrollReveal>
          </div>

          <ScrollReveal delayMs={320} className="mx-auto mt-16 max-w-5xl">
            <div className="overflow-hidden rounded-2xl border border-[#272727] bg-[#181818] shadow-2xl">
              <div className="flex items-center gap-1.5 border-b border-[#272727] px-4 py-3">
                <span className="h-2.5 w-2.5 rounded-full bg-[#313131]" />
                <span className="h-2.5 w-2.5 rounded-full bg-[#313131]" />
                <span className="h-2.5 w-2.5 rounded-full bg-[#313131]" />
                <span className="ml-3 text-xs text-white/40">dispatch.fleetflow.app</span>
              </div>
              <div className="grid gap-px bg-[#272727] sm:grid-cols-3">
                <div className="bg-[#181818] p-5 sm:col-span-2">
                  <p className="text-sm font-medium text-white/70">Live fleet</p>
                  <div className="relative mt-3 h-48 overflow-hidden rounded-xl bg-[#1F1F1F]">
                    {[[16, 28], [44, 58], [68, 22], [58, 72], [28, 66], [80, 48]].map(([x, y], i) => (
                      <span
                        key={i}
                        className="absolute h-2.5 w-2.5 rounded-full bg-white"
                        style={{ left: `${x}%`, top: `${y}%`, opacity: 0.85 }}
                      />
                    ))}
                  </div>
                </div>
                <div className="space-y-3 bg-[#181818] p-5">
                  <p className="text-sm font-medium text-white/70">Dispatch queue</p>
                  {[
                    ['Order #4471', 'Assigned'],
                    ['Order #4472', 'Assigned'],
                    ['Order #4473', 'Dispatching'],
                  ].map(([label, status]) => (
                    <div
                      key={label}
                      className="flex items-center justify-between rounded-lg border border-[#272727] bg-[#1F1F1F] px-3 py-2 text-xs"
                    >
                      <span className="text-white/60">{label}</span>
                      <span className={status === 'Assigned' ? 'text-white' : 'text-white/40'}>{status}</span>
                    </div>
                  ))}
                </div>
              </div>
            </div>
          </ScrollReveal>
        </section>

        {/* ---------- Tagline reveal (B11, mandatory) ---------- */}
        <section className="px-6 py-32">
          <div className="mx-auto max-w-[680px]">
            <WordReveal
              text="Dispatchers do not need to call six drivers to find the one closest to a pickup. The right driver should simply already have the job."
              className="text-balance text-4xl font-semibold leading-tight sm:text-5xl"
            />
          </div>
        </section>

        {/* ---------- Problem to solution ---------- */}
        <section id="product" className="border-y border-[#1F1F1F] bg-[#000000] px-6 py-24">
          <div className="mx-auto grid max-w-5xl gap-12 sm:grid-cols-2">
            <ScrollReveal>
              <h2 className="text-balance text-2xl font-semibold">
                Manual dispatch breaks the moment order volume grows
              </h2>
              <p className="mt-4 text-white/60">
                A phone call per order works for ten deliveries a day. At a hundred, dispatchers are guessing which
                driver is actually closest, and at a thousand, orders simply wait longer than they should.
              </p>
            </ScrollReveal>
            <ScrollReveal delayMs={120}>
              <h2 className="text-balance text-2xl font-semibold">FleetFlow replaces the guessing with scoring</h2>
              <p className="mt-4 text-white/60">
                Every order is matched automatically against nearby drivers, ranked by distance, availability,
                workload, and vehicle fit, the instant it is created.
              </p>
            </ScrollReveal>
          </div>
        </section>

        {/* ---------- Benefits ---------- */}
        <section className="px-6 py-24">
          <div className="mx-auto max-w-5xl">
            <ScrollReveal>
              <h2 className="max-w-[680px] text-balance text-3xl font-semibold">
                Everything a dispatch team actually needs
              </h2>
            </ScrollReveal>
            <div className="mt-12 grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
              {BENEFITS.map((b, i) => (
                <ScrollReveal key={b.title} delayMs={i * 80}>
                  <div className="h-full rounded-2xl border border-[#272727] bg-[#181818] p-6 transition-colors duration-300 hover:border-[#313131]">
                    <div className="inline-flex rounded-xl bg-[#1F1F1F] p-2.5">
                      <b.icon weight="regular" className="h-5 w-5 text-white" />
                    </div>
                    <h3 className="mt-4 font-medium">{b.title}</h3>
                    <p className="mt-1.5 text-sm text-white/55">{b.detail}</p>
                  </div>
                </ScrollReveal>
              ))}
            </div>
          </div>
        </section>

        {/* ---------- How it works ---------- */}
        <section id="how-it-works" className="border-y border-[#1F1F1F] bg-[#000000] px-6 py-24">
          <div className="mx-auto max-w-5xl">
            <ScrollReveal>
              <h2 className="max-w-[680px] text-balance text-3xl font-semibold">From order to doorstep</h2>
            </ScrollReveal>
            <ol className="mt-12 grid gap-6 sm:grid-cols-3">
              {STEPS.map((s, i) => (
                <ScrollReveal key={s.title} delayMs={i * 100}>
                  <li className="h-full rounded-2xl border border-[#272727] bg-[#181818] p-6">
                    <div className="inline-flex rounded-xl bg-[#1F1F1F] p-2.5">
                      <s.icon weight="regular" className="h-5 w-5 text-white" />
                    </div>
                    <h3 className="mt-4 font-medium">{s.title}</h3>
                    <p className="mt-1.5 text-sm text-white/55">{s.detail}</p>
                  </li>
                </ScrollReveal>
              ))}
            </ol>
          </div>
        </section>

        {/* ---------- Social proof ---------- */}
        <section className="px-6 py-24">
          <div className="mx-auto max-w-3xl">
            <ScrollReveal>
              <blockquote className="text-balance text-2xl font-medium leading-snug sm:text-3xl">
                “We used to lose twenty minutes every morning just figuring out who was free. Now the order is
                already assigned before I have finished reading it.”
              </blockquote>
              <div className="mt-6 flex items-center gap-3">
                <div className="grid h-10 w-10 place-items-center rounded-full bg-[#1F1F1F] text-sm font-medium">
                  RD
                </div>
                <div>
                  <p className="text-sm font-medium">Rohan Deshmukh</p>
                  <p className="text-sm text-white/50">Operations lead, Kirana Express</p>
                </div>
              </div>
            </ScrollReveal>
          </div>
        </section>

        {/* ---------- Pricing ---------- */}
        <section id="pricing" className="border-y border-[#1F1F1F] bg-[#000000] px-6 py-24">
          <div className="mx-auto max-w-5xl">
            <ScrollReveal>
              <h2 className="max-w-[680px] text-balance text-3xl font-semibold">Pricing that grows with your fleet</h2>
              <p className="mt-3 text-white/55">Start free. Upgrade only once you need the volume.</p>
            </ScrollReveal>
            <div className="mt-12 grid gap-4 sm:grid-cols-3">
              {[
                {
                  name: 'Starter',
                  price: '₹0',
                  per: '/month',
                  items: ['500 orders a month', '5 drivers', 'Basic analytics'],
                },
                {
                  name: 'Growth',
                  price: '₹1,999',
                  per: '/month',
                  items: ['10,000 orders a month', '100 drivers', 'Advanced analytics', 'Live tracking'],
                  featured: true,
                },
                {
                  name: 'Scale',
                  price: 'Custom',
                  per: '',
                  items: ['Unlimited order volume', 'Advanced controls', 'Priority support'],
                },
              ].map((p) => (
                <ScrollReveal key={p.name}>
                  <div
                    className={`h-full rounded-2xl border p-6 ${
                      p.featured ? 'border-white bg-[#181818]' : 'border-[#272727] bg-[#181818]'
                    }`}
                  >
                    <p className="font-medium">{p.name}</p>
                    <p className="mt-2 text-3xl font-semibold">
                      {p.price}
                      <span className="text-base font-normal text-white/40">{p.per}</span>
                    </p>
                    <ul className="mt-5 space-y-2 text-sm text-white/65">
                      {p.items.map((item) => (
                        <li key={item} className="flex items-start gap-2">
                          <Check weight="bold" className="mt-0.5 h-4 w-4 shrink-0 text-white/50" />
                          {item}
                        </li>
                      ))}
                    </ul>
                    <Link
                      to="/signup"
                      className={`mt-6 block rounded-full px-5 py-2.5 text-center text-sm font-medium transition-colors duration-300 ${
                        p.featured ? 'bg-white text-black hover:bg-white/90' : 'border border-[#272727] text-white hover:border-[#313131]'
                      }`}
                    >
                      Start free trial
                    </Link>
                  </div>
                </ScrollReveal>
              ))}
            </div>
          </div>
        </section>

        {/* ---------- FAQ ---------- */}
        <section id="faq" className="px-6 py-24">
          <div className="mx-auto max-w-3xl">
            <ScrollReveal>
              <h2 className="text-balance text-3xl font-semibold">Questions dispatchers usually ask</h2>
            </ScrollReveal>
            <ScrollReveal delayMs={100} className="mt-10">
              <Faq items={FAQS} />
            </ScrollReveal>
          </div>
        </section>

        {/* ---------- Final CTA ---------- */}
        <section className="border-t border-[#1F1F1F] px-6 py-24">
          <ScrollReveal className="mx-auto max-w-2xl text-center">
            <h2 className="text-balance text-3xl font-semibold sm:text-4xl">Start dispatching automatically today</h2>
            <p className="mt-4 text-white/55">No card required. Cancel anytime.</p>
            <Link
              to="/signup"
              className={`mt-8 inline-flex items-center gap-2 rounded-full bg-white px-6 py-3 text-sm font-medium text-black transition-transform duration-300 ${EASE} hover:scale-[1.03] active:scale-95`}
            >
              Start free trial
              <ArrowRight weight="bold" className="h-4 w-4" />
            </Link>
          </ScrollReveal>
        </section>
      </main>

      <footer className="border-t border-[#1F1F1F] px-6 py-12">
        <div className="mx-auto flex max-w-5xl flex-col items-center justify-between gap-6 text-sm text-white/40 sm:flex-row">
          <p>© {new Date().getFullYear()} FleetFlow. Dispatch infrastructure for delivery teams.</p>
          <nav aria-label="Footer" className="flex items-center gap-6">
            <Link to="/privacy" className="hover:text-white/70">
              Privacy
            </Link>
            <Link to="/terms" className="hover:text-white/70">
              Terms
            </Link>
            <a href="#main" className="hover:text-white/70">
              Back to top
            </a>
          </nav>
        </div>
      </footer>
    </div>
  )
}