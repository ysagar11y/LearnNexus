import { Input } from '@ds/components/forms/Input';
import { Label } from '@ds/components/forms/Label';

/**
 * Password entry with a live strength meter.
 *
 * The rules mirror the server's `PasswordPolicy` so a user is never told their
 * password is fine and then rejected on submit. Length carries most of the
 * weight, which is what actually resists cracking.
 */

const MIN_LENGTH = 10;

export function scorePassword(password: string, email?: string): {
  score: 0 | 1 | 2 | 3 | 4;
  label: string;
  problem: string | null;
} {
  if (!password) return { score: 0, label: '', problem: null };

  if (password.length < MIN_LENGTH) {
    return { score: 1, label: 'Too short', problem: `Use at least ${MIN_LENGTH} characters.` };
  }

  const hasLetter = /[a-z]/i.test(password);
  const hasOther = /[^a-z]/i.test(password);
  if (!hasLetter || !hasOther) {
    return {
      score: 1,
      label: 'Weak',
      problem: 'Mix letters with at least one number or symbol.',
    };
  }

  const localPart = email?.split('@')[0]?.toLowerCase();
  if (localPart && localPart.length >= 4 && password.toLowerCase().includes(localPart)) {
    return { score: 1, label: 'Weak', problem: 'Do not include your email address.' };
  }

  let score = 2;
  if (password.length >= 14) score += 1;
  if (password.length >= 18 || (/[A-Z]/.test(password) && /\d/.test(password) && /[^\w\s]/.test(password))) {
    score += 1;
  }

  const labels = ['', 'Weak', 'Fair', 'Good', 'Strong'];
  return { score: Math.min(score, 4) as 2 | 3 | 4, label: labels[Math.min(score, 4)], problem: null };
}

export function PasswordField({
  id,
  label,
  value,
  onChange,
  email,
  autoComplete = 'new-password',
  showMeter = true,
}: {
  id: string;
  label: string;
  value: string;
  onChange: (value: string) => void;
  email?: string;
  autoComplete?: string;
  showMeter?: boolean;
}) {
  const { score, label: strengthLabel, problem } = scorePassword(value, email);

  const tone =
    score >= 4 ? 'var(--success)' : score === 3 ? 'var(--success)' : score === 2 ? 'var(--warning)' : 'var(--destructive)';

  return (
    <div className="field">
      <div className="field-label-row">
        <Label htmlFor={id} required>
          {label}
        </Label>
        {showMeter && value && (
          <span style={{ fontSize: 'var(--text-xs)', color: tone, fontWeight: 'var(--font-weight-medium)' }}>
            {strengthLabel}
          </span>
        )}
      </div>

      <Input
        id={id}
        type="password"
        value={value}
        onChange={(event) => onChange(event.target.value)}
        autoComplete={autoComplete}
        required
        minLength={MIN_LENGTH}
        placeholder="At least 10 characters"
      />

      {showMeter && value && (
        <>
          <div
            aria-hidden="true"
            style={{ display: 'flex', gap: 4, marginTop: 8 }}
          >
            {[1, 2, 3, 4].map((step) => (
              <span
                key={step}
                style={{
                  flex: 1,
                  height: 3,
                  borderRadius: 'var(--radius-full)',
                  background: step <= score ? tone : 'var(--muted)',
                  transition: 'background var(--duration-fast) var(--ease-out)',
                }}
              />
            ))}
          </div>
          {problem && (
            <p style={{ marginTop: 6, fontSize: 'var(--text-xs)', color: 'var(--muted-foreground)' }}>
              {problem}
            </p>
          )}
        </>
      )}
    </div>
  );
}
