/**
 * SDK Tag Component
 * Displays SDK type with appropriate color coding
 */

import { Badge } from '@mantine/core';
import { SDKType } from '../../SamplingConfig.interface';
import { SDK_COLORS } from '../../SamplingConfig.constants';

interface SdkTagProps {
  sdk: SDKType;
  size?: 'xs' | 'sm' | 'md';
}

export function SdkTag({ sdk, size = 'xs' }: SdkTagProps) {
  const color = SDK_COLORS[sdk];
  
  const getLabel = () => {
    switch (sdk) {
      case 'ANDROID':
        return 'Android';
      case 'IOS':
        return 'iOS';
      case 'REACT_NATIVE':
        return 'React Native';
      case 'WEB':
        return 'Web';
      default:
        return sdk;
    }
  };

  return (
    <Badge
      size={size}
      variant="light"
      style={{
        backgroundColor: `${color}15`,
        color: color,
        border: `1px solid ${color}30`,
        textTransform: 'none',
        fontWeight: 600,
      }}
    >
      {getLabel()}
    </Badge>
  );
}

