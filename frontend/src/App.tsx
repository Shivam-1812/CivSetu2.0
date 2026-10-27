import { lazy, Suspense, type ReactNode } from 'react';
import { Navigate, Route, Routes } from 'react-router';

import { RequireRole } from './auth/RequireRole';
import { MainLayout } from './components/layout/MainLayout';
import { Spinner } from './components/shared';
import type { Role } from './api/types';
import { Index } from './pages/Index';
import { IssuePage } from './pages/IssuePage';
import { Login } from './pages/Login';
import { MapView } from './pages/MapView';
import { NotFound } from './pages/NotFound';
import { Signup } from './pages/Signup';

// Pages most visitors never open are loaded on demand, keeping the first download small
const named = <T extends string>(loader: () => Promise<Record<T, React.ComponentType<any>>>, name: T) =>
  lazy(() => loader().then((module) => ({ default: module[name] })));

const ProfilePage = named(() => import('./pages/ProfilePage'), 'ProfilePage');
const Dashboard = named(() => import('./pages/Dashboard'), 'Dashboard');
const ReportIssue = named(() => import('./pages/ReportIssue'), 'ReportIssue');
const PortalLayout = named(() => import('./components/layout/PortalLayout'), 'PortalLayout');
const PortalDashboard = named(() => import('./pages/portal/PortalDashboard'), 'PortalDashboard');
const ManageIssues = named(() => import('./pages/portal/ManageIssues'), 'ManageIssues');
const PortalMap = named(() => import('./pages/portal/PortalMap'), 'PortalMap');
const Analytics = named(() => import('./pages/portal/Analytics'), 'Analytics');
const AdminUsersPage = named(() => import('./pages/admin/AdminUsersPage'), 'AdminUsersPage');
const AdminRoutingPage = named(() => import('./pages/admin/AdminRoutingPage'), 'AdminRoutingPage');
const AdminAuditPage = named(() => import('./pages/admin/AdminAuditPage'), 'AdminAuditPage');

/** A signed-in page, optionally limited to roles, loaded on demand. */
function Protected({ roles, children }: { roles?: Role[]; children: ReactNode }) {
  return (
    <RequireRole roles={roles}>
      <Suspense fallback={<Spinner />}>{children}</Suspense>
    </RequireRole>
  );
}

/** Pages inside a portal's sidebar layout share its loading state. */
function Lazy({ children }: { children: ReactNode }) {
  return <Suspense fallback={<Spinner />}>{children}</Suspense>;
}

export function App() {
  return (
    <Routes>
      <Route element={<MainLayout />}>
        {/* Public: browsing is open to everyone */}
        <Route index element={<Index />} />
        <Route path="map" element={<MapView />} />
        <Route path="issues/:id" element={<IssuePage />} />
        <Route path="login" element={<Login />} />
        <Route path="signup" element={<Signup />} />
        <Route path="register" element={<Navigate to="/signup" replace />} />

        <Route path="profile" element={<Protected><ProfilePage /></Protected>} />

        {/* Citizens */}
        <Route path="report" element={<Protected roles={['CITIZEN']}><ReportIssue /></Protected>} />
        <Route path="dashboard" element={<Protected roles={['CITIZEN']}><Dashboard /></Protected>} />

        {/* Department officials: their department's issues */}
        <Route path="authority" element={<Protected roles={['AUTHORITY']}><PortalLayout portal="authority" /></Protected>}>
          <Route index element={<Lazy><PortalDashboard portal="authority" /></Lazy>} />
          <Route path="issues" element={<Lazy><ManageIssues portal="authority" /></Lazy>} />
          <Route path="map" element={<Lazy><PortalMap /></Lazy>} />
          <Route path="analytics" element={<Lazy><Analytics /></Lazy>} />
        </Route>

        {/* Admins: city-wide view and system setup */}
        <Route path="admin" element={<Protected roles={['ADMIN']}><PortalLayout portal="admin" /></Protected>}>
          <Route index element={<Lazy><PortalDashboard portal="admin" /></Lazy>} />
          <Route path="issues" element={<Lazy><ManageIssues portal="admin" /></Lazy>} />
          <Route path="map" element={<Lazy><PortalMap /></Lazy>} />
          <Route path="analytics" element={<Lazy><Analytics /></Lazy>} />
          <Route path="users" element={<Lazy><AdminUsersPage /></Lazy>} />
          <Route path="routing" element={<Lazy><AdminRoutingPage /></Lazy>} />
          <Route path="audit" element={<Lazy><AdminAuditPage /></Lazy>} />
        </Route>

        <Route path="*" element={<NotFound />} />
      </Route>
    </Routes>
  );
}
