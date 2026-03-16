// Session Replay Screen Constants
// Following Pulse UI rules: feature-first constants

// Colors (should eventually come from theme/design tokens)
export const COLORS = {
  // Teal theme for Session Replay
  primary: 'var(--mantine-color-teal-6)',
  primaryLight: 'var(--mantine-color-teal-1)',
  
  // Status colors
  error: 'var(--mantine-color-red-6)',
  warning: 'var(--mantine-color-orange-6)',
  success: 'var(--mantine-color-green-6)',
  
  // Backgrounds
  background: '#fafbfc',
  cardBg: 'white',
  hoverBg: '#f8f9fa',
  
  // Borders
  border: '#e9ecef',
  borderHover: '#dee2e6',
  
  // Text
  textPrimary: 'var(--mantine-color-dark-8)',
  textSecondary: 'var(--mantine-color-dark-4)',
  textDimmed: 'var(--mantine-color-gray-6)',
} as const;

// Spacing (following Mantine spacing scale)
export const SPACING = {
  xs: '8px',
  sm: '12px',
  md: '16px',
  lg: '24px',
  xl: '32px',
} as const;

// Border radius (following Mantine radius scale)
export const RADIUS = {
  sm: '4px',
  md: '8px',
  lg: '12px',
} as const;

// Shadows
export const SHADOWS = {
  card: '0 1px 3px rgba(0, 0, 0, 0.05)',
  hover: '0 4px 12px rgba(0, 0, 0, 0.08)',
} as const;

// Breakpoints (for responsive design)
export const BREAKPOINTS = {
  mobile: '768px',
  tablet: '1024px',
  desktop: '1200px',
} as const;

// Filter Badge Colors (interaction quality theme)
export const FILTER_BADGE_COLORS = {
  // Interaction Quality
  failedInteractions: 'red',
  slowInteractions: 'orange',
  jankyInteractions: 'yellow',
  
  // User Frustration
  rageClicks: 'pink',
  deadClicks: 'violet',
  
  // Session Outcome
  hasErrors: 'red',
  crashed: 'grape',
} as const;

// Z-index layers
export const Z_INDEX = {
  base: 1,
  dropdown: 1000,
  sticky: 1100,
  modal: 1300,
  tooltip: 1400,
} as const;

// Animation durations
export const ANIMATION = {
  fast: '150ms',
  normal: '250ms',
  slow: '350ms',
} as const;

// Session Replay - Date Range Configuration
// TODO: Fetch from /api/config/date-ranges endpoint (tracked in backend implementation)

export const DATE_RANGE_SPECIAL = {
  CUSTOM: 'custom',
} as const;

export const DEFAULT_DATE_RANGES = {
  options: [
    { value: '1h', label: 'Last hour' },
    { value: '1', label: 'Last 24 hours' },
    { value: '7d', label: 'Last 7 days' },
    { value: '30', label: 'Last 30 days' },
    { value: DATE_RANGE_SPECIAL.CUSTOM, label: 'Custom Range' },
  ],
  defaultValue: '7d',
} as const;
