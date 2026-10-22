import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Check } from 'lucide-react';
import { useState, type SyntheticEvent } from 'react';

import { Button, ErrorNote, Field, PageHeader, Section } from '../../components/ui';
import { api } from '../../lib/api';
import { roleLabel } from '../../lib/format';
import type { PermissionInfo, Role } from '../../lib/types';

/**
 * Permission matrix: every role (column) against every permission (row). System roles are read-only;
 * custom roles toggle in place. Platform-scoped permissions are not offered: the API would refuse them anyway.
 */
export function Roles() {
  const queryClient = useQueryClient();
  const roles = useQuery({ queryKey: ['roles'], queryFn: () => api<Role[]>('/api/v1/roles') });
  const permissions = useQuery({
    queryKey: ['permissions'],
    queryFn: () => api<PermissionInfo[]>('/api/v1/permissions'),
  });

  const update = useMutation({
    mutationFn: (r: Role) =>
      api(`/api/v1/roles/${r.id}`, {
        method: 'PUT',
        body: { description: r.description, permissions: r.permissions },
      }),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ['roles'] }),
  });
  const remove = useMutation({
    mutationFn: (r: Role) => api(`/api/v1/roles/${r.id}`, { method: 'DELETE' }),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ['roles'] }),
  });

  const tenantPerms = (permissions.data ?? []).filter((p) => p.scope === 'TENANT');
  const groups = Map.groupBy(tenantPerms, (p) => p.code.split(':')[0] ?? p.code);
  const cols = roles.data ?? [];

  function toggle(role: Role, code: string) {
    const has = role.permissions.includes(code);
    const next = has ? role.permissions.filter((p) => p !== code) : [...role.permissions, code];
    if (next.length > 0) update.mutate({ ...role, permissions: next });
  }

  return (
    <>
      <PageHeader
        title="Roles y permisos"
        lead="Los permisos son granulares (recurso:acción:alcance). Los roles del sistema no se editan; crea roles propios para necesidades específicas."
      />
      <CreateRole
        permissions={tenantPerms}
        onDone={() => void queryClient.invalidateQueries({ queryKey: ['roles'] })}
      />
      <ErrorNote error={roles.error ?? update.error ?? remove.error} />
      <Section title="Matriz">
        <div className="relative overflow-x-auto rounded-lg border border-line bg-surface">
          <table className="text-sm">
            <thead>
              <tr className="border-b border-line">
                <th scope="col" className="sticky left-0 z-10 bg-surface px-4 py-3 text-left font-medium">
                  Permiso
                </th>
                {cols.map((r) => (
                  <th
                    key={r.id}
                    scope="col"
                    className="min-w-24 px-2 py-3 text-center align-bottom text-xs font-medium"
                  >
                    <span className="block">{roleLabel(r.name)}</span>
                    {r.system ? (
                      <span className="font-normal text-ink-2">sistema</span>
                    ) : (
                      <button
                        type="button"
                        className="font-normal text-risk underline"
                        onClick={() => {
                          remove.mutate(r);
                        }}
                      >
                        eliminar
                      </button>
                    )}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {[...groups.entries()].map(([resource, perms]) => (
                <GroupRows key={resource} resource={resource} perms={perms} roles={cols} onToggle={toggle} />
              ))}
            </tbody>
          </table>
        </div>
      </Section>
    </>
  );
}

function GroupRows({
  resource,
  perms,
  roles,
  onToggle,
}: {
  resource: string;
  perms: PermissionInfo[];
  roles: Role[];
  onToggle: (r: Role, code: string) => void;
}) {
  return (
    <>
      <tr className="bg-surface-2">
        <th
          scope="rowgroup"
          colSpan={roles.length + 1}
          className="sticky left-0 px-4 py-1.5 text-left text-xs font-medium text-ink-2"
        >
          {resource}
        </th>
      </tr>
      {perms.map((p) => (
        <tr key={p.code} className="border-t border-line">
          <th scope="row" className="sticky left-0 z-10 bg-surface px-4 py-2 text-left font-normal">
            <span className="num block text-xs">{p.code}</span>
            <span className="block text-xs text-ink-2">{p.description}</span>
          </th>
          {roles.map((r) => {
            const has = r.permissions.includes(p.code);
            return (
              <td key={r.id} className="px-2 py-2 text-center">
                {r.system ? (
                  has ? (
                    <Check className="mx-auto size-4 text-lapis" aria-label="incluido" />
                  ) : (
                    <span className="sr-only">no incluido</span>
                  )
                ) : (
                  <input
                    type="checkbox"
                    aria-label={`${p.code} en ${r.name}`}
                    checked={has}
                    onChange={() => {
                      onToggle(r, p.code);
                    }}
                  />
                )}
              </td>
            );
          })}
        </tr>
      ))}
    </>
  );
}

function CreateRole({ permissions, onDone }: { permissions: PermissionInfo[]; onDone: () => void }) {
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [selected, setSelected] = useState<string[]>([]);
  const create = useMutation({
    mutationFn: () =>
      api('/api/v1/roles', { method: 'POST', body: { name, description, permissions: selected } }),
    onSuccess: () => {
      setName('');
      setDescription('');
      setSelected([]);
      onDone();
    },
  });
  return (
    <Section title="Crear rol">
      <form
        onSubmit={(e: SyntheticEvent) => {
          e.preventDefault();
          create.mutate();
        }}
        className="flex flex-col gap-4"
      >
        <div className="grid gap-4 sm:grid-cols-2">
          <Field
            label="Nombre"
            required
            pattern="[A-Z][A-Z0-9_]{2,63}"
            hint="MAYÚSCULAS_CON_GUION_BAJO, p. ej. ANALISTA_JUNIOR"
            value={name}
            onChange={(e) => {
              setName(e.target.value.toUpperCase());
            }}
          />
          <Field
            label="Descripción"
            value={description}
            onChange={(e) => {
              setDescription(e.target.value);
            }}
          />
        </div>
        <fieldset className="grid gap-x-6 gap-y-1.5 sm:grid-cols-2 lg:grid-cols-3">
          <legend className="mb-2 text-sm font-medium">Permisos</legend>
          {permissions.map((p) => (
            <label key={p.code} className="flex items-center gap-2 text-sm">
              <input
                type="checkbox"
                checked={selected.includes(p.code)}
                onChange={(e) => {
                  setSelected(
                    e.target.checked ? [...selected, p.code] : selected.filter((x) => x !== p.code),
                  );
                }}
              />
              <span className="num text-xs">{p.code}</span>
            </label>
          ))}
        </fieldset>
        <ErrorNote error={create.error} />
        <Button type="submit" className="self-start" disabled={create.isPending || selected.length === 0}>
          Crear rol
        </Button>
      </form>
    </Section>
  );
}
