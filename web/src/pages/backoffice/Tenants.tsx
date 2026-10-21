import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState, type SyntheticEvent } from 'react';

import { Button, ErrorNote, Field, Money, PageHeader, Section, Table } from '../../components/ui';
import { api } from '../../lib/api';

interface Tenant {
  id: string;
  code: string;
  name: string;
  country: string;
  currency: string;
  bic: string;
  status: 'ACTIVE' | 'SUSPENDED';
  approvalThreshold: number;
}

const empty = {
  code: '',
  name: '',
  bic: '',
  approvalThreshold: '5000',
  adminEmail: '',
  adminName: '',
  adminPassword: '',
};

export function Tenants() {
  const queryClient = useQueryClient();
  const tenants = useQuery({
    queryKey: ['platform-tenants'],
    queryFn: () => api<Tenant[]>('/api/v1/platform/tenants'),
  });
  const [form, setForm] = useState(empty);
  const refresh = () => queryClient.invalidateQueries({ queryKey: ['platform-tenants'] });

  const create = useMutation({
    mutationFn: () =>
      api('/api/v1/platform/tenants', {
        method: 'POST',
        body: { ...form, country: 'EC', currency: 'USD', approvalThreshold: Number(form.approvalThreshold) },
      }),
    onSuccess: () => {
      setForm(empty);
      void refresh();
    },
  });
  const toggle = useMutation({
    mutationFn: (t: Tenant) =>
      api(`/api/v1/platform/tenants/${t.id}`, {
        method: 'PATCH',
        body: { status: t.status === 'ACTIVE' ? 'SUSPENDED' : 'ACTIVE' },
      }),
    onSuccess: () => void refresh(),
  });
  const set = (k: keyof typeof empty) => (e: { target: { value: string } }) => {
    setForm({ ...form, [k]: k === 'code' || k === 'bic' ? e.target.value.toUpperCase() : e.target.value });
  };

  return (
    <>
      <PageHeader
        title="Instituciones"
        lead="Cada institución recibe sus roles, su bóveda de efectivo y su primer administrador. Sus datos quedan aislados del resto."
      />
      <Section title="Dar de alta una institución">
        <form
          onSubmit={(e: SyntheticEvent) => {
            e.preventDefault();
            create.mutate();
          }}
          className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4"
        >
          <Field
            label="Código"
            required
            pattern="[A-Z]{4}"
            hint="4 letras"
            value={form.code}
            onChange={set('code')}
          />
          <Field label="Nombre" required value={form.name} onChange={set('name')} />
          <Field
            label="BIC"
            required
            pattern="[A-Z]{6}[A-Z0-9]{2}([A-Z0-9]{3})?"
            hint="ISO 9362, p. ej. ANDNECE1XXX"
            value={form.bic}
            onChange={set('bic')}
          />
          <Field
            label="Umbral de aprobación (USD)"
            type="number"
            min="1"
            value={form.approvalThreshold}
            onChange={set('approvalThreshold')}
          />
          <Field label="Administrador" required value={form.adminName} onChange={set('adminName')} />
          <Field
            label="Correo del administrador"
            type="email"
            required
            value={form.adminEmail}
            onChange={set('adminEmail')}
          />
          <Field
            label="Contraseña inicial"
            type="password"
            required
            minLength={10}
            value={form.adminPassword}
            onChange={set('adminPassword')}
          />
          <div className="flex items-end">
            <Button type="submit" disabled={create.isPending}>
              Dar de alta
            </Button>
          </div>
          <div className="sm:col-span-2 lg:col-span-4">
            <ErrorNote error={create.error} />
          </div>
        </form>
      </Section>
      <Section title="Instituciones">
        <ErrorNote error={tenants.error ?? toggle.error} />
        <Table head={['Código', 'Nombre', 'BIC', 'Umbral cuatro ojos', 'Estado', '']}>
          {tenants.data?.map((t) => (
            <tr key={t.id}>
              <td className="num px-4 py-3 font-medium">{t.code}</td>
              <td className="px-4 py-3">{t.name}</td>
              <td className="num px-4 py-3 text-ink-2">{t.bic}</td>
              <td className="px-4 py-3 text-right">
                <Money amount={t.approvalThreshold} currency={t.currency} />
              </td>
              <td className={`px-4 py-3 ${t.status === 'ACTIVE' ? 'text-ok' : 'text-risk'}`}>
                {t.status === 'ACTIVE' ? 'Activa' : 'Suspendida'}
              </td>
              <td className="px-4 py-3">
                {t.code !== 'KNTR' && (
                  <Button
                    variant="quiet"
                    className="px-2 py-1 text-xs"
                    onClick={() => {
                      toggle.mutate(t);
                    }}
                  >
                    {t.status === 'ACTIVE' ? 'Suspender' : 'Reactivar'}
                  </Button>
                )}
              </td>
            </tr>
          ))}
        </Table>
      </Section>
    </>
  );
}
