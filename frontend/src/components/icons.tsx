/**
 * Inline icons.
 *
 * Drawn on a 16px grid with 1.5 stroke to match the weight of the design
 * system's UI type. Everything uses `currentColor` so an icon inherits its
 * surface's foreground — which is what lets the same icon sit on the pastel
 * sidebar and on a white card without a second variant.
 */

import type { SVGProps } from 'react';

type IconProps = SVGProps<SVGSVGElement> & { size?: number };

function Icon({ size = 17, children, ...props }: IconProps) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 16 16"
      fill="none"
      aria-hidden="true"
      focusable="false"
      {...props}
    >
      {children}
    </svg>
  );
}

const stroke = {
  stroke: 'currentColor',
  strokeWidth: 1.5,
  strokeLinecap: 'round' as const,
  strokeLinejoin: 'round' as const,
};

export const IconDashboard = (p: IconProps) => (
  <Icon {...p}>
    <rect x="2" y="2" width="5" height="5" rx="1.4" fill="currentColor" />
    <rect x="9" y="2" width="5" height="5" rx="1.4" fill="currentColor" opacity=".45" />
    <rect x="2" y="9" width="5" height="5" rx="1.4" fill="currentColor" opacity=".45" />
    <rect x="9" y="9" width="5" height="5" rx="1.4" fill="currentColor" opacity=".45" />
  </Icon>
);

export const IconLearning = (p: IconProps) => (
  <Icon {...p}>
    <rect x="2.2" y="3" width="11.6" height="10" rx="1.6" {...stroke} />
    <path d="M8 3v10" {...stroke} />
  </Icon>
);

export const IconSearch = (p: IconProps) => (
  <Icon {...p}>
    <circle cx="7.2" cy="7.2" r="4.6" {...stroke} />
    <path d="M10.6 10.6 14 14" {...stroke} />
  </Icon>
);

export const IconCalendar = (p: IconProps) => (
  <Icon {...p}>
    <rect x="2.4" y="3.4" width="11.2" height="9.2" rx="1.6" {...stroke} />
    <path d="M2.4 6.2h11.2M5.4 2.2v2.2M10.6 2.2v2.2" {...stroke} />
  </Icon>
);

export const IconCertificate = (p: IconProps) => (
  <Icon {...p}>
    <circle cx="8" cy="6.4" r="3.4" {...stroke} />
    <path d="M4.6 9.6 3.6 14 8 12l4.4 2-1-4.4" {...stroke} />
  </Icon>
);

export const IconCourses = (p: IconProps) => (
  <Icon {...p}>
    <path d="M2 4.4 8 2l6 2.4L8 6.8 2 4.4Z" {...stroke} />
    <path d="M4.4 5.6v3.8c0 1 1.6 1.8 3.6 1.8s3.6-.8 3.6-1.8V5.6" {...stroke} />
    <path d="M14 4.6v4" {...stroke} />
  </Icon>
);

export const IconPeople = (p: IconProps) => (
  <Icon {...p}>
    <circle cx="6.2" cy="5.6" r="2.6" {...stroke} />
    <path d="M1.8 13.4c0-2.2 2-3.8 4.4-3.8s4.4 1.6 4.4 3.8" {...stroke} />
    <path d="M11 3.4a2.4 2.4 0 0 1 0 4.5M12.2 9.9c1.3.5 2.2 1.6 2.2 3.1" {...stroke} opacity=".55" />
  </Icon>
);

export const IconChart = (p: IconProps) => (
  <Icon {...p}>
    <path d="M2.4 13.2h11.2" {...stroke} />
    <path d="M4.4 13.2V8M7.6 13.2V4.4M10.8 13.2v-3.4" {...stroke} />
  </Icon>
);

export const IconEnrollment = (p: IconProps) => (
  <Icon {...p}>
    <rect x="2.4" y="2.6" width="8.6" height="10.8" rx="1.6" {...stroke} />
    <path d="M5 5.8h3.4M5 8.2h3.4M5 10.6h2" {...stroke} />
    <path d="m10.8 9.6 1.6 1.6 2.4-3" {...stroke} />
  </Icon>
);

export const IconPalette = (p: IconProps) => (
  <Icon {...p}>
    <path d="M8 2a6 6 0 0 0 0 12c.8 0 1.2-.6 1.2-1.2 0-.9-.7-1.1-.7-1.9 0-.6.5-1 1.1-1H11a3 3 0 0 0 3-3c0-2.7-2.7-4.9-6-4.9Z" {...stroke} />
    <circle cx="5.4" cy="7" r=".9" fill="currentColor" />
    <circle cx="8" cy="5.2" r=".9" fill="currentColor" />
    <circle cx="10.8" cy="6.8" r=".9" fill="currentColor" />
  </Icon>
);

export const IconSettings = (p: IconProps) => (
  <Icon {...p}>
    <circle cx="8" cy="8" r="2.2" {...stroke} />
    <path d="M8 1.8v1.6M8 12.6v1.6M14.2 8h-1.6M3.4 8H1.8M12.4 3.6l-1.1 1.1M4.7 11.3l-1.1 1.1M12.4 12.4l-1.1-1.1M4.7 4.7 3.6 3.6" {...stroke} />
  </Icon>
);

export const IconShield = (p: IconProps) => (
  <Icon {...p}>
    <path d="M8 1.8 13 3.6v4.2c0 3-2.1 5.4-5 6.4-2.9-1-5-3.4-5-6.4V3.6L8 1.8Z" {...stroke} />
    <path d="m5.9 8 1.5 1.5 2.9-3" {...stroke} />
  </Icon>
);

export const IconBuilding = (p: IconProps) => (
  <Icon {...p}>
    <rect x="2.4" y="2.6" width="7" height="10.8" rx="1.2" {...stroke} />
    <path d="M9.4 6.4h3a1.2 1.2 0 0 1 1.2 1.2v5.8" {...stroke} />
    <path d="M4.6 5.2h2.6M4.6 7.6h2.6M4.6 10h2.6M11.2 9h1.4" {...stroke} />
  </Icon>
);

export const IconBell = (p: IconProps) => (
  <Icon {...p}>
    <path d="M4.2 6.6a3.8 3.8 0 0 1 7.6 0c0 3 1.2 3.9 1.2 3.9H3s1.2-.9 1.2-3.9Z" {...stroke} />
    <path d="M6.6 12.6a1.6 1.6 0 0 0 2.8 0" {...stroke} />
  </Icon>
);

export const IconGrading = (p: IconProps) => (
  <Icon {...p}>
    <path d="M2.6 3.4a1.4 1.4 0 0 1 1.4-1.4h6l3.4 3.4v7.2a1.4 1.4 0 0 1-1.4 1.4H4a1.4 1.4 0 0 1-1.4-1.4V3.4Z" {...stroke} />
    <path d="M9.6 2v3.6h3.6" {...stroke} />
    <path d="m5.6 9.6 1.4 1.4 3-3.2" {...stroke} />
  </Icon>
);

export const IconAudit = (p: IconProps) => (
  <Icon {...p}>
    <circle cx="8" cy="8" r="6" {...stroke} />
    <path d="M8 4.6V8l2.4 1.6" {...stroke} />
  </Icon>
);

export const IconMenu = (p: IconProps) => (
  <Icon {...p}>
    <path d="M2.6 4.4h10.8M2.6 8h10.8M2.6 11.6h10.8" {...stroke} />
  </Icon>
);

export const IconClose = (p: IconProps) => (
  <Icon {...p}>
    <path d="M4 4l8 8M12 4l-8 8" {...stroke} />
  </Icon>
);

export const IconChevronRight = (p: IconProps) => (
  <Icon {...p}>
    <path d="m6 3.6 4.4 4.4L6 12.4" {...stroke} />
  </Icon>
);

export const IconChevronLeft = (p: IconProps) => (
  <Icon {...p}>
    <path d="M10 3.6 5.6 8 10 12.4" {...stroke} />
  </Icon>
);

export const IconCheck = (p: IconProps) => (
  <Icon {...p}>
    <path d="m3.4 8.4 3 3 6.2-6.8" {...stroke} />
  </Icon>
);

export const IconPlus = (p: IconProps) => (
  <Icon {...p}>
    <path d="M8 3.2v9.6M3.2 8h9.6" {...stroke} />
  </Icon>
);

export const IconPlay = (p: IconProps) => (
  <Icon {...p}>
    <path d="M5.4 3.6 12 8l-6.6 4.4V3.6Z" {...stroke} />
  </Icon>
);

export const IconDownload = (p: IconProps) => (
  <Icon {...p}>
    <path d="M8 2.4v7.2M5.2 7l2.8 2.8L10.8 7" {...stroke} />
    <path d="M2.8 11.4v1.2a1 1 0 0 0 1 1h8.4a1 1 0 0 0 1-1v-1.2" {...stroke} />
  </Icon>
);

export const IconSun = (p: IconProps) => (
  <Icon {...p}>
    <circle cx="8" cy="8" r="3" {...stroke} />
    <path d="M8 1.6v1.5M8 12.9v1.5M14.4 8h-1.5M3.1 8H1.6M12.5 3.5l-1 1M4.5 11.5l-1 1M12.5 12.5l-1-1M4.5 4.5l-1-1" {...stroke} />
  </Icon>
);

export const IconMoon = (p: IconProps) => (
  <Icon {...p}>
    <path d="M13.2 9.4A5.6 5.6 0 0 1 6.6 2.8a5.8 5.8 0 1 0 6.6 6.6Z" {...stroke} />
  </Icon>
);

export const IconClock = (p: IconProps) => (
  <Icon {...p}>
    <circle cx="8" cy="8" r="5.6" {...stroke} />
    <path d="M8 4.8V8l2.2 1.4" {...stroke} />
  </Icon>
);

export const IconFlame = (p: IconProps) => (
  <Icon {...p}>
    <path d="M8 1.8s.6 2 2 3.4c1.5 1.5 2.4 2.6 2.4 4.4a4.4 4.4 0 0 1-8.8 0c0-1.5.7-2.6 1.6-3.4 0 1 .5 1.7 1.2 1.7.9 0 1.6-1.5 1.6-6.1Z" {...stroke} />
  </Icon>
);

export const IconLogo = ({ size = 24, ...props }: IconProps) => (
  <svg width={size} height={size} viewBox="0 0 32 32" fill="none" aria-hidden="true" {...props}>
    <path
      d="M6 24.5 L14 16.5 L20 21 L26.5 8.5"
      stroke="currentColor"
      strokeWidth="2.4"
      strokeLinecap="round"
      strokeLinejoin="round"
      opacity=".32"
    />
    <circle cx="6" cy="24.5" r="3.1" fill="currentColor" opacity=".55" />
    <circle cx="14" cy="16.5" r="3.1" fill="currentColor" opacity=".75" />
    <circle cx="26.5" cy="8.5" r="4" fill="currentColor" />
  </svg>
);
