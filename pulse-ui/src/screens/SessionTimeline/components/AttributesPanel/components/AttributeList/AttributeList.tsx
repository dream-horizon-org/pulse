import { Text } from "@mantine/core";
import { AttributesView } from "./components/AttributesView";
import { EventsView } from "./components/EventsView";
import { LinksView } from "./components/LinksView";

interface AttributeListProps {
  attributes?: Record<string, any>;
  events?: Array<Record<string, any>>;
  links?: Array<Record<string, any>>;
  searchQuery?: string;
}

export function AttributeList({
  attributes,
  events,
  links,
  searchQuery,
}: AttributeListProps) {
  const query = searchQuery || "";

  if (attributes) {
    return <AttributesView attributes={attributes} searchQuery={query} />;
  }

  if (events) {
    return <EventsView events={events} searchQuery={query} />;
  }

  if (links) {
    return <LinksView links={links} searchQuery={query} />;
  }

  return (
    <Text size="sm" c="dimmed" ta="center" py="xl">
      No data available
    </Text>
  );
}
