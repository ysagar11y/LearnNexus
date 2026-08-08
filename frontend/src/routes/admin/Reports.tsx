import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Button } from '@ds/components/forms/Button';
import { Select } from '@ds/components/forms/Select';
import { Input } from '@ds/components/forms/Input';
import { Label } from '@ds/components/forms/Label';
import { EmptyState } from '@ds/components/feedback/EmptyState';

import { api, download } from '@/lib/api';
import { formatDate, formatNumber } from '@/lib/format';
import type { CourseSummary, OrgUnitNode, Page, ReportDefinition, ReportResult } from '@/lib/types';
import { ErrorState, PageHeader } from '@/components/states';
import { Column, DataTable } from '@/components/DataTable';
import { IconChart, IconDownload } from '@/components/icons';

export default function AdminReports() {
  const [reportKey, setReportKey] = useState('course-completion');
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  const [courseId, setCourseId] = useState('');
  const [orgUnitId, setOrgUnitId] = useState('');

  const { data: definitions } = useQuery({
    queryKey: ['report-definitions'],
    queryFn: () => api.get<ReportDefinition[]>('/reports'),
  });

  const { data: courses } = useQuery({
    queryKey: ['report-courses'],
    queryFn: () => api.get<Page<CourseSummary>>('/courses', { size: 100 }),
  });

  const { data: orgUnits } = useQuery({
    queryKey: ['org-units-flat'],
    queryFn: () => api.get<OrgUnitNode[]>('/org-units/flat'),
  });

  const filters = { from: from || undefined, to: to || undefined, courseId: courseId || undefined, orgUnitId: orgUnitId || undefined };

  const { data, isLoading, error, refetch } = useQuery({
    queryKey: ['report', reportKey, from, to, courseId, orgUnitId],
    queryFn: () => api.get<ReportResult>(`/reports/${reportKey}`, filters),
  });

  const active = definitions?.find((definition) => definition.key === reportKey);

  // One generic renderer serves every report: the server sends the column list
  // with its own type information, so adding a report needs no frontend change.
  const columns: Column<Record<string, unknown>>[] = (data?.columns ?? []).map((column) => ({
    key: column.key,
    header: column.label,
    numeric: column.type === 'NUMBER' || column.type === 'PERCENT',
    render: (row) => formatCell(row[column.key], column.type),
  }));

  return (
    <div className="app-inner-wide">
      <PageHeader
        title="Reports"
        subtitle={active?.description ?? 'Standard reports across your workspace.'}
        actions={
          <Button
            variant="outline"
            disabled={!data || data.rowCount === 0}
            onClick={() =>
              download(
                `/reports/${reportKey}/export?${new URLSearchParams(
                  Object.entries(filters).filter(([, value]) => value) as [string, string][],
                )}`,
                `${reportKey}-${new Date().toISOString().slice(0, 10)}.csv`,
              )
            }
          >
            <IconDownload size={15} />
            Export CSV
          </Button>
        }
      />

      <div className="filter-bar">
        <div style={{ minWidth: 240 }}>
          <Select
            value={reportKey}
            onValueChange={setReportKey}
            options={(definitions ?? []).map((definition) => ({
              value: definition.key,
              label: definition.title,
            }))}
            aria-label="Report"
          />
        </div>
        <div className="filter-control">
          <Select
            value={courseId}
            onValueChange={setCourseId}
            options={[
              { value: '', label: 'All courses' },
              ...(courses?.items ?? []).map((course) => ({ value: course.id, label: course.title })),
            ]}
          />
        </div>
        <div className="filter-control">
          <Select
            value={orgUnitId}
            onValueChange={setOrgUnitId}
            options={[
              { value: '', label: 'All departments' },
              ...(orgUnits ?? []).map((unit) => ({
                value: unit.id,
                label: `${'— '.repeat(unit.depth)}${unit.name}`,
              })),
            ]}
          />
        </div>
        <div className="row row-gap-2">
          <div>
            <Label htmlFor="from" hint="From">
              <span className="ln-sr-only">From</span>
            </Label>
            <Input
              id="from"
              type="date"
              value={from}
              onChange={(event) => setFrom(event.target.value)}
              aria-label="From date"
            />
          </div>
          <div>
            <Label htmlFor="to">
              <span className="ln-sr-only">To</span>
            </Label>
            <Input
              id="to"
              type="date"
              value={to}
              onChange={(event) => setTo(event.target.value)}
              aria-label="To date"
            />
          </div>
        </div>
        {(from || to || courseId || orgUnitId) && (
          <Button
            variant="ghost"
            size="sm"
            onClick={() => {
              setFrom('');
              setTo('');
              setCourseId('');
              setOrgUnitId('');
            }}
          >
            Clear
          </Button>
        )}
      </div>

      {error ? (
        <ErrorState error={error} onRetry={refetch} />
      ) : (
        <>
          <DataTable
            columns={columns}
            rows={data?.rows ?? []}
            keyOf={(row) => JSON.stringify(Object.values(row).slice(0, 3))}
            loading={isLoading}
            empty={
              <EmptyState
                icon={<IconChart size={24} />}
                title="No rows"
                description="Nothing matches these filters. Try widening the date range."
                action={
                  <Button
                    onClick={() => {
                      setFrom('');
                      setTo('');
                      setCourseId('');
                      setOrgUnitId('');
                    }}
                  >
                    Clear filters
                  </Button>
                }
              />
            }
          />
          {data && data.rowCount > 0 && (
            <p style={{ marginTop: 12, fontSize: 'var(--text-xs)', color: 'var(--muted-foreground)' }}>
              {formatNumber(data.rowCount)} rows · generated {new Date(data.generatedAt).toLocaleTimeString('en-GB')}
            </p>
          )}
        </>
      )}
    </div>
  );
}

function formatCell(value: unknown, type: ReportDefinition['columns'][number]['type']) {
  if (value === null || value === undefined || value === '') return '—';
  switch (type) {
    case 'PERCENT':
      return `${value}%`;
    case 'NUMBER':
      return formatNumber(Number(value));
    case 'DATE':
      return formatDate(String(value));
    default:
      return String(value);
  }
}
