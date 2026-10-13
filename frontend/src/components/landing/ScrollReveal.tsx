import { useEffect, useRef, useState, type ReactNode } from 'react'

/**
 * Fades a block up from translate-y-16 blur-md opacity-0 to its resting state
 * the first time it crosses the viewport. Uses IntersectionObserver only —
 * never a scroll listener, per the motion rules this page follows.
 */
export function ScrollReveal({
  children,
  delayMs = 0,
  className = '',
}: {
  children: ReactNode
  delayMs?: number
  className?: string
}) {
  const ref = useRef<HTMLDivElement>(null)
  const [visible, setVisible] = useState(false)

  useEffect(() => {
    const el = ref.current
    if (!el) return
    const io = new IntersectionObserver(
      ([entry]) => {
        if (entry.isIntersecting) {
          setVisible(true)
          io.disconnect()
        }
      },
      { threshold: 0.2 },
    )
    io.observe(el)
    return () => io.disconnect()
  }, [])

  return (
    <div
      ref={ref}
      style={{ transitionDelay: `${delayMs}ms` }}
      className={[
        'transition-all duration-700 ease-[cubic-bezier(0.32,0.72,0,1)]',
        visible ? 'translate-y-0 opacity-100 blur-0' : 'translate-y-16 opacity-0 blur-md',
        className,
      ].join(' ')}
    >
      {children}
    </div>
  )
}