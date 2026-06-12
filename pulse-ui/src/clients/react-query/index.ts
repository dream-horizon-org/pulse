import {QueryClient} from '@tanstack/react-query'

export const queryClient: QueryClient = new QueryClient({
    defaultOptions: {
        queries: {
          refetchOnWindowFocus: false,
          retry: 2,
        }
      }
})