import { useQueryClient } from '@tanstack/react-query';
import {
  ArrowLeftRight,
  Bell,
  Building2,
  Calculator,
  FileStack,
  Gauge,
  History,
  KeyRound,
  Landmark,
  LogOut,
  Menu,
  ScrollText,
  ShieldAlert,
  Stamp,
  Users,
  Wallet,
  type LucideIcon,
} from 'lucide-react';
import { useState } from 'react';
import { NavLink, Outlet } from 'react-router';

import { useAuth } from '../lib/auth';
import { money } from '../lib/format';
import { useStomp } from '../lib/realtime';
import type { Notification } from '../lib/types';

interface Item {
  to: string;
  label: string;
  icon: LucideIcon;
  perms: string[];
}

const groups: { title: string; items: Item[] }[] = [
  {
    title: 'Mi banca',
    items: [
      { to: '/banca', label: 'Mis cuentas', icon: Wallet, perms: ['accounts:read:own'] },
      { to: '/banca/transferir', label: 'Transferir', icon: ArrowLeftRight, perms: ['transfers:create:own'] },
      { to: '/banca/pagos-masivos', label: 'Pagos masivos', icon: FileStack, perms: ['payments:batch'] },
      { to: '/simuladores', label: 'Simuladores', icon: Calculator, perms: ['simulations:use'] },
    ],
  },
  {
    title: 'Operación',
    items: [
      { to: '/backoffice/operaciones', label: 'Operaciones en vivo', icon: Gauge, perms: ['ops:monitor'] },
      { to: '/backoffice/aprobaciones', label: 'Aprobaciones', icon: Stamp, perms: ['transfers:approve'] },
      { to: '/backoffice/riesgo', label: 'Revisión de riesgo', icon: ShieldAlert, perms: ['risk:review'] },
      {
        to: '/backoffice/clientes',
        label: 'Clientes y cuentas',
        icon: Landmark,
        perms: ['accounts:read:any'],
      },
      {
        to: '/transferencias',
        label: 'Transferencias',
        icon: History,
        perms: ['transfers:read:any', 'transfers:read:own'],
      },
    ],
  },
  {
    title: 'Administración',
    items: [
      { to: '/backoffice/usuarios', label: 'Usuarios', icon: Users, perms: ['users:read'] },
      { to: '/backoffice/roles', label: 'Roles y permisos', icon: KeyRound, perms: ['roles:manage'] },
      { to: '/backoffice/auditoria', label: 'Auditoría', icon: ScrollText, perms: ['audit:read'] },
      { to: '/plataforma/instituciones', label: 'Instituciones', icon: Building2, perms: ['tenants:manage'] },
    ],
  },
];

export function Shell() {
  const { session, can, logout } = useAuth();
  const queryClient = useQueryClient();
  const [open, setOpen] = useState(false);
  const [toasts, setToasts] = useState<Notification[]>([]);

  useStomp<Notification>('/user/queue/notifications', (n) => {
    setToasts((t) => [n, ...t].slice(0, 3));
    void queryClient.invalidateQueries();
    setTimeout(() => {
      setToasts((t) => t.filter((x) => x !== n));
    }, 6000);
  });

  if (!session) return null;
  const user = session.user;

  const nav = (
    <nav aria-label="Principal" className="flex flex-col gap-6">
      {groups.map((g) => {
        const visible = g.items.filter((i) => can(...i.perms));
        if (visible.length === 0) return null;
        return (
          <div key={g.title}>
            <p className="mb-1.5 px-3 text-xs font-medium text-on-lapis/60">{g.title}</p>
            <ul className="flex flex-col gap-0.5">
              {visible.map((i) => (
                <li key={i.to}>
                  <NavLink
                    to={i.to}
                    end
                    onClick={() => {
                      setOpen(false);
                    }}
                    className={({ isActive }) =>
                      `flex items-center gap-2.5 rounded-md px-3 py-2 text-sm ${
                        isActive
                          ? 'bg-on-lapis/15 font-medium text-on-lapis'
                          : 'text-on-lapis/80 hover:bg-on-lapis/10'
                      }`
                    }
                  >
                    <i.icon className="size-4" aria-hidden />
                    {i.label}
                  </NavLink>
                </li>
              ))}
            </ul>
          </div>
        );
      })}
    </nav>
  );

  return (
    <div className="min-h-dvh lg:grid lg:grid-cols-[260px_1fr]">
      <aside
        className={`fixed inset-y-0 left-0 z-30 flex w-[260px] flex-col bg-lapis text-on-lapis transition-transform lg:sticky lg:top-0 lg:h-dvh lg:translate-x-0 ${
          open ? 'translate-x-0' : '-translate-x-full'
        }`}
      >
        <div className="textile h-3" aria-hidden />
        <div className="px-5 pb-6 pt-5">
          <p className="text-xl font-semibold tracking-tight">Kuntur</p>
          <p className="text-sm text-on-lapis/70">{user.tenantName}</p>
        </div>
        <div className="flex-1 overflow-y-auto px-2">{nav}</div>
        <div className="border-t border-on-lapis/15 px-5 py-4 text-sm">
          <p className="font-medium">{user.fullName}</p>
          <p className="truncate text-on-lapis/70">
            {user.roles.join(', ').toLowerCase().replaceAll('_', ' ')}
          </p>
          <button
            type="button"
            onClick={() => void logout()}
            className="mt-3 inline-flex items-center gap-2 text-on-lapis/80 hover:text-on-lapis"
          >
            <LogOut className="size-4" aria-hidden /> Cerrar sesión
          </button>
        </div>
      </aside>
      {open && (
        <button
          type="button"
          aria-label="Cerrar menú"
          className="fixed inset-0 z-20 bg-black/40 lg:hidden"
          onClick={() => {
            setOpen(false);
          }}
        />
      )}

      <div className="min-w-0">
        <div className="sticky top-0 z-10 flex items-center justify-between border-b border-line bg-bg/90 px-4 py-3 backdrop-blur lg:hidden">
          <button
            type="button"
            aria-label="Abrir menú"
            onClick={() => {
              setOpen(true);
            }}
          >
            <Menu className="size-5" />
          </button>
          <span className="font-semibold">Kuntur</span>
          <Bell className="size-5 text-ink-2" aria-hidden />
        </div>
        <main className="mx-auto max-w-6xl px-4 py-8 sm:px-8 lg:py-12">
          <Outlet />
        </main>
      </div>

      <div
        aria-live="polite"
        className="fixed bottom-4 right-4 z-40 flex w-80 max-w-[calc(100vw-2rem)] flex-col gap-2"
      >
        {toasts.map((t) => (
          <div
            key={`${t.transferId}-${t.type}`}
            className="rounded-lg border border-line bg-surface p-3 text-sm shadow-lg"
          >
            <p className="font-medium">
              {t.direction === 'IN' ? 'Recibiste ' : 'Enviaste '}
              <span className="num">{money(t.amount, t.currency)}</span>
            </p>
            <p className="text-ink-2">
              {t.direction === 'IN' ? 'Desde' : 'A'} {t.counterparty} (
              {t.status.toLowerCase().replaceAll('_', ' ')})
            </p>
          </div>
        ))}
      </div>
    </div>
  );
}
