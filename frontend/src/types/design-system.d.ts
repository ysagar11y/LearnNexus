/**
 * Type declarations for the LearnNexus design system.
 *
 * The system is authored as plain `.jsx` so its preview cards and UI kits render
 * with no build step. These declarations mirror `design-system/components/API.md`
 * so the app still gets real prop checking. If a component's props change, update
 * this file in the same commit — nothing else enforces the agreement.
 */

declare module '@ds/styles.css';

declare module '@ds/components/forms/Button' {
  import type { ButtonHTMLAttributes, ReactNode } from 'react';

  export interface ButtonProps extends Omit<ButtonHTMLAttributes<HTMLButtonElement>, 'children'> {
    /** `accent` is reserved for a single marketing CTA and never used in the app shell. */
    variant?: 'primary' | 'accent' | 'secondary' | 'outline' | 'ghost' | 'destructive' | 'link';
    size?: 'sm' | 'md' | 'lg' | 'icon';
    fullWidth?: boolean;
    loading?: boolean;
    children?: ReactNode;
  }
  export function Button(props: ButtonProps): JSX.Element;
}

declare module '@ds/components/forms/Input' {
  import type { InputHTMLAttributes, ReactNode } from 'react';

  export interface InputProps extends InputHTMLAttributes<HTMLInputElement> {
    leading?: ReactNode;
    trailing?: ReactNode;
    invalid?: boolean;
    /** Rendered with role="alert" and wired to the input via aria-describedby. */
    error?: string;
  }
  export function Input(props: InputProps): JSX.Element;
}

declare module '@ds/components/forms/Label' {
  import type { LabelHTMLAttributes, ReactNode } from 'react';

  export interface LabelProps extends LabelHTMLAttributes<HTMLLabelElement> {
    required?: boolean;
    hint?: string;
    children?: ReactNode;
  }
  export function Label(props: LabelProps): JSX.Element;
}

declare module '@ds/components/forms/Textarea' {
  import type { TextareaHTMLAttributes } from 'react';

  export interface TextareaProps extends TextareaHTMLAttributes<HTMLTextAreaElement> {
    invalid?: boolean;
  }
  export function Textarea(props: TextareaProps): JSX.Element;
}

declare module '@ds/components/forms/Select' {
  export interface SelectOption {
    value: string;
    label: string;
  }
  export interface SelectProps {
    options: SelectOption[];
    value?: string;
    onValueChange?: (value: string) => void;
    placeholder?: string;
    invalid?: boolean;
    disabled?: boolean;
    id?: string;
    name?: string;
    style?: React.CSSProperties;
  }
  export function Select(props: SelectProps): JSX.Element;
}

declare module '@ds/components/forms/Checkbox' {
  import type { ReactNode } from 'react';

  export interface CheckboxProps {
    checked?: boolean;
    /** For bulk-select headers. */
    indeterminate?: boolean;
    onCheckedChange?: (checked: boolean) => void;
    label?: ReactNode;
    description?: ReactNode;
    disabled?: boolean;
    id?: string;
    style?: React.CSSProperties;
  }
  export function Checkbox(props: CheckboxProps): JSX.Element;
}

declare module '@ds/components/forms/Switch' {
  import type { ReactNode } from 'react';

  /** Use only when the change applies immediately; otherwise use Checkbox. */
  export interface SwitchProps {
    checked?: boolean;
    onCheckedChange?: (checked: boolean) => void;
    label?: ReactNode;
    description?: ReactNode;
    disabled?: boolean;
    id?: string;
    style?: React.CSSProperties;
  }
  export function Switch(props: SwitchProps): JSX.Element;
}

declare module '@ds/components/core/Card' {
  import type { ElementType, HTMLAttributes, ReactNode } from 'react';

  export interface CardProps extends HTMLAttributes<HTMLElement> {
    /** `interactive` lifts on hover and is for clickable cards only. */
    elevation?: 'flat' | 'raised' | 'interactive';
    padding?: 'none' | 'sm' | 'md' | 'lg';
    as?: ElementType;
    children?: ReactNode;
  }
  export function Card(props: CardProps): JSX.Element;

  export interface CardHeaderProps {
    title?: ReactNode;
    subtitle?: ReactNode;
    action?: ReactNode;
    style?: React.CSSProperties;
  }
  export function CardHeader(props: CardHeaderProps): JSX.Element;
}

declare module '@ds/components/core/Avatar' {
  export interface AvatarProps {
    src?: string | null;
    /** Drives a deterministic hue and the initials fallback. */
    name?: string;
    size?: 'xs' | 'sm' | 'md' | 'lg' | 'xl' | '2xl';
    status?: 'online' | 'away' | 'offline';
    style?: React.CSSProperties;
  }
  export function Avatar(props: AvatarProps): JSX.Element;

  export interface AvatarGroupProps {
    users: Array<{ name?: string; src?: string | null }>;
    max?: number;
    size?: AvatarProps['size'];
  }
  export function AvatarGroup(props: AvatarGroupProps): JSX.Element;
}

declare module '@ds/components/core/Progress' {
  export interface ProgressProps {
    value: number;
    size?: 'xs' | 'sm' | 'md' | 'lg';
    showLabel?: boolean;
    label?: string;
    tone?: 'accent' | 'warning';
    style?: React.CSSProperties;
  }
  /** Switches to the success hue at 100%. */
  export function Progress(props: ProgressProps): JSX.Element;

  export interface ProgressRingProps {
    value: number;
    size?: number;
    thickness?: number;
    showValue?: boolean;
    label?: string;
  }
  export function ProgressRing(props: ProgressRingProps): JSX.Element;
}

declare module '@ds/components/core/StatTile' {
  import type { ReactNode } from 'react';

  export interface StatTileProps {
    label: ReactNode;
    value: ReactNode;
    unit?: string;
    delta?: number;
    /** Pass explicitly when up is not good — overdue counts, for instance. */
    deltaTone?: 'positive' | 'negative' | 'neutral';
    caption?: ReactNode;
    icon?: ReactNode;
    style?: React.CSSProperties;
  }
  export function StatTile(props: StatTileProps): JSX.Element;
}

declare module '@ds/components/core/Separator' {
  export interface SeparatorProps {
    orientation?: 'horizontal' | 'vertical';
    /** Makes the separator semantic; without it it is decorative and aria-hidden. */
    label?: string;
    style?: React.CSSProperties;
  }
  export function Separator(props: SeparatorProps): JSX.Element;
}

declare module '@ds/components/feedback/Badge' {
  import type { ReactNode } from 'react';

  export interface BadgeProps {
    /** A raw schema enum — PUBLISHED, WAITLISTED, PAST_DUE … — resolves tone and label itself. */
    status?: string;
    tone?: 'neutral' | 'brand' | 'success' | 'warning' | 'danger' | 'info' | 'accent';
    size?: 'sm' | 'md';
    dot?: boolean;
    children?: ReactNode;
    style?: React.CSSProperties;
  }
  export function Badge(props: BadgeProps): JSX.Element;
}

declare module '@ds/components/feedback/Alert' {
  import type { ReactNode } from 'react';

  export interface AlertProps {
    /** Accepts the notifications.severity enum. */
    severity?: 'INFO' | 'SUCCESS' | 'WARNING' | 'CRITICAL';
    tone?: 'info' | 'success' | 'warning' | 'critical';
    title?: ReactNode;
    action?: ReactNode;
    onDismiss?: () => void;
    children?: ReactNode;
    style?: React.CSSProperties;
  }
  export function Alert(props: AlertProps): JSX.Element;
}

declare module '@ds/components/feedback/EmptyState' {
  import type { ReactNode } from 'react';

  export interface EmptyStateProps {
    title: ReactNode;
    description?: ReactNode;
    /** Always pass an action — see readme §8. */
    action?: ReactNode;
    secondaryAction?: ReactNode;
    icon?: ReactNode;
    compact?: boolean;
    style?: React.CSSProperties;
  }
  export function EmptyState(props: EmptyStateProps): JSX.Element;
}

declare module '@ds/components/feedback/Skeleton' {
  export interface SkeletonProps {
    width?: number | string;
    height?: number | string;
    radius?: number | string;
    style?: React.CSSProperties;
  }
  export function Skeleton(props: SkeletonProps): JSX.Element;
  export function SkeletonCourseCard(): JSX.Element;
  export function SkeletonRows(props: { rows?: number; columns?: number }): JSX.Element;
}

declare module '@ds/components/navigation/Tabs' {
  export interface TabDefinition {
    value: string;
    label: string;
    count?: number;
    disabled?: boolean;
  }
  export interface TabsProps {
    tabs: TabDefinition[];
    value: string;
    onValueChange: (value: string) => void;
    size?: 'sm' | 'md';
    style?: React.CSSProperties;
  }
  export function Tabs(props: TabsProps): JSX.Element;
}

declare module '@ds/components/navigation/Sidebar' {
  import type { ReactNode } from 'react';

  export type SidebarItem =
    | { section: string }
    | { key: string; label: string; icon?: ReactNode; badge?: number | string };

  export interface SidebarProps {
    items: SidebarItem[];
    active?: string;
    onSelect?: (key: string) => void;
    collapsed?: boolean;
    header?: ReactNode;
    footer?: ReactNode;
    style?: React.CSSProperties;
  }
  export function Sidebar(props: SidebarProps): JSX.Element;
}

declare module '@ds/components/navigation/Breadcrumb' {
  export interface BreadcrumbItem {
    label: string;
    href?: string;
  }
  export interface BreadcrumbProps {
    items: BreadcrumbItem[];
    /** Collapses the middle, keeping root plus the last two. */
    maxItems?: number;
    onNavigate?: (item: BreadcrumbItem) => void;
  }
  export function Breadcrumb(props: BreadcrumbProps): JSX.Element;
}

declare module '@ds/components/overlays/Dialog' {
  import type { ReactNode } from 'react';

  export interface DialogProps {
    open: boolean;
    onClose: () => void;
    title?: ReactNode;
    description?: ReactNode;
    footer?: ReactNode;
    size?: 'sm' | 'md' | 'lg' | 'xl';
    children?: ReactNode;
  }
  /** Traps focus, restores it to the trigger, closes on Escape, docks as a sheet below 640px. */
  export function Dialog(props: DialogProps): JSX.Element;
}

declare module '@ds/components/overlays/DropdownMenu' {
  import type { ReactNode } from 'react';

  export type DropdownItem =
    | { separator: true }
    | {
        key: string;
        label: string;
        icon?: ReactNode;
        shortcut?: string;
        tone?: 'default' | 'destructive';
        disabled?: boolean;
        onSelect?: () => void;
      };

  export interface DropdownMenuProps {
    trigger: ReactNode | ((state: { open: boolean }) => ReactNode);
    items: DropdownItem[];
    align?: 'start' | 'end';
    onSelect?: (item: DropdownItem) => void;
  }
  export function DropdownMenu(props: DropdownMenuProps): JSX.Element;
}

declare module '@ds/components/overlays/Tooltip' {
  import type { ReactNode } from 'react';

  export interface TooltipProps {
    content: ReactNode;
    side?: 'top' | 'bottom' | 'left' | 'right';
    delay?: number;
    children?: ReactNode;
  }
  /** Opens on focus as well as hover, and may only supplement a visible label. */
  export function Tooltip(props: TooltipProps): JSX.Element;
}

declare module '@ds/components/learning/CourseCard' {
  export interface CourseCardCourse {
    id?: string;
    title: string;
    summary?: string | null;
    thumbnailUrl?: string | null;
    category?: string | null;
    level?: string;
    estimatedMinutes?: number;
    lessonCount?: number;
    instructor?: string | null;
    progressPercent?: number;
    isMandatory?: boolean;
    dueAt?: string | null;
  }
  export interface CourseCardProps {
    course: CourseCardCourse;
    mode?: 'browse' | 'enrolled';
    onOpen?: (course: CourseCardCourse) => void;
  }
  /** Falls back to a generated brand-tinted cover keyed off the title. */
  export function CourseCard(props: CourseCardProps): JSX.Element;
}

declare module '@ds/components/learning/LessonRow' {
  export interface LessonRowLesson {
    id?: string;
    title: string;
    contentType?: string;
    durationSeconds?: number;
    isPreview?: boolean;
  }
  export interface LessonRowProps {
    lesson: LessonRowLesson;
    state?: 'done' | 'current' | 'todo' | 'locked';
    index?: number;
    onOpen?: (lesson: LessonRowLesson) => void;
  }
  export function LessonRow(props: LessonRowProps): JSX.Element;
}

declare module '@ds/components/learning/CertificateCard' {
  export interface CertificateCardCertificate {
    id?: string;
    courseTitle: string;
    recipientName: string;
    serialNumber: string;
    issuedAt: string;
    expiresAt?: string | null;
    revokedAt?: string | null;
    score?: number | string | null;
  }
  export interface CertificateCardProps {
    certificate: CertificateCardCertificate;
    onDownload?: (certificate: CertificateCardCertificate) => void;
    onVerify?: (certificate: CertificateCardCertificate) => void;
  }
  /** Revoked and expired states drop the celebratory treatment on purpose. */
  export function CertificateCard(props: CertificateCardProps): JSX.Element;
}
