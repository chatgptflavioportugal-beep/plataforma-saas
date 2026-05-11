import { QueryClient } from '@tanstack/react-query'

export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 5 * 60 * 1000,
      retry: (failureCount, error) => {
        const axiosError = error as { response?: { status: number } }
        const status = axiosError?.response?.status
        if (status === 401 || status === 402 || status === 403) return false
        return failureCount < 2
      },
    },
  },
})
