import { Link } from 'react-router-dom'
import { useAuth } from '@/contexts/AuthContext'
import { useTenant } from '@/contexts/TenantContext'

export function DashboardPage() {
  const { profile } = useAuth()
  const { currentTenant, trialDaysRemaining } = useTenant()

  const plan = currentTenant?.plan
  const subscription = currentTenant?.subscription

  return (
    <div className="space-y-8">
      <div>
        <h1 className="text-2xl font-bold text-gray-900">
          Olá, {profile?.full_name?.split(' ')[0]} 👋
        </h1>
        <p className="text-gray-500 mt-1">
          Bem-vindo ao painel de {currentTenant?.tenant?.name}
        </p>
      </div>

      {subscription?.status === 'trial' && (
        <div className="rounded-xl bg-amber-50 border border-amber-200 p-4 flex items-center justify-between">
          <div>
            <p className="font-medium text-amber-800">
              Você está no plano Trial
            </p>
            <p className="text-sm text-amber-700 mt-0.5">
              {trialDaysRemaining !== null
                ? `${trialDaysRemaining} dias restantes`
                : 'Trial ativo'}
            </p>
          </div>
          <Link
            to="/app/billing/plans"
            className="rounded-lg bg-amber-600 px-4 py-2 text-sm font-medium text-white hover:bg-amber-700"
          >
            Ver planos
          </Link>
        </div>
      )}

      <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
        <div className="rounded-xl bg-white border border-gray-100 p-6 shadow-sm">
          <h3 className="text-sm font-medium text-gray-500">Plano atual</h3>
          <p className="mt-2 text-2xl font-bold text-gray-900">{plan?.name ?? '—'}</p>
          <p className="text-sm text-gray-500 mt-1">
            {plan?.price_monthly === 0 ? 'Gratuito' : `R$ ${plan?.price_monthly}/mês`}
          </p>
        </div>

        <div className="rounded-xl bg-white border border-gray-100 p-6 shadow-sm">
          <h3 className="text-sm font-medium text-gray-500">Status</h3>
          <p className="mt-2 text-2xl font-bold text-gray-900 capitalize">
            {subscription?.status ?? '—'}
          </p>
        </div>

        <div className="rounded-xl bg-white border border-gray-100 p-6 shadow-sm">
          <h3 className="text-sm font-medium text-gray-500">Máximo de usuários</h3>
          <p className="mt-2 text-2xl font-bold text-gray-900">
            {plan?.max_users === -1 ? 'Ilimitado' : plan?.max_users ?? '—'}
          </p>
        </div>
      </div>

      <div>
        <h2 className="text-lg font-semibold text-gray-900 mb-4">Módulos disponíveis</h2>
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          <Link
            to="/app/pdf/merge"
            className="rounded-xl bg-white border border-gray-100 p-6 shadow-sm hover:border-primary-200 hover:shadow-md transition-all group"
          >
            <div className="text-3xl mb-3">📄</div>
            <h3 className="font-semibold text-gray-900 group-hover:text-primary-700">Merge de PDFs</h3>
            <p className="text-sm text-gray-500 mt-1">
              Una dois arquivos PDF em um único documento
            </p>
          </Link>
        </div>
      </div>
    </div>
  )
}
