import { useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { Button } from '@ds/components/forms/Button';
import { Alert } from '@ds/components/feedback/Alert';

import { ApiError, api } from '@/lib/api';
import { AuthLayout } from './AuthLayout';
import { PasswordField, scorePassword } from './PasswordField';

export function ResetPassword() {
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const token = params.get('token') ?? '';

  const [password, setPassword] = useState('');
  const [confirm, setConfirm] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const { score } = scorePassword(password);
  const mismatch = confirm.length > 0 && confirm !== password;

  if (!token) {
    return (
      <AuthLayout title="Reset link missing">
        <Alert tone="critical" title="This link is incomplete">
          Open the reset link straight from your email, or request a new one.
        </Alert>
        <div style={{ marginTop: 16 }}>
          <Link to="/forgot-password" className="link">
            Request a new link
          </Link>
        </div>
      </AuthLayout>
    );
  }

  async function onSubmit(event: React.FormEvent) {
    event.preventDefault();
    if (mismatch || score < 2) return;

    setError(null);
    setSubmitting(true);
    try {
      await api.publicPost('/auth/reset-password', { token, password });
      navigate('/sign-in', { replace: true });
    } catch (caught) {
      setError(
        caught instanceof ApiError ? caught.message : 'That did not work. Request a new link.',
      );
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <AuthLayout
      title="Choose a new password"
      subtitle="Signing in again everywhere else will be required."
      footer={
        <Link to="/sign-in" className="link">
          Back to sign in
        </Link>
      }
    >
      <form onSubmit={onSubmit}>
        {error && (
          <div style={{ marginBottom: 14 }}>
            <Alert tone="critical" title="Could not reset your password">
              {error}
            </Alert>
          </div>
        )}

        <PasswordField id="password" label="New password" value={password} onChange={setPassword} />
        <PasswordField
          id="confirm"
          label="Confirm password"
          value={confirm}
          onChange={setConfirm}
          showMeter={false}
        />

        {mismatch && (
          <p style={{ fontSize: 'var(--text-xs)', color: 'var(--destructive)', marginBottom: 12 }}>
            The two passwords do not match.
          </p>
        )}

        <Button
          type="submit"
          fullWidth
          size="lg"
          loading={submitting}
          disabled={mismatch || score < 2}
        >
          Set new password
        </Button>
      </form>
    </AuthLayout>
  );
}
