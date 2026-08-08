# LearnNexus Design System

The visual and interaction language for LearnNexus — a multi-tenant SaaS
learning management platform. Three surfaces share one system: a marketing
site, a learner app, and an admin console.

---

## 0. The visual idea, in one line

**Deep blue-navy ground, translucent surfaces, pastel blue as the light source.**

The system is **dark-first**: `:root` is the dark theme, and light is the
override. Four rules make a dark UI read as refined rather than as "the light
one with the colours flipped":

1. **Surfaces are alpha, not solid.** One tint at rising opacity — 4.5%
   (`--surface`), 7.5% (`--surface-hover`), 10.5% (`--surface-active`), 16%
   (`--track`) — layered over the canvas. Nesting composes correctly at any
   depth because each layer adds light rather than replacing it. Never
   hand-pick a solid grey for a panel.
2. **Canvas↔surface contrast stays low** (~5% lightness). Separation is a
   hairline `--border`, not a value jump.
3. **Elevation is nearly absent.** Shadows on a dark ground read as smudge.
   Use `--edge-light` — a 1px inner top highlight — which is what actually
   reads as raised. Reserve real shadows for things that float free of the
   page: popovers, modals, the hero mock.
4. **Saturated colour is rare and small.** Only the chip palette
   (`--chip-info/hot/pro/new/warn`) is vivid. Everything structural is
   desaturated blue-grey. That restraint is what makes the few vivid pixels
   land.

Pastel blue is the **light source**: `--primary` is `brand-300` — a pastel blue
fill with dark navy text — and it is the brightest element on any screen.

Radii are soft (cards 18px, panels 24px, marketing 32px). Tight 8px corners
belong to developer consoles, not to a product used by everyone in the org.

## 1. The colour system is derived, not authored

Everything resolves from three custom properties:

```css
--brand-h: 232;    /* brand hue        0–360 */
--brand-c: 0.130;  /* brand chroma     pastel = low */
--accent-h: 38;    /* accent hue       warm counterpoint */
```

Steps **100–400 are the pastel working range** — that is where the sidebar,
panels and washes live. Steps 600+ exist only for text, icons and primary
actions, where contrast is non-negotiable.

These map 1:1 onto the `tenant_branding.brand_hue / brand_chroma / accent_hue`
columns. A tenant re-themes the entire product — app, marketing page, emails,
certificate PDFs — by writing three integers. No stylesheet rebuild.

This works because **lightness is fixed per step and only hue rotates**.
Contrast ratios therefore hold at every hue, and no tenant palette ever needs
hand-checking.

**Rules**

- Never write a hex, `rgb()`, or a named colour in a component. Ever.
- Never reference a ramp step (`--brand-600`) from a component either. Use the
  semantic layer (`--primary`, `--muted-foreground`, `--card`). The ramp is for
  the semantic layer's own definitions and for the guidelines cards.
- Status hues (`--success`, `--warning`, `--destructive`, `--info`) are fixed
  and are *not* tenant-themed. A tenant must not be able to make "failed" green.
- The warm accent is for **one element per screen at most** — the primary
  marketing CTA, a required-course flag, a certificate seal. It is not a
  secondary brand colour, and it never appears in the app shell.

## 2. Typography

Two families with distinct jobs:

- `--font-sans` (Plus Jakarta Sans) — the entire product UI. Not Inter: Inter is
  the correct neutral default and reads as exactly that, because every other B2B
  product uses it. Jakarta keeps the same discipline — wide apertures,
  unambiguous `1 l I`, tall x-height at 15px — with enough voice that the
  product does not look defaulted.
- `--font-display` (Fraunces) — marketing headlines, the learner greeting, and
  the single figure on a stat tile. **Nowhere else.** Used sparingly it signals
  "education"; used broadly it becomes costume.

Fraunces is variable with an optical-size axis, so a 30px greeting and a 76px
hero are *drawn* differently rather than being one outline scaled — which is
what stops large type looking thin and small type looking clotted. Its `SOFT`
and `WONK` axes are held low via `--font-display-variation`: a little softness
reads as considered, a lot reads as novelty, and this face carries certificate
names that have to stay credible.

Body default is 15px, not 16 — it reads better at app density. Lesson prose is
17px at 1.7 leading, capped at `--measure-prose` (68ch). The measure cap is
enforced, not suggested: prose wider than ~68ch measurably costs comprehension,
and learners read for an hour at a time here.

## 3. Elevation and motion

On dark, **border + tint carry elevation, not shadow**. Use `--edge-light` for
raised surfaces and a hairline `--border` for separation. Real shadows are for
popovers, modals and free-floating media only.

`oklch()` takes **`lightness chroma hue`**, in that order. Never pack two
channels into one custom property and interpolate it — `oklch(0.45 236 0.10)`
is read as chroma 236 (clamped to gamut) at hue 0.1, which renders vivid red.
The shadow tokens keep `--shadow-l` / `--shadow-c` / `--shadow-h` separate for
exactly this reason. This shipped as a bug once; don't reintroduce it.

**Elevation encodes interaction, not importance.** A card is not raised because
it matters more; it is raised because you can click it. Making things important
with bigger shadows is how a page ends up with six focal points and no
hierarchy.

**`--muted` vs `--track`.** `--muted` is a subtle fill for badges and inert
chips. Progress rails and sliders are thin (5–7px) and need real presence —
use `--track`. A 4.5% tint that works for a large panel disappears entirely at
rail size.

Durations are short (130–260ms). Anything above ~250ms on a control a user hits
repeatedly feels laggy rather than smooth. `--ease-spring` overshoots slightly
and is reserved for genuine achievement moments — never routine UI.

`prefers-reduced-motion` collapses every duration to 1ms. This is not optional.

## 4. The three surfaces

|                | Marketing site        | Learner app            | Admin console          |
|----------------|-----------------------|------------------------|------------------------|
| Density        | Generous              | Comfortable            | Dense                  |
| Display serif  | Headlines             | Greeting, stat figures | Stat figures only      |
| Accent colour  | Primary CTA           | Required-course flag   | Effectively unused     |
| Default control height | 46px (lg)     | 36px (md)              | 34px (sm/md)           |
| Section rhythm | `--section-y`         | 24–28px                | 22–26px                |

The sidebar sits **below the canvas in lightness** — recessed chrome, so content
is the brightest thing on screen. Its active item is `--surface-active`, the top
of the tint ladder, which reads as a lit block rather than a coloured highlight.
In light mode it inverts to a pastel-blue tinted rail with a white active pill.

## 5. Accessibility floor

Non-negotiable, and already handled in the components:

- Focus is a visible two-ring `:focus-visible` treatment. Never remove it
  without replacing it with something better.
- Colour is never the only carrier of meaning. Every `Alert` tone ships an icon;
  every `Badge` can carry a dot; progress states carry text.
- Touch targets stay at or above `--tap-min` (44px). Do not shrink for density.
- `Dialog` traps focus, restores it to the trigger, closes on Escape and locks
  body scroll. `Tabs` implements arrow-key roving focus. `Tooltip` opens on
  focus as well as hover — and may only ever supplement a visible label, never
  replace one.
- Table numerics use `font-variant-numeric: tabular-nums` so columns don't
  jitter as values update.

## 6. Layout

Mobile-first. Breakpoints: 640 / 768 / 1024 / 1280 / 1536.

- Grids collapse 3 → 2 → 1 at 1024 and 700.
- The app shell rail hides below 900px and becomes a bottom sheet or drawer.
- `Dialog` docks to the bottom as a sheet below 640px — a centred modal is
  unreachable one-handed on a phone.
- Wide content (tables, code) scrolls inside its own container. The page body
  never scrolls horizontally.

## 7. Domain components

The library carries LMS-specific primitives, not just generic UI. Prefer them
over rebuilding:

- `CourseCard` — `browse` and `enrolled` modes, generated cover fallback.
- `LessonRow` — done / current / todo / locked, with content-type glyphs.
- `CertificateCard` — celebratory when valid, deliberately stripped back when
  revoked or expired.
- `Progress` / `ProgressRing` — switch to the success hue at 100%.
- `Badge` — maps raw schema enums (`PUBLISHED`, `WAITLISTED`, `PAST_DUE`…) to
  tones, so the same status looks the same everywhere.

## 8. Empty states earn their keep

A new tenant's first impression is an empty catalog. Every `EmptyState` is
required to carry an action, not an apology. This is the highest-leverage
retention surface in the product and the most commonly neglected.

---

## File map

```
tokens/            colors · typography · spacing · effects
base.css           reset + ln-* primitives
styles.css         single entry point (import this)
components/        core · forms · feedback · navigation · overlays · learning
components/API.md  full prop reference
guidelines/        rendered foundation cards
ui_kits/           learnnexus-site · learnnexus-app · learnnexus-admin
assets/            logo mark and lockup
```

Preview cards and UI kits are **self-contained static HTML** that link
`styles.css` — no build step, no runtime bundle. They render in any browser.
