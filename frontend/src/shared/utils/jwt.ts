/** Decodifica o payload de um JWT sem validar assinatura (uso apenas para leitura de claims no client). */
export function decodeJwt<T = Record<string, unknown>>(token: string): T {
  const payload = token.split('.')[1]
  if (!payload) throw new Error('Token JWT inválido')

  const base64 = payload.replace(/-/g, '+').replace(/_/g, '/')
  const padded = base64 + '='.repeat((4 - (base64.length % 4)) % 4)

  const json = decodeURIComponent(
    atob(padded)
      .split('')
      .map((c) => '%' + c.charCodeAt(0).toString(16).padStart(2, '0'))
      .join('')
  )

  return JSON.parse(json) as T
}
