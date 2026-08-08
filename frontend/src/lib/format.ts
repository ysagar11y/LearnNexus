/** Formatting helpers. Kept in one place so dates and durations read the same everywhere. */

const DATE = new Intl.DateTimeFormat('en-GB', { day: 'numeric', month: 'short', year: 'numeric' });
const DATE_SHORT = new Intl.DateTimeFormat('en-GB', { day: 'numeric', month: 'short' });
const DATE_TIME = new Intl.DateTimeFormat('en-GB', {
  day: 'numeric',
  month: 'short',
  hour: '2-digit',
  minute: '2-digit',
});
const NUMBER = new Intl.NumberFormat('en-GB');

export function formatDate(value?: string | null): string {
  return value ? DATE.format(new Date(value)) : '—';
}

export function formatDateShort(value?: string | null): string {
  return value ? DATE_SHORT.format(new Date(value)) : '—';
}

export function formatDateTime(value?: string | null): string {
  return value ? DATE_TIME.format(new Date(value)) : '—';
}

export function formatNumber(value?: number | null): string {
  return value === null || value === undefined ? '—' : NUMBER.format(value);
}

/** "2 hours ago", "in 3 days" — the unit that keeps the number small. */
export function relativeTime(value?: string | null): string {
  if (!value) return '—';
  const target = new Date(value).getTime();
  const deltaSeconds = Math.round((target - Date.now()) / 1000);
  const absolute = Math.abs(deltaSeconds);

  const formatter = new Intl.RelativeTimeFormat('en-GB', { numeric: 'auto' });
  if (absolute < 60) return formatter.format(Math.round(deltaSeconds), 'second');
  if (absolute < 3600) return formatter.format(Math.round(deltaSeconds / 60), 'minute');
  if (absolute < 86_400) return formatter.format(Math.round(deltaSeconds / 3600), 'hour');
  if (absolute < 2_592_000) return formatter.format(Math.round(deltaSeconds / 86_400), 'day');
  if (absolute < 31_536_000) return formatter.format(Math.round(deltaSeconds / 2_592_000), 'month');
  return formatter.format(Math.round(deltaSeconds / 31_536_000), 'year');
}

/** Compact course length: "1h 45m", "12m". */
export function formatDuration(minutes?: number | null): string {
  if (!minutes || minutes <= 0) return '—';
  const hours = Math.floor(minutes / 60);
  const remainder = minutes % 60;
  if (hours === 0) return `${remainder}m`;
  if (remainder === 0) return `${hours}h`;
  return `${hours}h ${remainder}m`;
}

export function formatSeconds(seconds?: number | null): string {
  if (!seconds || seconds <= 0) return '0:00';
  const minutes = Math.floor(seconds / 60);
  const remainder = Math.floor(seconds % 60);
  return `${minutes}:${String(remainder).padStart(2, '0')}`;
}

export function formatBytes(bytes?: number | null): string {
  if (!bytes) return '0 B';
  const units = ['B', 'KB', 'MB', 'GB', 'TB'];
  const exponent = Math.min(Math.floor(Math.log(bytes) / Math.log(1024)), units.length - 1);
  const value = bytes / Math.pow(1024, exponent);
  return `${value.toFixed(value >= 100 || exponent === 0 ? 0 : 1)} ${units[exponent]}`;
}

/** Turns SCREAMING_SNAKE enums into sentence case for display. */
export function humanise(value?: string | null): string {
  if (!value) return '—';
  const spaced = value.replace(/_/g, ' ').toLowerCase();
  return spaced.charAt(0).toUpperCase() + spaced.slice(1);
}

export function initials(name?: string | null): string {
  if (!name) return '?';
  return name
    .split(/\s+/)
    .slice(0, 2)
    .map((part) => part.charAt(0).toUpperCase())
    .join('');
}

/**
 * Deadline wording that says what a learner actually needs to know: how urgent
 * it is, not merely the date.
 */
export function dueLabel(dueAt?: string | null): { text: string; urgent: boolean; overdue: boolean } {
  if (!dueAt) return { text: 'No deadline', urgent: false, overdue: false };

  const due = new Date(dueAt).getTime();
  const days = Math.ceil((due - Date.now()) / 86_400_000);

  if (days < 0) return { text: `Overdue by ${Math.abs(days)}d`, urgent: true, overdue: true };
  if (days === 0) return { text: 'Due today', urgent: true, overdue: false };
  if (days === 1) return { text: 'Due tomorrow', urgent: true, overdue: false };
  if (days <= 7) return { text: `Due in ${days} days`, urgent: true, overdue: false };
  return { text: `Due ${formatDate(dueAt)}`, urgent: false, overdue: false };
}
