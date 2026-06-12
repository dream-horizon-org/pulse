import { Text, Badge } from "@mantine/core";
import { filterArrayItems } from "../../utils/filters";
import classes from "../../AttributeList.module.css";

interface EventsViewProps {
  events: Array<Record<string, any>>;
  searchQuery: string;
}

export function EventsView({ events, searchQuery = "" }: EventsViewProps) {
  const filtered = filterArrayItems(events, searchQuery);

  if (filtered.length === 0) {
    return (
      <Text size="sm" c="dimmed" ta="center" py="xl">
        No events found
      </Text>
    );
  }

  return (
    <div className={classes.list}>
      {filtered.map((event, index) => (
        <div key={index} className={classes.eventItem}>
          <Text size="xs" fw={600} c="dimmed" mb="xs">
            Event {index + 1}
          </Text>
          {Object.entries(event).map(([key, value]) => (
            <div key={key} className={classes.listItem}>
              <Text className={classes.key} size="xs" fw={600}>
                {key}
              </Text>
              <Badge className={classes.valuePill} variant="light" color="gray">
                {typeof value === "object"
                  ? JSON.stringify(value)
                  : String(value)}
              </Badge>
            </div>
          ))}
        </div>
      ))}
    </div>
  );
}
