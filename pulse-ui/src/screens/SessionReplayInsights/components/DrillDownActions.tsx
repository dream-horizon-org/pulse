import { Button, ButtonProps, Text, ActionIcon, ActionIconProps, Tooltip } from "@mantine/core";
import { ReactNode } from "react";

/**
 * Reusable drill-down action components for Session Replay Insights
 * These components provide consistent UI for navigating to session lists
 */

// ============================================
// 1. Primary Drill-Down Button
// ============================================
interface DrillDownButtonProps extends Omit<ButtonProps, 'onClick'> {
  /** Label for the button */
  label?: string;
  /** Number of sessions (optional, shown in label) */
  count?: number;
  /** Click handler */
  onClick: () => void;
  /** Button variant */
  variant?: 'filled' | 'light' | 'outline' | 'subtle';
  /** Size */
  size?: 'xs' | 'sm' | 'md';
  /** Color */
  color?: string;
  /** Full width */
  fullWidth?: boolean;
}

export function DrillDownButton({
  label = 'View Sessions',
  count,
  onClick,
  variant = 'light',
  size = 'sm',
  color = 'teal',
  fullWidth = false,
  ...props
}: DrillDownButtonProps) {
  const buttonLabel = count !== undefined ? `${label} (${count})` : label;
  
  return (
    <Button
      variant={variant}
      size={size}
      color={color}
      fullWidth={fullWidth}
      onClick={onClick}
      {...props}
    >
      {buttonLabel}
    </Button>
  );
}

// ============================================
// 2. Inline Drill-Down Link (Text-based)
// ============================================
interface DrillDownLinkProps {
  /** Optional custom label */
  label?: string;
  /** Click handler */
  onClick: () => void;
}

export function DrillDownLink({ 
  label = 'Click to view sessions',
  onClick 
}: DrillDownLinkProps) {
  return (
    <Text 
      size="xs" 
      c="teal" 
      mt="xs"
      style={{ cursor: 'pointer' }}
      onClick={onClick}
    >
      → {label}
    </Text>
  );
}

// ============================================
// 3. Dual Action Buttons (Sample + All)
// ============================================
interface DualDrillDownProps {
  /** Label for primary action */
  primaryLabel?: string;
  /** Label for secondary action */
  secondaryLabel?: string;
  /** Count for secondary action */
  secondaryCount?: number;
  /** Primary action handler (e.g., view single sample session) */
  onPrimaryClick: () => void;
  /** Secondary action handler (e.g., view all sessions) */
  onSecondaryClick: () => void;
  /** Color for buttons */
  color?: string;
  /** Size */
  size?: 'xs' | 'sm';
}

export function DualDrillDown({
  primaryLabel = 'Sample',
  secondaryLabel = 'All',
  secondaryCount,
  onPrimaryClick,
  onSecondaryClick,
  color = 'teal',
  size = 'xs'
}: DualDrillDownProps) {
  const secondaryButtonLabel = secondaryCount !== undefined 
    ? `${secondaryLabel} (${secondaryCount})` 
    : secondaryLabel;

  return (
    <>
      <Button
        variant="subtle"
        size={size}
        color={color}
        onClick={onPrimaryClick}
        style={{ flex: 1 }}
      >
        {primaryLabel}
      </Button>
      <Button
        variant="light"
        size={size}
        color={color}
        onClick={onSecondaryClick}
        style={{ flex: 1 }}
      >
        {secondaryButtonLabel}
      </Button>
    </>
  );
}

// ============================================
// 4. Icon-Based Drill-Down (for compact spaces)
// ============================================
interface DrillDownIconProps extends Omit<ActionIconProps, 'onClick' | 'children'> {
  /** Tooltip label */
  label: string;
  /** Icon component */
  icon: ReactNode;
  /** Click handler */
  onClick: () => void;
  /** Color */
  color?: string;
  /** Variant */
  variant?: 'filled' | 'light' | 'outline' | 'subtle';
}

export function DrillDownIcon({
  label,
  icon,
  onClick,
  color = 'teal',
  variant = 'light',
  ...props
}: DrillDownIconProps) {
  return (
    <Tooltip label={label}>
      <ActionIcon
        variant={variant}
        color={color}
        onClick={onClick}
        {...props}
      >
        {icon}
      </ActionIcon>
    </Tooltip>
  );
}
