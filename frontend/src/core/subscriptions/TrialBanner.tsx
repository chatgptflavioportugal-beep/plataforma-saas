import { Link } from 'react-router-dom'
import { useTenant } from '@/core/workspaces/TenantContext'

export function TrialBanner() {
  const { isTrialExpiringSoon, trialDaysRemaining, currentTenant } = useTenant()

  if (currentTenant?.subscription?.status !== 'trial') return null
  if (!isTrialExpiringSoon) return null

  const isLastDay = trialDaysRemaining === 0

  return (
    <div className={`px-4 py-2 text-center text-sm font-medium ${isLastDay ? 'bg-red-600 text-white' : 'bg-amber-100 text-amber-800'}`}>
      {isLastDay
        ? 'Seu trial expira hoje! '
        : `Seu trial expira em ${trialDaysRemaining} dia${trialDaysRemaining !== 1 ? 's' : ''}. `}
      <Link
        to="/app/billing/plans"
        className={`underline font-semibold ${isLastDay ? 'text-white' : 'text-amber-900'}`}
      >
        Fazer upgrade agora
      </Link>
    </div>
  )
}
