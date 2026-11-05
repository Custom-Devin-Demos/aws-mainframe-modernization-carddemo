import { createContext, useCallback, useContext, useMemo, useState, type ReactNode } from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import type { SessionUser } from '../api/types';
import { clearSession, getUser, saveSession } from './sessionStore';
import { programPath } from '../programs';

interface SessionContextValue {
  user: SessionUser | null;
  signIn: (token: string, user: SessionUser) => void;
  signOut: () => void;
}

const SessionContext = createContext<SessionContextValue | null>(null);

export function SessionProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<SessionUser | null>(() => getUser());
  const signIn = useCallback((token: string, u: SessionUser) => {
    saveSession(token, u);
    setUser(u);
  }, []);
  const signOut = useCallback(() => {
    clearSession();
    setUser(null);
  }, []);
  const value = useMemo(() => ({ user, signIn, signOut }), [user, signIn, signOut]);
  return <SessionContext.Provider value={value}>{children}</SessionContext.Provider>;
}

export function useSession(): SessionContextValue {
  const ctx = useContext(SessionContext);
  if (!ctx) throw new Error('useSession must be used inside <SessionProvider>');
  return ctx;
}

/** Equivalent of the EIBCALEN = 0 check: no session means back to sign-on. */
export function RequireSession({ children, admin = false }: { children: ReactNode; admin?: boolean }) {
  const { user } = useSession();
  const location = useLocation();
  if (!user) return <Navigate to={programPath('COSGN00C')} replace state={{ from: location.pathname }} />;
  if (admin && user.userType !== 'A') return <Navigate to={programPath('COMEN01C')} replace />;
  return <>{children}</>;
}
