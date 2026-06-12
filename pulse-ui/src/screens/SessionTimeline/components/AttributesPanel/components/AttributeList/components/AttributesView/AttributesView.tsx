import { Text, Badge } from "@mantine/core";
import { filterAttributes } from "../../utils/filters";
import classes from "../../AttributeList.module.css";

interface AttributesViewProps {
  attributes: Record<string, any>;
  searchQuery: string;
}

export function AttributesView({
  attributes,
  searchQuery = "",
}: AttributesViewProps) {
  const filtered = filterAttributes(attributes, searchQuery);

  if (Object.keys(filtered).length === 0) {
    return (
      <Text size="sm" c="dimmed" ta="center" py="xl">
        No attributes found
      </Text>
    );
  }

  return (
    <div className={classes.list}>
      {Object.entries(filtered).map(([key, value]) => (
        <div key={key} className={classes.listItem}>
          <Text className={classes.key} size="xs" fw={600}>
            {key}
          </Text>
          <Badge className={classes.valuePill} variant="light" color="gray">
            {typeof value === "object" ? JSON.stringify(value) : String(value)}
          </Badge>
        </div>
      ))}
    </div>
  );
}
