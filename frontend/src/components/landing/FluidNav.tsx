import { useState } from 'react'
import { Link } from 'react-router-dom'

const LINKS = [
  { href: '#product', label: 'Product' },
  { href: '#how-it-works', label: 'How it works' },
  { href: '#pricing', label: 'Pricing' },
  { href: '#faq', label: 'FAQ' },
]

const EASE = 'ease-[cubic-bezier(0.32,0.72,0,1)]'

export function FluidNav({ dark }: { dark: boolean }) {
  const [open, setOpen] = useState(false)
  const glass = dark ? 'bg-[#181818]/70' : 'bg-white/70'
  const modalGlass = dark ? 'backdrop-blur-3xl bg-black/80' : 'backdrop-blur-3xl bg-white/80'

  return (
    <>
      <nav
        aria-label="Primary"
        className={`fixed inset-x-0 top-6 z-50 mx-auto w-max rounded-full border border-white/10 ${glass} px-2 py-2 shadow-lg backdrop-blur-xl transition-all duration-700 ${EASE}`}
      >
        <div className="flex items-center gap-1">
          <Link to="/" className="rounded-full px-4 py-2 text-sm font-semibold tracking-tight">
            FleetFlow
          </Link>
          <div className="hidden items-center gap-1 sm:flex">
            {LINKS.map((l) => (
              <a
                key={l.href}
                href={l.href}
                className="rounded-full px-4 py-2 text-sm text-current/70 transition-colors duration-500 hover:text-current focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-current"
              >
                {l.label}
              </a>
            ))}
          </div>
          <Link
            to="/login"
            className="hidden rounded-full px-4 py-2 text-sm text-current/70 transition-colors duration-500 hover:text-current sm:block"
          >
            Sign in
          </Link>
          <Link
            to="/signup"
            className="rounded-full bg-white px-5 py-2 text-sm font-medium text-black transition-transform duration-300 hover:bg-white/90 active:scale-95"
          >
            Start free trial
          </Link>
          <button
            type="button"
            aria-label={open ? 'Close menu' : 'Open menu'}
            aria-expanded={open}
            onClick={() => setOpen((v) => !v)}
            className="relative ml-1 grid h-9 w-9 place-items-center rounded-full sm:hidden"
          >
            <span
              className={`absolute h-[2px] w-4 bg-current transition-all duration-500 ${EASE} ${open ? 'translate-y-0 rotate-45' : '-translate-y-1.5 rotate-0'}`}
            />
            <span
              className={`absolute h-[2px] w-4 bg-current transition-all duration-500 ${EASE} ${open ? 'translate-y-0 -rotate-45' : 'translate-y-1.5 rotate-0'}`}
            />
          </button>
        </div>
      </nav>

      <div
        className={`fixed inset-0 z-40 ${modalGlass} transition-opacity duration-700 ${EASE} sm:hidden ${
          open ? 'pointer-events-auto opacity-100' : 'pointer-events-none opacity-0'
        }`}
      >
        <div className="flex h-full flex-col items-center justify-center gap-6">
          {[...LINKS, { href: '/login', label: 'Sign in' }].map((l, i) => (
            <a
              key={l.href}
              href={l.href}
              onClick={() => setOpen(false)}
              style={{ transitionDelay: open ? `${100 + i * 50}ms` : '0ms' }}
              className={`text-2xl font-medium transition-all duration-500 ${EASE} ${
                open ? 'translate-y-0 opacity-100' : 'translate-y-12 opacity-0'
              }`}
            >
              {l.label}
            </a>
          ))}
        </div>
      </div>
    </>
  )
}