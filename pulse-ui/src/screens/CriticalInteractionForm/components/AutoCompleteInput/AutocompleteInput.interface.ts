import { EventListData } from "../../../../hooks/useGetScreenNameToEventQueryMapping";

export type AutoCompleteInputProps = {
  onEventNameChange: (eventName: string) => void;
  eventName: string;
  placeHolderText?: string;
  showBadge?: boolean;
  badgeText?: string;
  onEventNameSelect?: (eventProperties: EventListData["properties"]) => void;
};
