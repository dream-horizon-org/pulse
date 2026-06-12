import { AbsoluteNumbersForGraphsProps } from "./AbsoluteNumbersForGraphsProps.interface";
import classes from "./AbsoluteNumbersForGraphs.module.css";
import { Text } from "@mantine/core";

export function AbsoluteNumbersForGraphs({
  title,
  data,
  color,
}: AbsoluteNumbersForGraphsProps) {
  return (
    <div className={classes.metricCard}>
      <Text className={classes.metricLabel}>{title}</Text>
      <Text
        className={classes.metricValue}
        style={{ color: `var(--mantine-color-${color})` }}
      >
        {data}
      </Text>
    </div>
  );
}
