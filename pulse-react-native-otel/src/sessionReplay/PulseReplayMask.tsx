import React from 'react';
import { View } from 'react-native';
import type { ViewProps } from 'react-native';

interface PulseMaskProps extends ViewProps {
  children: React.ReactNode;
}

const SESSION_REPLAY_MASKING_TAGS = {
  MASK: 'pulse-mask',
  UNMASK: 'pulse-unmask',
} as const;

export const PulseMask: React.FC<PulseMaskProps> = ({ children, ...props }) => (
  <View testID={SESSION_REPLAY_MASKING_TAGS.MASK} {...props}>
    {children}
  </View>
);

export const PulseUnmask: React.FC<PulseMaskProps> = ({
  children,
  ...props
}) => (
  <View testID={SESSION_REPLAY_MASKING_TAGS.UNMASK} {...props}>
    {children}
  </View>
);
