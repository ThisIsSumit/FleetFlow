import { useEffect, useRef, useState } from 'react'

/**
 * Renders `text` as individual words, each muted at rest. As the block enters
 * the viewport, words activate to full color one at a time, in reading order,
 * each on the shared cubic-bezier easing — never a single block-level fade.
 */
export function WordReveal({ text, className = '' }: { text: string; className?: string }) {
  const words = text.split(' ')
  const ref = useRef<HTMLParagraphElement>(null)
  const [activeCount, setActiveCount] = useState(0)

  useEffect(() => {
    const el = ref.current
    if (!el) return
    const io = new IntersectionObserver(
      ([entry]) => {
        if (!entry.isIntersecting) return
        io.disconnect()
        // stagger word activation in reading order
        words.forEach((_, i) => {
          window.setTimeout(() => setActiveCount((c) => Math.max(c, i + 1)), i * 55)
        })
      },
      { threshold: 0.4 },
    )
    io.observe(el)
    return () => io.disconnect()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  return (
    <p ref={ref} className={className}>
      {words.map((word, i) => (
        <span
          key={i}
          className="transition-colors duration-700 ease-[cubic-bezier(0.32,0.72,0,1)]"
          style={{ color: i < activeCount ? 'currentColor' : 'color-mix(in srgb, currentColor 30%, transparent)' }}
        >
          {word}
          {i < words.length - 1 ? ' ' : ''}
        </span>
      ))}
    </p>
  )
}