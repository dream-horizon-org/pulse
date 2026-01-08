import { Box, Text, UnstyledButton, Group, ThemeIcon, Stack } from "@mantine/core";
import { IconActivity, IconUsers, IconClock } from "@tabler/icons-react";
import { DataSourceType } from "../../RealTimeQuery.interface";
import { DATA_SOURCES } from "../../RealTimeQuery.constants";
import classes from "./DataSourceSelector.module.css";

interface DataSourceSelectorProps {
  value: DataSourceType;
  onChange: (value: DataSourceType) => void;
}

const ICONS: Record<string, typeof IconActivity> = {
  activity: IconActivity,
  users: IconUsers,
  clock: IconClock,
};

export function DataSourceSelector({ value, onChange }: DataSourceSelectorProps) {
  return (
    <Stack gap="xs">
      {DATA_SOURCES.map((source) => {
        const Icon = ICONS[source.icon];
        const isSelected = value === source.id;

        return (
          <UnstyledButton
            key={source.id}
            onClick={() => onChange(source.id)}
            className={`${classes.sourceCard} ${isSelected ? classes.selected : ""}`}
          >
            <Group gap="sm" wrap="nowrap">
              <ThemeIcon
                size="lg"
                radius="md"
                variant={isSelected ? "filled" : "light"}
                color={isSelected ? "teal" : "gray"}
                className={classes.icon}
              >
                <Icon size={18} stroke={1.5} />
              </ThemeIcon>
              <Box>
                <Text size="sm" fw={600} className={classes.label}>
                  {source.label}
                </Text>
                <Text size="xs" c="dimmed" className={classes.description}>
                  {source.description}
                </Text>
              </Box>
            </Group>
          </UnstyledButton>
        );
      })}
    </Stack>
  );
}

