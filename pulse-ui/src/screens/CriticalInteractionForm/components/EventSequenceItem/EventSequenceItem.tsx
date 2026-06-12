import { Draggable } from "@hello-pangea/dnd";
import { EventSequenceItemProps } from "./EventSequenceItem.interface";
import classes from "./EventSequenceItem.module.css";
import {
  IconCircleOff,
  IconGripVertical,
  IconTrash,
} from "@tabler/icons-react";
import { Grid, GridCol, Tooltip, rem } from "@mantine/core";
import { AutoCompleteInput } from "../AutoCompleteInput";
import { EventsPropsFilter } from "../EventsPropsFilter";
import {
  CRITICAL_INTERACTION_FORM_CONSTANTS,
  TOOLTIP_LABLES,
} from "../../../../constants";
import { EventListData } from "../../../../hooks/useGetScreenNameToEventQueryMapping";
import { useState } from "react";

export function EventSequenceItem(props: EventSequenceItemProps) {
  const {
    index,
    draggableId,
    isEventBlackListed,
    showAddEventFilterButton,
    onEventNameChange,
    onBlacklistEventButtonClick,
    onDeleteButtonClick,
    onAddEventFilterButtonClick,
    onDeleteFilterButtonClick,
    onFilterValueChange,
    eventData,
    totalItemsLength,
  } = props;

  const [propertiesOfSelectedEvent, setPropertiesOfSelectedEvent] = useState<
    EventListData["properties"]
  >([]);

  const isFirstIndex: boolean = index === 0;
  const isLastIndex: boolean = index === totalItemsLength - 1;
  const hideActionButtons: boolean =
    index === 0 || index === totalItemsLength - 1;
  let placeHolderText: string = isFirstIndex
    ? CRITICAL_INTERACTION_FORM_CONSTANTS.TO_EVENT_PLACE_HOLDER_TEXT
    : isLastIndex
      ? CRITICAL_INTERACTION_FORM_CONSTANTS.T1_EVENT_PLACE_HOLDER_TEXT
      : CRITICAL_INTERACTION_FORM_CONSTANTS.WHITELIST_EVENT_PLACEHOLDER_TEXT;

  const onEventNameSelect = (eventProperties: EventListData["properties"]) => {
    setPropertiesOfSelectedEvent(eventProperties);
  };

  const getBadgeText = (index: number) => {
    switch (index) {
      case 0:
        return "Start event";
      case totalItemsLength - 1:
        return "End event";
      default:
        return `T${index} event`;
    }
  };

  return (
    <Draggable key={draggableId} index={index} draggableId={draggableId}>
      {(provided, snapshot) => (
        <div
          className={`${classes.eventSequenceItem} ${snapshot.isDragging ? classes.eventSequenceItemDragging : ""} ${isEventBlackListed ? classes.blackListedEventSequenceItem : ""}`}
          ref={provided.innerRef}
          {...provided.draggableProps}
        >
          <Grid className={classes.eventSequenceInputAndButtonContainer}>
            <GridCol
              span={0.8}
              {...provided.dragHandleProps}
              className={classes.dragHandle}
            >
              <IconGripVertical
                style={{ width: rem(18), height: rem(18) }}
                stroke={1.5}
              />
            </GridCol>
            <GridCol
              span={11.2}
              className={classes.eventSequenceInputMainContainer}
            >
              <div className={classes.eventMidSequenceInputContainer}>
                <AutoCompleteInput
                  onEventNameChange={onEventNameChange}
                  eventName={eventData?.name ?? ""}
                  placeHolderText={placeHolderText}
                  showBadge={true}
                  badgeText={getBadgeText(index)}
                  onEventNameSelect={onEventNameSelect}
                />
                {!hideActionButtons && (
                  <div className={classes.eventSequenceButtonsContainer}>
                    <>
                      <Tooltip
                        withArrow
                        label={
                          isEventBlackListed
                            ? TOOLTIP_LABLES.REMOVE_BLACKLIST_EVENT_MESSAGE
                            : `Do not track the user interaction if ${eventData?.name ?? "this"} event is found in the sequence`
                        }
                      >
                        <button
                          onClick={onBlacklistEventButtonClick}
                          className={`${classes.eventSequenceActionButtons} ${isEventBlackListed ? classes.redColor : ""}`}
                        >
                          <IconCircleOff
                            style={{ width: rem(16), height: rem(16) }}
                            stroke={1.5}
                          />
                        </button>
                      </Tooltip>
                      <Tooltip
                        label={`Remove ${eventData?.name ?? "this"} event in the sequence`}
                      >
                        <button
                          onClick={onDeleteButtonClick}
                          className={classes.eventSequenceActionButtons}
                        >
                          <IconTrash
                            style={{ width: rem(16), height: rem(16) }}
                            stroke={1.5}
                          />
                        </button>
                      </Tooltip>
                    </>
                  </div>
                )}
              </div>
              <EventsPropsFilter
                eventsPropertiesSuggestions={propertiesOfSelectedEvent}
                showAddEventFilterButton={showAddEventFilterButton}
                onAddEventFilterButtonClick={onAddEventFilterButtonClick}
                filters={eventData?.props}
                onFilterValueChange={onFilterValueChange}
                onDeleteFilterButtonClick={onDeleteFilterButtonClick}
              />
            </GridCol>
          </Grid>
        </div>
      )}
    </Draggable>
  );
}
