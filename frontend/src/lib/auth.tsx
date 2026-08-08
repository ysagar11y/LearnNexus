import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react';
import type { ReactNode } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { api, bootstrapSession, clearSession, onSessionChange, storeSession } from './api';
import type { Profile, RoleCode, Session } from './types';

interface AuthContextValue {
  user: Profile | null;
  /** True until the stored session has been checked, so routes don't flash. */
  initialising: boolean;
  signIn: (email: string, password: string) => Promise<Profile>;
  signOut: () => Promise<void>;
  refreshProfile: () => Promise<void>;
  acceptInvite: (input: {
    token: string;
    password: string;
    firstName: string;
    lastName?: string;
  }) => Promise<Profile>;
  hasRole: (...roles: RoleCode[]) => boolean;
  isAdmin: boolean;
  isStaff: boolean;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<Profile | null>(null);
  const [initialising, setInitialising] = useState(true);
  const queryClient = useQueryClient();

  // Restore a session from the stored refresh token on first load.
  //
  // Guarded by a ref because refreshing is a *write*: it rotates the token. In
  // development React deliberately double-invokes effects, and a second restore
  // would present a token the first had already rotated out — which the server
  // rightly reads as a replay.
  const restoreStarted = useRef(false);

  useEffect(() => {
    if (restoreStarted.current) return;
    restoreStarted.current = true;

    // No cancellation flag here on purpose. The ref already guarantees this runs
    // exactly once, and bailing out on unmount would leave `initialising` stuck
    // at true — the app would sit on its loading spinner forever.
    (async () => {
      try {
        if (await bootstrapSession()) {
          setUser(await api.get<Profile>('/auth/me'));
        }
      } catch {
        clearSession();
      } finally {
        setInitialising(false);
      }
    })();
  }, []);

  // The API client clears the session when a refresh fails; mirror that here so
  // an expired session drops the user to sign-in rather than an empty shell.
  useEffect(
    () =>
      onSessionChange((signedIn) => {
        if (!signedIn) {
          setUser(null);
          queryClient.clear();
        }
      }),
    [queryClient],
  );

  const signIn = useCallback(
    async (email: string, password: string) => {
      const session = await api.publicPost<Session>('/auth/login', { email, password });
      storeSession(session);
      setUser(session.user);
      queryClient.clear();
      return session.user;
    },
    [queryClient],
  );

  const acceptInvite = useCallback(
    async (input: { token: string; password: string; firstName: string; lastName?: string }) => {
      const session = await api.publicPost<Session>('/auth/accept-invite', input);
      storeSession(session);
      setUser(session.user);
      return session.user;
    },
    [],
  );

  const signOut = useCallback(async () => {
    const refreshToken = localStorage.getItem('ln.refresh');
    try {
      await api.post('/auth/logout', { refreshToken });
    } catch {
      // A failed revoke must not trap the user in a signed-in shell.
    }
    clearSession();
    setUser(null);
    queryClient.clear();
  }, [queryClient]);

  const refreshProfile = useCallback(async () => {
    setUser(await api.get<Profile>('/auth/me'));
  }, []);

  const hasRole = useCallback(
    (...roles: RoleCode[]) => !!user && roles.some((role) => user.roles.includes(role)),
    [user],
  );

  const value = useMemo<AuthContextValue>(
    () => ({
      user,
      initialising,
      signIn,
      signOut,
      refreshProfile,
      acceptInvite,
      hasRole,
      isAdmin: hasRole('TENANT_ADMIN', 'PLATFORM_ADMIN'),
      isStaff: hasRole('TENANT_ADMIN', 'PLATFORM_ADMIN', 'INSTRUCTOR', 'AUTHOR', 'MANAGER'),
    }),
    [user, initialising, signIn, signOut, refreshProfile, acceptInvite, hasRole],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) throw new Error('useAuth must be used inside AuthProvider');
  return context;
}

/** Where a user lands after signing in, based on their most privileged role. */
export function landingPathFor(user: Profile): string {
  if (user.roles.includes('PLATFORM_ADMIN')) return '/platform';
  if (user.roles.includes('TENANT_ADMIN')) return '/admin';
  if (user.roles.includes('INSTRUCTOR') || user.roles.includes('AUTHOR')) return '/admin/courses';
  if (user.roles.includes('MANAGER')) return '/admin/reports';
  return '/';
}
