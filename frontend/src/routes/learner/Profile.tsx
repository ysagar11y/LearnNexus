import { useEffect, useState } from 'react';
import { useMutation } from '@tanstack/react-query';
import { Button } from '@ds/components/forms/Button';
import { Input } from '@ds/components/forms/Input';
import { Label } from '@ds/components/forms/Label';
import { Avatar } from '@ds/components/core/Avatar';
import { Badge } from '@ds/components/feedback/Badge';
import { Alert } from '@ds/components/feedback/Alert';

import { ApiError, api } from '@/lib/api';
import { useAuth } from '@/lib/auth';
import { useTheme } from '@/lib/theme';
import { formatDateTime, humanise } from '@/lib/format';
import { PageHeader } from '@/components/states';
import { PasswordField, scorePassword } from '../auth/PasswordField';

export default function Profile() {
  const { user, refreshProfile, signOut } = useAuth();
  const { choice, setChoice } = useTheme();

  const [firstName, setFirstName] = useState('');
  const [lastName, setLastName] = useState('');
  const [jobTitle, setJobTitle] = useState('');
  const [phone, setPhone] = useState('');
  const [saved, setSaved] = useState(false);

  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [passwordError, setPasswordError] = useState<string | null>(null);
  const [passwordChanged, setPasswordChanged] = useState(false);

  useEffect(() => {
    if (!user) return;
    setFirstName(user.firstName);
    setLastName(user.lastName ?? '');
    setJobTitle(user.jobTitle ?? '');
  }, [user]);

  const saveProfile = useMutation({
    mutationFn: () =>
      api.put('/users/me', {
        firstName: firstName.trim(),
        lastName: lastName.trim() || null,
        jobTitle: jobTitle.trim() || null,
        phone: phone.trim() || null,
        locale: user?.locale ?? null,
        timezone: user?.timezone ?? null,
        avatarUrl: user?.avatarUrl ?? null,
      }),
    onSuccess: async () => {
      await refreshProfile();
      setSaved(true);
    },
  });

  const changePassword = useMutation({
    mutationFn: () => api.post('/auth/change-password', { currentPassword, newPassword }),
    onSuccess: async () => {
      setPasswordChanged(true);
      setCurrentPassword('');
      setNewPassword('');
      // Every other session was just revoked, so this one goes too.
      setTimeout(() => void signOut(), 2500);
    },
    onError: (error) => {
      setPasswordError(error instanceof ApiError ? error.message : 'Could not change your password.');
    },
  });

  if (!user) return null;

  const { score } = scorePassword(newPassword, user.email);

  return (
    <div className="app-inner" style={{ maxWidth: 760 }}>
      <PageHeader title="Your profile" subtitle="How you appear to others in this workspace." />

      <div className="surface" style={{ padding: 22, marginBottom: 22 }}>
        <div className="row row-gap-4" style={{ marginBottom: 20 }}>
          <Avatar name={user.displayName} src={user.avatarUrl} size="xl" />
          <div style={{ minWidth: 0 }}>
            <div style={{ fontSize: 'var(--text-lg)', fontWeight: 'var(--font-weight-semibold)' }}>
              {user.displayName}
            </div>
            <div className="meta" style={{ marginTop: 4 }}>
              <span>{user.email}</span>
              {user.orgUnitName && (
                <>
                  <span className="meta-dot" />
                  <span>{user.orgUnitName}</span>
                </>
              )}
            </div>
            <div className="row row-gap-2 row-wrap" style={{ marginTop: 8 }}>
              {user.roles.map((role) => (
                <Badge key={role} tone="brand" size="sm">
                  {humanise(role)}
                </Badge>
              ))}
            </div>
          </div>
        </div>

        {saved && (
          <div style={{ marginBottom: 16 }}>
            <Alert tone="success" title="Profile saved" onDismiss={() => setSaved(false)} />
          </div>
        )}

        <form
          onSubmit={(event) => {
            event.preventDefault();
            setSaved(false);
            saveProfile.mutate();
          }}
        >
          <div className="row row-gap-3" style={{ alignItems: 'flex-start' }}>
            <div className="field" style={{ flex: 1 }}>
              <Label htmlFor="firstName" required>
                First name
              </Label>
              <Input
                id="firstName"
                value={firstName}
                onChange={(event) => setFirstName(event.target.value)}
                required
              />
            </div>
            <div className="field" style={{ flex: 1 }}>
              <Label htmlFor="lastName">Last name</Label>
              <Input
                id="lastName"
                value={lastName}
                onChange={(event) => setLastName(event.target.value)}
              />
            </div>
          </div>

          <div className="row row-gap-3" style={{ alignItems: 'flex-start' }}>
            <div className="field" style={{ flex: 1 }}>
              <Label htmlFor="jobTitle">Job title</Label>
              <Input
                id="jobTitle"
                value={jobTitle}
                onChange={(event) => setJobTitle(event.target.value)}
                placeholder="Senior Engineer"
              />
            </div>
            <div className="field" style={{ flex: 1 }}>
              <Label htmlFor="phone">Phone</Label>
              <Input
                id="phone"
                value={phone}
                onChange={(event) => setPhone(event.target.value)}
                placeholder="Optional"
              />
            </div>
          </div>

          <Button type="submit" loading={saveProfile.isPending}>
            Save changes
          </Button>
        </form>
      </div>

      <div className="surface" style={{ padding: 22, marginBottom: 22 }}>
        <h2 style={{ fontSize: 'var(--text-base)', marginBottom: 4 }}>Appearance</h2>
        <p style={{ fontSize: 'var(--text-sm)', color: 'var(--muted-foreground)', marginBottom: 14 }}>
          Follows your device by default.
        </p>
        <div className="row row-gap-2">
          {(['light', 'dark', 'system'] as const).map((option) => (
            <Button
              key={option}
              size="sm"
              variant={choice === option ? 'primary' : 'outline'}
              onClick={() => setChoice(option)}
            >
              {humanise(option)}
            </Button>
          ))}
        </div>
      </div>

      <div className="surface" style={{ padding: 22 }}>
        <h2 style={{ fontSize: 'var(--text-base)', marginBottom: 4 }}>Password</h2>
        <p style={{ fontSize: 'var(--text-sm)', color: 'var(--muted-foreground)', marginBottom: 14 }}>
          Changing it signs you out everywhere, including here.
        </p>

        {passwordChanged ? (
          <Alert tone="success" title="Password changed">
            Signing you out now — use your new password to sign back in.
          </Alert>
        ) : (
          <form
            onSubmit={(event) => {
              event.preventDefault();
              setPasswordError(null);
              changePassword.mutate();
            }}
          >
            {passwordError && (
              <div style={{ marginBottom: 14 }}>
                <Alert tone="critical" title="Could not change your password">
                  {passwordError}
                </Alert>
              </div>
            )}

            <div className="field">
              <Label htmlFor="currentPassword" required>
                Current password
              </Label>
              <Input
                id="currentPassword"
                type="password"
                autoComplete="current-password"
                value={currentPassword}
                onChange={(event) => setCurrentPassword(event.target.value)}
                required
              />
            </div>

            <PasswordField
              id="newPassword"
              label="New password"
              value={newPassword}
              onChange={setNewPassword}
              email={user.email}
            />

            <Button
              type="submit"
              variant="outline"
              loading={changePassword.isPending}
              disabled={!currentPassword || score < 2}
            >
              Change password
            </Button>
          </form>
        )}

        <p style={{ marginTop: 16, fontSize: 'var(--text-xs)', color: 'var(--muted-foreground)' }}>
          Last signed in {formatDateTime(user.lastLoginAt)}
        </p>
      </div>
    </div>
  );
}
