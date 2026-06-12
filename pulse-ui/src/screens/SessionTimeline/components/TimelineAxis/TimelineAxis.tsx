import { Text } from "@mantine/core";
import { formatTimeMs, calculateEventPosition } from "../../utils/formatters";
import classes from "./TimelineAxis.module.css";

interface TimelineAxisProps {
  maxTime: number;
  intervalSize?: number;
}

export function TimelineAxis({
  maxTime,
  intervalSize = 5000,
}: TimelineAxisProps) {
  const timeIntervals: number[] = [];
  for (let i = 0; i <= maxTime; i += intervalSize) {
    timeIntervals.push(i);
  }

  return (
    <div className={classes.timeAxis}>
      {timeIntervals.map((time) => {
        const position = calculateEventPosition(time, maxTime);

        return (
          <div
            key={time}
            className={classes.timeMarker}
            style={{ left: `${position}%` }}
          >
            <Text size="xs" c="dimmed">
              {formatTimeMs(time)}
            </Text>
          </div>
        );
      })}
    </div>
  );
}
