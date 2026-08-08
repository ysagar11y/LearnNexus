import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Select } from '@ds/components/forms/Select';
import { Input } from '@ds/components/forms/Input';
import { Button } from '@ds/components/forms/Button';
import { EmptyState } from '@ds/components/feedback/EmptyState';

import { api } from '@/lib/api';
import { formatDateTime, humanise } from '@/lib/format';
import type { AuditEntry, Page } from '@/lib/types';
import { Column, DataTable, Pager, StackedCell } from '@/components/DataTable';
import { ErrorState, PageHeader } from '@/components/states';
import { IconAudit } from '@/components/icons';

export default function AdminAudit() {
  const [action, setAction] = useState('');
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  const [page, setPage] = useState(0);

  const { data: actions } = useQuery({
    queryKey: ['audit-actions'],
    queryFn: () => api.get<string[]>('/audit/actions'),
  });

  const { data, isLoading, error, refetch } = useQuery({
    queryKey: ['audit', action, from, to, page],
    queryFn: () =>
      api.get<Page<AuditEntry>>('/audit', {
        action: action || undefined,
        from: from || undefined,
        to: to || undefined,
        page,
        size: 50,
      }),
  });

  const columns: Column<AuditEntry>[] = [
    {
      key: 'when',
      header: 'When',
      width: 170,
      render: (entry) => (
        <span style={{ fontSize: 'var(--text-xs)' }} className="numeric">
          {formatDateTime(entry.createdAt)}
        </span>
      ),
    },
    {
      key: 'action',
      header: 'Action',
      width: 200,
      render: (entry) => (
        <span style={{ fontFamily: 'var(--font-mono)', fontSize: 'var(--text-2xs)' }}>
          {entry.action}
        </span>
      ),
    },
    {
      key: 'summary',
      header: 'Detail',
      render: (entry) => (
        <StackedCell
          primary={entry.summary ?? humanise(entry.action)}
          secondary={entry.entityType ? `${entry.entityType} ${entry.entityId ?? ''}`.trim() : undefined}
        />
      ),
    },
    {
      key: 'actor',
      header: 'Who',
      width: 200,
      render: (entry) => (
        <StackedCell primary={entry.actorEmail ?? 'System'} secondary={entry.ipAddress ?? undefined} />
      ),
    },
  ];

  if (error) return <ErrorState error={error} onRetry={refetch} />;

  return (
    <div className="app-inner-wide">
      <PageHeader
        title="Audit trail"
        subtitle="Append-only. Entries cannot be edited or deleted, by anyone, including us."
      />

      <div className="filter-bar">
        <div style={{ minWidth: 240 }}>
          <Select
            value={action}
            onValueChange={(value) => {
              setAction(value);
              setPage(0);
            }}
            options={[
              { value: '', label: 'All actions' },
              ...(actions ?? []).map((value) => ({ value, label: value })),
            ]}
            aria-label="Action"
          />
        </div>
        <Input
          type="date"
          value={from}
          onChange={(event) => {
            setFrom(event.target.value);
            setPage(0);
          }}
          aria-label="From date"
        />
        <Input
          type="date"
          value={to}
          onChange={(event) => {
            setTo(event.target.value);
            setPage(0);
          }}
          aria-label="To date"
        />
        {(action || from || to) && (
          <Button
            variant="ghost"
            size="sm"
            onClick={() => {
              setAction('');
              setFrom('');
              setTo('');
              setPage(0);
            }}
          >
            Clear
          </Button>
        )}
      </div>

      <DataTable
        columns={columns}
        rows={data?.items ?? []}
        keyOf={(entry) => String(entry.id)}
        loading={isLoading}
        empty={
          <EmptyState
            icon={<IconAudit size={24} />}
            title="Nothing recorded"
            description="Administrative actions — sign-ins, role changes, publishing, enrolments — appear here as they happen."
            action={<Button variant="outline" onClick={() => refetch()}>Refresh</Button>}
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
    </div>
  );
}
