import { useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { Button } from '@ds/components/forms/Button';
import { Input } from '@ds/components/forms/Input';
import { Label } from '@ds/components/forms/Label';
import { Alert } from '@ds/components/feedback/Alert';

import { ApiError } from '@/lib/api';
import { landingPathFor, useAuth } from '@/lib/auth';
import { useTenant } from '@/lib/tenant';
import { DEMO_WORKSPACES, WorkspacePicker } from '@/components/WorkspacePicker';
import { AuthLayout } from './AuthLayout';

export function SignIn() {
  const { signIn } = useAuth();
  const { tenant, slug } = useTenant();
  const navigate = useNavigate();
  const location = useLocation();

  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<ApiError | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const returnTo = (location.state as { from?: string } | null)?.from;

  // Without a resolved workspace there is nothing to authenticate against, so
  // the first thing to ask for is the workspace address, not credentials.
  if (!slug || !tenant) {
    return (
      <AuthLayout
        title="Find your workspace"
        subtitle="Your organisation's short name — not your email address. It is the part before .learnnexus.app."
      >
        <WorkspacePicker size="md" autoFocus suggestions={DEMO_WORKSPACES} />
      </AuthLayout>
    );
  }

  async function onSubmit(event: React.FormEvent) {
    event.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      const user = await signIn(email.trim(), password);
      navigate(returnTo ?? landingPathFor(user), { replace: true });
    } catch (caught) {
      setError(caught instanceof ApiError ? caught : new ApiError(0, 'unknown', 'Sign-in failed.'));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <AuthLayout
      title={`Sign in to ${tenant.name}`}
      subtitle="Use the email address your organisation registered."
      footer={
        <>
          Not your workspace?{' '}
          <button
            type="button"
            className="link"
            style={{ background: 'none', border: 0, cursor: 'pointer', padding: 0 }}
            onClick={() => {
              localStorage.removeItem('ln.tenant');
              window.location.assign('/sign-in');
            }}
          >
            Switch workspace
          </button>
        </>
      }
    >
      <form onSubmit={onSubmit} noValidate>
        {error && (
          <div style={{ marginBottom: 14 }}>
            <Alert
              tone={error.code === 'invite_pending' ? 'info' : 'critical'}
              title={
                error.code === 'invalid_credentials'
                  ? 'Check your details'
                  : error.code === 'account_locked'
                    ? 'Account temporarily locked'
                    : error.code === 'invite_pending'
                      ? 'Finish setting up your account'
                      : 'Could not sign in'
              }
            >
              {error.message}
            </Alert>
          </div>
        )}

        <div className="field">
          <Label htmlFor="email" required>
            Email
          </Label>
          <Input
            id="email"
            type="email"
            value={email}
            onChange={(event) => setEmail(event.target.value)}
            autoComplete="username"
            required
            autoFocus
            placeholder="you@company.com"
          />
        </div>

        <div className="field">
          <div className="field-label-row">
            <Label htmlFor="password" required>
              Password
            </Label>
            <Link to="/forgot-password" className="link" style={{ fontSize: 'var(--text-xs)' }}>
              Forgot password?
            </Link>
          </div>
          <Input
            id="password"
            type="password"
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            autoComplete="current-password"
            required
            placeholder="••••••••••"
          />
        </div>

        <Button type="submit" fullWidth size="lg" loading={submitting} style={{ marginTop: 6 }}>
          Sign in
        </Button>
      </form>
    </AuthLayout>
  );
}
