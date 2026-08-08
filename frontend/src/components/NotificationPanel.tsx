import { useNavigate } from 'react-router-dom';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Dialog } from '@ds/components/overlays/Dialog';
import { Button } from '@ds/components/forms/Button';
import { EmptyState } from '@ds/components/feedback/EmptyState';

import { api } from '@/lib/api';
import { relativeTime } from '@/lib/format';
import type { Inbox } from '@/lib/types';
import { IconBell } from './icons';

const SEVERITY_COLOR: Record<string, string> = {
  INFO: 'var(--info)',
  SUCCESS: 'var(--success)',
  WARNING: 'var(--warning)',
  CRITICAL: 'var(--destructive)',
};

export function NotificationPanel({
  open,
  onClose,
  inbox,
}: {
  open: boolean;
  onClose: () => void;
  inbox?: Inbox;
}) {
  const navigate = useNavigate();
  const queryClient = useQueryClient();

  const markAllRead = useMutation({
    mutationFn: () => api.post('/notifications/read-all'),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['notifications'] }),
  });

  const markRead = useMutation({
    mutationFn: (id: string) => api.post(`/notifications/${id}/read`),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['notifications'] }),
  });

  const items = inbox?.notifications.items ?? [];

  return (
    <Dialog
      open={open}
      onClose={onClose}
      title="Notifications"
      description={
        inbox && inbox.unreadCount > 0
          ? `${inbox.unreadCount} unread`
          : 'You are all caught up.'
      }
      size="sm"
      footer={
        items.length > 0 ? (
          <Button
            variant="ghost"
            size="sm"
            onClick={() => markAllRead.mutate()}
            loading={markAllRead.isPending}
          >
            Mark all as read
          </Button>
        ) : undefined
      }
    >
      {items.length === 0 ? (
        <EmptyState
          compact
          icon={<IconBell size={22} />}
          title="Nothing yet"
          description="Course assignments, deadlines and certificates will appear here."
          action={
            <Button
              size="sm"
              variant="outline"
              onClick={() => {
                onClose();
                navigate('/catalog');
              }}
            >
              Browse the catalog
            </Button>
          }
        />
      ) : (
        <div className="stack">
          {items.map((notification) => (
            <button
              key={notification.id}
              type="button"
              className="surface-row"
              style={{
                width: '100%',
                textAlign: 'start',
                background: notification.read ? 'transparent' : 'var(--primary-soft)',
                border: 0,
                borderBottom: '1px solid var(--border)',
                cursor: notification.link ? 'pointer' : 'default',
                alignItems: 'flex-start',
              }}
              onClick={() => {
                if (!notification.read) markRead.mutate(notification.id);
                if (notification.link) {
                  onClose();
                  navigate(notification.link);
                }
              }}
            >
              <span
                aria-hidden="true"
                style={{
                  width: 7,
                  height: 7,
                  borderRadius: '50%',
                  marginTop: 6,
                  flexShrink: 0,
                  background: SEVERITY_COLOR[notification.severity] ?? 'var(--info)',
                }}
              />
              <span className="stack" style={{ gap: 3, minWidth: 0 }}>
                <span style={{ fontWeight: 'var(--font-weight-semibold)', fontSize: 'var(--text-sm)' }}>
                  {notification.title}
                </span>
                {notification.body && (
                  <span style={{ fontSize: 'var(--text-xs)', color: 'var(--muted-foreground)' }}>
                    {notification.body}
                  </span>
                )}
                <span style={{ fontSize: 'var(--text-2xs)', color: 'var(--muted-foreground)' }}>
                  {relativeTime(notification.createdAt)}
                </span>
              </span>
            </button>
          ))}
        </div>
      )}
    </Dialog>
  );
}
