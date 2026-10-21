import type { ReactNode } from 'react';
import { createBrowserRouter, Navigate, RouterProvider } from 'react-router';

import { Shell } from './components/Shell';
import { Empty } from './components/ui';
import { useAuth } from './lib/auth';
import { Login } from './pages/Login';
import { TransfersPage } from './pages/TransfersPage';
import { Audit } from './pages/backoffice/Audit';
import { Customers } from './pages/backoffice/Customers';
import { Operations } from './pages/backoffice/Operations';
import { ReviewQueue } from './pages/backoffice/ReviewQueue';
import { Roles } from './pages/backoffice/Roles';
import { Tenants } from './pages/backoffice/Tenants';
import { Users } from './pages/backoffice/Users';
import { BulkPayments } from './pages/customer/BulkPayments';
import { Overview } from './pages/customer/Overview';
import { Simulators } from './pages/customer/Simulators';
import { TransferPage } from './pages/customer/TransferPage';

function RequireAuth({ children }: { children: ReactNode }) {
  const { session } = useAuth();
  return session ? children : <Navigate to="/login" replace />;
}

function Guard({ perms, children }: { perms: string[]; children: ReactNode }) {
  const { can } = useAuth();
  return can(...perms) ? children : <Empty>Tu rol no tiene acceso a esta sección.</Empty>;
}

/** Landing page depends on who you are: customers see their money, staff see their work queue. */
function Home() {
  const { can } = useAuth();
  const target = can('accounts:read:own')
    ? '/banca'
    : can('tenants:manage')
      ? '/plataforma/instituciones'
      : can('risk:review')
        ? '/backoffice/riesgo'
        : can('ops:monitor')
          ? '/backoffice/operaciones'
          : can('accounts:read:any')
            ? '/backoffice/clientes'
            : can('audit:read')
              ? '/backoffice/auditoria'
              : '/simuladores';
  return <Navigate to={target} replace />;
}

const router = createBrowserRouter([
  { path: '/login', element: <Login /> },
  {
    element: (
      <RequireAuth>
        <Shell />
      </RequireAuth>
    ),
    children: [
      { index: true, element: <Home /> },
      {
        path: 'banca',
        element: (
          <Guard perms={['accounts:read:own']}>
            <Overview />
          </Guard>
        ),
      },
      {
        path: 'banca/transferir',
        element: (
          <Guard perms={['transfers:create:own']}>
            <TransferPage />
          </Guard>
        ),
      },
      {
        path: 'banca/pagos-masivos',
        element: (
          <Guard perms={['payments:batch']}>
            <BulkPayments />
          </Guard>
        ),
      },
      {
        path: 'simuladores',
        element: (
          <Guard perms={['simulations:use']}>
            <Simulators />
          </Guard>
        ),
      },
      {
        path: 'transferencias',
        element: (
          <Guard perms={['transfers:read:own', 'transfers:read:any']}>
            <TransfersPage />
          </Guard>
        ),
      },
      {
        path: 'backoffice/operaciones',
        element: (
          <Guard perms={['ops:monitor']}>
            <Operations />
          </Guard>
        ),
      },
      {
        path: 'backoffice/aprobaciones',
        element: (
          <Guard perms={['transfers:approve']}>
            <ReviewQueue key="approval" mode="approval" />
          </Guard>
        ),
      },
      {
        path: 'backoffice/riesgo',
        element: (
          <Guard perms={['risk:review']}>
            <ReviewQueue key="risk" mode="risk" />
          </Guard>
        ),
      },
      {
        path: 'backoffice/clientes',
        element: (
          <Guard perms={['accounts:read:any']}>
            <Customers />
          </Guard>
        ),
      },
      {
        path: 'backoffice/usuarios',
        element: (
          <Guard perms={['users:read']}>
            <Users />
          </Guard>
        ),
      },
      {
        path: 'backoffice/roles',
        element: (
          <Guard perms={['roles:manage']}>
            <Roles />
          </Guard>
        ),
      },
      {
        path: 'backoffice/auditoria',
        element: (
          <Guard perms={['audit:read']}>
            <Audit />
          </Guard>
        ),
      },
      {
        path: 'plataforma/instituciones',
        element: (
          <Guard perms={['tenants:manage']}>
            <Tenants />
          </Guard>
        ),
      },
      { path: '*', element: <Empty>Esta página no existe.</Empty> },
    ],
  },
]);

export function App() {
  return <RouterProvider router={router} />;
}
