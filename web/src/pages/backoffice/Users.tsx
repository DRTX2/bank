import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState, type SyntheticEvent } from 'react';

import { Can } from '../../components/Can';
import { Button, ErrorNote, Field, PageHeader, Section, Table } from '../../components/ui';
import { api } from '../../lib/api';
import { useAuth } from '../../lib/auth';
import { formatDate } from '../../lib/format';
import type { Role, UserView } from '../../lib/types';

export function Users() {
  const { session, can } = useAuth();
  const queryClient = useQueryClient();
  const users = useQuery({ queryKey: ['users'], queryFn: () => api<UserView[]>('/api/v1/users') });
  const roles = useQuery({ queryKey: ['roles'], queryFn: () => api<Role[]>('/api/v1/roles') });
  const [editing, setEditing] = useState<UserView | null>(null);
  const [selected, setSelected] = useState<string[]>([]);

  const refresh = () => queryClient.invalidateQueries({ queryKey: ['users'] });
  const assign = useMutation({
    mutationFn: () =>
      api(`/api/v1/users/${editing?.id ?? ''}/roles`, { method: 'PUT', body: { roles: selected } }),
    onSuccess: () => {
      setEditing(null);
      void refresh();
    },
  });
  const status = useMutation({
    mutationFn: (u: UserView) =>
      api(`/api/v1/users/${u.id}/status`, {
        method: 'PATCH',
        body: { status: u.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE' },
      }),
    onSuccess: () => void refresh(),
  });

  return (
    <>
      <PageHeader
        title="Usuarios"
        lead="Personal y clientes de la institución. Nadie puede cambiar sus propios roles."
      />
      <Can any={['users:manage']}>
        <CreateUser roles={roles.data ?? []} onDone={() => void refresh()} />
      </Can>
      <Section title="Directorio">
        <ErrorNote error={users.error ?? status.error} />
        {editing && (
          <div className="mb-4 rounded-lg border border-lapis bg-surface p-4">
            <p className="font-medium">Roles de {editing.fullName}</p>
            <div className="mt-3 flex flex-wrap gap-x-5 gap-y-2">
              {roles.data?.map((r) => (
                <label key={r.id} className="flex items-center gap-2 text-sm">
                  <input
                    type="checkbox"
                    checked={selected.includes(r.name)}
                    onChange={(e) => {
                      setSelected(
                        e.target.checked ? [...selected, r.name] : selected.filter((x) => x !== r.name),
                      );
                    }}
                  />
                  {r.name.toLowerCase().replaceAll('_', ' ')}
                </label>
              ))}
            </div>
            <ErrorNote error={assign.error} />
            <div className="mt-4 flex gap-2">
              <Button
                onClick={() => {
                  assign.mutate();
                }}
                disabled={assign.isPending || selected.length === 0}
              >
                Guardar roles
              </Button>
              <Button
                variant="quiet"
                onClick={() => {
                  setEditing(null);
                }}
              >
                Cancelar
              </Button>
            </div>
          </div>
        )}
        <Table head={['Nombre', 'Correo', 'Roles', 'Estado', 'Alta', '']}>
          {users.data?.map((u) => {
            const self = u.id === session?.user.id;
            return (
              <tr key={u.id}>
                <td className="px-4 py-3 font-medium">{u.fullName}</td>
                <td className="px-4 py-3 text-ink-2">{u.email}</td>
                <td className="px-4 py-3 text-xs">{u.roles.join(', ').toLowerCase().replaceAll('_', ' ')}</td>
                <td className={`px-4 py-3 ${u.locked ? 'text-risk' : ''}`}>
                  {u.locked ? 'Bloqueado' : 'Activo'}
                </td>
                <td className="px-4 py-3 text-ink-2">{formatDate(u.createdAt)}</td>
                <td className="px-4 py-3">
                  {can('users:manage') && !self && (
                    <div className="flex gap-1.5">
                      <Button
                        variant="quiet"
                        className="px-2 py-1 text-xs"
                        onClick={() => {
                          setEditing(u);
                          setSelected(u.roles);
                        }}
                      >
                        Roles
                      </Button>
                      <Button
                        variant="quiet"
                        className="px-2 py-1 text-xs"
                        onClick={() => {
                          status.mutate(u);
                        }}
                      >
                        {u.status === 'ACTIVE' ? 'Deshabilitar' : 'Habilitar'}
                      </Button>
                    </div>
                  )}
                </td>
              </tr>
            );
          })}
        </Table>
      </Section>
    </>
  );
}

function CreateUser({ roles, onDone }: { roles: Role[]; onDone: () => void }) {
  const [form, setForm] = useState({ fullName: '', email: '', password: '', role: 'TELLER' });
  const create = useMutation({
    mutationFn: () =>
      api('/api/v1/users', {
        method: 'POST',
        body: { fullName: form.fullName, email: form.email, password: form.password, roles: [form.role] },
      }),
    onSuccess: () => {
      setForm({ fullName: '', email: '', password: '', role: 'TELLER' });
      onDone();
    },
  });
  const set = (k: keyof typeof form) => (e: { target: { value: string } }) => {
    setForm({ ...form, [k]: e.target.value });
  };
  return (
    <Section title="Invitar a alguien del equipo">
      <form
        onSubmit={(e: SyntheticEvent) => {
          e.preventDefault();
          create.mutate();
        }}
        className="grid gap-4 sm:grid-cols-2 lg:grid-cols-5 lg:items-end"
      >
        <Field label="Nombre" required value={form.fullName} onChange={set('fullName')} />
        <Field label="Correo" type="email" required value={form.email} onChange={set('email')} />
        <Field
          label="Contraseña inicial"
          type="password"
          required
          minLength={10}
          value={form.password}
          onChange={set('password')}
        />
        <div className="flex flex-col gap-1.5">
          <label htmlFor="new-user-role" className="text-sm font-medium">
            Rol
          </label>
          <select
            id="new-user-role"
            className="rounded-md border border-line bg-surface px-3 py-2"
            value={form.role}
            onChange={set('role')}
          >
            {roles.map((r) => (
              <option key={r.id} value={r.name}>
                {r.name.toLowerCase().replaceAll('_', ' ')}
              </option>
            ))}
          </select>
        </div>
        <Button type="submit" disabled={create.isPending}>
          Crear usuario
        </Button>
        <div className="sm:col-span-2 lg:col-span-5">
          <ErrorNote error={create.error} />
        </div>
      </form>
    </Section>
  );
}
