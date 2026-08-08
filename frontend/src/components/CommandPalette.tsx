import { useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';

import { api } from '@/lib/api';
import { useAuth } from '@/lib/auth';
import { useTheme } from '@/lib/theme';
import type { CourseSummary, Page, UserSummary } from '@/lib/types';
import {
  IconCertificate,
  IconChart,
  IconCourses,
  IconDashboard,
  IconLearning,
  IconPeople,
  IconSearch,
  IconSun,
} from './icons';

interface Command {
  id: string;
  label: string;
  hint?: string;
  icon: React.ReactNode;
  run: () => void;
}

/**
 * ⌘K palette: navigation, actions and live search over courses and people.
 *
 * Search only fires for staff, and only past two characters — a palette that
 * queries the API on every keystroke from every learner is a load problem
 * disguised as a feature.
 */
export function CommandPalette({ open, onClose }: { open: boolean; onClose: () => void }) {
  const navigate = useNavigate();
  const { user, isStaff, signOut } = useAuth();
  const { toggle } = useTheme();

  const [term, setTerm] = useState('');
  const [cursor, setCursor] = useState(0);
  const inputRef = useRef<HTMLInputElement>(null);
  const listRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (open) {
      setTerm('');
      setCursor(0);
      // Focus after paint so the browser does not scroll the page behind.
      requestAnimationFrame(() => inputRef.current?.focus());
    }
  }, [open]);

  const debounced = useDebounced(term, 180);
  const searchable = isStaff && debounced.trim().length >= 2;

  const { data: courses } = useQuery({
    queryKey: ['palette-courses', debounced],
    queryFn: () => api.get<Page<CourseSummary>>('/courses', { query: debounced, size: 5 }),
    enabled: open && searchable,
  });

  const { data: people } = useQuery({
    queryKey: ['palette-people', debounced],
    queryFn: () => api.get<Page<UserSummary>>('/users', { query: debounced, size: 5 }),
    enabled: open && searchable,
  });

  const commands = useMemo<Command[]>(() => {
    if (!user) return [];

    const go = (to: string) => () => {
      navigate(to);
      onClose();
    };

    const base: Command[] = [
      { id: 'nav-dashboard', label: 'Go to dashboard', icon: <IconDashboard />, run: go('/') },
      { id: 'nav-learning', label: 'Go to my learning', icon: <IconLearning />, run: go('/my-learning') },
      { id: 'nav-catalog', label: 'Browse the catalog', icon: <IconSearch />, run: go('/catalog') },
      { id: 'nav-certs', label: 'Go to certificates', icon: <IconCertificate />, run: go('/certificates') },
    ];

    if (isStaff) {
      base.push(
        { id: 'nav-courses', label: 'Manage courses', icon: <IconCourses />, run: go('/admin/courses') },
        { id: 'nav-reports', label: 'Open reports', icon: <IconChart />, run: go('/admin/reports') },
      );
    }
    if (user.roles.includes('TENANT_ADMIN') || user.roles.includes('PLATFORM_ADMIN')) {
      base.push(
        { id: 'nav-people', label: 'Manage people', icon: <IconPeople />, run: go('/admin/people') },
        {
          id: 'act-invite',
          label: 'Invite someone',
          hint: 'Action',
          icon: <IconPeople />,
          run: go('/admin/people?invite=1'),
        },
      );
    }

    base.push(
      {
        id: 'act-theme',
        label: 'Toggle light / dark',
        hint: 'Action',
        icon: <IconSun />,
        run: () => {
          toggle();
          onClose();
        },
      },
      {
        id: 'act-signout',
        label: 'Sign out',
        hint: 'Action',
        icon: <IconSearch />,
        run: () => {
          void signOut().then(() => navigate('/sign-in'));
          onClose();
        },
      },
    );

    const needle = term.trim().toLowerCase();
    const filtered = needle
      ? base.filter((command) => command.label.toLowerCase().includes(needle))
      : base;

    const results: Command[] = [...filtered];

    courses?.items.forEach((course) =>
      results.push({
        id: `course-${course.id}`,
        label: course.title,
        hint: 'Course',
        icon: <IconCourses />,
        run: go(`/admin/courses/${course.id}`),
      }),
    );

    people?.items.forEach((person) =>
      results.push({
        id: `person-${person.id}`,
        label: person.displayName,
        hint: person.email,
        icon: <IconPeople />,
        run: go(`/admin/people?user=${person.id}`),
      }),
    );

    return results;
  }, [user, isStaff, term, courses, people, navigate, onClose, toggle, signOut]);

  useEffect(() => setCursor(0), [term, commands.length]);

  if (!open) return null;

  function onKeyDown(event: React.KeyboardEvent) {
    if (event.key === 'Escape') {
      event.preventDefault();
      onClose();
      return;
    }
    if (event.key === 'ArrowDown') {
      event.preventDefault();
      setCursor((index) => Math.min(index + 1, commands.length - 1));
      return;
    }
    if (event.key === 'ArrowUp') {
      event.preventDefault();
      setCursor((index) => Math.max(index - 1, 0));
      return;
    }
    if (event.key === 'Enter') {
      event.preventDefault();
      commands[cursor]?.run();
    }
  }

  return (
    <div
      className="palette-backdrop"
      role="dialog"
      aria-modal="true"
      aria-label="Command palette"
      onMouseDown={(event) => {
        if (event.target === event.currentTarget) onClose();
      }}
    >
      <div className="palette" onKeyDown={onKeyDown}>
        <input
          ref={inputRef}
          className="palette-input"
          placeholder={isStaff ? 'Search courses, people or actions…' : 'Search actions…'}
          value={term}
          onChange={(event) => setTerm(event.target.value)}
          aria-label="Search"
          aria-controls="palette-results"
        />
        <div className="palette-list" id="palette-results" ref={listRef} role="listbox">
          {commands.length === 0 && (
            <div style={{ padding: '18px 14px', color: 'var(--muted-foreground)', fontSize: 'var(--text-sm)' }}>
              Nothing matches “{term}”.
            </div>
          )}
          {commands.map((command, index) => (
            <button
              key={command.id}
              type="button"
              role="option"
              aria-selected={index === cursor}
              data-active={index === cursor}
              className="palette-item"
              onMouseEnter={() => setCursor(index)}
              onClick={command.run}
            >
              {command.icon}
              <span className="truncate">{command.label}</span>
              {command.hint && <span className="palette-item-hint">{command.hint}</span>}
            </button>
          ))}
        </div>
      </div>
    </div>
  );
}

function useDebounced<T>(value: T, delay: number): T {
  const [debounced, setDebounced] = useState(value);
  useEffect(() => {
    const timer = setTimeout(() => setDebounced(value), delay);
    return () => clearTimeout(timer);
  }, [value, delay]);
  return debounced;
}
