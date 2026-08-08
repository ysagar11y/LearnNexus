import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';

// Self-hosted so nothing is fetched from a third-party host at runtime — the
// design system's typography tokens name these two families explicitly.
import '@fontsource-variable/plus-jakarta-sans';
import '@fontsource-variable/fraunces';

import '@ds/styles.css';
import './styles/app.css';

import { App } from './App';
import { AuthProvider } from './lib/auth';
import { TenantProvider } from './lib/tenant';
import { ThemeProvider } from './lib/theme';
import { ApiError } from './lib/api';

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 30_000,
      refetchOnWindowFocus: false,
      retry: (failureCount, error) => {
        // Retrying an authorisation or validation failure just delays the error
        // the user needs to see; only transient faults are worth a second go.
        if (error instanceof ApiError && error.status < 500) return false;
        return failureCount < 2;
      },
    },
    mutations: { retry: false },
  },
});

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <ThemeProvider>
        <TenantProvider>
          <BrowserRouter>
            <AuthProvider>
              <App />
            </AuthProvider>
          </BrowserRouter>
        </TenantProvider>
      </ThemeProvider>
    </QueryClientProvider>
  </StrictMode>,
);
