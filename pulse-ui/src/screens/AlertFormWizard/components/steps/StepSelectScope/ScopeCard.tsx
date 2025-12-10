/**
 * Scope Card Component
 */

import React from "react";
import { Box, Text, ThemeIcon } from "@mantine/core";
import { IconClick, IconDeviceMobile, IconHeartRateMonitor, IconApi } from "@tabler/icons-react";
import { AlertScopeType } from "../../../types";
import classes from "./StepSelectScope.module.css";

interface ScopeCardProps {
  id: AlertScopeType;
  label: string;
  description: string;
  features: string[];
  color: string;
  isSelected: boolean;
  onClick: () => void;
}

const ICONS: Record<AlertScopeType, React.ElementType> = {
  [AlertScopeType.Interaction]: IconClick,
  [AlertScopeType.Screen]: IconDeviceMobile,
  [AlertScopeType.AppVitals]: IconHeartRateMonitor,
  [AlertScopeType.NetworkAPI]: IconApi,
};

export const ScopeCard: React.FC<ScopeCardProps> = ({ id, label, description, features, color, isSelected, onClick }) => {
  const Icon = ICONS[id];

  return (
    <Box className={`${classes.card} ${isSelected ? classes.selected : ""}`} onClick={onClick} style={{ "--scope-color": color } as React.CSSProperties}>
      <ThemeIcon size={48} radius="md" variant={isSelected ? "filled" : "light"} color={isSelected ? "teal" : "gray"} className={classes.icon}>
        <Icon size={24} />
      </ThemeIcon>
      <Box className={classes.cardContent}>
        <Text className={classes.cardTitle}>{label}</Text>
        <Text className={classes.cardDescription}>{description}</Text>
        <Box className={classes.features}>
          {features.map((f, i) => <Text key={i} className={classes.feature}>• {f}</Text>)}
        </Box>
      </Box>
    </Box>
  );
};

