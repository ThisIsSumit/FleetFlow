import { Link } from 'react-router-dom'
import { Hammer } from 'lucide-react'
import { EmptyState } from '@/components/ui/States'
import { Button } from '@/components/ui/Button'
import { Card } from '@/components/ui/Card'
export function ComingSoon({ name }: { name: string }) {
  return (<Card><EmptyState icon={<Hammer className="h-5 w-5" />} title={`${name} is not built yet`}
    description="This is a planned feature and is intentionally not simulated with fake data. It is scheduled for a later release." action={<Link to="/dashboard"><Button variant="secondary">Back to overview</Button></Link>} /></Card>)
}
export const NotFound = () => (<div className="grid min-h-screen place-items-center p-6"><EmptyState title="Page not found" description="The page you are looking for does not exist." action={<Link to="/"><Button>Go home</Button></Link>} /></div>)
