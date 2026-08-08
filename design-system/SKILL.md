---
name: learnnexus-design
description: Use this skill to design and build interfaces for LearnNexus, a multi-tenant SaaS learning management platform. Contains the OKLCH token system, component library, layout rules and UI kits — for production React code or for throwaway mocks, slides and prototypes.
user-invocable: true
---

Read `readme.md` first — it carries the rules that matter, and most mistakes
come from skipping it.

**The system is dark-first.** `:root` is the dark theme; light is the override.
Surfaces are one tint at rising alpha (4.5 / 7.5 / 10.5 / 16%) layered over the
canvas — never hand-picked solid greys. Elevation comes from `--edge-light` and
a hairline `--border`, not from shadows. Pastel blue (`--primary`, a `brand-300`
fill with dark navy text) is the light source and the brightest thing on screen.

**The one thing to get right:** never write a hardcoded colour. Every colour
derives from three custom properties (`--brand-h`, `--brand-c`, `--accent-h`)
via the semantic tokens in `tokens/colors.css`. Tenants re-theme the product by
changing those three numbers, so a literal `#3b82f6` anywhere is a bug that only
shows up on someone else's tenant.

**And one syntax trap:** `oklch()` takes `lightness chroma hue`. Do not pack two
channels into one custom property — `oklch(0.45 236 0.10)` renders vivid red.

Where things live:

- `tokens/` — colour, type, spacing, elevation. Start here.
- `base.css` — reset plus the `ln-*` primitives (`ln-container`, `ln-prose`,
  `ln-display`, `ln-eyebrow`, `ln-wash`).
- `components/<group>/*.jsx` — the React library. Each file's header comment
  explains *why* it is built that way; read it before changing behaviour.
- `components/API.md` — every component's props in one place.
- `guidelines/*.card.html` — rendered foundations (ramps, type scale, elevation,
  tenant theming).
- `ui_kits/` — three complete reference screens. Copy their structure rather
  than inventing new layouts.

Working modes:

- **Production code** — import from `components/`, style with the semantic
  tokens, follow `readme.md`.
- **Mocks, slides, prototypes** — write self-contained static HTML that links
  `styles.css`. Every file in `guidelines/` and `ui_kits/` is built this way and
  is a good template to copy.

If invoked with no direction, ask what is being designed and for which surface
(marketing site, learner app, or admin console) — the three have different
density and tone rules, set out in `readme.md`.
