import { useQueryClient } from '@tanstack/react-query';
import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';

import { onSessionChange, refreshSession, setSession } from '../api/client';
import { authApi } from '../api/endpoints';
import type { Role, User } from '../api/types';

interface AuthState {
  /** The signed-in user, or null for visitors. */
  user: User | null;
  /** False until the first session check (refresh cookie) has finished. */
  ready: boolean;
  login: (email: string, password: string) => Promise<User>;
  register: (data: { name: string; email: string; password: string; phone?: string }) => Promise<User>;
  logout: () => Promise<void>;
  hasRole: (...roles: Role[]) => boolean;
  /** Replaces the cached user after a profile edit. */
  updateUser: (user: User) => void;
}

const AuthContext = createContext<AuthState | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient();
  const [user, setUser] = useState<User | null>(null);
  const [ready, setReady] = useState(false);

  useEffect(() => {
    const unsubscribe = onSessionChange((session) => setUser(session?.user ?? null));
    // A returning visitor may still have a valid refresh cookie
    refreshSession().finally(() => setReady(true));
    return () => {
      unsubscribe();
    };
  }, []);

  const login = useCallback(async (email: string, password: string) => {
    const session = await authApi.login({ email, password });
    setSession(session);
    return session.user!;
  }, []);

  const register = useCallback(async (data: { name: string; email: string; password: string; phone?: string }) => {
    const session = await authApi.register(data);
    setSession(session);
    return session.user!;
  }, []);

  const logout = useCallback(async () => {
    try {
      await authApi.logout();
    } finally {
      setSession(null);
      // Nothing from the previous user may stay in the cache
      queryClient.clear();
    }
  }, [queryClient]);

  const value = useMemo<AuthState>(
    () => ({
      user,
      ready,
      login,
      register,
      logout,
      hasRole: (...roles) => !!user?.role && roles.includes(user.role),
      updateUser: setUser,
    }),
    [user, ready, login, register, logout],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthState {
  const context = useContext(AuthContext);
  if (!context) throw new Error('useAuth must be used inside <AuthProvider>');
  return context;
}
