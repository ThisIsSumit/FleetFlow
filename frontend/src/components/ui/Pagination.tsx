import { ChevronLeft, ChevronRight } from 'lucide-react'
import { Button } from './Button'
export function Pagination({ page, totalPages, total, onPage }: { page: number; totalPages: number; total: number; onPage: (p: number) => void }) {
  return (<div className="flex items-center justify-between border-t px-4 py-3 text-sm text-muted-foreground">
    <span>{total} result{total === 1 ? '' : 's'}</span>
    <div className="flex items-center gap-2">
      <Button variant="secondary" size="sm" aria-label="Previous page" disabled={page <= 0} onClick={() => onPage(page - 1)}><ChevronLeft className="h-4 w-4" /></Button>
      <span>Page {totalPages === 0 ? 0 : page + 1} of {totalPages}</span>
      <Button variant="secondary" size="sm" aria-label="Next page" disabled={page + 1 >= totalPages} onClick={() => onPage(page + 1)}><ChevronRight className="h-4 w-4" /></Button>
    </div>
  </div>)
}
