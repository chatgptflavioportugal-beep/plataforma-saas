import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useLocation, useNavigate } from 'react-router-dom'
import { subscriptionApi } from '@/shared/services/subscriptionApi'
import { Button } from '@/shared/components/Button'
import { Spinner } from '@/shared/components/Spinner'
import { useTenant } from '@/core/workspaces/TenantContext'
import type { ProfileModuleSubscription } from '@/shared/types'

// ─── Helpers ──────────────────────────────────────────────────────────────────

function brl(value: number) {
  if (value === 0) return 'Grátis'
  return `R$ ${value.toLocaleString('pt-BR', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
}

function formatDate(dateStr: string | null) {
  if (!dateStr) return '—'
  return new Date(dateStr).toLocaleDateString('pt-BR')
}

/** Retorna true se a assinatura ainda está dentro da validade (planos sem expiração, como Free, são sempre válidos) */
function isStillValid(expiresAt: string | null): boolean {
  if (!expiresAt) return true
  return new Date(expiresAt) > new Date()
}

type CancelState      = 'idle' | 'success' | 'error'
type ReactivateState  = 'idle' | 'success' | 'error'

// ─── Status Badge ─────────────────────────────────────────────────────────────

function StatusBadge({ status }: { status: ProfileModuleSubscription['status'] }) {
  const variants: Record<string, string> = {
    TRIAL:            'bg-amber-100 text-amber-700',
    TRIAL_CANCELLED:  'bg-amber-100 text-amber-700',
    ACTIVE:           'bg-green-100 text-green-700',
    PENDING_PAYMENT:  'bg-yellow-100 text-yellow-700',
    CANCELED:         'bg-red-100 text-red-600',
    EXPIRED:          'bg-gray-100 text-gray-500',
  }
  const labels: Record<string, string> = {
    TRIAL:            'Trial',
    TRIAL_CANCELLED:  'Trial cancelado',
    ACTIVE:           'Ativa',
    PENDING_PAYMENT:  'Aguardando pagamento',
    CANCELED:         'Cancelada',
    EXPIRED:          'Expirada',
  }
  return (
    <span className={`inline-block rounded-full px-2.5 py-0.5 text-xs font-semibold ${variants[status] ?? 'bg-gray-100 text-gray-500'}`}>
      {labels[status] ?? status}
    </span>
  )
}

/** Dias restantes de Trial a partir de trialEndAt (arredondado para cima, mínimo 0). */
function trialDaysRemaining(trialEndAt: string | null): number {
  if (!trialEndAt) return 0
  const diffMs = new Date(trialEndAt).getTime() - Date.now()
  return Math.max(0, Math.ceil(diffMs / (1000 * 60 * 60 * 24)))
}

function trialCountdownLabel(daysRemaining: number): string {
  if (daysRemaining <= 0) return 'Último dia do Trial'
  if (daysRemaining === 1) return 'Trial termina amanhã'
  return `Restam ${daysRemaining} dias de Trial`
}

// ─── Cancel Confirmation Modal ────────────────────────────────────────────────

type CancelModalProps = {
  subscription: ProfileModuleSubscription
  profileName: string
  onConfirm: () => void
  onClose: () => void
}

function CancelModal({ subscription, profileName, onConfirm, onClose }: CancelModalProps) {
  const isTrial = subscription.status === 'TRIAL'
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
      <div className="absolute inset-0 bg-black/50" onClick={onClose} />
      <div className="relative bg-white rounded-2xl shadow-2xl w-full max-w-md">
        {/* Header */}
        <div className="border-b border-gray-100 px-6 py-5">
          <h2 className="text-lg font-bold text-gray-900">{isTrial ? 'Cancelar Trial' : 'Cancelar assinatura'}</h2>
        </div>

        {/* Body */}
        <div className="px-6 py-5 space-y-4">
          <p className="text-sm text-gray-600">
            {isTrial
              ? 'Você está prestes a cancelar o Trial do módulo:'
              : 'Você está prestes a cancelar a assinatura do módulo:'}
          </p>

          <div className="rounded-xl border border-gray-200 bg-gray-50 px-4 py-3 space-y-2">
            <div className="flex justify-between text-sm">
              <span className="text-gray-500">Módulo</span>
              <span className="font-semibold text-gray-900">{subscription.moduleName}</span>
            </div>
            <div className="flex justify-between text-sm">
              <span className="text-gray-500">Plano</span>
              <span className="font-medium text-gray-700">{subscription.planName}</span>
            </div>
            <div className="flex justify-between text-sm">
              <span className="text-gray-500">Perfil</span>
              <span className="font-medium text-gray-700">{profileName}</span>
            </div>
            {subscription.expiresAt && (
              <div className="flex justify-between text-sm">
                <span className="text-gray-500">Válido até</span>
                <span className="font-medium text-gray-700">{formatDate(subscription.expiresAt)}</span>
              </div>
            )}
          </div>

          <div className="space-y-2">
            <p className="text-sm text-gray-500 leading-relaxed">
              Essa ação impedirá o uso do módulo após o período de expiração do plano atual.
            </p>
            <p className="text-sm text-gray-500 leading-relaxed">
              Você poderá continuar usando o módulo até a data de validade da assinatura.
              Após esse período, a renovação não será realizada.
            </p>
          </div>

          <p className="text-sm font-semibold text-red-600">Deseja continuar?</p>
        </div>

        {/* Footer */}
        <div className="border-t border-gray-100 px-6 py-4 flex gap-3">
          <Button variant="secondary" className="flex-1" onClick={onClose}>
            Voltar
          </Button>
          <Button
            variant="primary"
            className="flex-1 !bg-red-600 hover:!bg-red-700 focus:!ring-red-500"
            onClick={onConfirm}
          >
            Confirmar cancelamento
          </Button>
        </div>
      </div>
    </div>
  )
}

// ─── Cancel Processing Modal ──────────────────────────────────────────────────

function CancelProcessingModal() {
  return (
    <div className="fixed inset-0 z-[60] flex items-center justify-center p-4">
      <div className="absolute inset-0 bg-black/60" />
      <div className="relative bg-white rounded-2xl shadow-2xl w-full max-w-sm px-8 py-10 flex flex-col items-center gap-5 text-center">
        <Spinner size="lg" />
        <div>
          <p className="text-base font-bold text-gray-900">Cancelando assinatura...</p>
          <p className="mt-1.5 text-sm text-gray-500 leading-relaxed">
            Aguarde enquanto atualizamos sua assinatura.
          </p>
        </div>
      </div>
    </div>
  )
}

// ─── Cancel Success Modal ─────────────────────────────────────────────────────

type CancelSuccessModalProps = {
  subscription: ProfileModuleSubscription
  onClose: () => void
}

function CancelSuccessModal({ subscription, onClose }: CancelSuccessModalProps) {
  return (
    <div className="fixed inset-0 z-[60] flex items-center justify-center p-4">
      <div className="absolute inset-0 bg-black/50" />
      <div className="relative bg-white rounded-2xl shadow-2xl w-full max-w-sm px-8 py-10 flex flex-col items-center gap-5 text-center">
        <div className="h-16 w-16 rounded-full bg-green-100 flex items-center justify-center shrink-0">
          <svg className="h-8 w-8 text-green-600" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2.5}>
            <path strokeLinecap="round" strokeLinejoin="round" d="M5 13l4 4L19 7" />
          </svg>
        </div>

        <div>
          <p className="text-lg font-bold text-gray-900">Assinatura cancelada com sucesso!</p>
          <p className="mt-2 text-sm text-gray-500 leading-relaxed">
            O módulo <span className="font-semibold text-gray-700">{subscription.moduleName}</span> continuará
            disponível até o fim do período contratado.
            <br />
            A renovação automática não será realizada.
          </p>
          {subscription.expiresAt && (
            <p className="mt-3 text-sm font-medium text-gray-700">
              Disponível até:{' '}
              <span className="font-bold">{formatDate(subscription.expiresAt)}</span>
            </p>
          )}
        </div>

        <Button variant="primary" className="w-full" onClick={onClose}>
          Entendi
        </Button>
      </div>
    </div>
  )
}

// ─── Cancel Error Modal ───────────────────────────────────────────────────────

type CancelErrorModalProps = {
  onRetry: () => void
  onClose: () => void
}

function CancelErrorModal({ onRetry, onClose }: CancelErrorModalProps) {
  return (
    <div className="fixed inset-0 z-[60] flex items-center justify-center p-4">
      <div className="absolute inset-0 bg-black/50" onClick={onClose} />
      <div className="relative bg-white rounded-2xl shadow-2xl w-full max-w-sm px-8 py-10 flex flex-col items-center gap-5 text-center">
        <div className="h-16 w-16 rounded-full bg-red-100 flex items-center justify-center shrink-0">
          <svg className="h-8 w-8 text-red-600" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2.5}>
            <path strokeLinecap="round" strokeLinejoin="round" d="M12 9v4m0 4h.01M10.29 3.86L1.82 18a2 2 0 001.71 3h16.94a2 2 0 001.71-3L13.71 3.86a2 2 0 00-3.42 0z" />
          </svg>
        </div>

        <div>
          <p className="text-lg font-bold text-gray-900">Não foi possível cancelar a assinatura</p>
          <p className="mt-2 text-sm text-gray-500 leading-relaxed">
            Tivemos um problema ao processar o cancelamento.
            <br />
            Tente novamente em alguns instantes.
          </p>
        </div>

        <div className="flex gap-3 w-full">
          <Button variant="secondary" className="flex-1" onClick={onClose}>
            Fechar
          </Button>
          <Button variant="primary" className="flex-1" onClick={onRetry}>
            Tentar novamente
          </Button>
        </div>
      </div>
    </div>
  )
}

// ─── Reactivate Confirmation Modal ────────────────────────────────────────────

type ReactivateModalProps = {
  subscription: ProfileModuleSubscription
  profileName: string
  onConfirm: () => void
  onClose: () => void
}

function ReactivateModal({ subscription, profileName, onConfirm, onClose }: ReactivateModalProps) {
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
      <div className="absolute inset-0 bg-black/50" onClick={onClose} />
      <div className="relative bg-white rounded-2xl shadow-2xl w-full max-w-md">
        {/* Header */}
        <div className="border-b border-gray-100 px-6 py-5">
          <h2 className="text-lg font-bold text-gray-900">Reativar assinatura</h2>
        </div>

        {/* Body */}
        <div className="px-6 py-5 space-y-4">
          <p className="text-sm text-gray-600">
            Você está prestes a reativar a assinatura do módulo:
          </p>

          <div className="rounded-xl border border-gray-200 bg-gray-50 px-4 py-3 space-y-2">
            <div className="flex justify-between text-sm">
              <span className="text-gray-500">Módulo</span>
              <span className="font-semibold text-gray-900">{subscription.moduleName}</span>
            </div>
            <div className="flex justify-between text-sm">
              <span className="text-gray-500">Plano</span>
              <span className="font-medium text-gray-700">{subscription.planName}</span>
            </div>
            <div className="flex justify-between text-sm">
              <span className="text-gray-500">Perfil</span>
              <span className="font-medium text-gray-700">{profileName}</span>
            </div>
            {subscription.expiresAt && (
              <div className="flex justify-between text-sm">
                <span className="text-gray-500">Válido até</span>
                <span className="font-medium text-gray-700">{formatDate(subscription.expiresAt)}</span>
              </div>
            )}
          </div>

          <p className="text-sm text-gray-500 leading-relaxed">
            A renovação da assinatura será ativada novamente.
          </p>

          <p className="text-sm font-semibold text-gray-800">Deseja continuar?</p>
        </div>

        {/* Footer */}
        <div className="border-t border-gray-100 px-6 py-4 flex gap-3">
          <Button variant="secondary" className="flex-1" onClick={onClose}>
            Cancelar
          </Button>
          <Button variant="primary" className="flex-1" onClick={onConfirm}>
            Confirmar reativação
          </Button>
        </div>
      </div>
    </div>
  )
}

// ─── Reactivate Processing Modal ──────────────────────────────────────────────

function ReactivateProcessingModal() {
  return (
    <div className="fixed inset-0 z-[60] flex items-center justify-center p-4">
      <div className="absolute inset-0 bg-black/60" />
      <div className="relative bg-white rounded-2xl shadow-2xl w-full max-w-sm px-8 py-10 flex flex-col items-center gap-5 text-center">
        <Spinner size="lg" />
        <div>
          <p className="text-base font-bold text-gray-900">Reativando assinatura...</p>
          <p className="mt-1.5 text-sm text-gray-500 leading-relaxed">
            Aguarde enquanto atualizamos sua assinatura.
          </p>
        </div>
      </div>
    </div>
  )
}

// ─── Reactivate Success Modal ─────────────────────────────────────────────────

type ReactivateSuccessModalProps = {
  subscription: ProfileModuleSubscription
  onClose: () => void
}

function ReactivateSuccessModal({ subscription, onClose }: ReactivateSuccessModalProps) {
  return (
    <div className="fixed inset-0 z-[60] flex items-center justify-center p-4">
      <div className="absolute inset-0 bg-black/50" />
      <div className="relative bg-white rounded-2xl shadow-2xl w-full max-w-sm px-8 py-10 flex flex-col items-center gap-5 text-center">
        <div className="h-16 w-16 rounded-full bg-green-100 flex items-center justify-center shrink-0">
          <svg className="h-8 w-8 text-green-600" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2.5}>
            <path strokeLinecap="round" strokeLinejoin="round" d="M5 13l4 4L19 7" />
          </svg>
        </div>

        <div>
          <p className="text-lg font-bold text-gray-900">Assinatura reativada com sucesso!</p>
          <p className="mt-2 text-sm text-gray-500 leading-relaxed">
            A assinatura do módulo{' '}
            <span className="font-semibold text-gray-700">{subscription.moduleName}</span>{' '}
            voltou a ficar ativa.
          </p>
        </div>

        <Button variant="primary" className="w-full" onClick={onClose}>
          Entendi
        </Button>
      </div>
    </div>
  )
}

// ─── Reactivate Error Modal ───────────────────────────────────────────────────

type ReactivateErrorModalProps = {
  onRetry: () => void
  onClose: () => void
}

function ReactivateErrorModal({ onRetry, onClose }: ReactivateErrorModalProps) {
  return (
    <div className="fixed inset-0 z-[60] flex items-center justify-center p-4">
      <div className="absolute inset-0 bg-black/50" onClick={onClose} />
      <div className="relative bg-white rounded-2xl shadow-2xl w-full max-w-sm px-8 py-10 flex flex-col items-center gap-5 text-center">
        <div className="h-16 w-16 rounded-full bg-red-100 flex items-center justify-center shrink-0">
          <svg className="h-8 w-8 text-red-600" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2.5}>
            <path strokeLinecap="round" strokeLinejoin="round" d="M12 9v4m0 4h.01M10.29 3.86L1.82 18a2 2 0 001.71 3h16.94a2 2 0 001.71-3L13.71 3.86a2 2 0 00-3.42 0z" />
          </svg>
        </div>

        <div>
          <p className="text-lg font-bold text-gray-900">Não foi possível reativar a assinatura</p>
          <p className="mt-2 text-sm text-gray-500 leading-relaxed">
            Tivemos um problema ao processar sua solicitação.
            <br />
            Tente novamente em alguns instantes.
          </p>
        </div>

        <div className="flex gap-3 w-full">
          <Button variant="secondary" className="flex-1" onClick={onClose}>
            Fechar
          </Button>
          <Button variant="primary" className="flex-1" onClick={onRetry}>
            Tentar novamente
          </Button>
        </div>
      </div>
    </div>
  )
}

// ─── Subscription Card ────────────────────────────────────────────────────────

type SubscriptionCardProps = {
  subscription: ProfileModuleSubscription
  onCancel: () => void
  onReactivate: () => void
  onRenew: () => void
}

function SubscriptionCard({ subscription, onCancel, onReactivate, onRenew }: SubscriptionCardProps) {
  const isAnnual         = subscription.billingCycle === 'ANNUAL'
  const annualTotal      = subscription.annualMonthlyPrice * 12
  const isTrialCancelled = subscription.status === 'TRIAL_CANCELLED'
  const isTrial          = subscription.status === 'TRIAL' || isTrialCancelled
  // Só é possível cancelar um Trial (ou assinatura ativa) que ainda não foi cancelado
  const canCancel        = subscription.status === 'ACTIVE' || subscription.status === 'TRIAL'
  const isCanceled       = subscription.status === 'CANCELED'
  const isExpired        = subscription.status === 'EXPIRED' || subscription.status === 'PENDING_PAYMENT'
  // Permite reativar apenas se cancelada (assinatura ou Trial) E ainda dentro da validade
  const canReactivate    = (isCanceled || isTrialCancelled) && isStillValid(subscription.expiresAt)

  return (
    <div
      className={`rounded-2xl border bg-white p-6 shadow-sm flex flex-col gap-4 transition-opacity ${
        isExpired ? 'opacity-60' : ''
      }`}
    >
      {/* Module header */}
      <div className="flex items-center gap-3">
        {subscription.moduleIconPath ? (
          <img
            src={subscription.moduleIconPath}
            alt=""
            className="h-9 w-9 object-contain shrink-0"
          />
        ) : (
          <span className="h-9 w-9 rounded-xl bg-primary-100 flex items-center justify-center shrink-0">
            <span className="block h-4 w-4 rounded-md bg-primary-400" />
          </span>
        )}
        <div className="flex-1 min-w-0">
          <h3 className="text-base font-bold text-gray-900 truncate">
            {subscription.moduleName}
          </h3>
          <p className="text-xs text-gray-500">
            Plano: <span className="font-medium text-gray-700">{subscription.planName}</span>
          </p>
        </div>
        <StatusBadge status={subscription.status} />
      </div>

      {/* Details grid */}
      <div className="grid grid-cols-2 gap-x-6 gap-y-3 text-sm">
        <div>
          <p className="text-xs text-gray-400 mb-0.5">Cobrança</p>
          <p className="font-medium text-gray-700">{isAnnual ? 'Anual' : 'Mensal'}</p>
        </div>
        <div>
          <p className="text-xs text-gray-400 mb-0.5">Valor</p>
          {isAnnual ? (
            <div>
              <p className="font-bold text-gray-900">
                {annualTotal === 0 ? 'Grátis' : `${brl(annualTotal)}/ano`}
              </p>
              {annualTotal > 0 && (
                <p className="text-xs text-gray-500 mt-0.5">
                  em até 12x de {brl(subscription.annualMonthlyPrice)}
                </p>
              )}
            </div>
          ) : (
            <p className="font-bold text-gray-900">
              {subscription.monthlyPrice === 0 ? 'Grátis' : `${brl(subscription.monthlyPrice)}/mês`}
            </p>
          )}
        </div>
        <div>
          <p className="text-xs text-gray-400 mb-0.5">Início</p>
          <p className="font-medium text-gray-700">{formatDate(subscription.startedAt)}</p>
        </div>

        <div>
          <p className="text-xs text-gray-400 mb-0.5">
            {isExpired ? 'Expirou em' : isTrial ? 'Trial termina em' : 'Disponível até'}
          </p>
          <p className="font-medium text-gray-700">
            {formatDate(subscription.expiresAt)}
          </p>
        </div>
      </div>

      {/* Aviso de Trial em andamento (ainda não cancelado) */}
      {isTrial && !isTrialCancelled && (
        <div className="rounded-lg bg-amber-50 border border-amber-100 px-3 py-2.5 flex items-start gap-2">
          <svg className="h-4 w-4 text-amber-500 shrink-0 mt-0.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
            <path strokeLinecap="round" strokeLinejoin="round" d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z" />
          </svg>
          <p className="text-xs text-amber-700 leading-relaxed">
            {trialCountdownLabel(trialDaysRemaining(subscription.trialEndAt))}. Depois do Trial, a cobrança
            será iniciada automaticamente conforme o plano contratado.
          </p>
        </div>
      )}

      {/* Aviso de Trial cancelado — acesso mantido até o fim do período, sem cobrança */}
      {isTrialCancelled && (
        <div className="rounded-lg bg-amber-50 border border-amber-100 px-3 py-2.5 flex items-start gap-2">
          <svg className="h-4 w-4 text-amber-500 shrink-0 mt-0.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
            <path strokeLinecap="round" strokeLinejoin="round" d="M13 16h-1v-4h-1m1-4h.01M12 2a10 10 0 100 20A10 10 0 0012 2z" />
          </svg>
          <p className="text-xs text-amber-700 leading-relaxed">
            Trial cancelado — não haverá cobrança. Você continuará utilizando este módulo até{' '}
            <span className="font-semibold">{formatDate(subscription.expiresAt)}</span>. Depois dessa data o acesso será encerrado.
          </p>
        </div>
      )}

      {/* Aviso para canceladas */}
      {isCanceled && (
        <div className="rounded-lg bg-red-50 border border-red-100 px-3 py-2.5 flex items-start gap-2">
          <svg className="h-4 w-4 text-red-400 shrink-0 mt-0.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
            <path strokeLinecap="round" strokeLinejoin="round" d="M13 16h-1v-4h-1m1-4h.01M12 2a10 10 0 100 20A10 10 0 0012 2z" />
          </svg>
          <p className="text-xs text-red-600 leading-relaxed">
            Renovação automática cancelada.
            {subscription.expiresAt && (
              <> O módulo permanece disponível até <span className="font-semibold">{formatDate(subscription.expiresAt)}</span>.</>
            )}
          </p>
        </div>
      )}

      {/* Ações */}
      {(canCancel || canReactivate || isExpired) && (
        <div className="border-t border-gray-100 pt-3 flex flex-col gap-2">
          {isExpired && (
            <Button
              variant="primary"
              className="w-full !bg-red-600 hover:!bg-red-700 focus:!ring-red-500"
              onClick={onRenew}
            >
              Renovar
            </Button>
          )}
          {canReactivate && (
            <Button
              variant="primary"
              className="w-full"
              onClick={onReactivate}
            >
              {isTrialCancelled ? 'Reativar Trial' : 'Reativar assinatura'}
            </Button>
          )}
          {canCancel && (
            <button
              type="button"
              onClick={onCancel}
              className="text-sm text-red-500 hover:text-red-700 font-medium transition-colors text-left"
            >
              {isTrial ? 'Cancelar Trial' : 'Cancelar assinatura'}
            </button>
          )}
        </div>
      )}
    </div>
  )
}

// ─── Main Page ────────────────────────────────────────────────────────────────

export function SubscriptionsPage() {
  const { currentTenant } = useTenant()
  const location    = useLocation()
  const navigate    = useNavigate()
  const queryClient = useQueryClient()

  // ── Cancel state ──────────────────────────────────────────────────────────
  const [cancelTarget, setCancelTarget]   = useState<ProfileModuleSubscription | null>(null)
  const [cancelledSub, setCancelledSub]   = useState<ProfileModuleSubscription | null>(null)
  const [cancelState, setCancelState]     = useState<CancelState>('idle')

  // ── Reactivate state ──────────────────────────────────────────────────────
  const [reactivateTarget, setReactivateTarget]     = useState<ProfileModuleSubscription | null>(null)
  const [reactivatedSub, setReactivatedSub]         = useState<ProfileModuleSubscription | null>(null)
  const [reactivateState, setReactivateState]       = useState<ReactivateState>('idle')

  // Recebe flag de sucesso da PlansPage via navigate state
  const justSubscribed =
    (location.state as { justSubscribed?: boolean } | null)?.justSubscribed ?? false

  const profileName = currentTenant?.tenant.name ?? 'Seu perfil'
  const profileType = currentTenant?.tenant.type ?? 'individual'
  const typeLabel   = profileType === 'individual' ? 'Individual' : 'Empresarial'
  const typeColor   = profileType === 'individual'
    ? 'bg-blue-50 border-blue-200 text-blue-800'
    : 'bg-amber-50 border-amber-200 text-amber-800'

  // ── Query: busca assinaturas do perfil ativo ──────────────────────────────
  // staleTime: 0 e refetchOnMount: 'always' garantem que os dados são sempre
  // buscados novamente ao abrir a tela, sem depender de cache antigo.
  const {
    data: subscriptions = [],
    isLoading,
    isError,
  } = useQuery({
    queryKey: ['profile-subscriptions', currentTenant?.tenant.id],
    queryFn: async () => {
      const { data } = await subscriptionApi.get<ProfileModuleSubscription[]>('/api/v1/subscriptions/modules')
      return data
    },
    enabled: !!currentTenant,
    staleTime: 0,
    refetchOnMount: 'always',
  })

  // ── Mutation: cancelar assinatura ─────────────────────────────────────────
  const cancelMutation = useMutation({
    mutationFn: async (subscriptionId: string) => {
      await subscriptionApi.post(`/api/v1/subscriptions/modules/${subscriptionId}/cancel`)
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['profile-subscriptions'] })
      setCancelState('success')
    },
    onError: (err) => {
      console.error('[SubscriptionsPage] Erro ao cancelar assinatura:', err)
      setCancelState('error')
    },
  })

  // ── Mutation: reativar assinatura ─────────────────────────────────────────
  const reactivateMutation = useMutation({
    mutationFn: async (subscriptionId: string) => {
      await subscriptionApi.post(`/api/v1/subscriptions/modules/${subscriptionId}/reactivate`)
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['profile-subscriptions'] })
      setReactivateState('success')
    },
    onError: (err) => {
      console.error('[SubscriptionsPage] Erro ao reativar assinatura:', err)
      setReactivateState('error')
    },
  })

  // ── Handlers: cancelamento ────────────────────────────────────────────────

  function handleConfirmCancel() {
    if (!cancelTarget) return
    const target = cancelTarget
    setCancelledSub(target)
    setCancelTarget(null)
    cancelMutation.mutate(target.id)
  }

  function handleCancelRetry() {
    if (!cancelledSub) return
    setCancelState('idle')
    cancelMutation.mutate(cancelledSub.id)
  }

  function handleCancelSuccessClose() {
    setCancelState('idle')
    setCancelledSub(null)
  }

  // ── Handlers: reativação ──────────────────────────────────────────────────

  function handleConfirmReactivate() {
    if (!reactivateTarget) return
    const target = reactivateTarget
    setReactivatedSub(target)
    setReactivateTarget(null)
    reactivateMutation.mutate(target.id)
  }

  function handleReactivateRetry() {
    if (!reactivatedSub) return
    setReactivateState('idle')
    reactivateMutation.mutate(reactivatedSub.id)
  }

  function handleReactivateSuccessClose() {
    setReactivateState('idle')
    setReactivatedSub(null)
  }

  // ── Render ────────────────────────────────────────────────────────────────
  return (
    <div className="space-y-6">

      {/* Banner de sucesso (vindo da confirmação de assinatura na PlansPage) */}
      {justSubscribed && (
        <div className="rounded-xl bg-green-50 border border-green-200 px-5 py-4 flex items-start gap-3">
          <span className="text-green-500 text-xl shrink-0 mt-0.5">✓</span>
          <div>
            <p className="font-semibold text-green-800">Assinatura realizada com sucesso!</p>
            <p className="text-sm text-green-700 mt-0.5">
              Os módulos selecionados foram vinculados ao perfil atual.
            </p>
          </div>
        </div>
      )}

      {/* Cabeçalho da página */}
      <div className="flex items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Assinaturas</h1>
          <p className="text-sm text-gray-500 mt-1">
            Módulos contratados para o perfil ativo.
          </p>
        </div>
        <Button variant="secondary" onClick={() => navigate('/app/billing/plans')}>
          Ver planos disponíveis
        </Button>
      </div>

      {/* Banner do perfil ativo */}
      {currentTenant && (
        <div className={`rounded-xl border px-5 py-3.5 flex items-center gap-3 ${typeColor}`}>
          <div className="h-8 w-8 rounded-full bg-white/70 flex items-center justify-center shrink-0 shadow-sm">
            <span className="text-sm font-bold">
              {profileName.charAt(0).toUpperCase()}
            </span>
          </div>
          <div className="min-w-0">
            <p className="text-xs font-semibold uppercase tracking-wide opacity-70">
              Perfil atual
            </p>
            <p className="font-bold text-base leading-tight truncate">{profileName}</p>
          </div>
          <span className="ml-auto shrink-0 rounded-full bg-white/60 border border-current/20 px-2.5 py-0.5 text-xs font-semibold">
            Tipo: {typeLabel}
          </span>
        </div>
      )}

      {/* Conteúdo */}
      {isLoading ? (
        <div className="text-center py-16 text-gray-400 text-sm">
          Carregando assinaturas...
        </div>
      ) : isError ? (
        <div className="text-center py-16 space-y-1">
          <p className="text-gray-600 font-medium">Não foi possível carregar as assinaturas.</p>
          <p className="text-sm text-gray-400">Tente novamente mais tarde.</p>
        </div>
      ) : subscriptions.length === 0 ? (
        <div className="text-center py-16 space-y-4">
          <p className="text-gray-500">
            Nenhuma assinatura encontrada para este perfil.
          </p>
          <Button
            variant="primary"
            onClick={() => navigate('/app/billing/plans')}
          >
            Ver planos disponíveis
          </Button>
        </div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-4">
          {subscriptions.map(sub => (
            <SubscriptionCard
              key={sub.id}
              subscription={sub}
              onCancel={() => setCancelTarget(sub)}
              onReactivate={() => setReactivateTarget(sub)}
              onRenew={() => navigate('/app/billing/plans')}
            />
          ))}
        </div>
      )}

      {/* ── Modais de cancelamento ── */}

      {cancelTarget && !cancelMutation.isPending && cancelState === 'idle' && (
        <CancelModal
          subscription={cancelTarget}
          profileName={profileName}
          onConfirm={handleConfirmCancel}
          onClose={() => setCancelTarget(null)}
        />
      )}

      {cancelMutation.isPending && <CancelProcessingModal />}

      {cancelState === 'success' && cancelledSub && (
        <CancelSuccessModal
          subscription={cancelledSub}
          onClose={handleCancelSuccessClose}
        />
      )}

      {cancelState === 'error' && (
        <CancelErrorModal
          onRetry={handleCancelRetry}
          onClose={() => {
            setCancelState('idle')
            setCancelledSub(null)
          }}
        />
      )}

      {/* ── Modais de reativação ── */}

      {reactivateTarget && !reactivateMutation.isPending && reactivateState === 'idle' && (
        <ReactivateModal
          subscription={reactivateTarget}
          profileName={profileName}
          onConfirm={handleConfirmReactivate}
          onClose={() => setReactivateTarget(null)}
        />
      )}

      {reactivateMutation.isPending && <ReactivateProcessingModal />}

      {reactivateState === 'success' && reactivatedSub && (
        <ReactivateSuccessModal
          subscription={reactivatedSub}
          onClose={handleReactivateSuccessClose}
        />
      )}

      {reactivateState === 'error' && (
        <ReactivateErrorModal
          onRetry={handleReactivateRetry}
          onClose={() => {
            setReactivateState('idle')
            setReactivatedSub(null)
          }}
        />
      )}
    </div>
  )
}
