import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Button } from '@ds/components/forms/Button';
import { Input } from '@ds/components/forms/Input';
import { Label } from '@ds/components/forms/Label';
import { Select } from '@ds/components/forms/Select';
import { Badge } from '@ds/components/feedback/Badge';
import { Alert } from '@ds/components/feedback/Alert';
import { EmptyState } from '@ds/components/feedback/EmptyState';
import { Dialog } from '@ds/components/overlays/Dialog';
import { Progress } from '@ds/components/core/Progress';

import { ApiError, api } from '@/lib/api';
import { formatBytes, formatDate, formatNumber } from '@/lib/format';
import type { Page, TenantRow } from '@/lib/types';
import { Column, DataTable, Pager, StackedCell } from '@/components/DataTable';
import { ErrorState, PageHeader } from '@/components/states';
import { IconBuilding, IconPlus, IconSearch } from '@/components/icons';

interface TenantDetail {
  tenant: TenantRow;
  ownerName?: string | null;
  ownerEmail?: string | null;
  storageBytes: number;
  maxStorageBytes: number;
  certificates: number;
  lastActivityAt?: string | null;
}

export default function PlatformTenants() {
  const queryClient = useQueryClient();
  const [term, setTerm] = useState('');
  const [status, setStatus] = useState('');
  const [page, setPage] = useState(0);
  const [creating, setCreating] = useState(false);
  const [selected, setSelected] = useState<string | null>(null);

  const { data, isLoading, error, refetch } = useQuery({
    queryKey: ['platform-tenants', term, status, page],
    queryFn: () =>
      api.get<Page<TenantRow>>('/platform/tenants', {
        query: term || undefined,
        status: status || undefined,
        page,
        size: 25,
      }),
  });

  const columns: Column<TenantRow>[] = [
    {
      key: 'name',
      header: 'Workspace',
      render: (tenant) => (
        <StackedCell
          primary={tenant.name}
          secondary={tenant.customDomain ?? `${tenant.slug}.learnnexus.app`}
        />
      ),
    },
    {
      key: 'plan',
      header: 'Plan',
      width: 120,
      render: (tenant) => (
        <Badge tone={tenant.plan === 'ENTERPRISE' ? 'brand' : 'neutral'} size="sm">
          {tenant.plan}
        </Badge>
      ),
    },
    {
      key: 'status',
      header: 'Status',
      width: 120,
      render: (tenant) => <Badge status={tenant.status} size="sm" />,
    },
    {
      key: 'seats',
      header: 'Seats',
      numeric: true,
      width: 130,
      render: (tenant) => (
        <div>
          <div className="numeric">
            {formatNumber(tenant.users)} / {formatNumber(tenant.maxUsers)}
          </div>
          <div style={{ marginTop: 4 }}>
            <Progress
              value={Math.min(100, (tenant.users / Math.max(tenant.maxUsers, 1)) * 100)}
              size="xs"
            />
          </div>
        </div>
      ),
    },
    {
      key: 'courses',
      header: 'Courses',
      numeric: true,
      width: 90,
      render: (tenant) => formatNumber(tenant.courses),
    },
    {
      key: 'enrolments',
      header: 'Enrolments',
      numeric: true,
      width: 110,
      render: (tenant) => formatNumber(tenant.enrolments),
    },
    {
      key: 'created',
      header: 'Created',
      width: 120,
      render: (tenant) => (
        <span className="muted" style={{ fontSize: 'var(--text-xs)' }}>
          {formatDate(tenant.createdAt)}
        </span>
      ),
    },
  ];

  if (error) return <ErrorState error={error} onRetry={refetch} />;

  return (
    <div className="app-inner-wide">
      <PageHeader
        title="Workspaces"
        subtitle="Provision, suspend and resize every customer workspace."
        actions={
          <Button onClick={() => setCreating(true)}>
            <IconPlus size={15} />
            New workspace
          </Button>
        }
      />

      <div className="filter-bar">
        <div style={{ flex: 1, minWidth: 220, maxWidth: 340 }}>
          <Input
            value={term}
            onChange={(event) => {
              setTerm(event.target.value);
              setPage(0);
            }}
            placeholder="Search by name or address"
            leading={<IconSearch size={15} />}
            aria-label="Search workspaces"
          />
        </div>
        <div className="filter-control">
          <Select
            value={status}
            onValueChange={(value) => {
              setStatus(value);
              setPage(0);
            }}
            options={[
              { value: '', label: 'Any status' },
              { value: 'ACTIVE', label: 'Active' },
              { value: 'TRIAL', label: 'Trial' },
              { value: 'SUSPENDED', label: 'Suspended' },
              { value: 'ARCHIVED', label: 'Archived' },
            ]}
          />
        </div>
      </div>

      <DataTable
        columns={columns}
        rows={data?.items ?? []}
        keyOf={(tenant) => tenant.id}
        loading={isLoading}
        onRowClick={(tenant) => setSelected(tenant.id)}
        empty={
          <EmptyState
            icon={<IconBuilding size={24} />}
            title="No workspaces"
            description="Provision the first customer workspace to get started."
            action={<Button onClick={() => setCreating(true)}>New workspace</Button>}
          />
        }
      />

      {data && (
        <Pager
          page={data.page}
          size={data.size}
          totalItems={data.totalItems}
          totalPages={data.totalPages}
          onChange={setPage}
        />
      )}

      <CreateTenantDialog
        open={creating}
        onClose={() => setCreating(false)}
        onCreated={() => queryClient.invalidateQueries({ queryKey: ['platform-tenants'] })}
      />

      <TenantDrawer
        tenantId={selected}
        onClose={() => setSelected(null)}
        onChanged={() => queryClient.invalidateQueries({ queryKey: ['platform-tenants'] })}
      />
    </div>
  );
}

function CreateTenantDialog({
  open,
  onClose,
  onCreated,
}: {
  open: boolean;
  onClose: () => void;
  onCreated: () => void;
}) {
  const [name, setName] = useState('');
  const [slug, setSlug] = useState('');
  const [adminEmail, setAdminEmail] = useState('');
  const [adminFirstName, setAdminFirstName] = useState('');
  const [plan, setPlan] = useState('PRO');
  const [maxUsers, setMaxUsers] = useState(100);
  const [error, setError] = useState<string | null>(null);

  const create = useMutation({
    mutationFn: () =>
      api.post('/platform/tenants', {
        name: name.trim(),
        slug: slug.trim() || undefined,
        adminEmail: adminEmail.trim(),
        adminFirstName: adminFirstName.trim() || adminEmail.split('@')[0],
        plan,
        maxUsers,
      }),
    onSuccess: () => {
      setName('');
      setSlug('');
      setAdminEmail('');
      setAdminFirstName('');
      onCreated();
      onClose();
    },
    onError: (caught) =>
      setError(caught instanceof ApiError ? caught.message : 'Could not create the workspace.'),
  });

  return (
    <Dialog
      open={open}
      onClose={onClose}
      title="New workspace"
      description="Creates the workspace and emails its first administrator an invitation."
      footer={
        <>
          <Button variant="ghost" onClick={onClose}>
            Cancel
          </Button>
          <Button
            loading={create.isPending}
            disabled={!name.trim() || !adminEmail.includes('@')}
            onClick={() => {
              setError(null);
              create.mutate();
            }}
          >
            Provision
          </Button>
        </>
      }
    >
      {error && (
        <div style={{ marginBottom: 14 }}>
          <Alert tone="critical" title="Could not provision">
            {error}
          </Alert>
        </div>
      )}

      <div className="field">
        <Label htmlFor="tenant-name" required>
          Organisation name
        </Label>
        <Input
          id="tenant-name"
          value={name}
          onChange={(event) => {
            setName(event.target.value);
            // Suggest an address, but let it be overridden.
            if (!slug || slug === slugify(name)) setSlug(slugify(event.target.value));
          }}
          placeholder="Acme Corp"
          autoFocus
        />
      </div>

      <div className="field">
        <Label htmlFor="tenant-slug" hint="Lowercase letters, numbers and hyphens">
          Address
        </Label>
        <Input
          id="tenant-slug"
          value={slug}
          onChange={(event) => setSlug(slugify(event.target.value))}
          trailing={
            <span style={{ fontSize: 'var(--text-xs)', color: 'var(--muted-foreground)' }}>
              .learnnexus.app
            </span>
          }
        />
      </div>

      <div className="row row-gap-3" style={{ alignItems: 'flex-start' }}>
        <div className="field" style={{ flex: 1 }}>
          <Label htmlFor="tenant-admin" required>
            First admin's email
          </Label>
          <Input
            id="tenant-admin"
            type="email"
            value={adminEmail}
            onChange={(event) => setAdminEmail(event.target.value)}
            placeholder="admin@acme.com"
          />
        </div>
        <div className="field" style={{ flex: 1 }}>
          <Label htmlFor="tenant-admin-name">Their first name</Label>
          <Input
            id="tenant-admin-name"
            value={adminFirstName}
            onChange={(event) => setAdminFirstName(event.target.value)}
          />
        </div>
      </div>

      <div className="row row-gap-3" style={{ alignItems: 'flex-start' }}>
        <div className="field" style={{ flex: 1 }}>
          <Label htmlFor="tenant-plan">Plan</Label>
          <Select
            id="tenant-plan"
            value={plan}
            onValueChange={setPlan}
            options={[
              { value: 'FREE', label: 'Free' },
              { value: 'PRO', label: 'Pro' },
              { value: 'ENTERPRISE', label: 'Enterprise' },
            ]}
          />
        </div>
        <div className="field" style={{ flex: 1 }}>
          <Label htmlFor="tenant-seats">Seats</Label>
          <Input
            id="tenant-seats"
            type="number"
            min={1}
            value={maxUsers}
            onChange={(event) => setMaxUsers(Number(event.target.value))}
          />
        </div>
      </div>
    </Dialog>
  );
}

function TenantDrawer({
  tenantId,
  onClose,
  onChanged,
}: {
  tenantId: string | null;
  onClose: () => void;
  onChanged: () => void;
}) {
  const [reason, setReason] = useState('');

  const { data } = useQuery({
    queryKey: ['platform-tenant', tenantId],
    queryFn: () => api.get<TenantDetail>(`/platform/tenants/${tenantId}`),
    enabled: !!tenantId,
  });

  const changeStatus = useMutation({
    mutationFn: (status: string) =>
      api.patch(`/platform/tenants/${tenantId}/status`, { status, reason: reason || null }),
    onSuccess: () => {
      onChanged();
      onClose();
    },
  });

  return (
    <Dialog
      open={!!tenantId}
      onClose={onClose}
      title={data?.tenant.name ?? 'Workspace'}
      description={data ? data.tenant.customDomain ?? `${data.tenant.slug}.learnnexus.app` : undefined}
      footer={
        <>
          <Button variant="ghost" onClick={onClose}>
            Close
          </Button>
          {data?.tenant.status === 'SUSPENDED' ? (
            <Button loading={changeStatus.isPending} onClick={() => changeStatus.mutate('ACTIVE')}>
              Reactivate
            </Button>
          ) : (
            <Button
              variant="destructive"
              loading={changeStatus.isPending}
              onClick={() => changeStatus.mutate('SUSPENDED')}
            >
              Suspend
            </Button>
          )}
        </>
      }
    >
      {!data ? null : (
        <div className="stack stack-4">
          <div className="row row-gap-2">
            <Badge status={data.tenant.status} size="md" />
            <Badge tone="brand" size="md">
              {data.tenant.plan}
            </Badge>
          </div>

          <dl className="stack stack-3" style={{ fontSize: 'var(--text-sm)' }}>
            {[
              ['Owner', data.ownerName ? `${data.ownerName} · ${data.ownerEmail}` : '—'],
              ['Users', `${formatNumber(data.tenant.users)} of ${formatNumber(data.tenant.maxUsers)}`],
              ['Courses', formatNumber(data.tenant.courses)],
              ['Enrolments', formatNumber(data.tenant.enrolments)],
              ['Certificates', formatNumber(data.certificates)],
              ['Storage', `${formatBytes(data.storageBytes)} of ${formatBytes(data.maxStorageBytes)}`],
              ['Created', formatDate(data.tenant.createdAt)],
              ['Last activity', data.lastActivityAt ? formatDate(data.lastActivityAt) : 'Never'],
            ].map(([label, value]) => (
              <div key={label} className="row" style={{ justifyContent: 'space-between', gap: 16 }}>
                <dt className="muted">{label}</dt>
                <dd style={{ textAlign: 'end' }}>{value}</dd>
              </div>
            ))}
          </dl>

          {data.tenant.status !== 'SUSPENDED' && (
            <div className="field" style={{ margin: 0 }}>
              <Label htmlFor="suspend-reason" hint="Recorded in the audit trail">
                Reason for suspension
              </Label>
              <Input
                id="suspend-reason"
                value={reason}
                onChange={(event) => setReason(event.target.value)}
                placeholder="Non-payment, abuse report, customer request…"
              />
            </div>
          )}

          <Alert tone="warning" title="Suspending blocks every sign-in">
            Learners and admins in this workspace lose access immediately. Data is retained.
          </Alert>
        </div>
      )}
    </Dialog>
  );
}

function slugify(value: string): string {
  return value
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/(^-|-$)/g, '')
    .slice(0, 63);
}
