import { Link } from 'react-router-dom'

export function TrialExpiredPage() {
  return (
    <div className="flex min-h-[60vh] flex-col items-center justify-center text-center px-4">
      <div className="text-6xl mb-6">⏰</div>
      <h1 className="text-3xl font-bold text-gray-900">Trial expirado</h1>
      <p className="mt-3 text-gray-500 max-w-md">
        Seu período de trial chegou ao fim. Para continuar usando a plataforma, faça upgrade para um plano pago.
      </p>
      <Link
        to="/app/billing/plans"
        className="mt-8 inline-flex items-center rounded-xl bg-primary-600 px-6 py-3 text-base font-medium text-white hover:bg-primary-700"
      >
        Ver planos e fazer upgrade
      </Link>
    </div>
  )
}
