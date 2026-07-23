interface SpinnerProps {
  fullscreen?: boolean
  size?: 'sm' | 'md' | 'lg'
}

export function Spinner({ fullscreen = false, size = 'md' }: SpinnerProps) {
  const sizes = { sm: 'h-4 w-4', md: 'h-8 w-8', lg: 'h-12 w-12' }

  const spinner = (
    <div
      className={`${sizes[size]} animate-spin rounded-full border-2 border-gray-200 border-t-primary-600`}
      role="status"
      aria-label="Carregando"
    />
  )

  if (fullscreen) {
    return (
      <div className="flex h-screen items-center justify-center bg-gray-50">
        {spinner}
      </div>
    )
  }

  return spinner
}
