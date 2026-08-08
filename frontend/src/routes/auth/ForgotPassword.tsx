import { useState } from 'react';
import { Link } from 'react-router-dom';
import { Button } from '@ds/components/forms/Button';
import { Input } from '@ds/components/forms/Input';
import { Label } from '@ds/components/forms/Label';
import { Alert } from '@ds/components/feedback/Alert';

import { api } from '@/lib/api';
import { AuthLayout } from './AuthLayout';

export function ForgotPassword() {
  const [email, setEmail] = useState('');
  const [sent, setSent] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  async function onSubmit(event: React.FormEvent) {
    event.preventDefault();
    setSubmitting(true);
    try {
      await api.publicPost('/auth/forgot-password', { email: email.trim() });
    } catch {
      // The endpoint answers identically whether or not the address exists, and
      // so does this screen — anything else would confirm which emails are real.
    } finally {
      setSubmitting(false);
      setSent(true);
    }
  }

  return (
    <AuthLayout
      title="Reset your password"
      subtitle={sent ? undefined : 'We will email you a link to choose a new one.'}
      footer={
        <Link to="/sign-in" className="link">
          Back to sign in
        </Link>
      }
    >
      {sent ? (
        <Alert tone="success" title="Check your inbox">
          If <strong>{email}</strong> belongs to an account here, a reset link is on its way. It is
          valid for two hours.
        </Alert>
      ) : (
        <form onSubmit={onSubmit}>
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
          <Button type="submit" fullWidth size="lg" loading={submitting}>
            Send reset link
          </Button>
        </form>
      )}
    </AuthLayout>
  );
}
