import { useState } from 'react'
import { CaretDown } from '@phosphor-icons/react'

export interface FaqItem {
  q: string
  a: string
}

const EASE = 'ease-[cubic-bezier(0.32,0.72,0,1)]'

export function Faq({ items }: { items: FaqItem[] }) {
  const [openIndex, setOpenIndex] = useState<number | null>(0)

  return (
    <div className="divide-y divide-[#272727] rounded-2xl border border-[#272727] bg-[#181818]">
      {items.map((item, i) => {
        const isOpen = openIndex === i
        return (
          <div key={item.q}>
            <button
              type="button"
              aria-expanded={isOpen}
              aria-controls={`faq-panel-${i}`}
              onClick={() => setOpenIndex(isOpen ? null : i)}
              className="flex w-full items-center justify-between gap-4 px-6 py-5 text-left transition-colors duration-300 hover:bg-[#1F1F1F] active:scale-[0.99] focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-[-2px] focus-visible:outline-white/40"
            >
              <span className="text-base font-medium text-white">{item.q}</span>
              <CaretDown
                weight="bold"
                className={`h-4 w-4 shrink-0 text-white/50 transition-transform duration-500 ${EASE} ${isOpen ? 'rotate-180' : ''}`}
              />
            </button>
            <div
              id={`faq-panel-${i}`}
              className={`grid overflow-hidden transition-all duration-500 ${EASE} ${
                isOpen ? 'grid-rows-[1fr] opacity-100' : 'grid-rows-[0fr] opacity-0'
              }`}
            >
              <div className="min-h-0">
                <p className="px-6 pb-5 text-sm leading-relaxed text-white/60">{item.a}</p>
              </div>
            </div>
          </div>
        )
      })}
    </div>
  )
}