import type { ReactNode } from 'react';
import { Navigate, useLocation } from 'react-router';

import type { Role } from '../api/types';
import { Spinner } from '../components/shared';
import { useAuth } from './AuthProvider';

/**
 * Shows the page only to signed-in users with one of the roles (any role if none given).
 * This only shapes the UI; the API enforces every permission itself.
 */
export function RequireRole({ roles, children }: { roles?: Role[]; children: ReactNode }) {
  const { user, ready } = useAuth();
  const location = useLocation();

  if (!ready) return <Spinner label="Checking your session" />;
  if (!user) return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  if (roles && (!user.role || !roles.includes(user.role))) {
    return (
      <div className="mx-auto max-w-lg p-8 text-center">
        <h1 className="text-xl font-semibold">Not available for your account</h1>
        <p className="mt-2 text-slate-600">This page is for {roles.map((role) => role.toLowerCase()).join(' or ')} accounts.</p>
      </div>
    );
  }
  return <>{children}</>;
}
