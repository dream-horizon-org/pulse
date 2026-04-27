/** Shared palette for a cohesive store look */
export const theme = {
  primary: '#0d9488',
  primaryDark: '#0f766e',
  accent: '#f59e0b',
  bg: '#f8fafc',
  surface: '#ffffff',
  text: '#0f172a',
  textMuted: '#64748b',
  border: '#e2e8f0',
  danger: '#dc2626',
  success: '#059669',
  radiusLg: 14,
  radiusMd: 10,
  shadow: {
    shadowColor: '#0f172a',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.06,
    shadowRadius: 8,
    elevation: 3,
  },
} as const;
