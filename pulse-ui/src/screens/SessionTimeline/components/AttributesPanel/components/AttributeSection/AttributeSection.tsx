import { Paper, Text } from "@mantine/core";
import { AttributeItem } from "../AttributeItem/AttributeItem";
import classes from "./AttributeSection.module.css";

interface AttributeSectionProps {
  title: string;
  attributes?: Record<string, any>;
  events?: Array<Record<string, any>>;
}

export function AttributeSection({
  title,
  attributes,
  events,
}: AttributeSectionProps) {
  return (
    <Paper className={classes.section} p="sm" withBorder>
      <Text className={classes.sectionTitle} mb="xs">
        {title}
      </Text>
      <div className={classes.items}>
        {attributes &&
          Object.entries(attributes).map(([key, value]) => (
            <AttributeItem key={key} attributeKey={key} value={value} />
          ))}
        {events &&
          events.map((event, index) => (
            <div key={index} className={classes.eventItem}>
              <Text size="xs" fw={600} c="dimmed" mb="xs">
                Event {index + 1}
              </Text>
              {Object.entries(event).map(([key, value]) => (
                <AttributeItem key={key} attributeKey={key} value={value} />
              ))}
            </div>
          ))}
      </div>
    </Paper>
  );
}
