import { Text } from "@mantine/core";
import { TablerIcon } from "@tabler/icons-react";
import classes from "./TabButton.module.css";

interface TabButtonProps {
  icon: TablerIcon;
  label: string;
  count: number;
  isActive: boolean;
  onClick: () => void;
}

export function TabButton({
  icon: Icon,
  label,
  count,
  isActive,
  onClick,
}: TabButtonProps) {
  return (
    <button
      className={`${classes.tab} ${isActive ? classes.tabActive : ""}`}
      onClick={onClick}
    >
      <Icon size={14} />
      <Text size="sm" fw={500}>
        {label}
      </Text>
      <Text size="xs" className={classes.tabCount}>
        {count}
      </Text>
    </button>
  );
}
