# Component API reference

All components accept `style` and spread remaining props onto their root
element. None accept a colour prop — tone is always chosen from a fixed set so
the palette stays derived. See `readme.md` §1.

---

## forms/

### `Button`
| prop | type | default | notes |
|---|---|---|---|
| `variant` | `primary \| accent \| secondary \| outline \| ghost \| destructive \| link` | `primary` | `accent` is marketing-CTA only, one per screen |
| `size` | `sm \| md \| lg \| icon` | `md` | `md` is 36px; `sm` for toolbars and table rows |
| `fullWidth` | `boolean` | `false` | |
| `loading` | `boolean` | `false` | shows spinner, sets `aria-busy`, disables |
| `disabled` | `boolean` | `false` | |

### `Input`
`leading`, `trailing` (ReactNode) · `invalid` (boolean) · `error` (string —
renders with `role="alert"` and wires `aria-describedby`) · `disabled` · plus
all native `<input>` props.

### `Label`
`required` (boolean — renders a screen-reader-safe marker) · `hint` (string,
right-aligned) · `htmlFor`.

### `Textarea`
`invalid` · `disabled` · `maxLength` (renders a live counter that turns
destructive inside the last 10%) · `rows` · controlled or uncontrolled.

### `Select`
`options` (`{value,label}[]`) · `value` · `onValueChange(value)` ·
`placeholder` · `invalid` · `disabled`. Native `<select>` under a styled shell —
gets the OS picker on mobile and is keyboard-correct with no ARIA authoring.

### `Checkbox`
`checked` · `indeterminate` (for bulk-select headers) · `onCheckedChange(bool)` ·
`label` · `description` · `disabled`.

### `Switch`
Same shape as `Checkbox`. **Use `Switch` only when the change applies
immediately**; use `Checkbox` when it takes effect on submit.

---

## core/

### `Card` / `CardHeader`
`elevation`: `flat \| raised \| interactive` (default `raised`). `interactive`
lifts on hover and is for clickable cards only. `padding`: `none \| sm \| md \| lg`.
`as` to change the element. `CardHeader` takes `title`, `subtitle`, `action`.

### `Avatar` / `AvatarGroup`
`src` · `name` (drives deterministic hue + initials fallback) · `size`
(`xs \| sm \| md \| lg \| xl \| 2xl`) · `status` (`online \| away \| offline`).
`AvatarGroup` takes `users[]`, `max`, `size`.

### `Progress`
`value` (0–100) · `size` (`xs \| sm \| md \| lg`) · `showLabel` · `label` ·
`tone` (`accent \| warning`). Switches to the success hue at 100%.

### `ProgressRing`
`value` · `size` (px) · `thickness` · `showValue` · `label`.

### `StatTile`
`label` · `value` · `unit` · `delta` (signed number) · `deltaTone`
(`positive \| negative \| neutral` — pass explicitly when up ≠ good, e.g.
overdue counts) · `caption` · `icon`.

### `Separator`
`orientation` (`horizontal \| vertical`) · `label` (makes it semantic;
decorative and `aria-hidden` without one).

---

## feedback/

### `Badge`
`status` — pass a raw schema enum (`PUBLISHED`, `IN_REVIEW`, `ACTIVE`,
`WAITLISTED`, `PAST_DUE`, `GRADED`…) and it resolves tone and label itself.
Or `tone` (`neutral \| brand \| success \| warning \| danger \| info \| accent`)
with explicit children. `size` (`sm \| md`) · `dot`.

### `Alert`
`severity` — accepts the `notifications.severity` enum (`INFO \| SUCCESS \|
WARNING \| CRITICAL`) — or `tone` (`info \| success \| warning \| critical`).
`title` · `children` · `action` · `onDismiss`. Every tone ships an icon; colour
is never the only signal.

### `EmptyState`
`title` · `description` · `action` · `secondaryAction` · `icon` · `compact`.
**Always pass an action.**

### `Skeleton` / `SkeletonCourseCard` / `SkeletonRows`
`Skeleton`: `width`, `height`, `radius`. `SkeletonRows`: `rows`, `columns`.
Skeletons must mirror the footprint of what they replace.

---

## navigation/

### `Tabs`
`tabs` (`{value,label,count?,disabled?}[]`) · `value` · `onValueChange` ·
`size`. Implements arrow-key roving focus per the WAI-ARIA tabs pattern.

### `Sidebar`
`items` — a flat list mixing `{section: 'Label'}` headers and
`{key,label,icon,badge}` entries · `active` · `onSelect(key)` · `collapsed` ·
`header` · `footer`.

### `Breadcrumb`
`items` (`{label,href}[]`) · `maxItems` (default 4 — collapses the middle,
keeping root + last two) · `onNavigate(item)`.

---

## overlays/

### `Dialog`
`open` · `onClose` · `title` · `description` · `children` · `footer` ·
`size` (`sm \| md \| lg \| xl`). Traps focus, restores it to the trigger, closes
on Escape, locks body scroll, and docks to the bottom as a sheet below 640px.

### `DropdownMenu`
`trigger` (ReactNode or `({open}) => ReactNode`) · `items` — entries are
`{key,label,icon?,shortcut?,tone?,disabled?,onSelect?}` or `{separator:true}` ·
`align` (`start \| end`) · `onSelect(item)`. Flips above the trigger near the
viewport bottom.

### `Tooltip`
`content` · `side` (`top \| bottom \| left \| right`) · `delay` (ms).
Opens on focus as well as hover. May only supplement a visible label.

---

## learning/

### `CourseCard`
`course`: `{title, summary, thumbnailUrl, category, level, estimatedMinutes,
lessonCount, instructor, progressPercent, isMandatory, dueAt}` ·
`mode` (`browse \| enrolled`) · `onOpen(course)`.
Falls back to a generated brand-tinted cover keyed off the title.

### `LessonRow`
`lesson`: `{title, contentType, durationSeconds, isPreview}` ·
`state` (`done \| current \| todo \| locked`) · `index` · `onOpen(lesson)`.
`contentType` accepts the `lessons.content_type` enum.

### `CertificateCard`
`certificate`: `{courseTitle, recipientName, serialNumber, issuedAt, expiresAt,
revokedAt, score}` · `onDownload` · `onVerify`.
Revoked and expired states strip the celebratory treatment — an invalid
certificate must never look valid at a glance.
