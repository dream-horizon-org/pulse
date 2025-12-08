/**
 * Scope Tag Component
 * Displays scope type (LOGS, TRACES, METRICS) with appropriate color coding
 */

import { Badge } from '@mantine/core';
import { ScopeType } from '../../SamplingConfig.interface';
import { SCOPE_COLORS } from '../../SamplingConfig.constants';

interface ScopeTagProps {
  scope: ScopeType;
  size?: 'xs' | 'sm' | 'md';
}

export function ScopeTag({ scope, size = 'xs' }: ScopeTagProps) {
  const color = SCOPE_COLORS[scope];
  
  return (
    <Badge
      size={size}
      variant="light"
      style={{
        backgroundColor: `${color}15`,
        color: color,
        border: `1px solid ${color}30`,
        textTransform: 'capitalize',
        fontWeight: 500,
      }}
    >
      {scope.toLowerCase()}
    </Badge>
  );
}

