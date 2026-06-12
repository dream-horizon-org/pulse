import { Tooltip, rem } from "@mantine/core";
import { AutoCompleteInput } from "../AutoCompleteInput";
import { GlobalBlacklistEventSequenceItemProps } from "./GlobalBlacklistEventSequenceItem.interface";
import classes from "./GlobalBlacklistEventSequenceItem.module.css";
import { IconTrash } from "@tabler/icons-react";
import { EventsPropsFilter } from "../EventsPropsFilter";
import { EventListData } from "../../../../hooks/useGetScreenNameToEventQueryMapping";
import { useState } from "react";

export function GlobalBlacklistEventSequenceItem(
  props: GlobalBlacklistEventSequenceItemProps,
) {
  const {
    eventName,
    showAddEventFilterButton,
    filters,
    onEventNameChange,
    onDeleteButtonClick,
    onAddEventFilterButtonClick,
    onFilterValueChange,
    onDeleteFilterButtonClick,
  } = props;

  const [propertiesOfSelectedEvent, setPropertiesOfSelectedEvent] = useState<
    EventListData["properties"]
  >([]);

  const onEventNameSelect = (eventProperties: EventListData["properties"]) => {
    setPropertiesOfSelectedEvent(eventProperties);
  };

  return (
    <div className={classes.globalBlacklistEventItemMainContainer}>
      <div className={classes.globalBlacklistEventItemContainer}>
        <AutoCompleteInput
          onEventNameChange={onEventNameChange}
          eventName={eventName}
          onEventNameSelect={onEventNameSelect}
        />
        <Tooltip
          label={`Remove ${eventName ? eventName : "this"} event from the global blacklist`}
        >
          <button
            className={classes.deleteBlacklistEventsButton}
            onClick={(event) => {
              event.preventDefault();
              onDeleteButtonClick();
            }}
          >
            <IconTrash
              style={{ width: rem(18), height: rem(18) }}
              stroke={1.5}
            />
          </button>
        </Tooltip>
      </div>
      <EventsPropsFilter
        eventsPropertiesSuggestions={propertiesOfSelectedEvent}
        showAddEventFilterButton={showAddEventFilterButton}
        onAddEventFilterButtonClick={onAddEventFilterButtonClick}
        filters={filters}
        onFilterValueChange={onFilterValueChange}
        onDeleteFilterButtonClick={onDeleteFilterButtonClick}
      />
    </div>
  );
}
