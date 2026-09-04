import { Component, type ErrorInfo, type ReactNode } from 'react'

interface ErrorBoundaryProps {
  children: ReactNode
}

interface ErrorBoundaryState {
  hasError: boolean
}

const RELOAD_FLAG_KEY = 'admin:error-boundary-reloaded'

function isChunkLoadError(error: Error): boolean {
  return (
    error.name === 'ChunkLoadError' ||
    /Failed to fetch dynamically imported module|error loading dynamically imported module|Importing a module script failed/i.test(
      error.message
    )
  )
}

export class ErrorBoundary extends Component<ErrorBoundaryProps, ErrorBoundaryState> {
  state: ErrorBoundaryState = { hasError: false }

  static getDerivedStateFromError(): ErrorBoundaryState {
    return { hasError: true }
  }

  componentDidCatch(error: Error, errorInfo: ErrorInfo) {
    console.error('[ErrorBoundary] erro não tratado na árvore de componentes:', error, errorInfo)

    // Chunk desatualizado (ex.: pod reiniciou/redeployou enquanto a aba ficava aberta) — recarrega uma vez.
    if (isChunkLoadError(error) && !sessionStorage.getItem(RELOAD_FLAG_KEY)) {
      sessionStorage.setItem(RELOAD_FLAG_KEY, '1')
      window.location.reload()
    }
  }

  private handleReload = () => {
    sessionStorage.removeItem(RELOAD_FLAG_KEY)
    window.location.reload()
  }

  render() {
    if (this.state.hasError) {
      return (
        <div className="flex h-screen flex-col items-center justify-center bg-gray-900 px-6 text-center text-white">
          <div className="mb-4 flex h-16 w-16 items-center justify-center rounded-full bg-red-900/30 border border-red-700/50">
            <svg className="h-8 w-8 text-red-400" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
              <path strokeLinecap="round" strokeLinejoin="round"
                d="M12 9v3.75m-9.303 3.376c-.866 1.5.217 3.374 1.948 3.374h14.71c1.73 0 2.813-1.874 1.948-3.374L13.949 3.378c-.866-1.5-3.032-1.5-3.898 0L2.697 16.126zM12 15.75h.007v.008H12v-.008z" />
            </svg>
          </div>
          <h2 className="mb-2 text-lg font-semibold">Algo deu errado</h2>
          <p className="mb-6 max-w-xs text-sm text-gray-400">
            Ocorreu um erro inesperado ao carregar esta página. Isso pode acontecer após uma atualização do sistema.
          </p>
          <button
            onClick={this.handleReload}
            className="rounded-md bg-primary-600 px-4 py-2 text-sm font-medium text-white hover:bg-primary-700"
          >
            Recarregar página
          </button>
        </div>
      )
    }

    return this.props.children
  }
}
