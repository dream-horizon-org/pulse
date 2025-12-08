/**
 * Empty State Component
 * Displays when a section has no items configured
 */

import { Box, Text, Button } from '@mantine/core';
import { IconPlus } from '@tabler/icons-react';
import classes from '../../SamplingConfig.module.css';

interface EmptyStateProps {
  icon: React.ReactNode;
  title: string;
  description: string;
  actionLabel: string;
  onAction: () => void;
}

export function EmptyState({ 
  icon, 
  title, 
  description, 
  actionLabel, 
  onAction 
}: EmptyStateProps) {
  return (
    <Box className={classes.emptyState}>
      <Box className={classes.emptyStateIcon}>
        {icon}
      </Box>
      <Text className={classes.emptyStateTitle}>{title}</Text>
      <Text className={classes.emptyStateText}>{description}</Text>
      <Button
        leftSection={<IconPlus size={16} />}
        variant="light"
        color="teal"
        onClick={onAction}
      >
        {actionLabel}
      </Button>
    </Box>
  );
}

