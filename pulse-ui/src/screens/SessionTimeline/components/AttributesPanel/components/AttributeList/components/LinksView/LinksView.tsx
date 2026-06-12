import { Text, Badge } from "@mantine/core";
import { filterArrayItems } from "../../utils/filters";
import classes from "../../AttributeList.module.css";

interface LinksViewProps {
  links: Array<Record<string, any>>;
  searchQuery: string;
}

export function LinksView({ links, searchQuery = "" }: LinksViewProps) {
  const filtered = filterArrayItems(links, searchQuery);

  if (filtered.length === 0) {
    return (
      <Text size="sm" c="dimmed" ta="center" py="xl">
        No links found
      </Text>
    );
  }

  return (
    <div className={classes.list}>
      {filtered.map((link, index) => (
        <div key={index} className={classes.listItem}>
          <Text className={classes.key} size="xs" fw={600}>
            Link {index + 1}
          </Text>
          <Badge className={classes.valuePill} variant="light" color="gray">
            {typeof link === "object" ? JSON.stringify(link) : String(link)}
          </Badge>
        </div>
      ))}
    </div>
  );
}
