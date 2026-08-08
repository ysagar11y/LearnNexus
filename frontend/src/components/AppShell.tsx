import { useEffect, useState } from 'react';
import type { ReactNode } from 'react';
import { NavLink, useLocation, useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { Avatar } from '@ds/components/core/Avatar';
import { DropdownMenu } from '@ds/components/overlays/DropdownMenu';
import { Badge } from '@ds/components/feedback/Badge';

import { api } from '@/lib/api';
import { useAuth } from '@/lib/auth';
import { useTenant } from '@/lib/tenant';
import { useTheme } from '@/lib/theme';
import type { Inbox } from '@/lib/types';
import { CommandPalette } from './CommandPalette';
import { NotificationPanel } from './NotificationPanel';
import {
  IconAudit,
  IconBell,
  IconBuilding,
  IconCertificate,
  IconChart,
  IconClose,
  IconCourses,
  IconDashboard,
  IconEnrollment,
  IconGrading,
  IconLearning,
  IconLogo,
  IconMenu,
  IconMoon,
  IconPalette,
  IconPeople,
  IconSearch,
  IconSettings,
  IconShield,
  IconSun,
} from './icons';

interface NavEntry {
  to: string;
  label: string;
  icon: ReactNode;
  end?: boolean;
  badge?: number;
}

interface NavGroup {
  section: string;
  entries: NavEntry[];
}

export function AppShell({ children }: { children: ReactNode }) {
  const { user, signOut } = useAuth();
  const { tenant } = useTenant();
  const { resolved, toggle } = useTheme();
  const navigate = useNavigate();
  const location = useLocation();

  const [railOpen, setRailOpen] = useState(false);
  const [paletteOpen, setPaletteOpen] = useState(false);
  const [notificationsOpen, setNotificationsOpen] = useState(false);

  // Notifications drive a badge in two places, so they are polled once here
  // rather than fetched by each consumer.
  const { data: inbox } = useQuery({
    queryKey: ['notifications'],
    queryFn: () => api.get<Inbox>('/notifications', { size: 12 }),
    refetchInterval: 60_000,
  });

  const { data: gradingCount } = useQuery({
    queryKey: ['grading-count'],
    queryFn: async () => {
      const page = await api.get<{ totalItems: number }>('/assessments/grading-queue', { size: 1 });
      return page.totalItems;
    },
    enabled: !!user && (user.roles.includes('INSTRUCTOR') || user.roles.includes('TENANT_ADMIN')),
    refetchInterval: 120_000,
  });

  // Close the mobile drawer whenever navigation happens.
  useEffect(() => setRailOpen(false), [location.pathname]);

  useEffect(() => {
    function onKeyDown(event: KeyboardEvent) {
      if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === 'k') {
        event.preventDefault();
        setPaletteOpen((open) => !open);
      }
    }
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, []);

  if (!user) return null;

  const isAdmin = user.roles.includes('TENANT_ADMIN') || user.roles.includes('PLATFORM_ADMIN');
  const isInstructor = user.roles.includes('INSTRUCTOR') || user.roles.includes('AUTHOR');
  const isManager = user.roles.includes('MANAGER');

  const groups: NavGroup[] = [
    {
      section: 'Learn',
      entries: [
        { to: '/', label: 'Dashboard', icon: <IconDashboard />, end: true },
        { to: '/my-learning', label: 'My learning', icon: <IconLearning /> },
        { to: '/catalog', label: 'Catalog', icon: <IconSearch /> },
        { to: '/certificates', label: 'Certificates', icon: <IconCertificate /> },
      ],
    },
  ];

  if (isInstructor || isAdmin) {
    groups.push({
      section: 'Teach',
      entries: [
        { to: '/admin/courses', label: 'Courses', icon: <IconCourses /> },
        {
          to: '/admin/grading',
          label: 'Grading',
          icon: <IconGrading />,
          badge: gradingCount || undefined,
        },
        { to: '/admin/enrollments', label: 'Enrolments', icon: <IconEnrollment /> },
      ],
    });
  }

  if (isAdmin || isManager) {
    const entries: NavEntry[] = [{ to: '/admin/reports', label: 'Reports', icon: <IconChart /> }];
    if (isAdmin) {
      entries.unshift({ to: '/admin', label: 'Overview', icon: <IconDashboard />, end: true });
      entries.push(
        { to: '/admin/people', label: 'People', icon: <IconPeople /> },
        { to: '/admin/branding', label: 'Branding', icon: <IconPalette /> },
        { to: '/admin/settings', label: 'Settings', icon: <IconSettings /> },
        { to: '/admin/audit', label: 'Audit trail', icon: <IconAudit /> },
      );
    }
    groups.push({ section: 'Manage', entries });
  }

  if (user.roles.includes('PLATFORM_ADMIN')) {
    groups.push({
      section: 'Platform',
      entries: [
        { to: '/platform', label: 'Overview', icon: <IconShield />, end: true },
        { to: '/platform/tenants', label: 'Workspaces', icon: <IconBuilding /> },
      ],
    });
  }

  const unread = inbox?.unreadCount ?? 0;

  return (
    <div className="app-shell">
      {railOpen && <div className="rail-scrim" onClick={() => setRailOpen(false)} aria-hidden="true" />}

      <nav className={`rail${railOpen ? ' rail-open' : ''}`} aria-label="Main">
        <div className="rail-header">
          {tenant?.logoUrl ? (
            <img src={tenant.logoUrl} alt={tenant.name} className="rail-logo" />
          ) : (
            <>
              <IconLogo size={22} style={{ color: 'var(--primary)' }} />
              <span className="truncate">{tenant?.name ?? 'LearnNexus'}</span>
            </>
          )}
          <button
            type="button"
            className="icon-button"
            style={{ marginInlineStart: 'auto' }}
            onClick={() => setRailOpen(false)}
            aria-label="Close navigation"
            data-mobile-only
          >
            <IconClose />
          </button>
        </div>

        <div className="rail-body">
          {groups.map((group) => (
            <div key={group.section}>
              <div className="rail-section">{group.section}</div>
              {group.entries.map((entry) => (
                <NavLink key={entry.to} to={entry.to} end={entry.end} className="rail-item">
                  {entry.icon}
                  <span className="truncate">{entry.label}</span>
                  {entry.badge ? <span className="rail-count">{entry.badge}</span> : null}
                </NavLink>
              ))}
            </div>
          ))}
        </div>

        <div className="rail-footer">
          <DropdownMenu
            align="start"
            trigger={
              <button
                type="button"
                className="rail-item"
                style={{ minHeight: 44 }}
                aria-label="Account menu"
              >
                <Avatar name={user.displayName} src={user.avatarUrl} size="sm" />
                <span className="stack" style={{ minWidth: 0 }}>
                  <span className="truncate" style={{ fontWeight: 'var(--font-weight-semibold)' }}>
                    {user.displayName}
                  </span>
                  <span
                    className="truncate"
                    style={{ fontSize: 'var(--text-2xs)', color: 'var(--sidebar-muted-foreground)' }}
                  >
                    {user.email}
                  </span>
                </span>
              </button>
            }
            items={[
              { key: 'profile', label: 'Your profile', onSelect: () => navigate('/profile') },
              {
                key: 'theme',
                label: resolved === 'dark' ? 'Switch to light' : 'Switch to dark',
                onSelect: toggle,
              },
              { separator: true },
              {
                key: 'signout',
                label: 'Sign out',
                tone: 'destructive',
                onSelect: () => {
                  void signOut().then(() => navigate('/sign-in'));
                },
              },
            ]}
          />
        </div>
      </nav>

      <div className="app-main">
        <header className="topbar">
          <button
            type="button"
            className="icon-button"
            onClick={() => setRailOpen(true)}
            aria-label="Open navigation"
            data-mobile-only
          >
            <IconMenu />
          </button>

          <button
            type="button"
            className="topbar-search"
            onClick={() => setPaletteOpen(true)}
            aria-label="Search and commands"
          >
            <IconSearch size={15} />
            <span>Search courses, people, actions…</span>
            <kbd className="kbd">⌘K</kbd>
          </button>

          <div className="spacer" />

          <button
            type="button"
            className="icon-button"
            onClick={toggle}
            aria-label={resolved === 'dark' ? 'Switch to light theme' : 'Switch to dark theme'}
          >
            {resolved === 'dark' ? <IconSun /> : <IconMoon />}
          </button>

          <button
            type="button"
            className="icon-button"
            onClick={() => setNotificationsOpen(true)}
            aria-label={unread > 0 ? `Notifications, ${unread} unread` : 'Notifications'}
          >
            <IconBell />
            {unread > 0 && <span className="unread-dot" />}
          </button>

          {user.roles.includes('PLATFORM_ADMIN') && (
            <Badge tone="brand" size="sm">
              Platform
            </Badge>
          )}
        </header>

        <main className="app-body">{children}</main>
      </div>

      <CommandPalette open={paletteOpen} onClose={() => setPaletteOpen(false)} />
      <NotificationPanel
        open={notificationsOpen}
        onClose={() => setNotificationsOpen(false)}
        inbox={inbox}
      />
    </div>
  );
}
