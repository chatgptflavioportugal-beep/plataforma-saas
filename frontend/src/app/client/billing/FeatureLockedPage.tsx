import { Link, useLocation } from 'react-router-dom'

export function FeatureLockedPage() {
  const { state } = useLocation()
  const requiredPlan = (state as { requiredPlan?: string })?.requiredPlan
  const feature = (state as { feature?: string })?.feature

  return (
    <div className="flex min-h-[60vh] flex-col items-center justify-center text-center px-4">
      <div className="text-6xl mb-6">🔒</div>
      <h1 className="text-3xl font-bold text-gray-900">Recurso bloqueado</h1>
      <p className="mt-3 text-gray-500 max-w-md">
        {feature
          ? `Esta funcionalidade (${feature}) não está disponível no seu plano atual.`
          : 'Esta funcionalidade não está disponível no seu plano atual.'}
        {requiredPlan && ` Faça upgrade para o plano ${requiredPlan} para ter acesso.`}
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
