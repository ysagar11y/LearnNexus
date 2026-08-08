import { useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Button } from '@ds/components/forms/Button';
import { Input } from '@ds/components/forms/Input';
import { Label } from '@ds/components/forms/Label';
import { Select } from '@ds/components/forms/Select';
import { Checkbox } from '@ds/components/forms/Checkbox';
import { Badge } from '@ds/components/feedback/Badge';
import { Alert } from '@ds/components/feedback/Alert';
import { EmptyState } from '@ds/components/feedback/EmptyState';
import { Dialog } from '@ds/components/overlays/Dialog';
import { Avatar } from '@ds/components/core/Avatar';
import { Progress } from '@ds/components/core/Progress';

import { ApiError, api } from '@/lib/api';
import { formatDate, humanise, relativeTime } from '@/lib/format';
import type { OrgUnitNode, Page, RoleCode, UserDetail, UserSummary } from '@/lib/types';
import { Column, DataTable, Pager, StackedCell } from '@/components/DataTable';
import { ErrorState, PageHeader } from '@/components/states';
import { IconPeople, IconPlus, IconSearch } from '@/components/icons';

const ASSIGNABLE_ROLES: RoleCode[] = ['TENANT_ADMIN', 'AUTHOR', 'INSTRUCTOR', 'MANAGER', 'LEARNER'];

export default function AdminPeople() {
  const queryClient = useQueryClient();
  const [params, setParams] = useSearchParams();

  const [term, setTerm] = useState('');
  const [status, setStatus] = useState('');
  const [orgUnitId, setOrgUnitId] = useState('');
  const [page, setPage] = useState(0);
  const [inviting, setInviting] = useState(params.get('invite') === '1');
  const [selectedId, setSelectedId] = useState<string | null>(params.get('user'));

  const { data: orgUnits } = useQuery({
    queryKey: ['org-units-flat'],
    queryFn: () => api.get<OrgUnitNode[]>('/org-units/flat'),
  });

  const { data, isLoading, error, refetch } = useQuery({
    queryKey: ['people', term, status, orgUnitId, page],
    queryFn: () =>
      api.get<Page<UserSummary>>('/users', {
        query: term || undefined,
        status: status || undefined,
        orgUnitId: orgUnitId || undefined,
        page,
        size: 25,
        sort: 'name,asc',
      }),
  });

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['people'] });

  const columns: Column<UserSummary>[] = [
    {
      key: 'name',
      header: 'Person',
      render: (user) => (
        <div className="row row-gap-3">
          <Avatar name={user.displayName} src={user.avatarUrl} size="sm" />
          <StackedCell primary={user.displayName} secondary={user.email} />
        </div>
      ),
    },
    {
      key: 'roles',
      header: 'Roles',
      width: 220,
      render: (user) => (
        <div className="row row-gap-2 row-wrap">
          {user.roles.map((role) => (
            <Badge key={role} tone={role === 'TENANT_ADMIN' ? 'brand' : 'neutral'} size="sm">
              {humanise(role)}
            </Badge>
          ))}
        </div>
      ),
    },
    {
      key: 'org',
      header: 'Department',
      width: 150,
      render: (user) => user.orgUnitName ?? '—',
    },
    {
      key: 'status',
      header: 'Status',
      width: 110,
      render: (user) => <Badge status={user.status} size="sm" />,
    },
    {
      key: 'lastLogin',
      header: 'Last active',
      width: 140,
      render: (user) => (
        <span className="muted" style={{ fontSize: 'var(--text-xs)' }}>
          {user.lastLoginAt ? relativeTime(user.lastLoginAt) : 'Never'}
        </span>
      ),
    },
  ];

  if (error) return <ErrorState error={error} onRetry={refetch} />;

  return (
    <div className="app-inner-wide">
      <PageHeader
        title="People"
        subtitle="Everyone with access to this workspace."
        actions={
          <Button onClick={() => setInviting(true)}>
            <IconPlus size={15} />
            Invite people
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
            placeholder="Search by name or email"
            leading={<IconSearch size={15} />}
            aria-label="Search people"
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
              { value: 'INVITED', label: 'Invited' },
              { value: 'SUSPENDED', label: 'Suspended' },
            ]}
          />
        </div>
        <div className="filter-control">
          <Select
            value={orgUnitId}
            onValueChange={(value) => {
              setOrgUnitId(value);
              setPage(0);
            }}
            options={[
              { value: '', label: 'All departments' },
              ...(orgUnits ?? []).map((unit) => ({
                value: unit.id,
                label: `${'— '.repeat(unit.depth)}${unit.name}`,
              })),
            ]}
          />
        </div>
      </div>

      <DataTable
        columns={columns}
        rows={data?.items ?? []}
        keyOf={(user) => user.id}
        loading={isLoading}
        onRowClick={(user) => setSelectedId(user.id)}
        empty={
          <EmptyState
            icon={<IconPeople size={24} />}
            title={term ? 'Nobody matches' : 'No people yet'}
            description={
              term
                ? 'Try a different name or email.'
                : 'Invite your colleagues so they can start learning.'
            }
            action={<Button onClick={() => setInviting(true)}>Invite people</Button>}
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

      <InviteDialog
        open={inviting}
        orgUnits={orgUnits ?? []}
        onClose={() => {
          setInviting(false);
          params.delete('invite');
          setParams(params, { replace: true });
        }}
        onInvited={invalidate}
      />

      <PersonDrawer
        userId={selectedId}
        orgUnits={orgUnits ?? []}
        onClose={() => {
          setSelectedId(null);
          params.delete('user');
          setParams(params, { replace: true });
        }}
        onChanged={invalidate}
      />
    </div>
  );
}

function InviteDialog({
  open,
  orgUnits,
  onClose,
  onInvited,
}: {
  open: boolean;
  orgUnits: OrgUnitNode[];
  onClose: () => void;
  onInvited: () => void;
}) {
  const [email, setEmail] = useState('');
  const [firstName, setFirstName] = useState('');
  const [lastName, setLastName] = useState('');
  const [roles, setRoles] = useState<RoleCode[]>(['LEARNER']);
  const [orgUnitId, setOrgUnitId] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [done, setDone] = useState(false);

  const invite = useMutation({
    mutationFn: () =>
      api.post('/users', {
        email: email.trim(),
        firstName: firstName.trim() || email.split('@')[0],
        lastName: lastName.trim() || null,
        roles,
        orgUnitId: orgUnitId || null,
        sendInvitation: true,
      }),
    onSuccess: () => {
      setDone(true);
      setEmail('');
      setFirstName('');
      setLastName('');
      onInvited();
    },
    onError: (caught) =>
      setError(caught instanceof ApiError ? caught.message : 'Could not send the invitation.'),
  });

  return (
    <Dialog
      open={open}
      onClose={onClose}
      title="Invite someone"
      description="They will get an email with a link to set their password."
      footer={
        <>
          <Button variant="ghost" onClick={onClose}>
            Done
          </Button>
          <Button
            loading={invite.isPending}
            disabled={!email.includes('@') || roles.length === 0}
            onClick={() => {
              setError(null);
              setDone(false);
              invite.mutate();
            }}
          >
            Send invitation
          </Button>
        </>
      }
    >
      {error && (
        <div style={{ marginBottom: 14 }}>
          <Alert tone="critical" title="Could not invite">
            {error}
          </Alert>
        </div>
      )}
      {done && (
        <div style={{ marginBottom: 14 }}>
          <Alert tone="success" title="Invitation sent" onDismiss={() => setDone(false)}>
            Invite another person, or close this dialog.
          </Alert>
        </div>
      )}

      <div className="field">
        <Label htmlFor="invite-email" required>
          Email
        </Label>
        <Input
          id="invite-email"
          type="email"
          value={email}
          onChange={(event) => setEmail(event.target.value)}
          placeholder="colleague@company.com"
          autoFocus
        />
      </div>

      <div className="row row-gap-3" style={{ alignItems: 'flex-start' }}>
        <div className="field" style={{ flex: 1 }}>
          <Label htmlFor="invite-first">First name</Label>
          <Input
            id="invite-first"
            value={firstName}
            onChange={(event) => setFirstName(event.target.value)}
          />
        </div>
        <div className="field" style={{ flex: 1 }}>
          <Label htmlFor="invite-last">Last name</Label>
          <Input
            id="invite-last"
            value={lastName}
            onChange={(event) => setLastName(event.target.value)}
          />
        </div>
      </div>

      <div className="field">
        <Label htmlFor="invite-org">Department</Label>
        <Select
          id="invite-org"
          value={orgUnitId}
          onValueChange={setOrgUnitId}
          options={[
            { value: '', label: 'Unassigned' },
            ...orgUnits.map((unit) => ({
              value: unit.id,
              label: `${'— '.repeat(unit.depth)}${unit.name}`,
            })),
          ]}
        />
      </div>

      <fieldset style={{ border: 0, padding: 0, margin: 0 }}>
        <legend style={{ fontSize: 'var(--text-sm)', fontWeight: 500, marginBottom: 8 }}>Roles</legend>
        <div className="stack stack-2">
          {ASSIGNABLE_ROLES.map((role) => (
            <Checkbox
              key={role}
              checked={roles.includes(role)}
              onCheckedChange={(checked) =>
                setRoles((current) =>
                  checked ? [...current, role] : current.filter((value) => value !== role),
                )
              }
              label={humanise(role)}
            />
          ))}
        </div>
      </fieldset>
    </Dialog>
  );
}

function PersonDrawer({
  userId,
  orgUnits,
  onClose,
  onChanged,
}: {
  userId: string | null;
  orgUnits: OrgUnitNode[];
  onClose: () => void;
  onChanged: () => void;
}) {
  const [roles, setRoles] = useState<RoleCode[]>([]);
  const [orgUnitId, setOrgUnitId] = useState('');
  const [error, setError] = useState<string | null>(null);

  const { data } = useQuery({
    queryKey: ['person', userId],
    queryFn: () => api.get<UserDetail>(`/users/${userId}`),
    enabled: !!userId,
  });

  useEffect(() => {
    if (!data) return;
    setRoles(data.roles);
    setOrgUnitId(data.orgUnitId ?? '');
  }, [data]);

  const saveRoles = useMutation({
    mutationFn: () => api.put(`/users/${userId}/roles`, { roles }),
    onSuccess: onChanged,
    onError: (caught) =>
      setError(caught instanceof ApiError ? caught.message : 'Could not change the roles.'),
  });

  const changeStatus = useMutation({
    mutationFn: (status: string) => api.patch(`/users/${userId}/status`, { status }),
    onSuccess: onChanged,
    onError: (caught) =>
      setError(caught instanceof ApiError ? caught.message : 'Could not change the status.'),
  });

  const resend = useMutation({
    mutationFn: () => api.post(`/users/${userId}/resend-invitation`),
  });

  const move = useMutation({
    mutationFn: () =>
      api.put(`/users/${userId}`, {
        firstName: data!.firstName,
        lastName: data!.lastName,
        jobTitle: data!.jobTitle,
        phone: data!.phone,
        avatarUrl: data!.avatarUrl,
        orgUnitId: orgUnitId || null,
        managerId: data!.managerId,
        locale: data!.locale,
        timezone: data!.timezone,
      }),
    onSuccess: onChanged,
  });

  return (
    <Dialog
      open={!!userId}
      onClose={onClose}
      title={data?.displayName ?? 'Person'}
      description={data?.email}
      size="md"
      footer={
        <>
          <Button variant="ghost" onClick={onClose}>
            Close
          </Button>
          {data?.status === 'INVITED' && (
            <Button variant="outline" loading={resend.isPending} onClick={() => resend.mutate()}>
              {resend.isSuccess ? 'Invitation sent' : 'Resend invitation'}
            </Button>
          )}
          {data?.status === 'ACTIVE' ? (
            <Button variant="outline" onClick={() => changeStatus.mutate('SUSPENDED')}>
              Suspend
            </Button>
          ) : data?.status === 'SUSPENDED' ? (
            <Button variant="outline" onClick={() => changeStatus.mutate('ACTIVE')}>
              Reactivate
            </Button>
          ) : null}
        </>
      }
    >
      {!data ? null : (
        <div className="stack stack-4">
          {error && (
            <Alert tone="critical" title="Could not save" onDismiss={() => setError(null)}>
              {error}
            </Alert>
          )}

          <div className="stat-grid">
            <div className="surface" style={{ padding: 14, boxShadow: 'none' }}>
              <div style={{ fontSize: 'var(--text-xs)', color: 'var(--muted-foreground)' }}>Enrolled</div>
              <div style={{ fontFamily: 'var(--font-display)', fontVariationSettings: 'var(--font-display-variation)', fontSize: 'var(--text-xl)' }}>
                {data.learning.enrolled}
              </div>
            </div>
            <div className="surface" style={{ padding: 14, boxShadow: 'none' }}>
              <div style={{ fontSize: 'var(--text-xs)', color: 'var(--muted-foreground)' }}>Completed</div>
              <div style={{ fontFamily: 'var(--font-display)', fontVariationSettings: 'var(--font-display-variation)', fontSize: 'var(--text-xl)' }}>
                {data.learning.completed}
              </div>
            </div>
            <div className="surface" style={{ padding: 14, boxShadow: 'none' }}>
              <div style={{ fontSize: 'var(--text-xs)', color: 'var(--muted-foreground)' }}>Overdue</div>
              <div
                style={{
                  fontFamily: 'var(--font-display)', fontVariationSettings: 'var(--font-display-variation)',
                  fontSize: 'var(--text-xl)',
                  color: data.learning.overdue > 0 ? 'var(--destructive)' : undefined,
                }}
              >
                {data.learning.overdue}
              </div>
            </div>
          </div>

          <div>
            <div style={{ fontSize: 'var(--text-xs)', color: 'var(--muted-foreground)', marginBottom: 6 }}>
              Average progress
            </div>
            <Progress value={data.learning.averageProgress} size="sm" showLabel />
          </div>

          <div className="field" style={{ margin: 0 }}>
            <Label htmlFor="person-org">Department</Label>
            <div className="row row-gap-2">
              <div style={{ flex: 1 }}>
                <Select
                  id="person-org"
                  value={orgUnitId}
                  onValueChange={setOrgUnitId}
                  options={[
                    { value: '', label: 'Unassigned' },
                    ...orgUnits.map((unit) => ({
                      value: unit.id,
                      label: `${'— '.repeat(unit.depth)}${unit.name}`,
                    })),
                  ]}
                />
              </div>
              <Button
                size="sm"
                variant="outline"
                loading={move.isPending}
                disabled={orgUnitId === (data.orgUnitId ?? '')}
                onClick={() => move.mutate()}
              >
                Move
              </Button>
            </div>
          </div>

          <fieldset style={{ border: 0, padding: 0, margin: 0 }}>
            <legend style={{ fontSize: 'var(--text-sm)', fontWeight: 500, marginBottom: 8 }}>Roles</legend>
            <div className="stack stack-2">
              {ASSIGNABLE_ROLES.map((role) => (
                <Checkbox
                  key={role}
                  checked={roles.includes(role)}
                  onCheckedChange={(checked) =>
                    setRoles((current) =>
                      checked ? [...current, role] : current.filter((value) => value !== role),
                    )
                  }
                  label={humanise(role)}
                />
              ))}
            </div>
            <div style={{ marginTop: 12 }}>
              <Button
                size="sm"
                loading={saveRoles.isPending}
                disabled={roles.length === 0 || roles.join() === data.roles.join()}
                onClick={() => {
                  setError(null);
                  saveRoles.mutate();
                }}
              >
                Save roles
              </Button>
              <p style={{ marginTop: 8, fontSize: 'var(--text-xs)', color: 'var(--muted-foreground)' }}>
                Changing roles signs this person out of every device.
              </p>
            </div>
          </fieldset>

          <p style={{ fontSize: 'var(--text-xs)', color: 'var(--muted-foreground)' }}>
            Joined {formatDate(data.createdAt)}
            {data.lastLoginAt ? ` · last active ${relativeTime(data.lastLoginAt)}` : ''}
          </p>
        </div>
      )}
    </Dialog>
  );
}
