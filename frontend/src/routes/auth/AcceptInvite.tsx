import { useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { Button } from '@ds/components/forms/Button';
import { Input } from '@ds/components/forms/Input';
import { Label } from '@ds/components/forms/Label';
import { Alert } from '@ds/components/feedback/Alert';

import { ApiError } from '@/lib/api';
import { landingPathFor, useAuth } from '@/lib/auth';
import { useTenant } from '@/lib/tenant';
import { AuthLayout } from './AuthLayout';
import { PasswordField, scorePassword } from './PasswordField';

export function AcceptInvite() {
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const { acceptInvite } = useAuth();
  const { tenant } = useTenant();

  const token = params.get('token') ?? '';

  const [firstName, setFirstName] = useState('');
  const [lastName, setLastName] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const { score } = scorePassword(password);

  if (!token) {
    return (
      <AuthLayout title="Invitation link missing">
        <Alert tone="critical" title="This link is incomplete">
          Open the invitation straight from your email, or ask your administrator to resend it.
        </Alert>
        <div style={{ marginTop: 16 }}>
          <Link to="/sign-in" className="link">
            Go to sign in
          </Link>
        </div>
      </AuthLayout>
    );
  }

  async function onSubmit(event: React.FormEvent) {
    event.preventDefault();
    if (score < 2 || !firstName.trim()) return;

    setError(null);
    setSubmitting(true);
    try {
      const user = await acceptInvite({
        token,
        password,
        firstName: firstName.trim(),
        lastName: lastName.trim() || undefined,
      });
      navigate(landingPathFor(user), { replace: true });
    } catch (caught) {
      setError(
        caught instanceof ApiError
          ? caught.message
          : 'That did not work. Ask your administrator to resend the invitation.',
      );
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <AuthLayout
      title={`Join ${tenant?.name ?? 'your workspace'}`}
      subtitle="Set a password to activate your account and see the courses assigned to you."
    >
      <form onSubmit={onSubmit}>
        {error && (
          <div style={{ marginBottom: 14 }}>
            <Alert tone="critical" title="Could not accept the invitation">
              {error}
            </Alert>
          </div>
        )}

        <div className="row row-gap-3" style={{ alignItems: 'flex-start' }}>
          <div className="field" style={{ flex: 1 }}>
            <Label htmlFor="firstName" required>
              First name
            </Label>
            <Input
              id="firstName"
              value={firstName}
              onChange={(event) => setFirstName(event.target.value)}
              autoComplete="given-name"
              required
              autoFocus
            />
          </div>
          <div className="field" style={{ flex: 1 }}>
            <Label htmlFor="lastName">Last name</Label>
            <Input
              id="lastName"
              value={lastName}
              onChange={(event) => setLastName(event.target.value)}
              autoComplete="family-name"
            />
          </div>
        </div>

        <PasswordField
          id="password"
          label="Create a password"
          value={password}
          onChange={setPassword}
        />

        <Button
          type="submit"
          fullWidth
          size="lg"
          loading={submitting}
          disabled={score < 2 || !firstName.trim()}
        >
          Activate my account
        </Button>
      </form>
    </AuthLayout>
  );
}
