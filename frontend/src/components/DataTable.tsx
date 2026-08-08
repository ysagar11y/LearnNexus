import type { ReactNode } from 'react';
import { Skeleton } from '@ds/components/feedback/Skeleton';

export interface Column<T> {
  key: string;
  header: ReactNode;
  /** Right-aligns and applies tabular figures — use for every number. */
  numeric?: boolean;
  width?: number | string;
  render: (row: T) => ReactNode;
}

/**
 * The one table in the product.
 *
 * Wide tables scroll inside their own container so the page body never scrolls
 * horizontally, and numeric columns get tabular figures so values do not jitter
 * as they update.
 */
export function DataTable<T>({
  columns,
  rows,
  keyOf,
  loading = false,
  empty,
  onRowClick,
}: {
  columns: Column<T>[];
  rows: T[];
  keyOf: (row: T) => string;
  loading?: boolean;
  empty?: ReactNode;
  onRowClick?: (row: T) => void;
}) {
  if (loading) {
    return (
      <div className="surface">
        <div className="table-scroll">
          <table className="data-table">
            <thead>
              <tr>
                {columns.map((column) => (
                  <th key={column.key} style={{ width: column.width }}>
                    {column.header}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {Array.from({ length: 6 }).map((_, rowIndex) => (
                <tr key={rowIndex}>
                  {columns.map((column) => (
                    <td key={column.key}>
                      <Skeleton height={12} width={column.numeric ? 40 : '70%'} />
                    </td>
                  ))}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    );
  }

  if (rows.length === 0 && empty) {
    return <div className="surface" style={{ padding: 28 }}>{empty}</div>;
  }

  return (
    <div className="surface">
      <div className="table-scroll">
        <table className="data-table">
          <thead>
            <tr>
              {columns.map((column) => (
                <th
                  key={column.key}
                  style={{ width: column.width, textAlign: column.numeric ? 'end' : undefined }}
                >
                  {column.header}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {rows.map((row) => (
              <tr
                key={keyOf(row)}
                onClick={onRowClick ? () => onRowClick(row) : undefined}
                style={onRowClick ? { cursor: 'pointer' } : undefined}
              >
                {columns.map((column) => (
                  <td key={column.key} className={column.numeric ? 'numeric' : undefined}>
                    {column.render(row)}
                  </td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}

/** Two-line cell: a primary value with a quieter second line beneath it. */
export function StackedCell({ primary, secondary }: { primary: ReactNode; secondary?: ReactNode }) {
  return (
    <div style={{ minWidth: 0 }}>
      <div className="cell-primary truncate">{primary}</div>
      {secondary && <div className="cell-secondary truncate">{secondary}</div>}
    </div>
  );
}

/**
 * Compact page control. Deliberately shows the range rather than page numbers —
 * "51–75 of 312" answers the question an admin actually has.
 */
export function Pager({
  page,
  size,
  totalItems,
  totalPages,
  onChange,
}: {
  page: number;
  size: number;
  totalItems: number;
  totalPages: number;
  onChange: (page: number) => void;
}) {
  if (totalItems === 0) return null;

  const from = page * size + 1;
  const to = Math.min((page + 1) * size, totalItems);

  return (
    <div
      className="row row-gap-3"
      style={{ justifyContent: 'space-between', padding: '12px 4px 0', flexWrap: 'wrap' }}
    >
      <span style={{ fontSize: 'var(--text-xs)', color: 'var(--muted-foreground)' }}>
        {from}–{to} of {totalItems}
      </span>
      <div className="row row-gap-2">
        <button
          type="button"
          className="icon-button"
          disabled={page === 0}
          onClick={() => onChange(page - 1)}
          aria-label="Previous page"
          style={{ opacity: page === 0 ? 0.4 : 1 }}
        >
          ‹
        </button>
        <span style={{ fontSize: 'var(--text-xs)', color: 'var(--muted-foreground)' }}>
          {page + 1} / {Math.max(totalPages, 1)}
        </span>
        <button
          type="button"
          className="icon-button"
          disabled={page + 1 >= totalPages}
          onClick={() => onChange(page + 1)}
          aria-label="Next page"
          style={{ opacity: page + 1 >= totalPages ? 0.4 : 1 }}
        >
          ›
        </button>
      </div>
    </div>
  );
}
