# Deploying LearnNexus for free

A working stack for **$0, forever**, inside each provider's permanent free
tier — no credit card required anywhere in this guide. Three signups, about
15 minutes end to end.

| Layer | Provider | Why this one |
|---|---|---|
| Backend (API) | [Render](https://render.com) | Free Docker web service, no card, deploys straight from this repo's `Dockerfile`. |
| Database | [Neon](https://neon.tech) | Free Postgres with no expiry (unlike Render's own free Postgres, which is deleted after a trial window). |
| Frontend | [Cloudflare Pages](https://pages.cloudflare.com) | Free static hosting, generous limits, deploys straight from this repo. |

Object storage and outbound email are **not required** — see [What's
intentionally left out](#whats-intentionally-left-out) for why, and how to add
either later if you want to.

The one real cost of $0: Render's free plan spins the backend down after ~15
minutes with no traffic, and the next request pays a ~30–60s cold start to
wake it back up. Fine for friends trying it out; not what you'd want for a
public launch.

---

## 0. Push this repo to GitHub

Both Render and Cloudflare Pages deploy by connecting to a git repository —
there is no dashboard upload path used here. If you haven't already:

1. Create an empty repo at **github.com/new** — name it `learnnexus`, pick
   Private or Public (either works identically below), and do **not** let
   GitHub add a README, `.gitignore` or licence. The repo must start empty
   or the first push is rejected as a non-fast-forward.

2. Add the remote and push:

   ```bash
   git remote add origin git@github-personal:ysagar11y/learnnexus.git
   git push -u origin main
   ```

> **Use the `github-personal` host, not `github.com`.** This machine has two
> GitHub accounts and `~/.ssh/config` picks the key by host alias:
> `github.com` → the org account, `github-personal` → the personal account.
> A remote written as `git@github.com:ysagar11y/...` offers the org key and
> the push is rejected with *"Permission to ysagar11y/learnnexus denied to
> ysagaropti"*. Verify before pushing:
>
> ```bash
> git remote -v      # must show github-personal
> ```

---

## 1. Database — Neon

1. Sign up at **neon.tech** (GitHub login is fastest).
2. Create a project. Any region; pick one close to you.
3. On the project dashboard, copy the **connection string**. It looks like:
   ```
   postgresql://neondb_owner:AbC123xyz@ep-cool-name-12345.us-east-1.aws.neon.tech/neondb?sslmode=require
   ```
4. Split that one string into the three values the backend wants — you'll
   paste these into Render in the next step:

   | Render env var | Value |
   |---|---|
   | `DB_URL` | `jdbc:postgresql://ep-cool-name-12345.us-east-1.aws.neon.tech/neondb?sslmode=require` (same host/db/query string, with `jdbc:` glued on the front and the `user:password@` part removed) |
   | `DB_USER` | `neondb_owner` |
   | `DB_PASSWORD` | `AbC123xyz` |

That's it for Neon — the backend's own Flyway migration creates every table
on first boot.

---

## 2. Backend — Render

1. Sign up at **render.com** (GitHub login is fastest — it also grants Render
   read access to your repos, which the next step needs).
2. Go to **dashboard.render.com/blueprints** → **New Blueprint Instance** →
   pick this repo. Render reads `render.yaml` from the repo root and proposes
   one service, `learnnexus-api`, on the free plan.
3. Before clicking deploy, fill in the env vars Render is prompting for
   (the blueprint marks these `sync: false`, meaning "ask the human"):
   - `DB_URL`, `DB_USER`, `DB_PASSWORD` — from step 1.
   - `CORS_ORIGINS`, `PUBLIC_BASE_URL` — leave these blank for now; you'll
     come back and set them in step 4, once the frontend has a URL.
4. Deploy. First build takes a few minutes (it's building the Docker image
   from source). When it's live, Render shows you the service's URL —
   something like `https://learnnexus-api-xxxx.onrender.com`. **Copy it.**
5. Confirm it's actually up:
   ```bash
   curl https://learnnexus-api-xxxx.onrender.com/actuator/health
   # {"status":"UP","groups":["liveness","readiness"]}
   ```

`JWT_SECRET` is generated automatically by Render (`generateValue: true` in
the blueprint) — you never need to invent or paste one.

---

## 3. Frontend — Cloudflare Pages

1. Sign up at **pages.cloudflare.com** (a regular Cloudflare account).
2. **Create a project** → **Connect to Git** → pick this repo.
3. Build settings:

   | Setting | Value |
   |---|---|
   | Framework preset | Vite |
   | Root directory | `frontend` |
   | Build command | `npm run build` |
   | Build output directory | `dist` |

4. **Before the first deploy**, add one environment variable (Pages calls
   this "Environment variables" in the project settings, under Build):

   | Variable | Value |
   |---|---|
   | `VITE_API_BASE_URL` | the Render URL from step 2, e.g. `https://learnnexus-api-xxxx.onrender.com` |

   This has to be set *before* you build — Vite bakes it into the JS bundle
   at build time, it isn't read at runtime.

5. Deploy. Cloudflare gives you a URL like `https://learnnexus-xx.pages.dev`.
   **Copy it.**

---

## 4. Close the loop: tell the backend about the frontend

Back in Render, open `learnnexus-api` → **Environment**, and set the two
values you left blank in step 2:

| Env var | Value |
|---|---|
| `CORS_ORIGINS` | the Cloudflare Pages URL from step 3, e.g. `https://learnnexus-xx.pages.dev` |
| `PUBLIC_BASE_URL` | the same URL — it's what gets used in email links and certificate-verification links |

Saving triggers a redeploy (a minute or so, no rebuild needed — just a
restart with the new env).

---

## 5. Try it

Open the Cloudflare Pages URL. You'll land on sign-in with a workspace
picker — click one of the demo workspaces it lists (**Acme Corp**,
**Northwind Institute**, **Platform console**), then sign in with any of the
seeded accounts from the main [README](../README.md#demo-accounts) (password
`Learn@2026` for all of them).

Send friends the same Pages URL. `SEED_ENABLED=true` means the demo tenants
are always there; re-deploys don't duplicate them (the seeder checks first).

---

## What's intentionally left out

**Object storage (S3/R2).** Nothing in the UI uploads media — logos and
lesson URLs are plain text fields, not file pickers — and certificate PDFs
are rendered fresh on every download rather than read back from storage. So
`S3_ENDPOINT` is left pointing at a local address that Render can't reach,
and the only effect is a caught-and-logged error when a completed course
tries to cache its certificate PDF to storage. Confirmed by running the
container against real Postgres with storage deliberately unreachable:
startup succeeds, demo seeding completes, sign-in works, certificate PDFs
still download. If you later add a file-upload UI, wire up
[Cloudflare R2](https://developers.cloudflare.com/r2/) (10 GB free, S3-
compatible, same account as Pages) and point `S3_ENDPOINT` /
`S3_ACCESS_KEY` / `S3_SECRET_KEY` / `S3_BUCKET` at it.

**Outbound email.** `MAIL_ENABLED=false` means invite/reset/notification
emails are logged, not sent — the in-app notification still appears either
way. To turn real email on: sign up for
[Resend](https://resend.com) (100/day free, no card) or
[Brevo](https://www.brevo.com) (300/day free), set `MAIL_ENABLED=true`, and
point `MAIL_HOST` / `MAIL_PORT` (plus SMTP credentials, which need adding as
env vars alongside the existing `spring.mail.username`/`password`
properties) at their relay.

**Redis.** Declared as a dependency for future caching work but nothing in
the codebase calls it yet — see `docs/ARCHITECTURE.md`. No signup needed.

**Per-tenant subdomains.** The production design is
`acme.learnnexus.app`, resolved server-side from the host. A `*.pages.dev` /
`*.onrender.com` free deployment has no wildcard DNS to make that work, so
the frontend falls back to its header/query-param tenant resolution instead
— which is exactly what local development already uses. Functionally
identical; the only difference a visitor sees is picking their workspace
once via the sign-in picker rather than it being implied by the URL.

---

## Updating the deployment

Both Render and Cloudflare Pages auto-deploy on push to `main` once
connected — `git push` is the entire update workflow. No manual redeploy
step for routine changes.

## Free-tier limits worth knowing

| | Render (free) | Neon (free) | Cloudflare Pages (free) |
|---|---|---|---|
| Sleeps when idle | ~15 min → 30–60s cold start | ~5 min → a few seconds | Never (static) |
| Storage / compute cap | 750 hrs/month | 0.5 GB storage | 500 builds/month |
| Card required | No | No | No |
| Expires | Never | Never | Never |
